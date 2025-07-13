package com.nova.music.service

// This is just a snippet to fix the compilation error in MusicPlayerServiceImpl.kt
// Copy this method to replace the existing one in MusicPlayerServiceImpl.kt

/*
override suspend fun playQueueItemAt(index: Int) {
    withContext(Dispatchers.IO) {
        try {
            _error.value = null
            withContext(Dispatchers.Main) {
                ensurePlayerCreated()
                
                // Remember playback state
                val wasPlaying = if (exoPlayer?.isPlaying == true) {
                    true
                } else {
                    false
                }
                
                Log.d(TAG, "Playing queue item at index $index, wasPlaying=$wasPlaying")
                
                // Seek to the specified index
                if (index >= 0 && index < (exoPlayer?.mediaItemCount ?: 0)) {
                    exoPlayer?.seekTo(index, 0)
                    
                    // Ensure playback starts immediately
                    exoPlayer?.playWhenReady = true
                    exoPlayer?.play()
                    
                    // Update the current song
                    if (currentQueueInternal.isNotEmpty() && index < currentQueueInternal.size) {
                        _currentSong.value = currentQueueInternal[index]
                        Log.d(TAG, "Updated current song to: ${_currentSong.value?.title}")
                    }
                } else {
                    Log.e(TAG, "Invalid queue index: $index, queue size: ${exoPlayer?.mediaItemCount}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in playQueueItemAt", e)
            _error.value = "Error playing queue item: ${e.message}"
        }
    }
}
*/ 