package com.nova.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.music.data.model.Song
import com.nova.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    init {
        viewModelScope.launch {
            _recentSearches.value = musicRepository.getRecentSearches()
        }
    }

    suspend fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        musicRepository.searchSongs(query).collect {
            _searchResults.value = it
        }
    }

    suspend fun addToRecentSearches(query: String) {
        if (query.isBlank()) return
        musicRepository.addToRecentSearches(query)
        _recentSearches.value = musicRepository.getRecentSearches()
    }

    suspend fun removeFromRecentSearches(query: String) {
        musicRepository.removeFromRecentSearches(query)
        _recentSearches.value = musicRepository.getRecentSearches()
    }
} 