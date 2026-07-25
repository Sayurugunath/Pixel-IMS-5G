package dev.bluehouse.enablevolte

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import rikka.shizuku.ShizukuBinderWrapper

enum class PrivilegeMode {
    SHIZUKU,
    ROOT,
}

object PrivilegeManager {
    private const val TAG = "PrivilegeManager"
    private const val PREFS = "pixel_ims_privilege"
    private const val MODE = "mode"

    @Volatile
    private var rootBridge: IPrivilegedService? = null
    private var rootConnection: ServiceConnection? = null

    @Volatile
    var activeMode: PrivilegeMode = PrivilegeMode.SHIZUKU
        private set

    fun selectedMode(context: Context): PrivilegeMode? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(MODE, null)
            ?.let { runCatching { PrivilegeMode.valueOf(it) }.getOrNull() }

    fun activate(context: Context, mode: PrivilegeMode) {
        activeMode = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(MODE, mode.name).apply()
        InterfaceCache.cache.clear()
    }

    fun clearMode(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(MODE).apply()
        disconnectRoot()
        InterfaceCache.cache.clear()
    }

    fun isRootReady(): Boolean = runCatching { rootBridge?.serviceUid == 0 }.getOrDefault(false)

    fun getRootSystemProperty(name: String): String? =
        if (activeMode == PrivilegeMode.ROOT && isRootReady()) {
            runCatching { rootBridge?.getAllowedSystemProperty(name) }.getOrNull()
        } else {
            null
        }

    fun setRootSystemProperty(name: String, value: String): Boolean =
        activeMode == PrivilegeMode.ROOT &&
            isRootReady() &&
            runCatching { rootBridge?.setAllowedSystemProperty(name, value) == true }.getOrDefault(false)

    fun getRootTelephonyDiagnostic(kind: String): String? {
        if (activeMode != PrivilegeMode.ROOT || !isRootReady()) return null
        return try {
            rootBridge?.getTelephonyDiagnosticSnapshot(kind)
        } catch (error: Throwable) {
            Log.w(TAG, "Root diagnostic '$kind' failed", error)
            rootBridge = null
            rootConnection = null
            InterfaceCache.cache.clear()
            null
        }
    }

    fun connectRoot(context: Context, result: (Boolean, String?) -> Unit) {
        if (isRootReady()) {
            result(true, null)
            return
        }
        if (rootConnection != null) {
            result(false, "Root permission request is already running")
            return
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                rootBridge = IPrivilegedService.Stub.asInterface(service)
                val ready = isRootReady()
                Log.i(TAG, "Root service connected: component=$name ready=$ready")
                if (!ready) rootBridge = null
                result(ready, if (ready) null else "The root service did not start as UID 0")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.w(TAG, "Root service disconnected: $name")
                rootBridge = null
                rootConnection = null
                InterfaceCache.cache.clear()
            }

            override fun onNullBinding(name: ComponentName) {
                rootBridge = null
                rootConnection = null
                result(false, "Root access was denied or no supported su manager is installed")
            }

            override fun onBindingDied(name: ComponentName) {
                Log.w(TAG, "Root service binding died: $name")
                rootBridge = null
                rootConnection = null
                InterfaceCache.cache.clear()
            }
        }
        rootConnection = connection
        runCatching {
            val intent = Intent(context, PrivilegedService::class.java)
            // A daemon-mode root service can survive an APK update with the previous AIDL/code.
            // Stop that orphan before binding so a newly installed app always executes matching code.
            RootService.stop(intent)
            RootService.bind(intent, context.mainExecutor, connection)
        }.onFailure {
            Log.w(TAG, "Unable to bind root service", it)
            rootConnection = null
            result(false, it.message ?: "Unable to request root access")
        }
    }

    fun disconnectRoot() {
        rootConnection?.let { runCatching { RootService.unbind(it) } }
        rootConnection = null
        rootBridge = null
    }

    fun wrapService(name: String, directBinder: IBinder): IBinder =
        when (activeMode) {
            PrivilegeMode.ROOT ->
                rootBridge?.getSystemService(name)
                    ?: error("Root service is not connected")
            PrivilegeMode.SHIZUKU -> ShizukuBinderWrapper(directBinder)
        }
}
