package com.amit152116.riffle.ui.player

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.data.color.ArtworkColorExtractor
import com.amit152116.riffle.data.model.TrackUiModel
import com.amit152116.riffle.ui.components.AddToPlaylistSheet
import com.amit152116.riffle.ui.components.AsyncArtwork
import com.amit152116.riffle.ui.components.RiffleSlider
import com.amit152116.riffle.ui.components.RiffleTopBar
import com.amit152116.riffle.ui.components.SongDetailSheet
import com.amit152116.riffle.ui.playlist.PlaylistViewModel
import com.amit152116.riffle.ui.theme.favoriteRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

enum class PlayerViewMode { ARTWORK, LYRICS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onClose: () -> Unit = {}
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val currentColors by viewModel.currentColors.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsState()
    val sleepTimerEndOfTrack by viewModel.sleepTimerEndOfTrack.collectAsState()

    val isFavorite by viewModel.isFavorite.collectAsState()
    var viewMode by remember { mutableStateOf(PlayerViewMode.ARTWORK) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    var menuExpanded by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val playlistViewModel: PlaylistViewModel = viewModel(
        factory = AppContainer.getInstance(context).playlistViewModelFactory
    )

    // From fully expanded, a swipe down settles at the partial size first; a second swipe closes.
    // Programmatic hides (back press) set allowHide to bypass this.
    val queueSheetScope = rememberCoroutineScope()
    val allowSheetHide = remember { booleanArrayOf(false) }
    val sheetStateHolder = remember { arrayOfNulls<SheetState>(1) }
    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = { target ->
            target != SheetValue.Hidden || allowSheetHide[0] || sheetStateHolder[0]?.currentValue != SheetValue.Expanded
        })
    sheetStateHolder[0] = sheetState

