package com.amit152116.riffle.data.db.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.amit152116.riffle.data.db.AbsentReason
import com.amit152116.riffle.data.db.HashMethod
import com.amit152116.riffle.data.db.RunStatus
import com.amit152116.riffle.data.db.analysis.AudioFeatures
import com.amit152116.riffle.data.db.fingerprint.Fingerprint
import kotlinx.coroutines.flow.Flow

// Room projections for aggregate play-count queries - grouping by artist/album
// name directly (not by track) so an artist/album with plays spread across
// many low-count tracks still ranks correctly, instead of being derived from
// only the top-50 individually-most-played tracks.
data class ArtistPlayCount(val artist: String, val playCount: Int)

data class AlbumPlayCount(val album: String, val playCount: Int)

@Dao
interface LibraryDao {
    @Insert
    suspend fun insertAudioContent(audioContent: AudioContent): Long

    @Insert
    suspend fun insertScanRun(scanRun: ScanRun): Long

    @Insert
    suspend fun insertTrack(track: Track): Long

    @Update
    suspend fun updateTrack(track: Track)

    @Query("UPDATE ScanRun SET completedAt = :completedAt, status = :status WHERE id = :id")
    suspend fun completeScanRun(id: Long, completedAt: Long, status: RunStatus)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngestError(ingestError: IngestError)

    @Query("SELECT * FROM AudioContent WHERE id = :id")
    suspend fun getAudioContentById(id: Long): AudioContent?

    @Query("SELECT * FROM AudioContent WHERE audioHash = :hash AND hashMethod = :method")
    suspend fun getAudioContentByHash(hash: String, method: HashMethod): AudioContent?

    @Query("SELECT * FROM Track WHERE id = :id")
    suspend fun getTrackById(id: Long): Track?

    @Query("SELECT * FROM Track WHERE path = :path")
    suspend fun getTrackByPath(path: String): Track?

    // TrackUiModel.id and every playback/queue/search/browse capture site key off
    // MediaStore's _ID, not Track.id - resolve through this before writing a trackId FK.
    @Query("SELECT * FROM Track WHERE mediaStoreId = :mediaStoreId")
    suspend fun getTrackByMediaStoreId(mediaStoreId: Long): Track?

    // Never deletes: a Track not touched by the current scan (lastSeenScanId
    // != scanId) is presumed gone from MediaStore and marked absent instead,
    // mirroring ../riffle/riffle's "never destructive by default" rule.
    @Query(
        "UPDATE Track SET present = 0, absentReason = :reason " +
            "WHERE present = 1 AND (lastSeenScanId IS NULL OR lastSeenScanId != :scanId)"
    )
    suspend fun markTracksAbsentNotInScan(scanId: Long, reason: AbsentReason)

    @Query("SELECT * FROM IngestError WHERE path = :path")
    suspend fun getIngestErrorByPath(path: String): IngestError?

    // Rows the fast metadata pass wrote but the background hash pass hasn't
    // reached yet (fresh install, new files since last hash pass, or a
    // previous hash attempt that failed and left audioContentId null).
    @Query("SELECT * FROM Track WHERE present = 1 AND audioContentId IS NULL")
    suspend fun getTracksNeedingHash(): List<Track>

    @Query("SELECT MIN(path) FROM Track WHERE audioContentId = :audioContentId AND present = 1")
    suspend fun getAnyPresentTrackPath(audioContentId: Long): String?

    @Query(
        """
        SELECT ac.* FROM AudioContent ac
        WHERE EXISTS (SELECT 1 FROM Track t WHERE t.audioContentId = ac.id AND t.present = 1)
        AND (
            NOT EXISTS (
                SELECT 1 FROM AudioFeatures af
                WHERE af.audioContentId = ac.id AND af.extractorVersion = :extractorVersion AND af.configHash = :configHash
            )
            OR NOT EXISTS (
                SELECT 1 FROM Fingerprint f
                WHERE f.audioContentId = ac.id AND f.analyzerVersion = :extractorVersion
                  AND f.configHash = :configHash AND f.purpose = 'CANONICAL'
            )
        )
        """
    )
    suspend fun getAudioContentNeedingAnalysis(extractorVersion: String, configHash: String): List<AudioContent>

    @Transaction
    suspend fun getOrCreateAudioContentIdAndAssign(track: Track, audioContent: AudioContent): Long {
        val id = getAudioContentByHash(audioContent.audioHash, audioContent.hashMethod)?.id
            ?: insertAudioContent(audioContent)
        updateTrack(track.copy(audioContentId = id))
        return id
    }

