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
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    val recommendedSongs = musicRepository.getRecommendedSongs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    val recentlyPlayed = musicRepository.getRecentlyPlayed()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches = _recentSearches.asStateFlow()

    init {
        viewModelScope.launch {
            musicRepository.getAllSongs().collect {
                _songs.value = it
            }
            _recentSearches.value = musicRepository.getRecentSearches()
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                musicRepository.getAllSongs().collect {
                    _songs.value = it
                }
            } else {
                musicRepository.searchSongs(query).collect {
                    _songs.value = it
                }
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
            musicRepository.addToRecentlyPlayed(song)
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
 