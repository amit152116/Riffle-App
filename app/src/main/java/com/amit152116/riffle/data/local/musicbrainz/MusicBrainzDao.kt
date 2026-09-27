package com.amit152116.riffle.data.local.musicbrainz

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MusicBrainzDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMusicBrainzMatch(match: MusicBrainzMatch): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMbArtist(artist: MbArtist)

    @Insert
    suspend fun insertMbRecordingArtist(recordingArtist: MbRecordingArtist)

    @Insert
    suspend fun insertAcoustidCache(cache: AcoustidCache)

    @Insert
    suspend fun insertAcoustidCacheContent(cacheContent: AcoustidCacheContent)

    @Query("SELECT * FROM MusicBrainzMatch WHERE audioContentId = :audioContentId")
    suspend fun getMatchByAudioContentId(audioContentId: Long): MusicBrainzMatch?

    @Query(
        "SELECT MbArtist.* FROM MbArtist " +
            "JOIN MbRecordingArtist ON MbRecordingArtist.artistMbid = MbArtist.mbid " +
            "WHERE MbRecordingArtist.matchId = :matchId " +
            "ORDER BY MbRecordingArtist.position ASC"
    )
    suspend fun getArtistsForMatch(matchId: Long): List<MbArtist>

    @Query("SELECT 1 FROM AcoustidCache WHERE lookupKey = :lookupKey")
    suspend fun acoustidCacheExists(lookupKey: String): Int?

    @Query("SELECT audioContentId FROM AcoustidCacheContent WHERE lookupKey = :lookupKey")
    suspend fun getAudioContentIdsForLookupKey(lookupKey: String): List<Long>
}
