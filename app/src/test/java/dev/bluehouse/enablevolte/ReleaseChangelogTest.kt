package dev.bluehouse.enablevolte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseChangelogTest {
    @Test
    fun currentReleaseHasStructuredHighlightedItems() {
        val changelog = ReleaseChangelogCatalog.forVersion("v1.0.4r")

        assertNotNull(changelog)
        assertEquals("1.0.4 rev", changelog?.version)
        assertTrue(changelog.orEmptyItems().any { it.title == "How to enable 5G" })
    }

    @Test
    fun githubMarkdownBulletsBecomeCards() {
        val changelog =
            ReleaseChangelogCatalog.fromReleaseNotes(
                "1.1.0",
                """
                ## Pixel IMS 5G 1.1.0
                - **New monitor:** Adds a live modem timeline.
                - **Crash fix:** Corrects Pixel 6 band selection.
                """.trimIndent(),
            )

        assertEquals(2, changelog?.items?.size)
        assertEquals("New monitor", changelog?.items?.first()?.title)
        assertEquals(ChangelogTone.FIX, changelog?.items?.last()?.tone)
    }

    private fun InstalledChangelog?.orEmptyItems(): List<ChangelogItem> = this?.items.orEmpty()
}
