package com.nova.music.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.nova.music.data.model.Song
import com.nova.music.data.repository.MusicRepository
import com.nova.music.service.IMusicPlayerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RepeatMode {
    OFF, ONE, ALL
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val musicPlayerService: IMusicPlayerService,
    private val exoPlayer: ExoPlayer,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    init {
        // Load initial song if ID is provided
        savedStateHandle.get<String>("songId")?.let { songId ->
            viewModelScope.launch {
                musicRepository.getAllSongs().collect { songs ->
                    val song = songs.find { it.id == songId }
                    if (song != null) {
                        _currentSong.value = song
                        musicPlayerService.playSong(song)
                        musicRepository.addToRecentlyPlayed(song)
                    }
                }
            }
        }

        // Observe service state
        viewModelScope.launch {
            musicPlayerService.currentSong.collect { song ->
                _currentSong.value = song
            }
        }

        viewModelScope.launch {
            musicPlayerService.isPlaying.collect { playing ->
                _isPlaying.value = playing
            }
        }

        viewModelScope.launch {
            musicPlayerService.progress.collect { progress ->
                _progress.value = progress
            }
        }

        viewModelScope.launch {
            musicPlayerService.duration.collect { duration ->
                _duration.value = duration
            }
        }
    }

    fun loadSong(songId: String) {
        viewModelScope.launch {
            musicRepository.getAllSongs().collect { songs ->
                val song = songs.find { it.id == songId }
                if (song != null) {
                    _currentSong.value = song
                    musicPlayerService.playSong(song)
                    musicRepository.addToRecentlyPlayed(song)
                }
            }
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            if (_isPlaying.value) {
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
            _repeatMode.value = when (_repeatMode.value) {
                RepeatMode.OFF -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.OFF
            }
            musicPlayerService.setRepeatMode(_repeatMode.value)
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
            val positionMs = (position * _duration.value).toLong()
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
            _isPlaying.value = false
        }
    }

    fun clearCurrentSong() {
        viewModelScope.launch {
            _currentSong.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer.release()
        viewModelScope.launch {
            musicPlayerService.stop()
        }
    }
} 