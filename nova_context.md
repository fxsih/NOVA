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

## Current Development State
- **Database Version**: 3
- **Active Branch**: main
- **Current Focus**: Fixing playlist functionality and real-time updates
- **Last Fixed Issues**: 
  - Song count not updating in playlist UI
  - Selection state not persisting in playlist dialog
  - Playlist updates not reflecting immediately

## Key Features Implemented
1. Music Library Management
   - Song listing and playback
   - Search functionality with history
   - Recently played tracks (limited to 10)
   - Recommended songs section

2. Playlist System
   - Create, rename, and delete playlists
   - Add/remove songs to/from playlists
   - "Liked Songs" as a permanent playlist
   - Real-time song count updates
   - Multi-select support in playlist dialogs

3. Player Features
   - Play/pause/skip controls
   - Shuffle and repeat modes
   - Progress bar and duration display
   - Background playback support
   - Media style notifications

## Recent Development Focus
We've been working on improving the playlist functionality, specifically:
1. Changed from junction table approach to direct song-playlist relationship
2. Implemented playlist IDs storage in Song entity using comma-separated strings
3. Added real-time song count updates using Flow
4. Fixed UI update issues in playlist dialogs
5. Implemented proper state management for playlist selection

## Current Implementation Details

### Data Layer
- Songs stored in Room database with playlistIds field
- Playlists managed through Room with real-time updates
- Repository pattern for data access
- Direct song count queries for performance

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
) {
    fun getPlaylistIdsList(): List<String> = 
        if (playlistIds.isBlank()) emptyList() 
        else playlistIds.split(",")

    fun addPlaylistId(playlistId: String): String =
        if (playlistIds.isBlank()) playlistId
        else if (playlistId in getPlaylistIdsList()) playlistIds
        else "$playlistIds,$playlistId"

    fun removePlaylistId(playlistId: String): String =
        getPlaylistIdsList()
            .filter { it != playlistId }
            .joinToString(",")
}

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

3. **LibraryViewModel**: 
   - Manages UI state and user actions
   - Handles playlist operations
   - Provides real-time updates
   - Manages selection state

4. **PlaylistSelectionDialog**: 
   - Handles playlist selection
   - Shows real-time song counts
   - Supports multi-select
   - Provides create/rename options

5. **PlaylistItem**: 
   - Displays individual playlists
   - Shows real-time song count
   - Handles playlist actions
   - Updates UI automatically

## Current Challenges & Solutions
1. **Song Count Updates**
   - Challenge: UI not reflecting immediate changes
   - Solution: Implemented direct song count query with Flow
   - Implementation: Added getPlaylistSongCount to DAO and Repository

2. **Playlist Selection**
   - Challenge: Selection state not persisting
   - Solution: State hoisting and proper Flow management
   - Implementation: Updated PlaylistSelectionDialog with proper state management

3. **Database Operations**
   - Challenge: Complex playlist-song relationships
   - Solution: Simplified to comma-separated string approach
   - Implementation: Added helper functions in Song entity

## Best Practices Being Followed
1. **Architecture**
   - Clear separation of concerns
   - Single source of truth for data
   - Unidirectional data flow
   - Proper dependency injection

2. **UI/UX**
   - Consistent design language
   - Immediate feedback for user actions
   - Smooth animations and transitions
   - Error handling and loading states

3. **Code Quality**
   - Kotlin best practices
   - Proper error handling
   - Clean and maintainable code structure
   - Comprehensive documentation

## Next Steps
1. Continue improving playlist functionality
2. Enhance real-time updates
3. Optimize database operations
4. Add more user-requested features
5. Implement comprehensive testing

## Testing Strategy
1. Unit tests for ViewModels and Repository
2. Integration tests for database operations
3. UI tests for critical user flows
4. Performance testing for database operations

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