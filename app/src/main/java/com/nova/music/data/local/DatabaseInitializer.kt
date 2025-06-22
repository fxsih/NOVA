package com.nova.music.data.local

import com.nova.music.data.model.Song
import javax.inject.Inject

class DatabaseInitializer @Inject constructor(
    private val musicDao: MusicDao
) {
    suspend fun populateDatabase() {
        // No mock data needed anymore since we're using the YouTube Music API
    }
} 