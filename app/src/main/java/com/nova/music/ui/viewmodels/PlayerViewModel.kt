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

    init {
        // Load initial song if ID is provided
        savedStateHandle.get<String>("songId")?.let { songId ->
            viewModelScope.launch {
                musicRepository.getAllSongs().collect { songs ->
                    _currentSong.value = songs.find { it.id == songId }
                    _currentSong.value?.let { song ->
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
    }

    fun loadSong(songId: String) {
        viewModelScope.launch {
            musicRepository.getAllSongs().collect { songs ->
                _currentSong.value = songs.find { it.id == songId }
                _currentSong.value?.let { song ->
                    musicRepository.addToRecentlyPlayed(song)
                }
            }
        }
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
        if (_isPlaying.value) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
        viewModelScope.launch {
            musicPlayerService.setShuffle(_isShuffle.value)
        }
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.OFF
        }
        viewModelScope.launch {
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

    fun addToPlaylist(song: Song, playlistId: String) {
        viewModelScope.launch {
            musicRepository.addSongToPlaylist(song, playlistId)
        }
    }

    fun seekTo(position: Float) {
        _progress.value = position
        exoPlayer.seekTo((position * exoPlayer.duration).toLong())
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer.release()
        viewModelScope.launch {
            musicPlayerService.stop()
        }
    }
} 