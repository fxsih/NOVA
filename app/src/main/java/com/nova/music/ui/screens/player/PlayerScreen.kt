package com.nova.music.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nova.music.ui.viewmodels.PlayerViewModel
import com.nova.music.ui.viewmodels.LibraryViewModel
import com.nova.music.ui.viewmodels.RepeatMode
import java.util.concurrent.TimeUnit
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import com.nova.music.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    songId: String?,
    viewModel: PlayerViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    // Only load the song if songId is not null (when directly navigating to player)
    // If songId is null, we're coming from the mini player and should use the current song
    LaunchedEffect(songId) {
        if (songId != null) {
            viewModel.loadSong(songId)
        }
    }
    
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val likedSongs by libraryViewModel.likedSongs.collectAsState()

    val isLiked = currentSong?.let { song -> likedSongs.any { it.id == song.id } } ?: false

    // Get screen height to calculate better spacing
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val topSpacing = screenHeight * 0.05f // 5% of screen height for top spacing

    // Colors
    val backgroundColor = Color(0xFF000000) // AMOLED black background for consistency
    val accentColor = Color.White
    val secondaryTextColor = Color(0xFFD3E0C8)
    val progressColor = Color.White
    val progressInactiveColor = Color.White.copy(alpha = 0.2f)
    val sliderThumbColor = Color.White
    val controlIconColor = Color.White

    if (currentSong == null) {
        onNavigateBack()
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .systemBarsPadding(), // Add system bars padding for better edge-to-edge experience
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Add top spacing to prevent feeling constrained
            Spacer(modifier = Modifier.height(topSpacing))
            
            // Minimize button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Minimize Player",
                        tint = Color.White
                    )
                }
            }
            
            // Album Art with improved sizing
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f) // Make album art slightly smaller than full width
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp)) // Larger corner radius
                    .background(Color(0xFF1E1E1E)), // Slightly lighter background for album art container
                contentAlignment = Alignment.Center
            ) {
            AsyncImage(
                model = when {
                    !currentSong?.albumArtUrl.isNullOrBlank() -> currentSong?.albumArtUrl
                    !currentSong?.albumArt.isNullOrBlank() -> currentSong?.albumArt
                    else -> R.drawable.default_album_art
                },
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = painterResource(id = R.drawable.default_album_art),
                placeholder = painterResource(id = R.drawable.default_album_art)
            )
            }
            
            Spacer(modifier = Modifier.height(32.dp)) // Increased spacing
            
            // Song Info and Like Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth(0.85f) // Match album art width
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentSong?.title ?: "",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentSong?.artist ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        if (currentSong != null) {
                            if (isLiked) libraryViewModel.removeSongFromLiked(currentSong!!.id)
                            else libraryViewModel.addSongToLiked(currentSong!!)
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) Color.Red else accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Progress bar & times
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f) // Match album art width
                    .padding(horizontal = 8.dp)
            ) {
                Slider(
                    value = progress.coerceIn(0f, 1f),
                    onValueChange = { value -> viewModel.seekTo(value) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = sliderThumbColor,
                        activeTrackColor = progressColor,
                        inactiveTrackColor = progressInactiveColor
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration((progress * duration).toLong()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor
                    )
                    Text(
                        text = "-${formatDuration((duration - (progress * duration).toLong()).toLong())}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp)) // Increased spacing
            
            // Playback Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.85f) // Match album art width
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) accentColor else controlIconColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.skipToPrevious() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = controlIconColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(72.dp) // Larger play button
                        .background(accentColor, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = backgroundColor, // Use background color for icon to create contrast
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.skipToNext() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = controlIconColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.toggleRepeatMode() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = when (repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            RepeatMode.ALL -> Icons.Default.Repeat
                            else -> Icons.Outlined.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode != RepeatMode.NONE) accentColor else controlIconColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // Add bottom spacing for better balance
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return "%d:%02d".format(minutes, seconds)
}
