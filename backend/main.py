from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, FileResponse, JSONResponse, RedirectResponse, StreamingResponse
import yt_dlp
from ytmusicapi import YTMusic
import json
from typing import List, Dict, Any, Optional
import logging
import requests
from urllib.parse import parse_qs, urlparse
import os
import time

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="NOVA Music API", description="API for streaming music from YouTube Music")

# Configure CORS to allow requests from the Android app
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Allow all origins for development
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize YouTube Music API client
ytmusic = YTMusic()

# Cache for audio URLs to avoid repeated yt-dlp calls
# Structure: {video_id: (audio_url, expire_timestamp, content_type)}
audio_url_cache = {}

# Function to extract expire parameter from YouTube URL
def parse_expire_from_url(url):
    try:
        parsed_url = urlparse(url)
        query_params = parse_qs(parsed_url.query)
        if 'expire' in query_params:
            return int(query_params['expire'][0])
        return int(time.time()) + 3600  # Default: 1 hour from now
    except Exception as e:
        logger.error(f"Error parsing expire from URL: {str(e)}")
        return int(time.time()) + 3600  # Default: 1 hour from now

@app.get("/", response_class=HTMLResponse)
def read_root():
    """
    Serves the HTML player
    """
    html_file = os.path.join(os.path.dirname(__file__), "player.html")
    if os.path.exists(html_file):
        with open(html_file, "r") as f:
            return f.read()
    else:
        return "<html><body><h1>Welcome to NOVA Music API</h1><p>Player HTML not found.</p></body></html>"

@app.get("/search")
def search_songs(query: str = Query(..., description="Search query"), limit: int = Query(10, description="Number of results to return")):
    try:
        search_results = ytmusic.search(query, filter="songs", limit=limit)
        return search_results
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Search failed: {str(e)}")

# Removed redundant /stream endpoint that was just redirecting to /yt_audio

@app.get("/recommended")
def get_recommended(video_id: str = Query(..., description="YouTube video ID"), limit: int = Query(10, description="Number of recommendations to return")):
    try:
        logger.info(f"Getting recommendations for ID: {video_id}")
        
        # Check if the ID is a playlist ID (starts with MPREb)
        if video_id.startswith("MPREb"):
            logger.info("Detected playlist ID, searching for related songs...")
            
            try:
                # For playlist IDs, search for similar songs
                search_results = ytmusic.search("popular songs", filter="songs", limit=limit)
                return search_results
            except Exception as search_error:
                logger.error(f"Error searching for related songs: {str(search_error)}")
                raise HTTPException(status_code=404, detail="No recommendations found")
        
        # For regular video IDs, use the watch playlist
        try:
            # Get watch playlist (recommended songs)
            recommendations = ytmusic.get_watch_playlist(video_id, limit=limit)
            return recommendations.get('tracks', [])
        except Exception as watch_error:
            logger.error(f"Error getting watch playlist: {str(watch_error)}")
            
            # Fallback to search
            search_results = ytmusic.search("popular songs", filter="songs", limit=limit)
            return search_results
    except Exception as e:
        logger.error(f"Error fetching recommendations: {str(e)}", exc_info=True)
        
        # Instead of failing, return some default recommendations
        try:
            search_results = ytmusic.search("popular songs", filter="songs", limit=limit)
            return search_results
        except:
            raise HTTPException(status_code=500, detail=f"Failed to get recommendations: {str(e)}")

