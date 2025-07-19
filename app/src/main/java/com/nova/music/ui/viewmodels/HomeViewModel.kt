package com.nova.music.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.music.data.model.Song
import com.nova.music.data.repository.MusicRepository
import com.nova.music.data.model.UserMusicPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _recommendedSongsState = MutableStateFlow<UiState<List<Song>>>(UiState.Loading)
    val recommendedSongsState = _recommendedSongsState.asStateFlow()

    private var hasLoadedRecommendations = false

    private val _trendingSongsState = MutableStateFlow<UiState<List<Song>>>(UiState.Loading)
    val trendingSongsState = _trendingSongsState.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    val recentlyPlayed = musicRepository.getRecentlyPlayed()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches = _recentSearches.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            musicRepository.getAllSongs().collect {
                _songs.value = it
            }
            _recentSearches.value = musicRepository.getRecentSearches()
        }
        loadTrendingSongs()
    }
    
    fun loadTrendingSongs() {
        viewModelScope.launch {
            _trendingSongsState.value = UiState.Loading
            try {
                musicRepository.getTrendingSongs().collect { songs ->
                    if (songs.isEmpty()) {
                        _trendingSongsState.value = UiState.Error("No trending songs found")
                    } else {
                        _trendingSongsState.value = UiState.Success(songs)
                    }
                }
            } catch (e: Exception) {
                _trendingSongsState.value = UiState.Error("Failed to load trending songs: ${e.message}")
            }
        }
    }
    
    fun loadRecommendedSongs(preferences: UserMusicPreferences) {
        // Don't load if we already have recommendations loaded
        if (hasLoadedRecommendations && _recommendedSongsState.value is UiState.Success) {
            Log.d("HomeViewModel", "Recommendations already loaded, skipping")
            return
        }
        
        viewModelScope.launch {
            // Only set to loading if we don't already have data
            if (_recommendedSongsState.value !is UiState.Success) {
                _recommendedSongsState.value = UiState.Loading
            }
            
            try {
                val genres = preferences.genres.joinToString(",")
                val languages = preferences.languages.joinToString(",")
                val artists = preferences.artists.joinToString(",")
                
                Log.d("HomeViewModel", "Loading recommended songs with genres=$genres, languages=$languages, artists=$artists")
                
                musicRepository.getRecommendedSongs(genres, languages, artists)
                    .catch { e ->
                        Log.e("HomeViewModel", "Error loading recommendations", e)
                        // Only update state if we don't already have data
                        if (_recommendedSongsState.value !is UiState.Success) {
                            _recommendedSongsState.value = UiState.Error("Failed to load recommendations: ${e.message}")
                        }
                    }
                    .collect { songs ->
                        if (songs.isEmpty()) {
                            // Only update to error if we don't already have data
                            if (_recommendedSongsState.value !is UiState.Success) {
                                Log.d("HomeViewModel", "No recommended songs found")
                                _recommendedSongsState.value = UiState.Error("No recommended songs found")
                            }
                        } else {
                            Log.d("HomeViewModel", "Loaded ${songs.size} recommended songs")
                            _recommendedSongsState.value = UiState.Success(songs)
                            hasLoadedRecommendations = true
                        }
                    }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error in loadRecommendedSongs", e)
                // Only update state if we don't already have data
                if (_recommendedSongsState.value !is UiState.Success) {
                    _recommendedSongsState.value = UiState.Error("Failed to load recommendations: ${e.message}")
                }
            }
        }
    }

    fun refreshTrendingSongs() {
        loadTrendingSongs()
    }
    
    fun refreshRecommendedSongs(preferences: UserMusicPreferences) {
        Log.d("HomeViewModel", "Forcing refresh of recommended songs with preferences: genres=${preferences.genres}, languages=${preferences.languages}, artists=${preferences.artists}")
        
        // Reset the flag since we're forcing a refresh
        hasLoadedRecommendations = false
        
        // First set to loading state to clear the UI
        _recommendedSongsState.value = UiState.Loading
        
        viewModelScope.launch {
            try {
                val genres = preferences.genres.joinToString(",")
                val languages = preferences.languages.joinToString(",")
                val artists = preferences.artists.joinToString(",")
                Log.d("HomeViewModel", "Calling repository with forceRefresh=true")
                musicRepository.getRecommendedSongs(genres, languages, artists, forceRefresh = true)
                    .catch { e ->
                        Log.e("HomeViewModel", "Error refreshing recommendations", e)
                        _recommendedSongsState.value = UiState.Error("Failed to refresh recommendations: ${e.message}")
                    }
                    .collect { songs ->
                        if (songs.isEmpty()) {
                            Log.d("HomeViewModel", "Received empty list of recommended songs")
                            _recommendedSongsState.value = UiState.Error("No recommended songs found")
                        } else {
                            Log.d("HomeViewModel", "Received ${songs.size} recommended songs after refresh")
                            _recommendedSongsState.value = UiState.Success(songs)
                        }
                    }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error in refreshRecommendedSongs", e)
                _recommendedSongsState.value = UiState.Error("Failed to refresh recommendations: ${e.message}")
            }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                if (query.isBlank()) {
                    musicRepository.getAllSongs().collect {
                        _songs.value = it
                    }
                } else {
                    musicRepository.searchSongs(query).collect {
                        _songs.value = it
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Search failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSearch() {
        viewModelScope.launch {
            musicRepository.getAllSongs().collect {
                _songs.value = it
            }
        }
    }

    fun addToRecentlyPlayed(song: Song) {
        viewModelScope.launch {
            try {
                musicRepository.addToRecentlyPlayed(song)
                // Refresh the recently played list to ensure UI is updated
                musicRepository.getRecentlyPlayed()
                    .take(1)
                    .collect { /* Just trigger the collection to refresh the StateFlow */ }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error adding song to recently played", e)
                _errorMessage.value = "Failed to add song to recently played"
            }
        }
    }

    fun addToRecentSearches(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            musicRepository.addToRecentSearches(query)
            _recentSearches.value = musicRepository.getRecentSearches()
        }
    }

    fun removeFromRecentSearches(query: String) {
        viewModelScope.launch {
            musicRepository.removeFromRecentSearches(query)
            _recentSearches.value = musicRepository.getRecentSearches()
        }
    }
    
    // Expose MusicRepository for other components that need it
    fun getMusicRepository(): MusicRepository = musicRepository
} 
 