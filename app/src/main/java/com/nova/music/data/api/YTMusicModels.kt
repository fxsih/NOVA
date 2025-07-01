package com.nova.music.data.api

import com.google.gson.annotations.SerializedName
import com.nova.music.data.model.Song
import android.util.Log

data class YTMusicSearchResult(
    val videoId: String,
    val title: String,
    val length: String? = null,
    val thumbnail: List<YTMusicThumbnail>? = null,
    val artists: List<YTMusicArtist>? = null,
    val album: YTMusicAlbum? = null,
    val year: String? = null,
    val duration_seconds: Int? = null,
    val views: String? = null,
    val isExplicit: Boolean? = false,
    val videoType: String? = null,
    val category: String? = null,
    val resultType: String? = null,
    val audio_url: String? = null,
    val audio_url_expire: Long? = null,
    val audio_url_content_type: String? = null
) {
    fun toSong(): Song {
        // Get the best thumbnail - prefer higher resolution (at least 120x120 if available)
        val bestThumbnail = thumbnail?.let { thumbnails ->
            // First try to get a thumbnail with width >= 120
            val bestUrl = thumbnails.filter { it.width != null && it.width >= 120 }
                .maxByOrNull { it.width ?: 0 }?.url
                // If no thumbnail with width >= 120, get the largest available
                ?: thumbnails.maxByOrNull { it.width ?: 0 }?.url
                // If width is null for all, just take the first one
                ?: thumbnails.firstOrNull()?.url
                // If all else fails, use a default URL that's likely to work
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                
            // Log the thumbnail URL for debugging
            Log.d("YTMusicModels", "Selected thumbnail URL: $bestUrl")
            bestUrl
        } ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" // Default fallback URL using video ID
        
        // Handle artist names, joining them with commas
        val artistNames = artists?.mapNotNull { it.name }?.joinToString(", ") ?: ""
        
        // Handle album name
        val albumName = album?.name ?: ""
        
        // Create a song with the YouTube video ID prefixed with "yt_" to distinguish from local songs
        return Song(
            id = "yt_$videoId",
            title = title.ifBlank { "Unknown Title" },
            artist = artistNames.ifBlank { "Unknown Artist" },
            album = albumName.ifBlank { "Unknown Album" },
            albumArt = "",  // Local album art placeholder (empty for YouTube songs)
            albumArtUrl = bestThumbnail,  // Use the best thumbnail URL for YouTube songs
            duration = (duration_seconds?.toLong() ?: 0L) * 1000, // Convert to milliseconds
            isRecommended = false,
            isLiked = false,
            audioUrl = null // Don't rely on audio_url in list responses, will fetch on demand
        )
    }
}

data class YTMusicThumbnail(
    val url: String,
    val width: Int? = null,
    val height: Int? = null
)

data class YTMusicArtist(
    val name: String? = null,
    val id: String? = null
)

data class YTMusicAlbum(
    val name: String? = null,
    val id: String? = null
)

data class YTMusicPlaylist(
    val id: String,
    val title: String,
    val description: String? = null,
    val thumbnail: List<YTMusicThumbnail>? = null,
    val songCount: Int? = null
) 