package dev.bluehouse.enablevolte

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal object RootCarrierConfigPersistence {
    fun reapplyAll(context: Context): Boolean {
        if (PrivilegeManager.activeMode != PrivilegeMode.ROOT || !PrivilegeManager.isRootReady()) return false
        val carrierModer = CarrierModer(context)
        return carrierModer.subscriptions
            .filter { RootCarrierConfigStore(context).hasProfile(it.subscriptionId) }
            .all { SubscriptionModer(context, it.subscriptionId).reapplyPersistedRootCarrierConfig() }
    }

    fun schedule(context: Context, delaySeconds: Long) {
        if (PrivilegeManager.selectedMode(context) != PrivilegeMode.ROOT) return
        val request = OneTimeWorkRequestBuilder<RootCarrierConfigPersistenceWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private const val WORK_NAME = "root_carrier_config_persistence"
}

class RootCarrierConfigPersistenceWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        if (PrivilegeManager.selectedMode(applicationContext) != PrivilegeMode.ROOT) return Result.success()
        PrivilegeManager.activate(applicationContext, PrivilegeMode.ROOT)

        val completed = CountDownLatch(1)
        val applied = AtomicBoolean(false)
        PrivilegeManager.connectRoot(applicationContext, reapplyPersistedConfig = false) { ready, _ ->
            if (ready) applied.set(RootCarrierConfigPersistence.reapplyAll(applicationContext))
            completed.countDown()
        }
        if (!completed.await(25, TimeUnit.SECONDS)) return Result.retry()
        return if (applied.get()) Result.success() else if (runAttemptCount < 2) Result.retry() else Result.failure()
    }
}

class RootCarrierConfigBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val delay = if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) 3L else 20L
        RootCarrierConfigPersistence.schedule(context.applicationContext, delay)
    }
}
