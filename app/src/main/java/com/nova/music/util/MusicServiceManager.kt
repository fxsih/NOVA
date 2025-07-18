package com.nova.music.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.nova.music.service.MediaNotificationManager
import com.nova.music.service.MusicPlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class to manage the music service operations
 */
@Singleton
class MusicServiceManager @Inject constructor() {
    
    /**
     * Stops the music player service and cancels any active notifications
     */
    suspend fun stopMusicService(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("MusicServiceManager", "Stopping music service...")
                
                // First try to use the service's public method via binding
                val serviceConnection = object : android.content.ServiceConnection {
                    override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
                        try {
                            val binder = service as MusicPlayerService.MusicPlayerBinder
                            val musicService = binder.getService()
                            
                            // Store a reference to this connection for unbinding
                            val connection = this
                            
                            // Launch a coroutine to call suspend functions
                            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                                try {
                                    // First stop playback and clear current song
                                    musicService.stop()
                                    musicService.clearCurrentSong()
                                    
                                    // Then stop the service itself
                                    withContext(Dispatchers.Main) {
                                        val serviceIntent = Intent(context, MusicPlayerService::class.java)
                                        context.stopService(serviceIntent)
                                        context.unbindService(connection)
                                        Log.d("MusicServiceManager", "Successfully stopped service via binding")
                                    }
                                } catch (e: Exception) {
                                    Log.e("MusicServiceManager", "Error in coroutine stopping service", e)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MusicServiceManager", "Error stopping service via binding", e)
                        }
                    }
                    
                    override fun onServiceDisconnected(name: android.content.ComponentName?) {
                        Log.d("MusicServiceManager", "Service disconnected")
                    }
                }
                
                // Try to bind to the service
                val bindIntent = Intent(context, MusicPlayerService::class.java)
                val bindSuccessful = context.bindService(
                    bindIntent, 
                    serviceConnection, 
                    Context.BIND_AUTO_CREATE
                )
                
                if (!bindSuccessful) {
                    Log.d("MusicServiceManager", "Binding unsuccessful, trying alternative method")
                    
                    // If binding fails, use the stop action and then stop the service
                    val stopIntent = Intent(context, MusicPlayerService::class.java).apply {
                        action = MediaNotificationManager.ACTION_STOP
                    }
                    context.startService(stopIntent)
                    
                    // Give a moment for the stop action to be processed
                    kotlinx.coroutines.delay(300)
                    
                    // Then stop the service completely
                    val serviceIntent = Intent(context, MusicPlayerService::class.java)
                    context.stopService(serviceIntent)
                }
                
                Log.d("MusicServiceManager", "Music service stop process initiated")
            } catch (e: Exception) {
                Log.e("MusicServiceManager", "Error stopping music service", e)
                
                // Fallback to just stopping the service
                try {
                    val serviceIntent = Intent(context, MusicPlayerService::class.java)
                    context.stopService(serviceIntent)
                    Log.d("MusicServiceManager", "Used fallback method to stop service")
                } catch (e2: Exception) {
                    Log.e("MusicServiceManager", "Fallback stop also failed", e2)
                }
            }
        }
    }
} 