package com.nova.music.ui.util

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nova.music.ui.viewmodels.PlayerViewModel

@Composable
fun rememberDynamicBottomPadding(
    playerViewModel: PlayerViewModel = hiltViewModel()
): State<Int> {
    val currentSong by playerViewModel.currentSong.collectAsState()
    
    return remember(currentSong) {
        derivedStateOf {
            if (currentSong != null) {
                160 // Height when mini player is visible
            } else {
                80 // Height when only nav bar is visible
            }
        }
    }
} 