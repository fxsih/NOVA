package com.nova.music.data.repository.impl

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nova.music.data.local.MusicDao
import com.nova.music.data.model.Song
import com.nova.music.data.model.Playlist
import com.nova.music.data.model.RecentlyPlayed
import com.nova.music.data.model.SongPlaylistCrossRef
import com.nova.music.data.repository.MusicRepository
import com.nova.music.data.api.YTMusicService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MusicRepositoryImpl"

@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao,
    private val dataStore: DataStore<Preferences>,
    private val ytMusicService: YTMusicService
) : MusicRepository {

    private val recentSearchesKey = stringPreferencesKey("recent_searches")
    private val maxRecentSearches = 10

    override fun getAllSongs(): Flow<List<Song>> = musicDao.getAllSongs()

    override fun getRecommendedSongs(genres: String, languages: String, artists: String): Flow<List<Song>> = flow {
        try {
            // First try to emit cached recommendations if they exist
            val cachedRecommendations = musicDao.getRecommendedSongs().first()
            if (cachedRecommendations.isNotEmpty()) {
                Log.d(TAG, "Using ${cachedRecommendations.size} cached recommendations initially")
                emit(cachedRecommendations)
            }
            
            // Log the parameters to help with debugging
            Log.d(TAG, "Fetching recommendations with genres: $genres, languages: $languages, artists: $artists")
            
            val recommendedResults = ytMusicService.getRecommendations(
                genres = genres.takeIf { it.isNotBlank() },
                languages = languages.takeIf { it.isNotBlank() },
                artists = artists.takeIf { it.isNotBlank() }
            )
            
            // Log the number of results received
            Log.d(TAG, "Received ${recommendedResults.size} recommended songs")
            
            val songs = recommendedResults.map { it.toSong().copy(isRecommended = true) }
            
            // Cache the recommended songs in the database
            try {
                // First, clear previous recommended songs
                musicDao.clearRecommendedSongs()
                
                // Then insert new recommendations
                if (songs.isNotEmpty()) {
                    musicDao.insertSongs(songs)
                    Log.d(TAG, "Cached ${songs.size} recommended songs in database")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error caching recommended songs", e)
                // Continue even if caching fails
            }
            
            // Only emit new songs if we got some
            if (songs.isNotEmpty()) {
                emit(songs)
            } else if (cachedRecommendations.isEmpty()) {
                // Only emit empty list if we don't have cache and didn't get new songs
                emit(emptyList())
            }
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP error when fetching recommendations: ${e.code()}", e)
            // Try to get cached recommendations from the database
            val cachedRecommendations = musicDao.getRecommendedSongs().first()
            if (cachedRecommendations.isNotEmpty()) {
                Log.d(TAG, "Using ${cachedRecommendations.size} cached recommendations after HTTP error")
                emit(cachedRecommendations)
            } else {
                emit(emptyList())
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error when fetching recommendations", e)
            // Try to get cached recommendations from the database
            val cachedRecommendations = musicDao.getRecommendedSongs().first()
            if (cachedRecommendations.isNotEmpty()) {
                Log.d(TAG, "Using ${cachedRecommendations.size} cached recommendations after network error")
                emit(cachedRecommendations)
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error when fetching recommendations", e)
            // Try to get cached recommendations from the database
            val cachedRecommendations = musicDao.getRecommendedSongs().first()
            if (cachedRecommendations.isNotEmpty()) {
                Log.d(TAG, "Using ${cachedRecommendations.size} cached recommendations after unknown error")
                emit(cachedRecommendations)
            } else {
                emit(emptyList())
            }
        }
    }

    override fun getRecommendedSongs(genres: String, languages: String, artists: String, forceRefresh: Boolean): Flow<List<Song>> = flow {
        try {
            // Log the parameters to help with debugging
            Log.d(TAG, "Fetching recommendations with genres: $genres, languages: $languages, artists: $artists, forceRefresh: $forceRefresh")
            
            // If forceRefresh is true, clear the cache first
            if (forceRefresh) {
                Log.d(TAG, "Force refresh requested, clearing cached recommendations")
                musicDao.clearRecommendedSongs()
                
                // Emit an empty list first to clear the UI immediately
                emit(emptyList())
            }
            
            val cacheBustValue = if (forceRefresh) System.currentTimeMillis() else 0
            Log.d(TAG, "Using cache bust value: $cacheBustValue")
            
            val recommendedResults = ytMusicService.getRecommendations(
                genres = genres.takeIf { it.isNotBlank() },
                languages = languages.takeIf { it.isNotBlank() },
                artists = artists.takeIf { it.isNotBlank() },
                cacheBust = cacheBustValue
            )
            
            // Log the number of results received
            Log.d(TAG, "Received ${recommendedResults.size} recommended songs")
            
            val songs = recommendedResults.map { it.toSong().copy(isRecommended = true) }
            
            // Cache the recommended songs in the database
            try {
                // First, clear previous recommended songs
                musicDao.clearRecommendedSongs()
                
                // Then insert new recommendations
                if (songs.isNotEmpty()) {
                    musicDao.insertSongs(songs)
                    Log.d(TAG, "Cached ${songs.size} recommended songs in database")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error caching recommended songs", e)
                // Continue even if caching fails
            }
            
            emit(songs)
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP error when fetching recommendations: ${e.code()}", e)
            // Only try to get cached recommendations if not forcing refresh
            if (!forceRefresh) {
                val cachedRecommendations = musicDao.getRecommendedSongs().first()
                if (cachedRecommendations.isNotEmpty()) {
                    Log.d(TAG, "Using ${cachedRecommendations.size} cached recommendations")
                    emit(cachedRecommendations)
                } else {
                    emit(emptyList())
                }
            } else {
                emit(emptyList())
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error when fetching recommendations", e)
            // Only try to get cached recommendations if not forcing refresh
            if (!forceRefresh) {
                val cachedRecommendations = musicDao.getRecommendedSongs().first()
                if (cachedRecommendations.isNotEmpty()) {
                    Log.d(TAG, "Using ${cachedRecommendations.size} cached recommendations")
                    emit(cachedRecommendations)
                } else {
                    emit(emptyList())
                }
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error when fetching recommendations", e)
            emit(emptyList())
        }
    }

    override fun searchSongs(query: String): Flow<List<Song>> = flow {
        // First emit local search results
        val localResults = musicDao.searchSongs(query).first()
        emit(localResults)
        
        // Then try to fetch from backend
        try {
            val searchResults = ytMusicService.search(query)
            val songs = searchResults.map { it.toSong() }
            emit(songs)
            
            // Add to recent searches
            addToRecentSearches(query)
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP error when searching songs: ${e.code()}", e)
            // If backend call fails, we already emitted local results
        } catch (e: IOException) {
            Log.e(TAG, "Network error when searching songs", e)
            // Network error (no connection)
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error when searching songs", e)
            // Unknown error
        }
    }
    
    override fun getTrendingSongs(): Flow<List<Song>> = flow {
        try {
            val trendingResults = ytMusicService.getTrending()
            val songs = trendingResults.map { it.toSong() }
            emit(songs)
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP error when fetching trending songs: ${e.code()}", e)
            emit(emptyList())
        } catch (e: IOException) {
            Log.e(TAG, "Network error when fetching trending songs", e)
            emit(emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error when fetching trending songs", e)
            emit(emptyList())
        }
    }

    override fun getRecentlyPlayed(): Flow<List<Song>> = musicDao.getRecentlyPlayed()

    override suspend fun addToRecentlyPlayed(song: Song) {
        // First check if song exists in database and insert it if not
        val existingSong = musicDao.getSongById(song.id)
        if (existingSong == null) {
            // If song doesn't exist, insert it first
            try {
                musicDao.insertSong(song)
                Log.d(TAG, "Inserted new song into database: ${song.id} - ${song.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting song into database", e)
                // If we can't insert the song, we can't add it to recently played
                return
            }
        }
        
        // Then add to recently played - delete any existing entry first to avoid duplicates
        try {
            // First delete any existing recently played entry for this song
            musicDao.deleteRecentlyPlayedBySongId(song.id)
            // Then insert new entry
            musicDao.insertRecentlyPlayed(RecentlyPlayed(songId = song.id))
            Log.d(TAG, "Added to recently played: ${song.id} - ${song.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding song to recently played", e)
        }
    }

    override suspend fun getRecentSearches(): List<String> {
        return dataStore.data.first()[recentSearchesKey]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()
    }

    override suspend fun addToRecentSearches(query: String) {
        if (query.isBlank()) return
        
        dataStore.edit { preferences ->
            val currentSearches = preferences[recentSearchesKey]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toMutableList() 
                ?: mutableListOf()
                
            currentSearches.remove(query) // Remove if exists to avoid duplicates
            currentSearches.add(0, query) // Add to the beginning
            
            if (currentSearches.size > maxRecentSearches) {
                currentSearches.removeAt(currentSearches.lastIndex)
            }
            
            preferences[recentSearchesKey] = currentSearches.joinToString(",")
        }
    }

    override suspend fun removeFromRecentSearches(query: String) {
        dataStore.edit { preferences ->
            val currentSearches = preferences[recentSearchesKey]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toMutableList()
                ?: mutableListOf()
                
            currentSearches.remove(query)
            preferences[recentSearchesKey] = currentSearches.joinToString(",")
        }
    }

    override fun getLikedSongs(): Flow<List<Song>> = musicDao.getLikedSongs()

    override suspend fun addSongToLiked(song: Song) {
        // First check if song exists in database
        val existingSong = musicDao.getSongById(song.id)
        if (existingSong == null) {
            // If song doesn't exist, insert it first
            musicDao.insertSong(song.copy(isLiked = true))
        } else {
            // If song exists, just update liked status
            musicDao.updateSongLikedStatus(song.id, true)
        }
    }

    override suspend fun removeSongFromLiked(songId: String) {
        musicDao.updateSongLikedStatus(songId, false)
    }

    override fun getPlaylists(): Flow<List<Playlist>> = flow {
        musicDao.getPlaylists().collect { playlists ->
            val playlistsWithSongs = playlists.map { playlist ->
                playlist.apply {
                    songs = musicDao.getPlaylistSongsV2(playlist.id).first()
                }
            }
            emit(playlistsWithSongs)
        }
    }.distinctUntilChanged()

    override fun getPlaylistSongs(playlistId: String): Flow<List<Song>> = 
        musicDao.getPlaylistSongsV2(playlistId)

    override suspend fun createPlaylist(name: String) {
        val playlist = Playlist(
            id = "playlist_${System.currentTimeMillis()}",
            name = name
        )
        musicDao.createPlaylist(playlist)
    }

    override suspend fun deletePlaylist(playlistId: String) {
        // First get the playlist, then delete it
        musicDao.getPlaylists().first().find { it.id == playlistId }?.let { playlist ->
            // Remove the playlist from all songs that have it
            musicDao.getPlaylistSongsV2(playlistId).first().forEach { song ->
                musicDao.deleteSongPlaylistCrossRef(song.id, playlistId)
            }
            musicDao.deletePlaylist(playlist)
        }
    }

    override suspend fun renamePlaylist(playlistId: String, newName: String) {
        musicDao.renamePlaylist(playlistId, newName)
    }

    override suspend fun addSongToPlaylist(song: Song, playlistId: String) {
        // First check if song exists in database
        val existingSong = musicDao.getSongById(song.id)
        if (existingSong == null) {
            // If song doesn't exist, insert it first
            musicDao.insertSong(song)
        }
        
        // Use the new join table approach
        musicDao.insertSongPlaylistCrossRef(
            SongPlaylistCrossRef(
                songId = song.id,
                playlistId = playlistId
            )
        )
        
        // Also update the legacy approach for backward compatibility
        musicDao.addSongToPlaylist(song.id, playlistId)
    }

    override suspend fun removeSongFromPlaylist(songId: String, playlistId: String) {
        // Use the new join table approach
        musicDao.deleteSongPlaylistCrossRef(songId, playlistId)
        
        // Also update the legacy approach for backward compatibility
        musicDao.removeSongFromPlaylist(songId, playlistId)
    }

    override suspend fun isSongInPlaylist(songId: String, playlistId: String): Boolean {
        // Use the new V2 method
        return musicDao.isSongInPlaylistV2(songId, playlistId)
    }

    override fun getPlaylistSongCount(playlistId: String): Flow<Int> {
        // Use the new V2 method
        return musicDao.getPlaylistSongCountV2(playlistId)
    }

    override suspend fun getSongById(songId: String): Song? {
        return musicDao.getSongById(songId)
    }

    // Downloaded songs methods
    override fun getDownloadedSongs(): Flow<List<Song>> = musicDao.getDownloadedSongs()

    override suspend fun markSongAsDownloaded(song: Song, localFilePath: String) {
        // First check if song exists in database
        val existingSong = musicDao.getSongById(song.id)
        if (existingSong == null) {
            // If song doesn't exist, insert it with downloaded flag
            musicDao.insertSong(song.copy(isDownloaded = true, localFilePath = localFilePath))
        } else {
            // If song exists, update download status
            musicDao.updateSongDownloadStatus(song.id, true, localFilePath)
        }
    }

    override suspend fun markSongAsNotDownloaded(songId: String) {
        musicDao.updateSongDownloadStatus(songId, false, null)
    }

    override suspend fun isDownloaded(songId: String): Boolean {
        val song = musicDao.getSongById(songId)
        return song?.isDownloaded == true
    }
} 