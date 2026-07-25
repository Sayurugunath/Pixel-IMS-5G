package dev.bluehouse.enablevolte

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.ServiceManager
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal root-side Binder bridge. Calls are forwarded from UID 0 so system_server sees root as
 * the caller. Only the fixed set of telephony-related services used by this app is exposed.
 */
class PrivilegedService : RootService() {
    private val forwardedServices = ConcurrentHashMap<String, IBinder>()

    private val binder = object : IPrivilegedService.Stub() {
        override fun getSystemService(name: String): IBinder? {
            if (name !in ALLOWED_SERVICES) return null
            return forwardedServices.getOrPut(name) {
                ForwardingBinder(ServiceManager.getService(name) ?: error("Service $name is unavailable"))
            }
        }

        override fun getServiceUid(): Int = Process.myUid()

        override fun getAllowedSystemProperty(name: String): String? {
            if (name !in ALLOWED_PROPERTIES) return null
            return runCommand("/system/bin/getprop", name).takeIf { it.exitCode == 0 }?.output?.trim()
        }

        override fun setAllowedSystemProperty(name: String, value: String): Boolean {
            if (name !in ALLOWED_PROPERTIES || value !in ALLOWED_PROPERTY_VALUES) return false
            return runCommand("/system/bin/setprop", name, value).exitCode == 0
        }

        override fun getTelephonyDiagnosticSnapshot(kind: String): String {
            Log.i(TAG, "Reading root telephony diagnostic: $kind")
            val command = DIAGNOSTIC_COMMANDS[kind] ?: return "Unsupported diagnostic source"
            val result = runCommand(*command)
            if (result.exitCode != 0) return "Diagnostic source failed (${result.exitCode})"
            return runCatching {
                when (kind) {
                    "physical" -> extractPhysicalChannels(result.output)
                    "properties" -> extractRadioProperties(result.output)
                    "carrier" -> extractCarrierConfig(result.output)
                    else -> sanitizeDiagnostic(result.output)
                }
            }.getOrElse {
                Log.w(TAG, "Diagnostic parser failed for $kind", it)
                "Diagnostic parser failed: ${it.javaClass.simpleName}"
            }
        }

        override fun getRegionalModemPatchStatus(): String =
            regionalPatchStatus(message = regionalPatchStatusMessage()).toString()

        override fun installRegionalModemPatch(): String =
            runCatching { installRegionalPatch() }
                .getOrElse {
                    Log.e(TAG, "Regional modem patch installation failed", it)
                    regionalPatchStatus(message = it.message ?: "Patch installation failed.").toString()
                }

        override fun scheduleRegionalModemPatchRemoval(): String =
            runCatching {
                val moduleDir = File(REGIONAL_MODULE_DIR)
                check(moduleDir.isDirectory) { "The regional modem patch is not installed." }
                File(moduleDir, "remove").apply {
                    check(createNewFile() || isFile) { "Unable to create the Magisk removal marker." }
                }
                regionalPatchStatus(
                    rebootRequired = true,
                    message = "Removal scheduled. Reboot to restore the stock modem carrier database.",
                ).toString()
            }.getOrElse {
                Log.e(TAG, "Unable to schedule regional modem patch removal", it)
                regionalPatchStatus(message = it.message ?: "Unable to schedule removal.").toString()
            }
    }

    override fun onBind(intent: Intent): IBinder = binder

