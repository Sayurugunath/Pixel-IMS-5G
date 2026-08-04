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
        assertTrue(UpdateManager.isNewer("1.0.4r", "1.0.4"))
        assertFalse(UpdateManager.isNewer("1.0.4", "1.0.4r"))
        assertFalse(UpdateManager.isNewer("v1.0.4r", "1.0.4r"))
        assertTrue(UpdateManager.isNewer("1.0.5", "1.0.4r"))
        assertFalse(UpdateManager.isNewer("1.0.4r", "1.0.5"))
        assertTrue(UpdateManager.isNewer("1.0.6", "1.0.5"))
        assertFalse(UpdateManager.isNewer("1.0.5", "1.0.6"))
    }
}
