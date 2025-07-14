package com.nova.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.music.data.model.Song
import com.nova.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Added to track the current album or artist being viewed
    private val _currentFilterItem = MutableStateFlow<String?>(null)
    val currentFilterItem: StateFlow<String?> = _currentFilterItem.asStateFlow()

    init {
        viewModelScope.launch {
            _recentSearches.value = musicRepository.getRecentSearches()
        }
    }

    suspend fun search(query: String, filter: String = "all") {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        
        _isLoading.value = true
        _errorMessage.value = null
        _currentFilterItem.value = null
        
        try {
            Log.d("SearchViewModel", "Searching for '$query' with filter '$filter'")
            
            // Apply filter based on the selected type
            val results = when (filter) {
                "artists" -> {
                    // Filter results to only include songs by artists matching the query
                    musicRepository.searchSongs(query).map { songs ->
                        songs.filter { song -> 
                            song.artist.contains(query, ignoreCase = true)
                        }
                    }
                }
                "albums" -> {
                    // Filter results to only include songs from albums matching the query
                    musicRepository.searchSongs(query).map { songs ->
                        songs.filter { song -> 
                            song.album.contains(query, ignoreCase = true)
                        }
                    }
                }
                "songs" -> {
                    // Filter results to only include songs with titles matching the query
                    musicRepository.searchSongs(query).map { songs ->
                        songs.filter { song -> 
                            song.title.contains(query, ignoreCase = true)
                        }
                    }
                }
                else -> {
                    // No filtering, return all results
                    musicRepository.searchSongs(query)
                }
            }
            
            results.collect {
                _searchResults.value = it
                Log.d("SearchViewModel", "Search returned ${it.size} results")
            }
        } catch (e: Exception) {
            Log.e("SearchViewModel", "Search failed", e)
            _errorMessage.value = "Search failed: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    // New function to search for songs by a specific album
    suspend fun searchByAlbum(albumName: String) {
        _isLoading.value = true
        _errorMessage.value = null
        _currentFilterItem.value = albumName
        
        try {
            Log.d("SearchViewModel", "Searching for songs in album: '$albumName'")
            
            // First try to search with the album name to get better results
            val albumResults = musicRepository.searchSongs(albumName)
            
            // Then filter by exact album name match
            albumResults.collect { allSongs ->
                val filteredSongs = allSongs.filter { song -> 
                    song.album.equals(albumName, ignoreCase = true)
                }
                
                if (filteredSongs.isNotEmpty()) {
                    // If we found songs, use them
                    _searchResults.value = filteredSongs
                    Log.d("SearchViewModel", "Album search returned ${filteredSongs.size} results")
                } else {
                    // If no exact matches, try a more lenient search
                    val lenientResults = allSongs.filter { song ->
                        song.album.contains(albumName, ignoreCase = true)
                    }
                    _searchResults.value = lenientResults
                    Log.d("SearchViewModel", "Album search (lenient) returned ${lenientResults.size} results")
                }
            }
        } catch (e: Exception) {
            Log.e("SearchViewModel", "Album search failed", e)
            _errorMessage.value = "Search failed: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    // New function to search for songs by a specific artist
    suspend fun searchByArtist(artistName: String) {
        _isLoading.value = true
        _errorMessage.value = null
        _currentFilterItem.value = artistName
        
        try {
            Log.d("SearchViewModel", "Searching for songs by artist: '$artistName'")
            
            // First try to search with the artist name to get better results
            val artistResults = musicRepository.searchSongs(artistName)
            
            // Then filter by exact artist name match
            artistResults.collect { allSongs ->
                val filteredSongs = allSongs.filter { song -> 
                    song.artist.equals(artistName, ignoreCase = true)
                }
                
                if (filteredSongs.isNotEmpty()) {
                    // If we found songs, use them
                    _searchResults.value = filteredSongs
                    Log.d("SearchViewModel", "Artist search returned ${filteredSongs.size} results")
                } else {
                    // If no exact matches, try a more lenient search
                    val lenientResults = allSongs.filter { song ->
                        song.artist.contains(artistName, ignoreCase = true)
                    }
                    _searchResults.value = lenientResults
                    Log.d("SearchViewModel", "Artist search (lenient) returned ${lenientResults.size} results")
                }
            }
        } catch (e: Exception) {
            Log.e("SearchViewModel", "Artist search failed", e)
            _errorMessage.value = "Search failed: ${e.message}"
        } finally {
            _isLoading.value = false
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

    fun addToRecentlyPlayed(song: Song) {
        viewModelScope.launch {
            try {
                musicRepository.addToRecentlyPlayed(song)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add song to recently played: ${e.message}"
            }
        }
    }
}