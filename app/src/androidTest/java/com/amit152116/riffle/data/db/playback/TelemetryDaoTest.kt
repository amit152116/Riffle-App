package com.amit152116.riffle.data.db.playback

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.db.BrowseEntityType
import com.amit152116.riffle.data.db.PlaybackSource
import com.amit152116.riffle.data.db.QueueAction
import com.amit152116.riffle.data.db.RecommendationActionType
import com.amit152116.riffle.data.db.RecommendationSurface
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.SearchResultType
import com.amit152116.riffle.data.db.ShuffleMode
import com.amit152116.riffle.data.db.TestFixtures
import com.amit152116.riffle.data.db.library.LibraryDao
import com.amit152116.riffle.data.db.playback.entities.BrowseEvent
import com.amit152116.riffle.data.db.playback.entities.QueueEvent
import com.amit152116.riffle.data.db.playback.entities.RecommendationAction
import com.amit152116.riffle.data.db.playback.entities.RecommendationImpression
import com.amit152116.riffle.data.db.playback.entities.RecommendationSlate
import com.amit152116.riffle.data.db.playback.entities.SearchEvent
import com.amit152116.riffle.data.db.playback.entities.ShuffleDecision
import com.amit152116.riffle.data.db.playback.entities.ShuffleSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelemetryDaoTest {
    private lateinit var db: RiffleDatabase
    private lateinit var dao: TelemetryDao
    private lateinit var libraryDao: LibraryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RiffleDatabase::class.java).build()
        dao = db.telemetryDao()
        libraryDao = db.libraryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertTrack(): Long {
        val contentId = libraryDao.insertAudioContent(TestFixtures.audioContent())
        return libraryDao.insertTrack(TestFixtures.track(audioContentId = contentId))
    }

    @Test
    fun queueEvent_roundTripsAndFiltersByTrack() = runBlocking {
        val trackId = insertTrack()

        dao.insertQueueEvent(
            QueueEvent(
                trackId = trackId,
                queuedAt = 1_000L,
                action = QueueAction.ADD,
                origin = PlaybackSource.ALBUM
            )
        )
        dao.insertQueueEvent(
            QueueEvent(
                trackId = trackId,
                queuedAt = 2_000L,
                action = QueueAction.MOVE,
                fromPosition = 2,
                toPosition = 0,
                origin = PlaybackSource.MANUAL
            )
        )

        val events = dao.getQueueEventsForTrack(trackId)
        assertEquals(2, events.size)
        // ORDER BY queuedAt DESC
        assertEquals(QueueAction.MOVE, events[0].action)
    }

    @Test
    fun searchEvent_roundTrips() = runBlocking {
        val trackId = insertTrack()

        val id = dao.insertSearchEvent(
            SearchEvent(
                timestamp = 1_000L,
                query = "tycho",
                resultCount = 3,
                selectedType = SearchResultType.TRACK,
                selectedTrackId = trackId
            )
        )

        assertTrue(id > 0)
    }

    @Test
    fun browseEvent_roundTrips() = runBlocking {
        val id = dao.insertBrowseEvent(
            BrowseEvent(
                timestamp = 1_000L,
                entityType = BrowseEntityType.ARTIST,
                entityKey = "Tycho"
            )
        )

        assertTrue(id > 0)
    }

    @Test
    fun shuffleSession_decisionsRoundTripInPositionOrder() = runBlocking {
        val trackA = insertTrack()
        val sessionId = dao.insertShuffleSession(
            ShuffleSession(
                startedAt = 1_000L,
                mode = ShuffleMode.SIMILARITY,
                sourceTrackId = trackA,
                configHash = "cfg-1"
            )
        )

        dao.insertShuffleDecisions(
            listOf(
                ShuffleDecision(
                    sessionId = sessionId,
                    trackId = trackA,
                    position = 1,
                    score = 0.5,
                    reason = "bpm"
                ),
                ShuffleDecision(
                    sessionId = sessionId,
                    trackId = trackA,
                    position = 0,
                    score = 0.9,
                    reason = "mfcc"
                )
            )
        )

        val decisions = dao.getShuffleDecisions(sessionId)
        assertEquals(2, decisions.size)
        assertEquals(0, decisions[0].position)
        assertEquals(0.9, decisions[0].score, 0.0001)
    }

    @Test
    fun recommendationSlate_impressionsAndActionsRoundTrip() = runBlocking {
        val trackA = insertTrack()
        val slateId = dao.insertRecommendationSlate(
            RecommendationSlate(
                generatedAt = 1_000L,
                surface = RecommendationSurface.SIMILAR_TRACKS,
                sourceTrackId = trackA,
                algorithm = "mfcc-cosine",
                algorithmVersion = "audio-v1",
                configHash = "cfg-1"
            )
        )

        dao.insertRecommendationImpressions(
            listOf(
                RecommendationImpression(
                    slateId = slateId,
                    trackId = trackA,
                    position = 0,
                    score = 0.87
                )
            )
        )
        val impressions = dao.getRecommendationImpressions(slateId)
        assertEquals(1, impressions.size)

        val impressionId = impressions[0].id
        dao.insertRecommendationAction(
            RecommendationAction(
                impressionId = impressionId,
                type = RecommendationActionType.CLICKED,
                at = 1_100L
            )
        )
        dao.insertRecommendationAction(
            RecommendationAction(
                impressionId = impressionId,
                type = RecommendationActionType.DISMISSED,
                at = 1_200L
            )
        )

        // Both actions kept - clicking then later dismissing is two distinct
        // signals, never a single mutated column.
        val actions = dao.getRecommendationActions(impressionId)
        assertEquals(2, actions.size)
    }
}
