package com.nova.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nova.music.data.model.Playlist
import com.nova.music.data.model.Song

@Composable
fun AddToPlaylistDialog(
    onDismiss: () -> Unit,
    onCreateNewPlaylist: () -> Unit,
    onPlaylistsSelected: (Set<String>) -> Unit,
    onLikeToggle: (Boolean) -> Unit,
    playlists: List<Playlist>,
    song: Song,
    songInPlaylists: Set<String>,
    isLiked: Boolean
) {
    var selectedPlaylists by remember(songInPlaylists) { mutableStateOf(songInPlaylists) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = "Add to Playlists",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                // Create New Playlist Button
                TextButton(
                    onClick = onCreateNewPlaylist,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Playlist",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create New Playlist")
                    }
                }

                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )

                // Liked Songs Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLikeToggle(!isLiked) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Liked Songs",
                            tint = if (isLiked) Color(0xFFBB86FC) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Liked Songs",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    if (isLiked) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color(0xFFBB86FC)
                        )
                    }
                }

                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )

                // Playlist List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(playlists) { playlist ->
                        val isSelected = playlist.id in selectedPlaylists
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    selectedPlaylists = if (isSelected) {
                                        selectedPlaylists - playlist.id
                                    } else {
                                        selectedPlaylists + playlist.id
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onPlaylistsSelected(selectedPlaylists)
                            onDismiss()
                        }
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
} 