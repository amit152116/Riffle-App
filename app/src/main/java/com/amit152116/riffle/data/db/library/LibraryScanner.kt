package com.amit152116.riffle.data.db.library

import android.app.ActivityManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.amit152116.riffle.analysis.AnalysisParallelism
import com.amit152116.riffle.analysis.AnalysisRunner
import com.amit152116.riffle.analysis.ChunkResult
import com.amit152116.riffle.analysis.ContinuationScheduler
import com.amit152116.riffle.analysis.TrackAnalyzer
import com.amit152116.riffle.data.db.AbsentReason
import com.amit152116.riffle.data.db.RunStatus
import com.amit152116.riffle.data.db.analysis.AnalysisResultWriter
import com.amit152116.riffle.data.db.analysis.toFingerprint
import com.amit152116.riffle.data.db.analysis.toRoomAudioFeatures
import com.amit152116.riffle.dsp.EssentiaExtractor
import com.amit152116.riffle.log.RiffleLog
import com.amit152116.riffle.permission.MediaPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MissingMediaPermissionException :
    Exception("${MediaPermission.readAudio} not granted -- cannot query MediaStore")

data class ScanResult(val scanned: Int, val failed: Int)
data class HashResult(val hashed: Int, val failed: Int)
data class AnalysisResult(val analyzed: Int, val failed: Int)

/**
 * Runs [block], catching everything except [CancellationException] (rethrown so
 * WorkManager/structured-concurrency cancellation isn't swallowed by a broad catch) into a
 * [Result] instead of letting it propagate. Catches [Throwable], not just [Exception]: a
 * long/unsupported track can make native decode allocate enough to throw [OutOfMemoryError],
 * which is an [Error], not an [Exception] - a bare `catch (e: Exception)` around per-item work
 * in a loop over an entire library would let that escape uncaught and crash the whole pass
 * instead of failing just that one item.
 */
