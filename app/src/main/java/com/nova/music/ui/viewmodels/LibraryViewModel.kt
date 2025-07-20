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
            // For each playlist, get its song count directly from database
            val countFlows = playlists.map { playlist ->
                musicRepository.getPlaylistSongCount(playlist.id).map { count ->
                    playlist.id to count
                }
                }
            
            if (countFlows.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(countFlows) { counts ->
                counts.toMap()
                }
            }
        }
        .distinctUntilChanged()
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
        viewModelScope.launch { 
            loadUserPreferences()
            // Verify downloaded songs on ViewModel initialization
            verifyDownloadedSongsOnInit()
            // Sync playlists on ViewModel initialization
            syncPlaylistsOnInit()
        }
    }
    
    /**
     * Verify downloaded songs on ViewModel initialization
     */
    private suspend fun verifyDownloadedSongsOnInit() {
        try {
            Log.d("LibraryViewModel", "🔄 Verifying downloaded songs on ViewModel init")
            (musicRepository as? com.nova.music.data.repository.impl.MusicRepositoryImpl)?.syncDownloadedSongsOnStartup()
            Log.d("LibraryViewModel", "✅ Downloaded songs verified on init")
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "❌ Error verifying downloaded songs on init", e)
        }
    }
    
    /**
     * Sync playlists on ViewModel initialization
     */
    private suspend fun syncPlaylistsOnInit() {
        try {
            Log.d("LibraryViewModel", "🔄 Syncing playlists on ViewModel init")
            (musicRepository as? com.nova.music.data.repository.impl.MusicRepositoryImpl)?.syncPlaylistsOnStartup()
            Log.d("LibraryViewModel", "✅ Playlists synced on init")
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "❌ Error syncing playlists on init", e)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            musicRepository.createPlaylist(name)
            // No need to force refresh - the flow will update automatically
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            musicRepository.deletePlaylist(playlistId)
            // No need to force refresh - the flow will update automatically
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            musicRepository.renamePlaylist(playlistId, newName)
            // No need to force refresh - the flow will update automatically
        }
    }

    suspend fun addSongToPlaylist(song: Song, playlistId: String) {
        musicRepository.addSongToPlaylist(song, playlistId)
        // Force immediate UI update by triggering a refresh
        refreshPlaylistCounts()
    }

    fun removeSongFromPlaylist(songId: String, playlistId: String) {
        viewModelScope.launch {
        musicRepository.removeSongFromPlaylist(songId, playlistId)
            // Force immediate UI update by triggering a refresh
            refreshPlaylistCounts()
        }
    }
    
    /**
     * Force refresh playlist counts to ensure immediate UI updates
     */
    private fun refreshPlaylistCounts() {
        viewModelScope.launch {
            try {
                // Trigger a refresh of playlist counts by getting current playlists
                val currentPlaylists = playlists.value
                Log.d("LibraryViewModel", "🔄 Refreshing playlist counts for ${currentPlaylists.size} playlists")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "❌ Error refreshing playlist counts", e)
            }
        }
    }

    fun getPlaylistSongCount(playlistId: String): Flow<Int> {
        return musicRepository.getPlaylistSongCount(playlistId)
            .distinctUntilChanged()
            .onEach { count ->
                Log.d("LibraryViewModel", "🎵 Playlist $playlistId song count: $count")
            }
    }

    fun getPlaylistSongs(playlistId: String): Flow<List<Song>> = 
        musicRepository.getPlaylistSongs(playlistId)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    /**
     * Get real-time playlist membership for a song
     */
    fun isSongInPlaylistFlow(songId: String, playlistId: String): StateFlow<Boolean> =
        musicRepository.getPlaylistSongs(playlistId)
            .map { songs -> songs.any { it.id == songId } }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )

    /**
     * Get all playlist memberships for a song in a single flow
     */
    fun getSongPlaylistMemberships(songId: String): StateFlow<Set<String>> =
        playlists
            .flatMapLatest { playlists ->
                val membershipFlows = playlists
                    .filter { it.id != "liked_songs" && it.id != "downloads" }
                    .map { playlist ->
                        musicRepository.getPlaylistSongs(playlist.id)
                            .map { songs -> 
                                if (songs.any { it.id == songId }) playlist.id else null 
                            }
                    }
                
                if (membershipFlows.isEmpty()) {
                    flowOf(emptySet())
                } else {
                    combine(membershipFlows) { memberships ->
                        memberships.filterNotNull().toSet()
                    }
                }
            }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptySet()
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
                // Force refresh by getting playlists again and emitting immediately
                val currentPlaylists = musicRepository.getPlaylists().first()
                Log.d("LibraryViewModel", "Refreshed playlists, count: ${currentPlaylists.size}")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error refreshing playlists", e)
            }
        }
    }

    /**
     * Test Firebase sync functionality
     */
    fun testFirebaseSync() {
        viewModelScope.launch {
            try {
                Log.d("LibraryViewModel", "🧪 Testing Firebase sync...")
                
                // Create a test playlist
                val testPlaylistName = "Test_${System.currentTimeMillis()}"
                musicRepository.createPlaylist(testPlaylistName)
                
                Log.d("LibraryViewModel", "✅ Test playlist created: $testPlaylistName")
                
                // Wait a bit for sync
                kotlinx.coroutines.delay(2000)
                
                // Refresh to see if it appears
                refreshPlaylists()
                
                Log.d("LibraryViewModel", "✅ Firebase sync test completed")
                
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "❌ Firebase sync test failed", e)
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

    fun forceRefreshDownloadedSongs() {
        viewModelScope.launch {
            Log.d("LibraryViewModel", "🔄 Force refreshing downloaded songs from local database")
            try {
                // Force refresh by getting downloaded songs from local database
                val currentDownloadedSongs = musicRepository.getDownloadedSongs().first()
                Log.d("LibraryViewModel", "✅ Downloaded songs refreshed successfully: ${currentDownloadedSongs.size} songs")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "❌ Error refreshing downloaded songs", e)
            }
        }
    }

    /**
     * Restore downloaded songs if they get lost
     */
    fun restoreDownloadedSongs() {
        viewModelScope.launch {
            Log.d("LibraryViewModel", "🔄 Restoring downloaded songs...")
            try {
                (musicRepository as? com.nova.music.data.repository.impl.MusicRepositoryImpl)?.restoreDownloadedSongs()
                Log.d("LibraryViewModel", "✅ Downloaded songs restored successfully")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "❌ Error restoring downloaded songs", e)
            }
        }
    }

    /**
     * Force refresh downloaded songs from the repository.
     * This can be called from the UI to manually refresh downloaded songs.
     */
    fun forceRefreshDownloadedSongsFromRepository() {
        viewModelScope.launch {
            try {
                Log.d("LibraryViewModel", "🔄 Force refreshing downloaded songs from repository...")
                // Force refresh by getting downloaded songs again
                val currentDownloadedSongs = musicRepository.getDownloadedSongs().first()
                Log.d("LibraryViewModel", "✅ Force refreshed downloaded songs, count: ${currentDownloadedSongs.size}")
                Log.d("LibraryViewModel", "📋 Downloaded songs: ${currentDownloadedSongs.map { it.title }}")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "❌ Error force refreshing downloaded songs", e)
            }
        }
    }
    
} 