@app.get("/trending")
def get_trending(limit: int = Query(20, description="Number of trending songs to return")):
    try:
        # Since other methods aren't working, let's use the featured playlists approach
        logger.info("Getting trending songs from featured playlists...")
        
        try:
            # Get featured playlists first
            featured_playlists = []
            home_content = ytmusic.get_home()
            
            # Extract playlist information from home content
            for section in home_content:
                if 'contents' in section:
                    for item in section['contents']:
                        # Check if it's a playlist
                        if 'playlistId' in item:
                            featured_playlists.append({
                                'playlistId': item['playlistId'],
                                'title': item.get('title', 'Unknown Playlist')
                            })
                            
                            # Break if we have enough playlists
                            if len(featured_playlists) >= 3:
                                break
                
                # Break if we have enough playlists
                if len(featured_playlists) >= 3:
                    break
            
            # Collect songs from these playlists
            all_songs = []
            
            # Try to get songs from each playlist
            for playlist_info in featured_playlists:
                try:
                    if 'playlistId' in playlist_info:
                        playlist_id = playlist_info['playlistId']
                        
                        # Try to get the first song from the playlist to get its videoId
                        search_results = ytmusic.search(playlist_info['title'], filter="songs", limit=5)
                        
                        if search_results and len(search_results) > 0:
                            # Add songs to our collection
                            all_songs.extend(search_results)
                except Exception as e:
                    logger.error(f"Error getting songs from playlist {playlist_info.get('title')}: {str(e)}")
                    continue
            
            # Return the collected songs, limited by the requested limit
            return all_songs[:limit]
            
        except Exception as featured_error:
            logger.error(f"Error using featured playlists approach: {str(featured_error)}")
            
            # Fallback to search for popular songs
            search_results = ytmusic.search("top hits", filter="songs", limit=limit)
            return search_results
            
    except Exception as e:
        logger.error(f"Error fetching trending songs: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Failed to get trending songs: {str(e)}")

@app.get("/featured")
def get_featured_playlists(limit: int = Query(10, description="Number of featured playlists to return")):
    try:
        logger.info("Fetching featured playlists...")
        
        # Get the home page content which contains featured playlists
        home_content = ytmusic.get_home()
        
        featured_playlists = []
        
        # Extract playlist information from home content
        for section in home_content:
            if 'contents' in section:
                for item in section['contents']:
                    # Check if it's a playlist
                    if 'playlistId' in item:
                        playlist_info = {
                            'playlistId': item['playlistId'],
                            'title': item.get('title', 'Unknown Playlist'),
                            'description': item.get('description', ''),
                            'thumbnails': item.get('thumbnails', []),
                            'author': item.get('author', {})
                        }
                        featured_playlists.append(playlist_info)
                        
                        # Break if we have enough playlists
                        if len(featured_playlists) >= limit:
                            break
            
            # Break if we have enough playlists
            if len(featured_playlists) >= limit:
                break
        
        return featured_playlists
    except Exception as e:
        logger.error(f"Error fetching featured playlists: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Failed to get featured playlists: {str(e)}")

@app.get("/playlist")
def get_playlist_tracks(playlist_id: str = Query(..., description="YouTube Music playlist ID"), 
                       limit: int = Query(50, description="Number of tracks to return")):
    try:
        logger.info(f"Fetching playlist with ID: {playlist_id}")
        
        # For radio playlists (which start with RDCLAK)
        if playlist_id.startswith("RDCLAK"):
            # Instead of trying to get the playlist directly, search for the playlist title
            # Get the first few featured playlists
            featured_playlists = []
            home_content = ytmusic.get_home()
            
            # Find the matching playlist
            matching_playlist = None
            for section in home_content:
                if 'contents' in section:
                    for item in section['contents']:
                        if 'playlistId' in item and item['playlistId'] == playlist_id:
                            matching_playlist = item
                            break
            
            if matching_playlist and 'title' in matching_playlist:
                # Search for songs related to the playlist title
                search_results = ytmusic.search(matching_playlist['title'], filter="songs", limit=limit)
                
                return {
                    "playlistInfo": {
                        "title": matching_playlist.get('title', 'Radio Playlist'),
                        "description": matching_playlist.get('description', ''),
                        "thumbnails": matching_playlist.get('thumbnails', [])
                    },
                    "tracks": search_results
                }
            else:
                # If we can't find the playlist, just search for popular songs
                search_results = ytmusic.search("popular songs", filter="songs", limit=limit)
                
                return {
                    "playlistInfo": {
                        "title": "Popular Songs",
                        "description": "Popular songs collection"
                    },
                    "tracks": search_results
                }
        
        # For regular playlists
        try:
            playlist = ytmusic.get_playlist(playlist_id, limit=limit)
            
            if 'tracks' in playlist:
                return {
                    "playlistInfo": {
                        "title": playlist.get('title', 'Unknown Playlist'),
                        "description": playlist.get('description', ''),
                        "author": playlist.get('author', {}),
                        "trackCount": playlist.get('trackCount', 0),
                        "thumbnails": playlist.get('thumbnails', [])
                    },
                    "tracks": playlist['tracks'][:limit]
                }
        except Exception as playlist_error:
            logger.error(f"Error getting regular playlist: {str(playlist_error)}")
            
            # Fallback to search
            search_results = ytmusic.search("popular songs", filter="songs", limit=limit)
            
            return {
                "playlistInfo": {
                    "title": "Popular Songs",
                    "description": "Popular songs collection"
                },
                "tracks": search_results
            }
            
    except Exception as e:
        logger.error(f"Error fetching playlist tracks: {str(e)}", exc_info=True)
        # Instead of failing, return some default songs
        search_results = ytmusic.search("popular songs", filter="songs", limit=limit)
        
        return {
            "playlistInfo": {
                "title": "Popular Songs",
                "description": "Popular songs collection"
            },
            "tracks": search_results
        }

