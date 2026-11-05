package com.amit152116.riffle.ui.library

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.amit152116.riffle.ui.player.PlayerViewModel
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.amit152116.riffle.R
import com.amit152116.riffle.data.model.GridStyle
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.CircularProgressIndicator
import com.amit152116.riffle.ui.components.AddToPlaylistSheet
import com.amit152116.riffle.ui.components.GridStyleTile
import com.amit152116.riffle.ui.components.TrackRow
import com.amit152116.riffle.ui.components.RiffleTopBar
import com.amit152116.riffle.ui.components.AlphabetScrollbar
import com.amit152116.riffle.ui.components.AsyncArtwork
import com.amit152116.riffle.ui.playlist.PlaylistViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryCatalogScreen(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onOpenPlaylist: (Long) -> Unit = {}
) {
    var selectedTabIndex by remember { mutableStateOf(0) } // Default to Songs
    var selectedTrackIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectionMenuExpanded by remember { mutableStateOf(false) }
    var showBulkAddToPlaylist by remember { mutableStateOf(false) }
    val playlistViewModel: PlaylistViewModel = viewModel()
    val songs by libraryViewModel.songs.collectAsState()
    val isScanningLibrary by libraryViewModel.isScanning.collectAsState()
    val lastScanResult by libraryViewModel.lastScanResult.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(lastScanResult) {
        lastScanResult?.let { result ->
            Toast.makeText(
                context,
                "Scanned ${result.scanned} tracks" +
                    if (result.failed > 0) ", ${result.failed} failed" else "",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val allSongsSelected = songs.isNotEmpty() && selectedTrackIds.size == songs.size
    val tabs = listOf("Songs", "Artists", "Albums", "Genres", "Clusters", "Playlists")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val pagerScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
        selectedTrackIds = emptySet()
    }

    Scaffold(
        topBar = {
            if (selectedTrackIds.isNotEmpty()) {
                RiffleTopBar(
                    title = "${selectedTrackIds.size} selected",
                    navigationIcon = {
                        IconButton(onClick = { selectedTrackIds = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val selectedTracks = songs.filter { it.id in selectedTrackIds }
                            if (selectedTracks.isNotEmpty()) {
                                playerViewModel.playTrack(selectedTracks.first(), selectedTracks)
                                selectedTrackIds = emptySet()
                            }
                        }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play selected")
                        }
                        IconButton(onClick = {
                            playerViewModel.playNext(songs.filter { it.id in selectedTrackIds })
                            selectedTrackIds = emptySet()
                        }) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Play selected next")
                        }
                        IconButton(onClick = {
                            playerViewModel.addToQueue(songs.filter { it.id in selectedTrackIds })
                            selectedTrackIds = emptySet()
                        }) {
                            Icon(Icons.Filled.QueueMusic, contentDescription = "Add selected to queue")
                        }
                        Box {
                            IconButton(onClick = { selectionMenuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More selection options")
                            }
                            DropdownMenu(
                                expanded = selectionMenuExpanded,
                                onDismissRequest = { selectionMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share selected songs") },
                                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                    onClick = {
                                        val selectedTracks = songs.filter { it.id in selectedTrackIds }
                                        if (selectedTracks.isNotEmpty()) {
                                            context.startActivity(
                                                Intent.createChooser(
                                                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                                        type = "audio/*"
                                                        putParcelableArrayListExtra(
                                                            Intent.EXTRA_STREAM,
                                                            ArrayList(selectedTracks.map { it.mediaUri })
                                                        )
                                                        putExtra(
                                                            Intent.EXTRA_TEXT,
                                                            selectedTracks.joinToString("\n") { "${it.title} • ${it.artist}" }
                                                        )
                                                    },
                                                    "Share songs"
                                                )
                                            )
                                            selectedTrackIds = emptySet()
                                        }
                                        selectionMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(if (allSongsSelected) "Deselect all songs" else "Select all songs")
                                    },
                                    leadingIcon = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
                                    onClick = {
                                        if (allSongsSelected) {
                                            selectedTrackIds = emptySet()
                                        } else {
                                            selectedTrackIds = songs.mapTo(mutableSetOf()) { it.id }
                                        }
                                        selectionMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to playlist") },
                                    onClick = {
                                        showBulkAddToPlaylist = true
                                        selectionMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete from device") },
                                    onClick = {
                                        Toast.makeText(
                                            context,
                                            "Delete support coming soon",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        selectionMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                )
            } else {
                RiffleTopBar(
                    title = "Library",
                    actions = {
                        IconButton(
                            onClick = { libraryViewModel.scanLibrary() },
                            enabled = !isScanningLibrary
                        ) {
                            if (isScanningLibrary) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Filled.Sync, contentDescription = "Scan library")
                            }
                        }
                        IconButton(onClick = onNavigateToSearch) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tabs
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    divider = {},
                    edgePadding = 8.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                pagerScope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selectedTabIndex == index) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }

                when (selectedTabIndex) {
                    1 -> GridStyleMenuButton(
                        currentStyle = libraryViewModel.artistGridStyle.collectAsState().value,
                        onStyleSelected = libraryViewModel::setArtistGridStyle
                    )
                    2 -> GridStyleMenuButton(
                        currentStyle = libraryViewModel.albumGridStyle.collectAsState().value,
                        onStyleSelected = libraryViewModel::setAlbumGridStyle
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> SongsContent(
                        viewModel = libraryViewModel,
                        playerViewModel = playerViewModel,
                        onNavigateToArtist = onNavigateToArtist,
                        onNavigateToAlbum = onNavigateToAlbum,
                        selectedTrackIds = selectedTrackIds,
                        onToggleTrackSelection = { trackId ->
                            selectedTrackIds = if (trackId in selectedTrackIds) {
                                selectedTrackIds - trackId
                            } else {
                                selectedTrackIds + trackId
                            }
                        }
                    )
                    1 -> ArtistsContent(viewModel = libraryViewModel, onNavigateToArtist = onNavigateToArtist)
                    2 -> AlbumsContent(viewModel = libraryViewModel, onNavigateToAlbum = onNavigateToAlbum)
                    3 -> GenresContent(viewModel = libraryViewModel)
                    4 -> ClustersContent()
                    5 -> PlaylistsContent(viewModel = playlistViewModel, onOpenPlaylist = onOpenPlaylist)
                }
            }
        }
    }

    if (showBulkAddToPlaylist) {
        AddToPlaylistSheet(
            tracks = songs.filter { it.id in selectedTrackIds },
            viewModel = playlistViewModel,
            onDismiss = {
                showBulkAddToPlaylist = false
                selectedTrackIds = emptySet()
            }
        )
    }
}

@Composable
fun GridStyleMenuButton(currentStyle: GridStyle, onStyleSelected: (GridStyle) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.GridView, contentDescription = "Change grid style")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GridStyle.entries.forEach { style ->
                DropdownMenuItem(
                    text = { Text(style.label) },
                    onClick = {
                        onStyleSelected(style)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SearchBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Search artists, albums, tracks...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SongsContent(
    viewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    selectedTrackIds: Set<Long> = emptySet(),
    onToggleTrackSelection: (Long) -> Unit = {}
) {
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val listState = rememberLazyListState()

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No songs found on device.")
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 12.dp)
            ) {
                items(
                    count = songs.size,
                    key = { songs[it].id }
                ) { index ->
                    val track = songs[index]
                    TrackRow(
                        track = track,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selectedTrackIds.isNotEmpty()) {
                                        onToggleTrackSelection(track.id)
                                    } else {
                                        playerViewModel.playTrack(track, songs)
                                    }
                                },
                                onLongClick = { onToggleTrackSelection(track.id) }
                            )
                            .background(
                                if (track.id in selectedTrackIds) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                } else {
                                    Color.Transparent
                                },
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        onPlayNext = { playerViewModel.playNext(track) },
                        onAddToQueue = { playerViewModel.addToQueue(track) },
                        onGoToArtist = { onNavigateToArtist(track.artist) },
                        onGoToAlbum = { onNavigateToAlbum(track.album) },
                        onAddToBlacklist = { playerViewModel.blacklistTrack(track) },
                        selectionMode = selectedTrackIds.isNotEmpty(),
                        selected = track.id in selectedTrackIds,
                        onSelectionToggle = { onToggleTrackSelection(track.id) }
                    )
                }
            }

            AlphabetScrollbar(
                labels = songs.map { it.title },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(start = 6.dp, end = 6.dp),
                onLetterSelected = { letter ->
                    val targetIndex = songs.indexOfFirst {
                        val first = it.title.firstOrNull()?.uppercaseChar() ?: '#'
                        if (letter == '#') first !in 'A'..'Z' else first == letter
                    }
                    if (targetIndex >= 0) {
                        listState.requestScrollToItem(targetIndex)
                    }
                }
            )
        }
    }
}

@Composable
fun ArtistsContent(viewModel: LibraryViewModel, onNavigateToArtist: (String) -> Unit = {}) {
    val artists by viewModel.artists.collectAsState()
    val gridStyle by viewModel.artistGridStyle.collectAsState()
    val gridState = rememberLazyGridState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 12.dp)
        ) {
            gridItems(artists) { artist ->
                GridStyleTile(
                    title = artist.name,
                    subtitle = if (gridStyle == GridStyle.COLORED_CARD) null else "${artist.trackCount} tracks",
                    artworkUri = artist.artworkUri,
                    style = gridStyle,
                    onClick = { onNavigateToArtist(artist.name) }
                )
            }
        }
        AlphabetScrollbar(
            labels = artists.map { it.name },
            modifier = Modifier.align(Alignment.CenterEnd).padding(start = 6.dp, end = 6.dp),
            onLetterSelected = { letter ->
                val targetIndex = artists.indexOfFirst { item ->
                    val first = item.name.firstOrNull()?.uppercaseChar() ?: '#'
                    if (letter == '#') first !in 'A'..'Z' else first == letter
                }
                if (targetIndex >= 0) gridState.requestScrollToItem(targetIndex)
            }
        )
    }
}

@Composable
fun AlbumsContent(viewModel: LibraryViewModel, onNavigateToAlbum: (String) -> Unit = {}) {
    val albums by viewModel.albums.collectAsState()
    val gridStyle by viewModel.albumGridStyle.collectAsState()
    val gridState = rememberLazyGridState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 12.dp)
        ) {
            gridItems(albums) { album ->
                GridStyleTile(
                    title = album.name,
                    subtitle = album.artist,
                    artworkUri = album.artworkUri,
                    style = gridStyle,
                    onClick = { onNavigateToAlbum(album.name) }
                )
            }
        }
        AlphabetScrollbar(
            labels = albums.map { it.name },
            modifier = Modifier.align(Alignment.CenterEnd).padding(start = 6.dp, end = 6.dp),
            onLetterSelected = { letter ->
                val targetIndex = albums.indexOfFirst { item ->
                    val first = item.name.firstOrNull()?.uppercaseChar() ?: '#'
                    if (letter == '#') first !in 'A'..'Z' else first == letter
                }
                if (targetIndex >= 0) gridState.requestScrollToItem(targetIndex)
            }
        )
    }
}

