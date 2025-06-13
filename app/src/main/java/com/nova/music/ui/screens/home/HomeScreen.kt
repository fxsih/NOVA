package com.nova.music.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nova.music.R
import com.nova.music.data.model.Song
import com.nova.music.ui.viewmodels.HomeViewModel
import com.nova.music.ui.viewmodels.LibraryViewModel
import coil.compose.AsyncImage
import com.nova.music.ui.components.RecentlyPlayedItem
import com.nova.music.ui.components.RecommendedSongCard
import com.nova.music.ui.components.PlaylistSelectionDialog
import com.nova.music.ui.components.AddToPlaylistDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val songs by viewModel.songs.collectAsState()
    val recommendedSongs by viewModel.recommendedSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val likedSongs by libraryViewModel.likedSongs.collectAsState()
    val playlists by libraryViewModel.playlists.collectAsState()
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
    ) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    text = "NOVA",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Text(
                    text = "Recommended",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )

                // Recommended Songs
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(recommendedSongs) { song ->
                        val isLiked = likedSongs.any { it.id == song.id }
                        RecommendedSongCard(
                            song = song,
                            onClick = { 
                                viewModel.addToRecentlyPlayed(song)
                                onNavigateToPlayer(song.id)
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
                            isLiked = isLiked
                        )
                    }
                }

                Text(
                    text = "Recently Played",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
            }

            items(recentlyPlayed) { song ->
                val isLiked = likedSongs.any { it.id == song.id }
                RecentlyPlayedItem(
                    song = song,
                    onClick = { 
                        viewModel.addToRecentlyPlayed(song)
                        onNavigateToPlayer(song.id)
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
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item {
                Text(
                    text = "Popular Tracks",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
            }

            items(songs.take(5)) { song ->
                val isLiked = likedSongs.any { it.id == song.id }
                RecentlyPlayedItem(
                    song = song,
                    onClick = { 
                        viewModel.addToRecentlyPlayed(song)
                        onNavigateToPlayer(song.id)
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
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }

    // Add to Playlist Dialog
    if (showPlaylistDialog) {
        selectedSong?.let { song ->
            PlaylistSelectionDialog(
                onDismiss = { 
                    showPlaylistDialog = false
                    selectedSong = null
                },
                onPlaylistSelected = { playlist ->
                    scope.launch {
                        if (playlist.id == "liked_songs") {
                            val isLiked = likedSongs.any { it.id == song.id }
                            if (isLiked) {
                                libraryViewModel.removeSongFromLiked(song.id)
                            } else {
                                libraryViewModel.addSongToLiked(song)
                            }
                        } else {
                            libraryViewModel.addSongToPlaylist(song, playlist.id)
                        }
                    }
                },
                onCreateNewPlaylist = { name ->
                    scope.launch {
                        libraryViewModel.createPlaylist(name)
                        // Wait for the playlist to be created and get its ID
                        playlists.firstOrNull { it.name == name }?.let { newPlaylist ->
                            libraryViewModel.addSongToPlaylist(song, newPlaylist.id)
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
                        .filter { playlist -> playlist.songs.any { it.id == song.id } }
                        .map { it.id })
                    if (likedSongs.any { it.id == song.id }) add("liked_songs")
                }
            )
        }
    }

    if (showCreatePlaylistDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create New Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            scope.launch {
                                libraryViewModel.createPlaylist(playlistName)
                            }
                            showCreatePlaylistDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RecommendedSongCard(
    song: Song,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    isLiked: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(160.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            AsyncImage(
                model = song.albumArtUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .background(Color(0xFF2A2A2A).copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onLikeClick) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = if (isLiked) Color.Red else Color.White
                )
            }
            
            IconButton(onClick = onAddToPlaylist) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add to Playlist",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun RecentlyPlayedItem(
    song: Song,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    isLiked: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.albumArtUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF2A2A2A), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White
                )
            }
            
            IconButton(onClick = onLikeClick) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = if (isLiked) Color.Red else Color.White
                )
            }
            
            IconButton(onClick = onAddToPlaylist) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add to Playlist",
                    tint = Color.White
                )
            }
        }
    }
}