@app.get("/yt_audio")
async def get_yt_audio(request: Request, video_id: str = Query(..., description="YouTube video ID")):
    """
    Get an audio stream with proper HTTP Range support for efficient seeking.
    """
    try:
        # Check cache first
        if video_id in audio_url_cache:
            audio_url, expire_timestamp, content_type = audio_url_cache[video_id]
            # If URL is still valid (not expired)
            if time.time() < expire_timestamp:
                logger.info(f"Using cached audio URL for {video_id}, expires in {int(expire_timestamp - time.time())} seconds")
            else:
                # URL expired, remove from cache
                del audio_url_cache[video_id]
                audio_url = None
                content_type = None
        else:
            audio_url = None
            content_type = None
        
        # If not in cache or expired, extract new URL
        if audio_url is None:
            logger.info(f"Extracting new audio URL for {video_id}")
            url = f"https://www.youtube.com/watch?v={video_id}"
            
            ydl_opts = {
                'format': 'bestaudio/best',
                'quiet': False,
                'no_warnings': False,
                'noplaylist': True,
                'skip_download': True,
            }
            
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                
                if not info:
                    logger.error("No info returned from yt-dlp")
                    return {"error": "Could not extract video information"}
                
                # Try direct URL first
                if 'url' in info:
                    audio_url = info['url']
                    logger.info("Found direct URL in info dict")
                    
                    # Make a HEAD request to get content type
                    try:
                        head_response = requests.head(audio_url, timeout=5)
                        content_type = head_response.headers.get('Content-Type', 'audio/mpeg')
                    except Exception:
                        content_type = 'audio/mpeg'  # Default if HEAD request fails
                    
                    # Parse expiration time
                    expire_timestamp = parse_expire_from_url(audio_url)
                    
                    # Cache the URL
                    audio_url_cache[video_id] = (audio_url, expire_timestamp, content_type)
                    
                    logger.info(f"Cached audio URL for {video_id}, expires at {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(expire_timestamp))}")
                    
                else:
                    # Back to the old way if no direct URL
                    formats = info.get('formats', [])
                    if not formats:
                        return {"error": "No formats found", "url": url}
                    
                    # Try to find an audio format
                    audio_formats = [f for f in formats if f.get('acodec') != 'none']
                    
                    if not audio_formats:
                        # Fall back to any format if no audio formats are found
                        logger.warning("No audio formats found, using all formats")
                        audio_formats = formats
                    
                    # Sort by quality (prefer audio only, then by bitrate)
                    audio_formats.sort(key=lambda f: (
                        0 if f.get('vcodec') in (None, 'none') else 1,  # Prefer audio only
                        -(f.get('abr', 0) or 0)  # Then by audio bitrate (higher first)
                    ))
                    
                    if not audio_formats:
                        return {"error": "No formats available"}
                    
                    best_audio = audio_formats[0]
                    audio_url = best_audio.get('url')
                    
                    if not audio_url:
                        return {"error": "No URL found in best audio format"}
                    
                    logger.info(f"Selected format: {best_audio.get('format_id')}")
                    
                    # Get content type
                    content_type = best_audio.get('mime_type', 'audio/mpeg').split(';')[0]
                    
                    # Parse expiration time
                    expire_timestamp = parse_expire_from_url(audio_url)
                    
                    # Cache the URL
                    audio_url_cache[video_id] = (audio_url, expire_timestamp, content_type)
                    
                    logger.info(f"Cached audio URL for {video_id}, expires at {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(expire_timestamp))}")
        
        # Prepare headers for the request to YouTube
        headers = {}
        
        # Forward the Range header if present (critical for seeking)
        if "range" in request.headers:
            headers["Range"] = request.headers["range"]
            logger.info(f"Forwarding Range header: {headers['Range']}")
        
        # Make the request to YouTube
        response = requests.get(audio_url, headers=headers, stream=True, timeout=10)
        
        # Prepare response headers
        response_headers = {}
        
        # Forward important headers from YouTube's response
        important_headers = [
            "Content-Type", "Content-Length", "Content-Range", 
            "Accept-Ranges", "Content-Disposition"
        ]
        
        for header in important_headers:
            if header in response.headers:
                response_headers[header] = response.headers[header]
        
        # Ensure Content-Type is set
        if "Content-Type" not in response_headers:
            response_headers["Content-Type"] = content_type
            
        # Set Content-Disposition
        response_headers["Content-Disposition"] = f'inline; filename="{video_id}.mp3"'
        
        # Return the streaming response with the status code from YouTube
        return StreamingResponse(
            response.iter_content(chunk_size=1024),
            status_code=response.status_code,
            headers=response_headers
        )
        
    except Exception as e:
        logger.error(f"Error in yt_audio: {str(e)}", exc_info=True)
        return {"error": f"Error streaming audio: {str(e)}"}

