package com.amit152116.riffle.data.db.musicbrainz

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface MusicBrainzDao {
    // Plain insert (ABORT on conflict) - only safe to call when the caller
    // already knows no row exists for this audioContentId. Re-tagging must
    // go through upsertMusicBrainzMatch, not this directly: REPLACE would
    // delete+reinsert the row under a new id, which either orphans or
    // (with FK enforcement, which Room turns on by default) throws a foreign
    // key violation on any MbRecordingArtist rows still pointing at the old id.
    @Insert
    suspend fun insertMusicBrainzMatch(match: MusicBrainzMatch): Long

    @Update
    suspend fun updateMusicBrainzMatch(match: MusicBrainzMatch)

    @Query("SELECT id FROM MusicBrainzMatch WHERE audioContentId = :audioContentId")
    suspend fun getMatchIdByAudioContentId(audioContentId: Long): Long?

    @Query("DELETE FROM MbRecordingArtist WHERE matchId = :matchId")
    suspend fun deleteRecordingArtistsForMatch(matchId: Long)

    // Re-tag-safe upsert: updates the existing row in place (preserving its
    // id, and therefore every MbRecordingArtist FK pointing at it) instead of
    // deleting and reinserting under a new id.
    @Transaction
    suspend fun upsertMusicBrainzMatch(match: MusicBrainzMatch): Long {
        val existingId = getMatchIdByAudioContentId(match.audioContentId)
        return if (existingId != null) {
            updateMusicBrainzMatch(match.copy(id = existingId))
            existingId
        } else {
            insertMusicBrainzMatch(match)
        }
    }

    // Re-tag-safe: replaces the full ordered artist list for a match without
    // ever deleting the MusicBrainzMatch row itself.
    @Transaction
    suspend fun upsertMusicBrainzMatchWithArtists(match: MusicBrainzMatch, artistMbidsByPosition: List<String>): Long {
        val matchId = upsertMusicBrainzMatch(match)
        deleteRecordingArtistsForMatch(matchId)
        artistMbidsByPosition.forEachIndexed { position, mbid ->
            insertMbRecordingArtist(MbRecordingArtist(matchId = matchId, position = position, artistMbid = mbid))
        }
        return matchId
    }

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
