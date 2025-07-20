package com.nova.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Ignore
import androidx.room.ColumnInfo
import kotlinx.serialization.Serializable

@Entity(tableName = "songs")
@Serializable
data class Song(
    @PrimaryKey val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArt: String = "",
    val albumArtUrl: String? = null,
    val duration: Long = 0L,
    val audioUrl: String? = null,
    val isRecommended: Boolean = false,
    val isLiked: Boolean = false,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null
)

@Entity(tableName = "playlists")
@Serializable
data class Playlist(
    @PrimaryKey val id: String,
    val name: String,
    val coverUrl: String,
    val createdAt: Long
) {
    @Ignore
    var songs: List<Song> = emptyList()
    
    constructor(id: String, name: String) : this(
        id = id,
        name = name,
        coverUrl = "",
        createdAt = System.currentTimeMillis()
    )
    
    override fun equals(other: Any?): Boolean {
        return other is Playlist && 
               other.id == id && 
               other.name == name && 
               other.songs.size == songs.size
    }
    
    override fun hashCode(): Int {
        return java.util.Objects.hash(id, name, songs.size)
    }
}

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlistId"),
        Index("songId")
    ]
)
data class PlaylistSongCrossRef(
    val playlistId: String,
    val songId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recently_played",
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songId")]
)
data class RecentlyPlayed(
    @PrimaryKey val songId: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Proper join table for the many-to-many relationship between songs and playlists.
 * This is a more robust approach than using a comma-separated string.
 */
@Entity(
    tableName = "song_playlist_cross_ref",
    primaryKeys = ["songId", "playlistId"],
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("songId"),
        Index("playlistId")
    ]
)
data class SongPlaylistCrossRef(
    val songId: String,
    val playlistId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Serializable
data class UserMusicPreferences(
    val genres: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val artists: List<String> = emptyList()
) 