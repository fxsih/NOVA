package com.nova.music.ui.screens.library

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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nova.music.data.model.Playlist
import com.nova.music.ui.components.RecentlyPlayedItem
import com.nova.music.ui.viewmodels.LibraryViewModel
import kotlinx.coroutines.launch

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
    onPlaylistClick: (String) -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = { 
                Text(
                    text = "Library",
                    color = Color.White
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Liked Songs Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clickable { onPlaylistClick("liked_songs") },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF282828)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Top section with icon and text
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Playlist Icon/Cover
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF383838))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Liked Songs",
                                    tint = Color(0xFFBB86FC),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .align(Alignment.Center)
                                )
                            }

                            // Playlist Info
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Liked Songs",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${likedSongs.size} songs",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                        }

                        // Play Button
                        IconButton(
                            onClick = { onPlaylistClick("liked_songs") },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(48.dp)
                                .background(Color(0xFFBB86FC), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Liked Songs",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create New Playlist")
                }
            }

            items(playlists) { playlist ->
                PlaylistItem(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist.id) },
                    onDelete = { viewModel.deletePlaylist(playlist.id) },
                    onRename = { selectedPlaylist = playlist; showRenameDialog = true }
                )
            }
        }
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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            viewModel.createPlaylist(playlistName)
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
        var playlistName by remember { mutableStateOf(selectedPlaylist!!.name) }
        AlertDialog(
            onDismissRequest = { 
                showRenameDialog = false
                selectedPlaylist = null
            },
            title = { Text("Rename Playlist") },
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
                            viewModel.renamePlaylist(selectedPlaylist!!.id, playlistName)
                            showRenameDialog = false
                            selectedPlaylist = null
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showRenameDialog = false
                        selectedPlaylist = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
} 