    // Animations
    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.88f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow
        ), label = "artworkScale"
    )

    val favScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "favScale"
    )

    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

    // Formatting function
    val formatTime = { timeMs: Long ->
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    if (currentTrack == null) return

    val playerBackgroundStart = Color(
        ArtworkColorExtractor.darkPlayerBackground(currentColors.playerAccentColor)
    )
    val playerBackgroundEnd = Color(
        ArtworkColorExtractor.darkPlayerBackgroundEnd(currentColors.playerAccentColor)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to playerBackgroundStart,
                        0.60f to lerp(playerBackgroundStart, playerBackgroundEnd, 0.4f),
                        1.0f to lerp(playerBackgroundStart, playerBackgroundEnd, 0.8f)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .statusBarsPadding()
                .height(1.dp)
        )
        Scaffold(
            topBar = {
                RiffleTopBar(onBack = onClose, navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close")
                    }
                }, containerColor = Color.Transparent, actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Add to playlist") }, onClick = {
                                showAddToPlaylistSheet = true
                                menuExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Go to album") }, onClick = {
                                onNavigateToAlbum(currentTrack!!.album)
                                menuExpanded = false
                                onClose()
                            })
                            DropdownMenuItem(text = { Text("Go to artist") }, onClick = {
                                onNavigateToArtist(currentTrack!!.artist)
                                menuExpanded = false
                                onClose()
                            })
                            DropdownMenuItem(text = { Text("Share") }, onClick = {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "audio/*"
                                            putExtra(
                                                Intent.EXTRA_STREAM, currentTrack!!.mediaUri
                                            )
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "${currentTrack!!.title} • ${currentTrack!!.artist}"
                                            )
                                        }, "Share song"
                                    )
                                )
                                menuExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Tag editor") }, onClick = {
                                Toast.makeText(
                                    context, "Tag editor support coming soon", Toast.LENGTH_SHORT
                                ).show()
                                menuExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Details") }, onClick = {
                                showDetailSheet = true
                                menuExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Set as ringtone") }, onClick = {
                                Toast.makeText(
                                    context, "Ringtone support coming soon", Toast.LENGTH_SHORT
                                ).show()
                                menuExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Add to blacklist") }, onClick = {
                                viewModel.blacklistTrack(currentTrack!!)
                                menuExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Delete from device") }, onClick = {
                                Toast.makeText(
                                    context, "Delete support coming soon", Toast.LENGTH_SHORT
                                ).show()
                                menuExpanded = false
                            })
                        }
                    }
                })
            }, containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    // Content Area (Artwork / Synced Lyrics / Up Next Queue)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        when (viewMode) {
                            PlayerViewMode.ARTWORK -> {
                                var seekAccumulator by remember { mutableStateOf(0) }
                                var isRightSide by remember { mutableStateOf(true) }
                                var lastSeekTapTime by remember { mutableStateOf(0L) }

                                val currentSeekAccumulator by rememberUpdatedState(seekAccumulator)
                                val currentIsRightSide by rememberUpdatedState(isRightSide)
                                val currentLastSeekTapTime by rememberUpdatedState(lastSeekTapTime)

                                LaunchedEffect(seekAccumulator, lastSeekTapTime) {
                                    if (seekAccumulator != 0) {
                                        delay(1000)
                                        seekAccumulator = 0
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scale(artworkScale)
                                        .pointerInput(Unit) {
                                            detectTapGestures(onTap = { offset ->
                                                val now = System.currentTimeMillis()
                                                val isRight = offset.x > size.width / 2
                                                if (now - currentLastSeekTapTime < 500 && currentIsRightSide == isRight && currentSeekAccumulator != 0) {
                                                    val delta = if (isRight) 5 else -5
                                                    seekAccumulator = currentSeekAccumulator + delta
                                                    viewModel.seekBy(delta * 1000L)
                                                    lastSeekTapTime = now
                                                }
                                            }, onDoubleTap = { offset ->
                                                val now = System.currentTimeMillis()
                                                val isRight = offset.x > size.width / 2
                                                val delta = if (isRight) 5 else -5

                                                if (currentIsRightSide == isRight && currentSeekAccumulator != 0 && now - currentLastSeekTapTime < 500) {
                                                    seekAccumulator = currentSeekAccumulator + delta
                                                } else {
                                                    isRightSide = isRight
                                                    seekAccumulator = delta
                                                }
                                                viewModel.seekBy(delta * 1000L)
                                                lastSeekTapTime = now
                                            })
                                        }, contentAlignment = Alignment.Center
                                ) {
                                    AsyncArtwork(
                                        uri = currentTrack?.artworkUri,
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(24.dp)
                                    )

                                    SeekOverlay(
                                        seekAccumulator = seekAccumulator, isRightSide = isRightSide
                                    )
                                }
                            }

                            PlayerViewMode.LYRICS -> {
                                SyncedLyricsView(
                                    track = currentTrack, currentPositionMs = currentPosition
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Title and Metadata
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = currentTrack?.title ?: "Unknown",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentTrack?.artist ?: "Unknown",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) favoriteRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.scale(favScale)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp) // Visual padding for slider thumb
                    ) {
                        RiffleSlider(
                            value = progress,
                            onValueChange = { viewModel.seekTo(it) },
                            valueRange = 0f..1f,
                            startLabel = formatTime(currentPosition),
                            endLabel = formatTime(duration),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primaryContainer,
                                activeTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            activeTrackColorOverride = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        )

                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Playback Controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp), // This provides the outer padding for Shuffle and Timer
                    contentAlignment = Alignment.Center
                ) {
                    // Shuffle Button with state container animation
                    val shuffleIconColor by animateColorAsState(
                        targetValue = if (isShuffleEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "shuffleIconColor"
                    )

                    IconButton(
                        onClick = { viewModel.toggleShuffle() },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(52.dp)
                    ) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = shuffleIconColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .align(Alignment.Center),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Button
                        IconButton(
                            onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Filled.SkipPrevious,
                                contentDescription = "Previous",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(45.dp)
                            )
                        }
                        // Animated Play / Pause Circle Button
                        val playButtonScale by animateFloatAsState(
                            targetValue = if (isPlaying) 1.05f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "playButtonScale"
                        )

                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .scale(playButtonScale)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, Color.White, CircleShape)
                                .clickable { viewModel.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = isPlaying, transitionSpec = {
                                    (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                                }, label = "playPauseTransition"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(45.dp)
                                )
                            }
                        }

                        // Next Button
                        IconButton(
                            onClick = { viewModel.skipNext() }, modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = "Next",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(45.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { showSleepTimerDialog = true },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(52.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Timer,
                            contentDescription = "Sleep timer",
                            tint = if (sleepTimerRemainingMs != null || sleepTimerEndOfTrack) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Intelligent Actions / Mode Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IntelligentActionItem(
                        label = if (viewMode == PlayerViewMode.LYRICS) "Artwork" else "Lyrics",
                        icon = if (viewMode == PlayerViewMode.LYRICS) Icons.Filled.Person else Icons.Filled.Lyrics,
                        onClick = {
                            viewMode =
                                if (viewMode == PlayerViewMode.LYRICS) PlayerViewMode.ARTWORK else PlayerViewMode.LYRICS
                        })
                    IntelligentActionItem(
                        label = "Queue", icon = Icons.Filled.QueueMusic, onClick = {
                            showQueueSheet = true
                        })
                    IntelligentActionItem("Similar", Icons.Outlined.GraphicEq)
                }
            }

            // Spotify-style Bottom Sheet Queue
            if (showQueueSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showQueueSheet = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    BackHandler {
                        allowSheetHide[0] = true
                        queueSheetScope.launch { sheetState.hide() }.invokeOnCompletion {
                            allowSheetHide[0] = false
                            showQueueSheet = false
                        }
                    }
                    QueueSheetContent(viewModel = viewModel)
                }
            }
        }
    }

    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Sleep timer") },
            text = {
                Column {
                    SleepTimerOption("15 minutes") {
                        viewModel.setSleepTimer(15)
                        showSleepTimerDialog = false
                    }
                    SleepTimerOption("30 minutes") {
                        viewModel.setSleepTimer(30)
                        showSleepTimerDialog = false
                    }
                    SleepTimerOption("45 minutes") {
                        viewModel.setSleepTimer(45)
                        showSleepTimerDialog = false
                    }
                    SleepTimerOption("60 minutes") {
                        viewModel.setSleepTimer(60)
                        showSleepTimerDialog = false
                    }
                    SleepTimerOption("End of current song") {
                        viewModel.setSleepTimerEndOfTrack()
                        showSleepTimerDialog = false
                    }
                    if (sleepTimerRemainingMs != null || sleepTimerEndOfTrack) {
                        SleepTimerOption("Turn off timer") {
                            viewModel.cancelSleepTimer()
                            showSleepTimerDialog = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Cancel")
                }
            })
    }

    if (showAddToPlaylistSheet && currentTrack != null) {
        AddToPlaylistSheet(
            tracks = listOf(currentTrack!!),
            viewModel = playlistViewModel,
            onDismiss = { showAddToPlaylistSheet = false })
    }

    if (showDetailSheet && currentTrack != null) {
        SongDetailSheet(
            track = currentTrack!!, onDismiss = { showDetailSheet = false })
    }
}