@app.get("/youtube-dl-helper.js")
async def youtube_dl_helper():
    """
    Serve a JavaScript helper that can extract YouTube audio streams in the browser
    """
    js_content = """
// YouTube Player Extraction Helper
const YouTubeHelper = {
    // Extract audio streams directly from YouTube
    extractAudioStreams: async function(videoId) {
        try {
            // First try to get the video page
            const videoUrl = `https://www.youtube.com/watch?v=${videoId}`;
            const response = await fetch(videoUrl);
            const html = await response.text();
            
            // Look for the ytInitialPlayerResponse in the page
            const playerResponseMatch = html.match(/ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});\\s*(?:var\\s+meta|<\\/script>)/);
            if (!playerResponseMatch) {
                throw new Error('Could not find player response data');
            }
            
            try {
                const playerResponse = JSON.parse(playerResponseMatch[1]);
                const streamingData = playerResponse.streamingData;
                
                if (!streamingData) {
                    throw new Error('No streaming data found');
                }
                
                // Extract audio formats
                const formats = [
                    ...(streamingData.adaptiveFormats || []),
                    ...(streamingData.formats || [])
                ];
                
                // Filter for audio formats
                const audioFormats = formats.filter(format => {
                    return format.mimeType && format.mimeType.includes('audio');
                });
                
                // Get video details
                const videoDetails = playerResponse.videoDetails || {};
                
                return {
                    title: videoDetails.title || 'Unknown',
                    formats: audioFormats,
                    thumbnail: videoDetails.thumbnail ? 
                        videoDetails.thumbnail.thumbnails[videoDetails.thumbnail.thumbnails.length - 1].url : '',
                    duration: videoDetails.lengthSeconds || 0
                };
                
            } catch (parseError) {
                console.error('Error parsing player data:', parseError);
                throw new Error('Failed to parse player data');
            }
        } catch (error) {
            console.error('Error extracting streams:', error);
            throw error;
        }
    }
};
    """
    return JSONResponse(content={"code": js_content})

