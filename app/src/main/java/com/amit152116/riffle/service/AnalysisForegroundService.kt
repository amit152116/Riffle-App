package com.amit152116.riffle.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.amit152116.riffle.MainActivity
import com.amit152116.riffle.analysis.ContinuationScheduler
import com.amit152116.riffle.analysis.ForegroundAnalysisPolicy
import com.amit152116.riffle.analysis.ForegroundStep
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.log.RiffleLog
import com.amit152116.riffle.work.WorkManagerContinuationScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs library analysis as a user-visible foreground service so it keeps going with the screen off, where plain
 * WorkManager jobs are deferred by Doze/OEM battery managers (measured on the target phone: 7 idle hours produced
 * no progress).
 *
 * It only *loops the existing chunk runner* (`LibraryScanner.analyzeChunk`), so it shares the process-wide analysis
 * lock with `AnalysisWorker`: the two never run together, and whatever the service leaves behind (Stop, timeout,
 * crash of a chunk) is handed back to a WorkManager continuation.
 *
 * Must be started while the app is visible (Android 12+ forbids starting a foreground service from the background);
 * see [startIfPossible]. `dataSync` services are capped at 6 h per 24 h on Android 15+ ([onTimeout] stops cleanly).
 */
class AnalysisForegroundService : Service() {
    private val log = RiffleLog(this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            log.w("Stop requested from the notification")
            job?.cancel()
            return START_NOT_STICKY
        }
        // Must happen immediately: the system gives a started foreground service ~5 s to call startForeground().
        startInForeground(notification(ForegroundAnalysisPolicy.notificationText(0, 0), percent = null))
        if (job?.isActive != true) job = scope.launch { runLoop() }
        return START_NOT_STICKY
    }

    /** Android 15+: called when the `dataSync` time limit is reached; the service must stop within seconds. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        log.w("Foreground service timeout reached (fgsType=$fgsType); handing over to WorkManager")
        job?.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        scope.coroutineContext[Job]?.cancel()
        releaseWakeLock()
        running = false
        super.onDestroy()
    }

    private suspend fun runLoop() = coroutineScope {
        running = true
        val scanner = AppContainer.getInstance(applicationContext).libraryScanner
        val fallback = WorkManagerContinuationScheduler(applicationContext)
        // The service loops itself, so "enqueue the next chunk" is a no-op here; only the final intelligence
        // rebuild is scheduled. Leftover work is handed to WorkManager in the finally block below.
        val scheduler = object : ContinuationScheduler {
            override fun enqueueNextChunk(delaySeconds: Long) = Unit
            override fun enqueueIntelligence() = fallback.enqueueIntelligence()
        }
        acquireWakeLock()
        var startPending = 0
        var hashWaitPolls = 0
        try {
            while (true) {
                val pending = scanner.pendingAnalysisCount()
                startPending = maxOf(startPending, pending)
                val pendingHash = if (pending > 0) 0 else scanner.pendingHashCount()
                when (ForegroundAnalysisPolicy.next(pending, pendingHash, hashWaitPolls)) {
                    ForegroundStep.RUN_CHUNK -> {
                        hashWaitPolls = 0
                        update(startPending, pending)
                        // A chunk runs for minutes: refresh the notification while it does, not only between chunks.
                        val ticker = launch {
                            while (true) {
                                delay(PROGRESS_REFRESH_MS)
                                // a failed refresh must never take the running chunk down with it
                                runCatching { update(startPending, scanner.pendingAnalysisCount()) }
                            }
                        }
                        try {
                            if (scanner.analyzeChunk(scheduler) == null) delay(IDLE_POLL_MS) // a worker chunk holds the lock
                        } finally {
                            ticker.cancel()
                        }
                    }
                    ForegroundStep.WAIT_FOR_HASH -> {
                        hashWaitPolls++
                        update(startPending, pending)
                        delay(IDLE_POLL_MS)
                    }
                    ForegroundStep.FINISH -> break
                }
            }
            log.w("Foreground analysis finished")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e("Foreground analysis failed; falling back to WorkManager", e)
        } finally {
            withContext(NonCancellable) {
                releaseWakeLock()
                runCatching { if (scanner.pendingAnalysisCount() > 0) fallback.enqueueNextChunk() }
                    .onFailure { log.e("Could not hand over to WorkManager", it) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                running = false
                stopSelf()
            }
        }
    }

    private fun update(startPending: Int, pending: Int) {
        val text = ForegroundAnalysisPolicy.notificationText(startPending, pending)
        val percent = ForegroundAnalysisPolicy.progressPercent(startPending, pending)
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(text, percent))
    }

    private fun startInForeground(notification: Notification) {
        ensureChannel()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Library analysis", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows progress while Riffle analyses your music"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun notification(text: String, percent: Int?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AnalysisForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Riffle")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply { if (percent != null) setProgress(100, percent, false) else setProgress(100, 0, true) }
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "riffle:analysis")
            ?.apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS) // bounded: never held forever if the service is killed
            }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "analysis"
        private const val NOTIFICATION_ID = 4711
        private const val ACTION_STOP = "com.amit152116.riffle.action.STOP_ANALYSIS"
        private const val IDLE_POLL_MS = 5_000L
        private const val PROGRESS_REFRESH_MS = 8_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L

        @Volatile
        var running = false
            private set

        /**
         * Starts the service if the system allows it (the app must be visible, Android 12+). Returns false, and
         * changes nothing, when it cannot start: the WorkManager pipeline then carries on as before.
         */
        fun startIfPossible(context: Context): Boolean {
            if (running) return true
            return try {
                ContextCompat.startForegroundService(context, Intent(context, AnalysisForegroundService::class.java))
                true
            } catch (e: Exception) { // ForegroundServiceStartNotAllowedException and friends
                android.util.Log.w("AnalysisFgService", "Foreground analysis not started: ${e.javaClass.simpleName}")
                false
            }
        }
    }
}
