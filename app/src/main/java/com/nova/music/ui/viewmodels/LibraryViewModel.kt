package com.nova.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.music.data.model.Playlist
import com.nova.music.data.model.Song
import com.nova.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {
    
    val playlists: StateFlow<List<Playlist>> = musicRepository.getPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val likedSongs: StateFlow<List<Song>> = musicRepository.getLikedSongs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            musicRepository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            musicRepository.deletePlaylist(playlistId)
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            musicRepository.renamePlaylist(playlistId, newName)
        }
    }

    suspend fun addSongToPlaylist(song: Song, playlistId: String) {
        musicRepository.addSongToPlaylist(song, playlistId)
    }

    suspend fun removeSongFromPlaylist(songId: String, playlistId: String) {
        musicRepository.removeSongFromPlaylist(songId, playlistId)
    }

    fun getPlaylistSongCount(playlistId: String): Flow<Int> {
        return musicRepository.getPlaylistSongCount(playlistId)
    }

    fun getPlaylistSongs(playlistId: String): Flow<List<Song>> {
        return musicRepository.getPlaylists().transform { playlists ->
            val playlist = playlists.find { it.id == playlistId }
            emit(playlist?.songs ?: emptyList())
        }
    }

    fun addSongToLiked(song: Song) {
        viewModelScope.launch {
            musicRepository.addSongToLiked(song)
        }
    }

    fun removeSongFromLiked(songId: String) {
        viewModelScope.launch {
            musicRepository.removeSongFromLiked(songId)
        }
    }
} 