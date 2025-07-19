package com.nova.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.nova.music.data.model.Song
import com.nova.music.ui.viewmodels.LibraryViewModel
import com.nova.music.ui.components.RecentlyPlayedItem
import com.nova.music.ui.components.PlaylistSelectionDialog
import com.nova.music.ui.components.SearchBar
import com.nova.music.ui.components.RecentlyPlayedItemSkeleton
import com.nova.music.ui.viewmodels.SearchViewModel
import com.nova.music.ui.viewmodels.PlayerViewModel
import com.nova.music.ui.util.rememberDynamicBottomPadding
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import com.nova.music.util.TimeUtils.formatDuration
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.delay
import android.util.Log
import com.nova.music.ui.components.ShimmerBrush

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    onSongClick: (String) -> Unit,
    navController: NavController
) {
    val searchResults by viewModel.searchResults.collectAsState(initial = emptyList())
    val recentSearches by viewModel.recentSearches.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val currentFilterItem by viewModel.currentFilterItem.collectAsState()
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
    val focusManager = LocalFocusManager.current
    
    // Search filter states
    var selectedFilter by remember { mutableStateOf("All") }
    val filterOptions = listOf("All", "Songs", "Artists", "Albums")
    
    // Get player state
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    
    val bottomPadding by rememberDynamicBottomPadding()

    // Force search activation when component is first displayed
    LaunchedEffect(Unit) {
        // Small delay to ensure the UI is ready
        delay(300)
        if (searchQuery.isNotBlank()) {
            isSearchActive = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Search filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            filterOptions.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { 
                        selectedFilter = filter
                        // Re-search with new filter if we already have a query
                        if (searchQuery.isNotBlank() && hasSearched) {
                            scope.launch {
                                viewModel.search(searchQuery, selectedFilter.lowercase())
                            }
                        }
                    },
                    label = { Text(filter) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.height(32.dp)
                )
                if (filter != filterOptions.last()) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
        
        // Search bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { query ->
                    searchQuery = query
                    // Don't search immediately, just update the query
                    Log.d("SearchScreen", "Query changed: $query")
                },
                onSearch = {
                    if (searchQuery.isNotBlank()) {
                        hasSearched = true
                        focusManager.clearFocus() // Hide keyboard
                        scope.launch {
                            viewModel.addToRecentSearches(searchQuery)
                            viewModel.search(searchQuery, selectedFilter.lowercase())
                        }
                        Log.d("SearchScreen", "Search performed with filter: $selectedFilter")
                    }
                },
                active = isSearchActive,
                onActiveChange = { 
                    isSearchActive = it
                    Log.d("SearchScreen", "Search active: $it")
                },
                modifier = Modifier.fillMaxWidth(),
                hasRecentSearches = recentSearches.isNotEmpty(),
                 )
        }

        // Main content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when {
                searchQuery.isNotEmpty() && hasSearched -> {
                    // Show search results or loading/error states
                    if (isLoading) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 16.dp, bottom = bottomPadding.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Text(
                                    text = "Searching for \"$searchQuery\"...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            
                            // Skeleton loading for artists
                            if (selectedFilter == "All" || selectedFilter == "Artists") {
                                item {
                                    Text(
                                        text = "Artists",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                    )
                                }
                                
                                item {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        items(4) {
                                            // Artist skeleton
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.width(100.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(80.dp)
                                                        .clip(CircleShape)
                                                        .background(ShimmerBrush())
                                                )
                                                
                                                Spacer(modifier = Modifier.height(8.dp))
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .width(60.dp)
                                                        .height(16.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(ShimmerBrush())
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // Skeleton loading for songs
                            item {
                                Text(
                                    text = "Songs",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                            }
                            
                            items(8) {
                                RecentlyPlayedItemSkeleton()
                            }
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
                            // Display current filter item if we're viewing a specific album or artist
                            if (currentFilterItem != null) {
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (selectedFilter == "Albums") "Album" else "Artist",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                )
                                                Text(
                                                    text = currentFilterItem ?: "",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            IconButton(onClick = {
                                                // Clear filter and go back to regular search
                                                scope.launch {
                                                    viewModel.search(searchQuery, selectedFilter.lowercase())
                                                }
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear filter",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                item {
                                    Text(
                                        text = "Results for \"$searchQuery\"${if(selectedFilter != "All") " in $selectedFilter" else ""}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                
                                // Show unique artists if in "All" or "Artists" filter mode
                                if ((selectedFilter == "All" || selectedFilter == "Artists") && currentFilterItem == null) {
                                    val uniqueArtists = searchResults
                                        .groupBy { it.artist.trim() }
                                        .filter { it.key.isNotBlank() }
                                        .map { entry ->
                                            ArtistInfo(
                                                name = entry.key,
                                                // Get the first song's album art as a fallback artist image
                                                imageUrl = entry.value.firstOrNull()?.albumArtUrl ?: "",
                                                songCount = entry.value.size
                                            )
                                        }
                                    
                                    if (uniqueArtists.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = "Artists",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                            )
                                        }
                                        
                                        item {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                contentPadding = PaddingValues(vertical = 8.dp)
                                            ) {
                                                items(uniqueArtists) { artist ->
                                                    ArtistItem(
                                                        artist = artist,
                                                        onClick = {
                                                            scope.launch {
                                                                viewModel.searchByArtist(artist.name)
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                // Show unique albums if in "All" or "Albums" filter mode
                                if ((selectedFilter == "All" || selectedFilter == "Albums") && currentFilterItem == null) {
                                    val uniqueAlbums = searchResults
                                        .filter { it.album.isNotBlank() }
                                        .groupBy { it.album.trim() }
                                        .filter { entry -> entry.value.size >= 1 } // Only show albums with at least one song
                                        .map { entry -> 
                                            // Group songs by artist to ensure we only show albums from the same artist
                                            val songsGroupedByArtist = entry.value.groupBy { it.artist.trim() }
                                            val mostFrequentArtist = songsGroupedByArtist.maxByOrNull { it.value.size }?.key ?: ""
                                            val songsFromSameArtist = songsGroupedByArtist[mostFrequentArtist] ?: emptyList()
                                            
                                            AlbumInfo(
                                                name = entry.key,
                                                artUrl = songsFromSameArtist.firstOrNull()?.albumArtUrl ?: "",
                                                artistName = mostFrequentArtist,
                                                songCount = songsFromSameArtist.size
                                            )
                                        }
                                    
                                    if (uniqueAlbums.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = "Albums",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                            )
                                        }
                                        
                                        item {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                contentPadding = PaddingValues(vertical = 8.dp)
                                            ) {
                                                items(uniqueAlbums) { album ->
                                                    AlbumItem(
                                                        album = album,
                                                        onClick = {
                                                            scope.launch {
                                                                // Pass both album name and artist name to ensure we only get songs from the correct album
                                                                viewModel.searchByAlbum(album.name)
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                item {
                                    Text(
                                        text = "Songs",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                    )
                                }
                            }
                            
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
                                    val isSongPlaying = currentSong?.id == song.id && isPlaying
                                    val isSelected = currentSong?.id == song.id && !isPlaying
                                    RecentlyPlayedItem(
                                        song = song,
                                        onClick = { 
                                            // Add to recently played and directly load song in player
                                            viewModel.addToRecentlyPlayed(song)
                                            Log.d("SearchScreen", "Passing searchResults of size: ${searchResults.size}, ids: ${searchResults.map { it.id }} to loadSong")
                                            playerViewModel.loadSong(song, playlistId = "search_results", playlistSongs = searchResults)
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
                                        onRemoveFromPlaylist = null,
                                        isPlaying = isSongPlaying,
                                        isSelected = isSelected,
                                        onPlayPause = {
                                            if (currentSong?.id == song.id) {
                                                playerViewModel.togglePlayPause()
                                            } else {
                                                viewModel.addToRecentlyPlayed(song)
                                                Log.d("SearchScreen", "Passing searchResults of size: ${searchResults.size}, ids: ${searchResults.map { it.id }} to loadSong (onPlayPause)")
                                                playerViewModel.loadSong(song, playlistId = "search_results", playlistSongs = searchResults)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Show recent searches if available, otherwise show empty state
                    if (recentSearches.isNotEmpty()) {
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
                                                    viewModel.search(search, selectedFilter.lowercase())
                                                }
                                                hasSearched = true
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
                    } else {
                        // Show empty state when no recent searches
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "Search for songs, artists, or albums",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "Press Enter to search",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray.copy(alpha = 0.7f)
                                )
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

// Data class for album information
data class AlbumInfo(
    val name: String,
    val artUrl: String,
    val artistName: String,
    val songCount: Int = 0
)

// Data class for artist information
data class ArtistInfo(
    val name: String,
    val imageUrl: String,
    val songCount: Int = 0
)

// Artist item composable
@Composable
fun ArtistItem(
    artist: ArtistInfo,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick)
    ) {
        // Artist circle avatar with image or first letter
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            if (artist.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = artist.imageUrl,
                    contentDescription = "Artist image for ${artist.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = artist.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${artist.songCount} ${if (artist.songCount == 1) "Song" else "Songs"}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Album item composable
@Composable
fun AlbumItem(
    album: AlbumInfo,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        // Album cover
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(120.dp)
        ) {
            if (album.artUrl.isNotBlank()) {
                AsyncImage(
                    model = album.artUrl,
                    contentDescription = "Album art for ${album.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        
        Text(
            text = album.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        
        Text(
            text = "${album.songCount} ${if (album.songCount == 1) "Song" else "Songs"}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
} 