    private class ForwardingBinder(
        private val target: IBinder,
    ) : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean =
            target.transact(code, data, reply, flags)
    }

    companion object {
        private const val TAG = "PrivilegedService"
        private data class CommandResult(val exitCode: Int, val output: String)

        private fun runCommand(vararg command: String): CommandResult =
            runCatching {
                val process = ProcessBuilder(*command).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                CommandResult(process.waitFor(), output)
            }.getOrElse { CommandResult(-1, it.message.orEmpty()) }

        private fun installRegionalPatch(): String {
            val device = Build.DEVICE.lowercase()
            check(device in TENSOR_DEVICES) {
                "Unsupported device '$device'. This patch is limited to known Google Tensor Pixels."
            }
            check(findExecutable(MAGISK_CANDIDATES) != null) {
                "Magisk was not detected. The modem database patch is unavailable with Shizuku."
            }
            val source = File(REGIONAL_SOURCE_DB)
            check(source.isFile) { "The Tensor carrier database was not found at $REGIONAL_SOURCE_DB." }

            val policy = findExecutable(MAGISK_POLICY_CANDIDATES)
                ?: error("magiskpolicy was not found; the stock carrier database cannot be read safely.")
            val policyResult = runCommand(
                policy,
                "--live",
                "allow magisk vendor_fw_file file { getattr open read map }",
            )
            check(policyResult.exitCode == 0) {
                "SELinux refused temporary read access to the stock carrier database."
            }

            val workDir = File(cacheDirectory(), "regional-modem-patch").apply {
                deleteRecursively()
                check(mkdirs()) { "Unable to create the patch staging directory." }
            }
            val stagedDatabase = File(workDir, "cfg.db")
            source.copyTo(stagedDatabase, overwrite = true)
            val sourceSha = stagedDatabase.sha256()

            patchAndValidateCarrierDatabase(stagedDatabase)
            val patchedSha = stagedDatabase.sha256()
            check(sourceSha != patchedSha) {
                "The database already uses the requested wildcard profile; no patch was staged."
            }

            val pendingModule = File("$REGIONAL_MODULE_DIR.new").apply {
                deleteRecursively()
                check(mkdirs()) { "Unable to create the Magisk module staging directory." }
            }
            File(pendingModule, "module.prop").writeText(
                """
                id=pixelims5g_region
                name=Pixel IMS 5G Regional Modem Compatibility
                version=1
                versionCode=1
                author=Nadeeja Nirmala
                description=Systemless, reversible Tensor cfg.db wildcard/PTCRB compatibility mapping.
                """.trimIndent() + "\n",
            )
            File(pendingModule, "service.sh").writeText(
                """
                #!/system/bin/sh
                rm -f /data/adb/modules/pixelims5g_region/.pending_reboot
                """.trimIndent() + "\n",
            )
            File(pendingModule, ".pending_reboot").writeText("")
            File(pendingModule, "source.sha256").writeText("$sourceSha\n")
            File(pendingModule, "patched.sha256").writeText("$patchedSha\n")
            val targetDatabase = File(
                pendingModule,
                "system/vendor/firmware/carrierconfig/cfg.db",
            ).apply {
                check(parentFile?.mkdirs() == true || parentFile?.isDirectory == true) {
                    "Unable to create the systemless vendor overlay."
                }
            }
            stagedDatabase.copyTo(targetDatabase, overwrite = true)

            check(
                runCommand(
                    "/system/bin/chcon",
                    "-R",
                    "u:object_r:magisk_file:s0",
                    pendingModule.path,
                ).exitCode == 0,
            ) {
                "Unable to assign the Magisk module SELinux label."
            }
            check(runCommand("/system/bin/chmod", "0755", File(pendingModule, "service.sh").path).exitCode == 0) {
                "Unable to make the module boot service executable."
            }
            check(runCommand("/system/bin/chmod", "0644", targetDatabase.path).exitCode == 0) {
                "Unable to set modem database permissions."
            }
            check(
                runCommand(
                    "/system/bin/chcon",
                    "u:object_r:vendor_fw_file:s0",
                    targetDatabase.path,
                ).exitCode == 0,
            ) {
                "Unable to assign the vendor firmware SELinux label."
            }

            val moduleDir = File(REGIONAL_MODULE_DIR)
            if (moduleDir.exists()) {
                check(moduleDir.canonicalPath == REGIONAL_MODULE_DIR) { "Unsafe module target path." }
                check(moduleDir.deleteRecursively()) { "Unable to replace the previous regional patch." }
            }
            check(pendingModule.renameTo(moduleDir)) { "Unable to activate the staged Magisk module." }
            workDir.deleteRecursively()

            return regionalPatchStatus(
                rebootRequired = true,
                message = "Validated systemless modem patch installed. Reboot to load it.",
            ).toString()
        }

        private fun patchAndValidateCarrierDatabase(databaseFile: File) {
            val database = SQLiteDatabase.openDatabase(
                databaseFile.path,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )
            try {
                check(database.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }) {
                    "The stock carrier database failed SQLite integrity checking."
                }
                val tableNames = database.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('confnames','confmap')",
                    null,
                ).use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(0))
                    }
                }
                check(tableNames == setOf("confnames", "confmap")) {
                    "Unsupported carrier database schema: required tables are missing."
                }
                val profileExists = database.rawQuery(
                    "SELECT COUNT(*) FROM confmap WHERE carrier_id=(" +
                        "SELECT carrier_id FROM confnames WHERE name='it_iliad' LIMIT 1)",
                    null,
                ).use { it.moveToFirst() && it.getInt(0) == 1 }
                check(profileExists) {
                    "This firmware has no validated permissive Tensor carrier profile (it_iliad)."
                }

                database.beginTransaction()
                try {
                    val profile = "(SELECT confman FROM confmap WHERE carrier_id=(" +
                        "SELECT carrier_id FROM confnames WHERE name='it_iliad' LIMIT 1))"
                    database.execSQL(
                        "UPDATE confmap SET confman=$profile WHERE carrier_id IN (0,20001,20005)",
                    )
                    database.execSQL(
                        "INSERT OR IGNORE INTO confmap (carrier_id,confman) VALUES (20001,$profile)",
                    )
                    database.execSQL(
                        "INSERT OR IGNORE INTO confmap (carrier_id,confman) VALUES (20005,$profile)",
                    )
                    val mapped = database.rawQuery(
                        "SELECT COUNT(*) FROM confmap WHERE carrier_id IN (0,20001,20005) " +
                            "AND confman=$profile",
                        null,
                    ).use { it.moveToFirst() && it.getInt(0) == 3 }
                    check(mapped) { "The wildcard/PTCRB carrier mappings were not retained." }
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
                check(database.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }) {
                    "The patched carrier database failed SQLite integrity checking."
                }
            } finally {
                database.close()
            }
        }

        private fun regionalPatchStatus(
            rebootRequired: Boolean? = null,
            message: String,
        ): JSONObject {
            val device = Build.DEVICE.lowercase()
            val module = File(REGIONAL_MODULE_DIR)
            val database = File(module, "system/vendor/firmware/carrierconfig/cfg.db")
            val removalPending = File(module, "remove").isFile
            val pendingReboot = File(module, ".pending_reboot").isFile
            return JSONObject()
                .put("supported", device in TENSOR_DEVICES)
                .put("magiskAvailable", findExecutable(MAGISK_CANDIDATES) != null)
                .put("sourceAvailable", File(REGIONAL_SOURCE_DB).isFile)
                .put("installed", database.isFile)
                .put("removalPending", removalPending)
                .put("rebootRequired", rebootRequired ?: (pendingReboot || removalPending))
                .put("device", device)
                .put("sourceSha256", File(module, "source.sha256").readSmallText())
                .put("patchedSha256", File(module, "patched.sha256").readSmallText())
                .put("message", message)
        }

        private fun regionalPatchStatusMessage(): String {
            val device = Build.DEVICE.lowercase()
            val module = File(REGIONAL_MODULE_DIR)
            return when {
                device !in TENSOR_DEVICES -> "This device is not in the validated Tensor Pixel list."
                findExecutable(MAGISK_CANDIDATES) == null -> "Magisk is required; Shizuku cannot overlay vendor firmware."
                !File(REGIONAL_SOURCE_DB).isFile -> "The Tensor carrier database was not found on this firmware."
                File(module, "remove").isFile -> "Removal is scheduled. Reboot to restore the stock modem database."
                File(module, ".pending_reboot").isFile -> "Patch installed. Reboot is required before it becomes active."
                File(module, "system/vendor/firmware/carrierconfig/cfg.db").isFile ->
                    "The systemless regional modem compatibility patch is installed."
                else -> "Ready for schema validation and systemless installation."
            }
        }

        private fun cacheDirectory(): File =
            File("/data/local/tmp/pixelims5g").apply {
                check(mkdirs() || isDirectory) { "Unable to access the root staging directory." }
            }

        private fun findExecutable(paths: List<String>): String? =
            paths.firstOrNull { File(it).canExecute() }

        private fun File.readSmallText(): String =
            runCatching { if (isFile && length() < 512) readText().trim() else "" }.getOrDefault("")

        private fun File.sha256(): String {
            val digest = MessageDigest.getInstance("SHA-256")
            inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private val ALLOWED_SERVICES = setOf(
            "carrier_config",
            "phone",
            "iphonesubinfo",
            "isub",
            "power",
            "activity",
            "telephony.oem.oemrilhook",
        )

        private val ALLOWED_PROPERTIES = setOf(
            "persist.dbg.ims_volte_enable",
            "persist.dbg.volte_avail_ovr",
            "persist.dbg.wfc_avail_ovr",
            "persist.dbg.vt_avail_ovr",
            "persist.radio.is_vonr_enabled_0",
            "persist.radio.is_vonr_enabled_1",
        )
        private val ALLOWED_PROPERTY_VALUES = setOf("", "0", "1", "false", "true")

        private const val REGIONAL_SOURCE_DB = "/vendor/firmware/carrierconfig/cfg.db"
        private const val REGIONAL_MODULE_DIR = "/data/adb/modules/pixelims5g_region"
        private val MAGISK_CANDIDATES = listOf(
            "/system_ext/bin/magisk",
            "/sbin/magisk",
            "/data/adb/magisk/magisk",
        )
        private val MAGISK_POLICY_CANDIDATES = listOf(
            "/system_ext/bin/magiskpolicy",
            "/sbin/magiskpolicy",
            "/data/adb/magisk/magiskpolicy",
        )
        private val TENSOR_DEVICES = setOf(
            "oriole", "raven", "bluejay",
            "panther", "cheetah", "lynx", "tangorpro", "felix",
            "shiba", "husky",
            "tokay", "caiman", "komodo", "comet",
            "mustang", "blazer", "rango",
        )

        private val DIAGNOSTIC_COMMANDS = mapOf(
            "registry" to arrayOf("/system/bin/dumpsys", "telephony.registry"),
            "physical" to arrayOf("/system/bin/dumpsys", "telephony.registry"),
            "phone" to arrayOf("/system/bin/dumpsys", "activity", "service", "com.android.phone"),
            "carrier" to arrayOf("/system/bin/dumpsys", "carrier_config"),
            "properties" to arrayOf("/system/bin/getprop"),
            "radio" to arrayOf("/system/bin/logcat", "-b", "radio", "-d", "-v", "threadtime", "-t", "1200"),
        )
        private val DIAGNOSTIC_TERMS = Regex(
            "nr|5g|endc|en-dc|dcnr|dual.?connect|scg|secondary.?cell|reject|fail|cause|" +
                "registration|servicestate|physicalchannel|carrier.?aggregation",
            RegexOption.IGNORE_CASE,
        )
        private val LONG_IDENTIFIER = Regex("(?<![A-Za-z])\\+?\\d[\\d -]{8,}\\d")
        private val SENSITIVE_FIELD = Regex(
            "(?i)\\b(m?iccid|m?imsi|imei|meid|msisdn|phoneNumber|line1Number|" +
                "subscriberId)\\s*[=:]\\s*[^,\\s}]+",
        )
        private val IPV4 = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
        private val IPV6 = Regex("\\b(?:[0-9a-fA-F]{1,4}:){2,}[0-9a-fA-F:]{0,39}\\b")
        private val RADIO_PROPERTY = Regex(
            "(?i)^\\[(?:gsm\\.(?:operator|sim\\.operator)\\.(?:numeric|iso-country)|" +
                "gsm\\.version\\.baseband|persist\\.(?:radio|vendor\\.radio|vendor\\.ril|vendor\\.modem)\\.|" +
                "ro\\.(?:baseband|build\\.expect\\.baseband|carrier|vendor\\.config\\.build_carrier)|" +
                "vendor\\.ril\\.(?:modem\\.cfg|sim)\\.carrier_id|vendor\\.radio\\.|ril\\.)",
        )
        private val CARRIER_CONFIG_DIAGNOSTIC = Regex(
            "(?i)5g|\\bnr[_ -]|endc|en-dc|dcnr|vonr|volte|wfc|wifi.?call|ims|" +
                "carrier.?id|subscription|sub.?id|config.?package|lte.?plus|lte_ca|" +
                "advanced.?band|unmetered.?nr|rrc",
        )

        /**
         * Root radio output can contain subscriber identifiers. Return only relevant lines and
         * redact long numeric values before the data enters the app process or a shared report.
         */
        private fun sanitizeDiagnostic(raw: String): String =
            raw.lineSequence()
                .filter { DIAGNOSTIC_TERMS.containsMatchIn(it) }
                .flatMap { line ->
                    if (line.length <= 700) {
                        sequenceOf(line)
                    } else {
                        DIAGNOSTIC_TERMS.findAll(line).take(12).map { match ->
                            line.substring(
                                (match.range.first - 140).coerceAtLeast(0),
                                (match.range.last + 320).coerceAtMost(line.length),
                            )
                        }
                    }
                }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(260)
                .joinToString("\n") {
                    IPV6.replace(
                        IPV4.replace(
                            SENSITIVE_FIELD.replace(
                                LONG_IDENTIFIER.replace(it, "[redacted-id]"),
                            ) { match -> "${match.groupValues[1]}=[redacted]" },
                            "[redacted-ip]",
                        ),
                        "[redacted-ip]",
                    )
                }
                .take(180_000)

        private fun extractRadioProperties(raw: String): String =
            raw.lineSequence()
                .map(String::trim)
                .filter(RADIO_PROPERTY::containsMatchIn)
                .map { line ->
                    SENSITIVE_FIELD.replace(LONG_IDENTIFIER.replace(line, "[redacted-id]")) { match ->
                        "${match.groupValues[1]}=[redacted]"
                    }
                }
                .distinct()
                .sorted()
                .take(180)
                .joinToString("\n")
                .ifBlank { "No matching modem/carrier properties were exposed." }

        private fun extractCarrierConfig(raw: String): String =
            raw.lineSequence()
                .map(String::trim)
                .filter { it.isNotBlank() && CARRIER_CONFIG_DIAGNOSTIC.containsMatchIn(it) }
                .map { line ->
                    SENSITIVE_FIELD.replace(LONG_IDENTIFIER.replace(line, "[redacted-id]")) { match ->
                        "${match.groupValues[1]}=[redacted]"
                    }
                }
                .distinct()
                .take(500)
                .joinToString("\n")
                .take(180_000)
                .ifBlank { "No matching active CarrierConfig evidence was exposed." }

        private fun extractPhysicalChannels(raw: String): String {
            val entries = Regex("\\{mConnectionStatus=[^}]+\\}")
                .findAll(raw)
                .map { it.value }
                .distinct()
                .toList()
            if (entries.isEmpty()) return "No active PhysicalChannelConfig entries were reported."
            fun field(entry: String, name: String): String =
                Regex("$name=([^,}]+)").find(entry)?.groupValues?.get(1)?.trim().orEmpty().ifBlank { "—" }
            return entries.joinToString("\n") { entry ->
                listOf(
                    "status=${field(entry, "mConnectionStatus")}",
                    "RAT=${field(entry, "mNetworkType")}",
                    "band=${field(entry, "mBand")}",
                    "PCI=${field(entry, "mPhysicalCellId")}",
                    "DL-channel=${field(entry, "mDownlinkChannelNumber")}",
                    "UL-channel=${field(entry, "mUplinkChannelNumber")}",
                    "DL-kHz=${field(entry, "mCellBandwidthDownlinkKhz")}",
                    "UL-kHz=${field(entry, "mCellBandwidthUplinkKhz")}",
                    "DL-frequency-kHz=${field(entry, "mDownlinkFrequency")}",
                    "UL-frequency-kHz=${field(entry, "mUplinkFrequency")}",
                ).joinToString(", ")
            }
        }
    }
}
