package com.nova.music.data.repository.impl

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nova.music.data.api.YTMusicService
import com.nova.music.data.local.MusicDao
import com.nova.music.data.model.*
import com.nova.music.data.repository.MusicRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MusicRepositoryImpl"

@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao,
    private val dataStore: DataStore<Preferences>,
    private val ytMusicService: YTMusicService,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : MusicRepository {

    private val recentSearchesKey = stringPreferencesKey("recent_searches")
    private val maxRecentSearches = 10

    override fun getAllSongs(): Flow<List<Song>> = musicDao.getAllSongs()

    override fun getRecommendedSongs(genres: String, languages: String, artists: String): Flow<List<Song>> {
        // Delegate to the overloaded method with forceRefresh = false
        return getRecommendedSongs(genres, languages, artists, false)
    }

    override suspend fun criticalPrefetch(videoIds: List<String>) {
        try {
            if (videoIds.isEmpty()) return
            
            Log.d(TAG, "Triggering critical prefetch for ${videoIds.size} videos")
            
            // Convert video IDs to comma-separated string
            val videoIdsString = videoIds.joinToString(",")
            
            // Call the backend critical prefetch endpoint
            val response = ytMusicService.criticalPrefetch(videoIdsString)
            
            Log.d(TAG, "Critical prefetch response: ${response.message}")
            } catch (e: Exception) {
            Log.e(TAG, "Error in critical prefetch", e)
            // Don't throw - this is a background operation that shouldn't block playback
        }
    }

    override fun getRecommendedSongs(genres: String, languages: String, artists: String, forceRefresh: Boolean): Flow<List<Song>> = flow {
        try {
            // Log the parameters to help with debugging
            Log.d(TAG, "Fetching recommendations with genres: $genres, languages: $languages, artists: $artists, forceRefresh: $forceRefresh")
            
            // If forceRefresh is true, clear the cache first
            if (forceRefresh) {
                Log.d(TAG, "Force refresh requested, clearing cached recommendations")
                musicDao.clearRecommendedSongs()
                
                // Emit an empty list first to clear the UI immediately
                emit(emptyList())
            }
            
            val cacheBustValue = if (forceRefresh) System.currentTimeMillis() else 0
            Log.d(TAG, "Using cache bust value: $cacheBustValue")
            
            val recommendedResults = ytMusicService.getRecommendations(
                genres = genres.takeIf { it.isNotBlank() },
                languages = languages.takeIf { it.isNotBlank() },
                artists = artists.takeIf { it.isNotBlank() },
                cacheBust = cacheBustValue
            )
            
            // Log the number of results received
            Log.d(TAG, "Received ${recommendedResults.size} recommended songs")
            
            val songs = recommendedResults.map { it.toSong().copy(isRecommended = true) }
            
            // Cache the recommended songs in the database
            try {
                // First, clear previous recommended songs
                musicDao.clearRecommendedSongs()
                
                // Then insert new recommendations while preserving like status
                if (songs.isNotEmpty()) {
                    // Get current liked songs to preserve their status
                    val currentLikedSongs = musicDao.getLikedSongs().first()
                    val likedSongIds = currentLikedSongs.map { it.id }.toSet()
                    
                    // Preserve like status for songs that were already liked
                    val songsWithPreservedLikes = songs.map { song ->
                        if (likedSongIds.contains(song.id)) {
                            song.copy(isLiked = true)
                        } else {
                            song
                        }
                    }
                    
                    musicDao.insertSongs(songsWithPreservedLikes)
                    Log.d(TAG, "Cached ${songs.size} recommended songs in database with preserved like status")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error caching recommended songs", e)
                // Continue even if caching fails
            }
            
            emit(songs)
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP error when fetching recommendations: ${e.code()}", e)
            // Only try to get cached recommendations if not forcing refresh
            if (!forceRefresh) {
                val cachedRecommendations = musicDao.getRecommendedSongs().first()
                if (cachedRecommendations.isNotEmpty()) {
                    Log.d(TAG, "Using ${cachedRecommendations.size} cached recommendations")
                    emit(cachedRecommendations)
                } else {
                    emit(emptyList())
                }
            } else {
                emit(emptyList())
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error when fetching recommendations", e)
            // Only try to get cached recommendations if not forcing refresh
            if (!forceRefresh) {
                val cachedRecommendations = musicDao.getRecommendedSongs().first()
                if (cachedRecommendations.isNotEmpty()) {
                    Log.d(TAG, "Using ${cachedRecommendations.size} cached recommendations")
                    emit(cachedRecommendations)
                } else {
                    emit(emptyList())
                }
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error when fetching recommendations", e)
            emit(emptyList())
        }
    }

    override fun searchSongs(query: String): Flow<List<Song>> = flow {
        // First emit local search results
        val localResults = musicDao.searchSongs(query).first()
        emit(localResults)
        
        // Then try to fetch from backend
        try {
            val searchResults = ytMusicService.search(query)
            val songs = searchResults.map { it.toSong() }
            
            // Preserve like status for search results
            val currentLikedSongs = musicDao.getLikedSongs().first()
            val likedSongIds = currentLikedSongs.map { it.id }.toSet()
            
            val songsWithPreservedLikes = songs.map { song ->
                if (likedSongIds.contains(song.id)) {
                    song.copy(isLiked = true)
                } else {
                    song
                }
            }
            
            emit(songsWithPreservedLikes)
            
            // Add to recent searches
            addToRecentSearches(query)
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP error when searching songs: ${e.code()}", e)
            // If backend call fails, we already emitted local results
        } catch (e: IOException) {
            Log.e(TAG, "Network error when searching songs", e)
            // Network error (no connection)
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error when searching songs", e)
            // Unknown error
        }
    }
    
    override fun getTrendingSongs(): Flow<List<Song>> = flow {
        try {
            val trendingResults = ytMusicService.getTrending()
            val songs = trendingResults.map { it.toSong() }
            
            // Preserve like status for trending songs
            val currentLikedSongs = musicDao.getLikedSongs().first()
            val likedSongIds = currentLikedSongs.map { it.id }.toSet()
            
            val songsWithPreservedLikes = songs.map { song ->
                if (likedSongIds.contains(song.id)) {
                    song.copy(isLiked = true)
                } else {
                    song
                }
            }
            
            emit(songsWithPreservedLikes)
        } catch (e: HttpException) {
            Log.e(TAG, "HTTP error when fetching trending songs: ${e.code()}", e)
            emit(emptyList())
        } catch (e: IOException) {
            Log.e(TAG, "Network error when fetching trending songs", e)
            emit(emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error when fetching trending songs", e)
            emit(emptyList())
        }
    }

    override fun getRecentlyPlayed(): Flow<List<Song>> = musicDao.getRecentlyPlayed()

    override suspend fun addToRecentlyPlayed(song: Song) {
        // First check if song exists in database and insert it if not
        val existingSong = musicDao.getSongById(song.id)
        if (existingSong == null) {
            // If song doesn't exist, insert it first
            try {
                musicDao.insertSong(song)
                Log.d(TAG, "Inserted new song into database: ${song.id} - ${song.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting song into database", e)
                // If we can't insert the song, we can't add it to recently played
                return
            }
        }
        
        // Then add to recently played - delete any existing entry first to avoid duplicates
        try {
            // First delete any existing recently played entry for this song
            musicDao.deleteRecentlyPlayedBySongId(song.id)
            // Then insert new entry
            musicDao.insertRecentlyPlayed(RecentlyPlayed(songId = song.id))
            Log.d(TAG, "Added to recently played: ${song.id} - ${song.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding song to recently played", e)
        }
    }

    override suspend fun getRecentSearches(): List<String> {
        return dataStore.data.first()[recentSearchesKey]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()
    }

    override suspend fun addToRecentSearches(query: String) {
        if (query.isBlank()) return
        
        dataStore.edit { preferences ->
            val currentSearches = preferences[recentSearchesKey]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toMutableList() 
                ?: mutableListOf()
                
            currentSearches.remove(query) // Remove if exists to avoid duplicates
            currentSearches.add(0, query) // Add to the beginning
            
            if (currentSearches.size > maxRecentSearches) {
                currentSearches.removeAt(currentSearches.lastIndex)
            }
            
            preferences[recentSearchesKey] = currentSearches.joinToString(",")
        }
    }

    override suspend fun removeFromRecentSearches(query: String) {
        dataStore.edit { preferences ->
            val currentSearches = preferences[recentSearchesKey]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toMutableList()
                ?: mutableListOf()
                
            currentSearches.remove(query)
            preferences[recentSearchesKey] = currentSearches.joinToString(",")
        }
    }



    override fun getPlaylists(): Flow<List<Playlist>> = flow {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            // If not authenticated, just return local playlists
            musicDao.getPlaylists().collect { playlists ->
                val playlistsWithSongs = playlists.map { playlist ->
                    playlist.apply {
                        songs = musicDao.getPlaylistSongsV2(playlist.id).first()
                    }
                }
                emit(playlistsWithSongs)
            }
            return@flow
        }

        // Get local playlists first
        val localPlaylists = musicDao.getPlaylists().first()
        val playlistsWithSongs = localPlaylists.map { playlist ->
            playlist.apply {
                songs = musicDao.getPlaylistSongsV2(playlist.id).first()
            }
        }
        emit(playlistsWithSongs)
        
        // Set up real-time listener for playlists
        try {
            val playlistsCollection = firestore.collection("users").document(currentUser.uid)
                .collection("playlists")
            
            val listener = playlistsCollection.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error in playlists listener", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    Log.d(TAG, "Real-time update: Playlists changed")
                    
                    // Update local database based on remote changes
                    CoroutineScope(Dispatchers.IO).launch {
                        updateLocalPlaylistsFromRemote(snapshot)
                    }
                }
            }
            
            // Keep the listener active and emit updates
            try {
                // Initial sync
                val initialSnapshot = playlistsCollection.get().await()
                updateLocalPlaylistsFromRemote(initialSnapshot)
                
                // Keep the flow alive for real-time updates
                while (true) {
                    delay(1000) // Check for updates every second
                    val updatedPlaylists = musicDao.getPlaylists().first()
                    val updatedPlaylistsWithSongs = updatedPlaylists.map { playlist ->
                        playlist.apply {
                            songs = musicDao.getPlaylistSongsV2(playlist.id).first()
                        }
                    }
                    emit(updatedPlaylistsWithSongs)
                }
            } finally {
                listener.remove()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up playlists real-time listener", e)
            // Fallback to local playlists only
            val playlistsWithSongs = localPlaylists.map { playlist ->
                playlist.apply {
                    songs = musicDao.getPlaylistSongsV2(playlist.id).first()
                }
            }
            emit(playlistsWithSongs)
        }
    }.distinctUntilChanged()

    override fun getPlaylistSongs(playlistId: String): Flow<List<Song>> = flow {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            // If not authenticated, just return local songs
            emitAll(musicDao.getPlaylistSongsV2(playlistId))
            return@flow
        }

        // Get local songs first
        val localSongs = musicDao.getPlaylistSongsV2(playlistId).first()
        
        // Sync with Firestore
        try {
            val songsCollection = firestore.collection("users").document(currentUser.uid)
                .collection("playlists")
                .document(playlistId)
                .collection("songs")
            
            val remoteSongsSnapshot = songsCollection.get().await()
            val remoteSongIds: List<String> = remoteSongsSnapshot.documents.map { it.id }.filterNotNull()
            
            // Get remote songs that don't exist locally
            val missingSongIds = remoteSongIds.filter { songId ->
                localSongs.none { it.id == songId }
            }
            
            // Add missing songs to local database
            for (songId in missingSongIds) {
                // Try to get song details from user-specific Firestore collection
                try {
                    val song = getSongFromFirebase(songId, currentUser)
                    if (song != null) {
                        musicDao.insertSong(song)
                        musicDao.insertSongPlaylistCrossRef(
                            SongPlaylistCrossRef(
                                songId = song.id,
                                playlistId = playlistId
                            )
                        )
                        Log.d(TAG, "Added remote song to local playlist: $songId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting song details from Firestore: $songId", e)
                }
            }
            
            // Get final songs
            val finalSongs = musicDao.getPlaylistSongsV2(playlistId).first()
            emit(finalSongs)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing playlist songs with Firestore", e)
            // Fallback to local songs only
            emitAll(musicDao.getPlaylistSongsV2(playlistId))
        }
    }

    override suspend fun createPlaylist(name: String) {
        val currentUser = firebaseAuth.currentUser
        val playlist = Playlist(
            id = "playlist_${System.currentTimeMillis()}",
            name = name
        )
        
        // Add to local database
        musicDao.createPlaylist(playlist)
        
        // Sync to Firestore if authenticated
        if (currentUser != null) {
            try {
                firestore.collection("users").document(currentUser.uid)
                    .collection("playlists")
                    .document(playlist.id)
                    .set(playlist)
                    .await()
                Log.d(TAG, "Created playlist in Firestore: ${playlist.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating playlist in Firestore", e)
            }
        }
    }

    override suspend fun deletePlaylist(playlistId: String) {
        val currentUser = firebaseAuth.currentUser
        
        // First get the playlist, then delete it
        musicDao.getPlaylists().first().find { it.id == playlistId }?.let { playlist ->
            // Remove the playlist from all songs that have it
            musicDao.getPlaylistSongsV2(playlistId).first().forEach { song ->
                musicDao.deleteSongPlaylistCrossRef(song.id, playlistId)
            }
            musicDao.deletePlaylist(playlist)
            
            // Delete from Firestore if authenticated
            if (currentUser != null) {
                try {
                    firestore.collection("users").document(currentUser.uid)
                        .collection("playlists")
                        .document(playlistId)
                        .delete()
                        .await()
                    Log.d(TAG, "Deleted playlist from Firestore: ${playlist.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting playlist from Firestore", e)
                }
            }
        }
    }

    override suspend fun renamePlaylist(playlistId: String, newName: String) {
        val currentUser = firebaseAuth.currentUser
        
        // Update local database
        musicDao.renamePlaylist(playlistId, newName)
        
        // Update Firestore if authenticated
        if (currentUser != null) {
            try {
                firestore.collection("users").document(currentUser.uid)
                    .collection("playlists")
                    .document(playlistId)
                    .update("name", newName)
                    .await()
                Log.d(TAG, "Renamed playlist in Firestore: $newName")
            } catch (e: Exception) {
                Log.e(TAG, "Error renaming playlist in Firestore", e)
            }
        }
    }

    override suspend fun addSongToPlaylist(song: Song, playlistId: String) {
        val currentUser = firebaseAuth.currentUser
        
        // First check if song exists in database
        val existingSong = musicDao.getSongById(song.id)
        if (existingSong == null) {
            // If song doesn't exist, insert it first
            musicDao.insertSong(song)
        }
        
        // Use the new join table approach
        musicDao.insertSongPlaylistCrossRef(
            SongPlaylistCrossRef(
                songId = song.id,
                playlistId = playlistId
            )
        )
        
        // Also update the legacy approach for backward compatibility
        musicDao.addSongToPlaylist(song.id, playlistId)
        
        // Sync to Firestore if authenticated
        if (currentUser != null) {
            try {
                // Store song details in user-specific songs collection
                firestore.collection("users").document(currentUser.uid)
                    .collection("songs")
                    .document(song.id)
                    .set(song)
                    .await()
                
                // Add song to playlist in Firestore
                firestore.collection("users").document(currentUser.uid)
                    .collection("playlists")
                    .document(playlistId)
                    .collection("songs")
                    .document(song.id)
                    .set(mapOf(
                        "songId" to song.id,
                        "addedAt" to System.currentTimeMillis()
                    ))
                    .await()
                Log.d(TAG, "Added song to playlist in Firestore: ${song.title} -> ${playlistId}")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding song to playlist in Firestore", e)
            }
        }
    }

    override suspend fun removeSongFromPlaylist(songId: String, playlistId: String) {
        val currentUser = firebaseAuth.currentUser
        
        // Use the new join table approach
        musicDao.deleteSongPlaylistCrossRef(songId, playlistId)
        
        // Also update the legacy approach for backward compatibility
        musicDao.removeSongFromPlaylist(songId, playlistId)
        
        // Remove from Firestore if authenticated
        if (currentUser != null) {
            try {
                firestore.collection("users").document(currentUser.uid)
                    .collection("playlists")
                    .document(playlistId)
                    .collection("songs")
                    .document(songId)
                    .delete()
                    .await()
                Log.d(TAG, "Removed song from playlist in Firestore: $songId -> $playlistId")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing song from playlist in Firestore", e)
            }
        }
    }

    override suspend fun isSongInPlaylist(songId: String, playlistId: String): Boolean {
        // Use the new V2 method
        return musicDao.isSongInPlaylistV2(songId, playlistId)
    }

    override fun getPlaylistSongCount(playlistId: String): Flow<Int> {
        // Use the new V2 method with consistency verification
        return flow {
            val v2Count = musicDao.getPlaylistSongCountV2(playlistId).first()
            
            // Verify consistency with V1 system
            val v1Count = musicDao.getPlaylistSongCount(playlistId).first()
            
            if (v1Count != v2Count) {
                Log.w(TAG, "Playlist count inconsistency detected for playlist $playlistId: V1=$v1Count, V2=$v2Count")
                // Fix the inconsistency by syncing V1 to match V2
                fixPlaylistCountInconsistency(playlistId, v2Count)
            }
            
            emit(v2Count)
        }
    }
    
    /**
     * Fix playlist count inconsistency between V1 and V2 systems
     */
    private suspend fun fixPlaylistCountInconsistency(playlistId: String, correctCount: Int) {
        try {
            Log.d(TAG, "Fixing playlist count inconsistency for playlist $playlistId")
            
            // Get the actual songs from V2 system (the correct one)
            val v2Songs = musicDao.getPlaylistSongsV2(playlistId).first()
            
            // Update V1 system to match V2
            for (song in v2Songs) {
                // Ensure song has the correct playlistId in the playlistIds field
                val currentPlaylistIds = song.getPlaylistIdsList()
                if (!currentPlaylistIds.contains(playlistId)) {
                    val updatedPlaylistIds = song.addPlaylistId(playlistId)
                    musicDao.updateSongPlaylistIds(song.id, updatedPlaylistIds)
                    Log.d(TAG, "Fixed V1 system for song ${song.id} in playlist $playlistId")
                }
            }
            
            // Remove songs from V1 that shouldn't be there
            val v1Songs = musicDao.getPlaylistSongs(playlistId).first()
            for (song in v1Songs) {
                if (!v2Songs.any { it.id == song.id }) {
                    val updatedPlaylistIds = song.removePlaylistId(playlistId)
                    musicDao.updateSongPlaylistIds(song.id, updatedPlaylistIds)
                    Log.d(TAG, "Removed song ${song.id} from V1 system for playlist $playlistId")
                }
            }
            
            Log.d(TAG, "Playlist count inconsistency fixed for playlist $playlistId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fixing playlist count inconsistency for playlist $playlistId", e)
        }
    }
    
    /**
     * Sync playlist songs from Firebase to both V1 and V2 systems
     */
    private suspend fun syncPlaylistSongsFromFirebase(playlistId: String, remoteSongIds: List<String>) {
        try {
            Log.d(TAG, "Syncing playlist songs from Firebase for playlist $playlistId")
            
            val currentUser = firebaseAuth.currentUser
            if (currentUser == null) return
            
            // Get current local songs in this playlist (V2 system)
            val localV2Songs = musicDao.getPlaylistSongsV2(playlistId).first()
            val localV2SongIds = localV2Songs.map { it.id }.toSet()
            val remoteSongIdsSet = remoteSongIds.toSet()
            
            // Songs to add (in remote but not in local)
            val songsToAdd = remoteSongIdsSet - localV2SongIds
            // Songs to remove (in local but not in remote)
            val songsToRemove = localV2SongIds - remoteSongIdsSet
            
            Log.d(TAG, "Playlist sync - To add: $songsToAdd, To remove: $songsToRemove")
            
            // Add new songs to both V1 and V2 systems
            for (songId in songsToAdd) {
                try {
                    val song = getSongFromFirebase(songId, currentUser)
                    if (song != null) {
                        // Add to V2 system
                        musicDao.insertSongPlaylistCrossRef(
                            SongPlaylistCrossRef(
                                songId = song.id,
                                playlistId = playlistId
                            )
                        )
                        
                        // Add to V1 system
                        val updatedPlaylistIds = song.addPlaylistId(playlistId)
                        musicDao.updateSongPlaylistIds(song.id, updatedPlaylistIds)
                        
                        Log.d(TAG, "Added song ${song.title} to playlist $playlistId in both systems")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding song $songId to playlist $playlistId", e)
                }
            }
            
            // Remove songs from both V1 and V2 systems
            for (songId in songsToRemove) {
                try {
                    // Remove from V2 system
                    musicDao.deleteSongPlaylistCrossRef(songId, playlistId)
                    
                    // Remove from V1 system
                    val song = musicDao.getSongById(songId)
                    if (song != null) {
                        val updatedPlaylistIds = song.removePlaylistId(playlistId)
                        musicDao.updateSongPlaylistIds(song.id, updatedPlaylistIds)
                    }
                    
                    Log.d(TAG, "Removed song $songId from playlist $playlistId in both systems")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing song $songId from playlist $playlistId", e)
                }
            }
            
            Log.d(TAG, "Playlist songs sync completed for playlist $playlistId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing playlist songs from Firebase for playlist $playlistId", e)
        }
    }

    override suspend fun getSongById(songId: String): Song? {
        return musicDao.getSongById(songId)
    }

    override suspend fun getSongsByIds(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        // Fetch all songs from the database and filter by IDs, preserving order
        val allSongs = musicDao.getAllSongs().first()
        return ids.mapNotNull { id -> allSongs.find { it.id == id } }
    }

    // Downloaded songs methods with Firebase sync and real-time listeners
    override fun getDownloadedSongs(): Flow<List<Song>> = flow {
        val currentUser = firebaseAuth.currentUser
        
        Log.d(TAG, "getDownloadedSongs called, current user: ${currentUser?.uid ?: "null"}")
        
        // Get local downloaded songs first
        val localDownloadedSongs = musicDao.getDownloadedSongs().first()
        Log.d(TAG, "Local downloaded songs count: ${localDownloadedSongs.size}")
        emit(localDownloadedSongs)
        
        // Set up real-time listener if authenticated
        if (currentUser != null) {
            try {
                Log.d(TAG, "Setting up real-time listener for downloaded songs")
                
                val downloadedSongsCollection = firestore.collection("users").document(currentUser.uid)
                    .collection("downloaded_songs")
                
                // Set up real-time listener
                val listener = downloadedSongsCollection.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error in downloaded songs listener", error)
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null) {
                        val remoteSongIds: List<String> = snapshot.documents.map { it.id }.filterNotNull()
                        Log.d(TAG, "Real-time update: Remote downloaded songs: $remoteSongIds")
                        
                        // Update local database based on remote changes
                        CoroutineScope(Dispatchers.IO).launch {
                            updateLocalDownloadedSongsFromRemote(remoteSongIds)
                        }
                    }
                }
                
                // Keep the listener active and emit updates
                try {
                    // Initial sync
                    val initialSnapshot = downloadedSongsCollection.get().await()
                    val initialRemoteSongIds = initialSnapshot.documents.map { it.id }.filterNotNull()
                    updateLocalDownloadedSongsFromRemote(initialRemoteSongIds)
                    
                    // Emit current state
                    val currentDownloadedSongs = musicDao.getDownloadedSongs().first()
                    emit(currentDownloadedSongs)
                    
                    // Keep the flow alive for real-time updates
                    while (true) {
                        delay(1000) // Check for updates every second
                        val updatedDownloadedSongs = musicDao.getDownloadedSongs().first()
                        emit(updatedDownloadedSongs)
                    }
                } finally {
                    listener.remove()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up downloaded songs real-time listener", e)
                // Fallback to local downloaded songs only
                emitAll(musicDao.getDownloadedSongs())
            }
        } else {
            Log.d(TAG, "No user authenticated, using local downloaded songs only")
        }
    }.distinctUntilChanged()

    override suspend fun markSongAsDownloaded(song: Song, localFilePath: String) {
        val currentUser = firebaseAuth.currentUser
        
        // First check if song exists in database
        val existingSong = musicDao.getSongById(song.id)
        if (existingSong == null) {
            // If song doesn't exist, insert it with downloaded flag
            musicDao.insertSong(song.copy(isDownloaded = true, localFilePath = localFilePath))
        } else {
            // If song exists, update download status
            musicDao.updateSongDownloadStatus(song.id, true, localFilePath)
        }
        
        // Update SharedPreferences to keep all tracking systems in sync
        try {
            val preferenceManager = com.nova.music.util.PreferenceManager(context)
            preferenceManager.addDownloadedSongId(song.id)
            Log.d(TAG, "Updated SharedPreferences for downloaded song: ${song.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating SharedPreferences for downloaded song", e)
        }
        
        // Sync to Firestore if authenticated
        if (currentUser != null) {
            try {
                // Store song details in user-specific songs collection with correct downloaded status
                val songWithDownloadedStatus = song.copy(isDownloaded = true)
                firestore.collection("users").document(currentUser.uid)
                    .collection("songs")
                    .document(song.id)
                    .set(songWithDownloadedStatus)
                    .await()
                
                // Add to downloaded songs in Firestore
                firestore.collection("users").document(currentUser.uid)
                    .collection("downloaded_songs")
                    .document(song.id)
                    .set(mapOf(
                        "songId" to song.id,
                        "userId" to currentUser.uid,
                        "localFilePath" to localFilePath,
                        "downloadedAt" to System.currentTimeMillis()
                    ))
                    .await()
                Log.d(TAG, "Added song to downloaded songs in Firestore: ${song.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding song to downloaded songs in Firestore", e)
            }
        }
    }

    override suspend fun markSongAsNotDownloaded(songId: String) {
        val currentUser = firebaseAuth.currentUser
        
        // Update local database
        musicDao.updateSongDownloadStatus(songId, false, null)
        
        // Update SharedPreferences to keep all tracking systems in sync
        try {
            val preferenceManager = com.nova.music.util.PreferenceManager(context)
            preferenceManager.removeDownloadedSongId(songId)
            Log.d(TAG, "Updated SharedPreferences for undownloaded song: $songId")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating SharedPreferences for undownloaded song", e)
        }
        
        // Remove from Firestore if authenticated
        if (currentUser != null) {
            try {
                firestore.collection("users").document(currentUser.uid)
                    .collection("downloaded_songs")
                    .document(songId)
                    .delete()
                    .await()
                Log.d(TAG, "Removed song from downloaded songs in Firestore: $songId")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing song from downloaded songs in Firestore", e)
            }
        }
    }

    override suspend fun isDownloaded(songId: String): Boolean {
        val song = musicDao.getSongById(songId)
        return song?.isDownloaded == true
    }

    /**
     * Helper method to update local liked songs based on remote Firebase data
     */
    private suspend fun updateLocalLikedSongsFromRemote(remoteSongIds: List<String>) {
        try {
            Log.d(TAG, "updateLocalLikedSongsFromRemote called with ${remoteSongIds.size} remote song IDs: $remoteSongIds")
            
            val currentLocalLikedSongs = musicDao.getLikedSongs().first()
            val currentLocalLikedSongIds = currentLocalLikedSongs.map { it.id }.toSet()
            val remoteSongIdsSet = remoteSongIds.toSet()
            
            Log.d(TAG, "Current local liked songs: $currentLocalLikedSongIds")
            Log.d(TAG, "Remote song IDs set: $remoteSongIdsSet")
            
            // Songs to add (in remote but not in local)
            val songsToAdd = remoteSongIdsSet - currentLocalLikedSongIds
            // Songs to remove (in local but not in remote)
            val songsToRemove = currentLocalLikedSongIds - remoteSongIdsSet
            
            Log.d(TAG, "Updating local liked songs - To add: $songsToAdd, To remove: $songsToRemove")
            
            // Add new liked songs
            for (songId in songsToAdd) {
                try {
                    Log.d(TAG, "Processing song to add: $songId")
                    val song = getSongFromFirebase(songId, firebaseAuth.currentUser)
                    if (song != null) {
                        musicDao.insertSong(song.copy(isLiked = true))
                        Log.d(TAG, "Successfully added remote liked song to local database: $songId (${song.title})")
                    } else {
                        Log.w(TAG, "Song document exists but could not be converted to Song object or has empty ID: $songId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting song details for $songId", e)
                }
            }
            
            // Remove unliked songs
            for (songId in songsToRemove) {
                musicDao.updateSongLikedStatus(songId, false)
                Log.d(TAG, "Removed liked status from local database: $songId")
            }
            
            // Verify the final state
            val finalLocalLikedSongs = musicDao.getLikedSongs().first()
            val finalLocalLikedIds = finalLocalLikedSongs.map { it.id }.toSet()
            
            Log.d(TAG, "Final local liked songs count: ${finalLocalLikedSongs.size}")
            Log.d(TAG, "Final local liked songs: ${finalLocalLikedSongs.map { "${it.id} (${it.title})" }}")
            
            // Double-check consistency
            if (finalLocalLikedIds != remoteSongIdsSet) {
                Log.w(TAG, "Inconsistency still detected after sync! Final local: $finalLocalLikedIds, Remote: $remoteSongIdsSet")
                
                // Force sync by clearing all liked songs and re-adding from remote
                Log.d(TAG, "Force syncing by clearing all liked songs and re-adding from remote")
                musicDao.clearAllLikedSongs()
                
                for (songId in remoteSongIds) {
                    try {
                        val song = getSongFromFirebase(songId, firebaseAuth.currentUser)
                        if (song != null) {
                            musicDao.insertSong(song.copy(isLiked = true))
                            Log.d(TAG, "Force added liked song: $songId (${song.title})")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error force adding liked song: $songId", e)
                    }
                }
                
                val finalCheck = musicDao.getLikedSongs().first()
                Log.d(TAG, "Final check after force sync: ${finalCheck.size} songs")
            } else {
                Log.d(TAG, "Liked songs sync completed successfully - counts match!")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating local liked songs from remote", e)
        }
    }

    /**
     * Helper method to get song details from user-specific Firebase collection
     */
    private suspend fun getSongFromFirebase(songId: String, currentUser: com.google.firebase.auth.FirebaseUser?): Song? {
        if (currentUser == null) return null
        
        try {
            // Get song from user-specific songs collection
            val userSongDoc = firestore.collection("users").document(currentUser.uid)
                .collection("songs")
                .document(songId)
                .get()
                .await()
            
            if (userSongDoc.exists()) {
                val song = userSongDoc.toObject(Song::class.java)
                if (song != null && song.id.isNotBlank()) {
                    Log.d(TAG, "Found song in user-specific collection: $songId")
                    return song
                }
            }
            
            Log.w(TAG, "Song not found in user collection: $songId")
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting song from Firebase: $songId", e)
            return null
        }
    }
    
    /**
     * Clean up the global songs collection (use with caution!)
     * This method will delete all documents in the global songs collection
     * Only use this after ensuring all songs are properly migrated to user-specific collections
     */
    override suspend fun cleanupGlobalSongsCollection() {
        try {
            Log.d(TAG, "Starting cleanup of global songs collection")
            
            // Get all documents in the global songs collection
            val globalSongsSnapshot = firestore.collection("songs").get().await()
            val songIds = globalSongsSnapshot.documents.map { it.id }.filterNotNull()
            
            Log.d(TAG, "Found ${songIds.size} songs in global collection to delete")
            
            // Delete each document
            for (songId in songIds) {
                try {
                    firestore.collection("songs").document(songId).delete().await()
                    Log.d(TAG, "Deleted global song document: $songId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting global song document: $songId", e)
                }
            }
            
            Log.d(TAG, "Global songs collection cleanup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during global songs collection cleanup", e)
        }
    }

    /**
     * Helper method to update local downloaded songs based on remote Firebase data
     */
    private suspend fun updateLocalDownloadedSongsFromRemote(remoteSongIds: List<String>) {
        try {
            val currentUser = firebaseAuth.currentUser
            val currentLocalDownloadedSongs = musicDao.getDownloadedSongs().first()
            val currentLocalDownloadedSongIds = currentLocalDownloadedSongs.map { it.id }.toSet()
            val remoteSongIdsSet = remoteSongIds.toSet()
            
            // Songs to add (in remote but not in local)
            val songsToAdd = remoteSongIdsSet - currentLocalDownloadedSongIds
            // Songs to remove (in local but not in remote)
            val songsToRemove = currentLocalDownloadedSongIds - remoteSongIdsSet
            
            Log.d(TAG, "Updating local downloaded songs - To add: $songsToAdd, To remove: $songsToRemove")
            
            // Get PreferenceManager for syncing
            val preferenceManager = com.nova.music.util.PreferenceManager(context)
            
            // Add new downloaded songs
            for (songId in songsToAdd) {
                try {
                    val song = getSongFromFirebase(songId, currentUser)
                    if (song != null) {
                        musicDao.insertSong(song.copy(isDownloaded = true))
                        // Also update SharedPreferences
                        preferenceManager.addDownloadedSongId(songId)
                        Log.d(TAG, "Successfully added remote downloaded song to local database and preferences: $songId (${song.title})")
                    } else {
                        Log.w(TAG, "Song document exists but could not be converted to Song object or has empty ID: $songId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting song details for $songId", e)
                }
            }
            
            // Remove undownloaded songs
            for (songId in songsToRemove) {
                musicDao.updateSongDownloadStatus(songId, false, null)
                // Also update SharedPreferences
                preferenceManager.removeDownloadedSongId(songId)
                Log.d(TAG, "Removed downloaded status from local database and preferences: $songId")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating local downloaded songs from remote", e)
        }
    }

    /**
     * Helper method to update local playlists based on remote Firebase data
     */
    private suspend fun updateLocalPlaylistsFromRemote(snapshot: com.google.firebase.firestore.QuerySnapshot) {
        try {
            val remotePlaylists: List<Playlist> = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Playlist::class.java)?.copy(id = doc.id)
            }
            
            val currentLocalPlaylists = musicDao.getPlaylists().first()
            val currentLocalPlaylistIds = currentLocalPlaylists.map { it.id }.toSet()
            val remotePlaylistIds = remotePlaylists.map { it.id }.toSet()
            
            // Playlists to add (in remote but not in local)
            val playlistsToAdd = remotePlaylistIds - currentLocalPlaylistIds
            // Playlists to remove (in local but not in remote)
            val playlistsToRemove = currentLocalPlaylistIds - remotePlaylistIds
            
            Log.d(TAG, "Updating local playlists - To add: $playlistsToAdd, To remove: $playlistsToRemove")
            
            // Add new playlists
            for (playlistId in playlistsToAdd) {
                val remotePlaylist = remotePlaylists.find { it.id == playlistId }
                if (remotePlaylist != null) {
                    musicDao.createPlaylist(remotePlaylist)
                    Log.d(TAG, "Added remote playlist to local database: ${remotePlaylist.name}")
                }
            }
            
            // Remove deleted playlists
            for (playlistId in playlistsToRemove) {
                val localPlaylist = currentLocalPlaylists.find { it.id == playlistId }
                if (localPlaylist != null) {
                    musicDao.deletePlaylist(localPlaylist)
                    Log.d(TAG, "Removed playlist from local database: ${localPlaylist.name}")
                }
            }
            
            // Update existing playlists
            for (remotePlaylist in remotePlaylists) {
                val localPlaylist = currentLocalPlaylists.find { it.id == remotePlaylist.id }
                if (localPlaylist != null && localPlaylist.name != remotePlaylist.name) {
                    musicDao.renamePlaylist(remotePlaylist.id, remotePlaylist.name)
                    Log.d(TAG, "Updated playlist name in local database: ${remotePlaylist.name}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating local playlists from remote", e)
        }
    }

    // Liked songs methods with Firebase sync and real-time listeners
    override fun getLikedSongs(): Flow<List<Song>> = flow {
        val currentUser = firebaseAuth.currentUser
        
        Log.d(TAG, "getLikedSongs called, current user: ${currentUser?.uid ?: "null"}")
        
        // Get local liked songs first
        val localLikedSongs = musicDao.getLikedSongs().first()
        Log.d(TAG, "Local liked songs count: ${localLikedSongs.size}")
        emit(localLikedSongs)
        
        // Set up real-time listener if authenticated
        if (currentUser != null) {
            try {
                Log.d(TAG, "Setting up real-time listener for liked songs")
                
                // Try new structure first (sub-collection)
                try {
                    val likedSongsCollection = firestore.collection("users").document(currentUser.uid)
                        .collection("liked_songs")
                    
                    // Set up real-time listener
                    val listener = likedSongsCollection.addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Error in liked songs listener", error)
                            return@addSnapshotListener
                        }
                        
                        if (snapshot != null) {
                            val remoteSongIds: List<String> = snapshot.documents.map { it.id }.filterNotNull()
                            Log.d(TAG, "Real-time update: Remote liked songs: $remoteSongIds")
                            
                            // Update local database based on remote changes
                            CoroutineScope(Dispatchers.IO).launch {
                                updateLocalLikedSongsFromRemote(remoteSongIds)
                            }
                        }
                    }
                    
                    // Keep the listener active and emit updates
                    try {
                        // Initial sync
                        val initialSnapshot = likedSongsCollection.get().await()
                        val initialRemoteSongIds = initialSnapshot.documents.map { it.id }.filterNotNull()
                        updateLocalLikedSongsFromRemote(initialRemoteSongIds)
                        
                        // Emit current state
                        val currentLikedSongs = musicDao.getLikedSongs().first()
                        emit(currentLikedSongs)
                        
                        // Keep the flow alive for real-time updates
                        while (true) {
                            delay(1000) // Check for updates every second
                            val updatedLikedSongs = musicDao.getLikedSongs().first()
                            emit(updatedLikedSongs)
                        }
                    } finally {
                        listener.remove()
                    }
                    
                } catch (e: Exception) {
                    Log.d(TAG, "New structure failed, using legacy structure with real-time listener: ${e.message}")
                    
                    // Fallback to legacy structure with real-time listener
                    val userDocRef = firestore.collection("users").document(currentUser.uid)
                    
                    val listener = userDocRef.addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Error in user document listener", error)
                            return@addSnapshotListener
                        }
                        
                        if (snapshot != null && snapshot.exists()) {
                            val favoriteSongs = snapshot.get("favoriteSongs") as? List<Map<String, Any>> ?: emptyList()
                            val remoteSongIds = favoriteSongs.mapNotNull { it["id"] as? String }
                            Log.d(TAG, "Real-time update: Legacy favoriteSongs: $remoteSongIds")
                            
                            // Update local database based on remote changes
                            CoroutineScope(Dispatchers.IO).launch {
                                updateLocalLikedSongsFromRemote(remoteSongIds)
                            }
                        }
                    }
                    
                    // Keep the listener active and emit updates
                    try {
                        // Initial sync
                        val initialSnapshot = userDocRef.get().await()
                        val favoriteSongs = initialSnapshot.get("favoriteSongs") as? List<Map<String, Any>> ?: emptyList()
                        val initialRemoteSongIds = favoriteSongs.mapNotNull { it["id"] as? String }
                        updateLocalLikedSongsFromRemote(initialRemoteSongIds)
                        
                        // Emit current state
                        val currentLikedSongs = musicDao.getLikedSongs().first()
                        emit(currentLikedSongs)
                        
                        // Keep the flow alive for real-time updates
                        while (true) {
                            delay(1000) // Check for updates every second
                            val updatedLikedSongs = musicDao.getLikedSongs().first()
                            emit(updatedLikedSongs)
                        }
                    } finally {
                        listener.remove()
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up real-time listener", e)
                // Fallback to local liked songs only
                emitAll(musicDao.getLikedSongs())
            }
        } else {
            Log.d(TAG, "No user authenticated, using local liked songs only")
        }
    }.distinctUntilChanged()

    override suspend fun addSongToLiked(song: Song) {
        val currentUser = firebaseAuth.currentUser
        
        Log.d(TAG, "addSongToLiked called for song: ${song.title} (${song.id})")
        Log.d(TAG, "Current user: ${currentUser?.uid ?: "null"}")
        
        // First check if song exists in database
        val existingSong = musicDao.getSongById(song.id)
        if (existingSong == null) {
            // If song doesn't exist, insert it with liked status
            musicDao.insertSong(song.copy(isLiked = true))
            Log.d(TAG, "Inserted new song with liked status: ${song.title}")
        } else {
            // If song exists, update liked status
            musicDao.updateSongLikedStatus(song.id, true)
            Log.d(TAG, "Updated existing song liked status: ${song.title}")
        }
        
        // Sync to Firestore if authenticated
        if (currentUser != null) {
            try {
                Log.d(TAG, "Starting Firebase sync for song: ${song.title}")
                
                // Store song details in user-specific songs collection with correct liked status
                val songWithLikedStatus = song.copy(isLiked = true)
                firestore.collection("users").document(currentUser.uid)
                    .collection("songs")
                    .document(song.id)
                    .set(songWithLikedStatus)
                    .await()
                Log.d(TAG, "Stored song details in user-specific songs collection with liked=true: ${song.title}")
                
                // Try to add to new structure first (sub-collection)
                try {
                    firestore.collection("users").document(currentUser.uid)
                        .collection("liked_songs")
                        .document(song.id)
                        .set(mapOf(
                            "songId" to song.id,
                            "userId" to currentUser.uid,
                            "addedAt" to System.currentTimeMillis()
                        ))
                        .await()
                    Log.d(TAG, "Added song to liked songs in Firestore (new structure): ${song.title}")
                } catch (e: Exception) {
                    Log.d(TAG, "New structure failed, using legacy structure: ${e.message}")
                    
                    // Fallback to legacy structure (array field)
                    val userDoc = firestore.collection("users").document(currentUser.uid).get().await()
                    val currentFavoriteSongs = userDoc.get("favoriteSongs") as? List<Map<String, Any>> ?: emptyList()
                    
                    Log.d(TAG, "Current favoriteSongs in Firestore: $currentFavoriteSongs")
                    
                    // Check if song is already in favorites
                    val songAlreadyExists = currentFavoriteSongs.any { it["id"] == song.id }
                    
                    if (!songAlreadyExists) {
                        val newFavoriteSong = mapOf("id" to song.id)
                        val updatedFavoriteSongs = currentFavoriteSongs + newFavoriteSong
                        
                        Log.d(TAG, "Updating favoriteSongs array with: $updatedFavoriteSongs")
                        
                        firestore.collection("users").document(currentUser.uid)
                            .update("favoriteSongs", updatedFavoriteSongs)
                            .await()
                        Log.d(TAG, "Added song to favoriteSongs array in Firestore: ${song.title}")
                    } else {
                        Log.d(TAG, "Song already exists in favoriteSongs array: ${song.title}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding song to liked songs in Firestore", e)
            }
        } else {
            Log.d(TAG, "No user authenticated, skipping Firebase sync")
        }
    }

    override suspend fun removeSongFromLiked(songId: String) {
        val currentUser = firebaseAuth.currentUser
        
        Log.d(TAG, "removeSongFromLiked called for songId: $songId")
        Log.d(TAG, "Current user: ${currentUser?.uid ?: "null"}")
        
        // Update local database
        musicDao.updateSongLikedStatus(songId, false)
        Log.d(TAG, "Updated local database for songId: $songId")
        
        // Remove from Firestore if authenticated
        if (currentUser != null) {
            try {
                Log.d(TAG, "Starting Firebase sync for removing song: $songId")
                
                // Update song's liked status to false in user-specific songs collection
                val song = musicDao.getSongById(songId)
                if (song != null) {
                    val songWithUnlikedStatus = song.copy(isLiked = false)
                    firestore.collection("users").document(currentUser.uid)
                        .collection("songs")
                        .document(songId)
                        .set(songWithUnlikedStatus)
                        .await()
                    Log.d(TAG, "Updated song liked status to false in user-specific songs collection: $songId")
                }
                
                // Try to remove from new structure first (sub-collection)
                try {
                    firestore.collection("users").document(currentUser.uid)
                        .collection("liked_songs")
                        .document(songId)
                        .delete()
                        .await()
                    Log.d(TAG, "Removed song from liked songs in Firestore (new structure): $songId")
                } catch (e: Exception) {
                    Log.d(TAG, "New structure failed, using legacy structure: ${e.message}")
                    
                    // Fallback to legacy structure (array field)
                    val userDoc = firestore.collection("users").document(currentUser.uid).get().await()
                    val currentFavoriteSongs = userDoc.get("favoriteSongs") as? List<Map<String, Any>> ?: emptyList()
                    
                    Log.d(TAG, "Current favoriteSongs in Firestore: $currentFavoriteSongs")
                    
                    // Remove the song from favorites
                    val updatedFavoriteSongs = currentFavoriteSongs.filter { it["id"] != songId }
                    
                    Log.d(TAG, "Updating favoriteSongs array with: $updatedFavoriteSongs")
                    
                    firestore.collection("users").document(currentUser.uid)
                        .update("favoriteSongs", updatedFavoriteSongs)
                        .await()
                    Log.d(TAG, "Removed song from favoriteSongs array in Firestore: $songId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing song from liked songs in Firestore", e)
            }
        } else {
            Log.d(TAG, "No user authenticated, skipping Firebase sync")
        }
    }

    /**
     * Sync all user data with Firebase when user logs in or app starts
     */
    override suspend fun syncUserDataWithFirebase() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "No user logged in, skipping Firebase sync")
            return
        }

        try {
            Log.d(TAG, "Starting Firebase sync for user: ${currentUser.uid}")
            
            // Sync liked songs
            val localLikedSongs = musicDao.getLikedSongs().first()
            
            // Try to sync to new structure first (sub-collection)
            try {
                for (song in localLikedSongs) {
                    try {
                        // Store song details in user-specific songs collection with correct liked status
                        val songWithLikedStatus = song.copy(isLiked = true)
                        firestore.collection("users").document(currentUser.uid)
                            .collection("songs")
                            .document(song.id)
                            .set(songWithLikedStatus)
                            .await()
                        
                        // Add to liked songs in Firestore
                        firestore.collection("users").document(currentUser.uid)
                            .collection("liked_songs")
                            .document(song.id)
                            .set(mapOf(
                                "songId" to song.id,
                                "userId" to currentUser.uid,
                                "addedAt" to System.currentTimeMillis()
                            ))
                            .await()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing liked song to Firestore: ${song.id}", e)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "New structure failed, using legacy structure for liked songs")
                
                // Fallback to legacy structure (array field)
                val favoriteSongs = localLikedSongs.map { mapOf("id" to it.id) }
                firestore.collection("users").document(currentUser.uid)
                    .update("favoriteSongs", favoriteSongs)
                    .await()
                Log.d(TAG, "Synced ${favoriteSongs.size} liked songs to legacy structure")
            }
            
            // Sync downloaded songs
            val localDownloadedSongs = musicDao.getDownloadedSongs().first()
                            for (song in localDownloadedSongs) {
                    try {
                        // Store song details in user-specific songs collection with correct downloaded status
                        val songWithDownloadedStatus = song.copy(isDownloaded = true)
                        firestore.collection("users").document(currentUser.uid)
                            .collection("songs")
                            .document(song.id)
                            .set(songWithDownloadedStatus)
                            .await()
                    
                    // Add to downloaded songs in Firestore
                    firestore.collection("users").document(currentUser.uid)
                        .collection("downloaded_songs")
                        .document(song.id)
                        .set(mapOf(
                            "songId" to song.id,
                            "userId" to currentUser.uid,
                            "localFilePath" to song.localFilePath,
                            "downloadedAt" to System.currentTimeMillis()
                        ))
                        .await()
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing downloaded song to Firestore: ${song.id}", e)
                }
            }
            
            // Sync playlists
            val localPlaylists = musicDao.getPlaylists().first()
            
            // Try to sync to new structure first (sub-collection)
            try {
                for (playlist in localPlaylists) {
                    try {
                        // Store playlist in Firestore
                        firestore.collection("users").document(currentUser.uid)
                            .collection("playlists")
                            .document(playlist.id)
                            .set(playlist)
                            .await()
                        
                        // Sync playlist songs
                        val playlistSongs = musicDao.getPlaylistSongsV2(playlist.id).first()
                        for (song in playlistSongs) {
                            try {
                                // Store song details in user-specific songs collection
                                firestore.collection("users").document(currentUser.uid)
                                    .collection("songs")
                                    .document(song.id)
                                    .set(song)
                                    .await()
                                
                                // Add song to playlist in Firestore
                                firestore.collection("users").document(currentUser.uid)
                                    .collection("playlists")
                                    .document(playlist.id)
                                    .collection("songs")
                                    .document(song.id)
                                    .set(mapOf(
                                        "songId" to song.id,
                                        "addedAt" to System.currentTimeMillis()
                                    ))
                                    .await()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error syncing playlist song to Firestore: ${song.id} in playlist ${playlist.id}", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing playlist to Firestore: ${playlist.id}", e)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "New structure failed, using legacy structure for playlists")
                
                // Fallback to legacy structure (field)
                // For now, just store playlist names as a simple structure
                val playlistsData = localPlaylists.map { playlist ->
                    mapOf(
                        "id" to playlist.id,
                        "name" to playlist.name,
                        "songCount" to musicDao.getPlaylistSongCountV2(playlist.id).first()
                    )
                }
                firestore.collection("users").document(currentUser.uid)
                    .update("playlists", playlistsData)
                    .await()
                Log.d(TAG, "Synced ${playlistsData.size} playlists to legacy structure")
            }
            
            Log.d(TAG, "Firebase sync completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during Firebase sync", e)
        }
    }

    /**
     * Refresh Firebase listeners when user switches accounts
     * This forces the listeners to re-initialize with the new user's data
     */
    override suspend fun refreshFirebaseListeners() {
        val currentUser = firebaseAuth.currentUser
        Log.d(TAG, "Refreshing Firebase listeners for user: ${currentUser?.uid ?: "null"}")
        
        if (currentUser == null) {
            Log.d(TAG, "No user logged in, clearing local data")
            // Clear local data when no user is logged in
            musicDao.clearAllLikedSongs()
            musicDao.clearAllDownloadedSongs()
            musicDao.clearAllPlaylists()
            return
        }
        
        try {
            // Force refresh by clearing local data and re-syncing from Firebase
            Log.d(TAG, "Clearing local data to force refresh from Firebase")
            
            // Clear local data
            musicDao.clearAllLikedSongs()
            musicDao.clearAllDownloadedSongs()
            musicDao.clearAllPlaylists()
            musicDao.clearAllPlaylistSongs()
            
            // Also clear SharedPreferences to keep everything in sync
            try {
                val preferenceManager = com.nova.music.util.PreferenceManager(context)
                val downloadedIds = preferenceManager.getDownloadedSongIds()
                downloadedIds.forEach { songId ->
                    preferenceManager.removeDownloadedSongId(songId)
                }
                Log.d(TAG, "Cleared SharedPreferences for downloaded songs: ${downloadedIds.size} songs")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing SharedPreferences", e)
            }
            
            // Small delay to ensure clearing is complete
            delay(100)
            
            // Sync FROM Firebase TO local data
            Log.d(TAG, "Syncing data FROM Firebase for new user")
            syncFromFirebase()
            
            Log.d(TAG, "Firebase listeners refreshed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing Firebase listeners", e)
        }
    }
    
    /**
     * Sync data FROM Firebase TO local database
     */
    override suspend fun syncFromFirebase() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "No user logged in, skipping Firebase sync")
            return
        }

        try {
            Log.d(TAG, "Starting sync FROM Firebase for user: ${currentUser.uid}")
            
            // Sync liked songs FROM Firebase
            try {
                Log.d(TAG, "Starting liked songs sync FROM Firebase")
                
                // Try new structure first (sub-collection)
                val likedSongsCollection = firestore.collection("users").document(currentUser.uid)
                    .collection("liked_songs")
                
                Log.d(TAG, "Fetching from collection: users/${currentUser.uid}/liked_songs")
                val likedSongsSnapshot = likedSongsCollection.get().await()
                val likedSongIds = likedSongsSnapshot.documents.map { it.id }.filterNotNull()
                
                Log.d(TAG, "Found ${likedSongIds.size} liked songs in Firebase: $likedSongIds")
                
                // Update local database with liked songs from Firebase
                updateLocalLikedSongsFromRemote(likedSongIds)
                
                // Also verify consistency with database
                try {
                    val localLikedSongs = musicDao.getLikedSongs().first()
                    val localLikedIds = localLikedSongs.map { it.id }.toSet()
                    val remoteLikedIds = likedSongIds.toSet()
                    
                    Log.d(TAG, "Liked songs consistency check - Local: ${localLikedIds.size}, Remote: ${remoteLikedIds.size}")
                    Log.d(TAG, "Local liked songs: $localLikedIds")
                    Log.d(TAG, "Remote liked songs: $remoteLikedIds")
                    
                    if (localLikedIds != remoteLikedIds) {
                        Log.w(TAG, "Liked songs inconsistency detected! Syncing to match Firebase...")
                        // Force sync local database to match Firebase
                        updateLocalLikedSongsFromRemote(likedSongIds)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error verifying liked songs consistency", e)
                }
                
            } catch (e: Exception) {
                Log.d(TAG, "New structure failed, trying legacy structure for liked songs: ${e.message}")
                
                // Fallback to legacy structure (array field)
                val userDoc = firestore.collection("users").document(currentUser.uid).get().await()
                val favoriteSongs = userDoc.get("favoriteSongs") as? List<Map<String, Any>> ?: emptyList()
                val likedSongIds = favoriteSongs.mapNotNull { it["id"] as? String }
                
                Log.d(TAG, "Found ${likedSongIds.size} liked songs in Firebase (legacy)")
                
                // Update local database with liked songs from Firebase
                updateLocalLikedSongsFromRemote(likedSongIds)
                
                // Also verify consistency with database
                try {
                    val localLikedSongs = musicDao.getLikedSongs().first()
                    val localLikedIds = localLikedSongs.map { it.id }.toSet()
                    val remoteLikedIds = likedSongIds.toSet()
                    
                    Log.d(TAG, "Liked songs consistency check (legacy) - Local: ${localLikedIds.size}, Remote: ${remoteLikedIds.size}")
                    
                    if (localLikedIds != remoteLikedIds) {
                        Log.w(TAG, "Liked songs inconsistency detected (legacy)! Syncing to match Firebase...")
                        // Force sync local database to match Firebase
                        updateLocalLikedSongsFromRemote(likedSongIds)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error verifying liked songs consistency (legacy)", e)
                }
            }
            
            // Sync downloaded songs FROM Firebase
            try {
                val downloadedSongsCollection = firestore.collection("users").document(currentUser.uid)
                    .collection("downloaded_songs")
                
                val downloadedSongsSnapshot = downloadedSongsCollection.get().await()
                val downloadedSongIds = downloadedSongsSnapshot.documents.map { it.id }.filterNotNull()
                
                Log.d(TAG, "Found ${downloadedSongIds.size} downloaded songs in Firebase")
                
                // Update local database with downloaded songs from Firebase
                updateLocalDownloadedSongsFromRemote(downloadedSongIds)
                
                // Also sync SharedPreferences with database to ensure consistency
                try {
                    val preferenceManager = com.nova.music.util.PreferenceManager(context)
                    val localDownloadedSongs = musicDao.getDownloadedSongs().first()
                    val localDownloadedIds = localDownloadedSongs.map { it.id }.toSet()
                    
                    // Clear and rebuild SharedPreferences to match database
                    localDownloadedIds.forEach { songId ->
                        preferenceManager.addDownloadedSongId(songId)
                    }
                    
                    Log.d(TAG, "Synced SharedPreferences with database: ${localDownloadedIds.size} downloaded songs")
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing SharedPreferences with database", e)
                }
                
            } catch (e: Exception) {
                Log.d(TAG, "Error syncing downloaded songs from Firebase: ${e.message}")
            }
            
            // Sync playlists FROM Firebase
            try {
                val playlistsCollection = firestore.collection("users").document(currentUser.uid)
                    .collection("playlists")
                
                val playlistsSnapshot = playlistsCollection.get().await()
                val remotePlaylists: List<Playlist> = playlistsSnapshot.documents.mapNotNull { doc ->
                    doc.toObject(Playlist::class.java)?.copy(id = doc.id)
                }
                
                Log.d(TAG, "Found ${remotePlaylists.size} playlists in Firebase")
                
                // Update local database with playlists from Firebase
                updateLocalPlaylistsFromRemote(playlistsSnapshot)
                
                // Also sync playlist songs to ensure both V1 and V2 systems are updated
                for (playlist in remotePlaylists) {
                    try {
                        val playlistSongsCollection = firestore.collection("users").document(currentUser.uid)
                            .collection("playlists")
                            .document(playlist.id)
                            .collection("songs")
                        
                        val playlistSongsSnapshot = playlistSongsCollection.get().await()
                        val remoteSongIds = playlistSongsSnapshot.documents.map { it.id }.filterNotNull()
                        
                        Log.d(TAG, "Found ${remoteSongIds.size} songs in playlist ${playlist.name}")
                        
                        // Sync playlist songs to both V1 and V2 systems
                        syncPlaylistSongsFromFirebase(playlist.id, remoteSongIds)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing playlist songs from Firebase for playlist ${playlist.id}", e)
                    }
                }
                
            } catch (e: Exception) {
                Log.d(TAG, "Error syncing playlists from Firebase: ${e.message}")
            }
            
            Log.d(TAG, "Sync FROM Firebase completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync FROM Firebase", e)
        }
    }
} 