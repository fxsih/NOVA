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
import com.nova.music.data.api.YTMusicService
import com.nova.music.util.PreferenceManager
import com.nova.music.util.DynamicBaseUrlInterceptor
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

    companion object {
        
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
            .addMigrations(MIGRATION_7_8)
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
        fun provideOkHttpClient(
            loggingInterceptor: HttpLoggingInterceptor,
            preferenceManager: PreferenceManager
        ): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                // Add dynamic base URL interceptor
                .addInterceptor(DynamicBaseUrlInterceptor { preferenceManager.getApiBaseUrl() })
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
                .baseUrl("http://placeholder.com/") // Placeholder base URL - will be overridden by interceptor
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

        // Migration from version 7 to 8 - Remove V1 playlist system
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Remove the playlistIds column from the songs table
                // SQLite doesn't support DROP COLUMN directly, so we need to recreate the table
                
                // Create a temporary table with the new schema
                database.execSQL("""
                    CREATE TABLE songs_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        albumArt TEXT NOT NULL,
                        albumArtUrl TEXT,
                        duration INTEGER NOT NULL,
                        audioUrl TEXT,
                        isRecommended INTEGER NOT NULL DEFAULT 0,
                        isLiked INTEGER NOT NULL DEFAULT 0,
                        isDownloaded INTEGER NOT NULL DEFAULT 0,
                        localFilePath TEXT
                    )
                """)
                
                // Copy data from the old table to the new table
                database.execSQL("""
                    INSERT INTO songs_new (
                        id, title, artist, album, albumArt, albumArtUrl, 
                        duration, audioUrl, isRecommended, isLiked, isDownloaded, localFilePath
                    )
                    SELECT 
                        id, title, artist, album, albumArt, albumArtUrl, 
                        duration, audioUrl, isRecommended, isLiked, isDownloaded, localFilePath
                    FROM songs
                """)
                
                // Drop the old table
                database.execSQL("DROP TABLE songs")
                
                // Rename the new table to the original name
                database.execSQL("ALTER TABLE songs_new RENAME TO songs")
                
                // Recreate indices
                database.execSQL("CREATE INDEX IF NOT EXISTS index_songs_title ON songs(title)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_songs_artist ON songs(artist)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_songs_album ON songs(album)")
            }
        }
    }
} 