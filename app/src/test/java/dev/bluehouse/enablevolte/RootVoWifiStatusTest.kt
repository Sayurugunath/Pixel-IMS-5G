package dev.bluehouse.enablevolte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootVoWifiStatusTest {
    @Test
    fun parsesActiveIwlanStatus() {
        val status = RootVoWifiStatus.fromJson(
            """
            {
              "available": true,
              "subscriptionId": 2,
              "settingEnabled": true,
              "roamingEnabled": true,
              "mode": 2,
              "roamingMode": 2,
              "registrationState": 2,
              "transportType": 2,
              "wifiState": 3,
              "snapshotAvailable": true,
              "operationSucceeded": true,
              "failureReason": "",
              "message": "ready"
            }
            """.trimIndent(),
        )

        assertTrue(status.isVoWifiActive)
        assertEquals("Wi-Fi preferred", status.modeLabel)
        assertEquals("IWLAN (VoWiFi)", status.transportLabel)
    }

    @Test
    fun preservesRootFailureDiagnosis() {
        val status = RootVoWifiStatus.fromJson(
            """
            {
              "subscriptionId": 1,
              "failureReason": "IWLAN_IKE_INIT_TIMEOUT",
              "message": "not active"
            }
            """.trimIndent(),
        )

        assertEquals("IWLAN_IKE_INIT_TIMEOUT", status.failureReason)
        assertEquals("Unknown", status.modeLabel)
    }
}
