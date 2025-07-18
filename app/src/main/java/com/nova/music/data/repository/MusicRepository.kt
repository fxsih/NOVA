package com.nova.music.data.repository

import com.nova.music.data.model.Song
import com.nova.music.data.model.Playlist
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    // Songs
    fun getAllSongs(): Flow<List<Song>>
    fun getRecommendedSongs(genres: String = "", languages: String = "", artists: String = ""): Flow<List<Song>>
    fun getRecommendedSongs(genres: String = "", languages: String = "", artists: String = "", forceRefresh: Boolean = false): Flow<List<Song>>
    fun searchSongs(query: String): Flow<List<Song>>
    fun getTrendingSongs(): Flow<List<Song>>
    suspend fun getSongById(songId: String): Song?
    
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
    
    // Downloaded songs
    fun getDownloadedSongs(): Flow<List<Song>>
    suspend fun markSongAsDownloaded(song: Song, localFilePath: String)
    suspend fun markSongAsNotDownloaded(songId: String)
    suspend fun isDownloaded(songId: String): Boolean
    
    // Playlists
    fun getPlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun renamePlaylist(playlistId: String, newName: String)
    suspend fun addSongToPlaylist(song: Song, playlistId: String)
    suspend fun removeSongFromPlaylist(songId: String, playlistId: String)
    suspend fun isSongInPlaylist(songId: String, playlistId: String): Boolean
    fun getPlaylistSongCount(playlistId: String): Flow<Int>
    fun getPlaylistSongs(playlistId: String): Flow<List<Song>>
} 