package com.nova.music.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Ignore
import androidx.room.ColumnInfo

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArt: String,
    val albumArtUrl: String? = null,
    val duration: Long,
    val isRecommended: Boolean = false,
    val isLiked: Boolean = false,
    @ColumnInfo(defaultValue = "") val playlistIds: String = ""
) {
    fun getPlaylistIdsList(): List<String> = 
        if (playlistIds.isBlank()) emptyList() 
        else playlistIds.split(",")

    fun addPlaylistId(playlistId: String): String =
        if (playlistIds.isBlank()) playlistId
        else if (playlistId in getPlaylistIdsList()) playlistIds
        else "$playlistIds,$playlistId"

    fun removePlaylistId(playlistId: String): String =
        getPlaylistIdsList()
            .filter { it != playlistId }
            .joinToString(",")
}

@Entity(tableName = "playlists")
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