package com.nova.music

import android.app.Application
import com.nova.music.data.local.DatabaseInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.app.Application.ActivityLifecycleCallbacks
import android.app.Activity
import android.os.Bundle

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
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var activityReferences = 0
            private var isActivityChangingConfigurations = false
            override fun onActivityStarted(activity: Activity) {
                if (++activityReferences == 1 && !isActivityChangingConfigurations) {
                    getSharedPreferences("nova_state", MODE_PRIVATE)
                        .edit().putBoolean("wasInBackground", false).apply()
                }
            }
            override fun onActivityStopped(activity: Activity) {
                isActivityChangingConfigurations = activity.isChangingConfigurations
                if (--activityReferences == 0 && !isActivityChangingConfigurations) {
                    getSharedPreferences("nova_state", MODE_PRIVATE)
                        .edit().putBoolean("wasInBackground", true).apply()
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityDestroyed(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
        })
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