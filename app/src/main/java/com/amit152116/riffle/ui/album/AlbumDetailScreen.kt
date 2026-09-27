package com.amit152116.riffle.ui.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.amit152116.riffle.ui.library.LibraryViewModel
import com.amit152116.riffle.ui.components.AsyncArtwork
import com.amit152116.riffle.ui.components.rememberArtworkColors
import com.amit152116.riffle.ui.components.TrackRow
import com.amit152116.riffle.ui.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumName: String,
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    val songs by libraryViewModel.songs.collectAsState()
    val albumTracks = songs.filter { it.album.equals(albumName, ignoreCase = true) }
    
    val artistName = albumTracks.firstOrNull()?.artist ?: "Unknown Artist"
    val albumArtworkUri = albumTracks.firstOrNull { it.artworkUri != null }?.artworkUri
    val artistArtworkUri = albumTracks.firstOrNull { it.artworkUri != null }?.artworkUri
    val artworkColors = rememberArtworkColors(albumArtworkUri)
    val artworkBgOpaque = artworkColors.backgroundColor or 0xFF000000.toInt()
    val artworkBackgroundIsNearBlack =
        ColorUtils.calculateLuminance(artworkBgOpaque) < 0.08
    val artworkActionColor = if (artworkBackgroundIsNearBlack) {
        Color(artworkColors.primaryTextColor)
    } else {
        Color(artworkColors.backgroundColor)
    }
    val artworkActionColorArgb = artworkActionColor.toArgb() or 0xFF000000.toInt()
    val artworkActionTextColor = if (artworkBackgroundIsNearBlack) {
        val contrast = try {
            ColorUtils.calculateContrast(
                Color.White.toArgb() or 0xFF000000.toInt(),
                artworkActionColorArgb
            )
        } catch (e: IllegalArgumentException) {
            1.0
        }
        if (contrast >= 4.5) Color.White else Color.Black
    } else {
        Color(artworkColors.primaryTextColor)
    }
    val artworkOutlineColor = artworkActionColor
    val detailListState = rememberLazyListState()

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
            // Hero Album Artwork & Title Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncArtwork(
                        uri = albumArtworkUri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(24.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncArtwork(
                            uri = artistArtworkUri,
                            modifier = Modifier.size(52.dp),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = albumName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$artistName • ${albumTracks.size} tracks",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons (Play / Shuffle)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (albumTracks.isNotEmpty()) {
                                    playerViewModel.playTrack(albumTracks.first(), albumTracks)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = artworkOutlineColor
                            ),
                            border = BorderStroke(1.dp, artworkOutlineColor)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play", style = MaterialTheme.typography.titleMedium)
                        }

                        Button(
                            onClick = {
                                if (albumTracks.isNotEmpty()) {
                                    val shuffled = albumTracks.shuffled()
                                    playerViewModel.playTrack(shuffled.first(), shuffled)
                                    playerViewModel.toggleShuffle()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = artworkActionColor,
                                contentColor = artworkActionTextColor
                            )
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Shuffle", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            item {
                Text(
                    text = "Songs",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Numbered Track List
            itemsIndexed(albumTracks) { index, track ->
                TrackRow(
                    track = track,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { playerViewModel.playTrack(track, albumTracks) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    leadingText = "${index + 1}",
                    onPlayNext = { playerViewModel.playNext(track) },
                    onAddToQueue = { playerViewModel.addToQueue(track) },
                    onGoToArtist = { onNavigateToArtist(track.artist) }
                )
            }
        }
    }
}
