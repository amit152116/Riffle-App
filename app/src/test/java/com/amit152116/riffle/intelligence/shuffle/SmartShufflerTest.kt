package com.amit152116.riffle.intelligence.shuffle

import com.amit152116.riffle.intelligence.TestData
import com.amit152116.riffle.intelligence.model.ShuffleConfig
import com.amit152116.riffle.intelligence.model.SimilarityConfig
import com.amit152116.riffle.intelligence.similarity.ScoreBreakdown
import com.amit152116.riffle.intelligence.similarity.ScoredNeighbor
import com.amit152116.riffle.intelligence.similarity.SimilarityCalibration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SmartShufflerTest {
    private val det = ShuffleConfig(temperature = 0.0)

    private fun entry(
        id: Long,
        artist: String? = "artist$id",
        bpm: Double? = 120.0,
        key: String? = null,
        mfcc: DoubleArray = TestData.angleMfcc(0.0)
    ) = PoolEntry(TestData.rec(id, bpm = bpm, key = key, mfcc = mfcc), trackId = id * 10, artist = artist)

    private fun source(map: Map<Long, List<Pair<Long, Double>>>) = object : NeighborSource {
        override suspend fun neighbors(audioContentId: Long): List<ScoredNeighbor> =
            map[audioContentId].orEmpty().map { (id, s) -> ScoredNeighbor(id, ScoreBreakdown(s, null, null, null, s)) }
    }

    private fun shuffle(
        pool: List<PoolEntry>,
        neighbors: Map<Long, List<Pair<Long, Double>>> = emptyMap(),
        exclusions: Map<Long, Set<Long>> = emptyMap(),
        seed: Long? = pool.firstOrNull()?.audioContentId,
        length: Int = 10,
        config: ShuffleConfig = det,
        rng: Random = Random(1)
    ): List<ShuffleStep> = runBlocking {
        SmartShuffler(SimilarityConfig(), config).shuffle(pool, SimilarityCalibration.neutral(60.0), source(neighbors), exclusions, seed, length, rng)
    }

    private fun ids(steps: List<ShuffleStep>) = steps.map { it.audioContentId }

    @Test fun picksTheLowestTotalScore_andReportsEveryTerm() {
        val steps = shuffle(listOf(entry(1), entry(2), entry(3)), mapOf(1L to listOf(3L to 0.30, 2L to 0.10)), length = 2)
        assertEquals(listOf(1L, 2L), ids(steps))
        assertEquals(listOf(10L, 20L), steps.map { it.trackId })
        assertEquals(0.10, steps[1].score, 1e-9)
        assertEquals(listOf("similarity", "artistRepeat", "bpmJump", "keyHarmonyBonus"), steps[1].breakdown.map { it.name })
        assertTrue(steps[0].breakdown.isEmpty())
    }

    @Test fun artistRepeatPenalty_reordersCandidates() {
        val pool = listOf(entry(1, artist = "X"), entry(2, artist = "X"), entry(3, artist = "Y"))
        val steps = shuffle(pool, mapOf(1L to listOf(2L to 0.10, 3L to 0.30)), length = 2)
        assertEquals(3L, steps[1].audioContentId)
        assertEquals(0.0, steps[1].breakdown.first { it.name == "artistRepeat" }.value, 0.0)
    }

    @Test fun bpmJumpPenalty_appliesAboveSpreadTimesQuarter() {
        // spread 60 -> threshold 15 BPM
        val pool = listOf(entry(1, bpm = 100.0), entry(2, bpm = 140.0), entry(3, bpm = 105.0))
        val steps = shuffle(pool, mapOf(1L to listOf(2L to 0.10, 3L to 0.35)), length = 2)
        assertEquals(3L, steps[1].audioContentId)
    }

    @Test fun keyHarmonyBonus_rewardsAdjacentKeys() {
        val pool = listOf(entry(1, key = "C"), entry(2, key = "F#"), entry(3, key = "G"))
        val steps = shuffle(pool, mapOf(1L to listOf(2L to 0.30, 3L to 0.40)), length = 2)
        assertEquals(3L, steps[1].audioContentId)
        assertEquals(0.20, steps[1].score, 1e-9)
        assertEquals(-0.2, steps[1].breakdown.first { it.name == "keyHarmonyBonus" }.value, 1e-9)
    }

    @Test fun missingBpm_skipsTheBpmJumpTerm() {
        val steps = shuffle(listOf(entry(1), entry(2, bpm = null)), mapOf(1L to listOf(2L to 0.1)), length = 2)
        val term = steps[1].breakdown.first { it.name == "bpmJump" }
        assertTrue(term.skipped)
        assertEquals(0.0, term.value, 0.0)
    }

    @Test fun missingKey_skipsTheKeyTerm() {
        val steps = shuffle(listOf(entry(1, key = "C"), entry(2, key = null)), mapOf(1L to listOf(2L to 0.1)), length = 2)
        assertTrue(steps[1].breakdown.first { it.name == "keyHarmonyBonus" }.skipped)
    }

    @Test fun recordingWithNoArtist_isExemptFromTheArtistPenalty() {
        val steps = shuffle(listOf(entry(1, artist = null), entry(2, artist = null)), mapOf(1L to listOf(2L to 0.1)), length = 2)
        val term = steps[1].breakdown.first { it.name == "artistRepeat" }
        assertTrue(term.skipped)
        assertEquals(0.1, steps[1].score, 1e-9)
    }

    @Test fun excludedRecordings_areNeverPicked() {
        val pool = listOf(entry(1), entry(2), entry(3))
        val steps = shuffle(pool, mapOf(1L to listOf(2L to 0.10, 3L to 0.30)), exclusions = mapOf(1L to setOf(2L)), length = 3)
        assertEquals(listOf(1L, 3L), ids(steps))
    }

    @Test fun exclusionsOfLaterPicks_alsoApply() {
        val pool = listOf(entry(1), entry(2), entry(3), entry(4))
        val steps = shuffle(
            pool, mapOf(1L to listOf(2L to 0.1), 2L to listOf(3L to 0.1, 4L to 0.2)),
            exclusions = mapOf(2L to setOf(3L)), length = 4
        )
        assertTrue(3L !in ids(steps))
    }

    @Test fun noStoredNeighboursInPool_fallsBackToOnDemandScoring_notRandom() {
        val pool = listOf(
            entry(1, mfcc = TestData.angleMfcc(0.0)), entry(2, mfcc = TestData.angleMfcc(80.0)),
            entry(3, mfcc = TestData.angleMfcc(10.0)), entry(4, mfcc = TestData.angleMfcc(45.0))
        )
        assertEquals(listOf(1L, 3L, 4L, 2L), ids(shuffle(pool, rng = Random(1))))
        assertEquals(listOf(1L, 3L, 4L, 2L), ids(shuffle(pool, rng = Random(2))))
    }

    @Test fun seedOutsideThePool_throws() {
        assertThrows(IllegalArgumentException::class.java) { shuffle(listOf(entry(1), entry(2)), seed = 99L) }
    }

    @Test fun seedOutsideThePool_throwsADistinctType_withAnActionableMessage() {
        val e = assertThrows(SeedNotInPoolException::class.java) { shuffle(listOf(entry(1), entry(2)), seed = 99L) }
        assertTrue(e.message!!.contains("not in the candidate pool"))
        assertTrue(e is IllegalArgumentException)
    }

    @Test fun emptyPool_orNonPositiveLength_returnsEmpty() {
        assertTrue(shuffle(emptyList(), seed = null).isEmpty())
        assertTrue(shuffle(listOf(entry(1)), length = 0).isEmpty())
    }

    @Test fun lengthLargerThanPool_returnsEachRecordingOnce() {
        val steps = shuffle(listOf(entry(1), entry(2), entry(3)), length = 50)
        assertEquals(3, steps.size)
        assertEquals(3, ids(steps).toSet().size)
    }

    @Test fun poolOfOne_returnsThatRecording() =
        assertEquals(listOf(7L), ids(shuffle(listOf(entry(7)), length = 5)))

    @Test fun positiveTemperature_isReproduciblePerRngSeed_andNeverRepeats() {
        val pool = (1L..8L).map { entry(it, mfcc = TestData.angleMfcc(it * 20.0)) }
        val cfg = ShuffleConfig(temperature = 0.5, window = 3)
        val a = ids(shuffle(pool, config = cfg, rng = Random(9)))
        val b = ids(shuffle(pool, config = cfg, rng = Random(9)))
        assertEquals(a, b)
        assertEquals(a.size, a.toSet().size)
    }

    @Test fun noSeed_startsFromAnRngPickedRecording_reproduciblyPerRngSeed() {
        val pool = (1L..8L).map { entry(it) }
        assertEquals(shuffle(pool, seed = null, rng = Random(5)).first().audioContentId,
            shuffle(pool, seed = null, rng = Random(5)).first().audioContentId)
    }

    @Test fun callerPoolOrder_doesNotChangeTheResult() {
        val pool = (1L..6L).map { entry(it, mfcc = TestData.angleMfcc(it * 25.0)) }
        assertEquals(ids(shuffle(pool, seed = 1L)), ids(shuffle(pool.reversed(), seed = 1L)))
    }
}
