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
import android.util.Log

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

    val downloadedSongs: StateFlow<List<Song>> = musicRepository.getDownloadedSongs()
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
        
    // Count of downloaded songs
    val downloadedSongsCount: StateFlow<Int> = downloadedSongs
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
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
            Log.d("LibraryViewModel", "Adding song to liked: ${song.title} (${song.id})")
            try {
            musicRepository.addSongToLiked(song)
                Log.d("LibraryViewModel", "Song added to liked successfully")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error adding song to liked", e)
            }
        }
    }

    fun removeSongFromLiked(songId: String) {
        viewModelScope.launch {
            Log.d("LibraryViewModel", "Removing song from liked: $songId")
            musicRepository.removeSongFromLiked(songId)
            Log.d("LibraryViewModel", "Song removed from liked successfully")
        }
    }
    
    fun toggleLike(song: Song) {
        viewModelScope.launch {
            if (song.isLiked) {
                musicRepository.removeSongFromLiked(song.id)
            } else {
                musicRepository.addSongToLiked(song)
            }
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

    /**
     * Refreshes playlists from the repository.
     * This is used by the pull-to-refresh functionality.
     */
    fun refreshPlaylists() {
        viewModelScope.launch {
            try {
                // Force refresh by getting playlists again
                musicRepository.getPlaylists().first()
                Log.d("LibraryViewModel", "Refreshed playlists")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error refreshing playlists", e)
            }
        }
    }

    /**
     * Syncs playlists with Firebase Firestore.
     * This ensures all playlists are synchronized across devices.
     */
    fun syncPlaylistsWithFirebase() {
        viewModelScope.launch {
            try {
                // Force refresh playlists which will trigger Firebase sync
                musicRepository.getPlaylists().first()
                Log.d("LibraryViewModel", "Synced playlists with Firebase")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error syncing playlists with Firebase", e)
            }
        }
    }

    /**
     * Manually sync liked songs with Firebase for debugging
     */
    fun syncLikedSongsWithFirebase() {
        viewModelScope.launch {
            try {
                Log.d("LibraryViewModel", "Manually syncing liked songs with Firebase")
                musicRepository.syncUserDataWithFirebase()
                Log.d("LibraryViewModel", "Liked songs sync completed")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error syncing liked songs with Firebase", e)
            }
        }
    }
    
    /**
     * Force sync liked songs from Firebase to ensure consistency
     */
    fun forceSyncLikedSongs() {
        viewModelScope.launch {
            try {
                Log.d("LibraryViewModel", "Force syncing liked songs from Firebase")
                musicRepository.syncFromFirebase()
                Log.d("LibraryViewModel", "Liked songs force sync completed")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error force syncing liked songs", e)
            }
        }
    }
    
    /**
     * Force sync all user data (liked and downloaded songs) from Firebase
     */
    fun forceSyncAllUserData() {
        viewModelScope.launch {
            try {
                Log.d("LibraryViewModel", "Force syncing all user data from Firebase")
                musicRepository.syncFromFirebase()
                Log.d("LibraryViewModel", "All user data force sync completed")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error force syncing all user data", e)
            }
        }
    }
    
    /**
     * Clean up the global songs collection (use with caution!)
     * Only call this after ensuring all songs are properly migrated
     */
    fun cleanupGlobalSongsCollection() {
        viewModelScope.launch {
            try {
                Log.d("LibraryViewModel", "Cleaning up global songs collection")
                musicRepository.cleanupGlobalSongsCollection()
                Log.d("LibraryViewModel", "Global songs collection cleanup completed")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error cleaning up global songs collection", e)
            }
        }
    }
    
    /**
     * Force sync all playlist counts to ensure consistency
     */
    fun forceSyncPlaylistCounts() {
        viewModelScope.launch {
            try {
                Log.d("LibraryViewModel", "Force syncing all playlist counts")
                val playlists = playlists.value
                for (playlist in playlists) {
                    // This will trigger the consistency check and fix
                    musicRepository.getPlaylistSongCount(playlist.id).first()
                }
                Log.d("LibraryViewModel", "All playlist counts synced")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error force syncing playlist counts", e)
            }
        }
    }
    

} 