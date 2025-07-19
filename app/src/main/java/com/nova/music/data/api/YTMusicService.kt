package com.nova.music.data.api

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import okhttp3.ResponseBody

interface YTMusicService {
    @GET("search")
    suspend fun search(
        @Query("query") query: String,
        @Query("limit") limit: Int = 20
    ): List<YTMusicSearchResult>
    
    @GET("trending")
    suspend fun getTrending(
        @Query("limit") limit: Int = 20
    ): List<YTMusicSearchResult>
    
    @GET("recommended")
    suspend fun getRecommendations(
        @Query("video_id") videoId: String? = null,
        @Query("genres") genres: String? = null,
        @Query("languages") languages: String? = null,
        @Query("artists") artists: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("cache_bust") cacheBust: Long = System.currentTimeMillis()
    ): List<YTMusicSearchResult>
    
    @Streaming
    @GET("yt_audio")
    suspend fun streamAudio(
        @Query("video_id") videoId: String
    ): ResponseBody
    
    @GET("playlist")
    suspend fun getPlaylist(
        @Query("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 50
    ): List<YTMusicSearchResult>
    
    @GET("featured")
    suspend fun getFeaturedPlaylists(
        @Query("limit") limit: Int = 10
    ): List<YTMusicPlaylist>
    
    @Streaming
    @GET("audio_fallback")
    suspend fun streamAudioFallback(
        @Query("video_id") videoId: String
    ): ResponseBody
    
    @GET("critical_prefetch")
    suspend fun criticalPrefetch(
        @Query("video_ids") videoIds: String
    ): CriticalPrefetchResponse
    
    @GET("task_stats")
    suspend fun getTaskStats(): TaskStatsResponse
} 