@Composable
fun GenresContent(viewModel: LibraryViewModel) {
    val genreModels by viewModel.genres.collectAsState()
    val genres = genreModels.map { GenreItem(it.name, "${it.trackCount} tracks") }

    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        gridItems(genres) { genre ->
            GenreCard(genre)
        }
    }
}

@Composable
fun GenreCard(genre: GenreItem) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // R.mipmap.default_track is an adaptive-icon mipmap - painterResource()
            // can't load it directly ("Only VectorDrawables and rasterized asset
            // types are supported"). AsyncArtwork already routes the same resource
            // through Coil for its null-uri fallback, which decodes it fine.
            AsyncArtwork(
                uri = null,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp)
            )

            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = genre.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = genre.trackCount,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ClustersContent() {
    val clusters = listOf(
        GenreItem("Chill Electronic", "234 tracks"),
        GenreItem("Deep House", "187 tracks"),
        GenreItem("Ambient", "156 tracks"),
        GenreItem("Downtempo", "142 tracks"),
        GenreItem("Melodic Techno", "98 tracks"),
        GenreItem("Experimental", "87 tracks")
    )

    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        gridItems(clusters) { cluster ->
            GenreCard(cluster) // Reusing GenreCard for visual consistency
        }
    }
}

@Composable
fun PlaylistsContent(viewModel: PlaylistViewModel, onOpenPlaylist: (Long) -> Unit) {
    val playlists by viewModel.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No playlists yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                items(playlists) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPlaylist(playlist.id) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New playlist")
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createPlaylist(newName)
                    newName = ""
                    showCreateDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

data class GenreItem(val name: String, val trackCount: String)
