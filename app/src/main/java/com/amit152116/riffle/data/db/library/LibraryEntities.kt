package com.amit152116.riffle.data.db.library

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amit152116.riffle.data.db.AbsentReason
import com.amit152116.riffle.data.db.HashMethod
import com.amit152116.riffle.data.db.RunStatus

@Entity(indices = [Index("audioHash", "hashMethod", unique = true)])
data class AudioContent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audioHash: String,
    val hashMethod: HashMethod,
    val duration: Double?,
    val codec: String?,
    val sampleRate: Int?,
    val channels: Int?,
    val firstSeenAt: Long?
)

@Entity
data class ScanRun(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long?,
    val completedAt: Long?,
    val roots: String?,
    val status: RunStatus
)

@Entity(
    foreignKeys = [
        ForeignKey(AudioContent::class, ["id"], ["audioContentId"]),
        ForeignKey(ScanRun::class, ["id"], ["lastSeenScanId"])
    ],
    indices = [
        Index("path", unique = true),
        Index("audioContentId"),
        Index("lastSeenScanId"),
        Index("present"),
        Index("dev", "inode")
    ]
)
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val size: Long?,
    val mtime: Double?,
    val dev: Long?,
    val inode: Long?,
    val nlink: Int?,
    val audioContentId: Long?,
    val bitrate: Int?,
    val tagTitle: String?,
    val tagArtist: String?,
    val tagAlbum: String?,
    val tagGenre: String?,
    val tagCompleteness: Int,
    val lastSeenScanId: Long?,
    val present: Boolean = true,
    val absentReason: AbsentReason?,
    val scannedAt: Long?
)

@Entity
data class IngestError(
    @PrimaryKey val path: String,
    val dev: Long?,
    val inode: Long?,
    val size: Long?,
    val mtime: Double?,
    val stage: String,
    val message: String?,
    val attempts: Int = 1,
    val lastAttemptAt: Long?,
    val resolvedAt: Long?
)
