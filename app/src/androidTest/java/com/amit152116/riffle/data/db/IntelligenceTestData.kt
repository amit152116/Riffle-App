package com.amit152116.riffle.data.db

import com.amit152116.riffle.intelligence.model.toLittleEndianBytes

object IntelligenceTestData {
    /**
     * One recording with [copies] present-or-absent Tracks and features of well-separated group
     * [blob] (< 13). Returns the AudioContent id.
     */
    suspend fun insertRecording(
        db: RiffleDatabase,
        hash: String,
        blob: Int,
        jitter: Double = 0.0,
        copies: Int = 1,
        present: Boolean = true,
        genre: String? = "Rock",
        artist: String? = "Artist $hash"
    ): Long {
        val id = db.libraryDao().insertAudioContent(TestFixtures.audioContent(audioHash = hash))
        val mfcc = DoubleArray(13).also { it[blob] = 10.0 * (blob + 1) + jitter }
        db.analysisDao().insertAudioFeatures(
            TestFixtures.audioFeatures(
                audioContentId = id,
                bpm = 80.0 + 25.0 * blob + jitter,
                energy = 0.2 + 0.15 * blob + jitter / 100.0,
                danceability = 0.3 + 0.1 * blob,
                loudnessLufs = -20.0 + 3.0 * blob,
                spectralCentroid = 1000.0 + 500.0 * blob,
                onsetRate = 2.0 + blob,
                mfccMean = mfcc.toLittleEndianBytes()
            )
        )
        repeat(copies) { c ->
            db.libraryDao().insertTrack(
                TestFixtures.track(
                    path = "/$hash-$c.mp3", audioContentId = id, mediaStoreId = null, tagGenre = genre,
                    tagArtist = artist, tagTitle = hash, present = present,
                    absentReason = if (present) null else AbsentReason.MISSING
                )
            )
        }
        return id
    }

    /** blobCount well-separated groups of perBlob recordings; ids in insertion order. */
    suspend fun insertBlobs(db: RiffleDatabase, blobCount: Int, perBlob: Int, prefix: String = "h"): List<Long> {
        val ids = ArrayList<Long>()
        for (b in 0 until blobCount) repeat(perBlob) { i ->
            ids += insertRecording(db, "$prefix$b-$i", b, jitter = (i % 10) / 10.0)
        }
        return ids
    }
}
