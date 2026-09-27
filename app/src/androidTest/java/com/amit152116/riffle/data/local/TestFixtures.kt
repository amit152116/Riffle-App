package com.amit152116.riffle.data.local

import com.amit152116.riffle.data.local.library.AudioContent
import com.amit152116.riffle.data.local.library.IngestError
import com.amit152116.riffle.data.local.library.ScanRun
import com.amit152116.riffle.data.local.library.Track

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
}
