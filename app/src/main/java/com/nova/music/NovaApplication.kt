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

    // Private instance scope
    private val _applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Companion object to expose the application scope
    companion object {
        // Static application scope for operations that should survive configuration changes
        val applicationScope = CoroutineScope(SupervisorJob())
        
        // Static instance of the application for use in ViewModels
        lateinit var instance: NovaApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeDatabase()
    }

    private fun initializeDatabase() {
        _applicationScope.launch {
            try {
                databaseInitializer.populateDatabase()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
} 