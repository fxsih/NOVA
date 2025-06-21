# NOVA Music API Backend

A FastAPI-based backend for streaming music from YouTube Music.

## Requirements

- Python 3.8 or higher
- FFmpeg (system requirement)

## Installation

### 1. Install FFmpeg (System Requirement)

#### On Windows:
```
# Using chocolatey
choco install ffmpeg

# OR manually download from https://ffmpeg.org/download.html
```

#### On macOS:
```
brew install ffmpeg
```

#### On Ubuntu/Debian:
```
sudo apt update
sudo apt install ffmpeg
```

### 2. Set up Python Environment

```bash
# Create a virtual environment
python -m venv venv

# Activate the virtual environment
# On Windows:
venv\Scripts\activate
# On macOS/Linux:
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt
```

## Usage

### Start the Server

```bash
# Standard way
python main.py

# Recommended: Start with automatic yt-dlp updates
python run.py
```

The server will start on port 8000: http://localhost:8000

### Updating yt-dlp Only

If you want to update yt-dlp without starting the server:

```bash
python update_ytdlp.py
```

### Available Endpoints

1. **Web Player**: `GET /`
   - A simple web player interface to test the API

2. **Search**: `GET /search?query={search_term}&limit={limit}`
   - Search for songs
   - Parameters:
     - `query`: The search term
     - `limit`: Maximum number of results (default: 10)

3. **Stream Audio**: `GET /yt_audio?video_id={video_id}`
   - Get a streamable audio URL
   - Parameters:
     - `video_id`: YouTube video ID

4. **Recommendations**: `GET /recommended?video_id={video_id}&limit={limit}`
   - Get recommended songs based on a video
   - Parameters:
     - `video_id`: YouTube video ID
     - `limit`: Maximum number of recommendations (default: 10)

5. **Trending**: `GET /trending?limit={limit}`
   - Get trending songs
   - Parameters:
     - `limit`: Maximum number of songs (default: 20)

6. **Featured Playlists**: `GET /featured?limit={limit}`
   - Get featured playlists
   - Parameters:
     - `limit`: Maximum number of playlists (default: 10)

7. **Playlist Tracks**: `GET /playlist?playlist_id={playlist_id}&limit={limit}`
   - Get tracks from a playlist
   - Parameters:
     - `playlist_id`: YouTube Music playlist ID
     - `limit`: Maximum number of tracks (default: 50)

## Troubleshooting

### If you get errors with audio streaming:

1. Make sure FFmpeg is correctly installed and available in your PATH
2. Run the server with automatic updates: `python run.py` 
3. Check the server logs for more detailed error information
4. Try using a different video ID for testing

### If you get port conflicts:

Change the port number in the last line of `main.py`:

```python
uvicorn.run(app, host="0.0.0.0", port=8000)
```

## License

MIT