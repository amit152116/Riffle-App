package com.amit152116.riffle.ui.player

import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit152116.riffle.data.db.PlaybackSource
import com.amit152116.riffle.data.model.TrackUiModel
import com.amit152116.riffle.ui.components.AsyncArtwork
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale

/** One queue row. [uid] is stable per row so the same track queued twice doesn't collide as a lazy key. */
private class QueueEntry(val uid: Long, val track: TrackUiModel)

/** Sums "m:ss" / "h:mm:ss" trailing texts; the UI model carries no numeric duration. */
private fun totalSeconds(tracks: List<TrackUiModel>): Long = tracks.sumOf { t ->
    t.trailingText.split(":").mapNotNull { it.toLongOrNull() }.fold(0L) { acc, part -> acc * 60 + part }
}

private fun formatTotal(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) String.format(Locale.US, "%d hr %02d min", h, m) else "$m min"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheetContent(viewModel: PlayerViewModel) {
    val playlist by viewModel.currentPlaylist.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffle by viewModel.isShuffleEnabled.collectAsState()

    val currentIndex = playlist.indexOfFirst { it.id == currentTrack?.id }
    val upcoming = if (currentIndex >= 0 && currentIndex + 1 < playlist.size) {
        playlist.subList(currentIndex + 1, playlist.size)
    } else {
        emptyList()
    }

    // Local copy so drag reorders are instant; resynced only when the playlist changes from elsewhere.
    val rows = remember { mutableStateListOf<QueueEntry>() }
    val uidCounter = remember { longArrayOf(0L) }
    LaunchedEffect(upcoming) {
        if (rows.map { it.track.id } != upcoming.map { it.id }) {
            rows.clear()
            rows.addAll(upcoming.map { QueueEntry(uidCounter[0]++, it) })
        }
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        rows.add(to.index, rows.removeAt(from.index))
        viewModel.moveQueueItem(currentIndex + 1 + from.index, currentIndex + 1 + to.index)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "Queue",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (upcoming.isEmpty()) {
                "Nothing up next"
            } else {
                "Up next · ${upcoming.size} tracks · ${formatTotal(totalSeconds(upcoming))}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        currentTrack?.let { track ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncArtwork(
                    uri = track.artworkUri,
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
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
                IconButton(
                    onClick = viewModel::togglePlayPause,
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        if (rows.isNotEmpty()) {
            Text(
                text = "Next in queue",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(rows, key = { it.uid }) { entry ->
                ReorderableItem(reorderState, key = entry.uid) { isDragging ->
                    QueueRow(
                        track = entry.track,
                        isDragging = isDragging,
                        onClick = {
                            viewModel.playTrack(entry.track, viewModel.currentPlaylist.value, PlaybackSource.QUEUE)
                        },
                        onPlayNext = { viewModel.playNext(entry.track) },
                        onRemove = {
                            val index = rows.indexOf(entry)
                            if (index >= 0) {
                                rows.removeAt(index)
                                viewModel.removeFromQueue(currentIndex + 1 + index)
                            }
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QueuePill(
                label = "Shuffle",
                icon = Icons.Filled.Shuffle,
                active = isShuffle,
                onClick = viewModel::toggleShuffle,
                modifier = Modifier.weight(1f)
            )
            QueuePill(
                label = "Clear",
                icon = Icons.Filled.ClearAll,
                enabled = rows.isNotEmpty(),
                onClick = {
                    rows.clear()
                    viewModel.clearUpcoming()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QueuePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true
) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (active) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified
        )
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReorderableCollectionItemScope.QueueRow(
    track: TrackUiModel,
    isDragging: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val onDragStarted: (androidx.compose.ui.geometry.Offset) -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val onDragStopped: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
    }
    val lift by animateFloatAsState(if (isDragging) 1f else 0f, label = "queueRowLift")
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        modifier = Modifier
            .graphicsLayer {
                scaleX = 1f + 0.03f * lift
                scaleY = 1f + 0.03f * lift
            }
            .shadow(8.dp * lift, RoundedCornerShape(8.dp)),
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove from queue",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .longPressDraggableHandle(onDragStarted = onDragStarted, onDragStopped = onDragStopped)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "Reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .draggableHandle(onDragStarted = onDragStarted, onDragStopped = onDragStopped)
                    .padding(horizontal = 4.dp, vertical = 12.dp)
            )
            Spacer(Modifier.width(8.dp))
            AsyncArtwork(
                uri = track.artworkUri,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
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
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        onClick = { onPlayNext(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from queue") },
                        onClick = { onRemove(); menuExpanded = false }
                    )
                }
            }
        }
    }
}
