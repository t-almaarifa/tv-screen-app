package sa.org.talmaarifa.tvsignage.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import sa.org.talmaarifa.tvsignage.TvSignageApp
import sa.org.talmaarifa.tvsignage.data.repo.SyncOutcome
import java.util.concurrent.TimeUnit

/**
 * Periodic safety-net sync (the brief's "WorkManager للمزامنة الدورية"). The *primary*, fast-cadence
 * trigger is [sa.org.talmaarifa.tvsignage.service.SignageForegroundService]'s heartbeat loop, which
 * reacts to `publication_changed` immediately; this worker exists so the device still converges on
 * the right content — and still flushes the local playback log — if that service's process was ever
 * killed and not yet restarted. WorkManager's own minimum periodic interval (15 minutes) applies.
 */
class ManifestSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!TvSignageApp.from(applicationContext).credentialStore.isPaired()) return Result.success()
        val app = TvSignageApp.from(applicationContext)

        app.eventLogRepository.flushPending()

        return when (app.syncRepository.syncManifest()) {
            is SyncOutcome.CredentialInvalid -> {
                app.credentialStore.clear()
                Result.success() // not retryable until re-paired; app UI will notice on next launch
            }
            is SyncOutcome.Failed -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "manifest-sync-periodic"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<ManifestSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
