package com.nova.music.data.model

data class User(
    val id: String,
    val email: String,
    val username: String,
    val profilePictureUrl: String? = null,
    val playlists: List<Playlist> = emptyList(),
    val favoriteSongs: List<Song> = emptyList()
) 