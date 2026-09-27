package com.amit152116.riffle.data.db.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LibraryDao {
    @Insert
    suspend fun insertAudioContent(audioContent: AudioContent): Long

    @Insert
    suspend fun insertScanRun(scanRun: ScanRun): Long

    @Insert
    suspend fun insertTrack(track: Track): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngestError(ingestError: IngestError)

    @Query("SELECT * FROM AudioContent WHERE id = :id")
    suspend fun getAudioContentById(id: Long): AudioContent?

    @Query("SELECT * FROM Track WHERE id = :id")
    suspend fun getTrackById(id: Long): Track?

    @Query("SELECT * FROM Track WHERE path = :path")
    suspend fun getTrackByPath(path: String): Track?

    @Query("SELECT * FROM IngestError WHERE path = :path")
    suspend fun getIngestErrorByPath(path: String): IngestError?
}
