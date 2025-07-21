package com.nova.music

import android.Manifest
import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.nova.music.ui.navigation.NovaNavigation
import com.nova.music.ui.screens.home.LocalPreferenceManager
import com.nova.music.ui.theme.NovaTheme
import com.nova.music.ui.viewmodels.PlayerViewModel
import com.nova.music.ui.viewmodels.LibraryViewModel
import com.nova.music.util.PreferenceManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.nova.music.service.NovaSessionManager

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var preferenceManager: PreferenceManager
    @Inject
    lateinit var dataStore: DataStore<Preferences>
    
    // Get the ViewModels
    private val playerViewModel: PlayerViewModel by viewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()
    
    // Permission launcher for storage access
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, continue with app
            Toast.makeText(this, "Storage permission granted for downloads", Toast.LENGTH_SHORT).show()
        } else {
            // Permission denied, inform the user
            Toast.makeText(this, "Storage permission denied. Downloads may not work properly.", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("nova_state", MODE_PRIVATE)
        val wasKilled = prefs.getBoolean("wasKilledFromRecents", false)
        if (wasKilled) {
            prefs.edit().putBoolean("wasKilledFromRecents", false).apply()
            // Optionally show splash or clear state here
            recreate() // Or implement a more robust restart/clean logic
            return
        }
        
        // Check if we should open the player from notification click
        val shouldOpenPlayer = intent?.getBooleanExtra("openPlayer", false) == true || 
                              intent?.data?.toString() == "nova://player"
        
        // Make the app fullscreen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Reset the full player shown flag when the app is launched
        preferenceManager.resetFullPlayerShownFlag()
        
        // Check and request storage permission for downloads
        checkAndRequestStoragePermission()
        
        // Restore player state when app starts (in case service is still running)
        Log.d("MainActivity", "=== CALLING RESTORE PLAYER STATE FROM ONCREATE ===")
        playerViewModel.restorePlayerState(this)
        
        // Verify downloaded songs on app start
        playerViewModel.verifyDownloadedSongs(this)
        
        // Also update the current song's download state
        playerViewModel.updateCurrentSongDownloadState(this)
        
        // For Android 13+ (API level 33+), request notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
        
        setContent {
            AppContent(preferenceManager, shouldOpenPlayer)
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Restore player state when app comes back to foreground
        Log.d("MainActivity", "=== CALLING RESTORE PLAYER STATE FROM ONRESUME ===")
        playerViewModel.restorePlayerState(this)
        
        // Also verify downloads when app comes back to foreground
        playerViewModel.verifyDownloadedSongs(this)
        
        // Update the current song's download state
        playerViewModel.updateCurrentSongDownloadState(this)
        NovaSessionManager.onAppForegrounded()
    }

    override fun onPause() {
        super.onPause()
        NovaSessionManager.onAppBackgrounded(
            this,
            isPlaying = playerViewModel.isPlaying.value
        )
    }

    override fun onStop() {
        super.onStop()
        // Removed call to savePlayerState
    }
    
    private fun checkAndRequestStoragePermission() {
        // Only request storage permission on Android 10 (API 29) and below
        // For Android 11+, we use the MediaStore API which doesn't require permission
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    // Show rationale if needed
                    Toast.makeText(
                        this,
                        "Storage permission is needed to download songs",
                        Toast.LENGTH_LONG
                    ).show()
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                else -> {
                    // Request permission
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    // Add this method to request notification permission
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }
}

@Composable
private fun AppContent(preferenceManager: PreferenceManager, shouldOpenPlayer: Boolean) {
    NovaTheme {
        CompositionLocalProvider(LocalPreferenceManager provides preferenceManager) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                NovaNavigation(shouldOpenPlayer = shouldOpenPlayer)
            }
        }
    }
}