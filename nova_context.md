# NOVA Music Player App Context

## Project Overview
NOVA is a modern Android music player app built with Jetpack Compose, following MVVM architecture and clean code principles. The app provides a rich user experience for managing and playing music. It includes a backend service that integrates with YouTube Music for search and streaming capabilities, with optimized audio URL caching and pre-fetching.

## Technical Stack
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room (Version 7)
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
- **Cloud Sync**: Firebase Firestore

## Current Development State
- **Database Version**: 7
- **Active Branch**: main
- **Current Focus**: Production-ready release with elite UX optimizations
- **Release Status**: Ready for Release
- **Last Implemented Features**: 
  - **ELITE UX OPTIMIZATIONS**: Implemented comprehensive performance optimizations for instant UI feedback
  - **LIKED SONGS INSTANT UPDATES**: Optimistic UI updates with background Firebase sync for immediate like/unlike feedback
  - **CHECKMARK FLICKERING RESOLUTION**: Fixed playlist checkmark flickering with temporary UI overlay state
  - **SLEEP TIMER END-OF-SONG FIX**: Fixed sleep timer to properly stop at end of song instead of next song
  - **MUSIC SERVICE BACKGROUND PERSISTENCE**: Fixed service being killed when app is backgrounded after pausing
  - **RECENTLY PLAYED AUTOMATIC ADDITION FIX**: Prevented songs from being automatically added to recently played
  - **LIKED SONGS SYNCHRONIZATION FIX**: Resolved liked songs disappearing and reverting to unliked state
  - **CUSTOM PLAYLIST SONG COUNT FLICKERING FIX**: Stabilized playlist song counts to prevent 0/actual count flickering
  - **FIREBASE DATA INTEGRITY**: Fixed liked songs being removed when added to custom playlists using SetOptions.merge()
  - **COROUTINE SCOPE MANAGEMENT**: Isolated player and repository coroutine scopes to prevent interference
  - **COMPILATION ERROR RESOLUTION**: Fixed smart cast compilation errors after changing val to var in Song data class
  - **CRITICAL FOREGROUND SERVICE CRASH FIX**: Fixed app crashes when resuming from background by removing blocking delays and ensuring immediate foreground service startup
  - **SEEKBAR STUCK ISSUE RESOLVED**: Fixed seekbar appearing stuck by reducing progress update delay from 500ms to 100ms and removing MediaSession throttling
  - **DURATION FILTER IMPLEMENTATION**: Added 15-minute duration filter for search results, trending songs, and recommended songs to exclude long videos/podcasts
  - **CUSTOM PLAYLIST PERSISTENCE**: Fixed playlist persistence with Firebase sync using playlist ID, user ID, and song ID for reliable data persistence
  - **FOREIGN KEY CONSTRAINT FIX**: Resolved SQLite foreign key constraint errors when playing songs from search by ensuring songs exist before adding to recently played
  - **CHECKMARK FLICKERING FIX**: Consolidated multiple flows into single flow for playlist membership tracking to prevent UI flickering
  - **REMOVE FROM PLAYLIST BUTTON**: Added "Remove from this playlist" button in playlist song items for custom playlists
  - **COMPILATION ERROR FIXES**: Resolved Kotlin flow collection and property delegate errors in PlaylistSelectionDialog
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
  - **PERSISTENT SEEKBAR/PROGRESS UPDATES AFTER APP REOPEN**: Fixed issue where seekbar would not update after reopening the app by ensuring the coroutine scope for progress updates is always re-initialized in the service lifecycle. Now, the progress bar always updates correctly after service or player rebuilds.
  - **ROBUST COROUTINE SCOPE MANAGEMENT**: Refactored MusicPlayerServiceImpl and MusicPlayerService to manage the coroutine scope and its SupervisorJob explicitly, re-initializing on service creation and cancelling on destruction. This ensures all progress and playback coroutines are always active and cleaned up properly.

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
   - **Duration filtering (15-minute limit) for search, trending, and recommended songs**
   - **Elite UX: Instant liked songs updates with optimistic UI feedback**

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
   - **Firebase sync for custom playlists with persistent data**
   - **Remove from playlist button for custom playlists**
   - **Consistent checkmark display for playlist membership**
   - **Temporary UI overlay state for instant checkmark feedback**

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
   - **Smooth seekbar movement with 100ms update frequency**
   - **Real-time MediaSession updates for responsive controls**
   - **Stable foreground service without crashes**
   - **Sleep timer properly stops at end of song**

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
   - **Elite UX: Frame-level debouncing for smooth animations**
   - **Optimistic UI updates for instant feedback**

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
   - **Stable foreground service startup without blocking delays**
   - **Immediate foreground service initialization to prevent crashes**
   - **Isolated coroutine scopes for player and repository operations**

