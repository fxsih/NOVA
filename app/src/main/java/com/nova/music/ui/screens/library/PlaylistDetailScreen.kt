package com.nova.music.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nova.music.data.model.Song
import com.nova.music.ui.components.PlaylistSelectionDialog
import com.nova.music.ui.components.RecentlyPlayedItem
import com.nova.music.ui.viewmodels.LibraryViewModel
import com.nova.music.ui.viewmodels.HomeViewModel
import com.nova.music.ui.viewmodels.PlayerViewModel
import com.nova.music.ui.viewmodels.RepeatMode
import com.nova.music.ui.util.rememberDynamicBottomPadding
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    viewModel: HomeViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val playlists by libraryViewModel.playlists.collectAsState()
    val likedSongs by libraryViewModel.likedSongs.collectAsState()
    val currentPlaylist = playlists.find { it.id == playlistId }
    val playlistSongs by if (playlistId == "liked_songs") {
        libraryViewModel.likedSongs
    } else {
        libraryViewModel.getPlaylistSongs(playlistId)
    }.collectAsState(initial = emptyList())
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var detailsSong by remember { mutableStateOf<Song?>(null) }
    
    // Use the player's actual playing state instead of local state
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val currentPlaylistId by playerViewModel.currentPlaylistId.collectAsState()
    
    // Check if current song is from this playlist
    val isCurrentPlaylist = currentPlaylistId == playlistId
    
    // Function to play a song from this playlist
    fun playSongFromPlaylist(song: Song) {
        viewModel.addToRecentlyPlayed(song)
        // Make sure to pass the full playlist songs to the player
        println("DEBUG: Playing song from playlist: $playlistId with ${playlistSongs.size} songs")
        playerViewModel.loadSong(song, playlistId, playlistSongs)
        onNavigateToPlayer(song.id)
    }
    
    // Function to play the first song in the playlist
    fun playPlaylist() {
        if (playlistSongs.isEmpty()) return
        
            // Play the first song
            playSongFromPlaylist(playlistSongs.first())
    }
    
    val bottomPadding by rememberDynamicBottomPadding()

    Scaffold(
        topBar = {
        TopAppBar(
                title = { 
                    Text(
                        text = if (playlistId == "liked_songs") "Liked Songs" else (currentPlaylist?.name ?: ""),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (playlistId != "liked_songs") {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename Playlist") },
                                onClick = {
                                    showRenameDialog = true
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Playlist") },
                                onClick = {
                                    showDeleteDialog = true
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
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
                    text = if (playlistId == "liked_songs") "Liked Songs" else (currentPlaylist?.name ?: ""),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Song Count
            Text(
                    text = "${playlistSongs.size} songs",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Control Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                // Play Button
                Button(
                    onClick = {
                        if (isCurrentPlaylist && isPlaying) {
                            // If this playlist is currently playing, toggle pause
                            playerViewModel.togglePlayPause()
                        } else {
                            // Otherwise start playing this playlist
                            playPlaylist()
                        }
                    },
                    modifier = Modifier
                        .width(200.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (playlistId == "liked_songs") 
                            Color(0xFFBB86FC) else Color(0xFF1DB954)
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPlaying && isCurrentPlaylist) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying && isCurrentPlaylist) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPlaying && isCurrentPlaylist) "Pause" else "Play",
                            color = Color.Black,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color(0xFF282828))
        }

        // Songs List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding.dp)
        ) {
                items(playlistSongs) { song ->
                val isLiked = likedSongs.any { it.id == song.id }
                val isSongPlaying = currentSong?.id == song.id && isPlaying
                val isSelected = currentSong?.id == song.id && !isPlaying
                RecentlyPlayedItem(
                    song = song,
                    onClick = { 
                        // Play the song immediately when clicked
                        if (currentSong?.id == song.id && isCurrentPlaylist) {
                            playerViewModel.togglePlayPause()
                        } else {
                        playSongFromPlaylist(song)
                        }
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
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onDetailsClick = { 
                        detailsSong = song
                        showDetailsDialog = true
                    },
                    onRemoveFromPlaylist = if (playlistId != "liked_songs") {
                        {
                            scope.launch {
                                libraryViewModel.removeSongFromPlaylist(song.id, playlistId)
                            }
                        }
                    } else null,
                    isPlaying = currentSong?.id == song.id && isPlaying,
                    isSelected = currentSong?.id == song.id && !isPlaying,
                    onPlayPause = {
                        if (currentSong?.id == song.id && isCurrentPlaylist) {
                            playerViewModel.togglePlayPause()
                        } else {
                            playSongFromPlaylist(song)
                        }
                    }
                )
                }
            }
        }
    }

    // Add to Playlist Dialog
    if (showPlaylistDialog && selectedSong != null) {
        // Create a mutable state to track playlist membership changes immediately
        val mutableSelectedPlaylistIds = remember(selectedSong) {
            val ids = mutableSetOf<String>()
            // Safely capture the selectedSong to a local variable to avoid smart cast issues
            val song = selectedSong
            // Add custom playlist IDs
            if (song != null) {
                ids.addAll(song.getPlaylistIdsList())
                // Add liked songs if applicable
                if (song.isLiked) ids.add("liked_songs")
            }
            ids
        }
        
        PlaylistSelectionDialog(
            onDismiss = { 
                showPlaylistDialog = false
                selectedSong = null
            },
            onPlaylistSelected = { selectedPlaylist ->
                scope.launch {
                    // Safely capture the non-null song to avoid smart cast issues
                    val song = selectedSong ?: return@launch
                    
                    if (selectedPlaylist.id == "liked_songs") {
                        val isLiked = song.isLiked
                        if (isLiked) {
                            // Update UI state immediately
                            mutableSelectedPlaylistIds.remove("liked_songs")
                            // Then update database
                            libraryViewModel.removeSongFromLiked(song.id)
                        } else {
                            // Update UI state immediately
                            mutableSelectedPlaylistIds.add("liked_songs")
                            // Then update database
                            libraryViewModel.addSongToLiked(song)
                        }
                    } else {
                        val isInPlaylist = song.isInPlaylist(selectedPlaylist.id)
                        if (isInPlaylist) {
                            // Update UI state immediately
                            mutableSelectedPlaylistIds.remove(selectedPlaylist.id)
                            // Then update database
                            libraryViewModel.removeSongFromPlaylist(song.id, selectedPlaylist.id)
                        } else {
                            // Update UI state immediately
                            mutableSelectedPlaylistIds.add(selectedPlaylist.id)
                            // Then update database
                            libraryViewModel.addSongToPlaylist(song, selectedPlaylist.id)
                        }
                    }
                }
            },
            onCreateNewPlaylist = { name ->
                scope.launch {
                    // Safely capture the non-null song to avoid smart cast issues
                    val song = selectedSong ?: return@launch
                    
                    libraryViewModel.createPlaylist(name)
                    // Wait for the playlist to be created and get its ID
                    playlists.firstOrNull { it.name == name }?.let { newPlaylist ->
                        // Update UI state immediately
                        mutableSelectedPlaylistIds.add(newPlaylist.id)
                        // Then update database
                        libraryViewModel.addSongToPlaylist(song, newPlaylist.id)
                    }
                }
            },
            onRenamePlaylist = { selectedPlaylist, newName ->
                scope.launch {
                    libraryViewModel.renamePlaylist(selectedPlaylist.id, newName)
                }
            },
            playlists = playlists,
            selectedPlaylistIds = mutableSelectedPlaylistIds
        )
    }

    // Create Playlist Dialog
    if (showCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            scope.launch {
                                libraryViewModel.createPlaylist(playlistName)
                                showCreateDialog = false
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Playlist Dialog
    if (showRenameDialog) {
        var newName by remember { mutableStateOf(currentPlaylist?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            scope.launch {
                                libraryViewModel.renamePlaylist(playlistId, newName)
                                showRenameDialog = false
                            }
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Playlist Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete this playlist?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            libraryViewModel.deletePlaylist(playlistId)
                            showDeleteDialog = false
                            onNavigateBack()
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text("Close")
                }
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