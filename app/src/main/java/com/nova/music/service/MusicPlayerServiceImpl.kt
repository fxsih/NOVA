package com.nova.music.service

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.nova.music.data.model.Song
import com.nova.music.data.repository.MusicRepository
import com.nova.music.ui.viewmodels.RepeatMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlayerServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository
) : IMusicPlayerService {
    private var mediaPlayer: MediaPlayer? = null
    private var currentSongList: List<Song> = emptyList()
    private var currentSongIndex: Int = -1
    private var isShuffleEnabled: Boolean = false
    private var repeatMode: RepeatMode = RepeatMode.OFF

    private val _currentSong = MutableStateFlow<Song?>(null)
    override val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    override val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressJob: Job? = null

    override suspend fun playSong(song: Song) {
        withContext(Dispatchers.IO) {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, Uri.parse(song.albumArt))
                    prepareAsync()
                    setOnPreparedListener {
                        start()
                        _isPlaying.value = true
                        _duration.value = duration.toLong()
                        startProgressUpdates()
                    }
                    setOnCompletionListener {
                        coroutineScope.launch {
                            when (repeatMode) {
                                RepeatMode.ONE -> {
                                    seekTo(0)
                                    start()
                                }
                                RepeatMode.ALL -> {
                                    skipToNext()
                                }
                                RepeatMode.OFF -> {
                                    if (currentSongIndex < currentSongList.size - 1) {
                                        skipToNext()
                                    } else {
                                        stop()
                                    }
                                }
                            }
                        }
                    }
                }
                _currentSong.value = song
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val currentPosition = player.currentPosition.toLong()
                        val totalDuration = player.duration.toLong()
                        _progress.value = if (totalDuration > 0) {
                            currentPosition.toFloat() / totalDuration
                        } else 0f
                    }
                }
                delay(1000)
            }
        }
    }

    override suspend fun play() {
        withContext(Dispatchers.IO) {
            mediaPlayer?.start()
            _isPlaying.value = true
            startProgressUpdates()
        }
    }

    override suspend fun pause() {
        withContext(Dispatchers.IO) {
            mediaPlayer?.pause()
            _isPlaying.value = false
            progressJob?.cancel()
        }
    }

    override suspend fun resume() {
        withContext(Dispatchers.IO) {
            mediaPlayer?.start()
            _isPlaying.value = true
            startProgressUpdates()
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            _isPlaying.value = false
            _currentSong.value = null
            _progress.value = 0f
            progressJob?.cancel()
        }
    }

    override suspend fun seekTo(position: Long) {
        withContext(Dispatchers.IO) {
            mediaPlayer?.seekTo(position.toInt())
        }
    }

    override suspend fun skipToNext() {
        withContext(Dispatchers.IO) {
            if (currentSongList.isEmpty()) return@withContext
            
            val nextIndex = if (isShuffleEnabled) {
                (0 until currentSongList.size).random()
            } else {
                (currentSongIndex + 1) % currentSongList.size
            }
            currentSongList.getOrNull(nextIndex)?.let { song ->
                currentSongIndex = nextIndex
                playSong(song)
            }
        }
    }

    override suspend fun skipToPrevious() {
        withContext(Dispatchers.IO) {
            if (currentSongList.isEmpty()) return@withContext
            
            val prevIndex = if (isShuffleEnabled) {
                (0 until currentSongList.size).random()
            } else {
                if (currentSongIndex > 0) currentSongIndex - 1 else currentSongList.size - 1
            }
            currentSongList.getOrNull(prevIndex)?.let { song ->
                currentSongIndex = prevIndex
                playSong(song)
            }
        }
    }

    override suspend fun setShuffle(enabled: Boolean) {
        isShuffleEnabled = enabled
    }

    override suspend fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
    }

    fun setPlaylist(songs: List<Song>, startIndex: Int) {
        currentSongList = songs
        currentSongIndex = startIndex
        coroutineScope.launch {
            songs.getOrNull(startIndex)?.let { song ->
                playSong(song)
            }
        }
    }

    fun cleanup() {
        coroutineScope.launch {
            stop()
            mediaPlayer?.release()
            mediaPlayer = null
            currentSongList = emptyList()
            currentSongIndex = -1
            isShuffleEnabled = false
            repeatMode = RepeatMode.OFF
            progressJob?.cancel()
        }
    }
} 