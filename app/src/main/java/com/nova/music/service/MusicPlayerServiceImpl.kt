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

    private var progressJob: Job? = null
    
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
                
                // Ensure we have a fresh ExoPlayer instance on the main thread
                withContext(Dispatchers.Main) {
                    ensurePlayerCreated()
                }
                
                // Determine if this is a local or YouTube song
                if (song.id.startsWith("yt_")) {
                    // This is a YouTube song, stream from backend
                    val videoId = song.id.removePrefix("yt_")
                    Log.d(TAG, "Streaming YouTube song with ID: $videoId")
                    
                    try {
                        // Now the backend directly returns the audio stream via redirect
                        val baseUrl = "http://192.168.29.154:8000"
                        val streamUrl = "$baseUrl/yt_audio?video_id=$videoId"
                        Log.d(TAG, "Attempting to stream from URL: $streamUrl")
                        
                        val mediaItem = MediaItem.fromUri(streamUrl)
                        
                        withContext(Dispatchers.Main) {
                            Log.d(TAG, "Setting media item on main thread")
                            exoPlayer?.setMediaItem(mediaItem)
                            exoPlayer?.prepare()
                            exoPlayer?.playWhenReady = true
                            Log.d(TAG, "ExoPlayer prepared and playWhenReady set to true")
                        }
                        
                        // Setup error handling and fallback
                        withContext(Dispatchers.Main) {
                            exoPlayer?.addListener(object : Player.Listener {
                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                    Log.e(TAG, "ExoPlayer error: ${error.message}", error)
                                    _error.value = "Error playing song: ${error.message}"
                                    
                                    // Try fallback endpoint
                                    coroutineScope.launch {
                                        try {
                                            Log.d(TAG, "Trying fallback endpoint for video ID: $videoId")
                                            val fallbackUrl = "$baseUrl/audio_fallback?video_id=$videoId"
                                            
                                            val fallbackMediaItem = MediaItem.fromUri(fallbackUrl)
                                            
                                            withContext(Dispatchers.Main) {
                                                // Check if player still exists
                                                if (exoPlayer == null) {
                                                    ensurePlayerCreated()
                                                }
                                                exoPlayer?.setMediaItem(fallbackMediaItem)
                                                exoPlayer?.prepare()
                                                exoPlayer?.playWhenReady = true
                                                Log.d(TAG, "Fallback ExoPlayer prepared and started")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Fallback streaming also failed", e)
                                            _error.value = "Error playing song: ${e.message}"
                                        }
                                    }
                                }
                            })
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error streaming audio", e)
                        _error.value = "Error streaming audio: ${e.message}"
                    }
                } else {
                    // This is a local song
                    Log.d(TAG, "Playing local song from: ${song.albumArt}")
                    val mediaItem = MediaItem.fromUri(Uri.parse(song.albumArt))
                    
                    withContext(Dispatchers.Main) {
                        exoPlayer?.setMediaItem(mediaItem)
                        exoPlayer?.prepare()
                        exoPlayer?.playWhenReady = true
                        Log.d(TAG, "Local song ExoPlayer prepared and started")
                    }
                }
                
                _currentSong.value = song
                
                // Add to recently played - but first ensure the song exists in the database
                try {
                    // We're already in an IO context, so we can call this directly
                    musicRepository.addToRecentlyPlayed(song)
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding song to recently played", e)
                    // Don't fail the whole playback if this fails
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in playSong", e)
                _error.value = "Error playing song: ${e.message}"
            }
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
                } else 0f
                
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
                        exoPlayer?.play()
                        Log.d(TAG, "ExoPlayer resume/play() called")
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
                    exoPlayer?.shuffleModeEnabled = enabled
                    Log.d(TAG, "ExoPlayer shuffle mode set to $enabled")
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

    // This method should be called when the service is destroyed
    fun onDestroy() {
        Log.d(TAG, "onDestroy called, releasing resources")
        releasePlayer()
        coroutineScope.cancel()
    }
} 