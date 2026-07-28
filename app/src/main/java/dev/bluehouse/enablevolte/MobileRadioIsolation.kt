package dev.bluehouse.enablevolte

import android.net.wifi.WifiManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class MobileRadioIsolationResult(
    val session: MobileRadioIsolationSession?,
    val message: String,
) {
    val active: Boolean
        get() = session != null
}

class MobileRadioIsolationSession internal constructor(
    private val restoreWifi: Boolean,
) {
    @Volatile
    private var restored = false

    fun restore(): Boolean {
        if (restored) return true
        if (!restoreWifi) {
            restored = true
            return true
        }
        val accepted = runCatching {
            PrivilegeManager.setPrivilegedWifiEnabled(true)
        }.getOrDefault(false)
        if (accepted) restored = true
        return accepted
    }
}

object MobileRadioIsolation {
    private const val INITIAL_STATE_ATTEMPTS = 5
    private const val INITIAL_STATE_WAIT_MS = 200L
    private const val STATE_WAIT_ATTEMPTS = 30
    private const val STATE_WAIT_MS = 150L

    suspend fun begin(): MobileRadioIsolationResult {
        var initialState = WifiManager.WIFI_STATE_UNKNOWN
        var stateFailure: Throwable? = null
        for (attempt in 0 until INITIAL_STATE_ATTEMPTS) {
            runCatching {
                PrivilegeManager.getPrivilegedWifiState()
            }.onSuccess {
                initialState = it
                stateFailure = null
            }.onFailure {
                stateFailure = it
            }
            if (initialState != WifiManager.WIFI_STATE_UNKNOWN) break
            if (attempt < INITIAL_STATE_ATTEMPTS - 1) delay(INITIAL_STATE_WAIT_MS)
        }
        if (initialState == WifiManager.WIFI_STATE_UNKNOWN && stateFailure != null) {
            return MobileRadioIsolationResult(
                session = null,
                message = stateFailure?.message
                    ?: "Unable to read Wi-Fi state through the selected privilege backend.",
            )
        }
        val initiallyEnabled =
            initialState == WifiManager.WIFI_STATE_ENABLED ||
                initialState == WifiManager.WIFI_STATE_ENABLING
        if (initiallyEnabled) {
            val accepted = runCatching {
                PrivilegeManager.setPrivilegedWifiEnabled(false)
            }.getOrDefault(false)
            if (!accepted) {
                return MobileRadioIsolationResult(
                    session = null,
                    message = "Android rejected the privileged Wi-Fi disable request.",
                )
            }
            try {
                repeat(STATE_WAIT_ATTEMPTS) {
                    val state = runCatching {
                        PrivilegeManager.getPrivilegedWifiState()
                    }.getOrNull()
                    if (state == WifiManager.WIFI_STATE_DISABLED) {
                        return MobileRadioIsolationResult(
                            session = MobileRadioIsolationSession(restoreWifi = true),
                            message = "Wi-Fi disabled; monitoring is using the mobile-radio path.",
                        )
                    }
                    delay(STATE_WAIT_MS)
                }
            } catch (cancelled: CancellationException) {
                runCatching { PrivilegeManager.setPrivilegedWifiEnabled(true) }
                throw cancelled
            }
            runCatching { PrivilegeManager.setPrivilegedWifiEnabled(true) }
            return MobileRadioIsolationResult(
                session = null,
                message = "Wi-Fi did not reach the disabled state. The mobile-only session was not started.",
            )
        }
        return if (initialState == WifiManager.WIFI_STATE_DISABLED) {
            MobileRadioIsolationResult(
                session = MobileRadioIsolationSession(restoreWifi = false),
                message = "Wi-Fi was already disabled; monitoring is using the mobile-radio path.",
            )
        } else {
            MobileRadioIsolationResult(
                session = null,
                message = "Android reported an unknown Wi-Fi state. The mobile-only session was not started.",
            )
        }
    }
}
