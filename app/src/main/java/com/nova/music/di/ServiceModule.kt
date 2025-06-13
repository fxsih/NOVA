package com.nova.music.di

import com.nova.music.service.IMusicPlayerService
import com.nova.music.service.MusicPlayerServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {
    @Binds
    @Singleton
    abstract fun bindMusicPlayerService(
        impl: MusicPlayerServiceImpl
    ): IMusicPlayerService
} 