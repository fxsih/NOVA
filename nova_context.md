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
- **Current Focus**: Stability improvements and UI enhancements
- **Last Implemented Features**: 
  - Fixed file verification for downloaded songs with improved error handling
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
   - Media style notifications
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
   - Manages UI state and user actions
   - Handles playlist operations
   - Provides real-time updates
   - Manages selection state
   - Handles liked songs
   - Manages user preferences for personalization

5. **SearchViewModel**:
   - Manages search functionality and results
   - Handles search history with DataStore
   - Provides search filtering options (All, Songs, Artists, Albums)
   - Implements specialized search methods for albums and artists
   - Supports exact and partial matching for better accuracy
   - Tracks current filter item for UI state management
   - Manages loading states and error handling
   - Integrates with repository for data access

6. **PlayerViewModel**: 
   - Manages audio playback state
   - Handles playlist queue management
   - Provides synchronized queue between service and UI
   - Manages playback controls (play, pause, skip, etc.)
   - Handles shuffle and repeat modes
   - Manages current song and progress updates
   - Provides queue display data for UI
   - Implements optimized song download functionality with progress tracking
   - Verifies downloaded files to ensure accurate UI state
   - Manages persistent download state tracking across app sessions
   - Ensures consistent playback with explicit play commands
   - Implements sleep timer with multiple duration options and real-time countdown

7. **MusicPlayerService** and **MusicPlayerServiceImpl**: 
   - Handles audio playback with ExoPlayer
   - Manages the queue of songs
   - Provides StateFlow for current song, playback state, and queue
   - Handles audio URL fetching and fallback mechanisms
   - Manages playback errors and recovery
   - Implements shuffle and repeat functionality
   - Provides synchronized queue data to the UI

8. **PlaylistSelectionDialog**: 
   - Handles playlist selection
   - Shows real-time song counts
   - Supports multi-select
   - Provides create/rename options
   - Manages liked songs state

9. **PlaylistItem**: 
   - Displays individual playlists
   - Shows real-time song count
   - Handles playlist actions
   - Updates UI automatically
   - Enhanced visual design
   - Dynamic color scheme

10. **MarqueeText**: 
   - Handles text animation for long content
   - Uses graphicsLayer for smooth animation
   - Only animates when text overflows
   - Supports custom styling
   - 5-second animation duration
   - Smooth start/stop transitions

11. **MiniPlayerBar**: 
   - Enhanced visual design
   - Marquee text for song info
   - Colored album art backgrounds
   - Improved controls layout
   - Better touch targets
   - Dynamic bottom spacing
   - Swipe-to-dismiss functionality

12. **PlayerScreen**: 
    - Full-screen music player interface
    - Album art display with proper transformations
    - Playback controls with visual feedback
    - Progress bar with time display
    - Queue display with current and upcoming songs
    - Smart handling of queue data from service
    - Proper handling of null safety with local variables

13. **DynamicBottomPadding**:
   - Handles spacing for mini player
   - Automatically adjusts content padding (80dp/160dp)
   - Smooth transitions
   - Screen-specific customization
   - Maintains scroll position

14. **Backend Service**:
    - FastAPI server for YouTube Music integration
    - Non-blocking list/discovery endpoints for instant response
    - On-demand audio extraction only when needed for playback
    - Uses yt-dlp for efficient video extraction
    - Provides audio streaming capabilities with range support
    - Enhanced audio URL caching with larger TTLCache (2048 entries, 2h TTL)
    - Efficient background prefetching using thread pool (max 3 workers)
    - Automatic lock cleanup to prevent memory leaks
    - Per-video_id locks with timeouts to prevent deadlocks
    - Lock acquisition with try/finally blocks for guaranteed release
    - Failure caching to avoid repeated failed extraction attempts
    - Support for user preferences in recommendations
    - Socket and request timeouts for network operations
    - Proper error handling in background threads
    - Optimized multi-worker configuration with proper import strings
    - Graceful signal handling and shutdown procedures