@Composable
private fun SleepTimerOption(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick, modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun IntelligentActionItem(
    label: String, icon: ImageVector, onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

data class LyricLine(val timestampMs: Long, val text: String)

fun parseLrc(lrcText: String): List<LyricLine> {
    val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
    val lines = mutableListOf<LyricLine>()
    lrcText.lines().forEach { line ->
        val match = regex.find(line)
        if (match != null) {
            val min = match.groupValues[1].toLongOrNull() ?: 0L
            val sec = match.groupValues[2].toLongOrNull() ?: 0L
            val msStr = match.groupValues[3]
            val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
            val totalMs = min * 60000 + sec * 1000 + ms
            val text = match.groupValues[4].trim()
            if (text.isNotEmpty()) {
                lines.add(LyricLine(totalMs, text))
            }
        }
    }
    return lines.sortedBy { it.timestampMs }
}

@Composable
fun SyncedLyricsView(track: TrackUiModel?, currentPositionMs: Long) {
    val sampleLrc = """
        [00:00.00] (Instrumental Intro)
        [00:05.00] Drift along the ambient wave
        [00:15.00] Feel the rhythm in the dark
        [00:25.00] Echoes of a distant melody
        [00:35.00] Riffle brings the sound to life
        [00:50.00] Seamless transitions in key
        [01:10.00] Deep audio intelligence
    """.trimIndent()

    val lyrics = remember(track?.id) { parseLrc(sampleLrc) }
    val listState = rememberLazyListState()

    val activeIndex = remember(currentPositionMs, lyrics) {
        val idx = lyrics.indexOfLast { it.timestampMs <= currentPositionMs }
        if (idx < 0) 0 else idx
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex in lyrics.indices) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(lyrics) { index, line ->
            val isActive = index == activeIndex
            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.15f else 0.95f, label = "lyricScale"
            )
            val alpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.4f, label = "lyricAlpha"
            )

            Text(
                text = line.text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun BoxScope.SeekOverlay(seekAccumulator: Int, isRightSide: Boolean) {
    AnimatedVisibility(
        visible = seekAccumulator != 0,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(if (isRightSide) Alignment.CenterEnd else Alignment.CenterStart)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .background(
                    Brush.horizontalGradient(
                        colors = if (isRightSide) {
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                        } else {
                            listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent)
                        }
                    )
                ), contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isRightSide) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "${abs(seekAccumulator)}s",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
