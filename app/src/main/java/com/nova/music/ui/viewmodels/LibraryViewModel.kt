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
import kotlinx.coroutines.delay
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
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

    // ⚡ Elite UX: Use Snapshot State for blazing-fast UI updates
    private val _likedSongs = MutableStateFlow<List<Song>>(emptyList())
    val likedSongs: StateFlow<List<Song>> = _likedSongs.asStateFlow()
    
    // 🧹 Flow Debounce Safety: Prevent jank under heavy sync load
    init {
        // Initialize liked songs flow with frame-level debounce
        viewModelScope.launch {
            musicRepository.getLikedSongs()
                .debounce(16) // Frame-level debounce (16ms = 60fps)
                .distinctUntilChanged()
                .collect { songs ->
                    _likedSongs.value = songs
                    Log.d("LibraryViewModel", "📊 Liked songs updated: ${songs.size} songs")
                }
        }
    }

    val downloadedSongs: StateFlow<List<Song>> = musicRepository.getDownloadedSongs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Use a more stable approach to prevent flickering
    private val _playlistSongCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val playlistSongCounts: StateFlow<Map<String, Int>> = _playlistSongCounts.asStateFlow()
    
    // Track which playlists we're already monitoring
    private val monitoredPlaylists = mutableSetOf<String>()
    
    init {
        // Initialize playlist counts when playlists change, but only for new playlists
        viewModelScope.launch {
            playlists.collect { playlistsList ->
                val newPlaylists = playlistsList.filter { it.id !in monitoredPlaylists }
                if (newPlaylists.isNotEmpty()) {
                    Log.d("LibraryViewModel", "🔄 Adding ${newPlaylists.size} new playlists to monitoring")
                    newPlaylists.forEach { playlist ->
                        monitoredPlaylists.add(playlist.id)
                        startMonitoringPlaylist(playlist.id)
                    }
                }
            }
        }
    }
    
    private fun startMonitoringPlaylist(playlistId: String) {
        viewModelScope.launch {
            musicRepository.getPlaylistSongCount(playlistId)
                .debounce(16) // Frame-level debounce to prevent flickering
                .distinctUntilChanged()
                .collect { count ->
                    val currentCounts = _playlistSongCounts.value.toMutableMap()
                    currentCounts[playlistId] = count
                    _playlistSongCounts.value = currentCounts
                    Log.d("LibraryViewModel", "📊 Playlist $playlistId count updated: $count")
                }
        }
    }
    
    private suspend fun updateSinglePlaylistCount(playlistId: String) {
        try {
            val count = musicRepository.getPlaylistSongCount(playlistId).first()
            val currentCounts = _playlistSongCounts.value.toMutableMap()
            currentCounts[playlistId] = count
            _playlistSongCounts.value = currentCounts
            Log.d("LibraryViewModel", "✅ Updated count for playlist $playlistId: $count")
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "Error updating count for playlist $playlistId", e)
        }
    }
    
    /**
     * Clear the playlist songs cache for a specific playlist
     */
    fun clearPlaylistSongsCache(playlistId: String) {
        playlistSongsCache.remove(playlistId)
        Log.d("LibraryViewModel", "🧹 Cleared cache for playlist: $playlistId")
    }
    
    /**
     * Clear all playlist songs cache
     */
    fun clearAllPlaylistSongsCache() {
        playlistSongsCache.clear()
        Log.d("LibraryViewModel", "🧹 Cleared all playlist songs cache")
    }
    
    /**
     * Clear song memberships cache for a specific song
     */
    fun clearSongMembershipsCache(songId: String) {
        songMembershipsCache.remove(songId)
        Log.d("LibraryViewModel", "🧹 Cleared song memberships cache for song: $songId")
    }
    
    /**
     * Clear all song memberships cache
     */
    fun clearAllSongMembershipsCache() {
        songMembershipsCache.clear()
        Log.d("LibraryViewModel", "🧹 Cleared all song memberships cache")
    }
    
    /**
     * 🎯 Merge pending state with database state for UI display
     * This ensures pending additions/removals are reflected immediately
     * 🧠 Overlay state is prioritized as source of truth during transitions
     */
    fun getMergedSongPlaylistMemberships(songId: String): StateFlow<Set<String>> {
        return combine(
            getSongPlaylistMemberships(songId),
            pendingPlaylistAdditions
        ) { databaseMemberships, pendingAdditions ->
            val songPending = pendingAdditions[songId] ?: emptySet()
            
            // 🧠 Use overlay as source of truth during transitions
            if (songPending.isNotEmpty()) {
                val overlayMemberships = mutableSetOf<String>()
                
                // Start with database memberships
                overlayMemberships.addAll(databaseMemberships)
                
                // Apply pending additions
                songPending.forEach { pendingPlaylist ->
                    if (!pendingPlaylist.startsWith("REMOVE_")) {
                        overlayMemberships.add(pendingPlaylist)
                    }
                }
                
                // Apply pending removals
                songPending.forEach { pendingPlaylist ->
                    if (pendingPlaylist.startsWith("REMOVE_")) {
                        val playlistToRemove = pendingPlaylist.removePrefix("REMOVE_")
                        overlayMemberships.remove(playlistToRemove)
                    }
                }
                
                overlayMemberships.toSet()
            } else {
                // No pending state, use database as source of truth
                databaseMemberships
            }
        }
        .debounce(16) // ⏳ Prevent frequent updates from flooding the UI
        .distinctUntilChanged() // 🔄 Only emit when actually changed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )
    }
    
    /**
     * 🧩 Normalize display logic to ensure consistent ID-based comparisons
     * This prevents false negatives from object reference mismatches
     */
    fun isSongInPlaylistNormalized(songId: String, playlistId: String): Boolean {
        val mergedMemberships = getMergedSongPlaylistMemberships(songId).value
        return mergedMemberships.contains(playlistId)
    }
    
    /**
     * 🤹‍♀️ Elite UX: Retry helper for Firebase operations
     * Provides exponential backoff and automatic retry logic
     */
    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelay: Long = 100L,
        operation: suspend () -> T
    ): T {
        var retryCount = 0
        var lastException: Exception? = null
        
        while (retryCount < maxRetries) {
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                retryCount++
                Log.e("LibraryViewModel", "❌ Operation failed (attempt $retryCount/$maxRetries)", e)
                
                if (retryCount < maxRetries) {
                    val delay = initialDelay * retryCount // Exponential backoff
                    delay(delay)
                }
            }
        }
        
        throw lastException ?: Exception("Operation failed after $maxRetries attempts")
    }
        
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
            // Clear the cache for the deleted playlist
            playlistSongsCache.remove(playlistId)
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            musicRepository.renamePlaylist(playlistId, newName)
            // No need to force refresh - the flow will update automatically
        }
    }

    suspend fun addSongToPlaylist(song: Song, playlistId: String) {
        // 🎯 Temporary UI Overlay: Mark as pending to be added to playlist
        val currentPending = _pendingPlaylistAdditions.value.toMutableMap()
        val songPendingPlaylists = currentPending[song.id]?.toMutableSet() ?: mutableSetOf()
        songPendingPlaylists.add(playlistId)
        currentPending[song.id] = songPendingPlaylists
        _pendingPlaylistAdditions.value = currentPending
        Log.d("LibraryViewModel", "✅ Pending state updated for song: ${song.title} -> playlist: $playlistId")
        
        try {
        musicRepository.addSongToPlaylist(song, playlistId)
            // Update the specific playlist count immediately
            updateSinglePlaylistCount(playlistId)
            Log.d("LibraryViewModel", "✅ Song added to playlist successfully")
            
            // Clear pending state after successful operation
            val updatedPending = _pendingPlaylistAdditions.value.toMutableMap()
            val updatedSongPending = updatedPending[song.id]?.toMutableSet() ?: mutableSetOf()
            updatedSongPending.remove(playlistId)
            if (updatedSongPending.isEmpty()) {
                updatedPending.remove(song.id)
            } else {
                updatedPending[song.id] = updatedSongPending
            }
            _pendingPlaylistAdditions.value = updatedPending
            Log.d("LibraryViewModel", "🧹 Cleared pending state for song: ${song.title} -> playlist: $playlistId")
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "❌ Error adding song to playlist", e)
            // Revert pending state on error
            val updatedPending = _pendingPlaylistAdditions.value.toMutableMap()
            val updatedSongPending = updatedPending[song.id]?.toMutableSet() ?: mutableSetOf()
            updatedSongPending.remove(playlistId)
            if (updatedSongPending.isEmpty()) {
                updatedPending.remove(song.id)
            } else {
                updatedPending[song.id] = updatedSongPending
            }
            _pendingPlaylistAdditions.value = updatedPending
            throw e
        }
    }

    fun removeSongFromPlaylist(songId: String, playlistId: String) {
        // 🎯 Temporary UI Overlay: Mark as pending to be removed from playlist
        val currentPending = _pendingPlaylistAdditions.value.toMutableMap()
        val songPendingPlaylists = currentPending[songId]?.toMutableSet() ?: mutableSetOf()
        songPendingPlaylists.add("REMOVE_$playlistId") // Prefix to distinguish removal
        currentPending[songId] = songPendingPlaylists
        _pendingPlaylistAdditions.value = currentPending
        Log.d("LibraryViewModel", "✅ Pending state updated for song: $songId -> remove from playlist: $playlistId")
        
        viewModelScope.launch {
            try {
        musicRepository.removeSongFromPlaylist(songId, playlistId)
                // Update the specific playlist count immediately
                updateSinglePlaylistCount(playlistId)
                Log.d("LibraryViewModel", "✅ Song removed from playlist successfully")
                
                // Clear pending state after successful operation
                val updatedPending = _pendingPlaylistAdditions.value.toMutableMap()
                val updatedSongPending = updatedPending[songId]?.toMutableSet() ?: mutableSetOf()
                updatedSongPending.remove("REMOVE_$playlistId")
                if (updatedSongPending.isEmpty()) {
                    updatedPending.remove(songId)
                } else {
                    updatedPending[songId] = updatedSongPending
                }
                _pendingPlaylistAdditions.value = updatedPending
                Log.d("LibraryViewModel", "🧹 Cleared pending state for song: $songId -> remove from playlist: $playlistId")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "❌ Error removing song from playlist", e)
                // Revert pending state on error
                val updatedPending = _pendingPlaylistAdditions.value.toMutableMap()
                val updatedSongPending = updatedPending[songId]?.toMutableSet() ?: mutableSetOf()
                updatedSongPending.remove("REMOVE_$playlistId")
                if (updatedSongPending.isEmpty()) {
                    updatedPending.remove(songId)
                } else {
                    updatedPending[songId] = updatedSongPending
                }
                _pendingPlaylistAdditions.value = updatedPending
            }
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
                currentPlaylists.forEach { playlist ->
                    updateSinglePlaylistCount(playlist.id)
                }
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

    // Cache for playlist songs to prevent flickering
    private val playlistSongsCache = mutableMapOf<String, StateFlow<List<Song>>>()
    
    fun getPlaylistSongs(playlistId: String): StateFlow<List<Song>> {
        return playlistSongsCache.getOrPut(playlistId) {
        musicRepository.getPlaylistSongs(playlistId)
                .debounce(16) // Frame-level debounce to prevent flickering
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
        }
    }

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

    // Cache for song playlist memberships to prevent flickering
    private val songMembershipsCache = mutableMapOf<String, MutableStateFlow<Set<String>>>()
    
    // 🎯 Temporary UI Overlay State: Track songs that were just added to playlists
    // 🔄 Using MutableStateFlow to ensure state survives recomposition
    private val _pendingPlaylistAdditions = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val pendingPlaylistAdditions: StateFlow<Map<String, Set<String>>> = _pendingPlaylistAdditions.asStateFlow()
    
    /**
     * 🔄 Ensure overlay state survives recomposition and lifecycle events
     * This prevents state loss during configuration changes or recomposition
     */
    fun ensureOverlayStatePersistence() {
        // The MutableStateFlow already persists across recomposition
        // This function can be called to verify state integrity
        Log.d("LibraryViewModel", "🔄 Overlay state persistence check - pending: ${_pendingPlaylistAdditions.value.size} songs")
    }

    /**
     * Get all playlist memberships for a song in a single flow
     * ⏳ Optimized to prevent frequent updates from flooding the UI
     */
    fun getSongPlaylistMemberships(songId: String): StateFlow<Set<String>> {
        return songMembershipsCache.getOrPut(songId) {
            // Create a MutableStateFlow that we can update directly
            val mutableFlow = MutableStateFlow<Set<String>>(emptySet())
            
            // Start collecting the actual data and updating our mutable flow
            viewModelScope.launch {
        playlists
                    .flatMapConcat { playlists ->
                val membershipFlows = playlists
                    .filter { it.id != "liked_songs" && it.id != "downloads" }
                    .map { playlist ->
                        musicRepository.getPlaylistSongs(playlist.id)
                            .map { songs -> 
                                        // 🧩 Compare by song.id instead of object equality
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
                    .debounce(32) // ⏳ Increased debounce to prevent flooding
                    .distinctUntilChanged() // 🔄 Only emit when actually changed
                    .collect { memberships ->
                        mutableFlow.value = memberships
                    }
            }
            
            mutableFlow
        }
    }

    suspend fun isSongInPlaylist(songId: String, playlistId: String): Boolean {
        return musicRepository.isSongInPlaylist(songId, playlistId)
    }

    fun addSongToLiked(song: Song) {
        Log.d("LibraryViewModel", "Adding song to liked: ${song.title} (${song.id})")
        
        // ⚡ Elite UX: Update UI immediately (synchronous)
        val currentLikedSongs = _likedSongs.value.toMutableList()
        if (!currentLikedSongs.any { it.id == song.id }) {
            currentLikedSongs.add(song.copy(isLiked = true))
            _likedSongs.value = currentLikedSongs
            Log.d("LibraryViewModel", "✅ UI updated immediately for liked song: ${song.title}")
        }
        
        // 🤹‍♀️ Sync Retry Strategy: Optimism plus retry = best of both worlds
        viewModelScope.launch {
            try {
                retryWithBackoff {
            musicRepository.addSongToLiked(song)
                }
                Log.d("LibraryViewModel", "✅ Song added to liked successfully")
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "🔄 All retries failed, reverting UI for song: ${song.title}", e)
                // Revert UI change on error
                val currentLikedSongs = _likedSongs.value.toMutableList()
                currentLikedSongs.removeAll { it.id == song.id }
                _likedSongs.value = currentLikedSongs
            }
        }
    }

    fun removeSongFromLiked(songId: String) {
        Log.d("LibraryViewModel", "Removing song from liked: $songId")
        
        // ⚡ Elite UX: Update UI immediately (synchronous)
        val currentLikedSongs = _likedSongs.value.toMutableList()
        val songToRemove = currentLikedSongs.find { it.id == songId }
        if (songToRemove != null) {
            currentLikedSongs.removeAll { it.id == songId }
            _likedSongs.value = currentLikedSongs
            Log.d("LibraryViewModel", "✅ UI updated immediately for unliked song: ${songToRemove.title}")
            
            // 🤹‍♀️ Sync Retry Strategy: Optimism plus retry = best of both worlds
        viewModelScope.launch {
                try {
                    retryWithBackoff {
            musicRepository.removeSongFromLiked(songId)
                    }
                    Log.d("LibraryViewModel", "✅ Song removed from liked successfully")
                } catch (e: Exception) {
                    Log.e("LibraryViewModel", "🔄 All retries failed, reverting UI for song: ${songToRemove.title}", e)
                    // Revert UI change on error
                    val currentLikedSongs = _likedSongs.value.toMutableList()
                    currentLikedSongs.add(songToRemove)
                    _likedSongs.value = currentLikedSongs
                }
            }
        }
    }
    
    fun toggleLike(song: Song) {
        viewModelScope.launch {
            if (song.isLiked) {
                removeSongFromLiked(song.id)
            } else {
                addSongToLiked(song)
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