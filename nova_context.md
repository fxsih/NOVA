# NOVA Music Player App Context

## Project Overview
NOVA is a modern Android music player app built with Jetpack Compose, following MVVM architecture and clean code principles. The app provides a rich user experience for managing and playing music.

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

## Current Development State
- **Database Version**: 3
- **Active Branch**: main
- **Current Focus**: UI/UX improvements and player enhancements
- **Last Implemented Features**: 
  - Marquee text animation for long song titles and artist names
  - Colored album art backgrounds
  - Mini player improvements
  - Enhanced song card design
  - Improved navigation flow
  - Real-time song count updates in playlists
  - Multi-select support in playlist dialogs
  - Recently played tracks with limit of 10
  - Search history management
  - Playlist creation and management
  - Song details dialog

## Key Features Implemented
1. Music Library Management
   - Song listing and playback
   - Search functionality with history
   - Recently played tracks (limited to 10)
   - Recommended songs section
   - Song details view
   - Marquee text for long titles
   - Colored album art backgrounds

2. Playlist System
   - Create, rename, and delete playlists
   - Add/remove songs to/from playlists
   - "Liked Songs" as a permanent playlist
   - Real-time song count updates
   - Multi-select support in playlist dialogs
   - Playlist cover management

3. Player Features
   - Play/pause/skip controls
   - Shuffle and repeat modes
   - Progress bar and duration display
   - Background playback support
   - Media style notifications
   - Mini player with marquee text
   - Enhanced visual feedback

4. User Interface
   - Material 3 design implementation
   - Bottom navigation
   - Responsive layouts
   - Smooth animations
   - Dark/light theme support
   - Marquee text animations
   - Colored album art backgrounds
   - Enhanced visual hierarchy

## Current Implementation Details

### Data Layer
- Songs stored in Room database with playlistIds field
- Playlists managed through Room with real-time updates
- Repository pattern for data access
- Direct song count queries for performance
- DataStore for preferences management

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

6. **MarqueeText**: 
   - Handles text animation for long content
   - Uses graphicsLayer for smooth animation
   - Only animates when text overflows
   - Supports custom styling
   - 5-second animation duration

7. **MiniPlayerBar**: 
   - Enhanced visual design
   - Marquee text for song info
   - Colored album art backgrounds
   - Improved controls layout
   - Better touch targets

8. **SongCard**: 
   - Enhanced visual design
   - Marquee text for song info
   - Colored album art backgrounds
   - Improved controls layout
   - Better touch targets

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

2. **UI/UX**
   - Material 3 design system
   - Consistent design language
   - Immediate feedback for user actions
   - Smooth animations and transitions
   - Error handling and loading states
   - Marquee text for better readability
   - Colored backgrounds for visual interest

3. **Code Quality**
   - Kotlin best practices
   - Proper error handling
   - Clean and maintainable code structure
   - Comprehensive documentation
   - Type-safe navigation
   - Reusable components

## Next Steps
1. Implement user authentication
2. Add cloud sync functionality
3. Enhance offline support
4. Implement music visualization
5. Add social sharing features
6. Implement comprehensive testing
7. Add more animation effects
8. Enhance accessibility features

## Testing Strategy
1. Unit tests for ViewModels and Repository
2. Integration tests for database operations
3. UI tests for critical user flows
4. Performance testing for database operations
5. Navigation testing
6. Animation performance testing

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

4. If marquee text isn't animating:
   - Check if text width exceeds container
   - Verify animation parameters
   - Ensure proper layout constraints

5. If album art colors aren't consistent:
   - Verify song ID is stable
   - Check color palette implementation
   - Ensure proper color assignment 