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

    override fun getRecommendedSongs(): Flow<List<Song>> = flow {
        // First emit local recommended songs
        val localSongs = musicDao.getRecommendedSongs().first()
        emit(localSongs)
        
        // Then try to fetch from backend if we have a song to base recommendations on
        try {
            if (localSongs.isNotEmpty()) {
                val videoId = localSongs.first().id
                val recommendations = ytMusicService.getRecommendations(videoId)
                val songs = recommendations.map { it.toSong() }
                emit(songs)
            }
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP error when fetching recommendations: ${e.code()}", e)
            // If backend call fails, we already emitted local songs
        } catch (e: IOException) {
            Log.e(TAG, "Network error when fetching recommendations", e)
            // Network error (no connection)
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error when fetching recommendations", e)
            // Unknown error
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
                    songs = musicDao.getPlaylistSongs(playlist.id).first()
                }
            }
            emit(playlistsWithSongs)
        }
    }.distinctUntilChanged()

    override fun getPlaylistSongs(playlistId: String): Flow<List<Song>> = 
        musicDao.getPlaylistSongs(playlistId)

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
            musicDao.getPlaylistSongs(playlistId).first().forEach { song ->
                musicDao.removeSongFromPlaylist(song.id, playlistId)
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
        
        // Then add to playlist
        musicDao.addSongToPlaylist(song.id, playlistId)
    }

    override suspend fun removeSongFromPlaylist(songId: String, playlistId: String) {
        musicDao.removeSongFromPlaylist(songId, playlistId)
    }

    override fun getPlaylistSongCount(playlistId: String): Flow<Int> {
        return musicDao.getPlaylistSongCount(playlistId)
    }
} 