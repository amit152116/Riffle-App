package com.amit152116.riffle.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.data.model.TrackUiModel
import com.amit152116.riffle.ui.playlist.PlaylistViewModel

@Composable
fun TrackRow(
    track: TrackUiModel,
    modifier: Modifier = Modifier,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onGoToAlbum: () -> Unit = {},
    onGoToArtist: () -> Unit = {},
    onAddToBlacklist: () -> Unit = {},
    leadingText: String? = null,
    trailingAction: (() -> Unit)? = null,
    trailingActionIcon: ImageVector = Icons.Filled.MoreVert,
    trailingActionDescription: String = "More options",
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectionToggle: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val playlistViewModel: PlaylistViewModel = viewModel(
        factory = AppContainer.getInstance(context).playlistViewModelFactory
    )

    fun showUnavailable(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        menuExpanded = false
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingText != null) {
            Text(
                text = leadingText,
                modifier = Modifier.width(32.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            AsyncArtwork(
                uri = track.artworkUri,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
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

        if (selectionMode) {
            IconButton(
                onClick = onSelectionToggle
            ) {
                Icon(
                    if (selected) Icons.Filled.Check else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Box {
                IconButton(
                    modifier = Modifier.offset(x = 18.dp),
                    onClick = {
                        if (trailingAction != null) trailingAction() else menuExpanded = true
                    }
                ) {
                    Icon(
                        trailingActionIcon,
                        contentDescription = trailingActionDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    SongMenuItem("Play next") {
                        onPlayNext()
                        menuExpanded = false
                    }
                    SongMenuItem("Add to playing queue") {
                        onAddToQueue()
                        menuExpanded = false
                    }
                    SongMenuItem("Add to playlist") {
                        showAddToPlaylistSheet = true
                        menuExpanded = false
                    }
                    SongMenuItem("Go to album") {
                        onGoToAlbum()
                        menuExpanded = false
                    }
                    SongMenuItem("Go to artist") {
                        onGoToArtist()
                        menuExpanded = false
                    }
                    SongMenuItem("Tag Editor") { showUnavailable("Tag Editor support coming soon") }
                    SongMenuItem("Share") {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "audio/*"
                                    putExtra(Intent.EXTRA_STREAM, track.mediaUri)
                                    putExtra(Intent.EXTRA_TEXT, "${track.title} • ${track.artist}")
                                },
                                "Share song"
                            )
                        )
                        menuExpanded = false
                    }
                    SongMenuItem("Tag editor") { showUnavailable("Tag editor support coming soon") }
                    SongMenuItem("Details") {
                        showDetailSheet = true
                        menuExpanded = false
                    }
                    SongMenuItem("Set as ringtone") { showUnavailable("Ringtone support coming soon") }
                    SongMenuItem("Add to blacklist") {
                        onAddToBlacklist()
                        menuExpanded = false
                    }
                    SongMenuItem("Delete from device") { showUnavailable("Delete support coming soon") }
                }
            }
        }
    }

    if (showAddToPlaylistSheet) {
        AddToPlaylistSheet(
            tracks = listOf(track),
            viewModel = playlistViewModel,
            onDismiss = { showAddToPlaylistSheet = false }
        )
    }

    if (showDetailSheet) {
        SongDetailSheet(
            track = track,
            onDismiss = { showDetailSheet = false }
        )
    }
}

@Composable
private fun SongMenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick
    )
}
