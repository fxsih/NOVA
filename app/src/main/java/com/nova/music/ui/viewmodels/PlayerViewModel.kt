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
import javax.inject.Inject

enum class RepeatMode {
    NONE, ONE, ALL
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val musicPlayerService: IMusicPlayerService,
    private val preferenceManager: PreferenceManager,
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

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()
    
    // Track loading state to prevent duplicate loads
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Track whether full player should be shown
    private val _shouldShowFullPlayer = MutableStateFlow(false)
    val shouldShowFullPlayer: StateFlow<Boolean> = _shouldShowFullPlayer.asStateFlow()
    
    // Track which playlist started the current playback
    private val _currentPlaylistId = MutableStateFlow<String?>(null)
    val currentPlaylistId: StateFlow<String?> = _currentPlaylistId.asStateFlow()
    
    // Track current playlist songs for next/previous functionality
    private val _currentPlaylistSongs = MutableStateFlow<List<Song>>(emptyList())
    val currentPlaylistSongs: StateFlow<List<Song>> = _currentPlaylistSongs.asStateFlow()
    
    // Track current loading job to cancel if needed
    private var loadingJob: Job? = null

    init {
        // Load initial song if ID is provided
        savedStateHandle.get<String>("songId")?.let { songId ->
            loadSong(songId)
        }
        
        // Check if this is the first song played in this session
        _shouldShowFullPlayer.value = !preferenceManager.hasFullPlayerBeenShownInSession()
        
        // Initialize shuffle mode
        viewModelScope.launch {
            musicPlayerService.setShuffle(_isShuffle.value)
        }
        
        // Listen to changes in the current song to ensure playlist context is maintained
        viewModelScope.launch {
            currentSong.collect { song ->
                if (song != null && _currentPlaylistSongs.value.isEmpty()) {
                    // If we have a song but no playlist, at least add the current song to the playlist
                    _currentPlaylistSongs.value = listOf(song)
                    println("DEBUG: Auto-updated playlist with current song: ${song.title}")
                }
            }
        }
    }

    fun loadSong(song: Song, playlistId: String? = null, playlistSongs: List<Song>? = null) {
        // If already loading this song, don't reload
        if (_isLoading.value && currentSong.value?.id == song.id) return
        
        // Debug logging
        println("DEBUG: loadSong(Song) called with playlistId: $playlistId, playlistSongs size: ${playlistSongs?.size}")
        
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                // Set the current playlist ID and songs
                _currentPlaylistId.value = playlistId
                
                // Always ensure we have at least the current song in the playlist
                if (playlistSongs != null && playlistSongs.isNotEmpty()) {
                    println("DEBUG: Setting _currentPlaylistSongs with ${playlistSongs.size} songs")
                    _currentPlaylistSongs.value = playlistSongs
                    
                    // Use the new method to set the entire playlist queue
                    try {
                        musicPlayerService.setPlaylistQueue(playlistSongs, song.id)
                    } catch (e: Exception) {
                        println("ERROR: Failed to set playlist queue: ${e.message}")
                        // Fallback to just playing the single song
                        musicPlayerService.playSong(song)
                    }
                } else {
                    println("DEBUG: Setting _currentPlaylistSongs with just the current song")
                    _currentPlaylistSongs.value = listOf(song)
                    
                    // Just play a single song
                    musicPlayerService.playSong(song)
                }
                
                // Check if we should show the full player
                if (!preferenceManager.hasFullPlayerBeenShownInSession()) {
                    _shouldShowFullPlayer.value = true
                    preferenceManager.setFullPlayerShownInSession()
                } else {
                    _shouldShowFullPlayer.value = false
                }
                
                // Small delay to ensure UI has time to update
                delay(50)
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
        if (_isLoading.value && currentSong.value?.id == songId) return
        
        // Debug logging
        println("DEBUG: loadSong(String) called with playlistId: $playlistId, playlistSongs size: ${playlistSongs?.size}")
        
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                musicRepository.getAllSongs().collect { songs ->
                    val song = songs.find { it.id == songId }
                    if (song != null) {
                        // Set the current playlist ID and songs
                        _currentPlaylistId.value = playlistId
                        
                        // Always ensure we have at least the current song in the playlist
                        if (playlistSongs != null && playlistSongs.isNotEmpty()) {
                            println("DEBUG: Setting _currentPlaylistSongs with ${playlistSongs.size} songs")
                            _currentPlaylistSongs.value = playlistSongs
                            
                            // Use the new method to set the entire playlist queue
                            try {
                                musicPlayerService.setPlaylistQueue(playlistSongs, song.id)
                            } catch (e: Exception) {
                                println("ERROR: Failed to set playlist queue: ${e.message}")
                                // Fallback to just playing the single song
                                musicPlayerService.playSong(song)
                            }
                        } else {
                            println("DEBUG: Setting _currentPlaylistSongs with just the current song")
                            _currentPlaylistSongs.value = listOf(song)
                            
                            // Just play a single song
                            musicPlayerService.playSong(song)
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
                
                // Small delay to ensure UI has time to update
                delay(50)
            } catch (e: Exception) {
                println("ERROR: Failed to load song: ${e.message}")
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            if (isPlaying.value) {
                musicPlayerService.pause()
            } else {
                musicPlayerService.resume()
            }
        }
    }

    fun toggleShuffleMode() {
        val newShuffleState = !_isShuffle.value
        setShuffleMode(newShuffleState)
    }

    fun setShuffleMode(enabled: Boolean) {
        _isShuffle.value = enabled
        viewModelScope.launch {
            musicPlayerService.setShuffle(enabled)
        }
    }

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

    fun skipToNext() {
        viewModelScope.launch {
            // Simply use the service's skipToNext method since we now maintain a queue
            musicPlayerService.skipToNext()
        }
    }

    fun skipToPrevious() {
        viewModelScope.launch {
            // Simply use the service's skipToPrevious method since we now maintain a queue
            musicPlayerService.skipToPrevious()
        }
    }

    fun seekTo(position: Float) {
        viewModelScope.launch {
            // Calculate position based on the duration from the service
            val durationMs = duration.value
            if (durationMs > 0) {
                val positionMs = (position * durationMs).toLong()
                musicPlayerService.seekTo(positionMs)
            }
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
    
    fun resetFullPlayerShownFlag() {
        preferenceManager.resetFullPlayerShownFlag()
        _shouldShowFullPlayer.value = true
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

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            musicPlayerService.stop()
        }
    }
} 