package com.nova.music.service

import android.media.MediaPlayer
import com.nova.music.data.model.Song
import com.nova.music.ui.viewmodels.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlayerServiceImpl @Inject constructor() : IMusicPlayerService {
    private var mediaPlayer: MediaPlayer? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    override val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _progress = MutableStateFlow(0f)
    override val progress: StateFlow<Float> = _progress

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration

    override suspend fun playSong(song: Song) {
        _currentSong.value = song
        // TODO: Implement actual media playback
        _isPlaying.value = true
    }

    override suspend fun play() {
        mediaPlayer?.start()
        _isPlaying.value = true
    }

    override suspend fun pause() {
        mediaPlayer?.pause()
        _isPlaying.value = false
    }

    override suspend fun resume() {
        mediaPlayer?.start()
        _isPlaying.value = true
    }

    override suspend fun stop() {
        mediaPlayer?.stop()
        _isPlaying.value = false
    }

    override suspend fun seekTo(position: Long) {
        mediaPlayer?.seekTo(position.toInt())
    }

    override suspend fun skipToNext() {
        // TODO: Implement skip to next
    }

    override suspend fun skipToPrevious() {
        // TODO: Implement skip to previous
    }

    override suspend fun setShuffle(enabled: Boolean) {
        // TODO: Implement shuffle
    }

    override suspend fun setRepeatMode(mode: RepeatMode) {
        // TODO: Implement repeat mode
    }

    fun cleanup() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
} 