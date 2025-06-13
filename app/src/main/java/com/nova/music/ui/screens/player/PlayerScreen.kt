package com.nova.music.ui.screens.player

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nova.music.ui.viewmodels.HomeViewModel
import com.nova.music.ui.viewmodels.LibraryViewModel
import com.nova.music.ui.components.PlaylistSelectionDialog
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.os.Build
import android.view.WindowManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    songId: String,
    viewModel: HomeViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val songs by viewModel.songs.collectAsState()
    val likedSongs by libraryViewModel.likedSongs.collectAsState()
    val playlists by libraryViewModel.playlists.collectAsState()
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isShuffleOn by remember { mutableStateOf(false) }
    var isRepeatOn by remember { mutableStateOf(false) }

    val song = songs.find { it.id == songId }
    val isLiked = likedSongs.any { it.id == songId }

    // Hide system bars
    val activity = LocalContext.current as Activity
    DisposableEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.setDecorFitsSystemWindows(false)
            val controller = activity.window.insetsController
            controller?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )
        }
        
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.setDecorFitsSystemWindows(true)
                val controller = activity.window.insetsController
                controller?.let {
                    it.show(WindowInsets.Type.systemBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_BARS_BY_TOUCH
                }
            } else {
                @Suppress("DEPRECATION")
                activity.window.clearFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
                )
            }
        }
    }

    if (song == null) {
        onNavigateBack()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )

            // Empty box for alignment
            Box(modifier = Modifier.size(48.dp))
        }

        // Album Art
        AsyncImage(
            model = song.albumArt,
            contentDescription = "Album Art",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
        )

        // Song Info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Progress Bar (Mock)
        LinearProgressIndicator(
            progress = 0.3f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = Color(0xFFBB86FC),
            trackColor = Color(0xFF2A2A2A)
        )

        // Time Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "1:23",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = "3:45",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Main Playback Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { isShuffleOn = !isShuffleOn }
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffleOn) Color(0xFFBB86FC) else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(onClick = { /* Skip Previous */ }) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFFBB86FC), RoundedCornerShape(32.dp))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = { /* Skip Next */ }) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = { isRepeatOn = !isRepeatOn }
            ) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = if (isRepeatOn) Color(0xFFBB86FC) else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Additional Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(
                onClick = {
                    if (isLiked) {
                        libraryViewModel.removeSongFromLiked(songId)
                    } else {
                        libraryViewModel.addSongToLiked(song)
                    }
                }
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = if (isLiked) Color(0xFFBB86FC) else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = { showPlaylistDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = "Add to Playlist",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Add to Playlist Dialog
    if (showPlaylistDialog) {
        PlaylistSelectionDialog(
            onDismiss = { showPlaylistDialog = false },
            onPlaylistSelected = { playlist ->
                scope.launch {
                    if (playlist.id == "liked_songs") {
                        if (isLiked) {
                            libraryViewModel.removeSongFromLiked(songId)
                        } else {
                            libraryViewModel.addSongToLiked(song)
                        }
                    } else {
                        libraryViewModel.addSongToPlaylist(song, playlist.id)
                    }
                }
            },
            onCreateNewPlaylist = { name ->
                scope.launch {
                    libraryViewModel.createPlaylist(name)
                    // Wait for the playlist to be created and get its ID
                    playlists.firstOrNull { it.name == name }?.let { newPlaylist ->
                        libraryViewModel.addSongToPlaylist(song, newPlaylist.id)
                    }
                }
            },
            onRenamePlaylist = { playlist, newName ->
                scope.launch {
                    libraryViewModel.renamePlaylist(playlist.id, newName)
                }
            },
            playlists = playlists,
            selectedPlaylistIds = buildSet {
                addAll(playlists
                    .filter { playlist -> playlist.songs.any { it.id == songId } }
                    .map { it.id })
                if (isLiked) add("liked_songs")
            }
        )
    }
}