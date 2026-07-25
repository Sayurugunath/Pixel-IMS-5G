package dev.bluehouse.enablevolte

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.ServiceManager
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
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
                if (kind == "physical") {
                    extractPhysicalChannels(result.output)
                } else {
                    sanitizeDiagnostic(result.output)
                }
            }.getOrElse {
                Log.w(TAG, "Diagnostic parser failed for $kind", it)
                "Diagnostic parser failed: ${it.javaClass.simpleName}"
            }
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

        private val DIAGNOSTIC_COMMANDS = mapOf(
            "registry" to arrayOf("/system/bin/dumpsys", "telephony.registry"),
            "physical" to arrayOf("/system/bin/dumpsys", "telephony.registry"),
            "phone" to arrayOf("/system/bin/dumpsys", "activity", "service", "com.android.phone"),
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
