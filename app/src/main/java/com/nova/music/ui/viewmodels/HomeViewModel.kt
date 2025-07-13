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
    
    private fun loadTrendingSongs() {
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
        viewModelScope.launch {
            _recommendedSongsState.value = UiState.Loading
            try {
                val genres = preferences.genres.joinToString(",")
                val languages = preferences.languages.joinToString(",")
                val artists = preferences.artists.joinToString(",")
                musicRepository.getRecommendedSongs(genres, languages, artists)
                    .catch { e ->
                        Log.e("HomeViewModel", "Error loading recommendations", e)
                        _recommendedSongsState.value = UiState.Error("Failed to load recommendations: ${e.message}")
                    }
                    .collect { songs ->
                    if (songs.isEmpty()) {
                        _recommendedSongsState.value = UiState.Error("No recommended songs found")
                    } else {
                        _recommendedSongsState.value = UiState.Success(songs)
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error in loadRecommendedSongs", e)
                _recommendedSongsState.value = UiState.Error("Failed to load recommendations: ${e.message}")
            }
        }
    }

    fun refreshTrendingSongs() {
        loadTrendingSongs()
    }
    
    fun refreshRecommendedSongs(preferences: UserMusicPreferences) {
        loadRecommendedSongs(preferences)
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
} 
 