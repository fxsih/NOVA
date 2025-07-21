package com.nova.music.service

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nova.music.data.model.Song
import com.nova.music.data.repository.MusicRepository
import com.nova.music.data.api.YTMusicService
import com.nova.music.ui.viewmodels.RepeatMode
import com.nova.music.util.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MusicPlayerServiceImpl"

@Singleton
class MusicPlayerServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val ytMusicService: YTMusicService,
    private val preferenceManager: PreferenceManager
) : IMusicPlayerService {

    var progressJobSupervisor = SupervisorJob()
    var coroutineScope = CoroutineScope(progressJobSupervisor + Dispatchers.Main)
    
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
            val mediaId = mediaItem.mediaId
            Log.d(TAG, "Media item transitioned to index: $currentMediaItemIndex, mediaId: $mediaId, reason: $reason")
            
            // Find the song in the current queue with this media ID
            val song = currentQueueInternal.find { it.id == mediaId }
            if (song != null) {
                _currentSong.value = song
                Log.d(TAG, "Media item transitioned to: ${song.title}")
                
                // Add to recently played
                coroutineScope.launch {
                    try {
                        musicRepository.addToRecentlyPlayed(song)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error adding song to recently played", e)
                    }
                }
            } else {
                Log.d(TAG, "Could not find song with mediaId=$mediaId in the queue")
                
                // If the current queue doesn't have this song (unusual case),
                // try to find it in the media items directly
                if (currentMediaItemIndex < currentQueueInternal.size) {
                    _currentSong.value = currentQueueInternal[currentMediaItemIndex]
                    Log.d(TAG, "Falling back to song at index $currentMediaItemIndex: ${_currentSong.value?.title}")
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

    /**
     * Plays the given song.
     */
    override suspend fun playSong(song: Song) {
        withContext(Dispatchers.IO) {
            try {
                _error.value = null
                Log.d(TAG, "Playing song: ${song.title}, ID: ${song.id}")
                
                if (song.isDownloaded) {
                    Log.d(TAG, "Song is marked as downloaded, isDownloaded=${song.isDownloaded}, localFilePath=${song.localFilePath}")
                }
                
                withContext(Dispatchers.Main) { ensurePlayerCreated() }
                
                // Reset progress and duration immediately when loading new song
                _progress.value = 0f
                _duration.value = 0L
                
                // Update the current song immediately
                _currentSong.value = song
                
                // Log current song details for debugging
                logCurrentSongDetails()
                
                // Update the queue to just this song
                currentQueueInternal = listOf(song)
                _currentQueue.value = currentQueueInternal
                
                // Create a media item for the song - check if downloaded first
                val mediaItem = if (song.isDownloaded && song.localFilePath != null) {
                    val file = File(song.localFilePath)
                    if (file.exists() && file.length() > 0) {
                        Log.d(TAG, "Using local file for playback: ${song.localFilePath}")
                        MediaItem.Builder()
                            .setMediaId(song.id)
                            .setUri(song.localFilePath)
                            .build()
                    } else {
                        Log.d(TAG, "Local file doesn't exist, falling back to streaming")
                        createMediaItem(song)
                    }
                } else {
                    createMediaItem(song)
                }
                
                withContext(Dispatchers.Main) {
                    // Remember playback state - default to true since we want to play immediately
                    val wasPlaying = true
                    
                    // Clear the current playlist and add the new song
                    exoPlayer?.let { player ->
                        player.clearMediaItems()
                        player.addMediaItem(mediaItem)
                        player.prepare()
                        // Update duration after prepare
                        _duration.value = player.duration
                        // Reset progress to 0 when new song is prepared
                        _progress.value = 0f
                        
                        // Set playWhenReady to ensure playback starts
                        player.playWhenReady = wasPlaying
                        
                        // Force play if needed
                        if (wasPlaying) {
                            player.play()
                        }
                        
                        // Start progress updates
                        startProgressUpdates()
                        
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
        Log.d(TAG, "setPlaylistQueue called with ${songs.size} songs, ids: ${songs.map { it.id }}")
        withContext(Dispatchers.IO) {
            try {
            
                Log.d(TAG, "Setting playlist queue with ${songs.size} songs, starting with $startSongId")
                
                // Log downloaded songs info
                val downloadedCount = songs.count { it.isDownloaded }
                Log.d(TAG, "Queue contains $downloadedCount downloaded songs")
                
                withContext(Dispatchers.Main) { ensurePlayerCreated() }
                
                // Update the queue
                currentQueueInternal = songs
                
                // Find the start song index
                val startIndex = if (startSongId != null) {
                    songs.indexOfFirst { it.id == startSongId }.takeIf { it >= 0 } ?: 0
                } else {
                    0
                }
                
                // Create media items for all songs
                val mediaItems = songs.map { song ->
                    // Check if the song is downloaded
                    if (song.isDownloaded && song.localFilePath != null) {
                        val file = File(song.localFilePath)
                        if (file.exists() && file.length() > 0) {
                            Log.d(TAG, "Using local file for song ${song.title}: ${song.localFilePath}")
                            MediaItem.Builder()
                                .setMediaId(song.id)
                                .setUri(song.localFilePath)
                                .build()
                        } else {
                            Log.d(TAG, "Local file for song ${song.title} doesn't exist, falling back to streaming")
                            createMediaItem(song)
                        }
                    } else {
                        createMediaItem(song)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    // Remember playback state - default to true since we want to play immediately
                    val wasPlaying = true
                    
                    // Remember current shuffle state
                    val isShuffleEnabled = exoPlayer?.shuffleModeEnabled ?: false
                    Log.d(TAG, "Current shuffle mode is $isShuffleEnabled")
                    
                    exoPlayer?.let { player ->
                        // Temporarily disable shuffle while setting up the queue
                        if (isShuffleEnabled) {
                            player.shuffleModeEnabled = false
                            Log.d(TAG, "Temporarily disabled shuffle mode to set up queue")
                        }
                        
                        // Clear the current playlist
                        player.clearMediaItems()
                    
                        // Add all songs
                        player.addMediaItems(mediaItems)
                        
                        // Set the start position
                        player.seekTo(startIndex, 0)
                        
                        // Prepare the player
                        player.prepare()
                        // Update duration after prepare
                        _duration.value = player.duration
                        // Reset progress to 0 when new playlist is prepared
                        _progress.value = 0f
                        
                        // Re-enable shuffle if it was enabled before
                        if (isShuffleEnabled) {
                            // We need to make sure the start song is played first, then enable shuffle
                            player.shuffleModeEnabled = true
                            Log.d(TAG, "Re-enabled shuffle mode")
                            
                            // Give time for shuffle to take effect
                            delay(100)
                            
                            // Update our internal queue to match the shuffled order
                            val updatedQueue = mutableListOf<Song>()
                            for (i in 0 until player.mediaItemCount) {
                                val mediaItem = player.getMediaItemAt(i)
                                val mediaId = mediaItem.mediaId
                                
                                // Find the song in the original queue with this media ID
                                val song = songs.find { it.id == mediaId }
                                if (song != null) {
                                    updatedQueue.add(song)
                                }
                            }
                            
                            // Update our internal queue with the new order
                            if (updatedQueue.isNotEmpty()) {
                                currentQueueInternal = updatedQueue
                                _currentQueue.value = updatedQueue
                                Log.d(TAG, "Updated internal queue to match ExoPlayer's shuffled order")
                            }
                        }
                        
                        // Set playWhenReady to ensure playback starts
                        player.playWhenReady = wasPlaying
                        
                        // Force play if needed
                        if (wasPlaying) {
                            player.play()
                        }
                        
                        // Start progress updates
                        startProgressUpdates()
                
                        // Update the current song
                        _currentSong.value = songs[startIndex]
                        
                        Log.d(TAG, "ExoPlayer prepared with ${songs.size} songs, starting at index $startIndex, playWhenReady=$wasPlaying, shuffle=$isShuffleEnabled")
                    }
                }
                
                // Add the start song to recently played
                if (startIndex < songs.size) {
                    coroutineScope.launch {
                        try { 
                            musicRepository.addToRecentlyPlayed(songs[startIndex]) 
                        } catch (e: Exception) {
                            Log.e(TAG, "Error adding song to recently played", e)
                        }
                    }
                }
                // At the end, always set the StateFlow to the full queue
                _currentQueue.value = currentQueueInternal
            } catch (e: Exception) {
                Log.e(TAG, "Error in setPlaylistQueue", e)
                _error.value = "Error setting playlist queue: ${e.message}"
            }
        }
    }
    
    /**
     * Creates a MediaItem for the given song.
     */
    private fun createMediaItem(song: Song): MediaItem {
        // Check if the song is downloaded and has a local file path
        if (song.isDownloaded && song.localFilePath != null) {
            val file = File(song.localFilePath)
            if (file.exists() && file.length() > 0) {
                Log.d(TAG, "Using local file for playback: ${song.localFilePath}")
                return MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(song.localFilePath)
                    .build()
            } else {
                Log.d(TAG, "Local file doesn't exist, falling back to streaming: ${song.title}")
            }
        }

        // Otherwise, use streaming URL
        val videoId = if (song.id.startsWith("yt_")) {
            song.id.removePrefix("yt_")
        } else {
            song.id
        }
        
        val streamUrl = "${preferenceManager.getApiBaseUrl()}/yt_audio?video_id=$videoId"
        Log.d(TAG, "Creating media item with streaming URL: $streamUrl")
        
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(streamUrl)
            .build()
    }

    private fun startProgressUpdates() {
        Log.d(TAG, "startProgressUpdates() called - starting progress coroutine")
        Log.d(TAG, "coroutineScope isActive=${coroutineScope.isActive}")
        progressJob?.cancel()
        progressJob = coroutineScope.launch(Dispatchers.Main) {
            Log.d(TAG, "Progress coroutine started (isActive=$isActive)")
            try {
                var retries = 0
                while ((exoPlayer == null || exoPlayer?.mediaItemCount == 0) && retries < 20) {
                    Log.d(TAG, "Waiting for exoPlayer to be ready... (retries=$retries)")
                    delay(100)
                    retries++
                }
                val player = exoPlayer
                if (player == null || player.mediaItemCount == 0) {
                    Log.w(TAG, "Progress coroutine exiting: exoPlayer is null or has no media items after waiting.")
                    return@launch
                }
                while (isActive) {
                    val currentPosition = player.currentPosition
                    val totalDuration = player.duration
                    
                    // Ensure values are valid
                    val validDuration = if (totalDuration > 0) totalDuration else 0L
                    val validPosition = if (currentPosition >= 0) currentPosition else 0L
                    
                    // Calculate progress with validation
                    val newProgress = if (validDuration > 0) {
                        (validPosition.toFloat() / validDuration).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    
                    // Update progress and duration
                    _progress.value = newProgress
                    _duration.value = validDuration
                    
                    // Log progress for debugging (only every 5 seconds to avoid spam)
                    if (validPosition % 5000 < 1000) {
                        Log.d(TAG, "Progress update: ${(newProgress * 100).toInt()}% ($validPosition/$validDuration ms) [progress coroutine running]")
                    }
                    
                    delay(100) // Update more frequently for smoother seekbar
                }
                Log.d(TAG, "Progress coroutine exited normally (isActive=$isActive)")
            } finally {
                Log.d(TAG, "Progress coroutine finally block: Cancelled or completed.")
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
                        // Start progress updates to update seekbar
                        startProgressUpdates()
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
                        // Start progress updates to update seekbar
                        startProgressUpdates()
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
                    // Stop playback
                    exoPlayer?.stop()
                    // Clear the playlist
                    exoPlayer?.clearMediaItems()
                    Log.d(TAG, "ExoPlayer stopped and cleared in clearCurrentSong()")
                }
                
                // Reset all state
                _isPlaying.value = false
                _currentSong.value = null
                _progress.value = 0f
                _duration.value = 0L
                _currentQueue.value = emptyList()
                currentQueueInternal = emptyList()
                
                // Cancel progress tracking
                progressJob?.cancel()
                
                Log.d(TAG, "All player state cleared successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error in clearCurrentSong", e)
                _error.value = "Error clearing current song: ${e.message}"
            }
        }
    }
    
    /**
     * Resets the player state when a new song is selected.
     * This ensures the seekbar starts from 0:00 and all state is clean.
     */
    override suspend fun resetPlayerForNewSong() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Resetting player state for new song")
                
                // Reset progress and duration immediately
                _progress.value = 0f
                _duration.value = 0L
                
                withContext(Dispatchers.Main) {
                    // Ensure player is at position 0
                    exoPlayer?.seekTo(0)
                }
                
                Log.d(TAG, "Player state reset successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting player state", e)
            }
        }
    }
    
    /**
     * Restores the player state from the actual ExoPlayer instance.
     * This is called when the app restarts to sync with the running service.
     */
    override suspend fun restorePlayerState() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== RESTORE PLAYER STATE SERVICE STARTED ===")
                Log.d(TAG, "Current state flows before restore: progress=${_progress.value}, duration=${_duration.value}, playing=${_isPlaying.value}")
                
                // Check if we have a current song but the ExoPlayer is empty
                val currentSongValue = currentSong.value
                if (currentSongValue != null) {
                    Log.d(TAG, "We have a current song: ${currentSongValue.title}, but ExoPlayer might be empty")
                    
                    withContext(Dispatchers.Main) {
                        ensurePlayerCreated()
                        
                        exoPlayer?.let { player ->
                            val currentPosition = player.currentPosition
                            val totalDuration = player.duration
                            val isCurrentlyPlaying = player.isPlaying
                            val hasMediaItems = player.mediaItemCount > 0
                            val playbackState = player.playbackState
                            
                            Log.d(TAG, "ExoPlayer state: position=$currentPosition, duration=$totalDuration, playing=$isCurrentlyPlaying, hasMediaItems=$hasMediaItems, playbackState=$playbackState")
                            
                            // If ExoPlayer is empty but we have a current song, we need to reload the media
                            if (!hasMediaItems && currentSongValue != null) {
                                Log.d(TAG, "ExoPlayer is empty but we have a current song. Reloading media...")
                                
                                // Remember the original progress before reloading
                                val originalProgress = _progress.value
                                val originalDuration = _duration.value
                                val originalPosition = if (originalDuration > 0) {
                                    (originalProgress * originalDuration).toLong()
                                } else {
                                    0L
                                }
                                
                                Log.d(TAG, "Original state before reload: progress=$originalProgress, duration=$originalDuration, position=$originalPosition")
                                
                                // Reload the current song into the ExoPlayer
                                try {
                                    Log.d(TAG, "Reloading song: ${currentSongValue.title}")
                                    
                                    // Create media item for the current song
                                    val mediaItem = createMediaItem(currentSongValue)
                                    player.setMediaItem(mediaItem)
                                    player.prepare()
                                    // Update duration after prepare
                                    _duration.value = player.duration
                                    
                                    // Wait a moment for the player to load
                                    delay(1000) // Increased delay to ensure proper loading
                                    
                                    // Now get the updated state
                                    val newPosition = player.currentPosition
                                    val newDuration = player.duration
                                    val newIsPlaying = player.isPlaying
                                    val newHasMediaItems = player.mediaItemCount > 0
                                    val newPlaybackState = player.playbackState
                                    
                                    Log.d(TAG, "After reload: position=$newPosition, duration=$newDuration, playing=$newIsPlaying, hasMediaItems=$newHasMediaItems, playbackState=$newPlaybackState")
                                    
                                    // Update state flows with the reloaded media
                                    if (newHasMediaItems && newDuration > 0) {
                                        _duration.value = newDuration
                                        _isPlaying.value = newIsPlaying
                                        
                                        // Seek to the original position if we had one
                                        if (originalPosition > 0 && originalPosition < newDuration) {
                                            Log.d(TAG, "Seeking to original position: $originalPosition ms")
                                            player.seekTo(originalPosition)
                                            
                                            // Wait a moment for seek to complete
                                            delay(200)
                                            
                                            // Get the position after seeking
                                            val finalPosition = player.currentPosition
                                            val finalProgress = if (newDuration > 0) {
                                                (finalPosition.toFloat() / newDuration).coerceIn(0f, 1f)
                                            } else 0f
                                            
                                            _progress.value = finalProgress
                                            Log.d(TAG, "After seeking: position=$finalPosition, progress=${(finalProgress * 100).toInt()}%")
                                        } else {
                                            // No original position or invalid, use current position
                                            if (newPosition >= 0) {
                                                val progress = (newPosition.toFloat() / newDuration).coerceIn(0f, 1f)
                                                _progress.value = progress
                                                Log.d(TAG, "Using current position: ${(progress * 100).toInt()}% ($newPosition/$newDuration ms)")
                                            }
                                        }
                                        
                                        // Restart progress updates
                                        startProgressUpdates()
                                        Log.d(TAG, "Restarted progress updates after reload")
                                    } else {
                                        Log.d(TAG, "Reload failed - no media items or invalid duration")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error reloading media", e)
                                    e.printStackTrace()
                                }
                            } else if (hasMediaItems && totalDuration > 0) {
                                // ExoPlayer has media, update state normally
                                _duration.value = totalDuration
                                _isPlaying.value = isCurrentlyPlaying
                                
                                if (currentPosition >= 0) {
                                    val progress = (currentPosition.toFloat() / totalDuration).coerceIn(0f, 1f)
                                    _progress.value = progress
                                    Log.d(TAG, "Restored progress: ${(progress * 100).toInt()}% ($currentPosition/$totalDuration ms)")
                                }
                                // Always restart progress updates after restore
                                startProgressUpdates()
                                Log.d(TAG, "Restarted progress updates after restore (hasMediaItems branch)")
                            } else {
                                Log.d(TAG, "ExoPlayer state is invalid, keeping current state flows")
                            }
                        } ?: run {
                            Log.d(TAG, "ExoPlayer is null, cannot restore state")
                        }
                    }
                } else {
                    Log.d(TAG, "No current song, nothing to restore")
                }
                
                Log.d(TAG, "Final state flows after restore: progress=${_progress.value}, duration=${_duration.value}, playing=${_isPlaying.value}")
                Log.d(TAG, "=== RESTORE PLAYER STATE SERVICE COMPLETED ===")
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring player state", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Shuffles the current queue while keeping the current song at the top.
     * This method doesn't toggle the shuffle mode state but just reorders the queue.
     */
    override suspend fun shuffleQueue() {
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                    
                    // Remember playback state
                    val wasPlaying = exoPlayer?.isPlaying == true
                    val currentPosition = exoPlayer?.currentPosition ?: 0
                    val currentSongId = exoPlayer?.currentMediaItem?.mediaId
                    val currentIndex = exoPlayer?.currentMediaItemIndex ?: 0
                    
                    Log.d(TAG, "shuffleQueue: wasPlaying=$wasPlaying, currentSongId=$currentSongId, currentIndex=$currentIndex")
                    
                    // Save the current queue before shuffling
                    val originalQueue = currentQueueInternal.toList()
                    if (originalQueue.isEmpty()) {
                        Log.d(TAG, "Queue is empty, nothing to shuffle")
                        return@withContext
                    }
                    
                    // Get the current song for later
                    val currentSong = currentQueueInternal.find { it.id == currentSongId }
                    
                    // Create a new shuffled queue while keeping the current song at the top
                    val shuffledQueue = if (currentSong != null) {
                        // Remove current song from the queue
                        val remainingQueue = originalQueue.filter { it.id != currentSongId }
                        
                        // Shuffle the remaining songs
                        val shuffled = remainingQueue.shuffled()
                        
                        // Put current song at the beginning
                        listOf(currentSong) + shuffled
                    } else {
                        // No current song, just shuffle everything
                        originalQueue.shuffled()
                    }
                    
                    // Update our internal queue with the new shuffled order
                    currentQueueInternal = shuffledQueue
                    _currentQueue.value = shuffledQueue
                    
                    Log.d(TAG, "Created shuffled queue with ${shuffledQueue.size} songs")
                    
                    // Create media items for the shuffled queue
                    val mediaItems = shuffledQueue.map { createMediaItem(it) }
                    
                    // Clear and rebuild the queue in ExoPlayer
                    exoPlayer?.clearMediaItems()
                    exoPlayer?.setMediaItems(mediaItems)
                    exoPlayer?.prepare()
                    
                    // Restore playback position - the current song should be at index 0
                    exoPlayer?.seekTo(0, currentPosition)
                    Log.d(TAG, "Restored position to index 0 (current song), position $currentPosition")
                    
                    // Restore playback state
                    exoPlayer?.playWhenReady = wasPlaying
                    
                    // Additional check to ensure playback is restored
                    if (wasPlaying && exoPlayer?.isPlaying == false) {
                        Log.d(TAG, "Playback not restored with playWhenReady, forcing play()")
                        exoPlayer?.play()
                    }
                    
                    Log.d(TAG, "Queue shuffled successfully, playWhenReady=$wasPlaying")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error shuffling queue", e)
                _error.value = "Error shuffling queue: ${e.message}"
            }
        }
    }
    
    /**
     * Legacy method to support old behavior. Now just calls shuffleQueue().
     * This method is kept for backward compatibility.
     */
    override suspend fun setShuffle(enabled: Boolean) {
        // Just shuffle the queue regardless of the enabled parameter
        shuffleQueue()
    }
    
    override suspend fun skipToNext() {
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                    if (exoPlayer?.hasNextMediaItem() == true) {
                        // Reset progress to 0 when skipping to next song
                        _progress.value = 0f
                        
                        exoPlayer?.seekToNextMediaItem()
                        Log.d(TAG, "ExoPlayer skipToNext() called")
                        
                        // Update the current song based on the new media item index
                        updateCurrentSongAfterNavigation()
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
                        // Reset progress to 0 when skipping to previous song
                        _progress.value = 0f
                        
                        exoPlayer?.seekToPreviousMediaItem()
                        Log.d(TAG, "ExoPlayer skipToPrevious() called")
                        
                        // Update the current song based on the new media item index
                        updateCurrentSongAfterNavigation()
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
    
    /**
     * Updates the current song after navigation (next/previous)
     * This ensures the current song is correctly updated after shuffle changes
     */
    private suspend fun updateCurrentSongAfterNavigation() {
        val currentIndex = exoPlayer?.currentMediaItemIndex ?: 0
        val currentMediaItem = exoPlayer?.currentMediaItem
        
        if (currentMediaItem != null) {
            val mediaId = currentMediaItem.mediaId
            Log.d(TAG, "After navigation: currentIndex=$currentIndex, mediaId=$mediaId")
            
            // Reset progress to 0 when navigating to a new song
            _progress.value = 0f
            
            // Find the song in the current queue with this media ID
            val song = currentQueueInternal.find { it.id == mediaId }
            if (song != null) {
                _currentSong.value = song
                Log.d(TAG, "Updated current song to: ${song.title}")
                
                // Add to recently played
                try {
                    musicRepository.addToRecentlyPlayed(song)
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding song to recently played", e)
                }
            } else {
                Log.d(TAG, "Could not find song with mediaId=$mediaId in the queue")
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
                    
                    // Validate index
                    if (index < 0 || index >= currentQueueInternal.size) {
                        Log.e(TAG, "Invalid queue index: $index, queue size: ${currentQueueInternal.size}")
                        return@withContext
                    }
                    
                    // Get the song at the requested index from our internal queue
                    val songToPlay = currentQueueInternal[index]
                    val songId = songToPlay.id
                    
                    Log.d(TAG, "Looking for song ID $songId in ExoPlayer's media items")
                    
                    // Find the correct index in ExoPlayer's media items by matching the song ID
                    var exoPlayerIndex = -1
                    val mediaItemCount = exoPlayer?.mediaItemCount ?: 0
                    
                    for (i in 0 until mediaItemCount) {
                        val mediaItem = exoPlayer?.getMediaItemAt(i)
                        if (mediaItem?.mediaId == songId) {
                            exoPlayerIndex = i
                            break
                        }
                    }
                    
                    if (exoPlayerIndex >= 0) {
                        Log.d(TAG, "Found song ID $songId at ExoPlayer index $exoPlayerIndex")
                        
                        // Reset progress to 0 when playing a new queue item
                        _progress.value = 0f
                        
                        // Seek to the correct index in ExoPlayer
                        exoPlayer?.seekTo(exoPlayerIndex, 0)
                        
                        // Ensure playback starts immediately
                        exoPlayer?.playWhenReady = true
                        exoPlayer?.play()
                        
                        // Start progress updates
                        startProgressUpdates()
                        
                        // Update the current song
                        _currentSong.value = songToPlay
                    } else {
                        Log.e(TAG, "Could not find song ID $songId in ExoPlayer's media items")
                        
                        // Fallback to using the provided index directly if we can't find the song ID
                        if (index < mediaItemCount) {
                            Log.d(TAG, "Falling back to direct index $index")
                            
                            // Reset progress to 0 when playing a new queue item
                            _progress.value = 0f
                            
                            exoPlayer?.seekTo(index, 0)
                            exoPlayer?.playWhenReady = true
                            exoPlayer?.play()
                            
                            // Start progress updates
                            startProgressUpdates()
                            
                            // Update the current song
                            _currentSong.value = songToPlay
                            Log.d(TAG, "Updated current song to: ${songToPlay.title} (using fallback)")
                        } else {
                            Log.e(TAG, "Index $index is out of bounds for ExoPlayer media items (count: $mediaItemCount)")
                        }
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
        
        // Update duration after prepare
        _duration.value = exoPlayer?.duration ?: 0L
        
        // Restore playback state using playWhenReady for reliability
        Log.d(TAG, "Setting playWhenReady=$wasPlaying")
        exoPlayer?.playWhenReady = wasPlaying
        
        // Additional check to ensure playback is restored
        if (wasPlaying && exoPlayer?.isPlaying == false) {
            Log.d(TAG, "Playback not restored with playWhenReady, forcing play()")
            exoPlayer?.play()
        }
    }

    // Add this debug method to log the current song details
    private fun logCurrentSongDetails() {
        val song = _currentSong.value
        if (song != null) {
            Log.d(TAG, "Current song details: " +
                "id=${song.id}, " +
                "title=${song.title}, " +
                "artist=${song.artist}, " +
                "isDownloaded=${song.isDownloaded}, " +
                "localFilePath=${song.localFilePath}"
            )
        } else {
            Log.d(TAG, "No current song set")
        }
    }

    // This method should be called when the service is destroyed
    fun onDestroy() {
        Log.d(TAG, "onDestroy called, releasing resources")
        
        // Reset all state flows to ensure clean state
        resetStateFlows()
        
        releasePlayer()
        coroutineScope.cancel()
    }
    
    /**
     * Resets all state flows to ensure clean state when service is destroyed
     */
    private fun resetStateFlows() {
        Log.d(TAG, "Resetting all state flows")
        
        // Cancel progress updates
        progressJob?.cancel()
        
        // Reset all state flows to initial values
        _currentSong.value = null
        _isPlaying.value = false
        _progress.value = 0f
        _error.value = null
        _repeatMode.value = RepeatMode.NONE
        _duration.value = 0L
        _currentQueue.value = emptyList()
        currentQueueInternal = emptyList()
        
        Log.d(TAG, "All state flows reset to initial values")
    }
} 