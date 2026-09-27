package com.amit152116.riffle.data.db.musicbrainz

class MusicBrainzRepository(private val dao: MusicBrainzDao) {
    suspend fun insertMusicBrainzMatch(match: MusicBrainzMatch) = dao.insertMusicBrainzMatch(match)
    suspend fun upsertMusicBrainzMatch(match: MusicBrainzMatch) = dao.upsertMusicBrainzMatch(match)
    suspend fun upsertMusicBrainzMatchWithArtists(match: MusicBrainzMatch, artistMbidsByPosition: List<String>) =
        dao.upsertMusicBrainzMatchWithArtists(match, artistMbidsByPosition)
    suspend fun insertMbArtist(artist: MbArtist) = dao.insertMbArtist(artist)
    suspend fun insertMbRecordingArtist(recordingArtist: MbRecordingArtist) =
        dao.insertMbRecordingArtist(recordingArtist)
    suspend fun insertAcoustidCache(cache: AcoustidCache) = dao.insertAcoustidCache(cache)
    suspend fun insertAcoustidCacheContent(cacheContent: AcoustidCacheContent) =
        dao.insertAcoustidCacheContent(cacheContent)
    suspend fun getMatchByAudioContentId(audioContentId: Long) = dao.getMatchByAudioContentId(audioContentId)
    suspend fun getArtistsForMatch(matchId: Long) = dao.getArtistsForMatch(matchId)
    suspend fun acoustidCacheExists(lookupKey: String) = dao.acoustidCacheExists(lookupKey) != null
    suspend fun getAudioContentIdsForLookupKey(lookupKey: String) = dao.getAudioContentIdsForLookupKey(lookupKey)
}