## Critical Issues Resolved (Ready for Release)

### 1. Elite UX Optimizations ✅
- **Feature**: Comprehensive performance optimizations for instant UI feedback
- **Implementation**:
  - Optimistic UI updates for liked songs with background Firebase sync
  - Frame-level debouncing (16ms) for smooth animations
  - Temporary UI overlay state for playlist checkmarks
  - Retry strategy with exponential backoff for Firebase operations
  - Direct cache updates to prevent flow recreation

### 2. Checkmark Flickering Resolution ✅
- **Issue**: Checkmarks flickering when adding songs to playlists
- **Root Cause**: Race condition between optimistic updates and database operations
- **Solution**:
  - Implemented temporary UI overlay state (`_pendingPlaylistAdditions`)
  - Overlay state takes priority over database state during transitions
  - Automatic cleanup after successful operations
  - Error recovery with state reversion

### 3. Sleep Timer End-of-Song Fix ✅
- **Issue**: Sleep timer not stopping at end of song, continuing to next song
- **Root Cause**: Timer logic interference and unnecessary delays
- **Solution**:
  - Moved sleep timer logic to `MusicPlayerServiceImpl`
  - Implemented 99.5% completion check for end-of-song option
  - Removed interfering logic from `onMediaItemTransition`
  - Added comprehensive logging for debugging

### 4. Music Service Background Persistence ✅
- **Issue**: Service being killed when app is backgrounded after pausing
- **Root Cause**: Service calling `stopForeground()` when music was paused
- **Solution**:
  - Always keep service in foreground (`startForeground`)
  - Set notification as `setOngoing(true)` for non-dismissible state
  - Enhanced service lifecycle management

### 5. Recently Played Automatic Addition Fix ✅
- **Issue**: Songs appearing in recently played automatically without user action
- **Root Cause**: Songs added in both explicit play and automatic media transitions
- **Solution**:
  - Removed `addToRecentlyPlayed` call from `onMediaItemTransition`
  - Only add to recently played on explicit user actions

### 6. Liked Songs Synchronization Fix ✅
- **Issue**: Liked songs disappearing and reverting to unliked state
- **Root Cause**: Firebase deserialization issues and race conditions
- **Solution**:
  - Manual construction of Song objects from raw document data
  - Background Firebase operations with optimistic UI updates
  - Proper error handling with UI reversion

### 7. Custom Playlist Song Count Flickering Fix ✅
- **Issue**: Playlist song counts flickering between 0 and actual count
- **Root Cause**: Reactive flows causing temporary 0 counts during sync
- **Solution**:
  - Replaced reactive flows with `MutableStateFlow` and manual updates
  - Independent monitoring of each playlist's song count
  - Immediate targeted updates for specific playlists

### 8. Firebase Data Integrity Fix ✅
- **Issue**: Liked songs being removed when added to custom playlists
- **Root Cause**: `set()` overwriting entire song document, losing `isLiked` status
- **Solution**:
  - Used `SetOptions.merge()` to preserve existing fields
  - Explicit song data map excluding `isLiked` field
  - Maintained data integrity across operations

### 9. Coroutine Scope Management ✅
- **Issue**: Interference between player and repository operations
- **Root Cause**: Shared coroutine scopes causing premature cancellation
- **Solution**:
  - Isolated `repositoryCoroutineScope` for repository operations
  - Separate scope management for player and repository
  - Proper cleanup methods for both scopes

### 10. Foreground Service Crash Fix ✅
- **Issue**: App crashed with `ForegroundServiceDidNotStartInTimeException` when resuming from background
- **Root Cause**: Blocking 500ms delay in service startup and delayed `startForeground()` call
- **Solution**: 
  - Removed blocking `runBlocking` delay from `PlayerViewModel.ensureServiceRunning()`
  - Added immediate `startForeground()` call in `MusicPlayerService.onCreate()`
  - Added service running check to prevent duplicate starts
  - Enhanced error handling to prevent crashes

