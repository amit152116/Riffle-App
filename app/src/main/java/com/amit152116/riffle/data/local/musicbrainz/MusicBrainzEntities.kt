package com.amit152116.riffle.data.local.musicbrainz

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amit152116.riffle.data.local.library.AudioContent

@Entity(
    foreignKeys = [ForeignKey(AudioContent::class, ["id"], ["audioContentId"])],
    indices = [Index("audioContentId", unique = true)]
)
data class MusicBrainzMatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audioContentId: Long,
    val acoustidId: String?,
    val recordingMbid: String?,
    val recordingTitle: String?,
    val releaseTitle: String?,
    val releaseMbid: String?,
    val score: Double?,
    val parsedAt: Long?
)

@Entity
data class MbArtist(@PrimaryKey val mbid: String, val name: String)

@Entity(
    primaryKeys = ["matchId", "position"],
    foreignKeys = [
        ForeignKey(MusicBrainzMatch::class, ["id"], ["matchId"]),
        ForeignKey(MbArtist::class, ["mbid"], ["artistMbid"])
    ],
    indices = [Index("artistMbid")]
)
data class MbRecordingArtist(val matchId: Long, val position: Int, val artistMbid: String)

@Entity
data class AcoustidCache(
    @PrimaryKey val lookupKey: String,
    val responseJson: String?,
    val fetchedAt: Long?
)

@Entity(
    primaryKeys = ["lookupKey", "audioContentId"],
    foreignKeys = [
        ForeignKey(AcoustidCache::class, ["lookupKey"], ["lookupKey"]),
        ForeignKey(AudioContent::class, ["id"], ["audioContentId"])
    ],
    indices = [Index("audioContentId")]
)
data class AcoustidCacheContent(val lookupKey: String, val audioContentId: Long)
