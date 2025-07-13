package com.nova.music.util

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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
    
    companion object {
        private const val PREFS_NAME = "nova_preferences"
        
        // Preference keys
        const val KEY_FIRST_LAUNCH = "first_launch"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_LAST_PLAYED_SONG_ID = "last_played_song_id"
        const val KEY_API_BASE_URL = "api_base_url"
        
        // Default values
        const val DEFAULT_API_BASE_URL = "http://192.168.29.154:8000"
    }
} 