package sa.org.talmaarifa.tvsignage.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sa.org.talmaarifa.tvsignage.BuildConfig
import sa.org.talmaarifa.tvsignage.MainActivity
import sa.org.talmaarifa.tvsignage.R
import sa.org.talmaarifa.tvsignage.TvSignageApp
import sa.org.talmaarifa.tvsignage.core.HeartbeatPolicy
import sa.org.talmaarifa.tvsignage.core.PlaybackEventFactory
import sa.org.talmaarifa.tvsignage.core.RetrySchedule
import sa.org.talmaarifa.tvsignage.data.repo.SyncOutcome
import sa.org.talmaarifa.tvsignage.network.ApiResult
import sa.org.talmaarifa.tvsignage.network.isCredentialInvalid

/**
 * The always-on background worker (برنامج خلفي، Foreground Service): a tight heartbeat loop whose
 * cadence the *server* controls (docs/tv-device-api.md §3 — `poll_interval_seconds`), which triggers
 * a manifest sync whenever the server reports `publication_changed`. Playback itself never depends on
 * this service being alive at any given instant — [sa.org.talmaarifa.tvsignage.player] reads the last
 * atomically-activated publication straight from Room and keeps looping it with or without network.
 */
class SignageForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var loopStarted = false

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!loopStarted) {
            loopStarted = true
            scope.launch { heartbeatLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun heartbeatLoop() {
        val app = TvSignageApp.from(applicationContext)
        var failureAttempt = 0

        while (scope.isActive) {
            if (!app.credentialStore.isPaired()) {
                delay(5_000)
                continue
            }

            when (val heartbeat = app.syncRepository.heartbeat(appVersion = BuildConfig.VERSION_NAME, lastError = null)) {
                is ApiResult.Success -> {
                    failureAttempt = 0
                    val activeBefore = app.database.activePublicationDao().getOnce()
                    val shouldRefetch = HeartbeatPolicy.shouldRefetchManifest(
                        publicationChanged = heartbeat.value.publicationChanged,
                        hasAnyLocalPublication = activeBefore != null,
                    )
                    if (shouldRefetch) {
                        val outcome = app.syncRepository.syncManifest()
                        if (outcome is SyncOutcome.CredentialInvalid) {
                            app.credentialStore.clear()
                            delay(5_000)
                            continue
                        }
                    }
                    app.eventLogRepository.flushPending()
                    delay(HeartbeatPolicy.clampIntervalSeconds(heartbeat.value.pollIntervalSeconds) * 1_000L)
                }

                is ApiResult.ApiError -> {
                    if (heartbeat.isCredentialInvalid()) {
                        app.credentialStore.clear()
                        delay(5_000)
                        continue
                    }
                    // e.g. 409 MODULE_DISABLED: a known, admin-controlled pause. Keep showing whatever
                    // is already active locally and back off before trying the heartbeat again.
                    delay(RetrySchedule.delaySeconds(failureAttempt++) * 1_000L)
                }

                is ApiResult.NetworkError -> {
                    // Offline: the player keeps looping the last verified publication untouched. Log
                    // one best-effort "still running" estimate for this outage window (confirmed=false,
                    // docs/tv-device-api.md's own example of an unconfirmed event) and back off.
                    val activeId = app.database.activePublicationDao().getOnce()?.publication?.publicationId
                    app.eventLogRepository.record(PlaybackEventFactory.contentContinuedOffline(activeId, System.currentTimeMillis()))
                    delay(RetrySchedule.delaySeconds(failureAttempt++) * 1_000L)
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.signage_service_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.signage_service_title))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "signage_service"
        private const val NOTIFICATION_ID = 1

        fun start(context: android.content.Context) {
            context.startForegroundService(Intent(context, SignageForegroundService::class.java))
        }
    }
}
