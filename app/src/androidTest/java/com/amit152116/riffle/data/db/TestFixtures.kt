package com.amit152116.riffle.data.db

import com.amit152116.riffle.data.db.library.AudioContent
import com.amit152116.riffle.data.db.library.IngestError
import com.amit152116.riffle.data.db.library.ScanRun
import com.amit152116.riffle.data.db.library.Track

object TestFixtures {
    fun audioContent(
        audioHash: String = "hash-1",
        hashMethod: HashMethod = HashMethod.STREAMHASH,
        duration: Double? = 180.0,
        codec: String? = "mp3",
        sampleRate: Int? = 44100,
        channels: Int? = 2,
        firstSeenAt: Long? = 1_000L
    ) = AudioContent(
        audioHash = audioHash,
        hashMethod = hashMethod,
        duration = duration,
        codec = codec,
        sampleRate = sampleRate,
        channels = channels,
        firstSeenAt = firstSeenAt
    )

    fun scanRun(
        startedAt: Long? = 1_000L,
        completedAt: Long? = 2_000L,
        roots: String? = "[\"/storage/emulated/0/Music\"]",
        status: RunStatus = RunStatus.COMPLETE
    ) = ScanRun(startedAt = startedAt, completedAt = completedAt, roots = roots, status = status)

    fun track(
        path: String = "/storage/emulated/0/Music/song.mp3",
        size: Long? = 4_000_000L,
        mtime: Double? = 1_700_000_000.0,
        dev: Long? = 1L,
        inode: Long? = 100L,
        nlink: Int? = 1,
        audioContentId: Long? = null,
        bitrate: Int? = 320,
        tagTitle: String? = "Test Title",
        tagArtist: String? = "Test Artist",
        tagAlbum: String? = "Test Album",
        tagGenre: String? = "Electronic",
        tagCompleteness: Int = 4,
        lastSeenScanId: Long? = null,
        present: Boolean = true,
        absentReason: AbsentReason? = null,
        scannedAt: Long? = 1_000L
    ) = Track(
        path = path,
        size = size,
        mtime = mtime,
        dev = dev,
        inode = inode,
        nlink = nlink,
        audioContentId = audioContentId,
        bitrate = bitrate,
        tagTitle = tagTitle,
        tagArtist = tagArtist,
        tagAlbum = tagAlbum,
        tagGenre = tagGenre,
        tagCompleteness = tagCompleteness,
        lastSeenScanId = lastSeenScanId,
        present = present,
        absentReason = absentReason,
        scannedAt = scannedAt
    )

    fun ingestError(
        path: String = "/storage/emulated/0/Music/broken.mp3",
        dev: Long? = 1L,
        inode: Long? = 200L,
        size: Long? = 1_000L,
        mtime: Double? = 1_700_000_000.0,
        stage: String = "tag_read",
        message: String? = "Unsupported codec",
        attempts: Int = 1,
        lastAttemptAt: Long? = 1_000L,
        resolvedAt: Long? = null
    ) = IngestError(
        path = path,
        dev = dev,
        inode = inode,
        size = size,
        mtime = mtime,
        stage = stage,
        message = message,
        attempts = attempts,
        lastAttemptAt = lastAttemptAt,
        resolvedAt = resolvedAt
    )

    fun fingerprint(
        audioContentId: Long,
        analyzer: String = "essentia",
        analyzerVersion: String = "1.0.0",
        configHash: String = "cfg-1",
        purpose: FingerprintPurpose = FingerprintPurpose.CANONICAL,
        algorithm: Int? = 1,
        fpRaw: ByteArray = byteArrayOf(1, 2, 3),
        fpLength: Int = 3,
        computedAt: Long? = 1_000L
    ) = com.amit152116.riffle.data.db.fingerprint.Fingerprint(
        audioContentId = audioContentId,
        analyzer = analyzer,
        analyzerVersion = analyzerVersion,
        configHash = configHash,
        purpose = purpose,
        algorithm = algorithm,
        fpRaw = fpRaw,
        fpLength = fpLength,
        computedAt = computedAt
    )

    fun matchRun(
        createdAt: Long? = 1_000L,
        fingerprintConfig: String? = "cfg-1",
        matchConfig: String? = "match-cfg-1",
        softwareVersion: String? = "0.1.0",
        status: RunStatus = RunStatus.COMPLETE
    ) = com.amit152116.riffle.data.db.fingerprint.MatchRun(
        createdAt = createdAt,
        fingerprintConfig = fingerprintConfig,
        matchConfig = matchConfig,
        softwareVersion = softwareVersion,
        status = status
    )

