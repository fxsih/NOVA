package com.nova.music.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nova.music.R
import com.nova.music.ui.viewmodels.AuthViewModel
import androidx.compose.foundation.clickable
import com.nova.music.ui.viewmodels.LibraryViewModel
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.nova.music.data.model.UserMusicPreferences
import kotlinx.coroutines.launch
import com.nova.music.ui.screens.home.LocalPreferenceManager
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.nova.music.ui.viewmodels.HomeViewModel
import android.util.Log
import android.app.Activity
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var username by remember { mutableStateOf(currentUser?.username ?: "") }
    var isEditing by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showPreferencesDialog by remember { mutableStateOf(false) }
    
    // Preferences state
    val userPreferences by libraryViewModel.userPreferences.collectAsState()
    var selectedGenres by remember { mutableStateOf(userPreferences.genres) }
    var selectedLanguages by remember { mutableStateOf(userPreferences.languages) }
    var selectedArtists by remember { mutableStateOf(userPreferences.artists) }
    
    // Function to restart the app
    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        (context as? Activity)?.finish()
    }
    
    // Update username when user changes
    LaunchedEffect(currentUser) {
        username = currentUser?.username ?: ""
    }
    
    // Handle auth state changes
    LaunchedEffect(authState) {
        if (authState is AuthViewModel.AuthState.SignedOut) {
            onSignOut()
        }
    }
    
    // Update selected preferences when userPreferences changes
    LaunchedEffect(userPreferences) {
        selectedGenres = userPreferences.genres
        selectedLanguages = userPreferences.languages
        selectedArtists = userPreferences.artists
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Profile picture
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(currentUser?.profilePictureUrl ?: R.drawable.default_album_art)
                    .crossfade(true)
                    .build(),
                contentDescription = "Profile Picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Username
            if (isEditing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(
                        onClick = {
                            viewModel.updateProfile(username)
                            isEditing = false
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save"
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = currentUser?.username ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = { isEditing = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Username"
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Email
            Text(
                text = currentUser?.email ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Account settings section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Account Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Change Preferences
                    ListItem(
                        headlineContent = { Text("Change Preferences") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Change Preferences"
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Go"
                            )
                        },
                        modifier = Modifier.clickable {
                            showPreferencesDialog = true
                        }
                    )
                    
                    Divider()
                    
                    // Sign out
                    ListItem(
                        headlineContent = { Text("Sign Out") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Sign Out"
                            )
                        },
                        modifier = Modifier.clickable {
                            showSignOutDialog = true
                        }
                    )
                }
            }
        }
    }
    
    // Sign out confirmation dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.signOut()
                        showSignOutDialog = false
                    }
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Preferences dialog
    if (showPreferencesDialog) {
        // Get the preference manager
        val preferenceManager = LocalPreferenceManager.current
        
        AlertDialog(
            onDismissRequest = { showPreferencesDialog = false },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            title = {
                Text(
                    "Personalize Your Recommendations",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
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
                            
                            // Mark onboarding as shown
                            preferenceManager.setOnboardingShown()
                            
                            // Force refresh of recommendations with new preferences
                            Log.d("ProfileScreen", "Refreshing recommendations with new preferences")
                            homeViewModel.refreshRecommendedSongs(preferences)
                            
                            showPreferencesDialog = false
                            
                            // Show toast message about restarting
                            android.widget.Toast.makeText(
                                context,
                                "Preferences updated. Restarting app to apply changes...",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            
                            // Add a small delay before restarting to allow the toast to be seen
                            kotlinx.coroutines.delay(1000)
                            
                            // Restart the app instead of navigating back
                            restartApp()
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
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPreferencesDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        )
    }
} 