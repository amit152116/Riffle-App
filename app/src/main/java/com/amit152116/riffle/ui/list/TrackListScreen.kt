package com.amit152116.riffle.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amit152116.riffle.data.model.TrackUiModel
import com.amit152116.riffle.ui.components.RiffleTopBar
import com.amit152116.riffle.ui.components.TrackRow
import com.amit152116.riffle.ui.player.PlayerViewModel

@Composable
fun TrackListScreen(
    title: String,
    viewModel: TrackListViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    // Set for the blacklist listType: the row's trailing action removes the
    // track from the blacklist instead of opening the normal overflow menu -
    // "Add to blacklist" on an already-blacklisted track would just be a
    // confusing re-add.
    onRemoveFromBlacklist: ((TrackUiModel) -> Unit)? = null
) {
    val tracks by viewModel.tracks.collectAsState()

    Scaffold(
        topBar = {
            RiffleTopBar(
                title = title,
                onBack = onBack
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
                Text("No tracks found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { playerViewModel.playTrack(track, tracks) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        onPlayNext = { playerViewModel.playNext(track) },
                        onAddToQueue = { playerViewModel.addToQueue(track) },
                        onGoToArtist = { onNavigateToArtist(track.artist) },
                        onGoToAlbum = { onNavigateToAlbum(track.album) },
                        onAddToBlacklist = { playerViewModel.blacklistTrack(track) },
                        trailingAction = onRemoveFromBlacklist?.let { { it(track) } },
                        trailingActionIcon = Icons.Filled.Close,
                        trailingActionDescription = "Remove from blacklist"
                    )
                }
            }
        }
    }
}
