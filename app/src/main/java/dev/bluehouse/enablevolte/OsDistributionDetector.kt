package dev.bluehouse.enablevolte

import android.content.Context
import android.os.Build

data class OsDistribution(
    val name: String,
    val evidence: String,
    val isAlternativePixelOs: Boolean,
)

object OsDistributionDetector {
    private val graphenePackages = listOf(
        "app.grapheneos.apps",
        "app.vanadium.browser",
        "app.vanadium.webview",
    )

    private val alternativeMarkers = linkedMapOf(
        "graphene" to "GrapheneOS",
        "lineage" to "LineageOS",
        "calyx" to "CalyxOS",
        "crdroid" to "crDroid",
        "evolution" to "Evolution X",
    )

    @Suppress("DEPRECATION")
    fun detect(context: Context): OsDistribution {
        val installedGraphenePackages = graphenePackages.filter { packageName ->
            runCatching {
                context.packageManager.getPackageInfo(packageName, 0)
            }.isSuccess
        }
        val incremental = Build.VERSION.INCREMENTAL.orEmpty()
        val grapheneStyleBuildNumber = Regex("""20\d{8}""").matches(incremental)
        val buildEvidence = listOf(
            Build.FINGERPRINT,
            Build.DISPLAY,
            Build.ID,
            Build.VERSION.INCREMENTAL,
        ).joinToString(" ").lowercase()
        val markedDistribution = alternativeMarkers.entries.firstOrNull { (marker, _) ->
            marker in buildEvidence
        }?.value

        return when {
            installedGraphenePackages.isNotEmpty() && grapheneStyleBuildNumber ->
                OsDistribution(
                    name = "GrapheneOS",
                    evidence = "GrapheneOS system package(s)=${installedGraphenePackages.joinToString()}; " +
                        "release-style build=$incremental",
                    isAlternativePixelOs = true,
                )
            markedDistribution != null ->
                OsDistribution(
                    name = markedDistribution,
                    evidence = "distribution marker found in build metadata; build=$incremental",
                    isAlternativePixelOs = true,
                )
            installedGraphenePackages.isNotEmpty() ->
                OsDistribution(
                    name = "GrapheneOS-derived or GrapheneOS apps installed",
                    evidence = "package(s)=${installedGraphenePackages.joinToString()}; build=$incremental",
                    isAlternativePixelOs = true,
                )
            grapheneStyleBuildNumber ->
                OsDistribution(
                    name = "GrapheneOS-style build number (not package-verified)",
                    evidence = "incremental build=$incremental; distribution package evidence unavailable",
                    isAlternativePixelOs = true,
                )
            Build.MANUFACTURER.equals("Google", ignoreCase = true) &&
                Build.FINGERPRINT.startsWith("google/") ->
                OsDistribution(
                    name = "Google Pixel stock/AOSP-derived build",
                    evidence = "Google Pixel build metadata; incremental build=${incremental.ifBlank { "unknown" }}",
                    isAlternativePixelOs = false,
                )
            else ->
                OsDistribution(
                    name = "AOSP/custom distribution not positively identified",
                    evidence = "incremental build=${incremental.ifBlank { "unknown" }}",
                    isAlternativePixelOs = true,
                )
        }
    }
}
