package com.nova.music.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver that handles media notification actions
 */
class MediaNotificationReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MediaNotifReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")
        
        // Forward the action to the service
        val serviceIntent = Intent(context, MusicPlayerService::class.java).apply {
            action = intent.action
        }
        
        try {
            // For Android 8.0+, we need to use startForegroundService
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Log.d(TAG, "Using startForegroundService for action: ${intent.action}")
                context.startForegroundService(serviceIntent)
            } else {
                Log.d(TAG, "Using startService for action: ${intent.action}")
                context.startService(serviceIntent)
            }
            Log.d(TAG, "Successfully forwarded action: ${intent.action} to service")
        } catch (e: Exception) {
            Log.e(TAG, "Error forwarding action to service", e)
        }
    }
} 