    // durationMs >= :minDurationMs excludes null durations the same way the
    // old MediaStore `DURATION >= ?` selection did. COLLATE NOCASE matches
    // MediaStore's case-insensitive TITLE ASC ordering the UI (alphabet
    // scrollbar included) was built against. Referencing BlacklistedTrack
    // here (not just in the app layer) means Room also invalidates this
    // Flow when a track is blacklisted - it drops out of the songs list on
    // its own, no explicit reload.
    @Query(
        "SELECT * FROM Track WHERE present = 1 AND durationMs >= :minDurationMs " +
            "AND id NOT IN (SELECT trackId FROM BlacklistedTrack) " +
            "ORDER BY tagTitle COLLATE NOCASE ASC"
    )
    fun getPresentTracksFlow(minDurationMs: Long = 0L): Flow<List<Track>>

    // One-shot variant for call sites that need a single snapshot (e.g.
    // restoring a saved queue at startup) rather than an ongoing observer.
    @Query(
        "SELECT * FROM Track WHERE present = 1 AND durationMs >= :minDurationMs " +
            "AND id NOT IN (SELECT trackId FROM BlacklistedTrack) " +
            "ORDER BY tagTitle COLLATE NOCASE ASC"
    )
    suspend fun getPresentTracks(minDurationMs: Long = 0L): List<Track>

    // Ordered by MediaStore's real DATE_ADDED, not Room's autoincrement id - a bulk
    // initial scan inserts every track in one pass, so id DESC would reflect scan
    // traversal order rather than which files were actually added to the device most
    // recently. NULLS LAST since a track scanned before the dateAdded column existed
    // (pre-migration) won't have a value until its next rescan.
    @Query(
        "SELECT * FROM Track WHERE present = 1 " +
            "AND id NOT IN (SELECT trackId FROM BlacklistedTrack) " +
            "ORDER BY dateAdded IS NULL, dateAdded DESC LIMIT 50"
    )
    fun getLastAddedTracksFlow(): Flow<List<Track>>

    @Query(
        "SELECT t.* FROM Track t " +
            "INNER JOIN FavoriteTrack f ON t.id = f.trackId " +
            "WHERE t.present = 1 AND t.id NOT IN (SELECT trackId FROM BlacklistedTrack) " +
            "ORDER BY f.addedAt DESC"
    )
    fun getFavoriteTracksFlow(): Flow<List<Track>>

    @Query(
        "SELECT t.* FROM Track t " +
            "INNER JOIN PlaybackEvent p ON t.id = p.trackId " +
            "WHERE t.present = 1 AND t.id NOT IN (SELECT trackId FROM BlacklistedTrack) " +
            "GROUP BY p.trackId " +
            "ORDER BY MAX(p.startedAt) DESC LIMIT 50"
    )
    fun getHistoryTracksFlow(): Flow<List<Track>>

    @Query(
        "SELECT t.* FROM Track t " +
            "INNER JOIN PlaybackEvent p ON t.id = p.trackId " +
            "WHERE t.present = 1 AND t.id NOT IN (SELECT trackId FROM BlacklistedTrack) " +
            "GROUP BY p.trackId " +
            "ORDER BY COUNT(p.id) DESC LIMIT 50"
    )
    fun getMostPlayedTracksFlow(): Flow<List<Track>>

    // Aggregated across ALL PlaybackEvent rows (GROUP BY runs before LIMIT),
    // not derived from getMostPlayedTracksFlow's top-50 tracks - an artist
    // whose plays are spread across many tracks below that per-track cutoff
    // would otherwise be undercounted or dropped entirely.
    @Query(
        "SELECT COALESCE(t.tagArtist, 'Unknown Artist') AS artist, COUNT(p.id) AS playCount " +
            "FROM PlaybackEvent p INNER JOIN Track t ON t.id = p.trackId " +
            "WHERE t.present = 1 AND t.id NOT IN (SELECT trackId FROM BlacklistedTrack) " +
            "GROUP BY artist ORDER BY playCount DESC LIMIT 50"
    )
    fun getTopArtistsByPlayCountFlow(): Flow<List<ArtistPlayCount>>

    @Query(
        "SELECT COALESCE(t.tagAlbum, 'Unknown Album') AS album, COUNT(p.id) AS playCount " +
            "FROM PlaybackEvent p INNER JOIN Track t ON t.id = p.trackId " +
            "WHERE t.present = 1 AND t.id NOT IN (SELECT trackId FROM BlacklistedTrack) " +
            "GROUP BY album ORDER BY playCount DESC LIMIT 50"
    )
    fun getTopAlbumsByPlayCountFlow(): Flow<List<AlbumPlayCount>>

    // Deliberately not excluded by BlacklistedTrack (unlike every query
    // above) - this is the one screen whose whole job is to show blacklisted
    // tracks so they can be un-blacklisted.
    @Query(
        "SELECT t.* FROM Track t " +
            "INNER JOIN BlacklistedTrack b ON t.id = b.trackId " +
            "WHERE t.present = 1 ORDER BY b.addedAt DESC"
    )
    fun getBlacklistedTracksFlow(): Flow<List<Track>>
}
