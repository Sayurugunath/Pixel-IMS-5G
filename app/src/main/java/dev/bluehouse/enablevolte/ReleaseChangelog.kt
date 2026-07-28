package dev.bluehouse.enablevolte

enum class ChangelogTone {
    FEATURE,
    IMPROVEMENT,
    FIX,
    IMPORTANT,
}

data class ChangelogItem(
    val title: String,
    val detail: String,
    val tone: ChangelogTone,
)

data class InstalledChangelog(
    val version: String,
    val items: List<ChangelogItem>,
)

object ReleaseChangelogCatalog {
    fun forVersion(version: String): InstalledChangelog? =
        when (version.removePrefix("v")) {
            "1.0.4" ->
                InstalledChangelog(
                    version = "1.0.4",
                    items =
                        listOf(
                            ChangelogItem(
                                title = "Beautiful post-update changelogs",
                                detail = "OTA updates now open a versioned liquid-glass What’s New screen. It preserves GitHub release notes across Android’s installer and can be reopened from About.",
                                tone = ChangelogTone.FEATURE,
                            ),
                            ChangelogItem(
                                title = "Premium interface redesign",
                                detail = "A calmer hierarchy, semantic status colors, refined typography, compact expert controls, glass surfaces and restrained motion now scale cleanly across Pixel displays.",
                                tone = ChangelogTone.IMPROVEMENT,
                            ),
                            ChangelogItem(
                                title = "Perfectly aligned navigation",
                                detail = "The bottom bar is now custom-built with equal cells, fixed centered icon slots and stable label baselines. The app header also carries the by Nirmala signature.",
                                tone = ChangelogTone.FIX,
                            ),
                            ChangelogItem(
                                title = "Safer mobile-only diagnostics",
                                detail = "Monitor and Field Test temporarily disable Wi-Fi, fail closed if isolation cannot be confirmed, and restore the previous Wi-Fi state when the session ends.",
                                tone = ChangelogTone.IMPORTANT,
                            ),
                            ChangelogItem(
                                title = "Deeper field reports",
                                detail = "Reports now identify the OS build, operational PLMN, evidence source, NR and EN-DC flag semantics, IMS/callback coverage, readable policy gates and sanitized root modem deltas.",
                                tone = ChangelogTone.IMPROVEMENT,
                            ),
                            ChangelogItem(
                                title = "Honest Shizuku guidance",
                                detail = "Every Pixel using Shizuku receives the regional 5G limitation notice on stock Pixel OS and supported alternative Pixel operating systems.",
                                tone = ChangelogTone.IMPORTANT,
                            ),
                        ),
                )
            else -> null
        }

    fun fromReleaseNotes(
        version: String,
        notes: String,
    ): InstalledChangelog? {
        val items =
            notes
                .lineSequence()
                .map(String::trim)
                .filter { it.startsWith("- ") || it.startsWith("* ") }
                .map { it.drop(2).trim() }
                .filter(String::isNotBlank)
                .map { line ->
                    val titleMatch = Regex("^\\*\\*(.+?)\\*\\*[:—-]?\\s*(.*)$").find(line)
                    val title = titleMatch?.groupValues?.get(1)?.trim()?.trimEnd(':', '—', '-')
                        ?: line.substringBefore(":").trim().take(72)
                    val detail = titleMatch?.groupValues?.get(2)?.trim().orEmpty()
                        .ifBlank {
                            line.substringAfter(":", missingDelimiterValue = line).trim()
                        }
                    ChangelogItem(
                        title = title,
                        detail = detail.takeIf { it != title }.orEmpty(),
                        tone = toneFor("$title $detail"),
                    )
                }
                .take(10)
                .toList()
        return items.takeIf(List<*>::isNotEmpty)?.let {
            InstalledChangelog(version.removePrefix("v"), it)
        }
    }

    private fun toneFor(value: String): ChangelogTone {
        val normalized = value.lowercase()
        return when {
            listOf("warning", "important", "root", "shizuku", "safety").any(normalized::contains) ->
                ChangelogTone.IMPORTANT
            listOf("fix", "crash", "restore", "correct").any(normalized::contains) ->
                ChangelogTone.FIX
            listOf("improve", "redesign", "refine", "report").any(normalized::contains) ->
                ChangelogTone.IMPROVEMENT
            else -> ChangelogTone.FEATURE
        }
    }
}
