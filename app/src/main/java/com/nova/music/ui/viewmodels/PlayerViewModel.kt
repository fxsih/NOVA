package com.nova.music.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.music.data.model.Song
import com.nova.music.data.repository.MusicRepository
import com.nova.music.service.IMusicPlayerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RepeatMode {
    NONE, ONE, ALL
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val musicPlayerService: IMusicPlayerService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    // Use the service's state flows directly
    val currentSong = musicPlayerService.currentSong
    val isPlaying = musicPlayerService.isPlaying
    val progress = musicPlayerService.progress
    val error = musicPlayerService.error
    val repeatMode = musicPlayerService.repeatMode
    val duration = musicPlayerService.duration

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    init {
        // Load initial song if ID is provided
        savedStateHandle.get<String>("songId")?.let { songId ->
            viewModelScope.launch {
                musicRepository.getAllSongs().collect { songs ->
                    val song = songs.find { it.id == songId }
                    if (song != null) {
                        loadSong(song)
                    }
                }
            }
        }
    }

    fun loadSong(song: Song) {
        viewModelScope.launch {
            musicPlayerService.playSong(song)
        }
    }

    fun loadSong(songId: String) {
        viewModelScope.launch {
            musicRepository.getAllSongs().collect { songs ->
                val song = songs.find { it.id == songId }
                if (song != null) {
                    loadSong(song)
                }
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

    fun toggleShuffle() {
        viewModelScope.launch {
            _isShuffle.value = !_isShuffle.value
            musicPlayerService.setShuffle(_isShuffle.value)
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
            musicPlayerService.skipToNext()
        }
    }

    fun skipToPrevious() {
        viewModelScope.launch {
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

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            musicPlayerService.stop()
        }
    }
} 