# NOVA Music Player App Context

## Project Overview
NOVA is a modern Android music player app built with Jetpack Compose, following MVVM architecture and clean code principles. The app provides a rich user experience for managing and playing music. It now includes a backend service that integrates with YouTube Music for search and streaming capabilities.

## Technical Stack
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room (Version 3)
- **Dependency Injection**: Hilt
- **Async Operations**: Kotlin Coroutines & Flow
- **Image Loading**: Coil
- **Media Playback**: ExoPlayer
- **Data Persistence**: DataStore Preferences
- **Navigation**: Navigation Compose
- **Backend**: FastAPI with Python
- **Music API**: YouTube Music API (ytmusicapi)
- **Video Download**: yt-dlp

## Current Development State
- **Database Version**: 3
- **Active Branch**: main
- **Current Focus**: Backend integration and UI/UX improvements
- **Last Implemented Features**: 
  - Dynamic bottom padding for mini player (80dp without mini player, 160dp with it)
  - Swipe-to-dismiss functionality for mini player
  - Improved PlayerScreen with AMOLED black background and better spacing
  - Backend service with YouTube Music integration
  - Search, streaming, recommendations, and trending music endpoints
  - Enhanced mini player with improved UI and interactions

## Key Features Implemented
1. Music Library Management
   - Song listing and playback
   - Search functionality with history
   - Recently played tracks (limited to 10)
   - Recommended songs section
   - Song details view
   - Marquee text for long titles
   - Colored album art backgrounds
   - YouTube Music integration for expanded library

2. Playlist System
   - Create, rename, and delete playlists
   - Add/remove songs to/from playlists
   - "Liked Songs" as a permanent playlist
   - Real-time song count updates
   - Multi-select support in playlist dialogs
   - Playlist cover management
   - Dynamic playlist item design

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

5. Backend Services
   - FastAPI server for YouTube Music integration
   - Search endpoint for finding songs
   - Streaming endpoint for playing music
   - Recommendations endpoint for discovering new music
   - Trending music endpoint for popular tracks
   - Efficient video extraction with yt-dlp

## Current Implementation Details

### Data Layer
- Songs stored in Room database with playlistIds field
- Playlists managed through Room with real-time updates
- Repository pattern for data access
- Direct song count queries for performance
- DataStore for preferences management
- Backend API integration for YouTube Music content

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
    val isRecommended: Boolean = false,
    val isLiked: Boolean = false,
    @ColumnInfo(defaultValue = "") val playlistIds: String = ""
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
   - Provides Flow-based data streams
   - Manages search history
   - Integrates with backend API for YouTube Music

3. **LibraryViewModel**: 
   - Manages UI state and user actions
   - Handles playlist operations
   - Provides real-time updates
   - Manages selection state
   - Handles liked songs

4. **PlaylistSelectionDialog**: 
   - Handles playlist selection
   - Shows real-time song counts
   - Supports multi-select
   - Provides create/rename options
   - Manages liked songs state

5. **PlaylistItem**: 
   - Displays individual playlists
   - Shows real-time song count
   - Handles playlist actions
   - Updates UI automatically
   - Enhanced visual design
   - Dynamic color scheme

6. **MarqueeText**: 
   - Handles text animation for long content
   - Uses graphicsLayer for smooth animation
   - Only animates when text overflows
   - Supports custom styling
   - 5-second animation duration
   - Smooth start/stop transitions

7. **MiniPlayerBar**: 
   - Enhanced visual design
   - Marquee text for song info
   - Colored album art backgrounds
   - Improved controls layout
   - Better touch targets
   - Dynamic bottom spacing
   - Swipe-to-dismiss functionality

8. **SongCard**: 
   - Enhanced visual design
   - Marquee text for song info
   - Colored album art backgrounds
   - Improved controls layout
   - Better touch targets
   - Dynamic color adaptation

9. **DynamicBottomPadding**:
   - Handles spacing for mini player
   - Automatically adjusts content padding (80dp/160dp)
   - Smooth transitions
   - Screen-specific customization
   - Maintains scroll position

10. **Backend Service**:
    - FastAPI server for YouTube Music integration
    - Endpoints for search, streaming, recommendations, and trending
    - Uses yt-dlp for efficient video extraction
    - Provides audio streaming capabilities
    - Runs on local network (192.168.29.154)

## Navigation Structure
- Home Screen
- Search Screen
- Library Screen
- Player Screen (Full & Mini)
- Playlist Detail Screen

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

3. **Code Quality**
   - Kotlin best practices
   - Proper error handling
   - Clean and maintainable code structure
   - Comprehensive documentation
   - Type-safe navigation
   - Reusable components
   - State management patterns
   - Composable abstraction

4. **Backend Development**
   - RESTful API design
   - Efficient data processing
   - Error handling and validation
   - Asynchronous operations
   - Resource optimization
   - Proper dependency management

## Next Steps
1. Fix backend compatibility issues with Python dependencies
2. Complete YouTube Music integration
3. Implement user authentication
4. Add cloud sync functionality
5. Enhance offline support
6. Implement music visualization
7. Add social sharing features
8. Implement comprehensive testing
9. Add more animation effects
10. Enhance accessibility features
11. Improve performance optimization
12. Add gesture support

## Backend Implementation
The backend service is built with FastAPI and provides the following endpoints:
- `/search` - Search for songs on YouTube Music
- `/stream` - Stream audio from YouTube videos
- `/recommendations` - Get song recommendations
- `/trending` - Get trending music

### API Endpoints for App Integration
The Android app will connect to the backend using these specific endpoints:
- `GET /search?query={query}&limit={limit}` - Search for songs with the given query
- `GET /yt_audio?video_id={video_id}` - Stream audio for a specific YouTube video ID
- `GET /recommended?video_id={video_id}&limit={limit}` - Get recommendations based on a video ID
- `GET /trending?limit={limit}` - Get current trending music
- `GET /playlist?playlist_id={playlist_id}&limit={limit}` - Get songs from a specific YouTube Music playlist
- `GET /featured?limit={limit}` - Get featured playlists
- `GET /audio_fallback?video_id={video_id}` - Fallback endpoint for audio streaming

Base URL for development: `http://192.168.29.154:8000`

Current backend issues:
- Compatibility problems with Python 3.13 and pydantic
- "ForwardRef._evaluate() missing recursive_guard" error during startup
- Needs downgrade to compatible Python and library versions

## Testing Strategy
1. Unit tests for ViewModels and Repository
2. Integration tests for database operations
3. UI tests for critical user flows
4. Performance testing for database operations
5. Navigation testing
6. Animation performance testing
7. Component reuse testing
8. State management testing

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

4. If dynamic padding isn't working:
   - Verify PlayerViewModel state
   - Check composable recomposition
   - Ensure proper padding values

5. If marquee text isn't animating:
   - Check text overflow conditions
   - Verify animation duration
   - Ensure proper recomposition 