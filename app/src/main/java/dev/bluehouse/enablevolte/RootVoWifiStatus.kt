package dev.bluehouse.enablevolte

import org.json.JSONObject

data class RootVoWifiStatus(
    val available: Boolean,
    val subscriptionId: Int,
    val settingEnabled: Boolean,
    val roamingEnabled: Boolean,
    val mode: Int,
    val roamingMode: Int,
    val registrationState: Int,
    val transportType: Int,
    val wifiState: Int,
    val snapshotAvailable: Boolean,
    val operationSucceeded: Boolean,
    val failureReason: String,
    val message: String,
) {
    val modeLabel: String
        get() = when (mode) {
            0 -> "Wi-Fi only"
            1 -> "Cellular preferred"
            2 -> "Wi-Fi preferred"
            else -> "Unknown"
        }

    val roamingModeLabel: String
        get() = when (roamingMode) {
            0 -> "Wi-Fi only"
            1 -> "Cellular preferred"
            2 -> "Wi-Fi preferred"
            else -> "Unknown"
        }

    val registrationLabel: String
        get() = when (registrationState) {
            2 -> "Registered"
            1 -> "Registering"
            0 -> "Not registered"
            else -> "Unknown"
        }

    val transportLabel: String
        get() = when (transportType) {
            2 -> "IWLAN (VoWiFi)"
            1 -> "Cellular"
            else -> "Unknown"
        }

    val isVoWifiActive: Boolean
        get() = registrationState == 2 && transportType == 2

    companion object {
        fun fromJson(value: String): RootVoWifiStatus {
            val json = JSONObject(value)
            return RootVoWifiStatus(
                available = json.optBoolean("available"),
                subscriptionId = json.optInt("subscriptionId", -1),
                settingEnabled = json.optBoolean("settingEnabled"),
                roamingEnabled = json.optBoolean("roamingEnabled"),
                mode = json.optInt("mode", -1),
                roamingMode = json.optInt("roamingMode", -1),
                registrationState = json.optInt("registrationState", -1),
                transportType = json.optInt("transportType", -1),
                wifiState = json.optInt("wifiState", 0),
                snapshotAvailable = json.optBoolean("snapshotAvailable"),
                operationSucceeded = json.optBoolean("operationSucceeded"),
                failureReason = json.optString("failureReason"),
                message = json.optString("message"),
            )
        }

        fun unavailable(subscriptionId: Int, message: String) = RootVoWifiStatus(
            available = false,
            subscriptionId = subscriptionId,
            settingEnabled = false,
            roamingEnabled = false,
            mode = -1,
            roamingMode = -1,
            registrationState = -1,
            transportType = -1,
            wifiState = 0,
            snapshotAvailable = false,
            operationSucceeded = false,
            failureReason = "",
            message = message,
        )
    }
}
