package com.nova.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.nova.music.ui.navigation.NovaNavigation
import com.nova.music.ui.theme.NovaTheme
import com.nova.music.ui.viewmodels.PlayerViewModel
import com.nova.music.util.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.core.app.ActivityCompat
import android.Manifest.permission.POST_NOTIFICATIONS

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var preferenceManager: PreferenceManager
    
    // Get the PlayerViewModel
    private val playerViewModel: PlayerViewModel by viewModels()
    
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
        
        // Make the app fullscreen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Reset the full player shown flag when the app is launched
        preferenceManager.resetFullPlayerShownFlag()
        
        // Check and request storage permission for downloads
        checkAndRequestStoragePermission()
        
        // Verify downloaded songs on app start
        playerViewModel.verifyDownloadedSongs(this)
        
        // Also update the current song's download state
        playerViewModel.updateCurrentSongDownloadState(this)
        
        // For Android 13+ (API level 33+), request notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
        
        setContent {
            AppContent()
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Also verify downloads when app comes back to foreground
        playerViewModel.verifyDownloadedSongs(this)
        
        // Update the current song's download state
        playerViewModel.updateCurrentSongDownloadState(this)
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
private fun AppContent() {
    NovaTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NovaNavigation()
        }
    }
}