package com.amit152116.riffle.data.db.analysis

import androidx.room.withTransaction
import com.amit152116.riffle.data.db.RiffleDatabase
import com.amit152116.riffle.data.db.fingerprint.Fingerprint
import com.amit152116.riffle.data.db.fingerprint.FingerprintRepository

/**
 * Writes one AudioFeatures + one Fingerprint row atomically. Room's @Transaction only
 * coordinates calls within a single DAO; these two entities live on different DAOs
 * (AnalysisDao, FingerprintDao), so cross-DAO atomicity needs the database-level
 * withTransaction instead. Without this, a crash between the two inserts wouldn't
 * corrupt anything (the pending query already treats "either missing" as pending) but
 * would waste a full re-extraction next run - this makes that case impossible instead.
 */
class AnalysisResultWriter(
    private val database: RiffleDatabase,
    private val analysisRepository: AnalysisRepository,
    private val fingerprintRepository: FingerprintRepository
) {
    /**
     * [fingerprint] is null when chromaprint failed for this audio (see
     * DspAudioFeatures.toFingerprint's kdoc) - the AudioFeatures row is still written (that
     * part of the extraction succeeded), but no Fingerprint row is inserted, so the pending
     * query still finds this AudioContent missing a fingerprint and retries it later.
     */
    suspend fun write(audioFeatures: AudioFeatures, fingerprint: Fingerprint?) {
        database.withTransaction {
            analysisRepository.insertAudioFeatures(audioFeatures)
            fingerprint?.let { fingerprintRepository.insertFingerprint(it) }
        }
    }
}
