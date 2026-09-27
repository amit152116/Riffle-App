package com.amit152116.riffle.data.db.fingerprint

class FingerprintRepository(private val dao: FingerprintDao) {
    suspend fun insertFingerprint(fingerprint: Fingerprint) = dao.insertFingerprint(fingerprint)
    suspend fun insertMatchRun(matchRun: MatchRun) = dao.insertMatchRun(matchRun)
    suspend fun insertRunTrack(runTrack: RunTrack) = dao.insertRunTrack(runTrack)
    suspend fun getFingerprintById(id: Long) = dao.getFingerprintById(id)
    suspend fun getRunTrack(runId: Long, trackId: Long) = dao.getRunTrack(runId, trackId)
    suspend fun countAudioPairs(runId: Long) = dao.countAudioPairs(runId)

    /**
     * Normalizes to aContentId < bContentId before storing, so a pair discovered
     * as (b, a) is recognized as the same row as one discovered as (a, b).
     * bestOffset is defined as pos(a) - pos(b) under the AS-DISCOVERED labels
     * (matches riffle's match.py convention), so swapping a/b must negate it.
     */
    suspend fun insertAudioPair(pair: AudioPair) {
        val normalized = if (pair.aContentId < pair.bContentId) {
            pair
        } else {
            pair.copy(
                aContentId = pair.bContentId,
                bContentId = pair.aContentId,
                bestOffset = pair.bestOffset?.let { -it },
                coverageA = pair.coverageB,
                coverageB = pair.coverageA
            )
        }
        dao.insertAudioPair(normalized)
    }

    suspend fun getAudioPair(runId: Long, contentIdOne: Long, contentIdTwo: Long): AudioPair? {
        val (a, b) = if (contentIdOne < contentIdTwo) {
            contentIdOne to contentIdTwo
        } else {
            contentIdTwo to contentIdOne
        }
        return dao.getAudioPair(runId, a, b)
    }
}
