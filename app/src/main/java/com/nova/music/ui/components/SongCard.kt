package com.nova.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nova.music.R
import com.nova.music.data.model.Song
import com.nova.music.util.CenterCropSquareTransformation
import kotlin.math.absoluteValue

@Composable
fun SongCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onPlayPause: () -> Unit = {},
    onLike: () -> Unit = {},
    isLiked: Boolean = false,
    isSelected: Boolean = false
) {
    val albumArtColor = remember(song.id) {
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
        colors[song.id.hashCode().absoluteValue.rem(colors.size)]
    }
    
    // Define the purple color for the playing state - using Color(0xFF9C27B0) for consistency
    val playingBackgroundColor = Color(0xFF9C27B0).copy(alpha = 0.2f)
    
    // Show visual cue if the song is playing OR selected (in mini player)
    val shouldShowVisualCue = isPlaying || isSelected

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (shouldShowVisualCue) playingBackgroundColor else Color.Transparent)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album Art with colored background
        Box(
            modifier = Modifier
                .size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(when {
                        !song.albumArtUrl.isNullOrBlank() -> song.albumArtUrl
                        !song.albumArt.isBlank() -> song.albumArt
                        else -> R.drawable.default_album_art
                    })
                    .crossfade(true)
                    .transformations(CenterCropSquareTransformation())
                    .build(),
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                error = painterResource(id = R.drawable.default_album_art),
                placeholder = painterResource(id = R.drawable.default_album_art)
            )
            
            // Removed Play/Pause overlay when playing
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Song Info with Marquee
        Column(
            modifier = Modifier.weight(1f)
        ) {
            MarqueeText(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            MarqueeText(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onLike,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isLiked) "Remove from Liked" else "Add to Liked",
                    tint = if (isLiked) Color(0xFF1DB954) else Color.White
                )
            }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }
        }
    }
} 