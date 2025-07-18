package com.nova.music.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nova.music.data.local.AppDatabase
import com.nova.music.data.local.DatabaseInitializer
import com.nova.music.data.local.MusicDao
import com.nova.music.data.repository.MusicRepository
import com.nova.music.data.repository.impl.MusicRepositoryImpl
import com.nova.music.data.api.YTMusicService
import com.nova.music.util.PreferenceManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import java.io.File

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindMusicRepository(impl: MusicRepositoryImpl): MusicRepository

    companion object {
        private const val BASE_URL = "http://192.168.29.154:8000/"
        
        @Provides
        @Singleton
        fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "music.db"
            )
            .addMigrations(MIGRATION_5_6)
            .addMigrations(MIGRATION_6_7)
            .fallbackToDestructiveMigration()
            .build()
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
        fun providePreferenceManager(@ApplicationContext context: Context): PreferenceManager {
            return PreferenceManager(context)
        }
        
        @Provides
        @Singleton
        fun provideLoggingInterceptor(): HttpLoggingInterceptor {
            return HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        }
        
        @Provides
        @Singleton
        fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                // Configure connection pooling for better performance
                .connectionPool(okhttp3.ConnectionPool(
                    maxIdleConnections = 10,
                    keepAliveDuration = 5, 
                    timeUnit = TimeUnit.MINUTES
                ))
                // Enable response caching
                .cache(okhttp3.Cache(
                    directory = File(System.getProperty("java.io.tmpdir"), "okhttp_cache"),
                    maxSize = 30 * 1024 * 1024L // 30 MB cache
                ))
                // Enable gzip compression
                .addInterceptor { chain ->
                    val originalRequest = chain.request()
                    val requestWithCompression = originalRequest.newBuilder()
                        .header("Accept-Encoding", "gzip, deflate")
                        .build()
                    chain.proceed(requestWithCompression)
                }
                .retryOnConnectionFailure(true)
                .build()
        }
        
        @Provides
        @Singleton
        fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        
        @Provides
        @Singleton
        fun provideYTMusicService(retrofit: Retrofit): YTMusicService {
            return retrofit.create(YTMusicService::class.java)
        }

        // Migration from version 5 to 6
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the new song_playlist_cross_ref table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS song_playlist_cross_ref (
                        songId TEXT NOT NULL,
                        playlistId TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(songId, playlistId),
                        FOREIGN KEY(songId) REFERENCES songs(id) ON DELETE CASCADE,
                        FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE
                    )
                """)
                
                // Create indices for the new table
                database.execSQL("CREATE INDEX IF NOT EXISTS index_song_playlist_cross_ref_songId ON song_playlist_cross_ref(songId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_song_playlist_cross_ref_playlistId ON song_playlist_cross_ref(playlistId)")
                
                // Migrate existing data from playlistIds to the new join table
                database.execSQL("""
                    INSERT INTO song_playlist_cross_ref (songId, playlistId, addedAt)
                    SELECT s.id, p.id, ${System.currentTimeMillis()}
                    FROM songs s, playlists p
                    WHERE s.playlistIds LIKE '%' || p.id || '%'
                """)
            }
        }

        // Migration from version 6 to 7
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add the new isDownloaded and localFilePath fields to the songs table
                database.execSQL("ALTER TABLE songs ADD COLUMN isDownloaded INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE songs ADD COLUMN localFilePath TEXT")
            }
        }
    }
} 