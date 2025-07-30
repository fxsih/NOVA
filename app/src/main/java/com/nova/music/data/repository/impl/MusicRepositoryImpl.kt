package com.nova.music.data.repository.impl

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nova.music.data.api.YTMusicService
import com.nova.music.data.local.MusicDao
import com.nova.music.data.model.*
import com.nova.music.data.repository.MusicRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import java.io.File
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

    // Custom coroutine scope for repository operations to prevent premature cancellation
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    init {
        Log.d(TAG, "🔧 MusicRepositoryImpl initialized with repositoryScope")
    }
    
    /**
     * Cleanup method to cancel the repository scope when needed
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Cleaning up repository scope")
        repositoryScope.cancel()
        Log.d(TAG, "🔧 Repository scope cancelled")
    }

    private val recentSearchesKey = stringPreferencesKey("recent_searches")
    private val maxRecentSearches = 10

    // DataStore keys for download persistence
    private val downloadsSetKey = stringSetPreferencesKey("downloads_set")
    private val downloadPathsKey = stringPreferencesKey("download_paths")

    // Placeholder for now - will add methods

    override fun getPlaylists(): Flow<List<Playlist>> = flow {
        val currentUser = firebaseAuth.currentUser
        
        Log.d(TAG, "🔄 getPlaylists() called - User: ${currentUser?.uid ?: "null"}")
        
        // Helper function to emit local playlists with songs
        suspend fun emitLocal() {
            val localPlaylists = musicDao.getPlaylists().first()
            Log.d(TAG, "📋 Found ${localPlaylists.size} local playlists")
            
            val playlistsWithSongs = localPlaylists.map { playlist ->
                val songs = musicDao.getPlaylistSongs(playlist.id).first()
                Log.d(TAG, "🎵 Playlist '${playlist.name}' (${playlist.id}) has ${songs.size} songs")
                playlist.apply { this.songs = songs }
            }
            
            Log.d(TAG, "📊 Emitting ${playlistsWithSongs.size} playlists with songs")
            emit(playlistsWithSongs)
        }
        
        // 1️⃣ Emit immediate local state
        emitLocal()
        
        // 2️⃣ If not signed in, just keep emitting local
        if (currentUser == null) {
            Log.d(TAG, "ℹ️ No user authenticated, using local playlists only")
            musicDao.getPlaylists().collect { playlists ->
                val playlistsWithSongs = playlists.map { playlist ->
                    val songs = musicDao.getPlaylistSongs(playlist.id).first()
                    playlist.apply { this.songs = songs }
                }
                Log.d(TAG, "🔄 Local update: ${playlistsWithSongs.size} playlists")
                emit(playlistsWithSongs)
            }
            return@flow
        }
        
        // 3️⃣ Signed-in: set up Firebase listener and local updates
        try {
            val playlistsCollection = firestore.collection("users")
                .document(currentUser.uid)
                .collection("playlists")
            
            // Set up real-time listener for playlist changes
            val listener = playlistsCollection.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Error in playlists listener", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    Log.d(TAG, "🔥 Firebase real-time update: Playlists changed")
                    // Sync remote → local DB using repository scope
                    repositoryScope.launch {
                        updateLocalPlaylistsFromRemote(snapshot)
                    }
                }
            }
            
            // Initial sync only once using repository scope
            repositoryScope.launch {
                try {
                    val initialSnapshot = playlistsCollection.get().await()
                    updateLocalPlaylistsFromRemote(initialSnapshot)
                    Log.d(TAG, "✅ Initial playlist sync completed")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in initial playlist sync", e)
                }
            }
            
            // Continue listening to local database changes only
            musicDao.getPlaylists().collect { playlists ->
                val playlistsWithSongs = playlists.map { playlist ->
                    val songs = musicDao.getPlaylistSongs(playlist.id).first()
                    playlist.apply { this.songs = songs }
                }
                Log.d(TAG, "🔄 Local database update: ${playlistsWithSongs.size} playlists")
                emit(playlistsWithSongs)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up Firebase real-time listener", e)
            // Fallback to local playlists only
            Log.d(TAG, "🔄 Falling back to local-only mode")
            musicDao.getPlaylists().collect { playlists ->
                val playlistsWithSongs = playlists.map { playlist ->
                    val songs = musicDao.getPlaylistSongs(playlist.id).first()
                    playlist.apply { this.songs = songs }
                }
                Log.d(TAG, "🔄 Local fallback update: ${playlistsWithSongs.size} playlists")
                emit(playlistsWithSongs)
            }
        }
    }.distinctUntilChanged { old, new ->
        // Compare by id, name, and song count only
        old.size == new.size && old.zip(new).all { (o, n) ->
            o.id == n.id && o.name == n.name && o.songs.size == n.songs.size
        }
    }

    override fun getAllSongs(): Flow<List<Song>> = musicDao.getAllSongs()

    override fun getRecommendedSongs(genres: String, languages: String, artists: String): Flow<List<Song>> {
        return getRecommendedSongs(genres, languages, artists, false)
    }

    override fun getRecommendedSongs(genres: String, languages: String, artists: String, forceRefresh: Boolean): Flow<List<Song>> = flow {
        try {
            Log.d(TAG, "Fetching recommendations with genres: $genres, languages: $languages, artists: $artists, forceRefresh: $forceRefresh")
            
            if (forceRefresh) {
                Log.d(TAG, "Force refresh requested, clearing cached recommendations")
                musicDao.clearRecommendedSongs()
                emit(emptyList())
            }
            
            val cacheBustValue = if (forceRefresh) System.currentTimeMillis() else 0
            val recommendedResults = ytMusicService.getRecommendations(
                genres = genres.takeIf { it.isNotBlank() },
                languages = languages.takeIf { it.isNotBlank() },
                artists = artists.takeIf { it.isNotBlank() },
                cacheBust = cacheBustValue
            )
            
            Log.d(TAG, "Received ${recommendedResults.size} recommended songs")
            val songs = recommendedResults.map { it.toSong().copy(isRecommended = true) }
            
            // Apply duration filter to exclude songs longer than 15 minutes
            val filteredSongs = filterSongsByDuration(songs)
            
            try {
                musicDao.clearRecommendedSongs()
                if (filteredSongs.isNotEmpty()) {
                    val currentLikedSongs = musicDao.getLikedSongs().first()
                    val likedSongIds = currentLikedSongs.map { it.id }.toSet()
                    
                    val songsWithPreservedLikes = filteredSongs.map { song ->
                        if (likedSongIds.contains(song.id)) {
                            song.copy(isLiked = true)
                        } else {
                            song
                        }
                    }
                    
                    musicDao.insertSongs(songsWithPreservedLikes)
                    Log.d(TAG, "Cached ${filteredSongs.size} recommended songs in database with preserved like status")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error caching recommended songs", e)
            }
            
            emit(filteredSongs)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching recommendations", e)
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
        }
    }

    override fun searchSongs(query: String): Flow<List<Song>> = flow {
        val localResults = musicDao.searchSongs(query).first()
        emit(localResults)
        
        try {
            val searchResults = ytMusicService.search(query)
            val songs = searchResults.map { it.toSong() }
            
            // Apply duration filter to exclude songs longer than 15 minutes
            val filteredSongs = filterSongsByDuration(songs)
            
            val currentLikedSongs = musicDao.getLikedSongs().first()
            val likedSongIds = currentLikedSongs.map { it.id }.toSet()
            
            val songsWithPreservedLikes = filteredSongs.map { song ->
                if (likedSongIds.contains(song.id)) {
                    song.copy(isLiked = true)
                } else {
                    song
                }
            }
            
            emit(songsWithPreservedLikes)
            addToRecentSearches(query)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching songs", e)
        }
    }
    
    override fun getTrendingSongs(): Flow<List<Song>> = flow {
        try {
            val trendingResults = ytMusicService.getTrending()
            val songs = trendingResults.map { it.toSong() }
            
            // Apply duration filter to exclude songs longer than 15 minutes
            val filteredSongs = filterSongsByDuration(songs)
            
            emit(filteredSongs)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trending songs", e)
            emit(emptyList())
        }
    }

    override fun getLikedSongs(): Flow<List<Song>> = flow {
        val currentUser = firebaseAuth.currentUser
        
        Log.d(TAG, "🔄 getLikedSongs() called - User: ${currentUser?.uid ?: "null"}")
        
        // Get local liked songs first
        val localLikedSongs = musicDao.getLikedSongs().first()
        Log.d(TAG, "📋 Found ${localLikedSongs.size} local liked songs")
        Log.d(TAG, "📋 Local liked songs: ${localLikedSongs.map { it.title }}")
        
        // Emit local songs immediately
        emit(localLikedSongs)
        
        if (currentUser == null) {
            Log.d(TAG, "ℹ️ No user authenticated, using local liked songs only")
            // If not authenticated, just use local database as source of truth
            musicDao.getLikedSongs().collect { songs ->
                Log.d(TAG, "🔄 Local update: ${songs.size} liked songs")
                emit(songs)
            }
            return@flow
        }
        
        // Sync with Firebase for authenticated users using repository scope
        repositoryScope.launch {
            try {
                Log.d(TAG, "🔄 Syncing liked songs with Firebase")
                syncLikedSongsFromFirebase(currentUser.uid)
                Log.d(TAG, "✅ Firebase sync completed for liked songs")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error syncing liked songs with Firebase", e)
            }
        }
        
        // Continue listening to local database changes
        musicDao.getLikedSongs().collect { songs ->
            Log.d(TAG, "🔄 Local database update: ${songs.size} liked songs")
            emit(songs)
        }
    }.distinctUntilChanged()

    override fun getDownloadedSongs(): Flow<List<Song>> = flow {
        Log.d(TAG, "🔄 getDownloadedSongs() called")
        
        // Get local downloaded songs first
        val localDownloadedSongs = musicDao.getDownloadedSongs().first()
        Log.d(TAG, "📋 Found ${localDownloadedSongs.size} local downloaded songs")
        
        // Emit local songs immediately
        emit(localDownloadedSongs)
        
        // Verify and restore downloaded songs if needed using repository scope
        repositoryScope.launch {
            try {
                verifyAndRestoreDownloadedSongs()
                Log.d(TAG, "✅ Downloaded songs verification completed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error verifying downloaded songs", e)
            }
        }
        
        // Continue listening to local database changes
        musicDao.getDownloadedSongs().collect { songs ->
            Log.d(TAG, "🔄 Local database update: ${songs.size} downloaded songs")
            emit(songs)
        }
    }.distinctUntilChanged()

    override suspend fun addSongToLiked(song: Song) {
        Log.d(TAG, "Adding song to liked: ${song.title} (${song.id})")
        
        // Update local database first (immediate UI update)
        musicDao.updateSongLikedStatus(song.id, true)
        Log.d(TAG, "✅ Updated local database for song: ${song.title}")
        
        // Firebase operations in background (non-blocking)
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            repositoryScope.launch {
                try {
                    // Use merge to avoid checking if document exists
                    val songData = mapOf(
                        "id" to song.id,
                        "title" to song.title,
                        "artist" to song.artist,
                        "album" to song.album,
                        "albumArt" to song.albumArt,
                        "albumArtUrl" to song.albumArtUrl,
                        "duration" to song.duration,
                        "audioUrl" to song.audioUrl,
                        "isRecommended" to song.isRecommended,
                        "isLiked" to true,
                        "isDownloaded" to song.isDownloaded,
                        "localFilePath" to song.localFilePath
                    )
                    
                    firestore.collection("users").document(currentUser.uid)
                        .collection("songs")
                        .document(song.id)
                        .set(songData, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                    Log.d(TAG, "✅ Synced liked song to Firebase: ${song.title}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error syncing liked song to Firebase", e)
                }
            }
        } else {
            Log.d(TAG, "ℹ️ No user authenticated, skipping Firebase sync")
        }
    }

    override suspend fun removeSongFromLiked(songId: String) {
        Log.d(TAG, "Removing song from liked: $songId")
        
        // Update local database first (immediate UI update)
        musicDao.updateSongLikedStatus(songId, false)
        Log.d(TAG, "✅ Updated local database for song: $songId")
        
        // Firebase operations in background (non-blocking)
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            repositoryScope.launch {
                try {
                    firestore.collection("users").document(currentUser.uid)
                        .collection("songs")
                        .document(songId)
                        .update("isLiked", false)
                        .await()
                    Log.d(TAG, "✅ Synced unliked song to Firebase: $songId")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error syncing unliked song to Firebase", e)
                }
            }
        } else {
            Log.d(TAG, "ℹ️ No user authenticated, skipping Firebase sync")
        }
    }

    override suspend fun createPlaylist(name: String) {
        val currentUser = firebaseAuth.currentUser
        
        // Check if playlist with this name already exists
        val existingPlaylist = musicDao.getPlaylists().first().find { it.name == name }
        if (existingPlaylist != null) {
            Log.d(TAG, "ℹ️ Playlist '$name' already exists with ID: ${existingPlaylist.id}")
            return
        }
        
        val playlistId = "playlist_${System.currentTimeMillis()}"
        
        Log.d(TAG, "Creating playlist: $name (ID: $playlistId)")
        
        // INSTANT: Create in local database first
        val playlist = Playlist(playlistId, name)
        musicDao.createPlaylist(playlist)
        Log.d(TAG, "✅ Created playlist locally: $name")
        
        // BACKGROUND: Sync to Firebase if authenticated
        if (currentUser != null) {
            repositoryScope.launch {
                try {
                    val playlistData = mapOf(
                        "id" to playlistId,
                        "name" to name,
                        "createdAt" to System.currentTimeMillis()
                    )
                    
                    firestore.collection("users").document(currentUser.uid)
                        .collection("playlists")
                        .document(playlistId)
                        .set(playlistData)
                        .await()
                    Log.d(TAG, "✅ Synced playlist creation to Firebase: $name")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error syncing playlist creation to Firebase", e)
                }
            }
        } else {
            Log.d(TAG, "ℹ️ No user authenticated, skipping Firebase sync")
        }
    }

    override suspend fun deletePlaylist(playlistId: String) {
        val currentUser = firebaseAuth.currentUser
        
        Log.d(TAG, "Deleting playlist: $playlistId")
        
        // Get playlist details for logging
        val playlist = musicDao.getPlaylists().first().find { it.id == playlistId }
        val playlistName = playlist?.name ?: "Unknown"
        
        // INSTANT: Delete from local database first
        playlist?.let { musicDao.deletePlaylist(it) }
        Log.d(TAG, "✅ Deleted playlist locally: $playlistName")
        
        // BACKGROUND: Delete from Firebase if authenticated
        if (currentUser != null) {
            repositoryScope.launch {
                try {
                    firestore.collection("users").document(currentUser.uid)
                        .collection("playlists")
                        .document(playlistId)
                        .delete()
                        .await()
                    Log.d(TAG, "✅ Synced playlist deletion to Firebase: $playlistName")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error syncing playlist deletion to Firebase", e)
                }
            }
        } else {
            Log.d(TAG, "ℹ️ No user authenticated, skipping Firebase sync")
        }
    }

    override suspend fun renamePlaylist(playlistId: String, newName: String) {
        val currentUser = firebaseAuth.currentUser
        
        Log.d(TAG, "Renaming playlist: $playlistId to '$newName'")
        
        // INSTANT: Rename in local database first
        musicDao.renamePlaylist(playlistId, newName)
        Log.d(TAG, "✅ Renamed playlist locally: $playlistId -> '$newName'")
        
        // BACKGROUND: Sync to Firebase if authenticated
        if (currentUser != null) {
            repositoryScope.launch {
                try {
                    firestore.collection("users").document(currentUser.uid)
                        .collection("playlists")
                        .document(playlistId)
                        .update("name", newName)
                        .await()
                    Log.d(TAG, "✅ Synced playlist rename to Firebase: $playlistId -> '$newName'")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error syncing playlist rename to Firebase", e)
                }
            }
        } else {
            Log.d(TAG, "ℹ️ No user authenticated, skipping Firebase sync")
        }
    }

    override suspend fun addSongToPlaylist(song: Song, playlistId: String) {
        val currentUser = firebaseAuth.currentUser
        
        Log.d(TAG, "=== ADD SONG TO PLAYLIST START ===")
        Log.d(TAG, "Adding song: ${song.title} (${song.id}) to playlist: $playlistId")
        
        // First check if song exists in database
        val existingSong = musicDao.getSongById(song.id)
        if (existingSong == null) {
            musicDao.insertSong(song)
            Log.d(TAG, "✅ Inserted new song into database: ${song.title}")
        } else {
            Log.d(TAG, "✅ Song already exists in database: ${song.title}")
        }
        
        // Check if song is already in playlist
        val isAlreadyInPlaylist = musicDao.isSongInPlaylist(song.id, playlistId)
        if (isAlreadyInPlaylist) {
            Log.d(TAG, "ℹ️ Song ${song.title} is already in playlist $playlistId")
            return
        }
        
        // INSTANT: Add to local database first (UI updates immediately)
        try {
        musicDao.insertSongPlaylistCrossRef(
            SongPlaylistCrossRef(
                songId = song.id,
                playlistId = playlistId
            )
        )
            Log.d(TAG, "✅ Added song to playlist locally: ${song.title} -> $playlistId")
            
            // Force immediate emission of updated data
            val updatedPlaylistSongs = musicDao.getPlaylistSongs(playlistId).first()
            Log.d(TAG, "✅ Immediate update: Playlist $playlistId now has ${updatedPlaylistSongs.size} songs")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding song to playlist locally", e)
            throw e
        }
        
        // Verify the addition
        val playlistSongs = musicDao.getPlaylistSongs(playlistId).first()
        val songCount = playlistSongs.size
        Log.d(TAG, "✅ Verification: Playlist $playlistId now has $songCount songs")
        Log.d(TAG, "✅ Songs in playlist: ${playlistSongs.map { it.title }}")
        
        // Verify the song is actually in the playlist
        val isInPlaylist = musicDao.isSongInPlaylist(song.id, playlistId)
        if (!isInPlaylist) {
            Log.e(TAG, "❌ CRITICAL: Song ${song.title} was not added to playlist $playlistId")
            throw Exception("Failed to add song to playlist")
        }
        
        // BACKGROUND: Sync to Firestore if authenticated (doesn't block UI)
        if (currentUser != null) {
            repositoryScope.launch {
                try {
                    // Store song details in user-specific songs collection
                    // Use update instead of set to preserve existing fields like isLiked
                    val songData = mapOf(
                        "id" to song.id,
                        "title" to song.title,
                        "artist" to song.artist,
                        "album" to song.album,
                        "albumArt" to song.albumArt,
                        "albumArtUrl" to song.albumArtUrl,
                        "duration" to song.duration,
                        "audioUrl" to song.audioUrl,
                        "isRecommended" to song.isRecommended,
                        "isDownloaded" to song.isDownloaded,
                        "localFilePath" to song.localFilePath
                        // Note: isLiked is not included here to preserve existing value
                    )
                    
                    firestore.collection("users").document(currentUser.uid)
                        .collection("songs")
                        .document(song.id)
                        .set(songData, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                    
                    // Add song to playlist in Firestore collection structure
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
                    Log.d(TAG, "✅ Synced song addition to Firestore: ${song.title} -> $playlistId")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error syncing song addition to Firestore", e)
                    // Note: We don't rollback local changes - user sees instant feedback
                }
            }
        } else {
            Log.d(TAG, "ℹ️ No user authenticated, skipping Firebase sync")
        }
        
        Log.d(TAG, "=== ADD SONG TO PLAYLIST END ===")
    }

    override suspend fun removeSongFromPlaylist(songId: String, playlistId: String) {
        val currentUser = firebaseAuth.currentUser
        
        Log.d(TAG, "=== REMOVE SONG FROM PLAYLIST START ===")
        Log.d(TAG, "Removing song: $songId from playlist: $playlistId")
        
        // Check if song is actually in playlist
        val isInPlaylist = musicDao.isSongInPlaylist(songId, playlistId)
        if (!isInPlaylist) {
            Log.d(TAG, "ℹ️ Song $songId is not in playlist $playlistId")
            return
        }
        
        // INSTANT: Remove from local database first (UI updates immediately)
        try {
        musicDao.deleteSongPlaylistCrossRef(songId, playlistId)
            Log.d(TAG, "✅ Removed song from playlist locally: $songId -> $playlistId")
            
            // Force immediate emission of updated data
            val updatedPlaylistSongs = musicDao.getPlaylistSongs(playlistId).first()
            Log.d(TAG, "✅ Immediate update: Playlist $playlistId now has ${updatedPlaylistSongs.size} songs")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error removing song from playlist locally", e)
            throw e
        }
        
        // Verify the removal
        val playlistSongs = musicDao.getPlaylistSongs(playlistId).first()
        val songCount = playlistSongs.size
        Log.d(TAG, "✅ Verification: Playlist $playlistId now has $songCount songs")
        Log.d(TAG, "✅ Songs in playlist: ${playlistSongs.map { it.title }}")
        
        // Verify the song is actually removed from the playlist
        val isStillInPlaylist = musicDao.isSongInPlaylist(songId, playlistId)
        if (isStillInPlaylist) {
            Log.e(TAG, "❌ CRITICAL: Song $songId was not removed from playlist $playlistId")
            throw Exception("Failed to remove song from playlist")
        }
        
        // BACKGROUND: Remove from Firestore if authenticated (doesn't block UI)
        if (currentUser != null) {
            repositoryScope.launch {
                try {
                    firestore.collection("users").document(currentUser.uid)
                        .collection("playlists")
                        .document(playlistId)
                        .collection("songs")
                        .document(songId)
                        .delete()
                        .await()
                    Log.d(TAG, "✅ Synced song removal to Firestore: $songId -> $playlistId")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error syncing song removal to Firestore", e)
                    // Note: We don't rollback local changes - user sees instant feedback
                }
            }
        } else {
            Log.d(TAG, "ℹ️ No user authenticated, skipping Firebase sync")
        }
        
        Log.d(TAG, "=== REMOVE SONG FROM PLAYLIST END ===")
    }

    override suspend fun isSongInPlaylist(songId: String, playlistId: String): Boolean {
        return musicDao.isSongInPlaylist(songId, playlistId)
    }

    override fun getPlaylistSongCount(playlistId: String): Flow<Int> = flow {
        Log.d(TAG, "🔄 getPlaylistSongCount() called for playlist: $playlistId")
        
        // Get local count first
        val localCount = musicDao.getPlaylistSongCount(playlistId).first()
        Log.d(TAG, "📊 Local count for playlist $playlistId: $localCount")
        
        // Emit local count immediately
        emit(localCount)
        
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "ℹ️ No user authenticated, using local playlist count only")
            // If not authenticated, just use local database as source of truth
            musicDao.getPlaylistSongCount(playlistId).collect { count ->
                Log.d(TAG, "🔄 Local update: playlist $playlistId has $count songs")
                emit(count)
            }
            return@flow
        }
        
        // Only sync once on first call, not on every emission using repository scope
        repositoryScope.launch {
            try {
                Log.d(TAG, "🔄 Syncing playlist count with Firebase for playlist: $playlistId")
                syncPlaylistSongsFromFirebase(playlistId, currentUser)
                Log.d(TAG, "✅ Playlist count sync completed for playlist: $playlistId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error syncing playlist count with Firebase", e)
            }
        }
        
        // Continue listening to local database changes only
        musicDao.getPlaylistSongCount(playlistId).collect { count ->
            Log.d(TAG, "🔄 Local database update: playlist $playlistId has $count songs")
            emit(count)
        }
    }.distinctUntilChanged()

    override fun getPlaylistSongs(playlistId: String): Flow<List<Song>> = flow {
        Log.d(TAG, "🔄 getPlaylistSongs() called for playlist: $playlistId")
        
        // Get local songs first
        val localSongs = musicDao.getPlaylistSongs(playlistId).first()
        Log.d(TAG, "📋 Found ${localSongs.size} local songs in playlist $playlistId")
        
        // Emit local songs immediately
        emit(localSongs)
        
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.d(TAG, "ℹ️ No user authenticated, using local playlist songs only")
            // If not authenticated, just use local database as source of truth
            musicDao.getPlaylistSongs(playlistId).collect { songs ->
                Log.d(TAG, "🔄 Local update: ${songs.size} songs in playlist $playlistId")
                emit(songs)
            }
            return@flow
        }
        
        // Only sync once on first call, not on every emission using repository scope
        repositoryScope.launch {
            try {
                Log.d(TAG, "🔄 Syncing playlist songs with Firebase for playlist: $playlistId")
                syncPlaylistSongsFromFirebase(playlistId, currentUser)
                Log.d(TAG, "✅ Playlist songs sync completed for playlist: $playlistId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error syncing playlist songs with Firebase", e)
            }
        }
        
        // Continue listening to local database changes only
        musicDao.getPlaylistSongs(playlistId).collect { songs ->
            Log.d(TAG, "🔄 Local database update: ${songs.size} songs in playlist $playlistId")
            emit(songs)
        }
    }.distinctUntilChanged()

    override suspend fun getSongById(songId: String): Song? {
        return musicDao.getSongById(songId)
    }

    override suspend fun getSongsByIds(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val allSongs = musicDao.getAllSongs().first()
        return ids.mapNotNull { id -> allSongs.find { it.id == id } }
    }

    override fun getRecentlyPlayed(): Flow<List<Song>> = musicDao.getRecentlyPlayed()

    override suspend fun addToRecentlyPlayed(song: Song) {
        try {
            Log.d(TAG, "🔄 Adding song to recently played: ${song.title} (${song.id})")
            
            // First ensure song exists in database
            val existingSong = musicDao.getSongById(song.id)
            if (existingSong == null) {
                musicDao.insertSong(song)
                Log.d(TAG, "✅ Inserted song into database before adding to recently played: ${song.title}")
            } else {
                Log.d(TAG, "✅ Song already exists in database: ${song.title}")
            }
            
            // Now add to recently played
            musicDao.insertRecentlyPlayed(RecentlyPlayed(songId = song.id, timestamp = System.currentTimeMillis()))
            Log.d(TAG, "✅ Successfully added song to recently played: ${song.title}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding song to recently played: ${song.title}", e)
            // Don't rethrow - we don't want to break playback for this
        }
    }

    override suspend fun getRecentSearches(): List<String> {
        return dataStore.data.first()[recentSearchesKey]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    override suspend fun addToRecentSearches(query: String) {
        val currentSearches = getRecentSearches().toMutableList()
        currentSearches.remove(query)
        currentSearches.add(0, query)
        if (currentSearches.size > maxRecentSearches) {
            currentSearches.removeAt(currentSearches.size - 1)
        }
        dataStore.edit { it[recentSearchesKey] = currentSearches.joinToString(",") }
    }

    override suspend fun removeFromRecentSearches(query: String) {
        val currentSearches = getRecentSearches().toMutableList()
        currentSearches.remove(query)
        dataStore.edit { it[recentSearchesKey] = currentSearches.joinToString(",") }
    }

    override suspend fun markSongAsDownloaded(song: Song, localFilePath: String) {
        Log.d(TAG, "=== MARK SONG AS DOWNLOADED START ===")
        Log.d(TAG, "Marking song as downloaded: ${song.title} (${song.id})")
        
        // First ensure song exists in database
        val existingSong = musicDao.getSongById(song.id)
        if (existingSong == null) {
            musicDao.insertSong(song)
            Log.d(TAG, "✅ Inserted new song into database: ${song.title}")
        } else {
            Log.d(TAG, "✅ Song already exists in database: ${song.title}")
        }
        
        // INSTANT: Update local database (UI updates immediately)
            musicDao.updateSongDownloadStatus(song.id, true, localFilePath)
        Log.d(TAG, "✅ Marked song as downloaded locally: ${song.title}")
        
        // Save to DataStore for quick lookups
        addToDownloadsDataStore(song.id, localFilePath)
        
        // Save to SharedPreferences as backup
        saveDownloadStateToPreferences(song.id, true, localFilePath)
        
        // Verify the update immediately
        val updatedSong = musicDao.getSongById(song.id)
        if (updatedSong?.isDownloaded != true) {
            Log.e(TAG, "❌ CRITICAL: Download status not persisted for: ${song.title}")
            // Retry the update
            musicDao.updateSongDownloadStatus(song.id, true, localFilePath)
            Log.d(TAG, "🔄 Retried download status update for: ${song.title}")
        } else {
            Log.d(TAG, "✅ Verification: Song ${song.title} isDownloaded = ${updatedSong.isDownloaded}")
        }
        
        // No Firebase sync needed - downloads are device-specific
        Log.d(TAG, "ℹ️ Downloads are device-specific, no Firebase sync needed")
        
        Log.d(TAG, "=== MARK SONG AS DOWNLOADED END ===")
    }

    override suspend fun markSongAsNotDownloaded(songId: String) {
        Log.d(TAG, "=== MARK SONG AS NOT DOWNLOADED START ===")
        Log.d(TAG, "Marking song as not downloaded: $songId")
        
        // INSTANT: Update local database (UI updates immediately)
        musicDao.updateSongDownloadStatus(songId, false, null)
        Log.d(TAG, "✅ Marked song as not downloaded locally: $songId")
        
        // Remove from DataStore
        removeFromDownloadsDataStore(songId)
        
        // Save to SharedPreferences as backup
        saveDownloadStateToPreferences(songId, false, null)
        
        // No Firebase sync needed - downloads are device-specific
        Log.d(TAG, "ℹ️ Downloads are device-specific, no Firebase sync needed")
        
        Log.d(TAG, "=== MARK SONG AS NOT DOWNLOADED END ===")
    }

    override suspend fun isDownloaded(songId: String): Boolean {
        return musicDao.getSongById(songId)?.isDownloaded == true
    }

    override suspend fun syncUserDataWithFirebase() {
        // Implementation for syncing user data
    }

    override suspend fun refreshFirebaseListeners() {
        // Implementation for refreshing Firebase listeners
    }

    override suspend fun syncFromFirebase() {
        // Implementation for syncing from Firebase
    }

    override suspend fun criticalPrefetch(videoIds: List<String>) {
        try {
            if (videoIds.isEmpty()) return
            
            Log.d(TAG, "Triggering critical prefetch for ${videoIds.size} videos")
            val videoIdsString = videoIds.joinToString(",")
            val response = ytMusicService.criticalPrefetch(videoIdsString)
            Log.d(TAG, "Critical prefetch response: ${response.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in critical prefetch", e)
        }
    }

    override suspend fun cleanupGlobalSongsCollection() {
        // Implementation for cleaning up global songs collection
    }

    /**
     * Sync playlist songs from Firebase to the join table system
     */
    private suspend fun syncPlaylistSongsFromFirebase(playlistId: String, currentUser: com.google.firebase.auth.FirebaseUser) {
        try {
            Log.d(TAG, "🔄 Syncing playlist songs from Firebase for playlist $playlistId")
            
            val songsCollection = firestore.collection("users").document(currentUser.uid)
                .collection("playlists")
                .document(playlistId)
                .collection("songs")
            
            val remoteSongsSnapshot = songsCollection.get().await()
            val remoteSongIds: List<String> = remoteSongsSnapshot.documents.map { it.id }.filterNotNull()
            
            Log.d(TAG, "📋 Found ${remoteSongIds.size} remote songs in playlist $playlistId")
            
            // Get current local songs in this playlist
            val localSongs = musicDao.getPlaylistSongs(playlistId).first()
            val localSongIds = localSongs.map { it.id }.toSet()
            val remoteSongIdsSet = remoteSongIds.toSet()
            
            // Songs to add (in remote but not in local)
            val songsToAdd = remoteSongIdsSet - localSongIds
            // Songs to remove (in local but not in remote)
            val songsToRemove = localSongIds - remoteSongIdsSet
            
            Log.d(TAG, "🔄 Playlist songs sync - To add: $songsToAdd, To remove: $songsToRemove")
            
            // Add new songs to the join table system
            for (songId in songsToAdd) {
                try {
                    val song = getSongFromFirebase(songId, currentUser)
                    if (song != null) {
                        // Ensure song exists in database
                        val existingSong = musicDao.getSongById(songId)
                        if (existingSong == null) {
                            musicDao.insertSong(song)
                            Log.d(TAG, "✅ Inserted song from Firebase: ${song.title}")
                        }
                        
                        // Add to join table system
                        musicDao.insertSongPlaylistCrossRef(
                            SongPlaylistCrossRef(
                                songId = song.id,
                                playlistId = playlistId
                            )
                        )
                        
                        Log.d(TAG, "✅ Added song ${song.title} to playlist $playlistId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding song $songId to playlist $playlistId", e)
                }
            }
            
            // Remove songs from the join table system
            for (songId in songsToRemove) {
                try {
                    // Remove from join table system
                    musicDao.deleteSongPlaylistCrossRef(songId, playlistId)
                    
                    Log.d(TAG, "✅ Removed song $songId from playlist $playlistId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing song $songId from playlist $playlistId", e)
                }
            }
            
            Log.d(TAG, "✅ Playlist songs sync completed for playlist $playlistId")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing playlist songs from Firebase for playlist $playlistId", e)
        }
    }

    /**
     * Get song details from Firebase
     */
    private suspend fun getSongFromFirebase(songId: String, currentUser: com.google.firebase.auth.FirebaseUser): Song? {
        return try {
            val songDoc = firestore.collection("users").document(currentUser.uid)
                .collection("songs")
                .document(songId)
                .get()
                .await()
            
            if (songDoc.exists()) {
                val song = songDoc.toObject(Song::class.java)
                song?.let {
                    // Create a new song object with the correct ID
                    val songWithId = it.copy(id = songId)
                    Log.d(TAG, "✅ Retrieved song from Firebase: ${songWithId.title}")
                    return songWithId
                }
                null
            } else {
                Log.w(TAG, "⚠️ Song document does not exist in Firebase: $songId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting song from Firebase: $songId", e)
            null
        }
    }

    /**
     * Update local playlists from Firebase snapshot
     */
    private suspend fun updateLocalPlaylistsFromRemote(snapshot: com.google.firebase.firestore.QuerySnapshot) {
        try {
            Log.d(TAG, "🔄 Syncing playlists from Firebase snapshot")
            
            val currentUser = firebaseAuth.currentUser
            if (currentUser == null) return
            
            val remotePlaylists = snapshot.documents.mapNotNull { doc ->
                try {
                    val id = doc.id
                    val name = doc.getString("name") ?: return@mapNotNull null
                    Playlist(id, name)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing playlist document: ${doc.id}", e)
                    null
                }
            }
            
            Log.d(TAG, "📋 Found ${remotePlaylists.size} remote playlists")
            
            // Get current local playlists
            val localPlaylists = musicDao.getPlaylists().first()
            val localPlaylistIds = localPlaylists.map { it.id }.toSet()
            val remotePlaylistIds = remotePlaylists.map { it.id }.toSet()
            
            // Playlists to add (in remote but not in local)
            val playlistsToAdd = remotePlaylists.filter { it.id !in localPlaylistIds }
            // Playlists to remove (in local but not in remote)
            val playlistsToRemove = localPlaylists.filter { it.id !in remotePlaylistIds }
            
            Log.d(TAG, "🔄 Playlist sync - To add: ${playlistsToAdd.size}, To remove: ${playlistsToRemove.size}")
            
            // Add new playlists
            for (playlist in playlistsToAdd) {
                musicDao.createPlaylist(playlist)
                Log.d(TAG, "✅ Added playlist: ${playlist.name}")
                
                // Sync songs for this playlist (but don't trigger another sync)
                try {
                    syncPlaylistSongsFromFirebase(playlist.id, currentUser)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error syncing songs for playlist ${playlist.name}", e)
                }
            }
            
            // Remove deleted playlists
            for (playlist in playlistsToRemove) {
                musicDao.deletePlaylist(playlist)
                Log.d(TAG, "✅ Removed playlist: ${playlist.name}")
            }
            
            // Update existing playlists (name changes, etc.)
            for (remotePlaylist in remotePlaylists) {
                val localPlaylist = localPlaylists.find { it.id == remotePlaylist.id }
                if (localPlaylist != null && localPlaylist.name != remotePlaylist.name) {
                    musicDao.renamePlaylist(remotePlaylist.id, remotePlaylist.name)
                    Log.d(TAG, "✅ Updated playlist name: ${localPlaylist.name} -> ${remotePlaylist.name}")
                }
            }
            
            Log.d(TAG, "✅ Playlist sync completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing playlists from Firebase", e)
        }
    }

    /**
     * Force refresh downloaded songs from Firebase
     */
    suspend fun forceRefreshDownloadedSongs() {
        Log.d(TAG, "🔄 Force refreshing downloaded songs from local database")
        // No Firebase sync needed - downloads are device-specific
        val localDownloadedSongs = musicDao.getDownloadedSongs().first()
        Log.d(TAG, "✅ Local downloaded songs: ${localDownloadedSongs.size} songs")
        Log.d(TAG, "✅ Downloaded songs: ${localDownloadedSongs.map { it.title }}")
        
        // Also verify persistence
        verifyDownloadedSongsPersistence()
    }

    /**
     * Sync downloaded songs on app startup
     */
    suspend fun syncDownloadedSongsOnStartup() {
        Log.d(TAG, "🔄 Syncing downloaded songs on app startup from local database")
        
        try {
            // First, verify and restore any lost downloaded songs
            verifyAndRestoreDownloadedSongs()
            
            // Then scan download integrity
            scanDownloadIntegrity()
            
            // Then get the final state
            val localDownloadedSongs = musicDao.getDownloadedSongs().first()
            Log.d(TAG, "✅ Local downloaded songs on startup: ${localDownloadedSongs.size} songs")
            Log.d(TAG, "✅ Downloaded songs: ${localDownloadedSongs.map { it.title }}")
            
            // Additional verification: ensure all downloaded songs have valid file paths
            for (song in localDownloadedSongs) {
                if (song.localFilePath.isNullOrEmpty()) {
                    Log.w(TAG, "⚠️ Downloaded song has no file path: ${song.title}")
                } else {
                    Log.d(TAG, "✅ Downloaded song verified: ${song.title} -> ${song.localFilePath}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing downloaded songs on startup", e)
        }
    }
    
    /**
     * Restore downloaded songs if they get lost
     */
    suspend fun restoreDownloadedSongs() {
        try {
            Log.d(TAG, "🔄 Restoring downloaded songs...")
            
            // Get all songs in the database
            val allSongs = musicDao.getAllSongs().first()
            Log.d(TAG, "📋 Found ${allSongs.size} total songs in database")
            
            // Check for songs that should be downloaded but aren't marked as such
            var restoredCount = 0
            for (song in allSongs) {
                val localFilePath = song.localFilePath
                if (localFilePath != null && !song.isDownloaded) {
                    Log.d(TAG, "🔄 Restoring download status for: ${song.title}")
                    musicDao.updateSongDownloadStatus(song.id, true, localFilePath)
                    restoredCount++
                }
            }
            
            if (restoredCount > 0) {
                Log.d(TAG, "✅ Restored download status for $restoredCount songs")
            } else {
                Log.d(TAG, "✅ No songs needed restoration")
            }
            
            // Final verification
            val finalDownloadedSongs = musicDao.getDownloadedSongs().first()
            Log.d(TAG, "✅ Final state: ${finalDownloadedSongs.size} downloaded songs")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error restoring downloaded songs", e)
        }
    }
    
    /**
     * Verify that downloaded songs are properly persisted and not lost
     */
    private suspend fun verifyDownloadedSongsPersistence() {
        try {
            Log.d(TAG, "🔍 Verifying downloaded songs persistence...")
            
            // Get all songs that should be downloaded
            val downloadedSongs = musicDao.getDownloadedSongs().first()
            Log.d(TAG, "📋 Found ${downloadedSongs.size} downloaded songs in database")
            
            // Check each downloaded song for proper persistence
            for (song in downloadedSongs) {
                val songFromDb = musicDao.getSongById(song.id)
                if (songFromDb == null) {
                    Log.e(TAG, "❌ CRITICAL: Downloaded song not found in database: ${song.title}")
                } else if (!songFromDb.isDownloaded) {
                    Log.e(TAG, "❌ CRITICAL: Song marked as downloaded but isDownloaded = false: ${song.title}")
                    // Restore the download status
                    val localFilePath = song.localFilePath
                    musicDao.updateSongDownloadStatus(song.id, true, localFilePath)
                    Log.d(TAG, "✅ Restored download status for: ${song.title}")
                } else {
                    Log.d(TAG, "✅ Downloaded song properly persisted: ${song.title}")
                }
            }
            
            // Final verification
            val finalDownloadedSongs = musicDao.getDownloadedSongs().first()
            Log.d(TAG, "✅ Final verification: ${finalDownloadedSongs.size} downloaded songs properly persisted")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error verifying downloaded songs persistence", e)
        }
    }

    /**
     * Sync liked songs from Firebase to the local database
     */
    private suspend fun syncLikedSongsFromFirebase(userId: String) {
        try {
            Log.d(TAG, "🔄 Syncing liked songs from Firebase for user $userId")
            
            val songsCollection = firestore.collection("users").document(userId).collection("songs")
            val remoteSongsSnapshot = songsCollection.get().await()
            
            // Get all songs from Firebase and check their liked status
            val remoteLikedSongIds: MutableList<String> = mutableListOf()
            val remoteUnlikedSongIds: MutableList<String> = mutableListOf()
            
            for (doc in remoteSongsSnapshot.documents) {
                val songId = doc.id
                val isLiked = doc.getBoolean("isLiked") ?: false
                val title = doc.getString("title") ?: "Unknown"
                
                Log.d(TAG, "📋 Firebase song: $songId ($title), isLiked: $isLiked")
                
                if (isLiked) {
                    remoteLikedSongIds.add(songId)
                    Log.d(TAG, "✅ Added to remote liked list: $title")
                } else {
                    remoteUnlikedSongIds.add(songId)
                    Log.d(TAG, "❌ Added to remote unliked list: $title")
                }
            }
            
            Log.d(TAG, "📋 Found ${remoteLikedSongIds.size} remote liked songs and ${remoteUnlikedSongIds.size} unliked songs for user $userId")
            
            // Get current local liked songs
            val localLikedSongs = musicDao.getLikedSongs().first()
            val localLikedSongIds = localLikedSongs.map { it.id }.toSet()
            val remoteLikedSongIdsSet = remoteLikedSongIds.toSet()
            
            // Songs to add (in remote liked but not in local liked)
            val songsToAdd = remoteLikedSongIdsSet - localLikedSongIds
            // Songs to remove (in local liked but not in remote liked)
            val songsToRemove = localLikedSongIds - remoteLikedSongIdsSet
            
            Log.d(TAG, "🔄 Liked songs sync - To add: $songsToAdd, To remove: $songsToRemove")
            
            // Add new liked songs to the local database
            for (songId in songsToAdd) {
                try {
                    // Get the song document directly and create the song object manually
                    val songDoc = firestore.collection("users").document(userId)
                        .collection("songs")
                        .document(songId)
                        .get()
                        .await()
                    
                    if (songDoc.exists()) {
                        val songData = songDoc.data
                        if (songData != null) {
                            val rawIsLiked = songData["isLiked"]
                            Log.d(TAG, "🔍 Raw isLiked value from Firebase for $songId: $rawIsLiked (type: ${rawIsLiked?.javaClass?.simpleName})")
                            
                            val song = Song(
                                id = songId,
                                title = songData["title"] as? String ?: "",
                                artist = songData["artist"] as? String ?: "",
                                album = songData["album"] as? String ?: "",
                                albumArt = songData["albumArt"] as? String ?: "",
                                albumArtUrl = songData["albumArtUrl"] as? String,
                                duration = (songData["duration"] as? Number)?.toLong() ?: 0L,
                                audioUrl = songData["audioUrl"] as? String,
                                isRecommended = songData["isRecommended"] as? Boolean ?: false,
                                isLiked = songData["isLiked"] as? Boolean ?: false,
                                isDownloaded = songData["isDownloaded"] as? Boolean ?: false,
                                localFilePath = songData["localFilePath"] as? String
                            )
                            
                            Log.d(TAG, "🔍 Created song object for $songId: title='${song.title}', isLiked=${song.isLiked}")
                            
                            // Ensure the song is marked as liked since it's in the liked list
                            musicDao.insertSong(song.copy(isLiked = true))
                            Log.d(TAG, "✅ Inserted liked song from Firebase: ${song.title}")
                        } else {
                            Log.w(TAG, "⚠️ Song document exists but has no data: $songId")
                        }
                    } else {
                        Log.w(TAG, "⚠️ Song document does not exist in Firebase: $songId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding liked song $songId to local database", e)
                }
            }
            
            // Remove songs that are no longer liked in remote
            for (songId in songsToRemove) {
                try {
                    musicDao.updateSongLikedStatus(songId, false)
                    Log.d(TAG, "✅ Removed liked song $songId from local database")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing liked song $songId from local database", e)
                }
            }
            
            // Also handle songs that are explicitly marked as unliked in remote
            for (songId in remoteUnlikedSongIds) {
                try {
                    // Only update if the song is currently marked as liked locally
                    val localSong = musicDao.getSongById(songId)
                    if (localSong?.isLiked == true) {
                        musicDao.updateSongLikedStatus(songId, false)
                        Log.d(TAG, "✅ Updated song $songId to unliked based on remote status")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating song $songId to unliked", e)
                }
            }
            
            Log.d(TAG, "✅ Liked songs sync completed for user $userId")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing liked songs from Firebase for user $userId", e)
        }
    }

    /**
     * Sync liked songs on app startup
     */
    suspend fun syncLikedSongsOnStartup() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "🔄 Syncing liked songs on app startup")
            try {
                syncLikedSongsFromFirebase(currentUser.uid)
                Log.d(TAG, "✅ Liked songs synced on startup")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error syncing liked songs on startup", e)
            }
        } else {
            Log.d(TAG, "ℹ️ No user authenticated, skipping liked songs startup sync")
        }
    }

    /**
     * Sync playlists on app startup
     */
    suspend fun syncPlaylistsOnStartup() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "🔄 Syncing playlists on app startup")
            try {
                // Sync playlists from Firebase
                val playlistsCollection = firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("playlists")
                
                val remotePlaylistsSnapshot = playlistsCollection.get().await()
                updateLocalPlaylistsFromRemote(remotePlaylistsSnapshot)
                
                // Sync songs for each playlist
                val localPlaylists = musicDao.getPlaylists().first()
                for (playlist in localPlaylists) {
                    syncPlaylistSongsFromFirebase(playlist.id, currentUser)
                }
                
                // Verify playlist integrity
                verifyPlaylistIntegrity()
                
                Log.d(TAG, "✅ Playlists synced on startup: ${localPlaylists.size} playlists")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error syncing playlists on startup", e)
            }
        } else {
            Log.d(TAG, "ℹ️ No user authenticated, skipping playlists startup sync")
        }
    }
    
    /**
     * Verify playlist integrity and restore any lost playlist songs
     */
    private suspend fun verifyPlaylistIntegrity() {
        try {
            Log.d(TAG, "🔍 Verifying playlist integrity...")
            
            val allPlaylists = musicDao.getPlaylists().first()
            Log.d(TAG, "📋 Found ${allPlaylists.size} playlists to verify")
            
            for (playlist in allPlaylists) {
                val playlistSongs = musicDao.getPlaylistSongs(playlist.id).first()
                Log.d(TAG, "🎵 Playlist '${playlist.name}' (${playlist.id}) has ${playlistSongs.size} songs")
                
                // Check for any songs that should be in the playlist but aren't
                for (song in playlistSongs) {
                    val isInPlaylist = musicDao.isSongInPlaylist(song.id, playlist.id)
                    if (!isInPlaylist) {
                        Log.w(TAG, "⚠️ Song ${song.title} should be in playlist ${playlist.name} but isn't")
                        // Restore the relationship
                        musicDao.insertSongPlaylistCrossRef(
                            SongPlaylistCrossRef(
                                songId = song.id,
                                playlistId = playlist.id
                            )
                        )
                        Log.d(TAG, "✅ Restored song ${song.title} to playlist ${playlist.name}")
                    }
                }
            }
            
            Log.d(TAG, "✅ Playlist integrity verification completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error verifying playlist integrity", e)
        }
    }

    /**
     * Verify and restore downloaded songs if they get lost
     */
    private suspend fun verifyAndRestoreDownloadedSongs() {
        try {
            Log.d(TAG, "🔍 Verifying and restoring downloaded songs...")
            
            // Get all songs in the database
            val allSongs = musicDao.getAllSongs().first()
            Log.d(TAG, "📋 Found ${allSongs.size} total songs in database")
            
            // Get DataStore download IDs
            val dataStoreDownloads = getDownloadedIdsFromDataStore().first()
            Log.d(TAG, "📋 Found ${dataStoreDownloads.size} downloads in DataStore")
            
            // Check for songs that should be downloaded but aren't marked as such
            var restoredCount = 0
            for (song in allSongs) {
                val localFilePath = song.localFilePath
                if (localFilePath != null && !song.isDownloaded) {
                    Log.d(TAG, "🔄 Restoring download status for: ${song.title}")
                    musicDao.updateSongDownloadStatus(song.id, true, localFilePath)
                    addToDownloadsDataStore(song.id, localFilePath)
                    restoredCount++
                }
            }
            
            // Also check SharedPreferences for any missing download states
            val sharedPreferences = context.getSharedPreferences("download_states", Context.MODE_PRIVATE)
            val allKeys = sharedPreferences.all.keys.filter { !it.endsWith("_path") }
            
            for (songId in allKeys) {
                val (isDownloaded, localFilePath) = loadDownloadStateFromPreferences(songId)
                if (isDownloaded) {
        val song = musicDao.getSongById(songId)
                    if (song != null && !song.isDownloaded) {
                        Log.d(TAG, "🔄 Restoring download status from SharedPreferences for: ${song.title}")
                        musicDao.updateSongDownloadStatus(songId, true, localFilePath)
                        addToDownloadsDataStore(songId, localFilePath ?: "")
                        restoredCount++
                    }
                }
            }
            
            // Check DataStore downloads that aren't in database
            for (songId in dataStoreDownloads) {
                val song = musicDao.getSongById(songId)
                if (song == null) {
                    Log.w(TAG, "⚠️ Song in DataStore but not in database: $songId")
                    removeFromDownloadsDataStore(songId)
                } else if (!song.isDownloaded) {
                    Log.d(TAG, "🔄 Restoring download status from DataStore for: ${song.title}")
                    // Try to get file path from DataStore
                    val paths = dataStore.data.first()[downloadPathsKey] ?: ""
                    val pathEntry = paths.split(",").find { it.startsWith("$songId:") }
                    val localFilePath = song.localFilePath
                    val filePath = pathEntry?.substringAfter(":") ?: localFilePath
                    
                    musicDao.updateSongDownloadStatus(songId, true, filePath)
                    restoredCount++
                }
            }
            
            if (restoredCount > 0) {
                Log.d(TAG, "✅ Restored download status for $restoredCount songs")
            } else {
                Log.d(TAG, "✅ No songs needed restoration")
            }
            
            // Also check for songs marked as downloaded but have no local file path
            var cleanedCount = 0
            for (song in allSongs) {
                if (song.isDownloaded && song.localFilePath == null) {
                    Log.d(TAG, "🔄 Cleaning invalid download status for: ${song.title}")
                    musicDao.updateSongDownloadStatus(song.id, false, null)
                    removeFromDownloadsDataStore(song.id)
                    saveDownloadStateToPreferences(song.id, false, null)
                    cleanedCount++
                }
            }
            
            if (cleanedCount > 0) {
                Log.d(TAG, "✅ Cleaned invalid download status for $cleanedCount songs")
            }
            
            // Final verification
            val finalDownloadedSongs = musicDao.getDownloadedSongs().first()
            val finalDataStoreDownloads = getDownloadedIdsFromDataStore().first()
            Log.d(TAG, "✅ Final state: ${finalDownloadedSongs.size} downloaded songs in DB, ${finalDataStoreDownloads.size} in DataStore")
            Log.d(TAG, "✅ Downloaded songs: ${finalDownloadedSongs.map { it.title }}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error verifying and restoring downloaded songs", e)
        }
    }

    /**
     * Save download state to SharedPreferences
     */
    private suspend fun saveDownloadStateToPreferences(songId: String, isDownloaded: Boolean, localFilePath: String?) {
        val sharedPreferences = context.getSharedPreferences("download_states", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean(songId, isDownloaded)
        localFilePath?.let { editor.putString(songId + "_path", it) }
        editor.apply()
        Log.d(TAG, "Saved download state for $songId to SharedPreferences: isDownloaded=$isDownloaded, path=$localFilePath")
    }

    /**
     * Load download state from SharedPreferences
     */
    private suspend fun loadDownloadStateFromPreferences(songId: String): Pair<Boolean, String?> {
        val sharedPreferences = context.getSharedPreferences("download_states", Context.MODE_PRIVATE)
        val isDownloaded = sharedPreferences.getBoolean(songId, false)
        val localFilePath = sharedPreferences.getString(songId + "_path", null)
        Log.d(TAG, "Loaded download state for $songId from SharedPreferences: isDownloaded=$isDownloaded, path=$localFilePath")
        return Pair(isDownloaded, localFilePath)
    }
    
    /**
     * Add song to downloads in DataStore for quick lookups
     */
    private suspend fun addToDownloadsDataStore(songId: String, localFilePath: String) {
        dataStore.edit { preferences ->
            val currentDownloads = preferences[downloadsSetKey]?.toMutableSet() ?: mutableSetOf()
            currentDownloads.add(songId)
            preferences[downloadsSetKey] = currentDownloads
            
            // Store file path mapping
            val currentPaths = preferences[downloadPathsKey] ?: ""
            val newPaths = if (currentPaths.isEmpty()) "$songId:$localFilePath" else "$currentPaths,$songId:$localFilePath"
            preferences[downloadPathsKey] = newPaths
        }
        Log.d(TAG, "Added $songId to DataStore downloads")
    }
    
    /**
     * Remove song from downloads in DataStore
     */
    private suspend fun removeFromDownloadsDataStore(songId: String) {
        dataStore.edit { preferences ->
            val currentDownloads = preferences[downloadsSetKey]?.toMutableSet() ?: mutableSetOf()
            currentDownloads.remove(songId)
            preferences[downloadsSetKey] = currentDownloads
            
            // Remove from paths mapping
            val currentPaths = preferences[downloadPathsKey] ?: ""
            val newPaths = currentPaths.split(",")
                .filter { !it.startsWith("$songId:") }
                .joinToString(",")
            preferences[downloadPathsKey] = newPaths
        }
        Log.d(TAG, "Removed $songId from DataStore downloads")
    }
    
    /**
     * Get downloaded song IDs from DataStore for quick lookups
     */
    fun getDownloadedIdsFromDataStore(): Flow<Set<String>> {
        return dataStore.data.map { preferences ->
            preferences[downloadsSetKey] ?: emptySet()
        }
    }
    
    /**
     * Get download count from DataStore for immediate UI updates
     */
    fun getDownloadCountFromDataStore(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[downloadsSetKey]?.size ?: 0
        }
    }

    /**
     * Scan download folder and verify file integrity
     */
    suspend fun scanDownloadIntegrity() {
        try {
            Log.d(TAG, "🔍 Scanning download integrity...")
            
            val downloadedSongs = musicDao.getDownloadedSongs().first()
            Log.d(TAG, "📋 Found ${downloadedSongs.size} downloaded songs to verify")
            
            var validCount = 0
            var invalidCount = 0
            
            for (song in downloadedSongs) {
                val localFilePath = song.localFilePath
                if (localFilePath != null) {
                    val file = File(localFilePath)
                    if (file.exists() && file.length() > 0) {
                        validCount++
                        Log.d(TAG, "✅ File exists and valid: ${song.title} -> ${file.length()} bytes")
                    } else {
                        invalidCount++
                        Log.w(TAG, "⚠️ File missing or empty: ${song.title} -> $localFilePath")
                        
                        // Mark as not downloaded
                        musicDao.updateSongDownloadStatus(song.id, false, null)
                        removeFromDownloadsDataStore(song.id)
                        saveDownloadStateToPreferences(song.id, false, null)
                        
                        Log.d(TAG, "🔄 Marked as not downloaded: ${song.title}")
                    }
                } else {
                    invalidCount++
                    Log.w(TAG, "⚠️ No file path for downloaded song: ${song.title}")
                    
                    // Mark as not downloaded
                    musicDao.updateSongDownloadStatus(song.id, false, null)
                    removeFromDownloadsDataStore(song.id)
                    saveDownloadStateToPreferences(song.id, false, null)
                    
                    Log.d(TAG, "🔄 Marked as not downloaded: ${song.title}")
                }
            }
            
            Log.d(TAG, "✅ Download integrity scan completed: $validCount valid, $invalidCount invalid")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scanning download integrity", e)
        }
    }

    companion object {
        private const val TAG = "MusicRepositoryImpl"
        private const val MAX_SONG_DURATION_MS = 15 * 60 * 1000L // 15 minutes in milliseconds
    }

    /**
     * Filter songs to only include those with duration below 15 minutes
     */
    private fun filterSongsByDuration(songs: List<Song>): List<Song> {
        val filteredSongs = songs.filter { song ->
            song.duration > 0 && song.duration <= MAX_SONG_DURATION_MS
        }
        
        val filteredCount = songs.size - filteredSongs.size
        if (filteredCount > 0) {
            Log.d(TAG, "🎵 Filtered out $filteredCount songs with duration > 15 minutes")
        }
        
        return filteredSongs
    }
} 