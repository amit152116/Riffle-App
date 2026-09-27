package com.amit152116.riffle.data.local.fingerprint

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FingerprintDao {
    @Insert
    suspend fun insertFingerprint(fingerprint: Fingerprint): Long

    @Insert
    suspend fun insertMatchRun(matchRun: MatchRun): Long

    @Insert
    suspend fun insertRunTrack(runTrack: RunTrack)

    @Insert
    suspend fun insertAudioPair(audioPair: AudioPair)

    @Query("SELECT * FROM Fingerprint WHERE id = :id")
    suspend fun getFingerprintById(id: Long): Fingerprint?

    @Query("SELECT * FROM RunTrack WHERE runId = :runId AND trackId = :trackId")
    suspend fun getRunTrack(runId: Long, trackId: Long): RunTrack?

    @Query("SELECT * FROM AudioPair WHERE runId = :runId AND aContentId = :aContentId AND bContentId = :bContentId")
    suspend fun getAudioPair(runId: Long, aContentId: Long, bContentId: Long): AudioPair?
}
