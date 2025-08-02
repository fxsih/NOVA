package com.nova.music.util

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // In-memory flag to track if full player has been shown in current session
    private var fullPlayerShownInSession = false
    
    fun setFullPlayerShownInSession() {
        fullPlayerShownInSession = true
    }
    
    fun hasFullPlayerBeenShownInSession(): Boolean {
        return fullPlayerShownInSession
    }
    
    fun resetFullPlayerShownFlag() {
        fullPlayerShownInSession = false
    }
    
    /**
     * Checks if onboarding has been shown to the user before
     * @return true if onboarding has been shown, false otherwise
     */
    fun hasShownOnboarding(): Boolean {
        return getBoolean(KEY_ONBOARDING_SHOWN, false)
    }
    
    /**
     * Sets the flag indicating that onboarding has been shown to the user
     */
    fun setOnboardingShown() {
        setBoolean(KEY_ONBOARDING_SHOWN, true)
    }
    
    /**
     * Gets the base URL for the API server.
     * @return The API base URL, defaulting to localhost if not set
     */
    fun getApiBaseUrl(): String {
        return getString(KEY_API_BASE_URL, DEFAULT_API_BASE_URL)
    }
    
    /**
     * Sets the base URL for the API server.
     * @param url The API base URL to set
     */
    fun setApiBaseUrl(url: String) {
        setString(KEY_API_BASE_URL, url)
    }
    
    /**
     * Gets the set of downloaded song IDs.
     * @return A set of song IDs that have been downloaded
     */
    fun getDownloadedSongIds(): Set<String> {
        return prefs.getStringSet(KEY_DOWNLOADED_SONGS, emptySet()) ?: emptySet()
    }
    
    /**
     * Adds a song ID to the set of downloaded songs.
     * @param songId The ID of the downloaded song
     */
    fun addDownloadedSongId(songId: String) {
        val currentIds = getDownloadedSongIds().toMutableSet()
        currentIds.add(songId)
        prefs.edit().putStringSet(KEY_DOWNLOADED_SONGS, currentIds).apply()
    }
    
    /**
     * Checks if a song has been downloaded.
     * @param songId The ID of the song to check
     * @return true if the song has been downloaded, false otherwise
     */
    fun isSongDownloaded(songId: String): Boolean {
        return getDownloadedSongIds().contains(songId)
    }
    
    /**
     * Removes a song ID from the set of downloaded songs.
     * @param songId The ID of the song to remove from downloaded list
     */
    fun removeDownloadedSongId(songId: String) {
        val currentIds = getDownloadedSongIds().toMutableSet()
        currentIds.remove(songId)
        prefs.edit().putStringSet(KEY_DOWNLOADED_SONGS, currentIds).apply()
    }
    
    /**
     * Clears only playback-related preferences (not user recommendations or onboarding)
     */
    fun clearPlaybackPreferences() {
        prefs.edit()
            .remove(KEY_LAST_PLAYED_SONG_ID)
            // .remove(KEY_LAST_PLAYBACK_POSITION) // Uncomment if you have this key
            // .remove(KEY_LAST_QUEUE) // Uncomment if you have this key
            .apply()
    }
    
    /**
     * Clears only playback-related preferences (not user recommendations or onboarding) from SharedPreferences and DataStore
     */
    suspend fun clearPlaybackPreferencesDataStore(dataStore: DataStore<Preferences>) {
        // Clear from SharedPreferences
        clearPlaybackPreferences()
        // Clear from DataStore
        dataStore.edit { prefs: MutablePreferences ->
            prefs.remove(stringPreferencesKey(KEY_LAST_PLAYED_SONG_ID))
            // Remove other playback keys if needed
        }
    }
    
    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }
    
    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
    
    fun getString(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }
    
    fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    
    fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }
    
    fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }
    
    fun getLong(key: String, defaultValue: Long): Long {
        return prefs.getLong(key, defaultValue)
    }
    
    fun setLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }
    
    fun clear() {
        prefs.edit().clear().apply()
    }

    fun setLastPlayedPlaylist(playlistId: String, songIds: List<String>) {
        setString(KEY_LAST_PLAYED_PLAYLIST_ID, playlistId)
        setString(KEY_LAST_PLAYED_PLAYLIST_SONG_IDS, songIds.joinToString(","))
    }
    fun getLastPlayedPlaylistId(): String? = getString(KEY_LAST_PLAYED_PLAYLIST_ID, "").takeIf { it.isNotEmpty() }
    fun getLastPlayedPlaylistSongIds(): List<String> = getString(KEY_LAST_PLAYED_PLAYLIST_SONG_IDS, "").split(",").filter { it.isNotEmpty() }
    
    companion object {
        private const val PREFS_NAME = "nova_preferences"
        
        // Preference keys
        const val KEY_FIRST_LAUNCH = "first_launch"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LAST_PLAYED_SONG_ID = "last_played_song_id"
        const val KEY_API_BASE_URL = "api_base_url"
        const val KEY_DOWNLOADED_SONGS = "downloaded_songs"
        const val KEY_ONBOARDING_SHOWN = "onboarding_shown"
        const val KEY_LAST_PLAYED_PLAYLIST_ID = "last_played_playlist_id"
        const val KEY_LAST_PLAYED_PLAYLIST_SONG_IDS = "last_played_playlist_song_ids"
        
        // Default values
        const val DEFAULT_API_BASE_URL = "http://192.168.29.154:8000/"
    }
} 