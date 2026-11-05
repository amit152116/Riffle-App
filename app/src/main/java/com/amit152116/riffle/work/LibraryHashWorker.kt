package com.amit152116.riffle.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.log.RiffleLog

/**
 * Runs LibraryScanner.hashPendingTracks() as a WorkManager job instead of a
 * viewModelScope.launch. The hash pass can take minutes on a real library
 * (~29 min for ~800 tracks, timed in an earlier session) - tied to the
 * ViewModel's lifecycle, backgrounding the app or the ViewModel clearing
 * killed it silently mid-hash, with no record and no retry. WorkManager
 * survives both.
 */
class LibraryHashWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val log = RiffleLog(this)

    override suspend fun doWork(): Result {
        return try {
            val container = AppContainer.getInstance(applicationContext)
            val result = container.libraryScanner.hashPendingTracks()
            log.w("Hash pass finished: hashed=${result.hashed} failed=${result.failed}")
            Result.success()
        } catch (e: Exception) {
            // hashPendingTracks() already catches per-track failures (writes
            // an IngestError, keeps going) - reaching here means something
            // broader broke (e.g. the DB itself), worth a retry.
            log.e("Hash pass failed unexpectedly", e)
            Result.retry()
        }
    }
}
