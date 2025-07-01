package com.nova.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nova.music.ui.viewmodels.PlayerViewModel
import com.nova.music.ui.viewmodels.LibraryViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import com.nova.music.R
import coil.request.ImageRequest
import com.nova.music.util.CenterCropSquareTransformation

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
    
    var offsetX by remember { mutableStateOf(0f) }
    val dismissThreshold = with(LocalDensity.current) { 100.dp.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val animatedOffset = remember { Animatable(0f) }
    
    // Get actual screen width using WindowManager
    val context = LocalContext.current
    val windowManager = remember { context.getSystemService(WindowManager::class.java) }
    val screenWidth = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.width
        }.toFloat()
    }

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

    LaunchedEffect(offsetX) {
        if (offsetX.absoluteValue >= dismissThreshold) {
            // Stop playback and clear current song
            viewModel.stopPlayback()
            viewModel.clearCurrentSong()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(animatedOffset.value.roundToInt(), 0) }
            .clickable(onClick = onTap)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    coroutineScope.launch {
                        // Limit the drag to screen width
                        val newValue = (animatedOffset.value + delta).coerceIn(-screenWidth, screenWidth)
                        animatedOffset.snapTo(newValue)
                    }
                },
                onDragStarted = { },
                onDragStopped = {
                    coroutineScope.launch {
                        if (animatedOffset.value.absoluteValue < dismissThreshold) {
                            // Snap back if not dragged far enough
                            animatedOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        } else {
                            // Dismiss to the edge of the screen
                            val targetValue = if (animatedOffset.value > 0) {
                                screenWidth
                            } else {
                                -screenWidth
                            }
                            animatedOffset.animateTo(
                                targetValue = targetValue,
                                animationSpec = tween(
                                    durationMillis = 300,
                                    easing = FastOutSlowInEasing
                                )
                            )
                            viewModel.stopPlayback()
                            viewModel.clearCurrentSong()
                        }
                    }
                }
            ),
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
                    .size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(when {
                            !currentSong?.albumArtUrl.isNullOrBlank() -> currentSong?.albumArtUrl
                            !currentSong?.albumArt.isNullOrBlank() -> currentSong?.albumArt
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