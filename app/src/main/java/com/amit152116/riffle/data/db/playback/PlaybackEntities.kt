package com.amit152116.riffle.data.db.playback

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.amit152116.riffle.data.db.library.Track

@Entity(foreignKeys = [ForeignKey(Track::class, ["id"], ["trackId"])], indices = [Index("trackId"), Index("playedAt")])
data class PlayHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val playedAt: Long,
    val durationPlayedMs: Long?
)

@Entity(foreignKeys = [ForeignKey(Track::class, ["id"], ["trackId"])])
data class FavoriteTrack(@PrimaryKey val trackId: Long, val addedAt: Long)

@Entity(foreignKeys = [ForeignKey(Track::class, ["id"], ["trackId"])])
data class BlacklistedTrack(@PrimaryKey val trackId: Long, val addedAt: Long)

@Entity
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(
    foreignKeys = [
        ForeignKey(Playlist::class, ["id"], ["playlistId"]),
        ForeignKey(Track::class, ["id"], ["trackId"])
    ],
    indices = [Index("playlistId"), Index("trackId")]
)
data class PlaylistTrack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: Long,
    val position: Int
)