15. **User Onboarding**:
    - Personalization dialog for new users
    - Genre selection with scrollable UI
    - Language selection with 16 supported languages
    - Artist suggestions based on selected languages
    - Efficient UI with FlowRow for better layout
    - Vertical scrolling for all content
    - DataStore persistence of preferences

16. **Album Art Processing**:
    - Smart transformation to detect and remove extended color bars
    - Analysis of all four edges of album art images
    - Cropping to the tightest bounding box containing real content
    - Center-cropping to square if needed
    - Consistent display across all UI components
    - Proper clipping with rounded corners
    - Improved visual appearance of YouTube Music album art

## Navigation Structure
- Home Screen
- Search Screen
- Library Screen
- Player Screen (Full & Mini)
- Playlist Detail Screen
- Onboarding Dialog (first launch)

## Color Palette
```kotlin
val colors = listOf(
    Color(0xFF1DB954), // Spotify Green
    Color(0xFFE91E63), // Pink
    Color(0xFF9C27B0), // Purple
    Color(0xFF2196F3), // Blue
    Color(0xFF00BCD4), // Cyan
    Color(0xFF4CAF50), // Green
    Color(0xFFFFC107), // Amber
    Color(0xFFFF9800), // Orange
    Color(0xFFF44336), // Red
    Color(0xFF795548)  // Brown
)
```

## Best Practices Being Followed
1. **Architecture**
   - Clear separation of concerns
   - Single source of truth for data
   - Unidirectional data flow
   - Proper dependency injection
   - Repository pattern implementation
   - Composable reusability
   - Backend-frontend separation
   - Proper Flow exception handling

2. **UI/UX**
   - Material 3 design system
   - Consistent design language
   - Immediate feedback for user actions
   - Smooth animations and transitions
   - Error handling and loading states
   - Marquee text for better readability
   - Colored backgrounds for visual interest
   - Dynamic spacing and layout
   - Responsive design patterns
   - Gesture-based interactions
   - Efficient scrolling interfaces
   - Personalized content based on user preferences

3. **Code Quality**
   - Kotlin best practices
   - Proper error handling
   - Clean and maintainable code structure
   - Comprehensive documentation
   - Type-safe navigation
   - Reusable components
   - State management patterns
   - Composable abstraction
   - Flow exception transparency

4. **Backend Development**
   - RESTful API design
   - Efficient data processing
   - Error handling and validation
   - Asynchronous operations
   - Resource optimization
   - Proper dependency management
   - Caching strategies for performance
   - Concurrency control with locks and timeouts
   - Background processing for better UX
   - Defensive programming with try/finally blocks
   - Proper signal handling and graceful shutdown
   - Optimized multi-worker configuration

## Client-Side Optimizations
1. **On-Demand Audio URL Fetching**:
   - Modified Song model to not rely on audio_url in list responses
   - Updated MusicPlayerServiceImpl to always fetch audio URLs when needed
   - Improved error handling and fallback mechanisms
   - Better resource utilization by only loading what's needed

2. **Improved Error Handling**:
   - Enhanced error recovery in audio playback
   - Automatic fallback to alternative endpoints
   - Better user feedback during playback issues

3. **Smart Album Art Processing**:
   - Custom CenterCropSquareTransformation for Coil
   - Intelligent edge detection to find extended color bars
   - Precise cropping to remove only the extended parts
   - Consistent implementation across all UI components
   - Better visual appearance for YouTube Music album art

## Next Steps
1. Implement user authentication with Firebase
2. Add cloud sync functionality for playlists and user preferences
3. Enhance offline support with complete playlist download functionality
4. Add audio equalizer with preset and custom configurations
5. Implement music visualization for player screen
6. Add social sharing features for songs and playlists
7. Implement comprehensive testing suite (unit, integration, UI tests)
8. Add more animation effects for smoother transitions
9. Enhance accessibility features including TalkBack support
10. Further improve performance optimization for large libraries
11. Add more gesture support for intuitive navigation
12. Expand language and genre support for global audience
13. Implement smart playlists based on user listening habits
14. Add lyrics support with time-synced highlighting
15. Implement cross-fade between tracks for seamless playback

