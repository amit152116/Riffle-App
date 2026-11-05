package com.amit152116.riffle.data.db.playback

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.amit152116.riffle.data.db.playback.entities.BrowseEvent
import com.amit152116.riffle.data.db.playback.entities.QueueEvent
import com.amit152116.riffle.data.db.playback.entities.RecommendationAction
import com.amit152116.riffle.data.db.playback.entities.RecommendationImpression
import com.amit152116.riffle.data.db.playback.entities.RecommendationSlate
import com.amit152116.riffle.data.db.playback.entities.SearchEvent
import com.amit152116.riffle.data.db.playback.entities.ShuffleDecision
import com.amit152116.riffle.data.db.playback.entities.ShuffleSession

// Queue/search/browse/shuffle/recommendation event logs, kept separate from
// PlaybackDao (core playback + favorites/playlists) since this is a distinct,
// larger-growing concern - discovery/recommendation signals rather than the
// playback flight recorder itself.
@Dao
interface TelemetryDao {
    @Insert
    suspend fun insertQueueEvent(event: QueueEvent): Long

    @Query("SELECT * FROM QueueEvent WHERE trackId = :trackId ORDER BY queuedAt DESC")
    suspend fun getQueueEventsForTrack(trackId: Long): List<QueueEvent>

    @Insert
    suspend fun insertSearchEvent(event: SearchEvent): Long

    @Insert
    suspend fun insertBrowseEvent(event: BrowseEvent): Long

    @Insert
    suspend fun insertShuffleSession(session: ShuffleSession): Long

    @Insert
    suspend fun insertShuffleDecision(decision: ShuffleDecision): Long

    @Insert
    suspend fun insertShuffleDecisions(decisions: List<ShuffleDecision>)

    @Query("SELECT * FROM ShuffleDecision WHERE sessionId = :sessionId ORDER BY position ASC")
    suspend fun getShuffleDecisions(sessionId: Long): List<ShuffleDecision>

    @Insert
    suspend fun insertRecommendationSlate(slate: RecommendationSlate): Long

    @Insert
    suspend fun insertRecommendationImpression(impression: RecommendationImpression): Long

    @Insert
    suspend fun insertRecommendationImpressions(impressions: List<RecommendationImpression>)

    @Query("SELECT * FROM RecommendationImpression WHERE slateId = :slateId ORDER BY position ASC")
    suspend fun getRecommendationImpressions(slateId: Long): List<RecommendationImpression>

    @Insert
    suspend fun insertRecommendationAction(action: RecommendationAction): Long

    @Query("SELECT * FROM RecommendationAction WHERE impressionId = :impressionId ORDER BY at ASC")
    suspend fun getRecommendationActions(impressionId: Long): List<RecommendationAction>
}
