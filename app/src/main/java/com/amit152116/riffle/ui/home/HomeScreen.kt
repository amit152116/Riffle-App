package com.amit152116.riffle.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.amit152116.riffle.R
import com.amit152116.riffle.data.model.TrackUiModel
import com.amit152116.riffle.ui.components.TrackRow
import com.amit152116.riffle.ui.components.RiffleTopBar
import com.amit152116.riffle.ui.components.AsyncArtwork
import com.amit152116.riffle.ui.player.PlayerViewModel
import com.amit152116.riffle.ui.library.LibraryViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSection: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {}
) {
    val songs by libraryViewModel.songs.collectAsState()
    val homeListState = rememberLazyListState()
    
    Scaffold(
        topBar = {
            RiffleTopBar(
                titleContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_round),
                            contentDescription = null,
                            modifier = Modifier.size(45.dp)
                        )
                        Text(
                            text = "Riffle",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            state = homeListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 80.dp) // padding for bottom nav
        ) {
            item {
                SmartShuffleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            item {
                QuickAccessGrid(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }

            item {
                SuggestionsSection(
                    tracks = songs,
                    playerViewModel = playerViewModel
                )
            }

            item {
                HomeSections(
                    tracks = songs,
                    playerViewModel = playerViewModel,
                    onNavigateToSection = onNavigateToSection,
                    onNavigateToArtist = onNavigateToArtist,
                    onNavigateToAlbum = onNavigateToAlbum
                )
            }
        }
    }
}

@Composable
private fun HomeSections(
    tracks: List<TrackUiModel>,
    playerViewModel: PlayerViewModel,
    onNavigateToSection: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    if (tracks.isEmpty()) return

    val artistTracks = tracks
        .groupBy { it.artist }
        .entries
        .sortedByDescending { it.value.size }
        .map { it.value.first() }
    val albumTracks = tracks
        .groupBy { it.album }
        .entries
        .sortedByDescending { it.value.size }
        .map { it.value.first() }

    Column(modifier = Modifier.fillMaxWidth()) {
        HomeArtistSection(
            title = "Top artists",
            tracks = artistTracks.take(5),
            allTracks = tracks,
            playerViewModel = playerViewModel,
            onSeeAll = { onNavigateToSection(HomeSectionType.TOP_ARTISTS) },
            onNavigateToArtist = onNavigateToArtist
        )
        HomeAlbumSection(
            title = "Top albums",
            tracks = albumTracks.take(5),
            allTracks = tracks,
            playerViewModel = playerViewModel,
            onSeeAll = { onNavigateToSection(HomeSectionType.TOP_ALBUMS) },
            onNavigateToAlbum = onNavigateToAlbum
        )
        HomeArtistSection(
            title = "Recent artists",
            tracks = artistTracks.asReversed().take(5),
            allTracks = tracks,
            playerViewModel = playerViewModel,
            onSeeAll = { onNavigateToSection(HomeSectionType.RECENT_ARTISTS) },
            onNavigateToArtist = onNavigateToArtist
        )
        HomeAlbumSection(
            title = "Recent albums",
            tracks = albumTracks.asReversed().take(5),
            allTracks = tracks,
            playerViewModel = playerViewModel,
            onSeeAll = { onNavigateToSection(HomeSectionType.RECENT_ALBUMS) },
            onNavigateToAlbum = onNavigateToAlbum
        )
    }
}

@Composable
private fun HomeArtistSection(
    title: String,
    tracks: List<TrackUiModel>,
    allTracks: List<TrackUiModel>,
    playerViewModel: PlayerViewModel,
    onSeeAll: () -> Unit,
    onNavigateToArtist: (String) -> Unit
) {
    if (tracks.isEmpty()) return

    HomeSectionHeader(title = title, onSeeAll = onSeeAll)
    val artistListState = rememberLazyListState()
    LazyRow(
        state = artistListState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(tracks, key = { "artist-${it.artist}" }) { track ->
            Column(
                modifier = Modifier
                    .width(112.dp)
                    .clickable { onNavigateToArtist(track.artist) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncArtwork(
                    uri = track.artworkUri,
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape),
                    shape = CircleShape
                )
                Text(
                    text = track.artist,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HomeAlbumSection(
    title: String,
    tracks: List<TrackUiModel>,
    allTracks: List<TrackUiModel>,
    playerViewModel: PlayerViewModel,
    onSeeAll: () -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    if (tracks.isEmpty()) return

    HomeSectionHeader(title = title, onSeeAll = onSeeAll)
    val albumListState = rememberLazyListState()
    LazyRow(
        state = albumListState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(tracks, key = { "album-${it.album}" }) { track ->
            Column(
                modifier = Modifier
                    .width(144.dp)
                    .clickable { onNavigateToAlbum(track.album) }
            ) {
                AsyncArtwork(
                    uri = track.artworkUri,
                    modifier = Modifier
                        .size(144.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                )
                Text(
                    text = track.album,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 24.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        IconButton(onClick = onSeeAll) {
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = "See all $title",
                tint = MaterialTheme.colorScheme.primaryContainer
            )
        }
    }
}

@Composable
private fun SuggestionsSection(
    tracks: List<TrackUiModel>,
    playerViewModel: PlayerViewModel
) {
    if (tracks.isEmpty()) return

    val artworkTracks = tracks.filter { it.artworkUri != null }.ifEmpty { tracks }
    val suggestionTracks = artworkTracks.take(8)
    val featuredTrack = suggestionTracks.first()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Suggestions",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = { }) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh suggestions",
                    tint = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
            .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clickable { playerViewModel.playTrack(featuredTrack, tracks) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "New\nMusic\nMix",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 28.sp,
                        lineHeight = 27.sp,
                        letterSpacing = (-0.6).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            suggestionTracks.getOrNull(1)?.let { track ->
                ArtworkTile(
                    track = track,
                    modifier = Modifier
                        .weight(0.5f)
                        .aspectRatio(1f),
                    onClick = { playerViewModel.playTrack(track, tracks) }
                )
            }
            suggestionTracks.getOrNull(2)?.let { track ->
                ArtworkTile(
                    track = track,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    onClick = { playerViewModel.playTrack(track, tracks) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 4.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            suggestionTracks.drop(3).forEach { track ->
                ArtworkTile(
                    track = track,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    onClick = { playerViewModel.playTrack(track, tracks) }
                )
            }
        }
    }
}

@Composable
private fun ArtworkTile(
    track: TrackUiModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AsyncArtwork(
        uri = track.artworkUri,
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun SmartShuffleCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Widgets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Smart Shuffle",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Play similar music with smooth transitions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { /* TODO */ },
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play Smart Shuffle",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun QuickAccessGrid(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            QuickAccessCard(
                title = "History",
                icon = Icons.Filled.History,
                modifier = Modifier.weight(1f)
            )
            QuickAccessCard(
                title = "Last added",
                icon = Icons.Filled.PlaylistAdd,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            QuickAccessCard(
                title = "Most played",
                icon = Icons.Filled.TrendingUp,
                modifier = Modifier.weight(1f)
            )
            QuickAccessCard(
                title = "Clusters",
                icon = Icons.Filled.Widgets,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun QuickAccessCard(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun TrackSection(title: String, tracks: List<TrackUiModel>, playerViewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    if (tracks.isEmpty()) return
    
    Column(modifier = modifier.padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "See all",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        tracks.forEach { track ->
            TrackRow(
                track = track,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { playerViewModel.playTrack(track, tracks) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                onPlayNext = { playerViewModel.playNext(track) },
                onAddToQueue = { playerViewModel.addToQueue(track) }
            )
        }
    }
}
