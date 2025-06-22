package com.nova.music.data.repository

import com.nova.music.data.model.Song
import com.nova.music.data.model.Playlist
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    // Songs
    fun getAllSongs(): Flow<List<Song>>
    fun getRecommendedSongs(): Flow<List<Song>>
    fun searchSongs(query: String): Flow<List<Song>>
    fun getTrendingSongs(): Flow<List<Song>>
    
    // Recently played
    fun getRecentlyPlayed(): Flow<List<Song>>
    suspend fun addToRecentlyPlayed(song: Song)
    
    // Recent searches
    suspend fun getRecentSearches(): List<String>
    suspend fun addToRecentSearches(query: String)
    suspend fun removeFromRecentSearches(query: String)
    
    // Liked songs
    fun getLikedSongs(): Flow<List<Song>>
    suspend fun addSongToLiked(song: Song)
    suspend fun removeSongFromLiked(songId: String)
    
    // Playlists
    fun getPlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun renamePlaylist(playlistId: String, newName: String)
    suspend fun addSongToPlaylist(song: Song, playlistId: String)
    suspend fun removeSongFromPlaylist(songId: String, playlistId: String)
    fun getPlaylistSongCount(playlistId: String): Flow<Int>
    fun getPlaylistSongs(playlistId: String): Flow<List<Song>>
} 