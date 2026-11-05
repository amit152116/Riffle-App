package com.amit152116.riffle.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.data.db.PlaybackSource
import com.amit152116.riffle.ui.components.RiffleTopBar
import com.amit152116.riffle.ui.components.TrackRow
import com.amit152116.riffle.ui.player.PlayerViewModel

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
) {

    val viewModel: PlaylistViewModel =
        viewModel(factory = AppContainer.getInstance(LocalContext.current).playlistViewModelFactory)
    val playlists by viewModel.playlists.collectAsState()
    val playlistName = playlists.find { it.id == playlistId }?.name ?: "Playlist"
    val tracks by remember(playlistId) { viewModel.tracksFor(playlistId) }.collectAsState(initial = emptyList())
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            RiffleTopBar(
                title = playlistName,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete playlist")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No tracks in this playlist",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(tracks, key = { it.playlistTrackId }) { item ->
                    TrackRow(
                        track = item.track,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playerViewModel.playTrack(
                                    item.track,
                                    tracks.map { it.track },
                                    PlaybackSource.PLAYLIST
                                )
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        onPlayNext = { playerViewModel.playNext(item.track) },
                        onAddToQueue = { playerViewModel.addToQueue(item.track) },
                        onAddToBlacklist = { playerViewModel.blacklistTrack(item.track) },
                        trailingAction = { viewModel.removeFromPlaylist(item.playlistTrackId) },
                        trailingActionIcon = Icons.Filled.Close,
                        trailingActionDescription = "Remove from playlist"
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"$playlistName\"?") },
            text = { Text("This removes the playlist. The tracks themselves stay in your library.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(playlistId)
                    showDeleteConfirm = false
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
