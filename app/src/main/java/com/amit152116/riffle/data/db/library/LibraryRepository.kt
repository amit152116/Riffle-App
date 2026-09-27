package com.amit152116.riffle.data.db.library

class LibraryRepository(private val dao: LibraryDao) {
    suspend fun insertAudioContent(audioContent: AudioContent) = dao.insertAudioContent(audioContent)
    suspend fun insertScanRun(scanRun: ScanRun) = dao.insertScanRun(scanRun)
    suspend fun insertTrack(track: Track) = dao.insertTrack(track)
    suspend fun insertIngestError(ingestError: IngestError) = dao.insertIngestError(ingestError)
    suspend fun getAudioContentById(id: Long) = dao.getAudioContentById(id)
    suspend fun getTrackById(id: Long) = dao.getTrackById(id)
    suspend fun getTrackByPath(path: String) = dao.getTrackByPath(path)
    suspend fun getIngestErrorByPath(path: String) = dao.getIngestErrorByPath(path)
}
