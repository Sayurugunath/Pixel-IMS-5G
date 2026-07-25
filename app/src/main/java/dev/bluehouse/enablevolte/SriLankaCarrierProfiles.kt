package dev.bluehouse.enablevolte

data class SriLankaCarrierProfile(
    val operatorNumeric: String,
    val name: String,
    val lteBands: List<Int>,
    val nrBands: List<Int>,
    val wifiCallingNote: String,
)

object SriLankaCarrierProfiles {
    private val countryLteBands = listOf(1, 3, 5, 8, 40, 41)

    private val profiles = mapOf(
        "41301" to SriLankaCarrierProfile(
            "41301",
            "SLT-MOBITEL",
            countryLteBands,
            listOf(78),
            "VoWiFi still requires SLT-MOBITEL provisioning for this SIM and device.",
        ),
        "41302" to SriLankaCarrierProfile(
            "41302",
            "Dialog",
            countryLteBands,
            listOf(78),
            "Dialog customers can check/activate Wi-Fi Calling with #107# → option 2. Carrier provisioning is still required.",
        ),
        "41305" to SriLankaCarrierProfile(
            "41305",
            "Airtel Lanka SIM",
            countryLteBands,
            emptyList(),
            "No public carrier profile is assumed. Confirm IMS and 5G provisioning with Dialog/Airtel support.",
        ),
        "41308" to SriLankaCarrierProfile(
            "41308",
            "Hutch",
            countryLteBands,
            emptyList(),
            "No public VoWiFi or 5G entitlement is assumed; confirm provisioning with Hutch.",
        ),
    )

    fun find(mcc: String?, mnc: String?): SriLankaCarrierProfile? {
        if (mcc.isNullOrBlank() || mnc.isNullOrBlank()) return null
        return profiles[mcc + mnc.padStart(2, '0')]
    }
}
