package com.nova.music.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NovaMediaService : Service() {
    @Inject
    lateinit var musicPlayerService: IMusicPlayerService

    private val binder = MediaServiceBinder()

    inner class MediaServiceBinder : Binder() {
        fun getService(): IMusicPlayerService = musicPlayerService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        (musicPlayerService as? MusicPlayerServiceImpl)?.onDestroy()
    }
} 