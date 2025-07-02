package com.nova.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nova.music.R
import com.nova.music.data.model.Song
import com.nova.music.util.CenterCropSquareTransformation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyPlayedItem(
    song: Song,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    isLiked: Boolean,
    modifier: Modifier = Modifier,
    onDetailsClick: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    onPlayPause: () -> Unit = {},
    isSelected: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    
    // Define the purple color for the playing state - using Color(0xFF9C27B0) for consistency
    val playingBackgroundColor = Color(0xFF9C27B0).copy(alpha = 0.2f)
    
    // Show visual cue if the song is playing OR selected (in mini player)
    val shouldShowVisualCue = isPlaying || isSelected

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (shouldShowVisualCue) playingBackgroundColor else Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art
                val imageModel = when {
                    !song.albumArtUrl.isNullOrBlank() -> song.albumArtUrl
                    !song.albumArt.isBlank() -> song.albumArt
                    else -> R.drawable.default_album_art
                }
                
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageModel)
                        .crossfade(true)
                        .transformations(CenterCropSquareTransformation())
                        .build(),
                    contentDescription = "Song artwork",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = R.drawable.default_album_art),
                    placeholder = painterResource(id = R.drawable.default_album_art)
                )
                
                // Song Info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play/Pause Button
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Like Button
                    IconButton(
                        onClick = onLikeClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isLiked) "Unlike" else "Like",
                            tint = if (isLiked) Color.Red else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Menu Button
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Dropdown Menu
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color(0xFF282828))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = if (isLiked) "Unlike" else "Like",
                                            tint = if (isLiked) Color.Red else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = if (isLiked) "Remove from Liked" else "Add to Liked",
                                            color = Color.White
                                        )
                                    }
                                },
                                onClick = {
                                    onLikeClick()
                                    showMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.PlaylistAdd,
                                            contentDescription = "Add to Playlist",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Add to Playlist",
                                            color = Color.White
                                        )
                                    }
                                },
                                onClick = {
                                    onAddToPlaylist()
                                    showMenu = false
                                }
                            )
                            
                            // Details Option
                            if (onDetailsClick != null) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "Details",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Details",
                                                color = Color.White
                                            )
                                        }
                                    },
                                    onClick = {
                                        onDetailsClick()
                                        showMenu = false
                                    }
                                )
                            }
                            
                            // Remove from Playlist Option
                            if (onRemoveFromPlaylist != null) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove from Playlist",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Remove from Playlist",
                                                color = Color.White
                                            )
                                        }
                                    },
                                    onClick = {
                                        onRemoveFromPlaylist()
                                        showMenu = false
                                    }
                                )
                            }
                        } // End DropdownMenu
                    } // End Box for menu
                } // End Row for controls
            } // End Row for main content
        } // End Box
    } // End Card
} // End function

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
} 