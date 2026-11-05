package com.amit152116.riffle.data.model

import android.content.ContentUris
import android.net.Uri
import com.amit152116.riffle.data.db.library.Track
import java.util.Locale

data class TrackUiModel(
    // MediaStore.Audio.Media._ID - see Track.mediaStoreId kdoc. Never 0 for a
    // real track; toTrackUiModel() skips rows where this can't be resolved
    // rather than defaulting to 0, since 0 would collide across tracks in
    // selection/groupBy/queue lookups.
    val id: Long = 0L,
    val title: String,
    val artist: String,
    val album: String = "",
    val genre: String = "",
    val folderPath: String = "",
    val trailingText: String,
    val artworkUri: Uri? = null,
    val mediaUri: Uri = Uri.EMPTY,
    val durationMs: Long = 0L,
    val dateAdded: Long = 0L
)

private val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")

/** Null when the row has no mediaStoreId - never map that to id=0, see kdoc above. */
fun Track.toTrackUiModel(): TrackUiModel? {
    val mediaStoreId = mediaStoreId ?: return null
    val durationMs = durationMs ?: 0L
    val minutes = (durationMs / 1000) / 60
    val seconds = (durationMs / 1000) % 60
    return TrackUiModel(
        id = mediaStoreId,
        title = tagTitle ?: "Unknown Title",
        artist = tagArtist ?: "Unknown Artist",
        album = tagAlbum ?: "Unknown Album",
        genre = tagGenre ?: "",
        folderPath = relativePath ?: "Unknown Folder",
        trailingText = String.format(Locale.US, "%d:%02d", minutes, seconds),
        artworkUri = albumId?.let { ContentUris.withAppendedId(ALBUM_ART_BASE, it) },
        // path is already content://media/external/audio/media/{_ID} (see LibraryScanner).
        mediaUri = Uri.parse(path),
        durationMs = durationMs,
        dateAdded = dateAdded ?: 0L
    )
}
