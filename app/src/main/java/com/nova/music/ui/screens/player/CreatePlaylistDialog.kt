package com.nova.music.ui.screens.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Create New Playlist",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { 
                        playlistName = it
                        isError = false
                    },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Please enter a playlist name") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (playlistName.isBlank()) {
                                isError = true
                            } else {
                                onCreatePlaylist(playlistName)
                                onDismiss()
                            }
                        }
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
} 