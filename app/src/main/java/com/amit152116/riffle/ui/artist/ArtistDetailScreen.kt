package com.amit152116.riffle.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.amit152116.riffle.ui.components.RiffleTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit152116.riffle.ui.library.LibraryViewModel
import com.amit152116.riffle.ui.components.AsyncArtwork
import com.amit152116.riffle.ui.components.TrackRow
import com.amit152116.riffle.ui.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {}
) {
    val songs by libraryViewModel.songs.collectAsState()
    val artistTracks = songs.filter { it.artist.equals(artistName, ignoreCase = true) }
    
    val albumGroups = artistTracks.groupBy { it.album }
    val heroArtworkUri = artistTracks.firstOrNull { it.artworkUri != null }?.artworkUri
    var albumSort by remember { mutableStateOf("A-Z") }
    var songSort by remember { mutableStateOf("A-Z") }
    var albumSortExpanded by remember { mutableStateOf(false) }
    var songSortExpanded by remember { mutableStateOf(false) }

    val sortedAlbums = when (albumSort) {
        "Z-A" -> albumGroups.entries.sortedByDescending { it.key.lowercase() }
        else -> albumGroups.entries.sortedBy { it.key.lowercase() }
    }
    val sortedArtistTracks = when (songSort) {
        "Z-A" -> artistTracks.sortedByDescending { it.title.lowercase() }
        "Album" -> artistTracks.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))
        "Duration" -> artistTracks.sortedBy { durationInSeconds(it.trailingText) }
        else -> artistTracks.sortedBy { it.title.lowercase() }
    }
    val detailListState = rememberLazyListState()
    val albumsListState = rememberLazyListState()

    Scaffold(
        topBar = {
            RiffleTopBar(
                onBack = onBack,
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            state = detailListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Hero Image & Artist Title Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    AsyncArtwork(
                        uri = heroArtworkUri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(24.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = artistName,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${albumGroups.size} albums • ${artistTracks.size} tracks",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons (Play / Shuffle)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (artistTracks.isNotEmpty()) {
                                    playerViewModel.playTrack(artistTracks.first(), artistTracks)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play", style = MaterialTheme.typography.titleMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                if (artistTracks.isNotEmpty()) {
                                    val shuffled = artistTracks.shuffled()
                                    playerViewModel.playTrack(shuffled.first(), shuffled)
                                    playerViewModel.toggleShuffle()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Shuffle", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Albums Section
            if (albumGroups.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Albums",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            Box {
                                IconButton(onClick = { albumSortExpanded = true }) {
                                    Icon(Icons.Filled.Sort, contentDescription = "Sort albums")
                                }
                                DropdownMenu(
                                    expanded = albumSortExpanded,
                                    onDismissRequest = { albumSortExpanded = false }
                                ) {
                                    listOf("A-Z", "Z-A").forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                albumSort = option
                                                albumSortExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        LazyRow(
                            state = albumsListState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(sortedAlbums, key = { it.key }) { albumEntry ->
                                val albumName = albumEntry.key
                                val albumTracks = albumGroups[albumName] ?: emptyList()
                                val albumArt = albumTracks.firstOrNull { it.artworkUri != null }?.artworkUri

                                Column(
                                    modifier = Modifier
                                        .width(156.dp)
                                        .clickable { onNavigateToAlbum(albumName) }
                                ) {
                                    AsyncArtwork(
                                        uri = albumArt,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    Text(
                                        text = albumName,
                                        modifier = Modifier.padding(top = 8.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${albumTracks.size} tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }

            // Popular Tracks Section
            if (artistTracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Songs",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Box {
                            IconButton(onClick = { songSortExpanded = true }) {
                                Icon(Icons.Filled.Sort, contentDescription = "Sort songs")
                            }
                            DropdownMenu(
                                expanded = songSortExpanded,
                                onDismissRequest = { songSortExpanded = false }
                            ) {
                                listOf("A-Z", "Z-A", "Album", "Duration").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            songSort = option
                                            songSortExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                items(sortedArtistTracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { playerViewModel.playTrack(track, artistTracks) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                        onPlayNext = { playerViewModel.playNext(track) },
                        onAddToQueue = { playerViewModel.addToQueue(track) },
                        onGoToAlbum = {
                            onNavigateToAlbum(track.album)
                        },
                        onGoToArtist = { }
                    )
                }
            }
        }
    }
}

private fun durationInSeconds(value: String): Int {
    val parts = value.split(":")
    return if (parts.size == 2) {
        (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
    } else {
        Int.MAX_VALUE
    }
}
