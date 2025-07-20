package com.nova.music.data.local

import androidx.room.*
import com.nova.music.data.model.Song
import com.nova.music.data.model.Playlist
import com.nova.music.data.model.RecentlyPlayed
import com.nova.music.data.model.SongPlaylistCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isRecommended = 1")
    fun getRecommendedSongs(): Flow<List<Song>>
    
    @Query("UPDATE songs SET isRecommended = 0 WHERE isRecommended = 1")
    suspend fun clearRecommendedSongs()


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

    @Query("SELECT * FROM songs WHERE isDownloaded = 1")
    fun getDownloadedSongs(): Flow<List<Song>>

    @Query("UPDATE songs SET isLiked = :isLiked WHERE id = :songId")
    suspend fun updateSongLikedStatus(songId: String, isLiked: Boolean)

    @Query("UPDATE songs SET isDownloaded = :isDownloaded, localFilePath = :localFilePath WHERE id = :songId")
    suspend fun updateSongDownloadStatus(songId: String, isDownloaded: Boolean, localFilePath: String?)

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

    @Query("SELECT * FROM songs s INNER JOIN song_playlist_cross_ref ref ON s.id = ref.songId WHERE ref.playlistId = :playlistId ORDER BY ref.addedAt DESC")
    fun getPlaylistSongs(playlistId: String): Flow<List<Song>>

    @Query("DELETE FROM recently_played WHERE songId = :songId")
    suspend fun deleteRecentlyPlayedBySongId(songId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongPlaylistCrossRef(crossRef: SongPlaylistCrossRef)
    
    @Query("DELETE FROM song_playlist_cross_ref WHERE songId = :songId AND playlistId = :playlistId")
    suspend fun deleteSongPlaylistCrossRef(songId: String, playlistId: String)
    
    @Query("SELECT EXISTS(SELECT 1 FROM song_playlist_cross_ref WHERE songId = :songId AND playlistId = :playlistId)")
    suspend fun isSongInPlaylist(songId: String, playlistId: String): Boolean
    
    @Query("SELECT COUNT(*) FROM song_playlist_cross_ref WHERE playlistId = :playlistId")
    fun getPlaylistSongCount(playlistId: String): Flow<Int>
    
    // Clear methods for account switching
    @Query("UPDATE songs SET isLiked = 0")
    suspend fun clearAllLikedSongs()
    
    @Query("UPDATE songs SET isDownloaded = 0, localFilePath = NULL")
    suspend fun clearAllDownloadedSongs()
    
    @Query("DELETE FROM playlists")
    suspend fun clearAllPlaylists()
    
    @Query("DELETE FROM song_playlist_cross_ref")
    suspend fun clearAllPlaylistSongs()
} 