package com.nova.music.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nova.music.data.model.Playlist

private val Add = materialIcon(name = "Filled.Add") {
    materialPath {
        moveTo(19.0f, 13.0f)
        horizontalLineTo(13.0f)
        verticalLineTo(19.0f)
        horizontalLineTo(11.0f)
        verticalLineTo(13.0f)
        horizontalLineTo(5.0f)
        verticalLineTo(11.0f)
        horizontalLineTo(11.0f)
        verticalLineTo(5.0f)
        horizontalLineTo(13.0f)
        verticalLineTo(11.0f)
        horizontalLineTo(19.0f)
        verticalLineTo(13.0f)
        close()
    }
}

@Composable
fun PlaylistSelectionDialog(
    onDismiss: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onCreateNewPlaylist: () -> Unit,
    playlists: List<Playlist>
) {
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
                    text = "Add to Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Create New Playlist Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCreateNewPlaylist)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Add,
                        contentDescription = "Create New Playlist",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Create New Playlist",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Existing Playlists
                LazyColumn(
                    modifier = Modifier.weight(1f, false),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(playlists) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlaylistSelected(playlist) }
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Column {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "${playlist.songs.size} songs",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Cancel Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 24.dp, top = 8.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
} 