@app.get("/audio_fallback")
async def audio_fallback(request: Request, video_id: str = Query(..., description="YouTube video ID")):
    """
    Fallback endpoint that streams audio directly using a different approach
    """
    try:
        # Check cache first
        if video_id in audio_url_cache:
            audio_url, expire_timestamp, content_type = audio_url_cache[video_id]
            # If URL is still valid (not expired)
            if time.time() < expire_timestamp:
                logger.info(f"Using cached audio URL for fallback {video_id}, expires in {int(expire_timestamp - time.time())} seconds")
            else:
                # URL expired, remove from cache
                del audio_url_cache[video_id]
                audio_url = None
                content_type = None
        else:
            audio_url = None
            content_type = None
        
        # If not in cache or expired, extract new URL
        if audio_url is None:
            logger.info(f"Audio fallback for ID: {video_id}")
            url = f"https://www.youtube.com/watch?v={video_id}"
            
            # Use the same approach as the main endpoint but with different options
            ydl_opts = {
                'format': 'bestaudio/best',
                'quiet': False,
                'no_warnings': False,
                'noplaylist': True,
                'skip_download': True,
            }
            
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                
                if not info:
                    return {"error": "Could not extract video information"}
                
                # Try direct URL first
                if 'url' in info:
                    audio_url = info['url']
                    logger.info("Found direct URL in fallback")
                    
                    # Make a HEAD request to get content type
                    try:
                        head_response = requests.head(audio_url, timeout=5)
                        content_type = head_response.headers.get('Content-Type', 'audio/mpeg')
                    except Exception:
                        content_type = 'audio/mpeg'  # Default if HEAD request fails
                    
                    # Parse expiration time
                    expire_timestamp = parse_expire_from_url(audio_url)
                    
                    # Cache the URL
                    audio_url_cache[video_id] = (audio_url, expire_timestamp, content_type)
                    
                    logger.info(f"Cached fallback audio URL for {video_id}, expires at {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(expire_timestamp))}")
                    
                else:
                    # Process formats if no direct URL
                    formats = info.get('formats', [])
                    if formats:
                        # Try to find an audio format
                        audio_formats = [f for f in formats if f.get('acodec') != 'none']
                        
                        if audio_formats:
                            audio_formats.sort(key=lambda f: -(f.get('abr', 0) or 0))
                            best_audio = audio_formats[0]
                            audio_url = best_audio.get('url')
                            
                            if audio_url:
                                # Get content type
                                content_type = best_audio.get('mime_type', 'audio/mpeg').split(';')[0]
                                
                                # Parse expiration time
                                expire_timestamp = parse_expire_from_url(audio_url)
                                
                                # Cache the URL
                                audio_url_cache[video_id] = (audio_url, expire_timestamp, content_type)
                                
                                logger.info(f"Cached fallback audio URL for {video_id}, expires at {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(expire_timestamp))}")
        
        if not audio_url:
            return {"error": "No suitable audio URL found", "video_id": video_id}
        
        # Prepare headers for the request to YouTube
        headers = {}
        
        # Forward the Range header if present (critical for seeking)
        if "range" in request.headers:
            headers["Range"] = request.headers["range"]
            logger.info(f"Forwarding Range header to fallback: {headers['Range']}")
        
        # Make the request to YouTube
        response = requests.get(audio_url, headers=headers, stream=True, timeout=10)
        
        # Prepare response headers
        response_headers = {}
        
        # Forward important headers from YouTube's response
        important_headers = [
            "Content-Type", "Content-Length", "Content-Range", 
            "Accept-Ranges", "Content-Disposition"
        ]
        
        for header in important_headers:
            if header in response.headers:
                response_headers[header] = response.headers[header]
        
        # Ensure Content-Type is set
        if "Content-Type" not in response_headers:
            response_headers["Content-Type"] = content_type
            
        # Set Content-Disposition
        response_headers["Content-Disposition"] = f'inline; filename="{video_id}_fallback.mp3"'
        
        # Return the streaming response with the status code from YouTube
        return StreamingResponse(
            response.iter_content(chunk_size=1024),
            status_code=response.status_code,
            headers=response_headers
        )
        
    except Exception as e:
        logger.error(f"Error in audio_fallback: {str(e)}", exc_info=True)
        return {"error": str(e), "video_id": video_id}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000) 