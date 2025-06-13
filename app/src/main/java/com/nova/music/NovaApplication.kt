package com.nova.music

import android.app.Application
import com.nova.music.data.local.DatabaseInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NovaApplication : Application() {
    @Inject
    lateinit var databaseInitializer: DatabaseInitializer

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initializeDatabase()
    }

    private fun initializeDatabase() {
        applicationScope.launch {
            try {
                databaseInitializer.populateDatabase()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
} 