## Backend Implementation
The backend service is built with FastAPI and provides the following endpoints:
- `/search` - Search for songs on YouTube Music
- `/yt_audio` - Stream audio from YouTube videos
- `/recommended` - Get personalized song recommendations
- `/trending` - Get trending music

### API Endpoints for App Integration
The Android app connects to the backend using these specific endpoints:
- `GET /search?query={query}&limit={limit}` - Search for songs with the given query
- `GET /yt_audio?video_id={video_id}` - Stream audio for a specific YouTube video ID
- `GET /download_audio?video_id={video_id}` - Optimized endpoint for downloading audio with parallel chunk processing
- `GET /recommended?video_id={video_id}&genres={genres}&languages={languages}&artists={artists}&limit={limit}` - Get personalized recommendations
- `GET /trending?limit={limit}` - Get current trending music
- `GET /playlist?playlist_id={playlist_id}&limit={limit}` - Get songs from a specific YouTube Music playlist
- `GET /featured?limit={limit}` - Get featured playlists
- `GET /audio_fallback?video_id={video_id}` - Fallback endpoint for audio streaming

Base URL for development: `http://192.168.29.154:8000`

Backend optimizations:
- Non-blocking list/discovery endpoints for instant response
- On-demand audio extraction only when needed for playback
- Efficient background prefetching using thread pool (max 3 workers)
- Enhanced audio URL caching with larger TTLCache (2048 entries, 2h TTL)
- Optimized download endpoint with parallel chunk downloading and larger buffer sizes
- Improved compression support with gzip/deflate for faster downloads
- Connection pooling for better network performance
- Automatic lock cleanup to prevent memory leaks
- Per-video_id locks with timeouts to prevent deadlocks
- Lock acquisition with try/finally blocks for guaranteed release
- Failure caching to avoid repeated failed extraction attempts
- Fallback search strategies for better results
- Socket and request timeouts for network operations
- Proper error handling in background threads
- Optimized multi-worker configuration with proper import strings
- Graceful signal handling and shutdown procedures

## Testing Strategy
1. Unit tests for ViewModels and Repository
2. Integration tests for database operations
3. UI tests for critical user flows
4. Performance testing for database operations
5. Navigation testing
6. Animation performance testing
7. Component reuse testing
8. State management testing
9. Flow exception handling testing
10. Backend API integration testing
11. Concurrency and deadlock testing for backend

## Common Issues & Solutions
1. If playlist counts aren't updating:
   - Check Flow collection in PlaylistItem
   - Verify DAO query is working
   - Ensure Repository is properly connected

2. If selection state is lost:
   - Check state management in dialog
   - Verify ViewModel state handling
   - Ensure proper recomposition handling

3. If database updates aren't reflecting:
   - Clear app data and rebuild
   - Check migration scripts
   - Verify Flow connections
   - Ensure database version is updated (currently v7)

4. If dynamic padding isn't working:
   - Verify PlayerViewModel state
   - Check composable recomposition
   - Ensure proper padding values

5. If marquee text isn't animating:
   - Check text overflow conditions
   - Verify animation duration
   - Ensure proper recomposition

6. If download status isn't updating correctly:
   - Verify file existence in Downloads directory
   - Check PreferenceManager for stored download IDs
   - Call verifyDownloadedSongs() to refresh download states
   - Ensure updateCurrentSongDownloadState() is called when needed
   - Check that both stored paths and generated filenames are verified
   - Ensure database entries are cleaned up if files don't exist
   - Verify file permissions are properly set for the download directory

7. If downloads don't complete when navigating away:
   - Use applicationScope in NovaApplication instead of viewModelScope
   - Ensure download coroutines are launched in the applicationScope
   - Add proper monitoring in PlayerScreen to track download status
   - Handle suspend function calls properly in coroutines
   - Verify that download progress is properly tracked in the UI

