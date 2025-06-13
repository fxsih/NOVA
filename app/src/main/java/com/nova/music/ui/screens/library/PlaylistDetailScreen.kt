package com.nova.music.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nova.music.data.model.Song
import com.nova.music.ui.components.AddToPlaylistDialog
import com.nova.music.ui.components.PlaylistSelectionDialog
import com.nova.music.ui.components.RecentlyPlayedItem
import com.nova.music.ui.viewmodels.LibraryViewModel
import com.nova.music.ui.viewmodels.HomeViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    viewModel: HomeViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val playlists by libraryViewModel.playlists.collectAsState()
    val likedSongs by libraryViewModel.likedSongs.collectAsState()
    val playlist = if (playlistId == "liked_songs") null else playlists.find { it.id == playlistId }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var isShuffleEnabled by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
    ) {
        // Top Bar with back button
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        // Playlist Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Playlist Icon
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF282828)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playlistId == "liked_songs") Icons.Default.Favorite else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (playlistId == "liked_songs") Color(0xFFBB86FC) else Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Playlist Name
            Text(
                text = if (playlistId == "liked_songs") "Liked Songs" else (playlist?.name ?: ""),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Song Count
            Text(
                text = "${if (playlistId == "liked_songs") likedSongs.size else playlist?.songs?.size ?: 0} songs",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play Button
                Button(
                    onClick = {
                        val songs = if (playlistId == "liked_songs") likedSongs else playlist?.songs
                        songs?.firstOrNull()?.let { onNavigateToPlayer(it.id) }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFBB86FC)
                    ),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(24.dp)
                        )
                        Text("Play")
                    }
                }

                // Shuffle Button
                IconButton(
                    onClick = { 
                        isShuffleEnabled = !isShuffleEnabled
                        val songs = if (playlistId == "liked_songs") likedSongs else playlist?.songs
                        songs?.shuffled()?.firstOrNull()?.let { onNavigateToPlayer(it.id) }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = if (isShuffleEnabled) Color(0xFFBB86FC) else Color(0xFF282828),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) Color.Black else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider(color = Color(0xFF282828))
        }

        // Songs List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            val songs = if (playlistId == "liked_songs") likedSongs else (playlist?.songs ?: emptyList())
            items(songs) { song ->
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
                                // If we have a selected song, add it to the new playlist
                                selectedSong?.let { song ->
                                    // We need to wait for the playlist to be created and get its ID
                                    playlists.firstOrNull { it.name == playlistName }?.let { newPlaylist ->
                                        libraryViewModel.addSongToPlaylist(song, newPlaylist.id)
                                    }
                                }
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