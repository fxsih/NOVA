package com.nova.music.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.music.data.model.Song
import com.nova.music.data.repository.MusicRepository
import com.nova.music.service.IMusicPlayerService
import com.nova.music.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import javax.inject.Inject
import android.util.Log
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.nova.music.NovaApplication
import android.content.Intent
import android.os.Build
import com.nova.music.service.MusicPlayerService
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nova.music.service.NovaSessionManager

/**
 * Repeat modes for music playback.
 * The order of cycling is: NONE -> ALL -> ONE -> NONE
 * 
 * NONE: No repeat (play through once)
 * ALL: Repeat the entire playlist infinitely
 * ONE: Repeat the current song once
 */
enum class RepeatMode {
    NONE, ONE, ALL
}

/**
 * Sleep timer options
 */
enum class SleepTimerOption {
    OFF,
    TEN_MINUTES,
    FIFTEEN_MINUTES,
    THIRTY_MINUTES,
    ONE_HOUR,
    END_OF_SONG
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val musicPlayerService: IMusicPlayerService,
    private val preferenceManager: PreferenceManager,
    private val okHttpClient: OkHttpClient, // Inject OkHttpClient from DI
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    // Use the service's state flows directly
    val currentSong = musicPlayerService.currentSong
    val isPlaying = musicPlayerService.isPlaying
    val progress = musicPlayerService.progress
    val error = musicPlayerService.error
    val repeatMode = musicPlayerService.repeatMode
    val duration = musicPlayerService.duration
    val serviceQueue = musicPlayerService.currentQueue
    
    // Track loading state to prevent duplicate loads
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Track whether full player should be shown
    private val _shouldShowFullPlayer = MutableStateFlow(false)
    val shouldShowFullPlayer: StateFlow<Boolean> = _shouldShowFullPlayer.asStateFlow()
    
    // Add a flag to track if we're already in the player screen
    private val _isInPlayerScreen = MutableStateFlow(false)
    val isInPlayerScreen: StateFlow<Boolean> = _isInPlayerScreen.asStateFlow()
    
    // Track which playlist started the current playback
    private val _currentPlaylistId = MutableStateFlow<String?>(null)
    val currentPlaylistId: StateFlow<String?> = _currentPlaylistId.asStateFlow()
    
    // Track current playlist songs for next/previous functionality
    private val _currentPlaylistSongs = MutableStateFlow<List<Song>>(emptyList())
    val currentPlaylistSongs: StateFlow<List<Song>> = _currentPlaylistSongs.asStateFlow()
    
    // Track download state
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()
    
    // Track download progress
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    
    // Track which songs have been downloaded
    private val downloadedSongIds = mutableSetOf<String>()
    
    // Track if the current song is downloaded
    private val _isCurrentSongDownloaded = MutableStateFlow(false)
    val isCurrentSongDownloaded: StateFlow<Boolean> = _isCurrentSongDownloaded.asStateFlow()
    
    // Track sleep timer state
    private val _sleepTimerActive = MutableStateFlow(false)
    val sleepTimerActive: StateFlow<Boolean> = _sleepTimerActive.asStateFlow()
    
    // Track sleep timer remaining time
    private val _sleepTimerRemaining = MutableStateFlow<Long>(0)
    val sleepTimerRemaining: StateFlow<Long> = _sleepTimerRemaining.asStateFlow()
    
    // Track current sleep timer option
    private val _sleepTimerOption = MutableStateFlow(SleepTimerOption.OFF)
    val sleepTimerOption: StateFlow<SleepTimerOption> = _sleepTimerOption.asStateFlow()
    
    // Track current loading job to cancel if needed
    private var loadingJob: Job? = null
    
    // Track sleep timer job
    private var sleepTimerJob: Job? = null
    
    // Track current download job to cancel if needed
    private var downloadJob: Job? = null

    /**
     * Checks if a song file actually exists in the user-specific downloads directory
     * Checks both the stored local file path and the generated file name
     */
    private fun isSongFileExists(context: Context, song: Song): Boolean {
        // First check if the song has a stored local file path and if that file exists
        if (song.localFilePath != null) {
            val storedFile = File(song.localFilePath)
            if (storedFile.exists() && storedFile.length() > 0) {
                return true
            }
        }
        
        // If the stored path doesn't exist, check with the generated file name in user-specific folder
        val sanitizedTitle = song.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val sanitizedArtist = song.artist.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "${sanitizedArtist} - ${sanitizedTitle}.mp3"
        
        // Get current user ID for user-specific folder
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid ?: "anonymous"
        
        // Check in user-specific downloads directory
        val appDownloadsDir = File(context.filesDir, "downloads/$userId")
        val file = File(appDownloadsDir, fileName)
        
        return file.exists() && file.length() > 0
    }

