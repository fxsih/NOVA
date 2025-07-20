package com.nova.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.nova.music.data.model.Playlist
import com.nova.music.ui.viewmodels.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSelectionDialog(
    onDismiss: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onCreateNewPlaylist: (String) -> Unit,
    onRenamePlaylist: (Playlist, String) -> Unit,
    playlists: List<Playlist>,
    selectedPlaylistIds: Set<String>,
    songId: String? = null,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<Playlist?>(null) }
    
    // Get all playlist memberships for the song in a single flow
    val songPlaylistMemberships by if (songId != null) {
        viewModel.getSongPlaylistMemberships(songId).collectAsState(initial = emptySet())
    } else {
        remember { mutableStateOf(emptySet<String>()) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Add to Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Liked Songs Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            onPlaylistSelected(Playlist("liked_songs", "Liked Songs"))
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Liked Songs",
                            tint = Color(0xFFBB86FC),
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(
                                text = "Liked Songs",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White
                            )
                            val likedSongsCount by viewModel.likedSongs.collectAsState()
                            Text(
                                text = "${likedSongsCount.size} songs",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                    if ("liked_songs" in selectedPlaylistIds) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color(0xFFBB86FC), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Create New Playlist Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateDialog = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create New Playlist",
                        tint = Color(0xFFBB86FC),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Create New Playlist",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFBB86FC),
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                Divider(
                    color = Color(0xFF2A2A2A),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Playlist List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        val songCount by viewModel.getPlaylistSongCount(playlist.id).collectAsState(initial = 0)
                        
                        // Check if the current song is in this playlist using the efficient approach
                        val isSongInPlaylist = when {
                            songId != null && playlist.id != "liked_songs" && playlist.id != "downloads" -> {
                                // Use the single flow approach to prevent flickering
                                songPlaylistMemberships.contains(playlist.id)
                            }
                            playlist.id == "liked_songs" -> {
                                selectedPlaylistIds.contains("liked_songs")
                            }
                            else -> {
                                false
                            }
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    onPlaylistSelected(playlist)
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = "$songCount songs",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                            if (isSongInPlaylist) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(0xFFBB86FC), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel",
                            color = Color(0xFFBB86FC)
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Done",
                            color = Color(0xFFBB86FC)
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = "Create New Playlist",
            initialName = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreateNewPlaylist(name)
                showCreateDialog = false
            }
        )
    }

    showRenameDialog?.let { playlist ->
        PlaylistNameDialog(
            title = "Rename Playlist",
            initialName = playlist.name,
            onDismiss = { showRenameDialog = null },
            onConfirm = { newName ->
                onRenamePlaylist(playlist, newName)
                showRenameDialog = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White,
                        cursorColor = Color(0xFFBB86FC),
                        focusedBorderColor = Color(0xFFBB86FC),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFFBB86FC),
                        unfocusedLabelColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel",
                            color = Color(0xFFBB86FC)
                        )
                    }
                    TextButton(
                        onClick = { onConfirm(name.trim()) },
                        enabled = name.trim().isNotEmpty()
                    ) {
                        Text(
                            text = "OK",
                            color = Color(0xFFBB86FC)
                        )
                    }
                }
            }
        }
    }
} 