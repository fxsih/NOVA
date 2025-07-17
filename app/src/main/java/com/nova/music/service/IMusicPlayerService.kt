package com.nova.music.service

import com.nova.music.data.model.Song
import com.nova.music.ui.viewmodels.RepeatMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for music player service functionality.
 * This allows for better separation of concerns and testability.
 */
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
    suspend fun shuffleQueue()
    suspend fun skipToNext()
    suspend fun skipToPrevious()
    suspend fun moveSongUp(songId: String): Boolean
    suspend fun moveSongDown(songId: String): Boolean
    suspend fun reorderQueue(songs: List<Song>)
    suspend fun removeFromQueue(songId: String): Boolean
    suspend fun playQueueItemAt(index: Int)
} 