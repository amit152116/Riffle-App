package com.amit152116.riffle.data.db.playback

import com.amit152116.riffle.data.db.playback.entities.BrowseEvent
import com.amit152116.riffle.data.db.playback.entities.QueueEvent
import com.amit152116.riffle.data.db.playback.entities.RecommendationAction
import com.amit152116.riffle.data.db.playback.entities.RecommendationImpression
import com.amit152116.riffle.data.db.playback.entities.RecommendationSlate
import com.amit152116.riffle.data.db.playback.entities.SearchEvent
import com.amit152116.riffle.data.db.playback.entities.ShuffleDecision
import com.amit152116.riffle.data.db.playback.entities.ShuffleSession

class TelemetryRepository(private val dao: TelemetryDao) {
    suspend fun insertQueueEvent(event: QueueEvent) = dao.insertQueueEvent(event)
    suspend fun getQueueEventsForTrack(trackId: Long) = dao.getQueueEventsForTrack(trackId)

    suspend fun insertSearchEvent(event: SearchEvent) = dao.insertSearchEvent(event)

    suspend fun insertBrowseEvent(event: BrowseEvent) = dao.insertBrowseEvent(event)

    suspend fun insertShuffleSession(session: ShuffleSession) = dao.insertShuffleSession(session)
    suspend fun insertShuffleDecisions(decisions: List<ShuffleDecision>) = dao.insertShuffleDecisions(decisions)
    suspend fun getShuffleDecisions(sessionId: Long) = dao.getShuffleDecisions(sessionId)

    suspend fun insertRecommendationSlate(slate: RecommendationSlate) = dao.insertRecommendationSlate(slate)
    suspend fun insertRecommendationImpressions(impressions: List<RecommendationImpression>) =
        dao.insertRecommendationImpressions(impressions)
    suspend fun getRecommendationImpressions(slateId: Long) = dao.getRecommendationImpressions(slateId)
    suspend fun insertRecommendationAction(action: RecommendationAction) = dao.insertRecommendationAction(action)
    suspend fun getRecommendationActions(impressionId: Long) = dao.getRecommendationActions(impressionId)
}
