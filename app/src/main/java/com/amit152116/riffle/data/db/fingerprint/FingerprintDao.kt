package com.amit152116.riffle.data.db.fingerprint

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FingerprintDao {
    @Insert
    suspend fun insertFingerprint(fingerprint: Fingerprint): Long

    @Insert
    suspend fun insertMatchRun(matchRun: MatchRun): Long

    @Insert
    suspend fun insertRunTrack(runTrack: RunTrack)

    // IGNORE: a pair re-discovered in reversed order normalizes to the same
    // (runId, aContentId, bContentId) primary key. That's not an error - it's
    // the same pair recognized twice within one run - so it's a silent no-op
    // that keeps the first-recorded values, not a crash.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAudioPair(audioPair: AudioPair)

    @Query("SELECT * FROM Fingerprint WHERE id = :id")
    suspend fun getFingerprintById(id: Long): Fingerprint?

    @Query("SELECT * FROM Fingerprint WHERE audioContentId = :audioContentId")
    suspend fun getFingerprintByAudioContentId(audioContentId: Long): Fingerprint?

    @Query("SELECT * FROM RunTrack WHERE runId = :runId AND trackId = :trackId")
    suspend fun getRunTrack(runId: Long, trackId: Long): RunTrack?

    @Query("SELECT * FROM AudioPair WHERE runId = :runId AND aContentId = :aContentId AND bContentId = :bContentId")
    suspend fun getAudioPair(runId: Long, aContentId: Long, bContentId: Long): AudioPair?

    @Query("SELECT COUNT(*) FROM AudioPair WHERE runId = :runId")
    suspend fun countAudioPairs(runId: Long): Int
}
