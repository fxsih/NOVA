package com.nova.music.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.nova.music.data.model.Song
import com.nova.music.ui.viewmodels.RepeatMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlayerService : Service() {
    @Inject
    lateinit var musicPlayerServiceImpl: MusicPlayerServiceImpl
    
    private val binder = MusicPlayerBinder()

    inner class MusicPlayerBinder : Binder() {
        fun getService(): IMusicPlayerService = musicPlayerServiceImpl
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        musicPlayerServiceImpl.onDestroy()
    }
}

interface IMusicPlayerService {
    val currentSong: StateFlow<Song?>
    val isPlaying: StateFlow<Boolean>
    val progress: StateFlow<Float>
    val error: StateFlow<String?>
    val repeatMode: StateFlow<RepeatMode>
    val duration: StateFlow<Long>
    val currentQueue: StateFlow<List<Song>>

    suspend fun playSong(song: Song)
    suspend fun setPlaylistQueue(songs: List<Song>, startSongId: String? = null)
    suspend fun play()
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
    suspend fun seekTo(position: Long)
    suspend fun setRepeatMode(mode: RepeatMode)
    suspend fun clearCurrentSong()
    suspend fun setShuffle(enabled: Boolean)
    suspend fun skipToNext()
    suspend fun skipToPrevious()
} 