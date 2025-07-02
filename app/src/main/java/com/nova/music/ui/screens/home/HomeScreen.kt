package com.nova.music.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nova.music.data.model.Song
import com.nova.music.ui.viewmodels.HomeViewModel
import com.nova.music.ui.viewmodels.LibraryViewModel
import com.nova.music.ui.viewmodels.UiState
import coil.compose.AsyncImage
import com.nova.music.ui.components.RecentlyPlayedItem
import com.nova.music.ui.components.RecommendedSongCard
import com.nova.music.ui.components.PlaylistSelectionDialog
import com.nova.music.ui.util.rememberDynamicBottomPadding
import kotlinx.coroutines.launch
import com.nova.music.util.TimeUtils.formatDuration
import com.nova.music.data.model.UserMusicPreferences
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.DialogProperties
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.nova.music.util.CenterCropSquareTransformation
import com.nova.music.ui.viewmodels.PlayerViewModel
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val songs by viewModel.songs.collectAsState()
    val recommendedSongsState by viewModel.recommendedSongsState.collectAsState()
    val trendingSongsState by viewModel.trendingSongsState.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val likedSongs by libraryViewModel.likedSongs.collectAsState()
    val playlists by libraryViewModel.playlists.collectAsState()
    
    // Get current playing song from PlayerViewModel
    val currentPlayingSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var detailsSong by remember { mutableStateOf<Song?>(null) }
    val userPreferences by libraryViewModel.userPreferences.collectAsState()
    var showOnboarding by remember { mutableStateOf(userPreferences.genres.isEmpty() && userPreferences.languages.isEmpty() && userPreferences.artists.isEmpty()) }
    var selectedGenres by remember { mutableStateOf(listOf<String>()) }
    var selectedLanguages by remember { mutableStateOf(listOf<String>()) }
    var selectedArtists by remember { mutableStateOf(listOf<String>()) }
    
    val bottomPadding by rememberDynamicBottomPadding()

    // Load recommendations only when preferences change or initially
    LaunchedEffect(userPreferences) {
        // Only load if we have preferences and no recommendations yet or explicit refresh needed
        if ((userPreferences.genres.isNotEmpty() || userPreferences.languages.isNotEmpty() || userPreferences.artists.isNotEmpty()) && 
            (recommendedSongsState is UiState.Loading || recommendedSongsState is UiState.Error)) {
            viewModel.loadRecommendedSongs(userPreferences)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    text = "NOVA",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding.dp)
        ) {
            // Error Message
            if (errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = errorMessage ?: "Unknown error occurred",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            
            // Trending Songs Section
            item {
                Text(
                    text = "Trending Now",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )

                when (val state = trendingSongsState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Success -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(state.data.take(10)) { song ->
                                val isLiked = likedSongs.any { it.id == song.id }
                                val isSongPlaying = currentPlayingSong?.id == song.id && isPlaying
                                val isSelected = currentPlayingSong?.id == song.id && !isPlaying
                                RecommendedSongCard(
                                    song = song,
                                    onClick = { 
                                        viewModel.addToRecentlyPlayed(song)
                                        onNavigateToPlayer(song.id)
                                    },
                                    onLikeClick = {
                                        if (isLiked) {
                                            libraryViewModel.removeSongFromLiked(song.id)
                                        } else {
                                            libraryViewModel.addSongToLiked(song)
                                        }
                                    },
                                    onAddToPlaylist = {
                                        selectedSong = song
                                        showPlaylistDialog = true
                                    },
                                    isLiked = isLiked,
                                    onDetailsClick = {
                                        detailsSong = song
                                        showDetailsDialog = true
                                    },
                                    onRemoveFromPlaylist = null,
                                    isPlaying = isSongPlaying,
                                    isSelected = isSelected,
                                    onPlayPause = {
                                        if (currentPlayingSong?.id == song.id) {
                                            playerViewModel.togglePlayPause()
                                        } else {
                                            viewModel.addToRecentlyPlayed(song)
                                            onNavigateToPlayer(song.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    is UiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { viewModel.refreshTrendingSongs() }) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Recommended",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )

                // Recommended Songs
                when (val state = recommendedSongsState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Success -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(state.data) { song ->
                                val isLiked = likedSongs.any { it.id == song.id }
                                val isSongPlaying = currentPlayingSong?.id == song.id && isPlaying
                                val isSelected = currentPlayingSong?.id == song.id && !isPlaying
                                RecommendedSongCard(
                                    song = song,
                                    onClick = { 
                                        viewModel.addToRecentlyPlayed(song)
                                        onNavigateToPlayer(song.id)
                                    },
                                    onLikeClick = {
                                        if (isLiked) {
                                            libraryViewModel.removeSongFromLiked(song.id)
                                        } else {
                                            libraryViewModel.addSongToLiked(song)
                                        }
                                    },
                                    onAddToPlaylist = {
                                        selectedSong = song
                                        showPlaylistDialog = true
                                    },
                                    isLiked = isLiked,
                                    onDetailsClick = {
                                        detailsSong = song
                                        showDetailsDialog = true
                                    },
                                    onRemoveFromPlaylist = null,
                                    isPlaying = isSongPlaying,
                                    isSelected = isSelected,
                                    onPlayPause = {
                                        if (currentPlayingSong?.id == song.id) {
                                            playerViewModel.togglePlayPause()
                                        } else {
                                            viewModel.addToRecentlyPlayed(song)
                                            onNavigateToPlayer(song.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    is UiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { viewModel.refreshRecommendedSongs(userPreferences) }) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Recently Played",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
            }

            items(recentlyPlayed) { song ->
                val isLiked = likedSongs.any { it.id == song.id }
                val isSongPlaying = currentPlayingSong?.id == song.id && isPlaying
                val isSelected = currentPlayingSong?.id == song.id && !isPlaying
                RecentlyPlayedItem(
                    song = song,
                    onClick = { 
                        viewModel.addToRecentlyPlayed(song)
                        onNavigateToPlayer(song.id)
                    },
                    onLikeClick = {
                        if (isLiked) {
                            libraryViewModel.removeSongFromLiked(song.id)
                        } else {
                            libraryViewModel.addSongToLiked(song)
                        }
                    },
                    onAddToPlaylist = {
                        selectedSong = song
                        showPlaylistDialog = true
                    },
                    isLiked = isLiked,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onDetailsClick = {
                        detailsSong = song
                        showDetailsDialog = true
                    },
                    onRemoveFromPlaylist = null,
                    isPlaying = isSongPlaying,
                    isSelected = isSelected,
                    onPlayPause = {
                        if (currentPlayingSong?.id == song.id) {
                            playerViewModel.togglePlayPause()
                        } else {
                            viewModel.addToRecentlyPlayed(song)
                            onNavigateToPlayer(song.id)
                        }
                    }
                )
            }

            item {
                Text(
                    text = "Popular Tracks",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
            }

            when (val state = trendingSongsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Success -> {
                    items(state.data) { song ->
                        val isLiked = likedSongs.any { it.id == song.id }
                        val isSongPlaying = currentPlayingSong?.id == song.id && isPlaying
                        val isSelected = currentPlayingSong?.id == song.id && !isPlaying
                        RecentlyPlayedItem(
                            song = song,
                            onClick = { 
                                viewModel.addToRecentlyPlayed(song)
                                onNavigateToPlayer(song.id)
                            },
                            onLikeClick = {
                                if (isLiked) {
                                    libraryViewModel.removeSongFromLiked(song.id)
                                } else {
                                    libraryViewModel.addSongToLiked(song)
                                }
                            },
                            onAddToPlaylist = {
                                selectedSong = song
                                showPlaylistDialog = true
                            },
                            isLiked = isLiked,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            onDetailsClick = {
                                detailsSong = song
                                showDetailsDialog = true
                            },
                            onRemoveFromPlaylist = null,
                            isPlaying = isSongPlaying,
                            isSelected = isSelected,
                            onPlayPause = {
                                if (currentPlayingSong?.id == song.id) {
                                    playerViewModel.togglePlayPause()
                                } else {
                                    viewModel.addToRecentlyPlayed(song)
                                    onNavigateToPlayer(song.id)
                                }
                            }
                        )
                    }
                }
                is UiState.Error -> {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = state.message,
                                color = Color.Red
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.refreshTrendingSongs() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }

    // Add to Playlist Dialog
    if (showPlaylistDialog) {
        selectedSong?.let { song ->
            PlaylistSelectionDialog(
                onDismiss = { 
                    showPlaylistDialog = false
                    selectedSong = null
                },
                onPlaylistSelected = { playlist ->
                    scope.launch {
                        if (playlist.id == "liked_songs") {
                            val isLiked = likedSongs.any { it.id == song.id }
                            if (isLiked) {
                                libraryViewModel.removeSongFromLiked(song.id)
                            } else {
                                libraryViewModel.addSongToLiked(song)
                            }
                        } else {
                            libraryViewModel.addSongToPlaylist(song, playlist.id)
                        }
                    }
                },
                onCreateNewPlaylist = { name ->
                    scope.launch {
                        libraryViewModel.createPlaylist(name)
                        // Wait for the playlist to be created and get its ID
                        playlists.firstOrNull { it.name == name }?.let { newPlaylist ->
                            libraryViewModel.addSongToPlaylist(song, newPlaylist.id)
                        }
                    }
                },
                onRenamePlaylist = { playlist, newName ->
                    scope.launch {
                        libraryViewModel.renamePlaylist(playlist.id, newName)
                    }
                },
                playlists = playlists,
                selectedPlaylistIds = buildSet {
                    addAll(playlists
                        .filter { playlist -> playlist.songs.any { it.id == song.id } }
                        .map { it.id })
                    if (likedSongs.any { it.id == song.id }) add("liked_songs")
                }
            )
        }
    }

    if (showCreatePlaylistDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create New Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            scope.launch {
                                libraryViewModel.createPlaylist(playlistName)
                            }
                            showCreatePlaylistDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Song Details Dialog
    if (showDetailsDialog && detailsSong != null) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text("Song Details") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Title: ${detailsSong!!.title}")
                    Text("Artist: ${detailsSong!!.artist}")
                    Text("Album: ${detailsSong!!.album}")
                    Text("Duration: ${formatDuration(detailsSong!!.duration)}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            title = {
                Text(
                    "Personalize Your Recommendations",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // GENRES SECTION
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Select your favorite genres:",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 3
                        ) {
                            listOf("Pop", "Rock", "Hip Hop", "Electronic", "Jazz", "Classical", "R&B", "Country", "Indie").forEach { genre ->
                                val isSelected = selectedGenres.contains(genre)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedGenres = if (isSelected) {
                                            selectedGenres - genre
                                        } else {
                                            selectedGenres + genre
                                        }
                                    },
                                    label = { 
                                        Text(
                                            genre,
                                            style = MaterialTheme.typography.bodyMedium
                                        ) 
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFBB86FC),
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF2A2A2A)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = Color.Transparent,
                                        selectedBorderColor = Color.Transparent,
                                        enabled = true,
                                        selected = isSelected
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    }
                    
                    // LANGUAGES SECTION
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Select languages you prefer:",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 3
                        ) {
                            listOf("English", "Spanish", "Hindi", "Malayalam", "French", "Korean", "Japanese", "Chinese", "Arabic", "Portuguese").forEach { language ->
                                val isSelected = selectedLanguages.contains(language)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedLanguages = if (isSelected) {
                                            selectedLanguages - language
                                        } else {
                                            selectedLanguages + language
                                        }
                                    },
                                    label = { 
                                        Text(
                                            language,
                                            style = MaterialTheme.typography.bodyMedium
                                        ) 
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFBB86FC),
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF2A2A2A)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = Color.Transparent,
                                        selectedBorderColor = Color.Transparent,
                                        enabled = true,
                                        selected = isSelected
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    }
                    
                    // ARTISTS SECTION
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Select artists you like:",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 2
                        ) {
                            val allArtists = listOf(
                                "Taylor Swift", "Drake", "BTS", "Ed Sheeran", "Ariana Grande", 
                                "The Weeknd", "Billie Eilish", "Bad Bunny", "Justin Bieber",
                                "K.J. Yesudas", "K.S. Chithra", "Vidhu Prathap", "Sithara Krishnakumar", 
                                "Vineeth Sreenivasan", "Shreya Ghoshal"
                            )
                            allArtists.forEach { artist ->
                                val isSelected = selectedArtists.contains(artist)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedArtists = if (isSelected) {
                                            selectedArtists - artist
                                        } else {
                                            selectedArtists + artist
                                        }
                                    },
                                    label = { 
                                        Text(
                                            artist,
                                            style = MaterialTheme.typography.bodyMedium
                                        ) 
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFBB86FC),
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF2A2A2A)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = Color.Transparent,
                                        selectedBorderColor = Color.Transparent,
                                        enabled = true,
                                        selected = isSelected
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFF121212),
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val preferences = UserMusicPreferences(
                                genres = selectedGenres,
                                languages = selectedLanguages,
                                artists = selectedArtists
                            )
                            libraryViewModel.setUserPreferences(preferences)
                            viewModel.loadRecommendedSongs(preferences)
                            showOnboarding = false
                        }
                    },
                    enabled = selectedGenres.isNotEmpty() || selectedLanguages.isNotEmpty() || selectedArtists.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFBB86FC),
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFFBB86FC).copy(alpha = 0.3f),
                        disabledContentColor = Color.Black.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        "Save Preferences",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        )
    }
}

@Composable
fun RecommendedSongCard(
    song: Song,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    isLiked: Boolean,
    onDetailsClick: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    isPlaying: Boolean,
    isSelected: Boolean,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(160.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.albumArtUrl)
                    .crossfade(true)
                    .transformations(CenterCropSquareTransformation())
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .background(Color(0xFF2A2A2A).copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onLikeClick) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = if (isLiked) Color.Red else Color.White
                )
            }
            
            IconButton(onClick = onAddToPlaylist) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add to Playlist",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun RecentlyPlayedItem(
    song: Song,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    isLiked: Boolean,
    modifier: Modifier = Modifier,
    onDetailsClick: () -> Unit,
    onRemoveFromPlaylist: (Song?) -> Unit,
    isPlaying: Boolean,
    isSelected: Boolean,
    onPlayPause: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.albumArtUrl)
                    .crossfade(true)
                    .transformations(CenterCropSquareTransformation())
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF2A2A2A), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White
                )
            }
            
            IconButton(onClick = onLikeClick) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = if (isLiked) Color.Red else Color.White
                )
            }
            
            IconButton(onClick = onAddToPlaylist) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add to Playlist",
                    tint = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreChips(selected: List<String>, onSelectionChange: (List<String>) -> Unit) {
    val genres = listOf("Pop", "Rock", "Hip-Hop", "Classical", "Jazz", "EDM", "Bollywood", "Indie", "Folk", "Metal", "R&B", "Electronic", "Reggae", "Country", "Blues", "Punk")
    
    Column(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genres.forEach { genre ->
                FilterChip(
                    selected = selected.contains(genre),
                    onClick = {
                        val new = if (selected.contains(genre)) selected - genre else selected + genre
                        onSelectionChange(new)
                    },
                    label = { Text(genre) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageChips(selected: List<String>, onSelectionChange: (List<String>) -> Unit) {
    val languages = listOf("English", "Hindi", "Spanish", "Punjabi", "Tamil", "Telugu", "Malayalam", "French", "German", "Korean", "Japanese", "Arabic", "Portuguese", "Italian", "Russian", "Chinese")
    
    Column(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            languages.forEach { lang ->
                FilterChip(
                    selected = selected.contains(lang),
                    onClick = {
                        val new = if (selected.contains(lang)) selected - lang else selected + lang
                        onSelectionChange(new)
                    },
                    label = { Text(lang) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtistSuggestions(selectedLanguages: List<String>, selectedArtists: List<String>, onArtistSelectionChange: (List<String>) -> Unit) {
    val suggestedArtists = remember(selectedLanguages) {
        val artists = mutableListOf<String>()
        if (selectedLanguages.contains("English")) {
            artists.addAll(listOf("Taylor Swift", "Ed Sheeran", "Beyoncé", "The Weeknd", "Adele", "Drake", "Billie Eilish", "Post Malone"))
        }
        if (selectedLanguages.contains("Hindi")) {
            artists.addAll(listOf("Arijit Singh", "Shreya Ghoshal", "Neha Kakkar", "Badshah", "A.R. Rahman", "Jubin Nautiyal", "Atif Aslam"))
        }
        if (selectedLanguages.contains("Spanish")) {
            artists.addAll(listOf("Bad Bunny", "J Balvin", "Rosalía", "Daddy Yankee", "Shakira", "Maluma", "Karol G", "Enrique Iglesias"))
        }
        if (selectedLanguages.contains("Korean")) {
            artists.addAll(listOf("BTS", "BLACKPINK", "TWICE", "Stray Kids", "IU", "EXO", "NCT", "SEVENTEEN", "NewJeans"))
        }
        if (selectedLanguages.contains("Punjabi")) {
            artists.addAll(listOf("Diljit Dosanjh", "AP Dhillon", "Sidhu Moose Wala", "Guru Randhawa", "Jazzy B", "Honey Singh", "Karan Aujla"))
        }
        if (selectedLanguages.contains("Tamil")) {
            artists.addAll(listOf("A.R. Rahman", "Anirudh Ravichander", "Sid Sriram", "Yuvan Shankar Raja", "Shreya Ghoshal", "Dhanush"))
        }
        if (selectedLanguages.contains("Malayalam")) {
            artists.addAll(listOf("K.S. Chithra", "K.J. Yesudas", "Vidhu Prathap", "Sithara Krishnakumar", "Vineeth Sreenivasan", "Anne Amie", "Pradeep Kumar"))
        }
        if (selectedLanguages.contains("Telugu")) {
            artists.addAll(listOf("S. P. Balasubrahmanyam", "S. Janaki", "Devi Sri Prasad", "Thaman S", "Sid Sriram", "Anirudh Ravichander"))
        }
        if (selectedLanguages.contains("French")) {
            artists.addAll(listOf("Stromae", "Aya Nakamura", "Indila", "Maître Gims", "Zaz", "Angèle", "Dadju", "MC Solaar"))
        }
        if (selectedLanguages.contains("German")) {
            artists.addAll(listOf("Rammstein", "Kraftwerk", "Nena", "Mark Forster", "Sarah Connor", "Helene Fischer", "Falco"))
        }
        if (selectedLanguages.contains("Japanese")) {
            artists.addAll(listOf("YOASOBI", "Official HIGE DANdism", "LiSA", "ONE OK ROCK", "Kenshi Yonezu", "Radwimps", "BABYMETAL"))
        }
        if (selectedLanguages.contains("Arabic")) {
            artists.addAll(listOf("Amr Diab", "Nancy Ajram", "Elissa", "Mohamed Ramadan", "Tamer Hosny", "Najwa Karam"))
        }
        if (selectedLanguages.contains("Portuguese")) {
            artists.addAll(listOf("Anitta", "Marília Mendonça", "Gusttavo Lima", "Jorge & Mateus", "Luísa Sonza", "Alok"))
        }
        if (selectedLanguages.contains("Italian")) {
            artists.addAll(listOf("Laura Pausini", "Andrea Bocelli", "Måneskin", "Eros Ramazzotti", "Zucchero", "Giorgia", "Il Volo"))
        }
        if (selectedLanguages.contains("Russian")) {
            artists.addAll(listOf("Morgenshtern", "Little Big", "Zemfira", "JONY", "Kasta", "Scriptonite", "Polina Gagarina"))
        }
        if (selectedLanguages.contains("Chinese")) {
            artists.addAll(listOf("Jay Chou", "G.E.M.", "JJ Lin", "Teresa Teng", "Lay Zhang", "Jackson Wang", "Kris Wu"))
        }
        if (selectedLanguages.isEmpty()) {
            artists.addAll(listOf("Taylor Swift", "Ed Sheeran", "Arijit Singh", "BTS", "Bad Bunny", "The Weeknd", "Adele", "Drake", "Beyoncé", "Blackpink", "Billie Eilish"))
        }
        artists.distinct()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Suggested Artists",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestedArtists.forEach { artist ->
                FilterChip(
                    selected = selectedArtists.contains(artist),
                    onClick = {
                        val new = if (selectedArtists.contains(artist)) selectedArtists - artist else selectedArtists + artist
                        onArtistSelectionChange(new)
                    },
                    label = { Text(artist) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}