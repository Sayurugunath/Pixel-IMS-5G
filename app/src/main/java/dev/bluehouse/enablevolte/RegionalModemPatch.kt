package dev.bluehouse.enablevolte

import org.json.JSONObject

data class RegionalModemPatchStatus(
    val supported: Boolean,
    val magiskAvailable: Boolean,
    val sourceAvailable: Boolean,
    val installed: Boolean,
    val removalPending: Boolean,
    val rebootRequired: Boolean,
    val device: String,
    val sourceSha256: String,
    val patchedSha256: String,
    val message: String,
) {
    companion object {
        fun fromJson(value: String): RegionalModemPatchStatus {
            val json = JSONObject(value)
            return RegionalModemPatchStatus(
                supported = json.optBoolean("supported"),
                magiskAvailable = json.optBoolean("magiskAvailable"),
                sourceAvailable = json.optBoolean("sourceAvailable"),
                installed = json.optBoolean("installed"),
                removalPending = json.optBoolean("removalPending"),
                rebootRequired = json.optBoolean("rebootRequired"),
                device = json.optString("device"),
                sourceSha256 = json.optString("sourceSha256"),
                patchedSha256 = json.optString("patchedSha256"),
                message = json.optString("message"),
            )
        }

        fun unavailable(message: String) = RegionalModemPatchStatus(
            supported = false,
            magiskAvailable = false,
            sourceAvailable = false,
            installed = false,
            removalPending = false,
            rebootRequired = false,
            device = "",
            sourceSha256 = "",
            patchedSha256 = "",
            message = message,
        )
    }
}
