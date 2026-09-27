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

        // Insert with b, a (reversed) - repository must store it as (a, b).
        repository.insertAudioPair(
            TestFixtures.audioPair(runId = runId, aContentId = contentB, bContentId = contentA)
        )

        val stored = repository.getAudioPair(runId, contentA, contentB)
        assertEquals(contentA, stored!!.aContentId)
        assertEquals(contentB, stored.bContentId)
    }
}
