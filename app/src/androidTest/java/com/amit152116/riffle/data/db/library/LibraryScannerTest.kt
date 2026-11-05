package com.amit152116.riffle.data.db.library

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.TestFixtures
import com.amit152116.riffle.data.db.analysis.AnalysisRepository
import com.amit152116.riffle.data.db.analysis.AnalysisResultWriter
import com.amit152116.riffle.data.db.fingerprint.FingerprintRepository
import com.amit152116.riffle.dsp.EssentiaExtractor
import com.amit152116.riffle.permission.MediaPermission
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

private const val TAG = "LibraryScannerTest"

/**
 * Runs LibraryScanner against the REAL device MediaStore (an in-memory Room DB keeps it from
 * touching the app's actual riffle.db). Assertions are conditional on the device actually
 * having music in MediaStore, since that's environment-dependent -- but on a device that does,
 * this exercises the real streamhash/whole-file-fallback path end to end, not synthetic data.
 *
 * The data-verifying tests skip (via assumeTrue, not a silent pass) if READ_MEDIA_AUDIO isn't
 * granted -- can't be worked around from adb on every device/OEM (confirmed on a real OPPO
 * device: shell lacks GRANT_RUNTIME_PERMISSIONS here), so it needs a real permission grant,
 * not a test workaround.
 */