8. If duplicate "Downloads" entries appear in library:
   - Filter out any regular playlist with ID "downloads" from custom playlists section
   - Ensure consistent naming between screens
   - Check database for duplicate entries with similar IDs

9. If queue and shuffle don't work properly for Downloads playlist:
   - Add detailed logging to diagnose queue issues
   - Enhance downloaded song handling in MusicPlayerServiceImpl
   - Fix MediaItem creation for downloaded songs
   - Add proper file existence checks before using local paths
   - Ensure shuffle mode is properly applied to the Downloads playlist

10. If play button in song cards isn't working:
    - Add missing onPlayPause and onDetailsClick parameters to components
    - Implement proper play/pause logic for song items
    - Verify that the click area is properly defined

11. If recommended songs aren't loading:
   - Check user preferences in DataStore
   - Verify backend API connection
   - Ensure proper Flow collection in HomeViewModel
   - Check LaunchedEffect triggers
   - Verify network connectivity and API response format

12. If artist suggestions aren't showing:
   - Verify language selection
   - Check ArtistSuggestions composable
   - Ensure proper recomposition on language change 
   - Verify that the artist data is properly formatted

13. If queue display shows incorrect number of songs:
   - Check that MusicPlayerService exposes currentQueue StateFlow
   - Verify PlayerViewModel is collecting from service's queue
   - Ensure getCurrentPlaylistSongs() prioritizes service queue
   - Check for smart cast issues with currentSong in PlayerScreen
   - Use local variables to safely handle Flow properties with custom getters

14. If backend gets stuck or freezes:
   - Check for deadlocks in lock acquisition
   - Verify all locks are being released properly
   - Check network timeouts in yt-dlp and requests operations
   - Ensure proper error handling in background threads
   - Verify signal handling is working correctly
   - Check worker configuration in uvicorn settings
   - Verify thread pool is not overloaded
   - Check for lock cleanup execution
   - Monitor memory usage and implement proper garbage collection

15. If audio playback fails:
    - Verify the client is using the /yt_audio endpoint directly
    - Check that fallback mechanism to /audio_fallback is working
    - Ensure proper error handling in MusicPlayerServiceImpl
    - Verify network connectivity between app and backend
    - Check logs for any extraction errors in backend
    - Verify that the audio format is supported by ExoPlayer

16. If downloads are failing:
    - Verify the API base URL is correctly set (not using localhost)
    - Check storage permissions are granted on Android 10 and below
    - Verify network connectivity between app and backend
    - Check that the download directory exists and is writable
    - Verify proper error handling in download function
    - Ensure download status is properly tracked in database and preferences
    - Check for timeout settings in the download client

17. If songs don't play from search screen:
    - Ensure the song is loaded directly in PlayerViewModel, not just navigated to
    - Check that explicit play() calls are made after loading the song
    - Verify that both onClick and onPlayPause handlers properly load the song
    - Check that the search screen is properly collecting state from PlayerViewModel
    - Verify that the search results contain valid song data

18. If downloaded songs aren't playing offline:
    - Check if the song's isDownloaded flag is true in the database
    - Verify the localFilePath is correctly stored and points to an existing file
    - Ensure MusicPlayerServiceImpl is checking for local files before streaming
    - Verify the file exists in the Downloads directory
    - Check that the song was properly added to the Downloads playlist
    - Verify file permissions allow reading the downloaded file

19. If sleep timer isn't working properly:
    - Check that the timer coroutine is properly launched and cancelled
    - Verify that the countdown is properly updated in the UI
    - Ensure that the timer properly stops playback when it reaches zero
    - Check that the timer is cancelled when the user disables it
    - Verify that the timer persists across configuration changes

20. If album art isn't displaying correctly:
    - Check that the Coil image loading is properly configured
    - Verify that the album art URL is valid
    - Ensure that the default album art is provided as fallback
    - Check that the image transformation is properly applied
    - Verify that the album art cache is working correctly