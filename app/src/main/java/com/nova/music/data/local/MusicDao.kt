package com.nova.music.data.local

import androidx.room.*
import com.nova.music.data.model.Song
import com.nova.music.data.model.Playlist
import com.nova.music.data.model.PlaylistSongCrossRef
import com.nova.music.data.model.RecentlyPlayed
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isRecommended = 1")
    fun getRecommendedSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentlyPlayed(recentlyPlayed: RecentlyPlayed)

    @Query("""
        SELECT s.* FROM songs s 
        INNER JOIN recently_played rp ON s.id = rp.songId 
        ORDER BY rp.timestamp DESC 
        LIMIT 10
    """)
    fun getRecentlyPlayed(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isLiked = 1")
    fun getLikedSongs(): Flow<List<Song>>

    @Query("UPDATE songs SET isLiked = :isLiked WHERE id = :songId")
    suspend fun updateSongLikedStatus(songId: String, isLiked: Boolean)

    @Query("SELECT * FROM playlists")
    fun getPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: String, newName: String)

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: String): Song?

    @Query("UPDATE songs SET playlistIds = :newPlaylistIds WHERE id = :songId")
    suspend fun updateSongPlaylistIds(songId: String, newPlaylistIds: String)

    @Query("SELECT * FROM songs WHERE playlistIds LIKE '%' || :playlistId || '%'")
    fun getPlaylistSongs(playlistId: String): Flow<List<Song>>

    @Transaction
    suspend fun addSongToPlaylist(songId: String, playlistId: String) {
        val song = getSongById(songId) ?: return
        val updatedPlaylistIds = song.addPlaylistId(playlistId)
        updateSongPlaylistIds(songId, updatedPlaylistIds)
    }

    @Transaction
    suspend fun removeSongFromPlaylist(songId: String, playlistId: String) {
        val song = getSongById(songId) ?: return
        val updatedPlaylistIds = song.getPlaylistIdsList().filter { it != playlistId }.joinToString(",")
        updateSongPlaylistIds(songId, updatedPlaylistIds)
    }

    @Query("SELECT COUNT(*) FROM songs WHERE playlistIds LIKE '%' || :playlistId || '%'")
    fun getPlaylistSongCount(playlistId: String): Flow<Int>
} 