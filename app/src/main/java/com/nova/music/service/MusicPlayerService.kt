package com.nova.music.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
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
import kotlinx.coroutines.withContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

@AndroidEntryPoint
class MusicPlayerService : Service() {
    @Inject
    lateinit var musicPlayerServiceImpl: MusicPlayerServiceImpl
    @Inject
    lateinit var dataStore: DataStore<Preferences>
    
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
        Log.d(TAG, "=== SERVICE onCreate ===")
        
        // Start foreground immediately to prevent timeout crash
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val initialNotification = NotificationCompat.Builder(this, MediaNotificationManager.CHANNEL_ID)
                .setContentTitle("NOVA Music")
                .setContentText("Starting music service...")
                .setSmallIcon(R.drawable.ic_music_note)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            
            startForeground(MediaNotificationManager.NOTIFICATION_ID, initialNotification)
            Log.d(TAG, "Started foreground service immediately")
        }
        
        // Initialize MediaSession
        initMediaSession()
        
        // Initialize notification manager
        notificationManager = MediaNotificationManager(this, mediaSession)
        
        // Start collecting song and playback state changes
        observePlaybackState()
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
                        musicPlayerServiceImpl.pause()
                        // Do NOT call stopSelf() here; let the service and mini player remain
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
        
        // Observe progress changes - remove throttling for smooth seekbar
        serviceScope.launch {
            musicPlayerServiceImpl.progress.collectLatest { progress ->
                musicPlayerServiceImpl.currentSong.value?.let { song ->
                    // Update media session with new progress for smooth seekbar
                    updateMediaSessionProgress(song, progress)
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
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
            // Include duration for seekbar display
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, musicPlayerServiceImpl.duration.value)
        
        // Add album art URI to metadata
        when {
            !song.albumArtUrl.isNullOrBlank() -> {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, song.albumArtUrl)
                // We'll load the actual bitmap asynchronously
                loadAlbumArtForMediaSession(song.albumArtUrl, metadataBuilder)
            }
            !song.albumArt.isBlank() -> {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, song.albumArt)
                // We'll load the actual bitmap asynchronously
                loadAlbumArtForMediaSession(song.albumArt, metadataBuilder)
            }
        }
        
        // Set the metadata initially without bitmap (will be updated when bitmap is loaded)
        mediaSession.setMetadata(metadataBuilder.build())
        
        // Get current position for progress tracking with validation
        val duration = musicPlayerServiceImpl.duration.value
        val progress = musicPlayerServiceImpl.progress.value
        val currentPosition = if (duration > 0 && progress >= 0f && progress <= 1f) {
            (progress * duration).toLong()
        } else {
            0L
        }
        
        // Set playback state with progress information
        val stateBuilder = android.support.v4.media.session.PlaybackStateCompat.Builder()
            .setState(
                if (musicPlayerServiceImpl.isPlaying.value) 
                    android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
                else 
                    android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED,
                currentPosition, // Use validated position
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
    
    /**
     * Loads album art bitmap and updates the media session metadata
     */
    private fun loadAlbumArtForMediaSession(artUrl: String, metadataBuilder: MediaMetadataCompat.Builder) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Loading album art for media session: $artUrl")
                
                // Use Glide to load the bitmap directly (more reliable for media session)
                val futureTarget = com.bumptech.glide.Glide.with(this@MusicPlayerService)
                    .asBitmap()
                    .load(artUrl)
                    .submit(512, 512)  // Reasonable size for media session
                
                try {
                    // Get the bitmap on a background thread
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            futureTarget.get()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting bitmap from Glide for media session", e)
                            null
                        }
                    }
                    
                    // Clean up the future target
                    com.bumptech.glide.Glide.with(this@MusicPlayerService).clear(futureTarget)
                    
                    if (bitmap != null) {
                        // Update metadata with bitmap
                        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
                        
                        // Update media session with new metadata
                        mediaSession.setMetadata(metadataBuilder.build())
                        Log.d(TAG, "Album art loaded successfully for media session")
                    } else {
                        Log.e(TAG, "Failed to load album art for media session, bitmap is null")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in Glide bitmap processing for media session", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading album art for media session", e)
            }
        }
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
        Log.d(TAG, "=== SERVICE onStartCommand ===")
        Log.d(TAG, "Action: ${intent?.action}, startId: $startId")
        Log.d(TAG, "Current song: ${musicPlayerServiceImpl.currentSong.value?.title}")
        Log.d(TAG, "Is playing: ${musicPlayerServiceImpl.isPlaying.value}")
        
        // Reset media session state to ensure clean state after app kill
        resetMediaSessionState()
        
        // Restore player state if service is restarted
        serviceScope.launch {
            musicPlayerServiceImpl.restorePlayerState()
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
        
        Log.d(TAG, "Returning START_STICKY")
        return START_STICKY
    }
    
    /**
     * Resets the media session state to ensure clean state after app kill
     */
    private fun resetMediaSessionState() {
        try {
            Log.d(TAG, "Resetting media session state")
            
            // Clear metadata
            mediaSession.setMetadata(MediaMetadataCompat.Builder().build())
            
            // Set playback state to stopped
            val stateBuilder = android.support.v4.media.session.PlaybackStateCompat.Builder()
                .setState(
                    android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED,
                    0L,
                    1.0f
                )
                .setActions(
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY or
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            
            mediaSession.setPlaybackState(stateBuilder.build())
            
            Log.d(TAG, "Media session state reset completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting media session state", e)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        Log.d(TAG, "=== SERVICE onDestroy ===")
        Log.d(TAG, "Current song: ${musicPlayerServiceImpl.currentSong.value?.title}")
        Log.d(TAG, "Is playing: ${musicPlayerServiceImpl.isPlaying.value}")
        Log.d(TAG, "Service being destroyed - this should only happen on swipe-kill or explicit stop")
        
        // Cancel the notification to prevent stale notifications
        notificationManager.cancelNotification()
        
        // Stop foreground service to remove notification
        stopForeground(true)
        
        // Release resources
        serviceJob.cancel()
        mediaSession.release()
        
        super.onDestroy()
        musicPlayerServiceImpl.onDestroy()
    }
    
    /**
     * Called when the user swipes the app away from the recent apps list
     * This will stop music playback and clean up resources
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "=== onTaskRemoved ===")
        Log.d(TAG, "App removed from recent apps, stopping playback and releasing player")
        
        NovaSessionManager.onTaskRemoved(
            applicationContext,
            clearPlaybackState = {
                // Cleanup must run on the main thread for ExoPlayer
                CoroutineScope(Dispatchers.Main).launch {
                    musicPlayerServiceImpl.stop()
                    musicPlayerServiceImpl.onDestroy()
                }
            },
            stopService = {
                // Cancel notification first
                notificationManager.cancelNotification()
                // Stop foreground service
                stopForeground(true)
                // Stop the service
                stopSelf()
            }
        )
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Updates only the playback state with progress for smooth seekbar movement
     */
    private fun updateMediaSessionProgress(song: Song, progress: Float) {
        try {
            val duration = musicPlayerServiceImpl.duration.value
            val currentPosition = if (duration > 0 && progress >= 0f && progress <= 1f) {
                (progress * duration).toLong()
            } else {
                0L
            }
            
            // Update only the playback state with new position
            val stateBuilder = android.support.v4.media.session.PlaybackStateCompat.Builder()
                .setState(
                    if (musicPlayerServiceImpl.isPlaying.value) 
                        android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
                    else 
                        android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED,
                    currentPosition,
                    1.0f
                )
                .setActions(
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY or
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE or
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO or
                    android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
                .setBufferedPosition(duration)
            
            mediaSession.setPlaybackState(stateBuilder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error updating media session progress", e)
        }
    }
} 