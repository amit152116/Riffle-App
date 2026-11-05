package com.amit152116.riffle.data.db.analysis

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.TestFixtures
import com.amit152116.riffle.data.db.fingerprint.FingerprintRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisResultWriterTest {
    private lateinit var db: RiffleDatabase
    private lateinit var writer: AnalysisResultWriter
    private var contentId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RiffleDatabase::class.java).build()
        val analysisRepository = AnalysisRepository(db.analysisDao())
        val fingerprintRepository = FingerprintRepository(db.fingerprintDao())
        writer = AnalysisResultWriter(db, analysisRepository, fingerprintRepository)
        contentId = db.libraryDao().insertAudioContent(TestFixtures.audioContent())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun write_bothInsertsSucceed_bothRowsPersisted() = runBlocking {
        writer.write(
            TestFixtures.audioFeatures(audioContentId = contentId),
            TestFixtures.fingerprint(audioContentId = contentId)
        )

        assertNotNull(db.analysisDao().getAudioFeaturesByAudioContentId(contentId))
    }

    @Test
    fun write_nullFingerprint_writesAudioFeaturesOnlySkipsFingerprintInsert() = runBlocking {
        writer.write(TestFixtures.audioFeatures(audioContentId = contentId), null)

        assertNotNull(db.analysisDao().getAudioFeaturesByAudioContentId(contentId))
        assertNull(db.fingerprintDao().getFingerprintByAudioContentId(contentId))
    }

    @Test
    fun write_secondInsertFails_rollsBackBoth() {
        // A second Fingerprint for the same (audioContentId, analyzer, analyzerVersion,
        // configHash, purpose, algorithm) violates Fingerprint's unique index - pre-seeding
        // one and reusing the exact same fixture values forces insertFingerprint to fail
        // inside the transaction, while insertAudioFeatures for the same content id would
        // otherwise succeed cleanly (AudioFeatures.audioContentId is a separate unique index).
        runBlocking { db.fingerprintDao().insertFingerprint(TestFixtures.fingerprint(audioContentId = contentId)) }

        assertThrows(Exception::class.java) {
            runBlocking {
                writer.write(
                    TestFixtures.audioFeatures(audioContentId = contentId),
                    TestFixtures.fingerprint(audioContentId = contentId)
                )
            }
        }

        runBlocking {
            assertNull(
                "AudioFeatures insert must have rolled back when the Fingerprint insert failed",
                db.analysisDao().getAudioFeaturesByAudioContentId(contentId)
            )
        }
    }
}
