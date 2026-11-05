package com.amit152116.riffle.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit152116.riffle.data.db.BrowseEntityType
import com.amit152116.riffle.data.model.TrackUiModel
import com.amit152116.riffle.ui.components.AsyncArtwork
import com.amit152116.riffle.ui.components.RiffleTopBar
import com.amit152116.riffle.ui.library.LibraryViewModel

object HomeSectionType {
    const val TOP_ARTISTS = "top-artists"
    const val TOP_ALBUMS = "top-albums"
    const val RECENT_ARTISTS = "recent-artists"
    const val RECENT_ALBUMS = "recent-albums"
}

@Composable
fun HomeSectionDetailScreen(
    type: String,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    val topArtistTracks by libraryViewModel.topArtistTracks.collectAsState()
    val topAlbumTracks by libraryViewModel.topAlbumTracks.collectAsState()
    val recentTracks by libraryViewModel.recentTracks.collectAsState()

    LaunchedEffect(type) {
        libraryViewModel.logBrowseEvent(BrowseEntityType.HOME_SECTION, type)
    }

    val isArtistSection = type == HomeSectionType.TOP_ARTISTS ||
        type == HomeSectionType.RECENT_ARTISTS
    val title = when (type) {
        HomeSectionType.TOP_ARTISTS -> "Top artists"
        HomeSectionType.TOP_ALBUMS -> "Top albums"
        HomeSectionType.RECENT_ARTISTS -> "Recent artists"
        else -> "Recent albums"
    }

    val items = when (type) {
        HomeSectionType.TOP_ARTISTS -> topArtistTracks
        HomeSectionType.TOP_ALBUMS -> topAlbumTracks
        HomeSectionType.RECENT_ARTISTS -> recentTracks.groupBy { it.artist }.map { it.value.first() }
        else -> recentTracks.groupBy { it.album }.map { it.value.first() }
    }.take(50)

    val gridState = rememberLazyGridState()

    Scaffold(
        topBar = {
            RiffleTopBar(
                title = title,
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No music found")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(
                    items = items,
                    key = { if (isArtistSection) "artist-${it.artist}" else "album-${it.album}" }
                ) { item ->
                    if (isArtistSection) {
                        ArtistDetailCard(
                            track = item,
                            onClick = { onNavigateToArtist(item.artist) }
                        )
                    } else {
                        AlbumDetailCard(
                            track = item,
                            onClick = { onNavigateToAlbum(item.album) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistDetailCard(track: TrackUiModel, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncArtwork(
            uri = track.artworkUri,
            modifier = Modifier
                .size(148.dp)
                .clip(CircleShape),
            shape = CircleShape
        )
        Text(
            text = track.artist,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AlbumDetailCard(track: TrackUiModel, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        AsyncArtwork(
            uri = track.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
        )
        Text(
            text = track.album,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
