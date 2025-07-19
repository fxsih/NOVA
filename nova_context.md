# NOVA Music Player App Context

## Project Overview
NOVA is a modern Android music player app built with Jetpack Compose, following MVVM architecture and clean code principles. The app provides a rich user experience for managing and playing music. It includes a backend service that integrates with YouTube Music for search and streaming capabilities, with optimized audio URL caching and pre-fetching.

## Technical Stack
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room (Version 5)
- **Dependency Injection**: Hilt
- **Async Operations**: Kotlin Coroutines & Flow
- **Image Loading**: Coil
- **Media Playback**: ExoPlayer
- **Data Persistence**: DataStore Preferences
- **Navigation**: Navigation Compose
- **Backend**: FastAPI with Python
- **Music API**: YouTube Music API (ytmusicapi)
- **Video Download**: yt-dlp
- **Caching**: TTLCache for audio URLs

## Current Development State
- **Database Version**: 7
- **Active Branch**: main
- **Current Focus**: Session management and playback lifecycle stability
- **Last Implemented Features**: 
  - **Session and Playback Lifecycle Management**: Implemented robust NovaSessionManager singleton for centralized app lifecycle and playback state management
  - **Notification Click Navigation**: Clicking notification now navigates directly to full player screen with current song
  - **Seekbar Synchronization**: Fixed notification and full player seekbar synchronization issues after app kill/restart
  - **Service Lifecycle Improvements**: Enhanced service persistence and state restoration with proper cleanup
  - **App Kill Detection**: Proper detection and handling of app swipe-kill with clean state reset
  - **Media Session Reset**: Clean media session state reset to prevent stale notification conflicts
  - **State Flow Management**: Improved state flow reset and synchronization between service and UI
  - **Background/Foreground Handling**: Enhanced app lifecycle handling to maintain playback state across transitions
  - **Fixed file verification for downloaded songs with improved error handling
  - Enhanced download completion tracking with better state management
  - Improved queue management for Downloads playlist with proper ordering
  - Added visual indicators for download status in library and player screens
  - Fixed edge cases in offline playback with better file path handling
  - Enhanced search functionality with improved album and artist filtering
  - Added album and artist sections in search results with visual cards
  - Implemented artist image support using album art as fallback
  - Fixed issues with incorrect songs showing in album/artist filters
  - Added song count indicators for albums and artists
  - Improved search accuracy with exact and partial matching
  - Centered filter chips horizontally for better UI layout
  - Fixed bug where downloads wouldn't complete when navigating away from player screen
  - Fixed duplicate "Downloads" entries in library screen
  - Fixed incorrect download status indicators for songs
  - Improved queue and shuffle handling for the Downloads playlist
  - Enhanced file existence verification for downloaded songs
  - Added applicationScope in NovaApplication for background tasks
  - Updated PlayerViewModel to use applicationScope for downloads
  - Added better logging and user feedback for download tracking
  - Improved error handling and cleanup of database entries
  - Fixed play button functionality in song cards
  - Added Downloads playlist for offline playback of downloaded songs
  - Updated database schema to track downloaded songs and local file paths
  - Enhanced MusicPlayerService to play from local files when available
  - Added UI for managing downloaded songs with delete functionality
  - Optimized download functionality with improved speed and reliability
  - Added file verification for downloaded songs to ensure UI state accuracy
  - Enhanced download progress tracking with visual indicators
  - Implemented persistent download state tracking across app sessions
  - Added sleep timer with multiple duration options (10 min, 15 min, 30 min, 1 hour, end of song)
  - Added song download functionality with progress tracking
  - Fixed song playback issues from search screen
  - Improved auto-play functionality for better user experience
  - Fixed API base URL configuration for proper server connectivity
  - Enhanced playback reliability with explicit play commands
  - Fixed queue display discrepancy between service and UI
  - Enhanced personalization dialog with improved visual design
  - Visual cue for currently playing and selected songs
  - Removed obtrusive pause button overlay from album art
  - Backend optimizations for instant list/discovery endpoints
  - Non-blocking audio URL extraction with background prefetching
  - Thread pool implementation for controlled concurrency
  - Enhanced lock management with automatic cleanup
  - Client-side on-demand audio URL fetching
  - Increased cache size and TTL for better performance
  - Personalized music recommendations based on user preferences
  - User onboarding with genre, language, and artist selection
  - Flow exception handling improvements
  - Scrollable UI components for better user experience
  - Language-specific artist suggestions
  - Malayalam language support
  - Downloads playlist for offline playback of downloaded songs

## Key Features Implemented
1. Music Library Management
   - Song listing and playback
   - Search functionality with history and filter options (All, Songs, Artists, Albums)
   - Album and artist browsing with visual cards and song counts
   - Improved search accuracy with exact and partial matching
   - Clickable album and artist cards to filter songs by specific album/artist
   - Recently played tracks (limited to 10)
   - Personalized recommended songs section
   - Song details view
   - Marquee text for long titles
   - Colored album art backgrounds
   - YouTube Music integration for expanded library
   - User preference-based recommendations
   - Smart album art cropping to remove extended color bars
   - Visual cue (purple background) for currently playing songs
   - Downloads management with offline playback support

