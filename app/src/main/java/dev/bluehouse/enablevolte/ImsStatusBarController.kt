package dev.bluehouse.enablevolte

import android.content.Context

object ImsStatusBarController {
    private const val PREFS = "ims_status_indicator"
    private const val ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, true)

    fun setEnabled(
        context: Context,
        enabled: Boolean,
        subscriptionIds: IntArray,
    ): Boolean {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply()
        return apply(enabled, subscriptionIds)
    }

    fun apply(
        enabled: Boolean,
        subscriptionIds: IntArray,
    ): Boolean =
        PrivilegeManager.setRootImsStatusBarMonitoring(enabled, subscriptionIds)
}
