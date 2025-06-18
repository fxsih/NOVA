package com.nova.music.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nova.music.ui.viewmodels.PlayerViewModel
import com.nova.music.ui.viewmodels.LibraryViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import android.os.Build
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import kotlin.math.absoluteValue
import androidx.compose.foundation.border

@Composable
fun MiniPlayerBar(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel()
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val likedSongs by libraryViewModel.likedSongs.collectAsState()
    val isLiked = currentSong?.let { song -> likedSongs.any { it.id == song.id } } ?: false

    if (currentSong == null) return

    val albumArtColor = remember(currentSong?.id) {
        val colors = listOf(
            Color(0xFF1DB954), // Spotify Green
            Color(0xFFE91E63), // Pink
            Color(0xFF9C27B0), // Purple
            Color(0xFF2196F3), // Blue
            Color(0xFF00BCD4), // Cyan
            Color(0xFF4CAF50), // Green
            Color(0xFFFFC107), // Amber
            Color(0xFFFF9800), // Orange
            Color(0xFFF44336), // Red
            Color(0xFF795548)  // Brown
        )
        colors[currentSong?.id?.hashCode()?.absoluteValue?.rem(colors.size) ?: 0]
    }

    Surface(
        modifier = modifier
            .clickable(onClick = onTap),
        color = Color(0xFF282828),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art with colored background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(albumArtColor),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = currentSong?.albumArt,
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
            )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Song Info without Marquee
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = currentSong?.title ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = currentSong?.artist ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { 
                        if (isLiked) {
                            libraryViewModel.removeSongFromLiked(currentSong?.id ?: "")
                        } else {
                            currentSong?.let { libraryViewModel.addSongToLiked(it) }
                        }
                    }
                ) {
                Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isLiked) "Remove from Liked" else "Add to Liked",
                        tint = if (isLiked) Color.Red else Color.White
                )
            }
            IconButton(onClick = { viewModel.togglePlayPause() }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }
            IconButton(onClick = { viewModel.skipToNext() }) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White
                )
                }
            }
        }
    }
} 