### 11. Seekbar Stuck Issue Fix ✅
- **Issue**: Seekbar appeared stuck with delayed updates
- **Root Cause**: 500ms progress update delay and 2-second MediaSession throttling
- **Solution**:
  - Reduced progress update delay from 500ms to 100ms (5x faster)
  - Removed MediaSession throttling for real-time updates
  - Created optimized `updateMediaSessionProgress()` method
  - Separated progress updates from full metadata updates

### 12. Duration Filter Implementation ✅
- **Feature**: Filter out songs longer than 15 minutes from search, trending, and recommended
- **Implementation**:
  - Added `filterSongsByDuration()` utility function
  - Applied filter to `searchSongs()`, `getTrendingSongs()`, and `getRecommendedSongs()`
  - Enhanced logging for filtered song counts
  - Preserved existing liked songs, downloads, and playlists

### 13. Custom Playlist Persistence ✅
- **Issue**: Playlists not persisting after app restarts or data clears
- **Solution**: Firebase sync using playlist ID, user ID, and song ID for reliable persistence

### 14. Foreign Key Constraint Fix ✅
- **Issue**: SQLite foreign key constraint errors when playing songs from search
- **Solution**: Ensure songs exist in database before adding to recently played

### 15. Compilation Error Fixes ✅
- **Issue**: Kotlin flow collection and property delegate errors
- **Solution**: Fixed type mismatches and flow collection issues in PlaylistSelectionDialog

### 16. Persistent Progress Updates After App Reopen ✅
- **Issue**: Seekbar/progress bar would not update after reopening the app or after service/player rebuilds
- **Root Cause**: Coroutine scope for progress updates was not re-initialized after service recreation, causing the progress coroutine to be inactive
- **Solution**: Made coroutine scope a managed property, re-initialized in MusicPlayerService.onCreate() and cancelled in onDestroy(), ensuring progress updates always work after app restarts

## Current Implementation Details

### Data Layer
- Songs stored in Room database with playlistIds field and audioUrl field
- Playlists managed through Room with real-time updates
- Repository pattern for data access
- Direct song count queries for performance
- DataStore for preferences management
- Backend API integration for YouTube Music content
- User preferences stored in DataStore with JSON serialization
- **Duration filtering for music content (≤15 minutes)**
- **Firebase Firestore integration for cloud sync**
- **Optimistic UI updates with background sync**

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
1. **MusicDao**: Database access with optimized queries
2. **MusicRepository**: Data management with Firebase sync and optimistic updates
3. **MusicPlayerService**: Background playback service with stable foreground operation
4. **PlayerViewModel**: UI state management with optimized progress tracking
5. **LibraryViewModel**: Elite UX optimizations with temporary overlay state
6. **NovaSessionManager**: Centralized app lifecycle management
7. **MediaNotificationManager**: Notification controls with real-time updates

## Performance Optimizations
- **Progress Updates**: 100ms intervals for smooth seekbar
- **MediaSession**: Real-time updates without throttling
- **Service Startup**: Immediate foreground initialization
- **Duration Filtering**: Efficient content filtering for better UX
- **Flow Management**: Optimized state flows to prevent UI flickering
- **Error Handling**: Comprehensive error handling with graceful degradation
- **Elite UX**: Frame-level debouncing and optimistic updates
- **Coroutine Isolation**: Separate scopes for player and repository operations
- **Cache Management**: Direct cache updates to prevent flow recreation

## Release Readiness Checklist ✅
- [x] All critical crashes resolved
- [x] Seekbar functionality working smoothly
- [x] Foreground service stable
- [x] Playlist persistence reliable
- [x] Duration filtering implemented
- [x] UI responsiveness optimized
- [x] Error handling comprehensive
- [x] Performance optimized
- [x] User experience polished
- [x] Elite UX optimizations implemented
- [x] Checkmark flickering resolved
- [x] Sleep timer working correctly
- [x] Background service persistence fixed
- [x] Liked songs synchronization stable
- [x] Firebase data integrity maintained

## Next Steps for Release
1. **Testing**: Comprehensive testing on multiple devices
2. **Performance**: Monitor app performance in production
3. **User Feedback**: Collect and address user feedback
4. **Updates**: Plan future feature updates based on usage data

**Status: READY FOR RELEASE** 🚀