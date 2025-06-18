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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    songId: String,
    viewModel: PlayerViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val likedSongs by libraryViewModel.likedSongs.collectAsState()

    val isLiked = currentSong?.let { song -> likedSongs.any { it.id == song.id } } ?: false

    // Colors (use your theme or adjust as needed)
    val backgroundColor = Color(0xFF192040) // Deep navy
    val cardColor = MaterialTheme.colorScheme.surface // Use your theme's surface color
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
            .background(cardColor)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 160.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Minimize button
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Minimize Player",
                        tint = Color.White
                    )
                }
            }
            // Album Art
            AsyncImage(
                model = currentSong?.albumArt,
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(secondaryTextColor)
            )
            Spacer(modifier = Modifier.height(16.dp)) // Reduced height
            // Song Info and Like Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentSong?.title ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong?.artist ?: "",
                        style = MaterialTheme.typography.bodySmall,
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
                    }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) Color.Red else accentColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Progress bar & times
            Column {
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
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
                    )
                    Text(
                        text = "-${formatDuration(duration - (progress * duration).toLong())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Playback Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) accentColor else controlIconColor.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = { viewModel.skipToPrevious() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = controlIconColor
                    )
                }
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(accentColor, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = cardColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { viewModel.skipToNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = controlIconColor
                    )
                }
                IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                    Icon(
                        imageVector = when (repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            RepeatMode.ALL -> Icons.Default.Repeat
                            else -> Icons.Outlined.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode != RepeatMode.OFF) accentColor else controlIconColor.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return "%d:%02d".format(minutes, seconds)
}
