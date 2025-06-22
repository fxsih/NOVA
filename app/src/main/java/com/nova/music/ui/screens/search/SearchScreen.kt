package com.nova.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nova.music.data.model.Song
import com.nova.music.ui.viewmodels.LibraryViewModel
import com.nova.music.ui.components.RecentlyPlayedItem
import com.nova.music.ui.components.PlaylistSelectionDialog
import com.nova.music.ui.components.SearchBar
import com.nova.music.ui.viewmodels.SearchViewModel
import com.nova.music.ui.util.rememberDynamicBottomPadding
import kotlinx.coroutines.launch
import com.nova.music.util.TimeUtils.formatDuration
import androidx.compose.foundation.BorderStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onSongClick: (String) -> Unit,
    navController: NavController
) {
    val searchResults by viewModel.searchResults.collectAsState(initial = emptyList())
    val recentSearches by viewModel.recentSearches.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var showDetailsDialog by remember { mutableStateOf(false) }
    var detailsSong by remember { mutableStateOf<Song?>(null) }
    val playlists by libraryViewModel.playlists.collectAsState()
    val likedSongs by libraryViewModel.likedSongs.collectAsState()
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    
    val bottomPadding by rememberDynamicBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        SearchBar(
            query = searchQuery,
            onQueryChange = { query ->
                searchQuery = query
                scope.launch {
                    viewModel.search(query)
                }
            },
            onSearch = {
                if (searchQuery.isNotBlank()) {
                    hasSearched = true
                    scope.launch {
                        viewModel.addToRecentSearches(searchQuery)
                    }
                }
            },
            active = isSearchActive,
            onActiveChange = { isSearchActive = it },
            modifier = Modifier.fillMaxWidth(),
            hasRecentSearches = recentSearches.isNotEmpty()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when {
                searchQuery.isNotEmpty() -> {
                    // Show search results or loading/error states
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (errorMessage != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = errorMessage ?: "Unknown error occurred",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 16.dp, bottom = bottomPadding.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (searchResults.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No results found",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White
                                        )
                                    }
                                }
                            } else {
                                items(searchResults) { song ->
                                    val isLiked = likedSongs.any { it.id == song.id }
                                    RecentlyPlayedItem(
                                        song = song,
                                        onClick = { 
                                            // Add to recently played and navigate to player
                                            viewModel.addToRecentlyPlayed(song)
                                            onSongClick(song.id)
                                        },
                                        onLikeClick = {
                                            if (isLiked) {
                                                libraryViewModel.removeSongFromLiked(song.id)
                                            } else {
                                                libraryViewModel.addSongToLiked(song)
                                            }
                                        },
                                        onAddToPlaylist = {
                                            selectedSong = song
                                            showPlaylistDialog = true
                                        },
                                        isLiked = isLiked,
                                        onDetailsClick = {
                                            detailsSong = song
                                            showDetailsDialog = true
                                        },
                                        onRemoveFromPlaylist = null
                                    )
                                }
                            }
                        }
                    }
                }
                hasSearched && recentSearches.isNotEmpty() -> {
                    // Show recent searches
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = bottomPadding.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = "Recent Searches",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        items(recentSearches.filter { it.isNotBlank() }) { search ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF000000)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchQuery = search
                                            scope.launch {
                                                viewModel.search(search)
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "Recent search",
                                            tint = Color.White
                                        )
                                        Text(
                                            text = search,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                viewModel.removeFromRecentSearches(search)
                                                if (recentSearches.size <= 1) {
                                                    hasSearched = false
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove search",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Song Details Dialog
    if (showDetailsDialog && detailsSong != null) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text("Song Details") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Title: ${detailsSong!!.title}")
                    Text("Artist: ${detailsSong!!.artist}")
                    Text("Album: ${detailsSong!!.album}")
                    Text("Duration: ${formatDuration(detailsSong!!.duration)}")
                    if (detailsSong!!.id.startsWith("yt_")) {
                        Text("Source: YouTube Music")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Add to Playlist Dialog
    if (showPlaylistDialog && selectedSong != null) {
        PlaylistSelectionDialog(
            onDismiss = { 
                showPlaylistDialog = false
                selectedSong = null
            },
            onPlaylistSelected = { playlist ->
                scope.launch {
                    if (playlist.id == "liked_songs") {
                        val isLiked = likedSongs.any { it.id == selectedSong!!.id }
                        if (isLiked) {
                            libraryViewModel.removeSongFromLiked(selectedSong!!.id)
                        } else {
                            libraryViewModel.addSongToLiked(selectedSong!!)
                        }
                    } else {
                        libraryViewModel.addSongToPlaylist(selectedSong!!, playlist.id)
                    }
                }
            },
            onCreateNewPlaylist = { name ->
                scope.launch {
                    libraryViewModel.createPlaylist(name)
                    // Wait for the playlist to be created and get its ID
                    playlists.firstOrNull { it.name == name }?.let { newPlaylist ->
                        libraryViewModel.addSongToPlaylist(selectedSong!!, newPlaylist.id)
                    }
                }
            },
            onRenamePlaylist = { playlist, newName ->
                scope.launch {
                    libraryViewModel.renamePlaylist(playlist.id, newName)
                }
            },
            playlists = playlists,
            selectedPlaylistIds = buildSet {
                addAll(playlists
                    .filter { playlist -> playlist.songs.any { it.id == selectedSong!!.id } }
                    .map { it.id })
                if (likedSongs.any { it.id == selectedSong!!.id }) add("liked_songs")
            }
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
} 