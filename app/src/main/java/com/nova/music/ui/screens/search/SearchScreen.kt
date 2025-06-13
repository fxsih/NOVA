package com.nova.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nova.music.data.model.Song
import com.nova.music.ui.viewmodels.HomeViewModel
import com.nova.music.ui.viewmodels.LibraryViewModel
import com.nova.music.ui.components.SongItem
import com.nova.music.ui.components.PlaylistSelectionDialog
import com.nova.music.ui.components.SearchBar
import com.nova.music.ui.viewmodels.SearchViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onSongClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val searchResults by viewModel.searchResults.collectAsState(initial = emptyList())
    val recentSearches by viewModel.recentSearches.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
    ) {
        SearchBar(
            query = searchQuery,
            onQueryChange = { query ->
                searchQuery = query
                scope.launch {
                    viewModel.search(query)
                }
            },
            onSearch = {
                if (searchQuery.isNotBlank()) {
                    hasSearched = true
                    scope.launch {
                        viewModel.addToRecentSearches(searchQuery)
                    }
                }
            },
            active = isSearchActive,
            onActiveChange = { isSearchActive = it },
            modifier = Modifier.fillMaxWidth(),
            hasRecentSearches = recentSearches.isNotEmpty()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when {
                searchQuery.isNotEmpty() -> {
                    // Show search results
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults) { song ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSongClick(song.id) },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF282828)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = song.title,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = song.artist,
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                hasSearched && recentSearches.isNotEmpty() -> {
                    // Show recent searches
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = "Recent Searches",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        items(recentSearches.filter { it.isNotBlank() }) { search ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF282828)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchQuery = search
                                            scope.launch {
                                                viewModel.search(search)
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "Recent search",
                                            tint = Color.White
                                        )
                                        Text(
                                            text = search,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                viewModel.removeFromRecentSearches(search)
                                                if (recentSearches.size <= 1) {
                                                    hasSearched = false
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove search",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
} 