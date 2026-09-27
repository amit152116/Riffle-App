package com.amit152116.riffle.ui.search

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amit152116.riffle.data.model.TrackUiModel
import com.amit152116.riffle.ui.components.AsyncArtwork
import com.amit152116.riffle.ui.components.RiffleTopBar
import com.amit152116.riffle.ui.components.TrackRow
import com.amit152116.riffle.ui.library.LibraryViewModel
import com.amit152116.riffle.ui.player.PlayerViewModel

private enum class SearchFilter(val label: String) {
    ALL("All"), SONGS("Songs"), ARTISTS("Artists"), ALBUMS("Albums")
}

@Composable
fun SearchScreen(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    val songs by libraryViewModel.songs.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(SearchFilter.ALL) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val resultsListState = rememberLazyListState()
    val filtersListState = rememberLazyListState()
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        query = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
    }

    val matchingSongs = songs.filter { track ->
        val text = query.trim()
        text.isEmpty() || listOf(track.title, track.artist, track.album)
            .any { it.contains(text, ignoreCase = true) }
    }
    val artists = matchingSongs.distinctBy { it.artist }
    val albums = matchingSongs.distinctBy { it.album }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    LaunchedEffect(query, selectedFilter) {
        resultsListState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            Column {
                RiffleTopBar(
                    onBack = onBack,
                    titleContent = {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = { Text("Search") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (query.isEmpty()) {
                                    IconButton(onClick = {
                                        voiceLauncher.launch(
                                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(
                                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                                )
                                            }
                                        )
                                    }) {
                                        Icon(Icons.Filled.Mic, contentDescription = "Voice search")
                                    }
                                } else {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.background,
                                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                )
                LazyRow(
                    state = filtersListState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SearchFilter.entries) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            state = resultsListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            if (query.isBlank()) {
                item { Text("Search your library", modifier = Modifier.padding(20.dp)) }
            } else {
                if (selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.SONGS) {
                    item { SearchSectionTitle("Songs") }
                    items(matchingSongs, key = { "song-${it.id}" }) { track ->
                        TrackRow(
                            track = track,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { playerViewModel.playTrack(track, matchingSongs) }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            onPlayNext = { playerViewModel.playNext(track) },
                            onAddToQueue = { playerViewModel.addToQueue(track) },
                            onGoToArtist = { onNavigateToArtist(track.artist) },
                            onGoToAlbum = { onNavigateToAlbum(track.album) }
                        )
                    }
                }
                if (selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.ARTISTS) {
                    item { SearchSectionTitle("Artists") }
                    items(artists, key = { "artist-${it.artist}" }) { track ->
                        SearchEntityRow(track, isArtist = true) { onNavigateToArtist(track.artist) }
                    }
                }
                if (selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.ALBUMS) {
                    item { SearchSectionTitle("Albums") }
                    items(albums, key = { "album-${it.album}" }) { track ->
                        SearchEntityRow(track, isArtist = false) { onNavigateToAlbum(track.album) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun SearchEntityRow(
    track: TrackUiModel,
    isArtist: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncArtwork(
            uri = track.artworkUri,
            modifier = Modifier.size(48.dp),
            shape = if (isArtist) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(8.dp)
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = if (isArtist) track.artist else track.album,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isArtist) "Artist" else track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