internal suspend fun <T> runCatchingNonFatal(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

private data class MediaStoreAudioMeta(
    val title: String?,
    val artist: String?,
    val album: String?,
    val genre: String?,
    val durationMs: Long,
    val size: Long,
    val mtime: Long,
    val bitrate: Int?,
    val albumId: Long?,
    val dateAdded: Long?,
    val displayName: String?,
    val relativePath: String?
)

/**
 * Discovers audio via MediaStore and syncs Track/AudioContent, playing the role
 * ../riffle/riffle/scan.py plays for the desktop tool: identify content by audio hash
 * (dedup-aware -- multiple Track rows can share one AudioContent), and never delete a
 * Track that disappears, only mark it absent (matches the "never destructive by default"
 * rule AGENTS.md calls out for this app).
 *
 * Split into two independent passes so the library is usable immediately instead of
 * blocking on a full-library audio hash:
 *  - [scan] is the fast pass: a plain MediaStore cursor, upserts every Track's metadata,
 *    no hashing. This is what should run on launch/permission-grant - the UI's Room-backed
 *    query reflects it as soon as it lands.
 *  - [hashPendingTracks] is the slow pass: hashes only rows with no AudioContent yet
 *    (audioContentId IS NULL), run separately in the background. A track can be played,
 *    queued, searched etc. before it's hashed - see PlaybackEvent.audioContentId nullability.
 *
 * Deliberately knows nothing about who calls it -- a plain suspend fun so a manual button,
 * app startup, or a future WorkManager job can all call the same way without this class or
 * the database layer changing.
 */
class LibraryScanner(
    private val context: Context,
    private val repository: LibraryRepository,
    // Lazy: constructing an EssentiaExtractor triggers a native library load - deferred until
    // analyzePendingAudioContent() actually needs it, so scan()/hashPendingTracks() callers
    // never pay for it. See AppContainer.essentiaExtractor's kdoc.
    private val extractor: Lazy<EssentiaExtractor>,
    private val resultWriter: AnalysisResultWriter
) {
    private val log = RiffleLog(this)

    private companion object {
        val scanLock = Mutex()      // scan() and hashPendingTracks() never overlap (process-wide)
        val analysisLock = Mutex()  // one analysis chunk at a time
    }

    /** Builds the chunk runner over Room; see [analyzeChunk]. */
    internal fun analysisRunner(scheduler: ContinuationScheduler): AnalysisRunner {
        val source = RoomAnalysisWorkSource(repository, EssentiaExtractor.EXTRACTOR_VERSION, extractor.value.configHash())
        val analyzer = RoomRecordingAnalyzer(context, repository, source, TrackAnalyzer(extractor.value), resultWriter)
        return AnalysisRunner(source, analyzer, scheduler, parallelism = ::currentParallelism)
    }

    /** Slots for this chunk from the device's state: cores, charging, and whether the app is in the foreground. */
    private fun currentParallelism(): Int {
        val charging = context.getSystemService(BatteryManager::class.java)?.isCharging == true
        val state = ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }
        val foreground = state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        val heapMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return AnalysisParallelism.choose(Runtime.getRuntime().availableProcessors(), charging, foreground, heapMb)
    }

    /** Recordings still worth analysing (attempt-capped ones excluded); drives progress UI and the foreground service. */
    suspend fun pendingAnalysisCount(): Int =
        repository.getPendingAnalysis(EssentiaExtractor.EXTRACTOR_VERSION, extractor.value.configHash(), MAX_ANALYSIS_ATTEMPTS, Int.MAX_VALUE).size

    /** Tracks the hash pass has not reached yet (they cannot be analysed before they are hashed). */
    suspend fun pendingHashCount(): Int = repository.getTracksNeedingHash().size

    /** Runs one chunk unless another is already running (that one keeps the queue moving); null = skipped. */
    suspend fun analyzeChunk(scheduler: ContinuationScheduler): ChunkResult? {
        if (!analysisLock.tryLock()) return null
        try {
            return analysisRunner(scheduler).runChunkAndSchedule()
        } finally {
            analysisLock.unlock()
        }
    }

    suspend fun scan(): ScanResult = scanLock.withLock { scanLocked() }

    private suspend fun scanLocked(): ScanResult = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, MediaPermission.readAudio) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Without this, MediaStore.query() silently returns zero rows instead of
            // throwing, which looks identical to "device genuinely has no music" -- that
            // ambiguity is exactly what a caller needs to NOT see.
            throw MissingMediaPermissionException()
        }

        val scanRunId = repository.insertScanRun(
            ScanRun(
                startedAt = System.currentTimeMillis(),
                completedAt = null,
                roots = "mediastore",
                status = RunStatus.RUNNING
            )
        )

        var scanned = 0
        var failed = 0
        try {
            forEachMediaStoreAudioFile { uri, meta ->
                try {
                    syncTrackMetadata(uri, meta, scanRunId)
                    scanned++
                } catch (e: Exception) {
                    failed++
                    log.w("Failed to scan $uri", e)
                    repository.insertIngestError(
                        IngestError(
                            path = uri.toString(),
                            dev = null,
                            inode = null,
                            size = meta.size,
                            mtime = meta.mtime.toDouble(),
                            stage = "scan",
                            message = e.message,
                            attempts = 1,
                            lastAttemptAt = System.currentTimeMillis(),
                            resolvedAt = null
                        )
                    )
                }
            }
            repository.markTracksAbsentNotInScan(scanRunId, AbsentReason.MISSING)
            repository.completeScanRun(scanRunId, System.currentTimeMillis(), RunStatus.COMPLETE)
        } catch (e: Exception) {
            repository.completeScanRun(scanRunId, System.currentTimeMillis(), RunStatus.FAILED)
            throw e
        }
        ScanResult(scanned, failed)
    }

    /** Fast pass: MediaStore metadata only, no hashing. See class kdoc. */
    private suspend fun syncTrackMetadata(uri: Uri, meta: MediaStoreAudioMeta, scanRunId: Long) {
        val path = uri.toString()
        val existingTrack = repository.getTrackByPath(path)
        val track = Track(
            id = existingTrack?.id ?: 0,
            path = path,
            size = meta.size,
            mtime = meta.mtime.toDouble(),
            // Carried over, never reset here - only hashPendingTracks() sets this,
            // so a rescan must not null out a hash a previous run already did.
            audioContentId = existingTrack?.audioContentId,
            // uri is content://media/external/audio/media/{_ID} - same id TrackUiModel.id
            // carries everywhere else in the app. Extracting it here lets
            // playback/queue/search/browse events resolve to Track.id by a plain
            // indexed Long lookup instead of a path/URI round trip.
            mediaStoreId = uri.lastPathSegment?.toLongOrNull(),
            albumId = meta.albumId,
            durationMs = meta.durationMs,
            bitrate = meta.bitrate,
            tagTitle = meta.title,
            tagArtist = meta.artist,
            tagAlbum = meta.album,
            tagGenre = meta.genre,
            tagCompleteness = listOfNotNull(meta.title, meta.artist, meta.album, meta.genre).size,
            lastSeenScanId = scanRunId,
            present = true,
            absentReason = null,
            scannedAt = System.currentTimeMillis(),
            // Carried over on rescan like audioContentId above - MediaStore's DATE_ADDED
            // reflects when the file was first indexed on-device, not when this scan ran.
            dateAdded = meta.dateAdded ?: existingTrack?.dateAdded,
            displayName = meta.displayName,
            relativePath = meta.relativePath
        )
        if (existingTrack != null) repository.updateTrack(track) else repository.insertTrack(track)
    }

    /** Slow pass: hashes tracks the fast pass wrote but nothing has hashed yet. See class kdoc. */
    suspend fun hashPendingTracks(): HashResult = scanLock.withLock { hashPendingTracksLocked() }

    private suspend fun hashPendingTracksLocked(): HashResult = withContext(Dispatchers.IO) {
        var hashed = 0
        var failed = 0
        for (track in repository.getTracksNeedingHash()) {
            try {
                val uri = Uri.parse(track.path)
                // codec/sampleRate/channels come from the same MediaExtractor pass
                // that computes the hash -- no second setDataSource() per track.
                val identity = AudioHasher.computeAudioIdentity(context, uri)
                repository.getOrCreateAudioContentIdAndAssign(
                    track,
                    AudioContent(
                        audioHash = identity.audioHash,
                        hashMethod = identity.hashMethod,
                        duration = track.durationMs?.let { it / 1000.0 },
                        codec = identity.codec,
                        sampleRate = identity.sampleRate,
                        channels = identity.channels,
                        firstSeenAt = System.currentTimeMillis()
                    )
                )
                hashed++
            } catch (e: Exception) {
                failed++
                log.w("Failed to hash ${track.path}", e)
                repository.insertIngestError(
                    IngestError(
                        path = track.path,
                        dev = null,
                        inode = null,
                        size = track.size,
                        mtime = track.mtime,
                        stage = "hash",
                        message = e.message,
                        attempts = 1,
                        lastAttemptAt = System.currentTimeMillis(),
                        resolvedAt = null
                    )
                )
                // Row stays present with audioContentId still null - it'll be
                // retried on the next hashPendingTracks() call, not dropped.
            }
        }
        HashResult(hashed, failed)
    }

    /** Third pass: DSP extraction + fingerprinting for AudioContent rows the hash pass created. */
    suspend fun analyzePendingAudioContent(): AnalysisResult = withContext(Dispatchers.IO) {
        var analyzed = 0
        var failed = 0
        val extractorVersion = EssentiaExtractor.EXTRACTOR_VERSION
        val configHash = extractor.value.configHash()
        for (audioContent in repository.getAudioContentNeedingAnalysis(extractorVersion, configHash)) {
            val path = repository.getAnyPresentTrackPath(audioContent.id)
            if (path == null) {
                failed++
                log.w("No present track left for AudioContent ${audioContent.id}, skipping analysis")
                continue
            }
            val outcome = runCatchingNonFatal {
                val features = extractor.value.extractContent(context, Uri.parse(path))
                val now = System.currentTimeMillis()
                resultWriter.write(
                    features.toRoomAudioFeatures(audioContentId = audioContent.id, analyzedAt = now),
                    features.toFingerprint(audioContentId = audioContent.id, computedAt = now)
                )
            }
            outcome.onSuccess {
                analyzed++
            }.onFailure { e ->
                failed++
                log.w("Failed to analyze AudioContent ${audioContent.id} via $path", e)
                repository.insertIngestError(
                    IngestError(
                        path = path,
                        dev = null,
                        inode = null,
                        size = null,
                        mtime = null,
                        stage = "analyze",
                        message = e.message,
                        attempts = 1,
                        lastAttemptAt = System.currentTimeMillis(),
                        resolvedAt = null
                    )
                )
            }
        }
        AnalysisResult(analyzed, failed)
    }

    private suspend fun forEachMediaStoreAudioFile(action: suspend (Uri, MediaStoreAudioMeta) -> Unit) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.BITRATE,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val rows = mutableListOf<Pair<Uri, MediaStoreAudioMeta>>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            // Not present on every OEM provider; degrade to null rather than throw.
            val genreCol = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val dateAddedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val bitrateCol = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val displayNameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val relativePathCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                val durationMs = cursor.getLong(durationCol)
                val durationSeconds = durationMs / 1000.0
                val size = cursor.getLong(sizeCol)

                val bitrate = if (bitrateCol >= 0 && !cursor.isNull(bitrateCol)) {
                    cursor.getInt(bitrateCol)
                } else if (durationSeconds > 0) {
                    ((size * 8) / durationSeconds).toInt()
                } else {
                    null
                }

                val meta = MediaStoreAudioMeta(
                    title = cursor.getString(titleCol),
                    artist = cursor.getString(artistCol),
                    album = cursor.getString(albumCol),
                    genre = if (genreCol >= 0) cursor.getString(genreCol) else null,
                    durationMs = durationMs,
                    size = size,
                    mtime = cursor.getLong(dateModifiedCol),
                    bitrate = bitrate,
                    albumId = if (!cursor.isNull(albumIdCol)) cursor.getLong(albumIdCol) else null,
                    dateAdded = if (dateAddedCol >= 0 && !cursor.isNull(dateAddedCol)) {
                        cursor.getLong(dateAddedCol)
                    } else {
                        null
                    },
                    displayName = if (displayNameCol >= 0) cursor.getString(displayNameCol) else null,
                    relativePath = if (relativePathCol >= 0) cursor.getString(relativePathCol) else null
                )
                rows.add(uri to meta)
            }
        }
        for ((uri, meta) in rows) action(uri, meta)
    }
}
