package com.nova.music.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nova.music.data.model.Song
import com.nova.music.data.model.Playlist
import com.nova.music.data.model.PlaylistSongCrossRef
import com.nova.music.data.model.RecentlyPlayed
import com.nova.music.data.model.SongPlaylistCrossRef

@Database(
    entities = [
        Song::class,
        Playlist::class,
        RecentlyPlayed::class,
        PlaylistSongCrossRef::class,
        SongPlaylistCrossRef::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
} 