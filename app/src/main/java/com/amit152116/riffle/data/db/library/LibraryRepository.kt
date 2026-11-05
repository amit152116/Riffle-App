package com.amit152116.riffle.data.db.library

import com.amit152116.riffle.data.db.AbsentReason
import com.amit152116.riffle.data.db.HashMethod
import com.amit152116.riffle.data.db.RunStatus

class LibraryRepository(private val dao: LibraryDao) {
    suspend fun insertAudioContent(audioContent: AudioContent) = dao.insertAudioContent(audioContent)
    suspend fun insertScanRun(scanRun: ScanRun) = dao.insertScanRun(scanRun)
    suspend fun insertTrack(track: Track) = dao.insertTrack(track)
    suspend fun updateTrack(track: Track) = dao.updateTrack(track)
    suspend fun completeScanRun(id: Long, completedAt: Long, status: RunStatus) =
        dao.completeScanRun(id, completedAt, status)
    suspend fun insertIngestError(ingestError: IngestError) = dao.insertIngestError(ingestError)
    suspend fun getAudioContentById(id: Long) = dao.getAudioContentById(id)
    suspend fun getAudioContentByHash(hash: String, method: HashMethod) = dao.getAudioContentByHash(hash, method)
    suspend fun getTrackById(id: Long) = dao.getTrackById(id)
    suspend fun getTrackByPath(path: String) = dao.getTrackByPath(path)
    suspend fun getTrackByMediaStoreId(mediaStoreId: Long) = dao.getTrackByMediaStoreId(mediaStoreId)
    suspend fun markTracksAbsentNotInScan(scanId: Long, reason: AbsentReason = AbsentReason.MISSING) =
        dao.markTracksAbsentNotInScan(scanId, reason)
    suspend fun getIngestErrorByPath(path: String) = dao.getIngestErrorByPath(path)
    suspend fun getTracksNeedingHash() = dao.getTracksNeedingHash()
    suspend fun getAnyPresentTrackPath(audioContentId: Long) = dao.getAnyPresentTrackPath(audioContentId)
    suspend fun getAudioContentNeedingAnalysis(extractorVersion: String, configHash: String) =
        dao.getAudioContentNeedingAnalysis(extractorVersion, configHash)
    suspend fun getOrCreateAudioContentIdAndAssign(track: Track, audioContent: AudioContent) =
        dao.getOrCreateAudioContentIdAndAssign(track, audioContent)
    fun getPresentTracksFlow(minDurationMs: Long = 0L) = dao.getPresentTracksFlow(minDurationMs)
    suspend fun getPresentTracks(minDurationMs: Long = 0L) = dao.getPresentTracks(minDurationMs)
    
    fun getLastAddedTracksFlow() = dao.getLastAddedTracksFlow()
    fun getFavoriteTracksFlow() = dao.getFavoriteTracksFlow()
    fun getHistoryTracksFlow() = dao.getHistoryTracksFlow()
    fun getMostPlayedTracksFlow() = dao.getMostPlayedTracksFlow()
    fun getTopArtistsByPlayCountFlow() = dao.getTopArtistsByPlayCountFlow()
    fun getTopAlbumsByPlayCountFlow() = dao.getTopAlbumsByPlayCountFlow()
    fun getBlacklistedTracksFlow() = dao.getBlacklistedTracksFlow()
}