    /**
     * Verifies all downloaded songs and removes entries for files that no longer exist
     */
    fun verifyDownloadedSongs(context: Context) {
        viewModelScope.launch {
            val songsToRemove = mutableListOf<String>()
            
            // Get all songs that are marked as downloaded
            val downloadedIds = preferenceManager.getDownloadedSongIds()
            
            Log.d("PlayerViewModel", "Verifying ${downloadedIds.size} downloaded songs")
            
            // Check each song to see if the file exists
            for (songId in downloadedIds) {
                // Get the song details
                val song = musicRepository.getSongById(songId)
                if (song != null) {
                    val exists = isSongFileExists(context, song)
                    Log.d("PlayerViewModel", "Song ${song.title}: exists=$exists, path=${song.localFilePath}")
                    
                    if (!exists) {
                        songsToRemove.add(songId)
                        
                        // Also update the database to mark as not downloaded
                        musicRepository.markSongAsNotDownloaded(songId)
                    }
                } else {
                    // If song doesn't exist in repository, remove it from downloads
                    songsToRemove.add(songId)
                    Log.d("PlayerViewModel", "Song with ID $songId not found in repository")
                }
            }
            
            // Remove songs that don't have files
            for (songId in songsToRemove) {
                downloadedSongIds.remove(songId)
                preferenceManager.removeDownloadedSongId(songId)
            }
            
            // Also update the database to keep everything in sync
            for (songId in songsToRemove) {
                musicRepository.markSongAsNotDownloaded(songId)
            }
            
            // Update current song download state
            currentSong.value?.id?.let { id ->
                _isCurrentSongDownloaded.value = downloadedSongIds.contains(id) && 
                    currentSong.value?.let { isSongFileExists(context, it) } ?: false
            }
            
            if (songsToRemove.isNotEmpty()) {
                Log.i("PlayerViewModel", "Removed ${songsToRemove.size} songs from download tracking that no longer exist")
                Toast.makeText(context, "Fixed ${songsToRemove.size} incorrectly tracked downloads", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Update the init block to verify download status when a song changes
    init {
        // Load initial song if ID is provided
        savedStateHandle.get<String>("songId")?.let { songId ->
            loadSong(songId)
        }
        
        // Don't automatically show full player on startup - only when user plays a song
        _shouldShowFullPlayer.value = false
        
        // Listen to changes in the current song to ensure playlist context is maintained
        viewModelScope.launch {
            currentSong.collect { song ->
                if (song != null && _currentPlaylistSongs.value.isEmpty()) {
                    // If we have a song but no playlist, at least add the current song to the playlist
                    _currentPlaylistSongs.value = listOf(song)
                    println("DEBUG: Auto-updated playlist with current song: ${song.title}")
                }
                
                // Update the downloaded state for the current song without context check
                // We'll do a full verification when the app starts and when needed
                _isCurrentSongDownloaded.value = song?.id?.let { id -> downloadedSongIds.contains(id) } ?: false
                
                // If sleep timer is set to END_OF_SONG and song changes, stop playback at the end
                // This is handled in the service now
            }
        }
        
        // Load downloaded song IDs from preferences
        viewModelScope.launch {
            val savedDownloadedIds = preferenceManager.getDownloadedSongIds()
            if (savedDownloadedIds.isNotEmpty()) {
                downloadedSongIds.addAll(savedDownloadedIds)
                
                // Update current song download state immediately
                updateCurrentSongDownloadState()
            }
        }
    }

    fun loadSong(song: Song, playlistId: String? = null, playlistSongs: List<Song>? = null) {
        Log.d("PlayerViewModel", "loadSong called with playlistId: $playlistId, playlistSongs size: ${playlistSongs?.size}, ids: ${playlistSongs?.map { it.id }}")
        // If already loading this song, don't reload
        if (_isLoading.value && currentSong.value?.id == song.id) {
            // If the song is already loaded but not playing, just toggle play
            if (!isPlaying.value) {
                togglePlayPause()
            }
            return
        }
        
        // Debug logging
        println("DEBUG: loadSong(Song) called with playlistId: $playlistId, playlistSongs size: ${playlistSongs?.size}")
        
        // Log more details for Downloads playlist
        if (playlistId == "downloads") {
            println("DEBUG: Loading song from Downloads playlist")
            println("DEBUG: Song: ${song.title} (${song.id})")
            println("DEBUG: isDownloaded: ${song.isDownloaded}, localFilePath: ${song.localFilePath}")
            playlistSongs?.forEachIndexed { index, s ->
                println("DEBUG: Downloads song $index: ${s.title} (${s.id}), isDownloaded: ${s.isDownloaded}")
            }
        }
        
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                // Start the service to ensure it's running
                startMusicService(NovaApplication.instance)
                
                // Set the current playlist ID and songs
                _currentPlaylistId.value = playlistId
                
                // Always ensure we have at least the current song in the playlist
                if (playlistSongs != null && playlistSongs.isNotEmpty()) {
                    println("DEBUG: Setting _currentPlaylistSongs with ${playlistSongs.size} songs")
                    _currentPlaylistSongs.value = playlistSongs
                    
                    // Use the new method to set the entire playlist queue
                    try {
                        musicPlayerService.setPlaylistQueue(playlistSongs, song.id)
                        // Ensure playback starts immediately
                        musicPlayerService.resume()
                        // Force play to ensure it starts
                        musicPlayerService.play()
                    } catch (e: Exception) {
                        println("ERROR: Failed to set playlist queue: ${e.message}")
                        // Fallback to just playing the single song
                        musicPlayerService.playSong(song)
                        // Ensure playback starts
                        musicPlayerService.resume()
                        // Force play to ensure it starts
                        musicPlayerService.play()
                    }
                } else {
                    println("DEBUG: Setting _currentPlaylistSongs with just the current song")
                    _currentPlaylistSongs.value = listOf(song)
                    
                    // Just play a single song
                    musicPlayerService.playSong(song)
                
                    musicPlayerService.resume()
                    // Force play to ensure it starts
                    musicPlayerService.play()
                }
                
                // Check if we should show the full player
                if (!preferenceManager.hasFullPlayerBeenShownInSession()) {
                    _shouldShowFullPlayer.value = true
                    preferenceManager.setFullPlayerShownInSession()
                } else {
                    _shouldShowFullPlayer.value = false
                }
                // When saving playlist state:
                if (playlistId != null && playlistSongs != null && playlistSongs.isNotEmpty()) {
                    preferenceManager.setLastPlayedPlaylist(playlistId, playlistSongs.map { it.id })
                    Log.d("PlayerViewModel", "Saved playlist: $playlistId, songIds: ${playlistSongs.map { it.id }}")
                }
            } catch (e: Exception) {
                println("ERROR: Failed to load song: ${e.message}")
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSong(songId: String, playlistId: String? = null, playlistSongs: List<Song>? = null) {
        // If already loading this song, don't reload
        if (_isLoading.value && currentSong.value?.id == songId) {
            // If the song is already loaded but not playing, just toggle play
            if (!isPlaying.value) {
                togglePlayPause()
            }
            return
        }
        
        // Debug logging
        println("DEBUG: loadSong(String) called with playlistId: $playlistId, playlistSongs size: ${playlistSongs?.size}")
        
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                // Start the service to ensure it's running
                startMusicService(NovaApplication.instance)
                
                musicRepository.getAllSongs().collect { songs ->
                    val song = songs.find { it.id == songId }
                    if (song != null) {
                        // Trigger critical prefetch for immediate playback
                        if (song.id.startsWith("yt_")) {
                            val videoId = song.id.removePrefix("yt_")
                            musicRepository.criticalPrefetch(listOf(videoId))
                        }
                        
                        // Set the current playlist ID and songs
                        _currentPlaylistId.value = playlistId
                        
                        // Always ensure we have at least the current song in the playlist
                        if (playlistSongs != null && playlistSongs.isNotEmpty()) {
                            println("DEBUG: Setting _currentPlaylistSongs with ${playlistSongs.size} songs")
                            _currentPlaylistSongs.value = playlistSongs
                            
                            // Reset player state for new song
                            musicPlayerService.resetPlayerForNewSong()
                            
                            // Use the new method to set the entire playlist queue
                            try {
                                musicPlayerService.setPlaylistQueue(playlistSongs, song.id)
                                // Ensure playback starts immediately
                                musicPlayerService.resume()
                                // Force play to ensure it starts
                                musicPlayerService.play()
                            } catch (e: Exception) {
                                println("ERROR: Failed to set playlist queue: ${e.message}")
                                // Fallback to just playing the single song
                                musicPlayerService.resetPlayerForNewSong()
                                musicPlayerService.playSong(song)
                                // Ensure playback starts immediately
                                musicPlayerService.resume()
                                // Force play to ensure it starts
                                musicPlayerService.play()
                            }
                        } else {
                            println("DEBUG: Setting _currentPlaylistSongs with just the current song")
                            _currentPlaylistSongs.value = listOf(song)
                            
                            // Reset player state for new song
                            musicPlayerService.resetPlayerForNewSong()
                            
                            // Just play a single song
                            musicPlayerService.playSong(song)
                            // Ensure playback starts immediately
                            musicPlayerService.resume()
                            // Force play to ensure it starts
                            musicPlayerService.play()
                        }
                        
                        // Check if we should show the full player
                        if (!preferenceManager.hasFullPlayerBeenShownInSession()) {
                            _shouldShowFullPlayer.value = true
                            preferenceManager.setFullPlayerShownInSession()
                        } else {
                            _shouldShowFullPlayer.value = false
                        }
                    } else {
                        println("ERROR: Song with ID $songId not found")
                    }
                }
            } catch (e: Exception) {
                println("ERROR: Failed to load song: ${e.message}")
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Only call togglePlayPause, resume, or pause on explicit user actions
    fun togglePlayPause() {
        viewModelScope.launch {
            if (isPlaying.value) {
                musicPlayerService.pause()
            } else {
                musicPlayerService.resume()
            }
        }
    }

    /**
     * Shuffles the current queue.
     * This method doesn't toggle shuffle mode but just reorders the queue.
     */
    fun shuffleQueue() {
        viewModelScope.launch {
            try {
                // Call the service to shuffle the queue
                musicPlayerService.shuffleQueue()
                
                // No need to update isShuffle state since we're not toggling shuffle mode
                
                Log.d("PlayerViewModel", "Queue shuffled successfully")
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Failed to shuffle queue: ${e.message}", e)
            }
        }
    }

    /**
     * Legacy method for backward compatibility.
     * Now just calls shuffleQueue() regardless of the enabled parameter.
     */
    fun setShuffleMode(enabled: Boolean) {
        // Just shuffle the queue regardless of the enabled parameter
        shuffleQueue()
    }

    /**
     * Legacy method for backward compatibility.
     * Now just calls shuffleQueue().
     */
    fun toggleShuffleMode() {
        shuffleQueue()
    }

    /**
     * Legacy method for backward compatibility.
     * Now just calls shuffleQueue().
     */
    fun toggleShuffle() {
        shuffleQueue()
    }

    /**
     * Toggle repeat mode for the full player screen.
     * Cycles through: NONE -> ONE -> ALL -> NONE
     * Changed to prioritize song repeat (ONE) before playlist repeat (ALL)
     */
    fun toggleRepeatMode() {
        viewModelScope.launch {
            val nextMode = when (repeatMode.value) {
                RepeatMode.NONE -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.NONE
            }
            musicPlayerService.setRepeatMode(nextMode)
        }
    }
    
    /**
     * Toggle repeat mode for the playlist screen.
     * For playlist screen, we only toggle between NONE and ALL
     * (no single song repeat in playlist view)
     */
    fun togglePlaylistRepeatMode() {
        viewModelScope.launch {
            val nextMode = if (repeatMode.value == RepeatMode.NONE) {
                RepeatMode.ALL
            } else {
                RepeatMode.NONE
            }
            musicPlayerService.setRepeatMode(nextMode)
        }
    }

    fun skipToNext() {
        viewModelScope.launch {
            // Set the flag to prevent navigation to a new player screen
            _isInPlayerScreen.value = true
            // Simply use the service's skipToNext method since we now maintain a queue
                musicPlayerService.skipToNext()
        }
    }

    fun skipToPrevious() {
        viewModelScope.launch {
            // Set the flag to prevent navigation to a new player screen
            _isInPlayerScreen.value = true
            // Simply use the service's skipToPrevious method since we now maintain a queue
                musicPlayerService.skipToPrevious()
        }
    }

    fun seekTo(position: Float) {
        viewModelScope.launch {
            // Allow seeking even if duration is not yet available
            // The service will handle seeking properly
            val durationMs = duration.value
            val positionMs = if (durationMs > 0) {
                (position * durationMs).toLong()
            } else {
                // If duration is not available, use a reasonable default
                // or just pass the position as a percentage (0-1)
                (position * 1000).toLong() // 1 second as fallback
            }
            musicPlayerService.seekTo(positionMs)
        }
    }

    fun addToPlaylist(song: Song, playlistId: String) {
        viewModelScope.launch {
            musicRepository.addSongToPlaylist(song, playlistId)
        }
    }

    fun stopPlayback() {
        viewModelScope.launch {
            musicPlayerService.stop()
        }
    }
    
    fun clearCurrentSong() {
        viewModelScope.launch {
            musicPlayerService.clearCurrentSong()
        }
    }
    
    fun clearPlaybackState() {
        viewModelScope.launch {
            musicPlayerService.clearCurrentSong()
            // Optionally clear any local state, queues, etc.
        }
    }
    
    /**
     * Restores the player state from the actual service
     */
    fun restorePlayerState(context: Context) {
        viewModelScope.launch {
            try {
                Log.d("PlayerViewModel", "=== RESTORE PLAYER STATE STARTED ===")
                
                // Check if service was recently killed
                val wasServiceKilled = NovaSessionManager.wasSwipeKilled(context)
                Log.d("PlayerViewModel", "Service was killed: $wasServiceKilled")
                
                if (wasServiceKilled) {
                    Log.d("PlayerViewModel", "Service was killed, clearing state and resetting flag")
                    // Clear all state since service was killed
                    musicPlayerService.clearCurrentSong()
                    _currentPlaylistId.value = null
                    _currentPlaylistSongs.value = emptyList()
                    
                    // Reset the killed flag
                    NovaSessionManager.resetSwipeKilledFlag(context)
                    
                    Log.d("PlayerViewModel", "=== RESTORE PLAYER STATE COMPLETED (CLEARED DUE TO KILL) ===")
                    return@launch
                }
                
                // Check if service is running
                val serviceRunning = isServiceRunning(context)
                Log.d("PlayerViewModel", "Service running: $serviceRunning")
                
                // Ensure the service is running
                ensureServiceRunning(context)
                
                // Check if the service has a current song and is playing
                val serviceCurrentSong = musicPlayerService.currentSong.value
                val serviceIsPlaying = musicPlayerService.isPlaying.value
                val serviceQueue = musicPlayerService.currentQueue.value
                
                Log.d("PlayerViewModel", "Service state - currentSong: ${serviceCurrentSong?.title}, isPlaying: $serviceIsPlaying, queueSize: ${serviceQueue.size}")
                
                // If the service has a current song, restore the state
                if (serviceCurrentSong != null) {
                    Log.d("PlayerViewModel", "Restoring current song: ${serviceCurrentSong.title}")
                    
                    // Update the current playlist songs if we have a queue
                    if (serviceQueue.isNotEmpty()) {
                        _currentPlaylistSongs.value = serviceQueue
                        Log.d("PlayerViewModel", "Restored queue with ${serviceQueue.size} songs")
                    }
                    
                    // Don't clear the current song - let the service maintain it
                    // The service should already have the correct state
                } else {
                    Log.d("PlayerViewModel", "No current song in service, clearing state")
                    // Only clear if service has no current song
                    musicPlayerService.clearCurrentSong()
                    _currentPlaylistId.value = null
                    _currentPlaylistSongs.value = emptyList()
                }
                
                Log.d("PlayerViewModel", "=== RESTORE PLAYER STATE COMPLETED ===")
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error restoring player state", e)
                // On error, clear state to be safe
                musicPlayerService.clearCurrentSong()
                _currentPlaylistId.value = null
                _currentPlaylistSongs.value = emptyList()
            }
        }
    }
    
    /**
     * Ensures the music service is running
     */
    private fun ensureServiceRunning(context: Context) {
        try {
            Log.d("PlayerViewModel", "Ensuring music service is running")
            
            val serviceIntent = Intent(context, MusicPlayerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
                Log.d("PlayerViewModel", "Started foreground service")
            } else {
                context.startService(serviceIntent)
                Log.d("PlayerViewModel", "Started service")
            }
            
            // Give the service a moment to start
            kotlinx.coroutines.runBlocking {
                delay(500)
            }
            
            Log.d("PlayerViewModel", "Music service should be running now")
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error starting music service", e)
        }
    }
    
    /**
     * Checks if the music service is currently running
     */
    private fun isServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningServices = manager.getRunningServices(Integer.MAX_VALUE)
        return runningServices.any { it.service.className == MusicPlayerService::class.java.name }
    }
    
    fun resetFullPlayerShownFlag() {
        preferenceManager.resetFullPlayerShownFlag()
        _shouldShowFullPlayer.value = false
    }
    
    // Add a method to set the isInPlayerScreen flag
    fun setInPlayerScreen(inPlayerScreen: Boolean) {
        _isInPlayerScreen.value = inPlayerScreen
    }
    
    // Queue reordering methods
    fun moveSongUp(songId: String) {
        viewModelScope.launch {
            musicPlayerService.moveSongUp(songId)
        }
    }
    
    fun moveSongDown(songId: String) {
        viewModelScope.launch {
            musicPlayerService.moveSongDown(songId)
        }
    }
    
    fun reorderQueue(songs: List<Song>) {
        viewModelScope.launch {
            try {
                // Let the service handle playback state restoration
                musicPlayerService.reorderQueue(songs)
            } catch (e: Exception) {
                println("ERROR: Failed to reorder queue: ${e.message}")
            }
        }
    }
    
    fun removeFromQueue(songId: String) {
        viewModelScope.launch {
            musicPlayerService.removeFromQueue(songId)
        }
    }

    fun getCurrentPlaylistSongs(): List<Song> {
        val songs = _currentPlaylistSongs.value
        val current = currentSong.value
        val serviceQueueValue = serviceQueue.value
        
        // Log the current state for debugging
        println("DEBUG: getCurrentPlaylistSongs - currentPlaylistSongs: ${songs.size}, serviceQueue: ${serviceQueueValue.size}, currentSong: ${current?.title}")
        
        // First check if the service queue has songs (this is the most up-to-date source)
        if (serviceQueueValue.isNotEmpty()) {
            return serviceQueueValue
        }
        
        // If service queue is empty, fall back to our local playlist
        // If we have a current song but no playlist songs, or the current song isn't in the playlist,
        // return a list with at least the current song
        return if (songs.isEmpty() && current != null) {
            listOf(current)
        } else if (current != null && songs.none { it.id == current.id }) {
            // Current song not in playlist, add it at the beginning
            listOf(current) + songs
        } else if (songs.isNotEmpty()) {
            // We have playlist songs, use them
            songs
        } else {
            // Fallback to empty list
            emptyList()
        }
    }

    /**
     * Loads and plays a song from the current queue while maintaining the queue context.
     * This ensures that when a user selects a song from the queue, it plays within the
     * context of the current queue rather than as a standalone song.
     */
    fun loadQueueSongInContext(song: Song) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Get the current queue from the service
                val currentQueueSongs = serviceQueue.value
                
                if (currentQueueSongs.isNotEmpty()) {
                    // Find the index of the selected song in the queue
                    val songIndex = currentQueueSongs.indexOfFirst { it.id == song.id }
                    
                    if (songIndex >= 0) {
                        Log.d("PlayerViewModel", "Playing queue item at index $songIndex: ${song.title}")
                        
                        // Reset player state for new song
                        musicPlayerService.resetPlayerForNewSong()
                        
                        // Tell the service to play this specific song from the current queue
                        musicPlayerService.playQueueItemAt(songIndex)
                        
                        // Set the flag to prevent navigation to a new player screen
                        _isInPlayerScreen.value = true
                    } else {
                        Log.e("PlayerViewModel", "Song not found in queue, falling back to normal loading")
                        // If the song isn't in the queue (unusual case), fall back to normal loading
                        musicPlayerService.resetPlayerForNewSong()
                        musicPlayerService.playSong(song)
                    }
                } else {
                    Log.d("PlayerViewModel", "Queue is empty, playing song directly")
                    // If queue is empty, just play the song
                    musicPlayerService.resetPlayerForNewSong()
                    musicPlayerService.playSong(song)
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Failed to load queue song: ${e.message}")
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Downloads the current song to the device with optimized performance.
     * 
     * @param context The application context needed for file operations
     */
    fun downloadCurrentSong(context: Context) {
        val song = currentSong.value ?: return
        
        // First, verify if the song is actually downloaded
        if (downloadedSongIds.contains(song.id)) {
            // If it's marked as downloaded, verify the file actually exists
            if (isSongFileExists(context, song)) {
                Toast.makeText(context, "Song already downloaded", Toast.LENGTH_SHORT).show()
                return
            } else {
                // Song is marked as downloaded but file doesn't exist - fix tracking
                Log.d("PlayerViewModel", "Song ${song.title} marked as downloaded but file not found - fixing tracking")
                downloadedSongIds.remove(song.id)
                _isCurrentSongDownloaded.value = false
                preferenceManager.removeDownloadedSongId(song.id)
                // Use viewModelScope to call the suspend function
                viewModelScope.launch {
                    musicRepository.markSongAsNotDownloaded(song.id)
                }
                // Continue with download
            }
        } else {
            // Not marked as downloaded, but check if file exists anyway
            if (isSongFileExists(context, song)) {
                // File exists but not tracked - update tracking
                downloadedSongIds.add(song.id)
                _isCurrentSongDownloaded.value = true
                preferenceManager.addDownloadedSongId(song.id)
                
                // Get the file path to update in the database
                val sanitizedTitle = song.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val sanitizedArtist = song.artist.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val fileName = "${sanitizedArtist} - ${sanitizedTitle}.mp3"
                
                // Get current user ID for user-specific folder
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val userId = currentUser?.uid ?: "anonymous"
                val appDownloadsDir = File(context.filesDir, "downloads/$userId")
                val file = File(appDownloadsDir, fileName)
                
                // Update the database using viewModelScope
                viewModelScope.launch {
                    musicRepository.markSongAsDownloaded(song, file.absolutePath)
                }
                
                Toast.makeText(context, "Song found in user downloads and added to library", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        // Don't start a new download if one is already in progress
        if (_isDownloading.value) {
            Toast.makeText(context, "Download already in progress", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Cancel any existing download job
        downloadJob?.cancel()
        
        // Use application scope instead of viewModelScope to ensure download continues in background
        downloadJob = NovaApplication.applicationScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = 0f
            
            try {
                // Check if the song ID is from YouTube (starts with "yt_")
                val isYouTubeSong = song.id.startsWith("yt_")
                val videoId = if (isYouTubeSong) song.id.removePrefix("yt_") else song.id
                
                // Create a sanitized filename
                val sanitizedTitle = song.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val sanitizedArtist = song.artist.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val fileName = "${sanitizedArtist} - ${sanitizedTitle}.mp3"
                
                // Get the URL for downloading
                val downloadUrl = if (isYouTubeSong) {
                    // For YouTube songs, use the optimized download endpoint
                    "${preferenceManager.getApiBaseUrl()}/download_audio?video_id=$videoId"
                } else {
                    // For local songs, use the direct file path
                    song.audioUrl
                }
                
                if (downloadUrl.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download URL not available", Toast.LENGTH_SHORT).show()
                    }
                    _isDownloading.value = false
                    return@launch
                }
                
                // Create user-specific downloads directory
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val userId = currentUser?.uid ?: "anonymous"
                
                // Use app's internal files directory with user-specific subfolder
                val appDownloadsDir = File(context.filesDir, "downloads/$userId")
                if (!appDownloadsDir.exists()) {
                    appDownloadsDir.mkdirs()
                }
                
                Log.d("PlayerViewModel", "Downloading to user-specific folder: ${appDownloadsDir.absolutePath}")
                
                // Create the file in the user-specific downloads directory
                val file = File(appDownloadsDir, fileName)
                
                // Show starting toast
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Starting download: $fileName", Toast.LENGTH_SHORT).show()
                }
                
                // Download the file with optimized buffering
                withContext(Dispatchers.IO) {
                    try {
                        // Create an optimized request with cache control and compression support
                        val request = Request.Builder()
                            .url(downloadUrl)
                            .header("Accept-Encoding", "gzip, deflate")
                            .cacheControl(okhttp3.CacheControl.Builder().noCache().build()) // Bypass cache for downloads
                            .build()
                        
                        // Execute the request
                        okHttpClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                throw IOException("Failed to download: ${response.code}")
                            }
                            
                            val responseBody = response.body
                                ?: throw IOException("Empty response body")
                            
                            // Use Okio for more efficient I/O
                            downloadWithProgress(responseBody, file)
                        }
                        
                        // Verify the file was downloaded successfully
                        if (file.exists() && file.length() > 0) {
                            // Mark song as downloaded in preferences
                            downloadedSongIds.add(song.id)
                            preferenceManager.addDownloadedSongId(song.id)
                            
                            // Also mark as downloaded in the database with the local file path
                            musicRepository.markSongAsDownloaded(song, file.absolutePath)
                            
                            // Update UI state if we're still on the same song
                            if (currentSong.value?.id == song.id) {
                                _isCurrentSongDownloaded.value = true
                        }
                        
                        // Show success toast
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                    "Download complete: $fileName",
                                    Toast.LENGTH_SHORT
                            ).show()
                        }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Download failed: File not created",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (e: IOException) {
                        Log.e("PlayerViewModel", "Error downloading file", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Download failed: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error in download process", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Download error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                _isDownloading.value = false
                _downloadProgress.value = 0f
            }
        }
    }
    
    /**
     * Downloads a file with progress tracking
     */
    private suspend fun downloadWithProgress(responseBody: okhttp3.ResponseBody, file: File) {
        val contentLength = responseBody.contentLength()
        var bytesWritten = 0L
        
        // Use larger buffer size for faster downloads
        val bufferSize = 8192 * 4 // 32KB buffer instead of the default 8KB
        
        try {
            // Use Okio's buffered sink for efficient writing
            file.sink().buffer().use { bufferedSink ->
                // Read the response body in chunks
                responseBody.source().use { source ->
                    val buffer = okio.Buffer()
                    var read: Long
                    
                    // Read until there's no more data
                    while (source.read(buffer, bufferSize.toLong()).also { read = it } != -1L) {
                        // Write the chunk to the file
                        bufferedSink.write(buffer, read)
                        
                        // Update progress
                        bytesWritten += read
                        if (contentLength > 0) {
                            val progress = bytesWritten.toFloat() / contentLength.toFloat()
                            // Update progress on the main thread
                            withContext(Dispatchers.Main) {
                                _downloadProgress.value = progress
                            }
                        }
                    }
                    
                    // Ensure all data is written
                    bufferedSink.flush()
                }
            }
        } catch (e: IOException) {
            Log.e("PlayerViewModel", "Error writing downloaded file", e)
            
            // Delete the partial file if it exists
            if (file.exists()) {
                file.delete()
            }
            
            throw e
        }
    }

    /**
     * Sets a sleep timer with the specified option
     */
    fun setSleepTimer(option: SleepTimerOption) {
        // Cancel any existing timer
        sleepTimerJob?.cancel()
        
        _sleepTimerOption.value = option
        
        when (option) {
            SleepTimerOption.OFF -> {
                _sleepTimerActive.value = false
                _sleepTimerRemaining.value = 0
            }
            SleepTimerOption.TEN_MINUTES -> startTimerWithDuration(10 * 60 * 1000L)
            SleepTimerOption.FIFTEEN_MINUTES -> startTimerWithDuration(15 * 60 * 1000L)
            SleepTimerOption.THIRTY_MINUTES -> startTimerWithDuration(30 * 60 * 1000L)
            SleepTimerOption.ONE_HOUR -> startTimerWithDuration(60 * 60 * 1000L)
            SleepTimerOption.END_OF_SONG -> {
                // For END_OF_SONG, we'll handle this in the currentSong collector
                _sleepTimerActive.value = true
                
                // Calculate remaining time for the current song
                val songDuration = duration.value
                if (songDuration > 0) {
                    val currentPosition = progress.value * songDuration
                    _sleepTimerRemaining.value = (songDuration - currentPosition).toLong()
                    
                    sleepTimerJob = viewModelScope.launch {
                        val timeRemaining = _sleepTimerRemaining.value
                        if (timeRemaining > 0) {
                            delay(timeRemaining)
                            if (isActive) {
                                stopPlayback()
                                _sleepTimerActive.value = false
                                _sleepTimerOption.value = SleepTimerOption.OFF
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Starts a timer with the specified duration in milliseconds
     */
    private fun startTimerWithDuration(durationMs: Long) {
        sleepTimerJob?.cancel() // Cancel previous job before launching new one
        _sleepTimerActive.value = true
        _sleepTimerRemaining.value = durationMs
        sleepTimerJob = viewModelScope.launch {
            val endTime = System.currentTimeMillis() + durationMs
            var lastUpdateSec = -1L
            while (isActive && System.currentTimeMillis() < endTime) {
                val remaining = endTime - System.currentTimeMillis()
                val remainingSec = remaining / 1000L
                if (remainingSec != lastUpdateSec) {
                _sleepTimerRemaining.value = remaining
                    lastUpdateSec = remainingSec
            }
                delay(200) // Check more frequently, but only update on the second
            }
            if (isActive) {
                stopPlayback()
                _sleepTimerActive.value = false
                _sleepTimerOption.value = SleepTimerOption.OFF
            }
        }
    }
    
    /**
     * Cancels the current sleep timer
     */
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerActive.value = false
        _sleepTimerRemaining.value = 0
        _sleepTimerOption.value = SleepTimerOption.OFF
    }
    
    /**
     * Formats the remaining time as a string (MM:SS)
     */
    fun formatSleepTimerRemaining(): String {
        val remaining = _sleepTimerRemaining.value
        
        if (remaining <= 0) {
            return "00:00"
        }
        
        if (_sleepTimerOption.value == SleepTimerOption.END_OF_SONG) {
            return "End of song"
        }
        
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) - TimeUnit.MINUTES.toSeconds(minutes)
        
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Updates the current song's download state by checking if the file exists
     */
    fun updateCurrentSongDownloadState(context: Context? = null) {
        viewModelScope.launch {
            val song = currentSong.value ?: return@launch
            val songId = song.id
            
            // First check if the song is in our tracked downloads
            val isTrackedAsDownloaded = downloadedSongIds.contains(songId)
            
            if (isTrackedAsDownloaded && context != null) {
                // If we have context, verify the file actually exists
                val fileExists = isSongFileExists(context, song)
                
                if (!fileExists) {
                    // File doesn't exist but is tracked as downloaded - update tracking
                    downloadedSongIds.remove(songId)
                    preferenceManager.removeDownloadedSongId(songId)
                    _isCurrentSongDownloaded.value = false
                } else {
                    // File exists and is tracked - confirm download state
                    _isCurrentSongDownloaded.value = true
                }
            } else {
                // If no context provided, just use the tracking data
                _isCurrentSongDownloaded.value = isTrackedAsDownloaded
            }
        }
    }

    /**
     * Gets the user-specific downloads directory path for debugging
     */
    fun getUserDownloadsPath(context: Context): String {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid ?: "anonymous"
        val appDownloadsDir = File(context.filesDir, "downloads/$userId")
        return appDownloadsDir.absolutePath
    }

    /**
     * Deletes a downloaded song from storage and updates the database.
     * 
     * @param context The application context needed for file operations
     * @param songId The ID of the song to delete
     */
    fun deleteDownloadedSong(context: Context, songId: String) {
        viewModelScope.launch {
            try {
                // Get the song from the repository
                val song = musicRepository.getSongById(songId) ?: return@launch
                
                // Check if the song is actually downloaded
                if (!song.isDownloaded || song.localFilePath == null) {
                    Toast.makeText(context, "Song is not downloaded", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Delete the file
                val file = File(song.localFilePath)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        // Update the database
                        musicRepository.markSongAsNotDownloaded(songId)
                        
                        // Update our tracking
                        downloadedSongIds.remove(songId)
                        preferenceManager.removeDownloadedSongId(songId)
                        
                        // Update UI state if this is the current song
                        if (currentSong.value?.id == songId) {
                            _isCurrentSongDownloaded.value = false
                        }
                        
                        Toast.makeText(context, "Song deleted from downloads", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to delete song file", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // File doesn't exist, but update database anyway
                    musicRepository.markSongAsNotDownloaded(songId)
                    downloadedSongIds.remove(songId)
                    preferenceManager.removeDownloadedSongId(songId)
                    
                    // Update UI state if this is the current song
                    if (currentSong.value?.id == songId) {
                        _isCurrentSongDownloaded.value = false
                    }
                    
                    Toast.makeText(context, "Song removed from downloads", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error deleting downloaded song", e)
                Toast.makeText(context, "Error deleting song: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT stop the music service here; let it run unless swiped away from recents
    }

    // Add this method to start the MusicPlayerService
    private fun startMusicService(context: Context) {
        Log.d("PlayerViewModel", "Starting MusicPlayerService")
        val serviceIntent = Intent(context, MusicPlayerService::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    // Update the playSong method to start the service
    fun playSong(song: Song, context: Context) {
        viewModelScope.launch {
            startMusicService(context)
            musicPlayerService.playSong(song)
        }
    }
} 