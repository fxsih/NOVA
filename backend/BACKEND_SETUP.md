# NOVA Music API Backend Documentation

## 📋 Table of Contents
- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Setup](#setup)
- [Starting the Server](#starting-the-server)
- [API Endpoints](#api-endpoints)
- [Configuration](#configuration)
- [Troubleshooting](#troubleshooting)
- [Development](#development)

## 🎵 Overview

The NOVA Music API is a FastAPI-based backend service that provides music streaming capabilities by interfacing with YouTube Music. It offers features like:

- **Music Search**: Search for songs, artists, and albums
- **Audio Streaming**: Stream audio directly from YouTube Music
- **Playlist Management**: Access and manage playlists
- **Trending Content**: Get trending songs and featured playlists
- **Recommendations**: Get personalized music recommendations
- **Caching**: Intelligent caching for improved performance

## 🔧 Prerequisites

Before setting up the backend, ensure you have:

- **Python 3.8+** installed on your system
- **FFmpeg** installed for audio processing
- **Git** for cloning the repository
- **pip** for package management

### Installing FFmpeg

#### Windows:
```bash
# Using Chocolatey
choco install ffmpeg

# Or download from https://ffmpeg.org/download.html
```

#### macOS:
```bash
# Using Homebrew
brew install ffmpeg
```

#### Linux (Ubuntu/Debian):
```bash
sudo apt update
sudo apt install ffmpeg
```

## 📦 Installation

1. **Clone the repository** (if not already done):
```bash
git clone https://github.com/fxsih/NOVA.git
cd NOVA/backend
```

2. **Create a virtual environment**:
```bash
# Windows
python -m venv venv
venv\Scripts\activate

# macOS/Linux
python3 -m venv venv
source venv/bin/activate
```

3. **Install dependencies**:
```bash
pip install -r requirements.txt
```

## ⚙️ Setup

### Environment Configuration

The backend is designed to work out-of-the-box with minimal configuration. However, you can customize:

1. **Port Configuration**: Default port is 8000
2. **CORS Settings**: Configured to allow all origins for development
3. **Caching**: LRU cache with 2048 entries and 2-hour TTL

### Database Setup

The backend uses in-memory caching and doesn't require a separate database setup.

## 🚀 Starting the Server

### Method 1: Using the Launcher Script (Recommended)

The `run.py` script automatically updates yt-dlp and starts the server:

```bash
# Activate virtual environment first
venv\Scripts\activate  # Windows
# source venv/bin/activate  # macOS/Linux

# Start the server
python run.py
```

### Method 2: Direct Execution

```bash
# Activate virtual environment
venv\Scripts\activate  # Windows
# source venv/bin/activate  # macOS/Linux

# Start the server directly
python main.py
```

### Method 3: Using Uvicorn

```bash
# Activate virtual environment
venv\Scripts\activate  # Windows
# source venv/bin/activate  # macOS/Linux

# Start with uvicorn
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

## 🌐 API Endpoints

### Base URL
```
http://localhost:8000
```

### Available Endpoints

#### 1. **Root Endpoint**
- **URL**: `GET /`
- **Description**: API information and status
- **Response**: HTML page with API details

#### 2. **Search Songs**
- **URL**: `GET /search`
- **Parameters**:
  - `query` (required): Search term
  - `limit` (optional): Number of results (default: 10)
- **Example**: `GET /search?query=despacito&limit=5`
- **Response**: JSON array of song objects

#### 3. **Get Trending Songs**
- **URL**: `GET /trending`
- **Parameters**:
  - `limit` (optional): Number of results (default: 20)
- **Example**: `GET /trending?limit=10`
- **Response**: JSON array of trending songs

#### 4. **Get Recommendations**
- **URL**: `GET /recommended`
- **Parameters**:
  - `video_id` (optional): YouTube video ID
  - `genres` (optional): Comma-separated genres
  - `languages` (optional): Comma-separated languages
  - `artists` (optional): Comma-separated artists
  - `limit` (optional): Number of results (default: 10)
- **Example**: `GET /recommended?video_id=abc123&limit=5`
- **Response**: JSON array of recommended songs

#### 5. **Get Featured Playlists**
- **URL**: `GET /featured`
- **Parameters**:
  - `limit` (optional): Number of results (default: 10)
- **Example**: `GET /featured?limit=5`
- **Response**: JSON array of featured playlists

#### 6. **Get Playlist Tracks**
- **URL**: `GET /playlist`
- **Parameters**:
  - `playlist_id` (required): YouTube Music playlist ID
  - `limit` (optional): Number of tracks (default: 50)
- **Example**: `GET /playlist?playlist_id=PLabc123&limit=20`
- **Response**: JSON array of playlist tracks

#### 7. **Stream Audio**
- **URL**: `GET /yt_audio`
- **Parameters**:
  - `video_id` (required): YouTube video ID
- **Example**: `GET /yt_audio?video_id=abc123`
- **Response**: Audio stream

#### 8. **Download Audio**
- **URL**: `GET /download_audio`
- **Parameters**:
  - `video_id` (required): YouTube video ID
- **Example**: `GET /download_audio?video_id=abc123`
- **Response**: Audio file download

#### 9. **Task Statistics**
- **URL**: `GET /task_stats`
- **Description**: Get performance statistics
- **Response**: JSON with task statistics

#### 10. **Critical Prefetch**
- **URL**: `GET /critical_prefetch`
- **Parameters**:
  - `video_ids` (required): Comma-separated video IDs
- **Example**: `GET /critical_prefetch?video_ids=abc123,def456`
- **Response**: JSON confirmation

## ⚙️ Configuration

### CORS Settings
The backend is configured to allow all origins for development:
```python
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

### Caching Configuration
- **Audio URL Cache**: 2048 entries, 2-hour TTL
- **Failure Cache**: 512 entries, 5-minute TTL
- **Thread Pools**: Priority-based task execution

### Thread Pool Configuration
- **Priority Thread Pool**: 10 workers
- **Legacy Thread Pools**: Separate pools for prefetch and download

## 🔍 Troubleshooting

### Common Issues

#### 1. **Port Already in Use**
```bash
# Check what's using port 8000
netstat -ano | findstr :8000  # Windows
lsof -i :8000                 # macOS/Linux

# Kill the process or use a different port
uvicorn main:app --port 8001
```

#### 2. **FFmpeg Not Found**
```bash
# Verify FFmpeg installation
ffmpeg -version

# If not found, install it using the methods above
```

#### 3. **yt-dlp Update Issues**
```bash
# Manual update
pip install --upgrade yt-dlp
```

#### 4. **Virtual Environment Issues**
```bash
# Recreate virtual environment
rm -rf venv
python -m venv venv
venv\Scripts\activate  # Windows
# source venv/bin/activate  # macOS/Linux
pip install -r requirements.txt
```

#### 5. **Permission Errors**
```bash
# Windows: Run as Administrator
# macOS/Linux: Use sudo if needed
sudo python main.py
```

### Logs and Debugging

The backend provides detailed logging:
- **INFO**: General application events
- **ERROR**: Error messages and exceptions
- **DEBUG**: Detailed debugging information

To enable debug logging, modify `main.py`:
```python
logging.basicConfig(level=logging.DEBUG)
```

## 🛠️ Development

### Project Structure
```
backend/
├── main.py              # Main FastAPI application
├── run.py               # Server launcher with yt-dlp updates
├── requirements.txt     # Python dependencies
├── update_ytdlp.py     # yt-dlp update utility
├── app/                 # Application modules
│   ├── api/            # API endpoints
│   └── services/       # Business logic
├── middleware/          # Custom middleware
└── venv/               # Virtual environment
```

### Adding New Endpoints

1. **Create the endpoint function**:
```python
@app.get("/new_endpoint")
def new_endpoint(param: str = Query(...)):
    return {"message": f"Hello {param}"}
```

2. **Add proper error handling**:
```python
@app.get("/new_endpoint")
def new_endpoint(param: str = Query(...)):
    try:
        # Your logic here
        return {"result": "success"}
    except Exception as e:
        logger.error(f"Error in new_endpoint: {str(e)}")
        raise HTTPException(status_code=500, detail="Internal server error")
```

### Testing the API

#### Using curl:
```bash
# Test search endpoint
curl "http://localhost:8000/search?query=despacito&limit=5"

# Test trending endpoint
curl "http://localhost:8000/trending?limit=10"
```

#### Using Python requests:
```python
import requests

# Search for songs
response = requests.get("http://localhost:8000/search", 
                       params={"query": "despacito", "limit": 5})
print(response.json())

# Get trending songs
response = requests.get("http://localhost:8000/trending", 
                       params={"limit": 10})
print(response.json())
```

### Performance Monitoring

The backend includes built-in performance monitoring:
- **Task Statistics**: Available at `/task_stats`
- **Thread Pool Monitoring**: Real-time pool statistics
- **Cache Hit Rates**: LRU cache performance metrics

## 📚 Additional Resources

- **FastAPI Documentation**: https://fastapi.tiangolo.com/
- **yt-dlp Documentation**: https://github.com/yt-dlp/yt-dlp
- **YouTube Music API**: https://github.com/sigma67/ytmusicapi
- **FFmpeg Documentation**: https://ffmpeg.org/documentation.html

## 🤝 Contributing

When contributing to the backend:

1. **Follow PEP 8** coding standards
2. **Add proper error handling** to new endpoints
3. **Include logging** for debugging
4. **Test thoroughly** before submitting changes
5. **Update documentation** for new features

## 📄 License

This project is part of the NOVA Music application. See the main repository for license information.

---

**Last Updated**: January 2025  
**Version**: 1.0  
**Maintainer**: NOVA Development Team
