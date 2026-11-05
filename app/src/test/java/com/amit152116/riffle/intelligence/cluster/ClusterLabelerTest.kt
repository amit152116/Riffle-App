package com.amit152116.riffle.intelligence.cluster

import com.amit152116.riffle.intelligence.TestData.rec
import com.amit152116.riffle.intelligence.model.ClusterConfig
import com.amit152116.riffle.intelligence.model.ClusterMode
import com.amit152116.riffle.intelligence.model.EnergyBand
import com.amit152116.riffle.intelligence.model.TempoBand
import com.amit152116.riffle.intelligence.model.TimbreBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClusterLabelerTest {
    private val cfg = ClusterConfig()

    // bpm 60..176 (terciles ~98.3 / ~136.6), energy 0..1 (terciles 0.33 / 0.66), centroid 1000..1290 (median 1150)
    private val library = (0 until 30).map { i ->
        rec(i + 1L, bpm = 60.0 + 4 * i, energy = i / 29.0, centroid = 1000.0 + 10 * i,
            scale = if (i % 2 == 0) "major" else "minor")
    }
    private val labeler = ClusterLabeler(cfg, library)

    private fun members(count: Int, bpm: Double, energy: Double, centroid: Double, scale: String?) =
        (1..count).map { rec(100L + it, bpm = bpm, energy = energy, centroid = centroid, scale = scale) }

    @Test fun upbeatEnergeticMajorBright() {
        val l = labeler.label(members(3, 170.0, 0.9, 1290.0, "major"))
        assertEquals(TempoBand.UPBEAT, l.tempo)
        assertEquals(EnergyBand.ENERGETIC, l.energy)
        assertEquals(ClusterMode.MAJOR, l.mode)
        assertEquals(TimbreBand.BRIGHT, l.timbre)
        assertEquals("Upbeat Energetic Major Bright", l.baseName)
    }

    @Test fun slowCalmMinorWarm() =
        assertEquals("Slow Calm Minor Warm", labeler.label(members(3, 65.0, 0.05, 1010.0, "minor")).baseName)

    @Test fun midTempoModerate() {
        val l = labeler.label(members(3, 110.0, 0.5, 1150.0, "major"))
        assertEquals(TempoBand.MID, l.tempo)
        assertEquals(EnergyBand.MODERATE, l.energy)
    }

    @Test fun smallLibrary_usesFallbackCutoffs() {
        val small = ClusterLabeler(cfg, (1..5).map { rec(it.toLong()) })
        assertEquals(TempoBand.UPBEAT, small.label(members(2, 130.0, 0.5, 1500.0, "major")).tempo)
        assertEquals(TempoBand.SLOW, small.label(members(2, 70.0, 0.5, 1500.0, "major")).tempo)
        assertEquals(EnergyBand.CALM, small.label(members(2, 100.0, 0.2, 1500.0, "major")).energy)
    }

    @Test fun modeShares_andTies() {
        fun modeOf(major: Int, minor: Int): ClusterMode {
            val ms = (1..major).map { rec(200L + it, scale = "major") } + (1..minor).map { rec(300L + it, scale = "minor") }
            return labeler.label(ms).mode
        }
        assertEquals(ClusterMode.MAJOR, modeOf(3, 2))   // 0.6 >= 0.6
        assertEquals(ClusterMode.MINOR, modeOf(2, 3))   // 0.4 <= 0.4
        assertEquals(ClusterMode.MIXED, modeOf(1, 1))   // 0.5
    }

    @Test fun mixedAndUnknownMode_areOmittedFromTheName() {
        val mixed = labeler.label(listOf(rec(1, scale = "major"), rec(2, scale = "minor")))
        assertEquals(ClusterMode.MIXED, mixed.mode)
        assertEquals(false, mixed.baseName.contains("Major") || mixed.baseName.contains("Minor") || mixed.baseName.contains("Mixed"))
        val unknown = labeler.label(listOf(rec(3, scale = null), rec(4, scale = null)))
        assertEquals(ClusterMode.UNKNOWN, unknown.mode)
    }

    @Test fun noValidBpm_omitsTempo() {
        val l = labeler.label(listOf(rec(1, bpm = 0.0), rec(2, bpm = null)))
        assertNull(l.tempo)
        assertEquals(false, listOf("Slow", "Mid-tempo", "Upbeat").any { l.baseName.contains(it) })
    }

    @Test fun uniqueNames_suffixInClusterIdOrder_firstKeepsBareName() {
        assertEquals(
            mapOf(4 to "A", 8 to "A · 2", 5 to "B"),
            ClusterLabeler.uniqueNames(mapOf(8 to "A", 4 to "A", 5 to "B"))
        )
        assertEquals(
            mapOf(1 to "A", 2 to "A · 2", 3 to "A · 3"),
            ClusterLabeler.uniqueNames(mapOf(3 to "A", 1 to "A", 2 to "A"))
        )
    }

    @Test fun uniqueNames_suffixIsCosmetic_mayShiftWhenTheSetChanges() {
        assertEquals(mapOf(8 to "A", 12 to "A · 2"), ClusterLabeler.uniqueNames(mapOf(8 to "A", 12 to "A")))
    }
}
