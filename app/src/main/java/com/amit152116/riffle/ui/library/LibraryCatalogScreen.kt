package com.amit152116.riffle.ui.library

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import com.amit152116.riffle.ui.player.PlayerViewModel
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.amit152116.riffle.R
import com.amit152116.riffle.data.model.GridStyle
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.CircularProgressIndicator
import com.amit152116.riffle.ui.components.GridStyleTile
import com.amit152116.riffle.ui.components.TrackRow
import com.amit152116.riffle.ui.components.RiffleTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryCatalogScreen(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {}
) {
    var selectedTabIndex by remember { mutableStateOf(0) } // Default to Songs
    val tabs = listOf("Songs", "Artists", "Albums", "Genres", "Clusters")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val pagerScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }

    Scaffold(
        topBar = {
            RiffleTopBar(
                title = "Library",
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                }
            )
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
                    0 -> SongsContent(viewModel = libraryViewModel, playerViewModel = playerViewModel)
                    1 -> ArtistsContent(viewModel = libraryViewModel, onNavigateToArtist = onNavigateToArtist)
                    2 -> AlbumsContent(viewModel = libraryViewModel, onNavigateToAlbum = onNavigateToAlbum)
                    3 -> GenresContent()
                    4 -> ClustersContent()
                }
            }
        }
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
fun SongsContent(viewModel: LibraryViewModel, playerViewModel: PlayerViewModel) {
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
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier.fillMaxSize()
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
                        .clickable { playerViewModel.playTrack(track, songs) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    onPlayNext = { playerViewModel.playNext(track) },
                    onAddToQueue = { playerViewModel.addToQueue(track) }
                )
            }
        }
    }
}

@Composable
fun ArtistsContent(viewModel: LibraryViewModel, onNavigateToArtist: (String) -> Unit = {}) {
    val artists by viewModel.artists.collectAsState()
    val gridStyle by viewModel.artistGridStyle.collectAsState()
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
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
}

@Composable
fun AlbumsContent(viewModel: LibraryViewModel, onNavigateToAlbum: (String) -> Unit = {}) {
    val albums by viewModel.albums.collectAsState()
    val gridStyle by viewModel.albumGridStyle.collectAsState()
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
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
}

@Composable
fun GenresContent() {
    val genres = listOf(
        GenreItem("Electronic", "1,234 tracks"),
        GenreItem("Rock", "892 tracks"),
        GenreItem("Jazz", "456 tracks"),
        GenreItem("Ambient", "321 tracks"),
        GenreItem("Hip Hop", "287 tracks"),
        GenreItem("Classical", "243 tracks")
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
            Image(
                painter = painterResource(id = R.drawable.screen),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
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

data class GenreItem(val name: String, val trackCount: String)
