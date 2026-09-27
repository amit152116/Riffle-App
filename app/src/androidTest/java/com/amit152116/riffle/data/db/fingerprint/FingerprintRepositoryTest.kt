package com.amit152116.riffle.data.db.fingerprint

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.TestFixtures
import com.amit152116.riffle.data.db.library.LibraryDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FingerprintRepositoryTest {
    private lateinit var db: RiffleDatabase
    private lateinit var repository: FingerprintRepository
    private lateinit var libraryDao: LibraryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RiffleDatabase::class.java).build()
        repository = FingerprintRepository(db.fingerprintDao())
        libraryDao = db.libraryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAudioPair_reversedOrder_normalizesToSamePair() = runBlocking {
        val contentA = libraryDao.insertAudioContent(TestFixtures.audioContent(audioHash = "a"))
        val contentB = libraryDao.insertAudioContent(TestFixtures.audioContent(audioHash = "b"))
        val runId = repository.insertMatchRun(TestFixtures.matchRun())

        // Discovered as (b, a) with asymmetric values so a naive field-copy
        // bug (e.g. forgetting to negate bestOffset) can't hide behind
        // coincidentally-equal inputs. bestOffset here means
        // pos(contentB) - pos(contentA) = 42 under the AS-DISCOVERED labels.
        repository.insertAudioPair(
            TestFixtures.audioPair(
                runId = runId,
                aContentId = contentB,
                bContentId = contentA,
                bestOffset = 42,
                coverageA = 0.80,
                coverageB = 0.60
            )
        )

        // Repository must store it as (a, b): fields swapped AND bestOffset
        // negated, since it flips the pos(a) - pos(b) convention's sign.
        val stored = repository.getAudioPair(runId, contentA, contentB)
        assertEquals(contentA, stored!!.aContentId)
        assertEquals(contentB, stored.bContentId)
        assertEquals(-42, stored.bestOffset)
        assertEquals(0.60, stored.coverageA)
        assertEquals(0.80, stored.coverageB)
    }

    @Test
    fun insertAudioPair_sameUnorderedPairTwice_isRecognizedAsOneRow() = runBlocking {
        val contentA = libraryDao.insertAudioContent(TestFixtures.audioContent(audioHash = "a"))
        val contentB = libraryDao.insertAudioContent(TestFixtures.audioContent(audioHash = "b"))
        val runId = repository.insertMatchRun(TestFixtures.matchRun())

        repository.insertAudioPair(
            TestFixtures.audioPair(runId = runId, aContentId = contentA, bContentId = contentB)
        )
        // Same pair, discovered again in reversed order - must not create a
        // second row; the duplicate insert is silently ignored, keeping the
        // first-recorded values.
        repository.insertAudioPair(
            TestFixtures.audioPair(
                runId = runId,
                aContentId = contentB,
                bContentId = contentA,
                tier = 2
            )
        )

        val rowCount = repository.countAudioPairs(runId)
        assertEquals(1, rowCount)
        val stored = repository.getAudioPair(runId, contentA, contentB)
        assertEquals(1, stored!!.tier)
    }
}
