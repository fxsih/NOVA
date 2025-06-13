package com.nova.music.data.local

import com.nova.music.data.model.Song
import javax.inject.Inject

class DatabaseInitializer @Inject constructor(
    private val musicDao: MusicDao
) {
    suspend fun populateDatabase() {
        val sampleSongs = listOf(
            Song(
                id = "1",
                title = "Shape of You",
                artist = "Ed Sheeran",
                album = "÷ (Divide)",
                duration = 235000,
                albumArt = "https://example.com/artwork1.jpg",
                isRecommended = true,
                isLiked = false,
                playlistIds = ""
            ),
            Song(
                id = "2",
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                duration = 201000,
                albumArt = "https://example.com/artwork2.jpg",
                isRecommended = true,
                isLiked = false,
                playlistIds = ""
            ),
            Song(
                id = "3",
                title = "Dance Monkey",
                artist = "Tones and I",
                album = "The Kids Are Coming",
                duration = 209000,
                albumArt = "https://example.com/artwork3.jpg",
                isRecommended = false,
                isLiked = false,
                playlistIds = ""
            ),
            Song(
                id = "4",
                title = "Someone You Loved",
                artist = "Lewis Capaldi",
                album = "Divinely Uninspired to a Hellish Extent",
                duration = 182000,
                albumArt = "https://example.com/artwork4.jpg",
                isRecommended = true,
                isLiked = false,
                playlistIds = ""
            ),
            Song(
                id = "5",
                title = "Watermelon Sugar",
                artist = "Harry Styles",
                album = "Fine Line",
                duration = 174000,
                albumArt = "https://example.com/artwork5.jpg",
                isRecommended = true,
                isLiked = false,
                playlistIds = ""
            ),
            Song(
                id = "6",
                title = "Bad Guy",
                artist = "Billie Eilish",
                album = "When We All Fall Asleep, Where Do We Go?",
                duration = 194000,
                albumArt = "https://example.com/artwork6.jpg",
                isRecommended = false,
                isLiked = false,
                playlistIds = ""
            ),
            Song(
                id = "7",
                title = "Stay",
                artist = "The Kid LAROI & Justin Bieber",
                album = "F*CK LOVE 3: OVER YOU",
                duration = 141000,
                albumArt = "https://example.com/artwork7.jpg",
                isRecommended = true,
                isLiked = false,
                playlistIds = ""
            ),
            Song(
                id = "8",
                title = "Levitating",
                artist = "Dua Lipa ft. DaBaby",
                album = "Future Nostalgia",
                duration = 203000,
                albumArt = "https://example.com/artwork8.jpg",
                isRecommended = false,
                isLiked = false,
                playlistIds = ""
            ),
            Song(
                id = "9",
                title = "drivers license",
                artist = "Olivia Rodrigo",
                album = "SOUR",
                duration = 242000,
                albumArt = "https://example.com/artwork9.jpg",
                isRecommended = true,
                isLiked = false,
                playlistIds = ""
            ),
            Song(
                id = "10",
                title = "Save Your Tears",
                artist = "The Weeknd & Ariana Grande",
                album = "After Hours",
                duration = 215000,
                albumArt = "https://example.com/artwork10.jpg",
                isRecommended = true,
                isLiked = false,
                playlistIds = ""
            )
        )

        musicDao.insertSongs(sampleSongs)
    }
} 