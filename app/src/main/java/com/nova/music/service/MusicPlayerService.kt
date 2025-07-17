package com.nova.music.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.MediaMetadataCompat
import com.nova.music.MainActivity
import com.nova.music.data.model.Song
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.os.Build
import com.nova.music.R
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MusicPlayerService : Service() {
    @Inject
    lateinit var musicPlayerServiceImpl: MusicPlayerServiceImpl
    
    private val binder = MusicPlayerBinder()
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: MediaNotificationManager
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private val TAG = "MusicPlayerService"
    
    companion object {
        private const val NOTIFICATION_ID = 1
    }

    inner class MusicPlayerBinder : Binder() {
        fun getService(): IMusicPlayerService = musicPlayerServiceImpl
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        // Initialize MediaSession
        initMediaSession()
        
        // Initialize notification manager
        notificationManager = MediaNotificationManager(this, mediaSession)
        
        // Start collecting song and playback state changes
        observePlaybackState()
        
        // Create an initial notification to start as foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val initialNotification = NotificationCompat.Builder(this, MediaNotificationManager.CHANNEL_ID)
                .setContentTitle("NOVA Music")
                .setContentText("Starting music service...")
                .setSmallIcon(R.drawable.ic_music_note)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            
            startForeground(MediaNotificationManager.NOTIFICATION_ID, initialNotification)
            Log.d(TAG, "Started with initial notification")
        }
    }
    
    private fun initMediaSession() {
        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, activityIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        mediaSession = MediaSessionCompat(this, "NovaMediaSession").apply {
            setSessionActivity(pendingIntent)
            isActive = true
            
            // Set callbacks for media button events
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    Log.d(TAG, "MediaSession callback: onPlay")
                    serviceScope.launch {
                        musicPlayerServiceImpl.resume()
                    }
                }
                
                override fun onPause() {
                    Log.d(TAG, "MediaSession callback: onPause")
                    serviceScope.launch {
                        musicPlayerServiceImpl.pause()
                    }
                }
                
                override fun onSkipToNext() {
                    Log.d(TAG, "MediaSession callback: onSkipToNext")
                    serviceScope.launch {
                        musicPlayerServiceImpl.skipToNext()
                    }
                }
                
                override fun onSkipToPrevious() {
                    Log.d(TAG, "MediaSession callback: onSkipToPrevious")
                    serviceScope.launch {
                        musicPlayerServiceImpl.skipToPrevious()
                    }
                }
                
                override fun onStop() {
                    Log.d(TAG, "MediaSession callback: onStop")
                    serviceScope.launch {
                        musicPlayerServiceImpl.stop()
            stopSelf()
                    }
                }
                
                override fun onSeekTo(pos: Long) {
                    Log.d(TAG, "MediaSession callback: onSeekTo to position $pos")
                    serviceScope.launch {
                        musicPlayerServiceImpl.seekTo(pos)
                    }
                }
            })
            
            // Set flags to enable seeking capabilities
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or 
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        }
    }
    
    private fun observePlaybackState() {
        // Observe current song changes
        serviceScope.launch {
            musicPlayerServiceImpl.currentSong.collectLatest { song ->
                song?.let {
                    updateMediaSession(it)
                    updateNotification(it, musicPlayerServiceImpl.isPlaying.value)
                }
            }
        }
        
        // Observe playback state changes
        serviceScope.launch {
            musicPlayerServiceImpl.isPlaying.collectLatest { isPlaying ->
                musicPlayerServiceImpl.currentSong.value?.let { song ->
                    updateNotification(song, isPlaying)
                    updateMediaSession(song) // Update media session when playback state changes
                }
            }
        }
        
        // Observe progress changes
        serviceScope.launch {
            musicPlayerServiceImpl.progress.collectLatest { progress ->
                musicPlayerServiceImpl.currentSong.value?.let { song ->
                    // Update media session with new progress
                    updateMediaSession(song)
                }
            }
        }
        
        // Observe duration changes
        serviceScope.launch {
            musicPlayerServiceImpl.duration.collectLatest { duration ->
                musicPlayerServiceImpl.currentSong.value?.let { song ->
                    // Update media session with new duration
                    updateMediaSession(song)
                }
            }
        }
    }
    
    private fun updateMediaSession(song: Song) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
            // Include duration for seekbar display
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, musicPlayerServiceImpl.duration.value)
            .build()
        
        mediaSession.setMetadata(metadata)
        
        // Get current position for progress tracking
        val currentPosition = (musicPlayerServiceImpl.progress.value * musicPlayerServiceImpl.duration.value).toLong()
        
        // Set playback state with progress information
        val stateBuilder = android.support.v4.media.session.PlaybackStateCompat.Builder()
            .setState(
                if (musicPlayerServiceImpl.isPlaying.value) 
                    android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
                else 
                    android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED,
                currentPosition, // Use actual position
                1.0f // Use normal playback speed
            )
            .setActions(
                android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY or
                android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE or
                android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO or
                android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            // Set buffering progress
            .setBufferedPosition(musicPlayerServiceImpl.duration.value)
        
        mediaSession.setPlaybackState(stateBuilder.build())
    }
    
    private fun updateNotification(song: Song, isPlaying: Boolean) {
        try {
            Log.d(TAG, "Updating notification, isPlaying: $isPlaying")
            val notification = notificationManager.updateNotification(song, isPlaying)
            
            if (isPlaying) {
                Log.d(TAG, "Starting foreground service with notification")
                startForeground(MediaNotificationManager.NOTIFICATION_ID, notification)
            } else {
                // If not playing, we can make the notification dismissible but keep the service running
                Log.d(TAG, "Stopping foreground but keeping notification")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    stopForeground(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand, action: ${intent?.action}")
        
        // Ensure we start as foreground service for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create a basic notification if we don't have a current song yet
            if (musicPlayerServiceImpl.currentSong.value == null) {
                val basicNotification = NotificationCompat.Builder(this, MediaNotificationManager.CHANNEL_ID)
                    .setContentTitle("NOVA Music")
                    .setContentText("Music service is running")
                    .setSmallIcon(R.drawable.ic_music_note)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                
                startForeground(MediaNotificationManager.NOTIFICATION_ID, basicNotification)
                Log.d(TAG, "Started with basic notification")
            }
        }
        
        // Handle notification actions
        when (intent?.action) {
            MediaNotificationManager.ACTION_PLAY -> {
                Log.d(TAG, "Received PLAY action")
                serviceScope.launch {
                    musicPlayerServiceImpl.resume()
                }
            }
            MediaNotificationManager.ACTION_PAUSE -> {
                Log.d(TAG, "Received PAUSE action")
                serviceScope.launch {
                    Log.d(TAG, "Executing pause in coroutine")
                    musicPlayerServiceImpl.pause()
                    Log.d(TAG, "Pause executed, isPlaying: ${musicPlayerServiceImpl.isPlaying.value}")
                }
            }
            MediaNotificationManager.ACTION_NEXT -> {
                Log.d(TAG, "Received NEXT action")
                serviceScope.launch {
                    musicPlayerServiceImpl.skipToNext()
                }
            }
            MediaNotificationManager.ACTION_PREVIOUS -> {
                Log.d(TAG, "Received PREVIOUS action")
                serviceScope.launch {
                    musicPlayerServiceImpl.skipToPrevious()
                }
            }
            MediaNotificationManager.ACTION_STOP -> {
                Log.d(TAG, "Received STOP action")
                serviceScope.launch {
                    musicPlayerServiceImpl.stop()
            stopSelf()
                }
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        
        // Release resources
        serviceJob.cancel()
            mediaSession.release()
        notificationManager.cancelNotification()
            
        super.onDestroy()
            musicPlayerServiceImpl.onDestroy()
    }
    
    /**
     * Called when the user swipes the app away from the recent apps list
     * This will stop music playback and clean up resources
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved: App removed from recent apps, stopping playback")
        
        try {
            // Stop playback immediately using runBlocking to call suspend function
            runBlocking {
                musicPlayerServiceImpl.stop()
            }
            
            // Call onDestroy directly to release player resources
            musicPlayerServiceImpl.onDestroy()
            
            // Cancel notification
            notificationManager.cancelNotification()
            
            // Release media session
            mediaSession.isActive = false
            mediaSession.release()
            
            // Cancel all coroutines
            serviceJob.cancel()
            
            // Stop the service
            stopForeground(true)
            stopSelf()
            
            Log.d(TAG, "Successfully stopped playback and released resources on task removed")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback on task removed", e)
        }
        
        super.onTaskRemoved(rootIntent)
    }
} 