2. Playlist System
   - Create, rename, and delete playlists
   - Add/remove songs to/from playlists
   - "Liked Songs" as a permanent playlist
   - "Downloads" as a permanent playlist for offline songs
   - Real-time song count updates
   - Multi-select support in playlist dialogs
   - Playlist cover management
   - Dynamic playlist item design
   - Downloaded song management with delete functionality

3. Player Features
   - Play/pause/skip controls
   - Shuffle and repeat modes
   - Progress bar and duration display
   - Background playback support
   - Media style notifications with click-to-navigate functionality
   - Mini player with marquee text and swipe-to-dismiss
   - Enhanced visual feedback
   - Dynamic spacing with mini player
   - AMOLED black background in full player
   - Visual indication for currently selected song in mini player
   - Queue management with synchronized display between service and UI
   - Queue view in player screen with current and upcoming songs
   - Optimized song download functionality with progress indicator and status tracking
   - File verification for downloaded songs with automatic UI state updates
   - Improved download speed with connection pooling and parallel downloads
   - Improved playback reliability with auto-play enhancements
   - Sleep timer with multiple duration options (10 min, 15 min, 30 min, 1 hour, end of song)
   - Local file playback for downloaded songs with automatic fallback to streaming
   - Robust session management with app lifecycle handling
   - Synchronized seekbar between notification and full player
   - Clean state reset after app kill/restart

4. User Interface
   - Material 3 design implementation
   - Bottom navigation
   - Responsive layouts
   - Smooth animations
   - Dark/light theme support
   - Marquee text animations
   - Colored album art backgrounds
   - Enhanced visual hierarchy
   - Dynamic bottom padding (80dp/160dp)
   - Improved scroll behavior
   - Swipe gestures for mini player
   - Scrollable genre and language selection
   - Language-specific artist suggestions
   - Redesigned personalization dialog with improved spacing and visual appeal

5. Backend Services
   - FastAPI server for YouTube Music integration
   - Instant list/discovery endpoints with non-blocking design
   - On-demand audio extraction only when needed for playback
   - Efficient background prefetching using thread pool (max 3 workers)
   - Streaming endpoint with proper range support for seeking
   - Optimized download endpoint with parallel chunk downloading for faster speeds
   - Improved caching and compression for better download performance
   - Recommendations endpoint with personalization support
   - Trending music endpoint for popular tracks
   - Efficient video extraction with yt-dlp
   - Enhanced audio URL caching with larger TTLCache (2048 entries, 2h TTL)
   - Automatic lock cleanup to prevent memory leaks
   - Per-video_id locks with timeouts to prevent deadlocks
   - Failure caching to avoid repeated failed extraction attempts
   - Support for user preferences in recommendations
   - Robust error handling with proper exception management
   - Socket and request timeouts for network operations
   - Optimized multi-worker configuration for better performance

6. User Personalization
   - User preferences for genres, languages, and artists
   - Personalized music recommendations
   - Onboarding flow for new users
   - DataStore persistence for preferences
   - Multi-language support (16 languages including Malayalam)
   - Artist suggestions based on selected languages

7. Session and Lifecycle Management
   - NovaSessionManager singleton for centralized lifecycle management
   - App background/foreground handling without interrupting playback
   - Swipe-kill detection with proper state cleanup
   - Service persistence with START_STICKY for background playback
   - Idle timeout management (5 minutes after playback stops)
   - Clean state restoration when app reopens
   - Notification click navigation to full player
   - Media session state synchronization
   - State flow reset and cleanup on service destruction

## Current Implementation Details

### Data Layer
- Songs stored in Room database with playlistIds field and audioUrl field
- Playlists managed through Room with real-time updates
- Repository pattern for data access
- Direct song count queries for performance
- DataStore for preferences management
- Backend API integration for YouTube Music content
- User preferences stored in DataStore with JSON serialization

### Database Schema
```kotlin
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArt: String,
    val albumArtUrl: String? = null,
    val duration: Long,
    val audioUrl: String? = null,
    val isRecommended: Boolean = false,
    val isLiked: Boolean = false,
    @ColumnInfo(defaultValue = "") val playlistIds: String = "",
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey val id: String,
    val name: String,
    val coverUrl: String,
    val createdAt: Long
) {
    @Ignore
    var songs: List<Song> = emptyList()
}

@Entity(tableName = "recently_played")
data class RecentlyPlayed(
    @PrimaryKey val songId: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class UserMusicPreferences(
    val genres: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val artists: List<String> = emptyList()
)
```

### Key Components
1. **MusicDao**: 
   - Handles all database operations
   - Uses Flow for reactive updates
   - Includes direct song count queries
   - Manages playlist-song relationships

2. **MusicRepository**: 
   - Manages data access and transformations
   - Implements caching strategy
   - Handles background operations
   - Provides Flow-based data streams with proper exception handling
   - Manages search history
   - Integrates with backend API for YouTube Music
   - Handles user preferences for personalized recommendations

