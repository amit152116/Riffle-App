package com.amit152116.riffle.ui.player

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.media3.common.Player
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.amit152116.riffle.ui.components.RiffleTopBar
import com.amit152116.riffle.ui.components.RiffleSlider
import com.amit152116.riffle.ui.theme.favoriteRed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit152116.riffle.data.db.PlaybackSource
import com.amit152116.riffle.data.model.TrackUiModel
import com.amit152116.riffle.data.color.ArtworkColorExtractor
import com.amit152116.riffle.ui.components.AsyncArtwork
import com.amit152116.riffle.ui.components.TrackRow
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

enum class PlayerViewMode { ARTWORK, LYRICS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onClose: () -> Unit = {}) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val currentColors by viewModel.currentColors.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsState()
    val sleepTimerEndOfTrack by viewModel.sleepTimerEndOfTrack.collectAsState()

    val isFavorite by viewModel.isFavorite.collectAsState()
    var viewMode by remember { mutableStateOf(PlayerViewMode.ARTWORK) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    
    val sheetState = rememberModalBottomSheetState()

    // Animations
    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.88f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "artworkScale"
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
            RiffleTopBar(
                onBack = onClose,
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close")
                    }
                },
                containerColor = Color.Transparent,
                actions = {
                    IconButton(onClick = { showSleepTimerDialog = true }) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = "Sleep timer",
                            tint = if (sleepTimerRemainingMs != null || sleepTimerEndOfTrack) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            }
                        )
                    }
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Filled.MoreHoriz, contentDescription = "More")
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
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
                                    detectTapGestures(
                                        onTap = { offset ->
                                            val now = System.currentTimeMillis()
                                            val isRight = offset.x > size.width / 2
                                            if (now - currentLastSeekTapTime < 500 && currentIsRightSide == isRight && currentSeekAccumulator != 0) {
                                                val delta = if (isRight) 5 else -5
                                                seekAccumulator = currentSeekAccumulator + delta
                                                viewModel.seekBy(delta * 1000L)
                                                lastSeekTapTime = now
                                            }
                                        },
                                        onDoubleTap = { offset ->
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
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncArtwork(
                                uri = currentTrack?.artworkUri,
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(24.dp)
                            )

                            SeekOverlay(
                                seekAccumulator = seekAccumulator,
                                isRightSide = isRightSide
                            )
                        }
                    }
                    PlayerViewMode.LYRICS -> {
                        SyncedLyricsView(
                            track = currentTrack,
                            currentPositionMs = currentPosition
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
                Column(modifier = Modifier.weight(1f)) {
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
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Progress Bar
            Column(modifier = Modifier.fillMaxWidth()) {
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
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button with state container animation
                val shuffleIconColor by animateColorAsState(
                    targetValue = if (isShuffleEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "shuffleIconColor"
                )

                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = shuffleIconColor,
                        modifier = Modifier.size(30.dp)
                    )
                }
                
                // Previous Button
                IconButton(
                    onClick = { viewModel.skipPrevious() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(36.dp)
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
                        targetState = isPlaying,
                        transitionSpec = {
                            (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                        },
                        label = "playPauseTransition"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
                
                // Next Button
                IconButton(
                    onClick = { viewModel.skipNext() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                // Repeat Button with state container animation
                val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
                val repeatIconColor by animateColorAsState(
                    targetValue = if (isRepeatActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "repeatIconColor"
                )

                IconButton(
                    onClick = { viewModel.cycleRepeatMode() },
                    modifier = Modifier.size(52.dp)
                ) {
                    val repeatIcon = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                        Player.REPEAT_MODE_ALL -> Icons.Filled.Repeat
                        else -> Icons.Filled.Repeat
                    }
                    Icon(
                        repeatIcon,
                        contentDescription = "Repeat",
                        tint = repeatIconColor,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Intelligent Actions / Mode Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IntelligentActionItem(
                    label = if (viewMode == PlayerViewMode.LYRICS) "Artwork" else "Lyrics",
                    icon = if (viewMode == PlayerViewMode.LYRICS) Icons.Filled.Person else Icons.Filled.Lyrics,
                    onClick = {
                        viewMode = if (viewMode == PlayerViewMode.LYRICS) PlayerViewMode.ARTWORK else PlayerViewMode.LYRICS
                    }
                )
                IntelligentActionItem(
                    label = "Queue",
                    icon = Icons.Filled.QueueMusic,
                    onClick = {
                        showQueueSheet = true
                    }
                )
                IntelligentActionItem("Similar", Icons.Outlined.GraphicEq)
                IntelligentActionItem("More", Icons.Filled.MoreHoriz)
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
            }
        )
    }
}

@Composable
private fun SleepTimerOption(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun IntelligentActionItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
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
            val scale by animateFloatAsState(targetValue = if (isActive) 1.15f else 0.95f, label = "lyricScale")
            val alpha by animateFloatAsState(targetValue = if (isActive) 1f else 0.4f, label = "lyricAlpha")

            Text(
                text = line.text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale),
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
                ),
            contentAlignment = Alignment.Center
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

@Composable
fun QueueSheetContent(viewModel: PlayerViewModel) {
    val playlist by viewModel.currentPlaylist.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()

    val currentIndex = playlist.indexOfFirst { it.id == currentTrack?.id }
    val upcomingTracks = if (currentIndex >= 0 && currentIndex + 1 < playlist.size) {
        playlist.subList(currentIndex + 1, playlist.size)
    } else {
        emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Queue",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val queueListState = rememberLazyListState()
        LazyColumn(
            state = queueListState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Currently Playing section
            if (currentTrack != null) {
                item {
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                item {
                    TrackRow(
                        track = currentTrack!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                            .clickable {
                                viewModel.playTrack(currentTrack!!, viewModel.currentPlaylist.value)
                            }
                            .padding(8.dp),
                        onPlayNext = { viewModel.playNext(currentTrack!!) },
                        onAddToQueue = { viewModel.addToQueue(currentTrack!!) },
                        onAddToBlacklist = { viewModel.blacklistTrack(currentTrack!!) }
                    )
                }
            }

            // Up Next section
            if (upcomingTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Next In Queue",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                itemsIndexed(upcomingTracks) { index, track ->
                    val originalIndex = currentIndex + 1 + index
                    TrackRow(
                        track = track,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.playTrack(track, viewModel.currentPlaylist.value, PlaybackSource.QUEUE)
                            }
                            .padding(8.dp),
                        leadingText = "${index + 1}",
                        trailingAction = { viewModel.removeFromQueue(originalIndex) },
                        trailingActionIcon = Icons.Filled.Close,
                        trailingActionDescription = "Remove from queue",
                        onPlayNext = { viewModel.playNext(track) },
                        onAddToQueue = { viewModel.addToQueue(track) }
                    )
                }
            }
        }
    }
}
