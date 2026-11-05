package com.amit152116.riffle.analysis

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amit152116.riffle.data.db.analysis.toRoomAudioFeatures
import com.amit152116.riffle.dsp.EssentiaExtractor
import com.amit152116.riffle.dsp.PcmStreamDecoder
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Re-analyses tracks already stored in the app's own DB and compares every feature bit-for-bit with what an
 * earlier build produced. Proves a build change (compiler flags, parallelism) is lossless.
 * Run with `-e goldenBeforeMs <epoch ms>` = only compare rows analysed before that instant. Read-only on the DB.
 */
@RunWith(AndroidJUnit4::class)
class GoldenFeatureRegressionTest {
    @Test fun reanalysis_matchesStoredFeaturesExactly() {
        val args = InstrumentationRegistry.getArguments()
        val before = args.getString("goldenBeforeMs")?.toLongOrNull()
        assumeTrue("pass -e goldenBeforeMs <epoch ms>", before != null)
        val limit = args.getString("goldenLimit")?.toIntOrNull() ?: 8
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val path = context.getDatabasePath("riffle.db").absolutePath
        val analyzer = TrackAnalyzer(EssentiaExtractor())
        val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
        var compared = 0
        var mismatches = 0
        db.use {
            it.rawQuery(
                """
                SELECT af.audioContentId, af.bpm, af.keyName, af.scale, af.keyStrength, af.loudnessLufs, af.danceability, af.energy,
                       af.spectralCentroid, af.onsetRate, af.dynamicComplexity, af.dissonance, af.zcr, af.mfccMean,
                       (SELECT MIN(t.path) FROM Track t WHERE t.audioContentId = af.audioContentId AND t.present = 1)
                FROM AudioFeatures af WHERE af.analyzedAt < ? ORDER BY af.audioContentId LIMIT ?
                """.trimIndent(),
                arrayOf(before.toString(), limit.toString())
            ).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val track = c.getString(14) ?: continue
                    val f = analyzer.analyze { PcmStreamDecoder.open(context, Uri.parse(track)) }.toRoomAudioFeatures(id, 0L)
                    fun d(i: Int) = if (c.isNull(i)) null else c.getDouble(i)
                    val same = f.bpm == d(1) && f.keyName == c.getString(2) && f.scale == c.getString(3) &&
                        f.keyStrength == d(4) && f.loudnessLufs == d(5) && f.danceability == d(6) && f.energy == d(7) &&
                        f.spectralCentroid == d(8) && f.onsetRate == d(9) && f.dynamicComplexity == d(10) &&
                        f.dissonance == d(11) && f.zcr == d(12) && f.mfccMean.contentEquals(c.getBlob(13))
                    compared++
                    if (!same) {
                        mismatches++
                        Log.w("RiffleGolden", "MISMATCH id=$id bpm ${f.bpm} vs ${d(1)}; energy ${f.energy} vs ${d(7)}; lufs ${f.loudnessLufs} vs ${d(5)}; centroid ${f.spectralCentroid} vs ${d(8)}")
                    }
                }
            }
        }
        Log.i("RiffleGolden", "compared=$compared mismatches=$mismatches")
        assertTrue("nothing to compare", compared > 0)
        assertTrue("$mismatches of $compared tracks differ from the stored features", mismatches == 0)
    }
}
