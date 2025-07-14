package com.nova.music.ui.screens.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nova.music.R
import com.nova.music.data.model.Song
import com.nova.music.ui.components.SongItem
import com.nova.music.ui.viewmodels.PlayerViewModel
import com.nova.music.ui.viewmodels.LibraryViewModel
import com.nova.music.ui.viewmodels.RepeatMode
import com.nova.music.ui.viewmodels.SleepTimerOption
import com.nova.music.util.CenterCropSquareTransformation
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.CheckCircle

// Data class to track dragging state
data class DragInfo(
    val isDragging: Boolean = false,
    val draggedItemIndex: Int = -1,
    val draggedOffset: Offset = Offset.Zero,
    val draggedItem: Song? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
        // Set the flag to indicate we're in the player screen
        viewModel.setInPlayerScreen(true)
    }

    // Get context for download operations
    val context = LocalContext.current

    // Update download state whenever the screen is shown
    LaunchedEffect(Unit) {
        // Verify the current song's download state
        viewModel.updateCurrentSongDownloadState(context)
    }
    
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val likedSongs by libraryViewModel.likedSongs.collectAsState()
    val currentPlaylistId by viewModel.currentPlaylistId.collectAsState()
    val currentPlaylistSongs by viewModel.currentPlaylistSongs.collectAsState()
    val queueSongs = viewModel.getCurrentPlaylistSongs()
    
    // State for the queue bottom sheet
    val sheetState = rememberModalBottomSheetState()
    var showQueueSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Debug logging
    LaunchedEffect(currentPlaylistSongs, currentSong) {
        println("DEBUG: Current playlist ID: $currentPlaylistId")
        println("DEBUG: Current playlist songs count: ${currentPlaylistSongs.size}")
        println("DEBUG: Current song: ${currentSong?.title}")
    }
    
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

    // Layout stabilization - pre-calculate dimensions
    val density = LocalDensity.current
    val albumArtSize = with(density) { (screenHeight * 0.4f).roundToPx() }
    val controlsHeight = with(density) { (screenHeight * 0.25f).roundToPx() }
    
    // Handle case where currentSong is null
    if (currentSong == null) {
        onNavigateBack()
        return
    }

    // Main player screen
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
            
            // Top bar with minimize button and queue button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Minimize Player",
                        tint = Color.White
                    )
                }
            }
            
            // Album Art with improved sizing and layout stabilization
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f) // Make album art slightly smaller than full width
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp)) // Larger corner radius
                    .background(Color(0xFF1E1E1E)), // Slightly lighter background for album art container
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
                        val songToLike = currentSong
                        if (songToLike != null) {
                            if (isLiked) libraryViewModel.removeSongFromLiked(songToLike.id)
                            else libraryViewModel.addSongToLiked(songToLike)
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
                // Get context outside the lambda
                val context = LocalContext.current
                
                IconButton(
                    onClick = { 
                        // Update download state first
                        viewModel.updateCurrentSongDownloadState(context)
                        
                        // Then verify all downloaded songs
                        viewModel.verifyDownloadedSongs(context)
                        
                        // Finally, download if needed
                        viewModel.downloadCurrentSong(context)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    // Show download icon, progress indicator, or downloaded icon
                    val isDownloading by viewModel.isDownloading.collectAsState()
                    val downloadProgress by viewModel.downloadProgress.collectAsState()
                    val isDownloaded by viewModel.isCurrentSongDownloaded.collectAsState()
                    
                    if (isDownloading) {
                        // Use CircularProgressIndicator with percentage text
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFFBB86FC),
                                trackColor = Color(0xFFBB86FC).copy(alpha = 0.2f),
                                strokeWidth = 2.dp
                            )
                            
                            // Display percentage
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontSize = 8.sp
                            )
                        }
                    } else if (isDownloaded) {
                        // Show downloaded icon with purple tint
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = Color(0xFFBB86FC),
                            modifier = Modifier.size(24.dp)
                            )
                    } else {
                        // Show download icon when not downloading
                    Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Download",
                            tint = controlIconColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                    }
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
                        contentDescription = when (repeatMode) {
                            RepeatMode.NONE -> "No Repeat"
                            RepeatMode.ALL -> "Repeat Infinitely"
                            RepeatMode.ONE -> "Repeat Once"
                        },
                        tint = if (repeatMode != RepeatMode.NONE) Color(0xFFBB86FC) else controlIconColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Queue button
            OutlinedButton(
                onClick = { 
                    showQueueSheet = true 
                },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                    containerColor = Color(0xFF2A2A2A)
                ),
                border = BorderStroke(1.dp, Color(0xFF444444))
            ) {
                Icon(
                    imageVector = Icons.Outlined.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "View Queue",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // Add bottom spacing for better balance
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    
    // Queue bottom sheet
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { 
                showQueueSheet = false
            },
            sheetState = sheetState,
            containerColor = Color(0xFF121212),
            contentColor = Color.White,
            dragHandle = { Spacer(modifier = Modifier.height(1.dp)) },
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            QueueContent(
                queueSongs = queueSongs,
                currentSong = currentSong,
                isPlaying = isPlaying,
                viewModel = viewModel,
                onSongClick = { song ->
                    // Load and play the song directly within the context of the current queue
                    viewModel.loadQueueSongInContext(song)
                    showQueueSheet = false
                },
                onDismissRequest = { 
                    showQueueSheet = false
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueContent(
    queueSongs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    viewModel: PlayerViewModel,
    onSongClick: (Song) -> Unit,
    onDismissRequest: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    
    // State for drag and drop
    var dragState by remember { mutableStateOf(DragInfo()) }
    
    // Use mutableStateListOf for better state management
    val mutableQueue = remember { mutableStateListOf<Song>() }
    
    // Sync the mutable queue with the external queue whenever it changes
    LaunchedEffect(queueSongs) {
        mutableQueue.clear()
        mutableQueue.addAll(queueSongs)
        // Reset drag state when queue changes externally
        dragState = DragInfo()
    }
    
    // Track shuffle state
    val isShuffle by viewModel.isShuffle.collectAsState()
    
    // Apply queue changes immediately when drag ends
    val applyQueueChanges = {
        if (mutableQueue.isNotEmpty()) {
            scope.launch {
                // Apply the queue update
                viewModel.reorderQueue(mutableQueue.toList())
            }
        }
    }
    
    // Function to shuffle the queue
    val shuffleQueue = {
        if (mutableQueue.isNotEmpty()) {
            val currentSongId = currentSong?.id
            
            // Create a shuffled version of the queue
            val shuffledQueue = mutableQueue.toMutableList()
            
            // If there's a current song, move it to the top (index 0)
            if (currentSongId != null) {
                val currentSongItem = shuffledQueue.find { it.id == currentSongId }
                // Remove current song from the list
                shuffledQueue.removeAll { it.id == currentSongId }
                // Shuffle the remaining songs
                shuffledQueue.shuffle()
                // Add current song at the top
                if (currentSongItem != null) {
                    shuffledQueue.add(0, currentSongItem)
                }
            } else {
                // No current song, shuffle everything
                shuffledQueue.shuffle()
            }
            
            // Update the queue
            mutableQueue.clear()
            mutableQueue.addAll(shuffledQueue)
            
            // Apply the changes
            scope.launch {
                viewModel.reorderQueue(mutableQueue.toList())
            }
        }
    }
    
    // Function to restore original queue order
    val restoreOriginalOrder = {
        if (queueSongs.isNotEmpty()) {
            // Use the latest queueSongs as the source of truth
            mutableQueue.clear()
            mutableQueue.addAll(queueSongs)
            
            // Apply the changes
            scope.launch {
                viewModel.reorderQueue(mutableQueue.toList())
            }
        }
    }
    
    // Effect to handle shuffle state changes - only apply when shuffle state actually changes
    var previousShuffleState by remember { mutableStateOf(isShuffle) }
    LaunchedEffect(isShuffle) {
        if (isShuffle != previousShuffleState) {
            if (isShuffle) {
                shuffleQueue()
            } else {
                restoreOriginalOrder()
            }
            previousShuffleState = isShuffle
        }
    }
    
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            .background(Color(0xFF1E0040)) // Purple background color
                    .padding(bottom = 32.dp)
            ) {
                // Queue header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Queue",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Shuffle button
                val isShuffle by viewModel.isShuffle.collectAsState()
                IconButton(
                    onClick = { 
                        // Toggle shuffle mode
                        viewModel.toggleShuffle()
                        
                        // Apply shuffle immediately if enabled
                        if (!isShuffle) {
                            shuffleQueue()
                        } else {
                            restoreOriginalOrder()
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) Color(0xFFBB86FC) else Color.White,
                    )
                }
                
                // Timer button (sleep timer)
                var showTimerDialog by remember { mutableStateOf(false) }
                val sleepTimerActive by viewModel.sleepTimerActive.collectAsState()
                
                IconButton(
                    onClick = { showTimerDialog = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Sleep Timer",
                        tint = if (sleepTimerActive) Color(0xFFBB86FC) else Color.White
                    )
                }
                
                // Close button
                IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Queue",
                            tint = Color.White
                        )
                    }
                
                // Sleep timer dialog
                if (showTimerDialog) {
                    AlertDialog(
                        onDismissRequest = { showTimerDialog = false },
                        title = { Text("Set Sleep Timer") },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val sleepTimerActive by viewModel.sleepTimerActive.collectAsState()
                                val sleepTimerOption by viewModel.sleepTimerOption.collectAsState()
                                
                                if (sleepTimerActive) {
                                    val remainingTime = viewModel.formatSleepTimerRemaining()
                                    Text(
                                        text = "Timer active: $remainingTime remaining",
                                        color = Color(0xFFBB86FC),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    
                                    Button(
                                        onClick = { 
                                            viewModel.cancelSleepTimer()
                                            showTimerDialog = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFBB86FC),
                                            contentColor = Color.Black
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Cancel Timer")
                                    }
                                } else {
                                    TimerOption(
                                        text = "10 minutes",
                                        onClick = { 
                                            viewModel.setSleepTimer(SleepTimerOption.TEN_MINUTES)
                                            showTimerDialog = false
                                        }
                                    )
                                    
                                    TimerOption(
                                        text = "15 minutes",
                                        onClick = { 
                                            viewModel.setSleepTimer(SleepTimerOption.FIFTEEN_MINUTES)
                                            showTimerDialog = false
                                        }
                                    )
                                    
                                    TimerOption(
                                        text = "30 minutes",
                                        onClick = { 
                                            viewModel.setSleepTimer(SleepTimerOption.THIRTY_MINUTES)
                                            showTimerDialog = false
                                        }
                                    )
                                    
                                    TimerOption(
                                        text = "1 hour",
                                        onClick = { 
                                            viewModel.setSleepTimer(SleepTimerOption.ONE_HOUR)
                                            showTimerDialog = false
                                        }
                                    )
                                    
                                    TimerOption(
                                        text = "End of song",
                                        onClick = { 
                                            viewModel.setSleepTimer(SleepTimerOption.END_OF_SONG)
                                            showTimerDialog = false
                                        }
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showTimerDialog = false }) {
                                Text("Close", color = Color.White)
                            }
                        },
                        containerColor = Color(0xFF282828),
                        titleContentColor = Color.White,
                        textContentColor = Color.White
                    )
                }
            }
        }
        
        // Songs list with dragged item overlay
        Box(modifier = Modifier.fillMaxWidth()) {
            // Show loading indicator during queue operations
            var isQueueUpdating by remember { mutableStateOf(false) }
            
            LazyColumn(
                state = lazyListState,
                        modifier = Modifier
                            .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                // Find the item being touched
                                val touchedItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { itemInfo ->
                                    val itemTop = itemInfo.offset
                                    val itemBottom = itemInfo.offset + itemInfo.size
                                    offset.y.toInt() in itemTop..itemBottom
                                }
                                
                                // Allow dragging any song
                                if (touchedItem != null && touchedItem.index >= 0 && touchedItem.index < mutableQueue.size) {
                                    val draggedSong = mutableQueue[touchedItem.index]
                                    
                                    // Start dragging this item
                                    dragState = dragState.copy(
                                        isDragging = true,
                                        draggedItemIndex = touchedItem.index,
                                        draggedOffset = offset,
                                        draggedItem = draggedSong
                                    )
                                }
                            },
                            onDragEnd = {
                                if (dragState.isDragging && dragState.draggedItemIndex >= 0) {
                                    // Apply the reordering to the actual queue
                                    isQueueUpdating = true
                                    applyQueueChanges()
                                    isQueueUpdating = false
                                }
                                dragState = DragInfo()
                            },
                            onDragCancel = {
                                dragState = DragInfo()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (dragState.isDragging && dragState.draggedItem != null) {
                                    val dragOffset = dragState.draggedOffset.copy(
                                        y = dragState.draggedOffset.y + dragAmount.y
                                    )
                                    
                                    // Find the item under the current drag position
                                    val targetItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { itemInfo ->
                                        val itemTop = itemInfo.offset
                                        val itemBottom = itemInfo.offset + itemInfo.size
                                        dragOffset.y.toInt() in itemTop..itemBottom
                                    }
                                    
                                    // Allow reordering any song
                                    if (targetItem != null && targetItem.index != dragState.draggedItemIndex &&
                                        targetItem.index >= 0 && targetItem.index < mutableQueue.size) {
                                        
                                        // Get the songs being moved
                                        val draggedSong = dragState.draggedItem!!
                                        val targetIndex = targetItem.index
                                        
                                        // Find their positions in the queue
                                        val draggedIndex = mutableQueue.indexOfFirst { it.id == draggedSong.id }
                                        
                                        if (draggedIndex >= 0) {
                                            // Move the item in the queue
                                            val temp = mutableQueue[draggedIndex]
                                            mutableQueue.removeAt(draggedIndex)
                                            mutableQueue.add(targetIndex, temp)
                                            
                                            // Update the dragged item index
                                            dragState = dragState.copy(
                                                draggedItemIndex = targetIndex,
                                                draggedOffset = dragOffset
                                            )
                                        }
                                    }
                                    
                                    // Update the drag offset
                                    dragState = dragState.copy(draggedOffset = dragOffset)
                                }
                            }
                        )
                    }
            ) {
                // Show all songs in the queue as a simple list
                items(mutableQueue.toList(), key = { it.id }) { song ->
                    val isCurrentSong = song.id == currentSong?.id
                    val isSongPlaying = isCurrentSong && isPlaying
                    val isDragged = dragState.isDragging && dragState.draggedItem?.id == song.id
                    
                    // Only render non-dragged items normally
                    if (!isDragged) {
                        QueueSongItem(
                            song = song,
                            onClick = { 
                                // Play the song immediately when clicked
                                if (isCurrentSong) {
                                    viewModel.togglePlayPause()
                                } else {
                                    // Use loadQueueSongInContext to maintain queue context
                                    viewModel.loadQueueSongInContext(song)
                                }
                            },
                            isPlaying = isSongPlaying,
                            isSelected = isCurrentSong,
                            onPlayPause = { 
                                if (isCurrentSong) {
                                    viewModel.togglePlayPause()
                                } else {
                                    // Use loadQueueSongInContext to maintain queue context
                                    viewModel.loadQueueSongInContext(song)
                                }
                            },
                            modifier = Modifier
                                .animateItemPlacement(
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        easing = FastOutSlowInEasing
                                    )
                    )
                        )
                    } else {
                        // Placeholder for dragged item with the same height
                        // This keeps layout stable while the item is being dragged
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp) // Approximate height of SongItem
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .animateItemPlacement(
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                        )
                    }
                }
                
                // Show message if queue is empty
                if (mutableQueue.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No songs in queue",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Gray
                                )
                            }
                        }
                }
            }
            
            // Show loading indicator during queue operations
            if (isQueueUpdating) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.TopCenter)
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFBB86FC),
                        trackColor = Color(0x33BB86FC)
                    )
                }
            }
            
            // Floating dragged item
            if (dragState.isDragging && dragState.draggedItem != null) {
                val draggedSong = dragState.draggedItem!!
                val isCurrentSong = draggedSong.id == currentSong?.id
                val isSongPlaying = isCurrentSong && isPlaying
                
                val offsetY = with(density) { dragState.draggedOffset.y - 36.dp.toPx() } // Center the item on the touch point
                
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, offsetY.toInt()) }
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(8.dp))
                        .zIndex(10f)
                ) {
                    QueueSongItem(
                        song = draggedSong,
                        onClick = { /* No action during drag */ },
                        isPlaying = isSongPlaying,
                        isSelected = isCurrentSong,
                        onPlayPause = { /* No action during drag */ },
                        modifier = Modifier
                    )
                }
            }
        }
    }
}

@Composable
fun QueueSongItem(
    song: Song,
    onClick: () -> Unit,
    isPlaying: Boolean,
    isSelected: Boolean,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF3D1A66) else Color(0xFF2A1152)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = "Drag to reorder",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(4.dp)
                    .size(24.dp)
            )
            
            // Song details
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(when {
                            !song.albumArtUrl.isNullOrBlank() -> song.albumArtUrl
                            !song.albumArt.isBlank() -> song.albumArt
                            else -> R.drawable.default_album_art
                        })
                        .crossfade(true)
                        .transformations(CenterCropSquareTransformation())
                        .build(),
                    contentDescription = "Album art for ${song.title}",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = R.drawable.default_album_art),
                    placeholder = painterResource(id = R.drawable.default_album_art)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Song info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Play/Pause button
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isPlaying) Color(0xFFBB86FC) else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return "%d:%02d".format(minutes, seconds)
}

// Helper composable for timer options
@Composable
private fun TimerOption(
    text: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF333333)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = Color(0xFFBB86FC)
            )
        }
    }
}
