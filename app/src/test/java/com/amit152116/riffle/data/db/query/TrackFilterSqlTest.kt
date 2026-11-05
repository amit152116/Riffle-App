package com.amit152116.riffle.data.db.query

import com.amit152116.riffle.intelligence.model.EligibilityConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackFilterSqlTest {
    private fun build(f: TrackFilter = TrackFilter(), run: Long? = 7L, e: EligibilityConfig = EligibilityConfig()) =
        TrackFilterSql.build(f, run, e)

    @Test fun baseQuery_guardsPresenceRepresentativeBlacklistAndEligibility() {
        val sql = build().sql
        assertTrue(sql.contains("t.present = 1"))
        assertTrue(sql.contains("af.mfccMean IS NOT NULL AND af.energy IS NOT NULL"))
        assertTrue(sql.contains("SELECT MIN(t2.id) FROM Track t2"))
        assertTrue(sql.contains("t2.id NOT IN (SELECT trackId FROM BlacklistedTrack)"))
        assertTrue(sql.contains("t.id NOT IN (SELECT trackId FROM BlacklistedTrack)"))
    }

    @Test fun eligibility_excludesShortAndSilentAudio_withBoundThresholds() {
        val q = build()
        assertTrue(q.sql.contains("JOIN AudioContent ac ON ac.id = t.audioContentId"))
        assertTrue(q.sql.contains("(ac.duration IS NULL OR ac.duration >= ?)"))
        assertTrue(q.sql.contains("(af.loudnessLufs IS NULL OR af.loudnessLufs > ?)"))
        assertEquals(30.0, q.args[1])
        assertEquals(-50.0, q.args[2])
        val custom = build(e = EligibilityConfig(minDurationSeconds = 45.0, silenceLufsFloor = -40.0))
        assertEquals(45.0, custom.args[1])
        assertEquals(-40.0, custom.args[2])
    }

    @Test fun latestRunId_isTheFirstBoundArgument_orMinusOneWhenAbsent() {
        assertEquals(7L, build(run = 7L).args[0])
        assertEquals(-1L, build(run = null).args[0])
    }

    @Test fun genreFilter_escapesLikeWildcards() {
        val q = build(TrackFilter(genre = "100%_\\x"))
        assertTrue(q.sql.contains("t.tagGenre LIKE ? ESCAPE '\\'"))
        assertEquals("%100\\%\\_\\\\x%", q.args[3])
    }

    @Test fun bpmRange_isMinInclusiveMaxExclusive_inArgumentOrder() {
        val q = build(TrackFilter(bpmMin = 100.0, bpmMax = 130.0))
        assertTrue(q.sql.contains("af.bpm >= ?"))
        assertTrue(q.sql.contains("af.bpm < ?"))
        assertEquals(listOf<Any?>(7L, 30.0, -50.0, 100.0, 130.0, -1L, 0L), q.args)
    }

    @Test fun keyAndScale_energyRange_andCluster() {
        val q = build(TrackFilter(keyName = "A", scale = "minor", energyMin = 0.2, energyMax = 0.8, clusterId = 3))
        assertTrue(q.sql.contains("af.keyName = ?") && q.sql.contains("af.scale = ?"))
        assertTrue(q.sql.contains("af.energy >= ?") && q.sql.contains("af.energy <= ?"))
        assertTrue(q.sql.contains("ca.clusterId = ?"))
        assertEquals(listOf<Any?>(7L, 30.0, -50.0, "A", "minor", 0.2, 0.8, 3L, -1L, 0L), q.args)
    }

    @Test fun clusterFilter_withoutAnyRun_matchesNothing() {
        val q = build(TrackFilter(clusterId = 3), run = null)
        assertTrue(q.sql.contains("AND 0"))
        assertFalse(q.sql.contains("ca.clusterId = ?"))
    }

    @Test fun explicitIds_areBound_emptyMatchesNothing_andTooManyIsRejected() {
        val q = build(TrackFilter(audioContentIds = setOf(1L, 2L)))
        assertTrue(q.sql.contains("t.audioContentId IN (?,?)"))
        assertTrue(build(TrackFilter(audioContentIds = emptySet())).sql.contains("AND 0"))
        assertThrows(IllegalArgumentException::class.java) {
            build(TrackFilter(audioContentIds = (1L..501L).toSet()))
        }
    }

    @Test fun bpmSort_putsNullAndNonPositiveLast_thenValue_thenTrackId() {
        val sql = build(TrackFilter(sort = TrackSort.BPM, direction = SortDirection.DESC)).sql
        assertTrue(sql.contains("ORDER BY CASE WHEN (af.bpm IS NULL OR af.bpm <= 0) THEN 1 ELSE 0 END, af.bpm DESC, t.id ASC"))
    }

    @Test fun everySort_isWhitelisted_andBindsNothingExtra() {
        for (sort in TrackSort.values()) for (dir in SortDirection.values()) {
            val q = build(TrackFilter(sort = sort, direction = dir))
            assertTrue(q.sql.contains("ORDER BY CASE WHEN"))
            assertEquals(q.sql.count { it == '?' }, q.args.size)
        }
    }

    @Test fun limitAndOffset_areBound_nullLimitMeansUnlimited() {
        assertEquals(listOf<Any?>(7L, 30.0, -50.0, 5L, 10L), build(TrackFilter(limit = 5, offset = 10)).args)
        assertEquals(-1L, build().args[3])
        assertThrows(IllegalArgumentException::class.java) { build(TrackFilter(offset = -1)) }
    }

    @Test fun userText_isNeverInterpolatedIntoSql() {
        val evil = "'; DROP TABLE Track;--"
        val q = build(TrackFilter(genre = evil, keyName = evil))
        assertFalse(q.sql.contains("DROP TABLE"))
        assertTrue(q.args.any { it.toString().contains("DROP TABLE") })
    }
}
