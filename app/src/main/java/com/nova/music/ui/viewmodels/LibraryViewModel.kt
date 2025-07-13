package com.nova.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.music.data.model.Playlist
import com.nova.music.data.model.Song
import com.nova.music.data.model.UserMusicPreferences
import com.nova.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val dataStore: DataStore<Preferences>
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

    val playlistSongCounts: StateFlow<Map<String, Int>> = playlists
        .flatMapLatest { playlists ->
            combine(
                playlists.map { playlist ->
                    musicRepository.getPlaylistSongCount(playlist.id)
                        .map { count -> playlist.id to count }
                }
            ) { counts ->
                counts.toMap()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    private val _currentPlaylistSongs = MutableStateFlow<List<Song>>(emptyList())
    val currentPlaylistSongs: StateFlow<List<Song>> = _currentPlaylistSongs.asStateFlow()

    // Preferences for recommendations
    private val _userPreferences = MutableStateFlow(UserMusicPreferences())
    val userPreferences: StateFlow<UserMusicPreferences> = _userPreferences.asStateFlow()

    private val preferencesKey = stringPreferencesKey("user_music_preferences")

    init {
        viewModelScope.launch { loadUserPreferences() }
    }

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

    fun removeSongFromPlaylist(songId: String, playlistId: String) {
        viewModelScope.launch {
        musicRepository.removeSongFromPlaylist(songId, playlistId)
        }
    }

    fun getPlaylistSongCount(playlistId: String): Flow<Int> {
        return musicRepository.getPlaylistSongCount(playlistId)
    }

    fun getPlaylistSongs(playlistId: String): Flow<List<Song>> = 
        musicRepository.getPlaylistSongs(playlistId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    suspend fun isSongInPlaylist(songId: String, playlistId: String): Boolean {
        return musicRepository.isSongInPlaylist(songId, playlistId)
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

    fun setUserPreferences(preferences: UserMusicPreferences) {
        _userPreferences.value = preferences
        viewModelScope.launch { saveUserPreferences(preferences) }
    }

    private suspend fun saveUserPreferences(preferences: UserMusicPreferences) {
        val json = Json.encodeToString(preferences)
        dataStore.edit { it[preferencesKey] = json }
    }

    suspend fun loadUserPreferences() {
        try {
            val json = dataStore.data.first()[preferencesKey]
            if (json != null) {
                _userPreferences.value = Json.decodeFromString(json)
            }
        } catch (e: Exception) {
            // If there's an error loading preferences, keep the default empty preferences
            _userPreferences.value = UserMusicPreferences()
        }
    }
} 