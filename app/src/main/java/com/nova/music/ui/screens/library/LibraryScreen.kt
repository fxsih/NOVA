package com.nova.music.ui.screens.library

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nova.music.data.model.Playlist
import com.nova.music.data.model.Song
import com.nova.music.ui.viewmodels.LibraryViewModel
import com.nova.music.ui.viewmodels.PlayerViewModel
import com.nova.music.ui.util.rememberDynamicBottomPadding

@Composable
fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = "${playlist.songs.size} songs",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = Color.White
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        onRename()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Rename playlist"
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        onDelete()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete playlist"
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToPlayer: (String) -> Unit
) {
    val likedSongs by viewModel.likedSongs.collectAsState(initial = emptyList())
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val playlistSongCounts by viewModel.playlistSongCounts.collectAsState(initial = emptyMap())
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }
    var isShuffleEnabled by remember { mutableStateOf(false) }
    
    val bottomPadding by rememberDynamicBottomPadding()
    
    // Use the player's actual playing state
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val currentPlaylistId by playerViewModel.currentPlaylistId.collectAsState()
    
    // Function to play a song from a specific playlist
    fun playSongFromPlaylist(song: Song, playlistId: String, songs: List<Song>) {
        playerViewModel.loadSong(song = song, playlistId = playlistId, playlistSongs = songs)
        onNavigateToPlayer(song.id)
    }
    
    // Create a simpler way to check if a song is in a playlist
    fun isSongInLikedSongs(songId: String?): Boolean {
        return songId != null && likedSongs.any { it.id == songId }
    }
    
    // Check if the current song is from the liked songs playlist
    val isCurrentSongFromLikedPlaylist by remember(currentSong?.id, likedSongs) {
        derivedStateOf {
            val result = currentSong?.id?.let { songId ->
                val isLiked = isSongInLikedSongs(songId)
                Log.d("LibraryScreen", "Liked Songs: Contains current song ${currentSong?.title}: $isLiked")
                isLiked
            } ?: false
            result
        }
    }
    
    // Map to track if current song is in each playlist (computed once per composition)
    val playlistContainsCurrentSong = remember(currentSong, playlists) {
        playlists.associateWith { playlist ->
            false // We'll check this when needed in the UI
        }.toMutableMap()
    }

    Scaffold(
        topBar = {
        TopAppBar(
                title = { Text("Your Library") },
            colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF000000),
                    titleContentColor = Color.White
            )
        )
        },
        containerColor = Color(0xFF000000)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF000000))
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding.dp)
        ) {
            // Liked Songs Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                            .padding(16.dp)
                            .clickable { onNavigateToPlaylist("liked_songs") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2D1B69) // Darker purple
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .background(
                                            color = Color(0xFFBB86FC),
                                            shape = RoundedCornerShape(16.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Liked Songs",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "${likedSongs.size} songs",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFFE1E1E1)
                                )
                        }

                        // Play Button
                        IconButton(
                                onClick = { 
                                    likedSongs.firstOrNull()?.let { song ->
                                        if (currentPlaylistId == "liked_songs") {
                                            playerViewModel.togglePlayPause()
                                        } else {
                                            playSongFromPlaylist(song, "liked_songs", likedSongs)
                                        }
                                    }
                                },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .size(56.dp)
                                    .background(
                                        color = Color(0xFFBB86FC),
                                        shape = CircleShape
                                    )
                        ) {
                            // Use a key to force recomposition only when these values change
                            key(isPlaying, currentSong?.id) {
                                Icon(
                                    imageVector = if (isPlaying && currentPlaylistId == "liked_songs") 
                                        Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying && currentPlaylistId == "liked_songs") 
                                        "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }

                // Create Playlist Card
            item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp)
                            .clickable { showCreateDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1A472A) // Darker green
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                ) {
                            Text(
                                text = "Create New Playlist",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        color = Color(0xFF1DB954),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Create Playlist",
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                }
            }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Custom Playlist Cards
            items(playlists) { playlist ->
                    val playlistSongs by viewModel.getPlaylistSongs(playlist.id).collectAsState(initial = emptyList())
                    
                    // Log playlist songs for debugging
                    LaunchedEffect(playlist.id) {
                        Log.d("LibraryScreen", "Playlist ${playlist.name} has ${playlistSongs.size} songs")
                        playlistSongs.forEach { song ->
                            Log.d("LibraryScreen", "  - Song: ${song.title} (${song.id})")
                        }
                    }
                    
                    val playlistColor = remember(playlist.id) {
                        val colors = listOf(
                            Color(0xFF1DB954) to Color(0xFF0D4D2B), // Spotify Green
                            Color(0xFFE91E63) to Color(0xFF8B1044), // Pink
                            Color(0xFF9C27B0) to Color(0xFF5E1669), // Purple
                            Color(0xFF2196F3) to Color(0xFF145A92), // Blue
                            Color(0xFF00BCD4) to Color(0xFF007280), // Cyan
                            Color(0xFF4CAF50) to Color(0xFF2E6B30), // Green
                            Color(0xFFFFC107) to Color(0xFF997300), // Amber
                            Color(0xFFFF9800) to Color(0xFF995C00), // Orange
                            Color(0xFFF44336) to Color(0xFF932820), // Red
                            Color(0xFF795548) to Color(0xFF49332A)  // Brown
                        )
                        colors[playlist.id.hashCode().absoluteValue % colors.size]
                    }
                    
                    // Check if current song is from this playlist
                    val isCurrentPlaylistActive = currentPlaylistId == playlist.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .padding(16.dp)
                            .clickable { onNavigateToPlaylist(playlist.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = playlistColor.second // Darker variant
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .background(
                                            color = playlistColor.first, // Original color
                                            shape = RoundedCornerShape(16.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "${playlistSongCounts[playlist.id] ?: 0} songs",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFFE1E1E1)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                            ) {
                                // Play Button
                                IconButton(
                                    onClick = {
                                        playlistSongs.firstOrNull()?.let { song ->
                                            if (isCurrentPlaylistActive) {
                                                playerViewModel.togglePlayPause()
                                            } else {
                                                playSongFromPlaylist(song, playlist.id, playlistSongs)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            color = playlistColor.first, // Original color
                                            shape = CircleShape
                                        )
                                ) {
                                    // Use a key to force recomposition only when these values change
                                    key(isPlaying, currentSong?.id) {
                                        Icon(
                                            imageVector = if (isPlaying && isCurrentPlaylistActive) 
                                                Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying && isCurrentPlaylistActive) 
                                                "Pause" else "Play",
                                            tint = Color.Black,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Menu Button
                                Box {
                                    IconButton(
                                        onClick = { 
                                            selectedPlaylist = playlist
                                            showMenu = true
                                        },
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(
                                                color = playlistColor.first, // Match the play button color
                                                shape = CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More options",
                                            tint = Color.Black,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showMenu && selectedPlaylist?.id == playlist.id,
                                        onDismissRequest = { showMenu = false },
                                        modifier = Modifier
                                            .background(Color(0xFF282828))
                                            .width(200.dp)
                                    ) {
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    "Rename Playlist",
                                                    color = Color.White
                                                )
                                            },
                                            onClick = {
                                                showRenameDialog = true
                                                showMenu = false
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = null,
                                                    tint = Color.White
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    "Delete Playlist",
                                                    color = Color.White
                                                )
                                            },
                                            onClick = {
                                                showDeleteDialog = true
                                                showMenu = false
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = Color.White
                                                )
                                            }
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

    // Create Playlist Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName)
                            newPlaylistName = ""
                            showCreateDialog = false
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
    if (showRenameDialog && selectedPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("New Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            selectedPlaylist?.let { playlist ->
                                viewModel.renamePlaylist(playlist.id, newPlaylistName)
                            }
                            newPlaylistName = ""
                            showRenameDialog = false
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
    if (showDeleteDialog && selectedPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete this playlist?") },
            confirmButton = {
                TextButton(
                    onClick = { 
                        selectedPlaylist?.let { playlist ->
                            viewModel.deletePlaylist(playlist.id)
                        }
                        showDeleteDialog = false
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
} 

@Composable
fun PlaylistCard(
    playlist: Playlist,
    songCount: Int,
    onClick: () -> Unit,
    onMenuClick: (() -> Unit)? = null,
    onPlayClick: () -> Unit,
    isPlaying: Boolean = false,
    isCurrentPlaylist: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = Color(0xFF282828)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = if (playlist.id == "liked_songs") Color(0xFFBB86FC) else Color(0xFF1DB954),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playlist.id == "liked_songs") Icons.Default.Favorite else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "$songCount songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            Row {
                IconButton(onClick = onPlayClick) {
                    key(isPlaying) {
                        Icon(
                            imageVector = if (isCurrentPlaylist && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isCurrentPlaylist && isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }
                }

                if (onMenuClick != null) {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

private val Int.absoluteValue: Int
    get() = if (this < 0) -this else this 