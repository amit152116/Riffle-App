package com.amit152116.riffle.data.model

import android.net.Uri

data class AlbumUiModel(val name: String, val artist: String, val trackCount: Int, val artworkUri: Uri?)
data class ArtistUiModel(val name: String, val trackCount: Int, val artworkUri: Uri?)
