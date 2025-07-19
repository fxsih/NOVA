package com.nova.music.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nova.music.data.model.Song

object NovaSessionManager {
    private const val TAG = "NovaSessionManager"
    private const val PREFS_NAME = "nova_state"
    private const val KEY_WAS_SWIPE_KILLED = "wasSwipeKilled"
    private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

    private var idleHandler: Handler? = null
    private var idleRunnable: Runnable? = null

    fun onAppBackgrounded(context: Context, isPlaying: Boolean) {
        Log.d(TAG, "App backgrounded. isPlaying=$isPlaying")
        // Don't schedule idle timeout when app is backgrounded - let the service continue running
        // Only schedule timeout when playback actually stops
        if (!isPlaying) {
            Log.d(TAG, "Music not playing, but keeping service alive for potential restore")
        } else {
            cancelIdleTimeout()
        }
    }

    fun onAppForegrounded() {
        Log.d(TAG, "App foregrounded. Canceling idle timeout.")
        cancelIdleTimeout()
    }

    fun onTaskRemoved(context: Context, clearPlaybackState: () -> Unit, stopService: () -> Unit) {
        Log.d(TAG, "App swipe-killed — terminating service and clearing state")
        setWasSwipeKilled(context, true)
        clearPlaybackState()
        stopService()
    }

    fun onPlaybackStarted() {
        Log.d(TAG, "Playback started. Canceling idle timeout.")
        cancelIdleTimeout()
    }

    fun onPlaybackStopped(context: Context, stopService: () -> Unit) {
        Log.d(TAG, "Playback stopped. Scheduling idle timeout.")
        scheduleIdleTimeout(stopService)
    }

    fun clearPlaybackState(context: Context, clearQueue: () -> Unit) {
        Log.d(TAG, "Clearing playback state (queue, current song, etc.)")
        clearQueue()
        // Do NOT clear recently played
    }

    fun wasSwipeKilled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WAS_SWIPE_KILLED, false)
    }

    fun resetSwipeKilledFlag(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_WAS_SWIPE_KILLED, false).apply()
    }

    private fun setWasSwipeKilled(context: Context, value: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_WAS_SWIPE_KILLED, value).apply()
    }

    private fun scheduleIdleTimeout(stopService: () -> Unit) {
        cancelIdleTimeout()
        idleHandler = Handler(Looper.getMainLooper())
        idleRunnable = Runnable {
            Log.d(TAG, "Idle timeout reached. Stopping service.")
            stopService()
        }
        idleHandler?.postDelayed(idleRunnable!!, IDLE_TIMEOUT_MS)
    }

    private fun cancelIdleTimeout() {
        idleHandler?.removeCallbacks(idleRunnable ?: return)
        idleRunnable = null
    }
} 