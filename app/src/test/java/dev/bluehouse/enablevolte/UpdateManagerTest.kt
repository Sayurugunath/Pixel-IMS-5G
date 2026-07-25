package dev.bluehouse.enablevolte

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {
    @Test
    fun letterSuffixedReleaseUsesNumericVersionOrder() {
        assertTrue(UpdateManager.isNewer("0.12.10F", "0.12.6"))
        assertFalse(UpdateManager.isNewer("0.12.6", "0.12.10F"))
        assertFalse(UpdateManager.isNewer("v0.12.10F", "0.12.10F"))
        assertTrue(UpdateManager.isNewer("0.12.11", "0.12.10F"))
    }
}
