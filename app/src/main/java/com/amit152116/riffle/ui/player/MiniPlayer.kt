package com.amit152116.riffle.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.basicMarquee
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.amit152116.riffle.ui.components.AsyncArtwork
import com.amit152116.riffle.data.color.ArtworkColorExtractor
import com.amit152116.riffle.ui.theme.favoriteRed

@Composable
fun MiniPlayer(viewModel: PlayerViewModel, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val currentColors by viewModel.currentColors.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val swipeOffset = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    var titleWidthPx by remember { mutableIntStateOf(0) }
    val maxSwipeDistance = if (titleWidthPx > 0) titleWidthPx.toFloat() else 220f
    val swipeTriggerDistance = maxSwipeDistance * 0.40f

    if (currentTrack == null) return

    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
    val playerBackgroundStart = Color(
        ArtworkColorExtractor.darkMiniPlayerBackground(currentColors.playerAccentColor)
    )
    val playerBackgroundEnd = Color(
        ArtworkColorExtractor.darkPlayerBackgroundEnd(currentColors.playerAccentColor)
    )

    Box(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(playerBackgroundStart)
            .border(
                width = 1.dp,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
                modifier = Modifier
                    .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncArtwork(
                uri = currentTrack?.artworkUri,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .onSizeChanged { titleWidthPx = it.width }
                    .clip(RoundedCornerShape(1.dp))
                    .graphicsLayer {
                        alpha = (1f - (abs(swipeOffset.value) / maxSwipeDistance) * 0.65f)
                            .coerceIn(0.35f, 1f)
                    }
                    .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
                    .pointerInput(maxSwipeDistance) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                swipeScope.launch {
                                    swipeOffset.snapTo(
                                        (swipeOffset.value + dragAmount)
                                            .coerceIn(-maxSwipeDistance, maxSwipeDistance)
                                    )
                                }
                            },
                            onDragEnd = {
                                val finalOffset = swipeOffset.value
                                if (abs(finalOffset) >= swipeTriggerDistance) {
                                    if (finalOffset < 0f) viewModel.skipNext()
                                    else viewModel.skipPrevious()
                                }
                                swipeScope.launch {
                                    swipeOffset.animateTo(0f, spring())
                                }
                            },
                            onDragCancel = {
                                swipeScope.launch {
                                    swipeOffset.animateTo(0f, spring())
                                }
                            }
                        )
                    }
            ) {
                Text(
                    text = currentTrack?.title ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 900,
                        repeatDelayMillis = 700
                    )
                )
                Text(
                    text = currentTrack?.artist ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 1200,
                        repeatDelayMillis = 700
                    )
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(-4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.toggleFavorite() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) favoriteRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
        
        // Non-interactive progress indicator; tapping the mini player still opens PlayerScreen.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(Color.White)
            )
        }
    }
}
