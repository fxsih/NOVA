package com.nova.music.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.nova.music.data.model.Song
import com.nova.music.data.repository.MusicRepository
import com.nova.music.data.api.YTMusicService
import com.nova.music.ui.viewmodels.RepeatMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MusicPlayerServiceImpl"

@Singleton
class MusicPlayerServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val ytMusicService: YTMusicService
) : IMusicPlayerService {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // ExoPlayer is now created on demand and can be recreated when needed
    private var exoPlayer: ExoPlayer? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    override val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _progress = MutableStateFlow(0f)
    override val progress: StateFlow<Float> = _progress

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error

    private val _repeatMode = MutableStateFlow(RepeatMode.NONE)
    override val repeatMode: StateFlow<RepeatMode> = _repeatMode
    
    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration

    // Add a state flow for the current queue
    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    override val currentQueue: StateFlow<List<Song>> = _currentQueue

    private var progressJob: Job? = null
    
    // Keep track of the current queue (internal use)
    private var currentQueueInternal: List<Song> = emptyList()
    
    // Ensure ExoPlayer is created and configured properly
    private fun ensurePlayerCreated() {
        if (exoPlayer == null) {
            Log.d(TAG, "Creating new ExoPlayer instance")
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(playerListener)
            }
        }
    }
    
    // Player listener as a property for reuse
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, if (isPlaying) "ExoPlayer playing" else "ExoPlayer paused/stopped")
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startProgressUpdates()
            } else {
                progressJob?.cancel()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_IDLE -> Log.d(TAG, "ExoPlayer idle")
                Player.STATE_BUFFERING -> Log.d(TAG, "ExoPlayer buffering")
                Player.STATE_READY -> {
                    Log.d(TAG, "ExoPlayer ready")
                    // Update duration when player is ready
                    exoPlayer?.let { player ->
                        _duration.value = player.duration
                    }
                }
                Player.STATE_ENDED -> Log.d(TAG, "ExoPlayer ended")
            }
        }
        
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            // Update current song when media item changes
            if (mediaItem == null) {
                Log.d(TAG, "Media item transitioned to null")
                return
            }
            
            val currentMediaItemIndex = exoPlayer?.currentMediaItemIndex ?: 0
            if (currentQueueInternal.isNotEmpty() && currentMediaItemIndex < currentQueueInternal.size) {
                _currentSong.value = currentQueueInternal[currentMediaItemIndex]
                Log.d(TAG, "Media item transitioned to: ${_currentSong.value?.title}")
                
                // Add to recently played
                _currentSong.value?.let { song ->
                    coroutineScope.launch {
                        try {
                            musicRepository.addToRecentlyPlayed(song)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error adding song to recently played", e)
                        }
                    }
                }
            }
        }
        
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "ExoPlayer error: ${error.message}", error)
            _error.value = "Playback error: ${error.message}"
            
            // Get the current media item that failed
            val currentItem = exoPlayer?.currentMediaItem
            val currentIndex = exoPlayer?.currentMediaItemIndex ?: -1
            
            if (currentItem != null && currentItem.mediaId.startsWith("yt_")) {
                val videoId = currentItem.mediaId.removePrefix("yt_")
                Log.d(TAG, "Trying fallback for video ID: $videoId")
                
                coroutineScope.launch {
                    try {
                        // Try with fallback URL
                        val baseUrl = "http://192.168.29.154:8000"
                        val fallbackUrl = "$baseUrl/audio_fallback?video_id=$videoId"
                        
                        withContext(Dispatchers.Main) {
                            // Replace the current item with a fallback
                            val fallbackItem = MediaItem.Builder()
                                .setUri(fallbackUrl)
                                .setMediaId("${currentItem.mediaId}_fallback")
                                .build()
                            
                            exoPlayer?.removeMediaItem(currentIndex)
                            exoPlayer?.addMediaItem(currentIndex, fallbackItem)
                            exoPlayer?.seekTo(currentIndex, 0)
                            exoPlayer?.prepare()
                            exoPlayer?.play()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fallback also failed", e)
                        _error.value = "Fallback playback also failed: ${e.message}"
                        
                        // Skip to next song if available
                        withContext(Dispatchers.Main) {
                            if (exoPlayer?.hasNextMediaItem() == true) {
                                exoPlayer?.seekToNextMediaItem()
                            }
                        }
                    }
                }
            } else {
                // For non-YouTube songs or if the media ID is not available, just try to skip to the next
                coroutineScope.launch {
                    withContext(Dispatchers.Main) {
                        if (exoPlayer?.hasNextMediaItem() == true) {
                            exoPlayer?.seekToNextMediaItem()
                        }
                    }
                }
            }
        }
    }
    
    // Release ExoPlayer resources
    fun releasePlayer() {
        Log.d(TAG, "Releasing ExoPlayer")
        progressJob?.cancel()
        exoPlayer?.let { player ->
            player.removeListener(playerListener)
            player.release()
            exoPlayer = null
        }
    }

    override suspend fun playSong(song: Song) {
        withContext(Dispatchers.IO) {
            try {
                _error.value = null
                Log.d(TAG, "Playing song: ${song.title}, ID: ${song.id}")
                withContext(Dispatchers.Main) { ensurePlayerCreated() }
                
                // Update the current song immediately
                _currentSong.value = song
                
                // Update the queue to just this song
                currentQueueInternal = listOf(song)
                _currentQueue.value = currentQueueInternal
                
                // Create a media item for the song
                val mediaItem = createMediaItem(song)
                
                withContext(Dispatchers.Main) {
                    // Remember playback state - default to true since we want to play immediately
                    val wasPlaying = true
                    
                    // Clear the current playlist and add the new song
                    exoPlayer?.let { player ->
                        player.clearMediaItems()
                        player.addMediaItem(mediaItem)
                        player.prepare()
                        
                        // Set playWhenReady to ensure playback starts
                        player.playWhenReady = wasPlaying
                        
                        // Force play if needed
                        if (wasPlaying) {
                            player.play()
                        }
                        
                        Log.d(TAG, "ExoPlayer prepared with single song, playWhenReady=$wasPlaying")
                    }
                }
                
                // Add to recently played
                coroutineScope.launch {
                    try {
                        musicRepository.addToRecentlyPlayed(song)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error adding song to recently played", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in playSong", e)
                _error.value = "Error playing song: ${e.message}"
            }
        }
    }
    
    override suspend fun setPlaylistQueue(songs: List<Song>, startSongId: String?) {
        withContext(Dispatchers.IO) {
            try {
                if (songs.isEmpty()) {
                    Log.d(TAG, "Playlist is empty, not setting queue")
                    return@withContext
                }
                
                _error.value = null
                Log.d(TAG, "Setting playlist queue with ${songs.size} songs")
                
                // Store the current queue
                currentQueueInternal = songs
                _currentQueue.value = songs
                
                // Find the start song index (default to 0 if not found)
                val startIndex = if (startSongId != null) {
                    songs.indexOfFirst { it.id == startSongId }.takeIf { it >= 0 } ?: 0
                } else {
                    0
                }
                
                Log.d(TAG, "Starting playback from index $startIndex: ${songs[startIndex].title}")
                
                // Create media items for all songs
                val mediaItems = mutableListOf<MediaItem>()
                for (song in songs) {
                    try {
                        val mediaItem = createMediaItem(song)
                        mediaItems.add(mediaItem)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating media item for song ${song.id}: ${e.message}")
                        // Continue with other songs, don't fail the entire queue
                    }
                }
                
                if (mediaItems.isEmpty()) {
                    Log.e(TAG, "Failed to create any media items for the playlist")
                    _error.value = "Failed to create media items for the playlist"
                    return@withContext
                }
                
                    withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                    exoPlayer?.clearMediaItems()
                    exoPlayer?.setMediaItems(mediaItems)
                    
                    // Make sure we don't go out of bounds
                    val safeStartIndex = if (startIndex < mediaItems.size) {
                        startIndex
                    } else {
                        0
                    }
                    exoPlayer?.seekTo(safeStartIndex, 0)
                        exoPlayer?.prepare()
                        exoPlayer?.playWhenReady = true
                    Log.d(TAG, "Queue set with ${mediaItems.size} items, starting at index $safeStartIndex, playWhenReady=true")
                    
                    // Force play to ensure playback starts immediately
                    exoPlayer?.play()
                    }
                
                // Update current song
                _currentSong.value = songs[startIndex]
                try { 
                    musicRepository.addToRecentlyPlayed(songs[startIndex]) 
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding song to recently played", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in setPlaylistQueue", e)
                _error.value = "Error setting playlist queue: ${e.message}"
            }
        }
    }
    
    // Helper function to create a MediaItem from a Song
    private suspend fun createMediaItem(song: Song): MediaItem {
        return if (song.id.startsWith("yt_")) {
            val videoId = song.id.removePrefix("yt_")
            Log.d(TAG, "Creating MediaItem for YouTube song with ID: $videoId")
            val baseUrl = "http://192.168.29.154:8000"
            
            // Add a metadata property to store the video ID for potential retries
            MediaItem.Builder()
                .setUri("$baseUrl/yt_audio?video_id=$videoId")
                .setMediaId(song.id)
                .build()
        } else {
            // Local song
            Log.d(TAG, "Creating MediaItem for local song: ${song.albumArt}")
            MediaItem.Builder()
                .setUri(Uri.parse(song.albumArt))
                .setMediaId(song.id)
                .build()
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (isActive && _isPlaying.value) {
                val player = exoPlayer ?: break
                val currentPosition = withContext(Dispatchers.Main) {
                    player.currentPosition
                }
                val totalDuration = withContext(Dispatchers.Main) {
                    player.duration
                }
                
                _progress.value = if (totalDuration > 0) {
                    currentPosition.toFloat() / totalDuration
                } else {
                    0f
                }
                
                // Update duration in case it changed
                _duration.value = totalDuration
                
                delay(1000)
            }
        }
    }

    override suspend fun play() {
        withContext(Dispatchers.IO) {
            try {
                _error.value = null
                withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                    if (exoPlayer?.isPlaying == false) {
                        exoPlayer?.playWhenReady = true
                        exoPlayer?.play()
                        Log.d(TAG, "ExoPlayer play() called")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in play", e)
                _error.value = "Error playing: ${e.message}"
            }
        }
    }

    override suspend fun pause() {
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    if (exoPlayer?.isPlaying == true) {
                        exoPlayer?.pause()
                        Log.d(TAG, "ExoPlayer pause() called")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in pause", e)
                _error.value = "Error pausing: ${e.message}"
            }
        }
    }

    override suspend fun resume() {
        withContext(Dispatchers.IO) {
            try {
                _error.value = null
                withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                    if (exoPlayer?.isPlaying == false) {
                        exoPlayer?.playWhenReady = true
                        exoPlayer?.play()
                        Log.d(TAG, "ExoPlayer resume() called")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in resume", e)
                _error.value = "Error resuming: ${e.message}"
            }
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    exoPlayer?.stop()
                    Log.d(TAG, "ExoPlayer stop() called")
                }
                _isPlaying.value = false
                _currentSong.value = null
                _progress.value = 0f
                _duration.value = 0L
                _currentQueue.value = emptyList()
                progressJob?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error in stop", e)
                _error.value = "Error stopping: ${e.message}"
            }
        }
    }

    override suspend fun seekTo(position: Long) {
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                    exoPlayer?.seekTo(position)
                    Log.d(TAG, "ExoPlayer seekTo($position) called")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in seekTo", e)
                _error.value = "Error seeking: ${e.message}"
            }
        }
    }

    override suspend fun setRepeatMode(mode: RepeatMode) {
        withContext(Dispatchers.IO) {
            try {
                _repeatMode.value = mode
                withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                    when (mode) {
                        RepeatMode.NONE -> exoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
                        RepeatMode.ONE -> exoPlayer?.repeatMode = Player.REPEAT_MODE_ONE
                        RepeatMode.ALL -> exoPlayer?.repeatMode = Player.REPEAT_MODE_ALL
                    }
                    Log.d(TAG, "ExoPlayer repeat mode set to $mode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting repeat mode", e)
                _error.value = "Error setting repeat mode: ${e.message}"
            }
        }
    }
    
    override suspend fun clearCurrentSong() {
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    exoPlayer?.stop()
                    Log.d(TAG, "ExoPlayer stopped in clearCurrentSong()")
                }
                _isPlaying.value = false
                _currentSong.value = null
                _progress.value = 0f
                _duration.value = 0L
                _currentQueue.value = emptyList()
                progressJob?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error in clearCurrentSong", e)
                _error.value = "Error clearing current song: ${e.message}"
            }
        }
    }
    
    override suspend fun setShuffle(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                    
                    // Remember playback state
                    val wasPlaying = exoPlayer?.isPlaying == true
                    
                    Log.d(TAG, "Before setShuffle($enabled): wasPlaying=$wasPlaying")
                    
                    // Set shuffle mode
                    exoPlayer?.shuffleModeEnabled = enabled
                    
                    // Restore playback state using playWhenReady for reliability
                    Log.d(TAG, "Setting playWhenReady=$wasPlaying")
                    exoPlayer?.playWhenReady = wasPlaying
                    
                    // Additional check to ensure playback is restored
                    if (wasPlaying && exoPlayer?.isPlaying == false) {
                        Log.d(TAG, "Playback not restored with playWhenReady, forcing play()")
                        exoPlayer?.play()
                    }
                    
                    Log.d(TAG, "ExoPlayer shuffle mode set to $enabled, playWhenReady=$wasPlaying")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting shuffle mode", e)
                _error.value = "Error setting shuffle mode: ${e.message}"
            }
        }
    }
    
    override suspend fun skipToNext() {
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                    if (exoPlayer?.hasNextMediaItem() == true) {
                        exoPlayer?.seekToNextMediaItem()
                        Log.d(TAG, "ExoPlayer skipToNext() called")
                    } else {
                        Log.d(TAG, "No next media item available")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in skipToNext", e)
                _error.value = "Error skipping to next: ${e.message}"
            }
        }
    }
    
    override suspend fun skipToPrevious() {
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                    if (exoPlayer?.hasPreviousMediaItem() == true) {
                        exoPlayer?.seekToPreviousMediaItem()
                        Log.d(TAG, "ExoPlayer skipToPrevious() called")
                    } else {
                        Log.d(TAG, "No previous media item available")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in skipToPrevious", e)
                _error.value = "Error skipping to previous: ${e.message}"
            }
        }
    }
    
    override suspend fun moveSongUp(songId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val queue = currentQueueInternal.toMutableList()
                val index = queue.indexOfFirst { it.id == songId }
                
                // Can't move up if it's the first song or not found
                if (index <= 0) {
                    Log.d(TAG, "Can't move song up: index=$index")
                    return@withContext false
                }
                
                // Swap with the song above it
                val temp = queue[index]
                queue[index] = queue[index - 1]
                queue[index - 1] = temp
                
                // Update the queue
                currentQueueInternal = queue
                _currentQueue.value = queue
                
                // Update the ExoPlayer queue
                withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                    
                    // Remember the current position and index
                    val currentPosition = exoPlayer?.currentPosition ?: 0
                    val currentIndex = exoPlayer?.currentMediaItemIndex ?: 0
                    val currentMediaId = exoPlayer?.currentMediaItem?.mediaId
                    val wasPlaying = exoPlayer?.isPlaying == true
                    
                    Log.d(TAG, "Before moveSongUp: wasPlaying=$wasPlaying, currentPosition=$currentPosition, currentIndex=$currentIndex")
                    
                    // Use incremental queue changes instead of rebuilding the entire queue
                    if (exoPlayer?.mediaItemCount ?: 0 > index && index > 0) {
                        try {
                            // Move the media item up using ExoPlayer's moveMediaItem
                            exoPlayer?.moveMediaItem(index, index - 1)
                            Log.d(TAG, "Used incremental moveMediaItem($index, ${index - 1})")
                            
                            // If the current song was moved or is adjacent to the moved song,
                            // we need to update the current index
                            if (currentIndex == index) {
                                // Current song was moved up
                                exoPlayer?.seekTo(index - 1, currentPosition)
                                Log.d(TAG, "Current song moved up, seeking to index=${index - 1}")
                            } else if (currentIndex == index - 1) {
                                // Current song was moved down
                                exoPlayer?.seekTo(index, currentPosition)
                                Log.d(TAG, "Current song moved down, seeking to index=$index")
                            }
                        } catch (e: Exception) {
                            // Fallback to rebuilding the queue if incremental update fails
                            Log.e(TAG, "Incremental moveMediaItem failed, falling back to full rebuild", e)
                            rebuildEntireQueue(queue, currentMediaId, currentPosition, currentIndex, wasPlaying)
                        }
                    } else {
                        // Fallback to rebuilding the queue if indices are out of bounds
                        Log.d(TAG, "Index out of bounds for incremental update, using full rebuild")
                        rebuildEntireQueue(queue, currentMediaId, currentPosition, currentIndex, wasPlaying)
                    }
                    
                    Log.d(TAG, "Queue reordered: moved song up at index $index, playWhenReady=$wasPlaying")
                }
                
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error moving song up", e)
                _error.value = "Error reordering queue: ${e.message}"
                return@withContext false
            }
        }
    }
    
    override suspend fun moveSongDown(songId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val queue = currentQueueInternal.toMutableList()
                val index = queue.indexOfFirst { it.id == songId }
                
                // Can't move down if it's the last song or not found
                if (index < 0 || index >= queue.size - 1) {
                    Log.d(TAG, "Can't move song down: index=$index, size=${queue.size}")
                    return@withContext false
                }
                
                // Swap with the song below it
                val temp = queue[index]
                queue[index] = queue[index + 1]
                queue[index + 1] = temp
                
                // Update the queue
                currentQueueInternal = queue
                _currentQueue.value = queue
                
                // Update the ExoPlayer queue
                withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                    
                    // Remember the current position and index
                    val currentPosition = exoPlayer?.currentPosition ?: 0
                    val currentIndex = exoPlayer?.currentMediaItemIndex ?: 0
                    val currentMediaId = exoPlayer?.currentMediaItem?.mediaId
                    val wasPlaying = exoPlayer?.isPlaying == true
                    
                    Log.d(TAG, "Before moveSongDown: wasPlaying=$wasPlaying, currentPosition=$currentPosition, currentIndex=$currentIndex")
                    
                    // Use incremental queue changes instead of rebuilding the entire queue
                    if (exoPlayer?.mediaItemCount ?: 0 > index + 1) {
                        try {
                            // Move the media item down using ExoPlayer's moveMediaItem
                            exoPlayer?.moveMediaItem(index, index + 1)
                            Log.d(TAG, "Used incremental moveMediaItem($index, ${index + 1})")
                            
                            // If the current song was moved or is adjacent to the moved song,
                            // we need to update the current index
                            if (currentIndex == index) {
                                // Current song was moved down
                                exoPlayer?.seekTo(index + 1, currentPosition)
                                Log.d(TAG, "Current song moved down, seeking to index=${index + 1}")
                            } else if (currentIndex == index + 1) {
                                // Current song was moved up
                                exoPlayer?.seekTo(index, currentPosition)
                                Log.d(TAG, "Current song moved up, seeking to index=$index")
                            }
                        } catch (e: Exception) {
                            // Fallback to rebuilding the queue if incremental update fails
                            Log.e(TAG, "Incremental moveMediaItem failed, falling back to full rebuild", e)
                            rebuildEntireQueue(queue, currentMediaId, currentPosition, currentIndex, wasPlaying)
                        }
                    } else {
                        // Fallback to rebuilding the queue if indices are out of bounds
                        Log.d(TAG, "Index out of bounds for incremental update, using full rebuild")
                        rebuildEntireQueue(queue, currentMediaId, currentPosition, currentIndex, wasPlaying)
                    }
                    
                    Log.d(TAG, "Queue reordered: moved song down at index $index, playWhenReady=$wasPlaying")
                }
                
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error moving song down", e)
                _error.value = "Error reordering queue: ${e.message}"
                return@withContext false
            }
        }
    }
    
    override suspend fun reorderQueue(songs: List<Song>) {
        withContext(Dispatchers.IO) {
            try {
                if (songs.isEmpty()) {
                    Log.d(TAG, "Cannot reorder empty queue")
                    return@withContext
                }
                
                // Update the queue
                currentQueueInternal = songs
                _currentQueue.value = songs
                
                // Update the ExoPlayer queue
                withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                    
                    // Remember the current position, index, and playback state
                    val currentPosition = exoPlayer?.currentPosition ?: 0
                    val currentIndex = exoPlayer?.currentMediaItemIndex ?: 0
                    val currentMediaId = exoPlayer?.currentMediaItem?.mediaId
                    val wasPlaying = exoPlayer?.isPlaying == true
                    
                    Log.d(TAG, "Before reorderQueue: wasPlaying=$wasPlaying, currentPosition=$currentPosition, currentIndex=$currentIndex")
                    
                    // Rebuild the media items
                    val mediaItems = songs.map { createMediaItem(it) }
                    
                    // Set the new queue
                    exoPlayer?.clearMediaItems()
                    exoPlayer?.setMediaItems(mediaItems)
                    
                    // Restore playback position
                    if (currentMediaId != null) {
                        // Find the new index of the current song
                        val newIndex = songs.indexOfFirst { it.id == currentMediaId }
                        if (newIndex >= 0) {
                            exoPlayer?.seekTo(newIndex, currentPosition)
                            Log.d(TAG, "Seeking to newIndex=$newIndex (found by mediaId)")
                        } else {
                            exoPlayer?.seekTo(currentIndex, currentPosition)
                            Log.d(TAG, "Seeking to original currentIndex=$currentIndex (mediaId not found)")
                        }
                    } else {
                        exoPlayer?.seekTo(currentIndex, currentPosition)
                        Log.d(TAG, "Seeking to original currentIndex=$currentIndex (no mediaId)")
                    }
                    
                    // Prepare if needed
                    if (exoPlayer?.playbackState == Player.STATE_IDLE) {
                        Log.d(TAG, "Player in STATE_IDLE, preparing")
                        exoPlayer?.prepare()
                    }
                    
                    // Restore playback state using playWhenReady for reliability
                    Log.d(TAG, "Setting playWhenReady=$wasPlaying")
                    exoPlayer?.playWhenReady = wasPlaying
                    
                    // Additional check to ensure playback is restored
                    if (wasPlaying && exoPlayer?.isPlaying == false) {
                        Log.d(TAG, "Playback not restored with playWhenReady, forcing play()")
                        exoPlayer?.play()
                    }
                    
                    Log.d(TAG, "Queue fully reordered with ${songs.size} songs, playWhenReady=$wasPlaying")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reordering queue", e)
                _error.value = "Error reordering queue: ${e.message}"
            }
        }
    }
    
    override suspend fun removeFromQueue(songId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val queue = currentQueueInternal.toMutableList()
                val index = queue.indexOfFirst { it.id == songId }
                
                // Can't remove if not found
                if (index < 0) {
                    Log.d(TAG, "Can't remove song: not found in queue")
                    return@withContext false
                }
                
                // Don't allow removing the currently playing song
                val currentSongId = _currentSong.value?.id
                if (songId == currentSongId) {
                    Log.d(TAG, "Can't remove currently playing song from queue")
                    return@withContext false
                }
                
                // Remove the song
                queue.removeAt(index)
                
                // Update the queue
                currentQueueInternal = queue
                _currentQueue.value = queue
                
                // Update the ExoPlayer queue
                withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                    
                    // Remember the current position and index
                    val currentPosition = exoPlayer?.currentPosition ?: 0
                    val currentIndex = exoPlayer?.currentMediaItemIndex ?: 0
                    val wasPlaying = exoPlayer?.isPlaying == true
                    
                    Log.d(TAG, "Before removeFromQueue: wasPlaying=$wasPlaying, currentPosition=$currentPosition, currentIndex=$currentIndex, removedIndex=$index")
                    
                    // Use incremental queue changes instead of rebuilding the entire queue
                    if (exoPlayer?.mediaItemCount ?: 0 > index) {
                        try {
                            // Remove the media item using ExoPlayer's removeMediaItem
                            exoPlayer?.removeMediaItem(index)
                            Log.d(TAG, "Used incremental removeMediaItem($index)")
                            
                            // If the removed item was before the current item, we need to adjust the current index
                            if (index < currentIndex) {
                                // Current index shifts down by 1
                                exoPlayer?.seekTo(currentIndex - 1, currentPosition)
                                Log.d(TAG, "Removed item was before current, seeking to index=${currentIndex - 1}")
                            }
                        } catch (e: Exception) {
                            // Fallback to rebuilding the queue if incremental update fails
                            Log.e(TAG, "Incremental removeMediaItem failed, falling back to full rebuild", e)
                            
                            // Rebuild the media items
                            val mediaItems = queue.map { createMediaItem(it) }
                            
                            // Set the new queue
                            exoPlayer?.clearMediaItems()
                            exoPlayer?.setMediaItems(mediaItems)
                            
                            // Restore playback position
                            val newCurrentIndex = if (index < currentIndex) {
                                currentIndex - 1
                            } else {
                                currentIndex
                            }
                            val safeIndex = newCurrentIndex.coerceIn(0, mediaItems.size - 1)
                            exoPlayer?.seekTo(safeIndex, currentPosition)
                            Log.d(TAG, "Seeking to safeIndex=$safeIndex (adjusted from newCurrentIndex=$newCurrentIndex)")
                            
                            // Prepare if needed
                            if (exoPlayer?.playbackState == Player.STATE_IDLE) {
                                Log.d(TAG, "Player in STATE_IDLE, preparing")
                                exoPlayer?.prepare()
                            }
                        }
                    } else {
                        // Fallback to rebuilding the queue if indices are out of bounds
                        Log.d(TAG, "Index out of bounds for incremental update, using full rebuild")
                        
                        // Rebuild the media items
                        val mediaItems = queue.map { createMediaItem(it) }
                        
                        // Set the new queue
                        exoPlayer?.clearMediaItems()
                        exoPlayer?.setMediaItems(mediaItems)
                        
                        // Restore playback position
                        val newCurrentIndex = if (index < currentIndex) {
                            currentIndex - 1
                        } else {
                            currentIndex
                        }
                        val safeIndex = newCurrentIndex.coerceIn(0, mediaItems.size - 1)
                        exoPlayer?.seekTo(safeIndex, currentPosition)
                        Log.d(TAG, "Seeking to safeIndex=$safeIndex (adjusted from newCurrentIndex=$newCurrentIndex)")
                        
                        // Prepare if needed
                        if (exoPlayer?.playbackState == Player.STATE_IDLE) {
                            Log.d(TAG, "Player in STATE_IDLE, preparing")
                            exoPlayer?.prepare()
                        }
                    }
                    
                    // Always restore playback state using playWhenReady for reliability
                    Log.d(TAG, "Setting playWhenReady=$wasPlaying")
                    exoPlayer?.playWhenReady = wasPlaying
                    
                    // Additional check to ensure playback is restored
                    if (wasPlaying && exoPlayer?.isPlaying == false) {
                        Log.d(TAG, "Playback not restored with playWhenReady, forcing play()")
                        exoPlayer?.play()
                    }
                    
                    Log.d(TAG, "Queue updated: removed song at index $index, playWhenReady=$wasPlaying")
                }
                
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error removing song from queue", e)
                _error.value = "Error updating queue: ${e.message}"
                return@withContext false
            }
        }
    }
    
    override suspend fun playQueueItemAt(index: Int) {
        withContext(Dispatchers.IO) {
            try {
                _error.value = null
                withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                    
                    // Remember playback state - we want to ensure playback starts
                    val wasPlaying = if (exoPlayer?.isPlaying == true) {
                        true
                    } else {
                        true // Force playback to start even if it wasn't playing before
                    }
                    
                    Log.d(TAG, "Playing queue item at index $index, wasPlaying=$wasPlaying")
                    
                    // Seek to the specified index
                    val mediaItemCount = exoPlayer?.mediaItemCount ?: 0
                    if (index >= 0 && index < mediaItemCount) {
                        exoPlayer?.seekTo(index, 0)
                        
                        // Ensure playback starts immediately
                        exoPlayer?.playWhenReady = true
                        exoPlayer?.play()
                        
                        // Update the current song
                        if (currentQueueInternal.isNotEmpty() && index < currentQueueInternal.size) {
                            _currentSong.value = currentQueueInternal[index]
                            Log.d(TAG, "Updated current song to: ${_currentSong.value?.title}")
                        } else {
                            Log.d(TAG, "Could not update current song: queue is empty or index out of bounds")
                        }
                    } else {
                        Log.e(TAG, "Invalid queue index: $index, queue size: ${exoPlayer?.mediaItemCount}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in playQueueItemAt", e)
                _error.value = "Error playing queue item: ${e.message}"
            }
        }
    }
    
    // Helper method to rebuild the entire queue
    private suspend fun rebuildEntireQueue(
        queue: List<Song>,
        currentMediaId: String?,
        currentPosition: Long,
        fallbackIndex: Int,
        wasPlaying: Boolean
    ) {
        // Rebuild the media items
        val mediaItems = queue.map { createMediaItem(it) }
        
        // Set the new queue
        exoPlayer?.clearMediaItems()
        exoPlayer?.setMediaItems(mediaItems)
        
        // Restore playback position
        if (currentMediaId != null) {
            // Find the new index of the current song
            val newIndex = queue.indexOfFirst { it.id == currentMediaId }
            if (newIndex >= 0) {
                exoPlayer?.seekTo(newIndex, currentPosition)
                Log.d(TAG, "Seeking to newIndex=$newIndex (found by mediaId)")
            } else {
                exoPlayer?.seekTo(fallbackIndex, currentPosition)
                Log.d(TAG, "Seeking to fallbackIndex=$fallbackIndex (mediaId not found)")
            }
        } else {
            exoPlayer?.seekTo(fallbackIndex, currentPosition)
            Log.d(TAG, "Seeking to fallbackIndex=$fallbackIndex (no mediaId)")
        }
        
        // Prepare if needed
        if (exoPlayer?.playbackState == Player.STATE_IDLE) {
            Log.d(TAG, "Player in STATE_IDLE, preparing")
            exoPlayer?.prepare()
        }
        
        // Restore playback state using playWhenReady for reliability
        Log.d(TAG, "Setting playWhenReady=$wasPlaying")
        exoPlayer?.playWhenReady = wasPlaying
        
        // Additional check to ensure playback is restored
        if (wasPlaying && exoPlayer?.isPlaying == false) {
            Log.d(TAG, "Playback not restored with playWhenReady, forcing play()")
            exoPlayer?.play()
        }
    }

    // This method should be called when the service is destroyed
    fun onDestroy() {
        Log.d(TAG, "onDestroy called, releasing resources")
        releasePlayer()
        coroutineScope.cancel()
    }
} 