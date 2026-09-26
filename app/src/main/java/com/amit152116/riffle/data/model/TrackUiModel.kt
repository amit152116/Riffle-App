package com.amit152116.riffle.data.model

import android.net.Uri

data class TrackUiModel(
    val id: Long = 0L,
    val title: String,
    val artist: String,
    val album: String = "",
    val trailingText: String,
    val artworkUri: Uri? = null,
    val mediaUri: Uri = Uri.EMPTY
)
