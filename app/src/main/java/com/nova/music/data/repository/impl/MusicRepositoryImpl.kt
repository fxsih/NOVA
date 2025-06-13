package com.nova.music.data.repository.impl

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nova.music.data.local.MusicDao
import com.nova.music.data.model.Song
import com.nova.music.data.model.Playlist
import com.nova.music.data.model.PlaylistSongCrossRef
import com.nova.music.data.model.RecentlyPlayed
import com.nova.music.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao,
    private val dataStore: DataStore<Preferences>
) : MusicRepository {

    private val recentSearchesKey = stringPreferencesKey("recent_searches")
    private val maxRecentSearches = 10

    override fun getAllSongs(): Flow<List<Song>> = musicDao.getAllSongs()

    override fun getRecommendedSongs(): Flow<List<Song>> = musicDao.getRecommendedSongs()

    override fun searchSongs(query: String): Flow<List<Song>> = musicDao.searchSongs(query)

    override fun getRecentlyPlayed(): Flow<List<Song>> = musicDao.getRecentlyPlayed()

    override suspend fun addToRecentlyPlayed(song: Song) {
        musicDao.insertRecentlyPlayed(RecentlyPlayed(songId = song.id))
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
        musicDao.updateSongLikedStatus(song.id, true)
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
        musicDao.addSongToPlaylist(song.id, playlistId)
    }

    override suspend fun removeSongFromPlaylist(songId: String, playlistId: String) {
        musicDao.removeSongFromPlaylist(songId, playlistId)
    }

    override fun getPlaylistSongCount(playlistId: String): Flow<Int> {
        return musicDao.getPlaylistSongCount(playlistId)
    }
} 