3. **HomeViewModel**: 
   - Manages UI state for home screen
   - Handles trending and recommended songs
   - Provides efficient Flow collection with proper error handling
   - Avoids unnecessary reloading of recommended songs
   - Manages recently played songs

4. **LibraryViewModel**: 
   - Manages playlist operations
   - Handles user preferences
   - Provides playlist data with song counts
   - Manages recently played songs
   - Handles Firebase synchronization

5. **PlayerViewModel**: 
   - Manages playback state and controls
   - Handles song loading and queue management
   - Manages download functionality
   - Provides sleep timer functionality
   - Handles service state restoration
   - Manages app lifecycle and session state

6. **NovaSessionManager**: 
   - Centralized app lifecycle management
   - Handles background/foreground transitions
   - Manages swipe-kill detection and cleanup
   - Controls idle timeout for service lifecycle
   - Ensures proper state persistence and restoration

7. **MusicPlayerService**: 
   - Foreground service for background playback
   - Media session management
   - Notification controls
   - Service lifecycle handling
   - State restoration and cleanup

8. **MusicPlayerServiceImpl**: 
   - ExoPlayer integration and management
   - Playback state management
   - Queue handling and synchronization
   - Progress tracking and updates
   - State flow management and reset

### Session Management Architecture
```kotlin
// NovaSessionManager - Centralized lifecycle management
object NovaSessionManager {
    fun onAppBackgrounded(context: Context, isPlaying: Boolean)
    fun onAppForegrounded()
    fun onTaskRemoved(context: Context, clearPlaybackState: () -> Unit, stopService: () -> Unit)
    fun onPlaybackStarted()
    fun onPlaybackStopped(context: Context, stopService: () -> Unit)
    fun wasSwipeKilled(context: Context): Boolean
    fun resetSwipeKilledFlag(context: Context)
}

// Service lifecycle with proper cleanup
class MusicPlayerService : Service() {
    override fun onStartCommand(): Int = START_STICKY
    override fun onTaskRemoved() // Cleanup on swipe-kill
    override fun onDestroy() // State reset and notification cleanup
}

// State restoration in PlayerViewModel
fun restorePlayerState(context: Context) {
    // Check if service was killed
    // Ensure service is running
    // Restore state from service
    // Handle clean state reset
}
```

### Notification and Media Session Management
```kotlin
// Notification click navigation
val contentIntent = PendingIntent.getActivity(
    context, 0,
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = Uri.parse("nova://player")
        putExtra("openPlayer", true)
    },
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

// Media session state reset
private fun resetMediaSessionState() {
    mediaSession.setMetadata(MediaMetadataCompat.Builder().build())
    val stateBuilder = PlaybackStateCompat.Builder()
        .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 1.0f)
    mediaSession.setPlaybackState(stateBuilder.build())
}

// State flow reset
private fun resetStateFlows() {
    _currentSong.value = null
    _isPlaying.value = false
    _progress.value = 0f
    _duration.value = 0L
    _currentQueue.value = emptyList()
}
```

## Recent Technical Improvements

### Session Management
- **NovaSessionManager**: Implemented singleton for centralized lifecycle management
- **App Kill Detection**: Proper detection and handling of swipe-kill with clean state reset
- **Service Persistence**: Enhanced service lifecycle with START_STICKY and proper cleanup
- **State Restoration**: Improved state restoration when app reopens after backgrounding

### Notification System
- **Click Navigation**: Notification clicks now navigate directly to full player
- **Media Session Sync**: Proper synchronization between notification and full player
- **Seekbar Alignment**: Fixed seekbar synchronization issues after app kill/restart
- **Clean State Reset**: Proper cleanup of stale notification state

### Playback Lifecycle
- **Background Playback**: Maintains playback state when app is backgrounded
- **Foreground Restoration**: Proper state restoration when app comes to foreground
- **Idle Timeout**: 5-minute idle timeout after playback stops
- **Service Cleanup**: Proper resource cleanup and state reset on service destruction

### Error Handling
- **State Flow Reset**: Comprehensive state flow reset to prevent stale state
- **Media Session Reset**: Clean media session state to prevent conflicts
- **Notification Cleanup**: Proper notification cancellation to prevent stale notifications
- **Service Recovery**: Robust service recovery and state restoration

## Development Guidelines
- Follow MVVM architecture with clean separation of concerns
- Use Kotlin Coroutines and Flow for asynchronous operations
- Implement proper error handling and user feedback
- Maintain consistent UI/UX patterns across the app
- Ensure proper lifecycle management for all components
- Use dependency injection with Hilt for better testability
- Follow Material 3 design principles
- Implement proper state management with StateFlow
- Ensure robust session and playback lifecycle management

## Known Issues and Limitations
- Limited to YouTube Music content through backend API
- Requires internet connection for streaming (except downloaded songs)
- Download functionality limited to app's internal storage
- Sleep timer resets when app is killed

## Future Enhancements
- Cross-platform support (iOS, Web)
- Offline playlist management
- Advanced audio equalizer
- Social features (sharing, collaborative playlists)
- Cloud sync for user preferences and playlists
- Enhanced personalization with machine learning
- Multi-device playback synchronization
- Advanced audio processing and effects