@RunWith(AndroidJUnit4::class)
class LibraryScannerTest {
    private lateinit var db: RiffleDatabase
    private lateinit var repository: LibraryRepository
    private lateinit var scanner: LibraryScanner
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, RiffleDatabase::class.java).build()
        repository = LibraryRepository(db.libraryDao())
        val extractor = lazy { EssentiaExtractor() }
        val resultWriter = AnalysisResultWriter(
            db, AnalysisRepository(db.analysisDao()), FingerprintRepository(db.fingerprintDao())
        )
        scanner = LibraryScanner(context, repository, extractor, resultWriter)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun hasMediaPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, MediaPermission.readAudio) ==
            PackageManager.PERMISSION_GRANTED

    @Test
    fun scan_withoutPermission_throwsInsteadOfSilentlyReportingZero() {
        assumeTrue(
            "This test needs READ_MEDIA_AUDIO NOT granted; revoke it to run this one",
            !hasMediaPermission()
        )
        assertThrows(MissingMediaPermissionException::class.java) {
            runBlocking { scanner.scan() }
        }
    }

    @Test
    fun scan_populatesTracksFromRealMediaStore() = runBlocking {
        assumeTrue("READ_MEDIA_AUDIO not granted on this device/run", hasMediaPermission())

        val result = scanner.scan()

        assertTrue("scanned should never be negative", result.scanned >= 0)
        assertTrue("failed should never be negative", result.failed >= 0)

        if (result.scanned == 0) {
            Log.w(TAG, "Permission granted but MediaStore reported 0 tracks -- device has no music?")
            return@runBlocking
        }
        // scan() is the fast metadata pass only; AudioContent rows come from the hash pass.
        scanner.hashPendingTracks()

        val firstTrack = db.libraryDao().let { dao ->
            // Any inserted track will do -- just need one row to follow the FK chain.
            dao.getTrackById(1L) ?: error("Expected at least one Track row after a successful scan")
        }
        assertNotNull(firstTrack.audioContentId)
        val content = repository.getAudioContentById(firstTrack.audioContentId!!)
        assertNotNull("Track's AudioContent should exist", content)
        assertTrue(
            "audioHash should be non-empty",
            content!!.audioHash.isNotEmpty()
        )
    }

    @Test
    fun scan_twice_isIdempotent_noDuplicateRows() = runBlocking {
        assumeTrue("READ_MEDIA_AUDIO not granted on this device/run", hasMediaPermission())

        val first = scanner.scan()
        if (first.scanned == 0) return@runBlocking // nothing to verify idempotency against

        val second = scanner.scan()
        scanner.hashPendingTracks() // scan() no longer hashes; AudioContent rows come from this pass

        assertEquals(
            "Same MediaStore content should scan the same track count both times",
            first.scanned, second.scanned
        )

        // Re-scanning must UPDATE existing Track rows (by path), not insert duplicates: total
        // AudioContent count should not grow just because we scanned twice.
        var contentCount = 0
        var id = 1L
        while (repository.getAudioContentById(id) != null) {
            contentCount++
            id++
        }
        assertTrue(
            "AudioContent count should be reasonable relative to scanned tracks " +
                "(dedup-aware: can be <= scanned, never wildly larger from duplicate inserts)",
            contentCount in 1..first.scanned
        )
    }

    /** A minimal real, decodable 1-second mono 440Hz tone, written as 16-bit PCM WAV. */
    private fun writeMonoPcmWav(file: File, sampleRate: Int = 44100, seconds: Double = 1.0) {
        val totalSamples = (sampleRate * seconds).toInt()
        val dataSize = totalSamples * 2
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            fun writeIntLe(v: Int) {
                raf.write(
                    byteArrayOf(
                        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
                        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
                    )
                )
            }
            fun writeShortLe(v: Int) {
                raf.write(byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte()))
            }
            raf.writeBytes("RIFF")
            writeIntLe(36 + dataSize)
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            writeIntLe(16)
            writeShortLe(1) // PCM
            writeShortLe(1) // mono
            writeIntLe(sampleRate)
            writeIntLe(sampleRate * 2) // byte rate = sampleRate * channels(1) * bytesPerSample(2)
            writeShortLe(2) // block align
            writeShortLe(16) // bits per sample
            raf.writeBytes("data")
            writeIntLe(dataSize)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val sample = (sin(2.0 * PI * 440.0 * t) * 20000).toInt()
                writeShortLe(sample and 0xFFFF)
            }
        }
    }

    @Test
    fun analyzePendingAudioContent_writesFeaturesAndFingerprintForPresentTrack() {
        val wavFile = File(context.cacheDir, "scanner_test_tone.wav")
        writeMonoPcmWav(wavFile)
        val realWavPath = Uri.fromFile(wavFile).toString()
        runBlocking {
            val contentId = db.libraryDao().insertAudioContent(TestFixtures.audioContent())
            db.libraryDao().insertTrack(TestFixtures.track(path = realWavPath, audioContentId = contentId))

            val result = scanner.analyzePendingAudioContent()

            assertEquals(1, result.analyzed)
            assertEquals(0, result.failed)
            assertNotNull(db.analysisDao().getAudioFeaturesByAudioContentId(contentId))
        }
        wavFile.delete()
    }

    @Test
    fun analyzePendingAudioContent_duplicateTracksShareOneAudioContent_analyzesOnce() {
        val wavFile = File(context.cacheDir, "scanner_test_tone_dup.wav")
        writeMonoPcmWav(wavFile)
        val realWavPath = Uri.fromFile(wavFile).toString()
        runBlocking {
            val contentId = db.libraryDao().insertAudioContent(TestFixtures.audioContent())
            db.libraryDao().insertTrack(TestFixtures.track(path = realWavPath, audioContentId = contentId))
            db.libraryDao().insertTrack(TestFixtures.track(path = "$realWavPath.dup", audioContentId = contentId))

            val result = scanner.analyzePendingAudioContent()

            // Not just analyzed == 1: if the pending query ever regressed to return the same
            // AudioContent twice, a second extraction attempt would collide on Fingerprint's
            // unique index and roll back, giving analyzed=1, failed=1 - which analyzed == 1
            // alone wouldn't catch. failed == 0 is what actually proves "exactly once."
            assertEquals(1, result.analyzed)
            assertEquals(0, result.failed)
        }
        wavFile.delete()
    }

    @Test
    fun analyzePendingAudioContent_onlyAbsentSibling_isNoOpNotCountedAsFailure() = runBlocking {
        // An AudioContent whose only track is absent never becomes "pending" in the first
        // place (getAudioContentNeedingAnalysis's own EXISTS(present=1) guard, see
        // LibraryDaoTest) - the for-loop here never iterates it, so it's correctly a no-op,
        // not a failure. The `path == null` defensive branch in analyzePendingAudioContent
        // guards a narrower race (a sibling going absent between the pending-query snapshot
        // and getAnyPresentTrackPath within the same run) that this cannot exercise.
        val contentId = db.libraryDao().insertAudioContent(TestFixtures.audioContent())
        db.libraryDao().insertTrack(TestFixtures.track(path = "/gone.mp3", audioContentId = contentId, present = false))

        val result = scanner.analyzePendingAudioContent()

        assertEquals(0, result.analyzed)
        assertEquals(0, result.failed)
    }

    @Test
    fun analyzePendingAudioContent_extractionThrows_logsIngestErrorAndContinues() {
        val wavFile = File(context.cacheDir, "scanner_test_tone_after_failure.wav")
        writeMonoPcmWav(wavFile)
        runBlocking {
            // getAudioContentNeedingAnalysis has no ORDER BY, so rows come back in rowid order -
            // inserting the bad AudioContent first and a good, real one second is what actually
            // proves "continues to the NEXT row" rather than just "catches one failure."
            val badContentId = db.libraryDao().insertAudioContent(TestFixtures.audioContent(audioHash = "bad-hash"))
            // Not a real audio file - MediaExtractor.setDataSource fails, extractContent throws.
            db.libraryDao().insertTrack(
                TestFixtures.track(path = "content://media/external/audio/media/999999", audioContentId = badContentId)
            )

            val goodContentId = db.libraryDao().insertAudioContent(TestFixtures.audioContent(audioHash = "good-hash"))
            db.libraryDao().insertTrack(
                TestFixtures.track(path = Uri.fromFile(wavFile).toString(), audioContentId = goodContentId)
            )

            val result = scanner.analyzePendingAudioContent()

            assertEquals(1, result.analyzed)
            assertEquals(1, result.failed)
            assertNotNull(db.libraryDao().getIngestErrorByPath("content://media/external/audio/media/999999"))
            assertNotNull(
                "the good row after the bad one must still get analyzed, proving the loop continues",
                db.analysisDao().getAudioFeaturesByAudioContentId(goodContentId)
            )
        }
        wavFile.delete()
    }

    @Test
    fun analyzePendingAudioContent_secondRunWithNothingPending_doesNoWork() {
        val wavFile = File(context.cacheDir, "scanner_test_tone_second.wav")
        writeMonoPcmWav(wavFile)
        val realWavPath = Uri.fromFile(wavFile).toString()
        runBlocking {
            val contentId = db.libraryDao().insertAudioContent(TestFixtures.audioContent())
            db.libraryDao().insertTrack(TestFixtures.track(path = realWavPath, audioContentId = contentId))
            scanner.analyzePendingAudioContent()

            val second = scanner.analyzePendingAudioContent()

            assertEquals(0, second.analyzed)
            assertEquals(0, second.failed)
        }
        wavFile.delete()
    }
}
