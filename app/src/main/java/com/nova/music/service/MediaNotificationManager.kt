package com.nova.music.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.target.Target
import com.nova.music.MainActivity
import com.nova.music.R
import com.nova.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop

/**
 * Manages media notifications for the music player service
 */
class MediaNotificationManager(
    private val context: Context,
    private val mediaSession: MediaSessionCompat
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val imageLoader = ImageLoader(context)
    
    private var currentSong: Song? = null
    private var isPlaying = false
    
    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "nova_music_channel"
        const val CHANNEL_NAME = "NOVA Music Player"
        
        // Action constants for notification buttons
        const val ACTION_PLAY = "com.nova.music.ACTION_PLAY"
        const val ACTION_PAUSE = "com.nova.music.ACTION_PAUSE"
        const val ACTION_NEXT = "com.nova.music.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.nova.music.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.nova.music.ACTION_STOP"
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        Log.d("MediaNotificationMgr", "Creating notification channel")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH // Change to HIGH for better visibility
            ).apply {
                description = "Music playback controls"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(false)
                enableLights(false)
            }
            
            try {
                notificationManager.createNotificationChannel(channel)
                Log.d("MediaNotificationMgr", "Notification channel created successfully")
            } catch (e: Exception) {
                Log.e("MediaNotificationMgr", "Error creating notification channel", e)
            }
        }
    }
    
    /**
     * Updates the notification with the current song and playback state
     */
    fun updateNotification(song: Song, isPlaying: Boolean): Notification {
        Log.d("MediaNotificationMgr", "Updating notification for song: ${song.title}, isPlaying: $isPlaying")
        Log.d("MediaNotificationMgr", "Album art sources - albumArtUrl: ${song.albumArtUrl}, albumArt: ${song.albumArt}")
        
        this.currentSong = song
        this.isPlaying = isPlaying
        
        val notification = buildNotification()
                try {
                    notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d("MediaNotificationMgr", "Notification posted successfully")
        } catch (e: Exception) {
            Log.e("MediaNotificationMgr", "Error posting notification", e)
        }
        return notification
    }
    
    /**
     * Builds the media notification with playback controls
     */
    private fun buildNotification(): Notification {
        val song = currentSong ?: return createEmptyNotification()
        Log.d("MediaNotificationMgr", "Building notification for: ${song.title}")
        
        // Create content intent that navigates to the full player
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                // Add flags to navigate to player screen
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                // Add data to indicate we want to open the player
                data = Uri.parse("nova://player")
                putExtra("openPlayer", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create action intents
        val playPauseIntent = createActionIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY)
        val nextIntent = createActionIntent(ACTION_NEXT)
        val prevIntent = createActionIntent(ACTION_PREVIOUS)
        
        // Build the notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MAX) // Use MAX for better visibility
            .setOnlyAlertOnce(true)
            
        // Set default album art initially
        try {
            val defaultArtDrawable = ContextCompat.getDrawable(context, R.drawable.default_album_art)
            defaultArtDrawable?.let {
                builder.setLargeIcon(it.toBitmap())
            }
        } catch (e: Exception) {
            Log.e("MediaNotificationMgr", "Error setting default album art", e)
        }
            
        // Add actions based on current state - only show prev, play/pause, next
        // These will be centered in the notification
        builder.addAction(R.drawable.ic_skip_previous, "Previous", prevIntent)
        
        // Play/Pause action
        if (isPlaying) {
            builder.addAction(R.drawable.ic_pause, "Pause", playPauseIntent)
        } else {
            builder.addAction(R.drawable.ic_play, "Play", playPauseIntent)
        }
        
        builder.addAction(R.drawable.ic_skip_next, "Next", nextIntent)
        
        // Create a MediaStyle with seekbar enabled
        val mediaStyle = MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2) // Show all three actions in compact view
            .setShowCancelButton(true)
            
        // Apply the MediaStyle
        builder.setStyle(mediaStyle)
            .setColorized(true) // Use colorized notification
            .setOngoing(isPlaying) // Reinforce this setting
        
        // Load album art asynchronously
        loadAlbumArt(song, builder)
        
        return builder.build()
    }
    
    /**
     * Creates a PendingIntent for notification actions
     */
    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(action).apply {
            setPackage(context.packageName)
        }
        
        return PendingIntent.getBroadcast(
            context,
            getActionRequestCode(action),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    /**
     * Gets a unique request code for each action type
     */
    private fun getActionRequestCode(action: String): Int {
        return when (action) {
            ACTION_PLAY, ACTION_PAUSE -> 100
            ACTION_NEXT -> 101
            ACTION_PREVIOUS -> 102
            ACTION_STOP -> 103
            else -> 104
        }
    }
    
    /**
     * Loads album art asynchronously and updates the notification
     */
    private fun loadAlbumArt(song: Song, builder: NotificationCompat.Builder) {
        // Use albumArtUrl if available, otherwise use albumArt, similar to how it's done in UI components
        val albumArtSource = when {
            !song.albumArtUrl.isNullOrBlank() -> song.albumArtUrl
            !song.albumArt.isBlank() -> song.albumArt
            else -> null
        }
        
        if (albumArtSource == null) {
            // Use default album art
            Log.d("MediaNotificationMgr", "No album art source available for ${song.title}")
            try {
                val defaultArtDrawable = ContextCompat.getDrawable(context, R.drawable.default_album_art)
                defaultArtDrawable?.let {
                    builder.setLargeIcon(it.toBitmap())
                    notificationManager.notify(NOTIFICATION_ID, builder.build())
                }
            } catch (e: Exception) {
                Log.e("MediaNotificationMgr", "Error setting default album art", e)
            }
            return
        }
        
        // Try to load the album art directly using Glide for better compatibility with notifications
        try {
            Log.d("MediaNotificationMgr", "Loading album art from: $albumArtSource")
            
            // Use Glide to load the bitmap directly (more reliable for notifications)
            val futureTarget = com.bumptech.glide.Glide.with(context)
                .asBitmap()
                .load(albumArtSource)
                .transform(CenterCrop()) // Center crop for consistency
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .submit(512, 512)  // Reasonable size for notification
        
        serviceScope.launch {
            try {
                    // Get the bitmap on a background thread
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            futureTarget.get()
                        } catch (e: Exception) {
                            Log.e("MediaNotificationMgr", "Error getting bitmap from Glide", e)
                            null
                        }
                    }
                    
                    // Clean up the future target
                    com.bumptech.glide.Glide.with(context).clear(futureTarget)
                    
                    if (bitmap != null) {
                        Log.d("MediaNotificationMgr", "Loaded bitmap for notification: ${bitmap.width}x${bitmap.height}")
                        val croppedBitmap = centerCropBitmap(bitmap)
                        builder.setLargeIcon(croppedBitmap)
                        notificationManager.notify(NOTIFICATION_ID, builder.build())
                        Log.d("MediaNotificationMgr", "Album art loaded successfully with Glide and manual crop")
                    } else {
                        // Use default album art if Glide fails
                        val defaultArtDrawable = ContextCompat.getDrawable(context, R.drawable.default_album_art)
                        defaultArtDrawable?.let {
                            builder.setLargeIcon(it.toBitmap())
                            notificationManager.notify(NOTIFICATION_ID, builder.build())
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MediaNotificationMgr", "Error in Glide bitmap processing", e)
                            // Use default album art on error
                    try {
                        val defaultArtDrawable = ContextCompat.getDrawable(context, R.drawable.default_album_art)
                        defaultArtDrawable?.let {
                            builder.setLargeIcon(it.toBitmap())
                            notificationManager.notify(NOTIFICATION_ID, builder.build())
                        }
                    } catch (e2: Exception) {
                        Log.e("MediaNotificationMgr", "Error setting default album art", e2)
                    }
                }
                }
            } catch (e: Exception) {
            Log.e("MediaNotificationManager", "Error initializing Glide load", e)
            // Use default album art on error
            try {
                val defaultArtDrawable = ContextCompat.getDrawable(context, R.drawable.default_album_art)
                defaultArtDrawable?.let {
                    builder.setLargeIcon(it.toBitmap())
                    notificationManager.notify(NOTIFICATION_ID, builder.build())
                }
            } catch (e2: Exception) {
                Log.e("MediaNotificationMgr", "Error setting default album art", e2)
            }
        }
    }
    
    /**
     * Creates an empty notification for when no song is playing
     */
    private fun createEmptyNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle("NOVA Music")
            .setContentText("No song playing")
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
    
    /**
     * Cancels the current notification
     */
    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
    
    /**
     * Converts a Drawable to a Bitmap
     */
    private fun Drawable.toBitmap(): Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(
            intrinsicWidth.coerceAtLeast(1),
            intrinsicHeight.coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }

    private fun centerCropBitmap(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }
} 