package com.nova.music.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlayerService : Service() {
    @Inject
    lateinit var musicPlayerServiceImpl: MusicPlayerServiceImpl
    
    private val binder = MusicPlayerBinder()

    inner class MusicPlayerBinder : Binder() {
        fun getService(): IMusicPlayerService = musicPlayerServiceImpl
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        musicPlayerServiceImpl.onDestroy()
    }
} 