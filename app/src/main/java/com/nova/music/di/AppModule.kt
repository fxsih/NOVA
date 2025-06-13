package com.nova.music.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import com.nova.music.data.local.AppDatabase
import com.nova.music.data.local.DatabaseInitializer
import com.nova.music.data.local.MusicDao
import com.nova.music.data.repository.AuthRepository
import com.nova.music.data.repository.MusicRepository
import com.nova.music.data.repository.impl.AuthRepositoryImpl
import com.nova.music.data.repository.impl.MusicRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindMusicRepository(impl: MusicRepositoryImpl): MusicRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    companion object {
        @Provides
        @Singleton
        fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "nova_music_db"
            ).fallbackToDestructiveMigration().build()
        }

        @Provides
        @Singleton
        fun provideMusicDao(database: AppDatabase): MusicDao = database.musicDao()

        @Provides
        @Singleton
        fun provideDatabaseInitializer(musicDao: MusicDao): DatabaseInitializer {
            return DatabaseInitializer(musicDao)
        }

        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
            return PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("settings") }
            )
        }

        @Provides
        @Singleton
        fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer {
            return ExoPlayer.Builder(context).build()
        }
    }
} 