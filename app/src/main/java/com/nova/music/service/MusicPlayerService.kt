package com.nova.music.service

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import com.nova.music.data.model.Song
import com.nova.music.ui.viewmodels.RepeatMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlayerService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicPlayerBinder()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    inner class MusicPlayerBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun playSong(song: Song) {
        _currentSong.value = song
        // TODO: Implement actual media playback
        _isPlaying.value = true
    }

    fun pause() {
        mediaPlayer?.pause()
        _isPlaying.value = false
    }

    fun resume() {
        mediaPlayer?.start()
        _isPlaying.value = true
    }

    fun stop() {
        mediaPlayer?.stop()
        _isPlaying.value = false
        _currentSong.value = null
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

interface IMusicPlayerService {
    val currentSong: StateFlow<Song?>
    val isPlaying: StateFlow<Boolean>
    val progress: StateFlow<Float>
    val duration: StateFlow<Long>

    suspend fun playSong(song: Song)
    suspend fun play()
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
    suspend fun seekTo(position: Long)
    suspend fun skipToNext()
    suspend fun skipToPrevious()
    suspend fun setShuffle(enabled: Boolean)
    suspend fun setRepeatMode(mode: RepeatMode)
} 