    fun audioPair(
        runId: Long,
        aContentId: Long,
        bContentId: Long,
        bestOffset: Int? = 0,
        peakVotes: Int? = 10,
        peakVoteRatio: Double? = 0.9,
        matchedSpanItems: Int? = 100,
        matchedSpanSeconds: Double? = 30.0,
        coverageA: Double? = 0.95,
        coverageB: Double? = 0.95,
        meanBitError: Double? = 0.01,
        segmentCount: Int? = 1,
        tier: Int? = 1,
        verifiedDirect: Boolean = false
    ) = com.amit152116.riffle.data.db.fingerprint.AudioPair(
        runId = runId,
        aContentId = aContentId,
        bContentId = bContentId,
        bestOffset = bestOffset,
        peakVotes = peakVotes,
        peakVoteRatio = peakVoteRatio,
        matchedSpanItems = matchedSpanItems,
        matchedSpanSeconds = matchedSpanSeconds,
        coverageA = coverageA,
        coverageB = coverageB,
        meanBitError = meanBitError,
        segmentCount = segmentCount,
        tier = tier,
        verifiedDirect = verifiedDirect
    )

    fun dupGroup(
        runId: Long,
        tier: Int = 1,
        formedByChain: Boolean = false,
        decision: DupDecision = DupDecision.PROPOSED,
        decidedAt: Long? = null
    ) = com.amit152116.riffle.data.db.dedup.DupGroup(
        runId = runId,
        tier = tier,
        formedByChain = formedByChain,
        decision = decision,
        decidedAt = decidedAt
    )

    fun audioFeatures(
        audioContentId: Long,
        bpm: Double? = 120.0,
        bpmConfidence: Double? = 0.9,
        keyName: String? = "A",
        scale: String? = "minor",
        keyStrength: Double? = 0.8,
        loudnessLufs: Double? = -12.3,
        danceability: Double? = 0.7,
        energy: Double? = 0.6,
        spectralCentroid: Double? = 2000.0,
        onsetRate: Double? = 4.0,
        dynamicComplexity: Double? = 3.0,
        dissonance: Double? = 0.2,
        zcr: Double? = 0.1,
        mfccMean: ByteArray? = ByteArray(104),
        extractorVersion: String? = "essentia-1.0",
        configHash: String? = "cfg-1",
        analyzedAt: Long? = 1_000L
    ) = com.amit152116.riffle.data.db.analysis.AudioFeatures(
        audioContentId = audioContentId,
        bpm = bpm,
        bpmConfidence = bpmConfidence,
        keyName = keyName,
        scale = scale,
        keyStrength = keyStrength,
        loudnessLufs = loudnessLufs,
        danceability = danceability,
        energy = energy,
        spectralCentroid = spectralCentroid,
        onsetRate = onsetRate,
        dynamicComplexity = dynamicComplexity,
        dissonance = dissonance,
        zcr = zcr,
        mfccMean = mfccMean,
        extractorVersion = extractorVersion,
        configHash = configHash,
        analyzedAt = analyzedAt
    )

    fun musicBrainzMatch(
        audioContentId: Long,
        acoustidId: String? = "acoustid-1",
        recordingMbid: String? = "recording-mbid-1",
        recordingTitle: String? = "Test Recording",
        releaseTitle: String? = "Test Release",
        releaseMbid: String? = "release-mbid-1",
        score: Double? = 0.95,
        parsedAt: Long? = 1_000L
    ) = com.amit152116.riffle.data.db.musicbrainz.MusicBrainzMatch(
        audioContentId = audioContentId,
        acoustidId = acoustidId,
        recordingMbid = recordingMbid,
        recordingTitle = recordingTitle,
        releaseTitle = releaseTitle,
        releaseMbid = releaseMbid,
        score = score,
        parsedAt = parsedAt
    )

    fun clusterRun(
        nClusters: Int = 8,
        nTracks: Int = 100,
        scalerParams: ByteArray? = ByteArray(16),
        createdAt: Long? = 1_000L
    ) = com.amit152116.riffle.data.db.cluster.ClusterRun(
        nClusters = nClusters,
        nTracks = nTracks,
        scalerParams = scalerParams,
        createdAt = createdAt
    )

    fun trackSimilarity(
        trackId: Long,
        neighborId: Long,
        mfccNorm: Double = 0.1,
        bpmNorm: Double? = 0.05,
        keyNorm: Double? = 0.0,
        energyNorm: Double? = 0.02,
        combinedScore: Double = 0.9,
        configHash: String? = "cfg-1"
    ) = com.amit152116.riffle.data.db.cluster.TrackSimilarity(
        trackId = trackId,
        neighborId = neighborId,
        mfccNorm = mfccNorm,
        bpmNorm = bpmNorm,
        keyNorm = keyNorm,
        energyNorm = energyNorm,
        combinedScore = combinedScore,
        configHash = configHash
    )

    fun playlist(name: String = "My Playlist", createdAt: Long = 1_000L) =
        com.amit152116.riffle.data.db.playback.Playlist(name = name, createdAt = createdAt)
}
