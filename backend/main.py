from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, FileResponse, JSONResponse, RedirectResponse, StreamingResponse
from contextlib import asynccontextmanager
import yt_dlp
from ytmusicapi import YTMusic
import json
from typing import List, Dict, Any, Optional
import logging
import requests
from urllib.parse import parse_qs, urlparse
import os
import time
import random
import threading
from cachetools import TTLCache
from enum import IntEnum
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta

# Custom lock class that tracks acquisition time
class TimedLock:
    def __init__(self):
        self._lock = threading.RLock()
        self._acquire_time = None
    
    def acquire(self, timeout=None):
        result = self._lock.acquire(timeout=timeout)
        if result:
            self._acquire_time = time.time()
        return result
    
    def release(self):
        self._lock.release()
        self._acquire_time = None
    
    def locked(self):
        return self._lock._count > 0

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Lifespan context manager for startup and shutdown events
@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    logger.info("Starting NOVA Music API...")
    
    # No pre-caching of popular songs to avoid startup overhead
    logger.info("Starting NOVA Music API without pre-caching popular songs...")
    print("🚀 NOVA Music API starting up...")
    
    yield
    # Shutdown
    logger.info("Shutting down NOVA Music API...")
    try:
        # Shutdown priority thread pool with timeout
        priority_pool.shutdown(wait=True)
        logger.info("Priority thread pool shutdown complete")
        
        # Shutdown legacy thread pools with timeout
        prefetch_thread_pool.shutdown(wait=True)
        download_thread_pool.shutdown(wait=True)
        logger.info("Legacy thread pools shutdown complete")
        
        # Clean up locks
        cleanup_locks()
        logger.info("Lock cleanup complete")
        
        # Clear all caches to free memory
        audio_url_cache.clear()
        video_info_cache.clear()
        audio_url_failure_cache.clear()
        logger.info("Cache cleanup complete")
        
        # Force garbage collection
        import gc
        gc.collect()
        logger.info("Garbage collection complete")
        
    except Exception as e:
        logger.error(f"Error during shutdown: {str(e)}")
    finally:
        logger.info("NOVA Music API shutdown complete")
        print("🛑 NOVA Music API shutdown complete")

app = FastAPI(title="NOVA Music API", description="API for streaming music from YouTube Music", lifespan=lifespan)

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

# LRU cache for audio URLs (max 8192 entries, 6 hour TTL for better caching)
audio_url_cache = TTLCache(maxsize=8192, ttl=21600)
# Locks for each video_id to avoid duplicate yt-dlp calls
audio_url_locks = {}
# Cache for failures (short TTL)
audio_url_failure_cache = TTLCache(maxsize=2048, ttl=900)  # 15 min TTL for failures
# Cache for video info to avoid re-extraction
video_info_cache = TTLCache(maxsize=4096, ttl=7200)  # 2 hour TTL for video info
# Cache for search results to avoid repeated API calls
search_cache = TTLCache(maxsize=2048, ttl=1800)  # 30 min TTL for search results
# Cache for trending songs
trending_cache = TTLCache(maxsize=100, ttl=3600)  # 1 hour TTL for trending
# Cache for featured playlists
featured_cache = TTLCache(maxsize=50, ttl=7200)  # 2 hour TTL for featured
# Cache for recommendations
recommendations_cache = TTLCache(maxsize=512, ttl=1800)  # 30 min TTL for recommendations

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

# Cleanup function for locks to prevent memory leaks
def cleanup_locks():
    to_remove = []
    current_time = time.time()
    
    for video_id, lock in audio_url_locks.items():
        # Remove unlocked locks
        if not lock.locked():
            to_remove.append(video_id)
        # Also remove locks that have been held for too long (potential deadlocks)
        elif hasattr(lock, '_acquire_time') and current_time - lock._acquire_time > 30:
            logger.warning(f"Removing stuck lock for {video_id} (held for {current_time - lock._acquire_time:.1f}s)")
            to_remove.append(video_id)
    
    for video_id in to_remove:
        try:
            # Try to release the lock if it's still held
            if video_id in audio_url_locks and audio_url_locks[video_id].locked():
                audio_url_locks[video_id].release()
        except:
            pass  # Ignore errors when releasing
        del audio_url_locks[video_id]
    
    if to_remove:
        logger.info(f"Cleaned up {len(to_remove)} locks. Active locks: {len(audio_url_locks)}")

def force_cleanup_locks():
    """Emergency cleanup function to remove all locks"""
    global audio_url_locks
    logger.warning("Force cleaning up all locks due to potential deadlock")
    audio_url_locks.clear()
    logger.info("All locks cleared")

# Helper function to extract video info efficiently
def extract_video_info_fast(video_id):
    """Extract video info with ultra-optimized settings for maximum speed"""
    url = f"https://www.youtube.com/watch?v={video_id}"
    
    # Check cache first
    if video_id in video_info_cache:
        logger.info(f"Using cached video info for {video_id}")
        return video_info_cache[video_id]
    
    # Ultra-optimized yt-dlp options for maximum speed
    ydl_opts = {
        'format': 'bestaudio/best',
        'quiet': True,
        'no_warnings': True,
        'noplaylist': True,
        'skip_download': True,
        'socket_timeout': 3,  # Ultra-fast timeout
        'retries': 0,  # No retries for maximum speed
        'fragment_retries': 0,
        'extractor_retries': 0,
        'file_access_retries': 0,
        'http_chunk_size': 10485760,
        'max_downloads': 1,
        'concurrent_fragment_downloads': 1,
        'format_sort': ['abr', 'asr'],  # Simplified format sorting
        'extract_flat': False,
        'ignoreerrors': False,
        'prefer_ffmpeg': False,
        'postprocessors': [],
        # Skip all unnecessary data extraction
        'writesubtitles': False,
        'writeautomaticsub': False,
        'writethumbnail': False,
        'writedescription': False,
        'writeinfojson': False,
        'writemetadata': False,
        'writesubtitles': False,
        'writeautomaticsub': False,
        # Ultra-aggressive speed optimizations
        'no_check_certificate': True,
        'prefer_insecure': True,
        'nocheckcertificate': True,
        'geo_bypass': True,
        'no_color': True,
        'no_progress': True,
        'extractor_args': {
            'youtube': {
                'skip': ['dash', 'hls', 'webm'],  # Skip more formats
                'player_client': ['android'],  # Use Android client only
                'player_skip': ['webpage', 'configs'],  # Skip player configs
            }
        },
        # Use most compatible format specification
        'format': 'bestaudio/best',
    }
    
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            if info:
                # Cache the info immediately
                video_info_cache[video_id] = info
                logger.info(f"Cached video info for {video_id}")
                return info
    except Exception as e:
        logger.error(f"Error extracting video info for {video_id}: {str(e)}")
        return None

# Helper function to get or fetch and cache audio URL for a video_id
def get_or_cache_audio_url(video_id):
    # Check for recent failure
    if video_id in audio_url_failure_cache:
        return None, None, None
    
    # Periodically clean up locks (every ~25 calls)
    if random.random() < 0.04:
        cleanup_locks()
    
    # Use a more robust lock management system
    lock = audio_url_locks.setdefault(video_id, TimedLock())  # Use TimedLock for better tracking
    
    # Add timeout to lock acquisition to prevent deadlocks
    acquired = lock.acquire(timeout=5)  # Reduced timeout
    if not acquired:
        logger.error(f"Timeout acquiring lock for {video_id}, possible deadlock")
        # Force remove the stuck lock
        if video_id in audio_url_locks:
            del audio_url_locks[video_id]
        # If we have too many active locks, force cleanup
        if len(audio_url_locks) > 100:
            force_cleanup_locks()
        return None, None, None
    
    try:
        if video_id in audio_url_cache:
            audio_url, expire_timestamp, content_type = audio_url_cache[video_id]
            if time.time() < expire_timestamp:
                return audio_url, expire_timestamp, content_type
            else:
                del audio_url_cache[video_id]
        try:
            ydl_opts = {
                'format': 'bestaudio/best',
                'quiet': True,
                'no_warnings': True,
                'noplaylist': True,
                'skip_download': True,
                'socket_timeout': 15,  # Add timeout for network operations
            }
            url = f"https://www.youtube.com/watch?v={video_id}"
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                if not info:
                    audio_url_failure_cache[video_id] = True
                    return None, None, None
                if 'url' in info:
                    audio_url = info['url']
                    try:
                        head_response = requests.head(audio_url, timeout=5)
                        content_type = head_response.headers.get('Content-Type', 'audio/mpeg')
                    except Exception:
                        content_type = 'audio/mpeg'
                    expire_timestamp = parse_expire_from_url(audio_url)
                    audio_url_cache[video_id] = (audio_url, expire_timestamp, content_type)
                    return audio_url, expire_timestamp, content_type
                formats = info.get('formats', [])
                audio_formats = [f for f in formats if f.get('acodec') != 'none']
                if not audio_formats:
                    audio_formats = formats
                audio_formats.sort(key=lambda f: (
                    0 if f.get('vcodec') in (None, 'none') else 1,
                    -(f.get('abr', 0) or 0)
                ))
                if not audio_formats:
                    audio_url_failure_cache[video_id] = True
                    return None, None, None
                best_audio = audio_formats[0]
                audio_url = best_audio.get('url')
                content_type = best_audio.get('mime_type', 'audio/mpeg').split(';')[0]
                expire_timestamp = parse_expire_from_url(audio_url)
                audio_url_cache[video_id] = (audio_url, expire_timestamp, content_type)
                return audio_url, expire_timestamp, content_type
        except Exception as e:
            logger.error(f"Error extracting audio URL for {video_id}: {str(e)}")
            audio_url_failure_cache[video_id] = True
            return None, None, None
    finally:
        # Always release the lock, even if an exception occurs
        lock.release()

# Priority-based task management system
from concurrent.futures import ThreadPoolExecutor, as_completed
from queue import PriorityQueue
from enum import IntEnum

# Priority levels (lower number = higher priority)
class TaskPriority(IntEnum):
    CRITICAL = 1      # Music playback and seeking
    HIGH = 2          # Search requests
    MEDIUM = 3        # Trending songs, recommendations
    LOW = 4           # Background prefetching
    BACKGROUND = 5    # Non-essential tasks

# Task wrapper for priority queue
class PriorityTask:
    def __init__(self, priority: TaskPriority, task_id: str, func, *args, **kwargs):
        self.priority = priority
        self.task_id = task_id
        self.func = func
        self.args = args
        self.kwargs = kwargs
        self.created_at = time.time()
    
    def __lt__(self, other):
        # Lower priority number = higher priority
        if self.priority != other.priority:
            return self.priority < other.priority
        # If same priority, older tasks get priority
        return self.created_at < other.created_at

# Priority-based thread pool manager
class PriorityThreadPool:
    def __init__(self, max_workers=10):
        self.max_workers = max_workers
        self.task_queue = PriorityQueue()
        self.thread_pool = ThreadPoolExecutor(max_workers=max_workers, thread_name_prefix="priority")
        self.running_tasks = {}
        self.task_lock = threading.Lock()
        self.stats = {
            'critical': 0,
            'high': 0,
            'medium': 0,
            'low': 0,
            'background': 0
        }
    
    def submit(self, priority: TaskPriority, task_id: str, func, *args, **kwargs):
        """Submit a task with priority"""
        task = PriorityTask(priority, task_id, func, *args, **kwargs)
        
        with self.task_lock:
            self.stats[priority.name.lower()] += 1
        
        # Submit to thread pool
        future = self.thread_pool.submit(self._execute_task, task)
        
        with self.task_lock:
            self.running_tasks[task_id] = future
        
        return future
    
    def _execute_task(self, task: PriorityTask):
        """Execute a priority task"""
        try:
            logger.info(f"Executing {task.priority.name} priority task: {task.task_id}")
            result = task.func(*task.args, **task.kwargs)
            return result
        except Exception as e:
            logger.error(f"Error executing task {task.task_id}: {str(e)}")
            raise
        finally:
            with self.task_lock:
                if task.task_id in self.running_tasks:
                    del self.running_tasks[task.task_id]
    
    def get_stats(self):
        """Get current task statistics"""
        with self.task_lock:
            return self.stats.copy()
    
    def shutdown(self):
        """Shutdown the thread pool"""
        self.thread_pool.shutdown(wait=True)

# Create priority thread pool
priority_pool = PriorityThreadPool(max_workers=15)

# Legacy thread pools for backward compatibility (optimized for speed)
prefetch_thread_pool = ThreadPoolExecutor(max_workers=8, thread_name_prefix="prefetch")
download_thread_pool = ThreadPoolExecutor(max_workers=6, thread_name_prefix="download")

# Background pre-fetch for audio URLs with priority
def background_prefetch_audio_urls(video_ids, priority=TaskPriority.LOW):
    def fetch_single(vid):
        try:
            logger.info(f"Background prefetching audio URL for {vid} (priority: {priority.name})")
            get_or_cache_audio_url(vid)
            return True
        except Exception as e:
            logger.error(f"Error in background prefetch for {vid}: {str(e)}")
            return False
    
    # Submit each video_id to the priority thread pool
    for vid in video_ids:
        task_id = f"prefetch_{vid}"
        priority_pool.submit(priority, task_id, fetch_single, vid)

# High-priority prefetch for immediate playback
def critical_prefetch_audio_urls(video_ids):
    """Prefetch audio URLs with critical priority for immediate playback"""
    background_prefetch_audio_urls(video_ids, TaskPriority.CRITICAL)

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
    start_time = time.time()
    
    # Create cache key
    cache_key = f"search:{query}:{limit}"
    
    try:
        # Check cache first
        if cache_key in search_cache:
            logger.info(f"Using cached search results for '{query}'")
            results = search_cache[cache_key]
            # Still prefetch in background
            video_ids = [song.get('videoId') for song in results[:3] if song.get('videoId')]
            if video_ids:
                background_prefetch_audio_urls(video_ids, TaskPriority.HIGH)
            return results
        
        # Optimized search with single API call and smart fallback
        search_results = None
        
        # Try songs filter first (most common case)
        try:
            search_results = ytmusic.search(query, filter="songs", limit=limit)
        except Exception as e:
            logger.warning(f"Songs filter failed for '{query}': {str(e)}")
        
        # If no results, try without filter (broader search)
        if not search_results:
            try:
                search_results = ytmusic.search(query, filter=None, limit=limit)
            except Exception as e:
                logger.warning(f"General search failed for '{query}': {str(e)}")
        
        # Final fallback to popular songs
        if not search_results:
            try:
                search_results = ytmusic.search("popular songs", filter="songs", limit=limit)
                logger.info(f"Using fallback results for '{query}'")
            except Exception as e:
                logger.error(f"Fallback search failed: {str(e)}")
                search_results = []
        
        # Cache the results
        if search_results:
            search_cache[cache_key] = search_results
            
            # Prefetch top results in background
            video_ids = [song.get('videoId') for song in search_results[:3] if song.get('videoId')]
            if video_ids:
                background_prefetch_audio_urls(video_ids, TaskPriority.HIGH)
        
        elapsed = time.time() - start_time
        if elapsed > 1.0:
            logger.warning(f"/search for '{query}' took {elapsed:.2f}s")
        
        return search_results or []
        
    except Exception as e:
        logger.error(f"/search error for '{query}': {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Search failed: {str(e)}")

# Removed redundant /stream endpoint that was just redirecting to /yt_audio

@app.get("/recommended")
def get_recommended(
    video_id: str = Query(None, description="YouTube video ID"),
    genres: str = Query(None, description="Comma-separated genres"),
    languages: str = Query(None, description="Comma-separated languages"),
    artists: str = Query(None, description="Comma-separated artists"),
    limit: int = Query(10, description="Number of recommendations to return")
):
    # Create cache key
    cache_key = f"recommended:{video_id}:{genres}:{languages}:{artists}:{limit}"
    
    try:
        # Check cache first
        if cache_key in recommendations_cache:
            logger.info(f"Using cached recommendations for {video_id or 'general'}")
            results = recommendations_cache[cache_key]
            # Still prefetch in background
            video_ids = [song.get('videoId') for song in results[:3] if song.get('videoId')]
            if video_ids:
                background_prefetch_audio_urls(video_ids, TaskPriority.MEDIUM)
            return results
        
        logger.info(f"Getting recommendations for video_id={video_id}, genres={genres}, languages={languages}, artists={artists}")
        
        if video_id:
            # Video-based recommendations
            try:
                recommendations = ytmusic.get_watch_playlist(video_id, limit=limit)
                tracks = recommendations.get('tracks', [])
                if tracks:
                    # Cache and prefetch
                    recommendations_cache[cache_key] = tracks
                    video_ids = [song.get('videoId') for song in tracks[:3] if song.get('videoId')]
                    if video_ids:
                        background_prefetch_audio_urls(video_ids, TaskPriority.MEDIUM)
                    return tracks
            except Exception as watch_error:
                logger.error(f"Error getting watch playlist: {str(watch_error)}")
        
        # Fallback to search-based recommendations
        query_parts = []
        if genres:
            query_parts.append(genres.replace(",", " "))
        if languages:
            query_parts.append(languages.replace(",", " "))
        if artists:
            query_parts.append(artists.replace(",", " "))
        query = " ".join(query_parts).strip() or "popular songs"
        
        logger.info(f"Recommendation query: {query}")
        
        # Use optimized search
        search_results = ytmusic.search(query, filter="songs", limit=limit)
        if not search_results:
            search_results = ytmusic.search("popular songs", filter="songs", limit=limit)
        
        # Cache and prefetch
        if search_results:
            recommendations_cache[cache_key] = search_results
            video_ids = [song.get('videoId') for song in search_results[:3] if song.get('videoId')]
            if video_ids:
                background_prefetch_audio_urls(video_ids, TaskPriority.MEDIUM)
        
        return search_results or []
        
    except Exception as e:
        logger.error(f"Error fetching recommendations: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Failed to get recommendations: {str(e)}")

@app.get("/trending")
def get_trending(limit: int = Query(20, description="Number of trending songs to return")):
    """
    Get international trending songs using optimized caching and parallel processing.
    """
    # Check cache first
    cache_key = f"trending:{limit}"
    if cache_key in trending_cache:
        logger.info("Using cached trending songs")
        results = trending_cache[cache_key]
        # Still prefetch in background
        video_ids = [song.get('videoId') for song in results[:3] if song.get('videoId')]
        if video_ids:
            background_prefetch_audio_urls(video_ids, TaskPriority.MEDIUM)
        return results
    
    try:
        logger.info("Getting international trending songs...")
        
        # Optimized: Use fewer, more effective search terms
        trending_terms = [
            "top hits 2024", "viral songs", "popular songs"
        ]
        
        all_songs = []
        seen_video_ids = set()
        
        # Process terms in parallel using thread pool
        def search_term(term):
            try:
                return ytmusic.search(term, filter="songs", limit=limit//len(trending_terms))
            except Exception as e:
                logger.error(f"Error searching for term '{term}': {str(e)}")
                return []
        
        # Use thread pool for parallel searches
        with ThreadPoolExecutor(max_workers=3) as executor:
            search_futures = [executor.submit(search_term, term) for term in trending_terms]
            
            for future in as_completed(search_futures):
                try:
                    search_results = future.result()
                    if search_results:
                        for song in search_results:
                            video_id = song.get('videoId')
                            if video_id and video_id not in seen_video_ids:
                                all_songs.append(song)
                                seen_video_ids.add(video_id)
                                
                                if len(all_songs) >= limit:
                                    break
                except Exception as e:
                    logger.error(f"Error processing search result: {str(e)}")
                    continue
        
        # If we don't have enough songs, add popular songs
        if len(all_songs) < limit:
            try:
                remaining = limit - len(all_songs)
                popular_results = ytmusic.search("popular music", filter="songs", limit=remaining)
                if popular_results:
                    for song in popular_results:
                        video_id = song.get('videoId')
                        if video_id and video_id not in seen_video_ids:
                            all_songs.append(song)
                            seen_video_ids.add(video_id)
                            
                            if len(all_songs) >= limit:
                                break
            except Exception as e:
                logger.error(f"Error adding popular songs: {str(e)}")
        
        # Cache the results
        trending_cache[cache_key] = all_songs[:limit]
        
        # Prefetch top results in background
        if all_songs:
            video_ids = [song.get('videoId') for song in all_songs[:3] if song.get('videoId')]
            if video_ids:
                background_prefetch_audio_urls(video_ids, TaskPriority.MEDIUM)
        
        logger.info(f"Found {len(all_songs)} international trending songs")
        return all_songs[:limit]
        
    except Exception as e:
        logger.error(f"Error fetching trending songs: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Failed to get trending songs: {str(e)}")

@app.get("/featured")
def get_featured_playlists(limit: int = Query(10, description="Number of featured playlists to return")):
    # Check cache first
    cache_key = f"featured:{limit}"
    if cache_key in featured_cache:
        logger.info("Using cached featured playlists")
        return featured_cache[cache_key]
    
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
        
        # Cache the results
        featured_cache[cache_key] = featured_playlists
        
        return featured_playlists
    except Exception as e:
        logger.error(f"Error fetching featured playlists: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Failed to get featured playlists: {str(e)}")

@app.get("/task_stats")
def get_task_statistics():
    """Get current task priority statistics"""
    try:
        stats = priority_pool.get_stats()
        return {
            "task_statistics": stats,
            "total_tasks": sum(stats.values()),
            "priority_distribution": {
                "critical": f"{(stats['critical'] / max(sum(stats.values()), 1)) * 100:.1f}%",
                "high": f"{(stats['high'] / max(sum(stats.values()), 1)) * 100:.1f}%",
                "medium": f"{(stats['medium'] / max(sum(stats.values()), 1)) * 100:.1f}%",
                "low": f"{(stats['low'] / max(sum(stats.values()), 1)) * 100:.1f}%",
                "background": f"{(stats['background'] / max(sum(stats.values()), 1)) * 100:.1f}%"
            }
        }
    except Exception as e:
        logger.error(f"Error getting task statistics: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Failed to get task statistics: {str(e)}")

@app.get("/critical_prefetch")
def critical_prefetch_endpoint(video_ids: str = Query(..., description="Comma-separated video IDs")):
    """Prefetch audio URLs with critical priority for immediate playback"""
    try:
        video_id_list = [vid.strip() for vid in video_ids.split(",") if vid.strip()]
        if not video_id_list:
            return {"error": "No valid video IDs provided"}
        
        logger.info(f"Critical prefetch requested for {len(video_id_list)} videos")
        critical_prefetch_audio_urls(video_id_list)
        
        return {
            "message": f"Critical prefetch initiated for {len(video_id_list)} videos",
            "video_ids": video_id_list
        }
    except Exception as e:
        logger.error(f"Error in critical prefetch: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Critical prefetch failed: {str(e)}")

@app.get("/playlist")
def get_playlist_tracks(playlist_id: str = Query(..., description="YouTube Music playlist ID"), 
                       limit: int = Query(50, description="Number of tracks to return")):
    # Create cache key
    cache_key = f"playlist:{playlist_id}:{limit}"
    
    try:
        logger.info(f"Fetching playlist with ID: {playlist_id}")
        
        # Check cache first
        if cache_key in search_cache:
            logger.info(f"Using cached playlist for {playlist_id}")
            cached_result = search_cache[cache_key]
            # Still prefetch in background
            if isinstance(cached_result, dict) and 'tracks' in cached_result:
                tracks = cached_result['tracks']
            else:
                tracks = cached_result
            video_ids = [song.get('videoId') for song in tracks[:3] if song.get('videoId')]
            if video_ids:
                background_prefetch_audio_urls(video_ids)
            return cached_result
        
        # For radio playlists, use faster approach
        if playlist_id.startswith("RDCLAK"):
            # Use popular songs directly for radio playlists (much faster)
            try:
                search_results = ytmusic.search("popular songs", filter="songs", limit=limit)
                result = {
                    "playlistInfo": {
                        "title": "Popular Songs",
                        "description": "Popular songs collection"
                    },
                    "tracks": search_results
                }
                
                # Cache and prefetch
                search_cache[cache_key] = result
                video_ids = [song.get('videoId') for song in result['tracks'][:3] if song.get('videoId')]
                if video_ids:
                    background_prefetch_audio_urls(video_ids)
                return result
                
            except Exception as e:
                logger.error(f"Error processing radio playlist: {str(e)}")
                # Return empty result instead of failing
                result = {
                    "playlistInfo": {
                        "title": "Popular Songs",
                        "description": "Popular songs collection"
                    },
                    "tracks": []
                }
                search_cache[cache_key] = result
                return result
        
        # Regular playlists with timeout protection
        try:
            # Add timeout to prevent hanging
            import signal
            
            def timeout_handler(signum, frame):
                raise TimeoutError("Playlist fetch timeout")
            
            # Set timeout for playlist fetch
            signal.signal(signal.SIGALRM, timeout_handler)
            signal.alarm(10)  # 10 second timeout
            
            try:
                playlist = ytmusic.get_playlist(playlist_id, limit=limit)
                signal.alarm(0)  # Cancel timeout
                
                if 'tracks' in playlist:
                    tracks = playlist['tracks']
                    
                    # Cache and prefetch
                    search_cache[cache_key] = playlist
                    video_ids = [song.get('videoId') for song in tracks[:3] if song.get('videoId')]
                    if video_ids:
                        background_prefetch_audio_urls(video_ids)
                    return playlist
                else:
                    search_cache[cache_key] = playlist
                    return playlist
                    
            except TimeoutError:
                signal.alarm(0)
                logger.warning(f"Playlist fetch timeout for {playlist_id}, using fallback")
                # Fallback to popular songs
                search_results = ytmusic.search("popular songs", filter="songs", limit=limit)
                result = {
                    "playlistInfo": {
                        "title": "Popular Songs",
                        "description": "Popular songs collection (fallback)"
                    },
                    "tracks": search_results
                }
                search_cache[cache_key] = result
                return result
                
        except Exception as e:
            logger.error(f"Error fetching playlist: {str(e)}")
            # Return fallback instead of raising exception
            search_results = ytmusic.search("popular songs", filter="songs", limit=limit)
            result = {
                "playlistInfo": {
                    "title": "Popular Songs",
                    "description": "Popular songs collection (error fallback)"
                },
                "tracks": search_results
            }
            search_cache[cache_key] = result
            return result
            
    except Exception as e:
        logger.error(f"Error in get_playlist_tracks: {str(e)}", exc_info=True)
        # Final fallback
        try:
            search_results = ytmusic.search("popular songs", filter="songs", limit=limit)
            return {
                "playlistInfo": {
                    "title": "Popular Songs",
                    "description": "Popular songs collection (final fallback)"
                },
                "tracks": search_results
            }
        except:
            return {
                "playlistInfo": {
                    "title": "Error",
                    "description": "Could not load playlist"
                },
                "tracks": []
            }

@app.get("/yt_audio")
async def get_yt_audio(request: Request, video_id: str = Query(..., description="YouTube video ID")):
    """
    Ultra-optimized audio streaming endpoint with aggressive caching and faster extraction.
    """
    try:
        # Check cache first with optimized lookup
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
        
        # If not in cache or expired, extract new URL with ultra-fast extraction
        if audio_url is None:
            logger.info(f"Extracting new audio URL for {video_id} (ULTRA-FAST priority)")
            
            try:
                # Use ultra-fast extraction with minimal processing
                info = extract_video_info_fast(video_id)
                
                if not info:
                    logger.error("No info returned from yt-dlp")
                    return {"error": "Could not extract video information"}
                
                # Get audio URL directly from formats (faster than checking 'url' first)
                formats = info.get('formats', [])
                if not formats:
                    return {"error": "No formats found"}
                
                # Find the best audio format quickly (optimized selection)
                audio_url = None
                content_type = 'audio/mpeg'  # Default content type
                
                # First pass: look for direct audio URL in info
                if 'url' in info and info.get('acodec') != 'none':
                    audio_url = info['url']
                    logger.info("Found direct audio URL in info")
                else:
                    # Second pass: find best audio format from formats list
                    audio_formats = [f for f in formats if f.get('acodec') != 'none' and f.get('url')]
                    
                    if not audio_formats:
                        # Fallback to any format with URL
                        audio_formats = [f for f in formats if f.get('url')]
                    
                    if audio_formats:
                        # Sort by quality (optimized sorting)
                        audio_formats.sort(key=lambda f: (
                            0 if f.get('vcodec') in (None, 'none') else 1,  # Prefer audio only
                            -(f.get('abr', 0) or 0)  # Then by audio bitrate (higher first)
                        ))
                        
                        best_audio = audio_formats[0]
                        audio_url = best_audio.get('url')
                        content_type = best_audio.get('mime_type', 'audio/mpeg').split(';')[0]
                        
                        logger.info(f"Selected format: {best_audio.get('format_id')}, bitrate: {best_audio.get('abr', 'unknown')}")
                
                if not audio_url:
                    return {"error": "No suitable audio URL found"}
                
                # Parse expiration time (optimized)
                expire_timestamp = parse_expire_from_url(audio_url)
                
                # Cache the URL immediately
                audio_url_cache[video_id] = (audio_url, expire_timestamp, content_type)
                
                logger.info(f"Cached audio URL for {video_id}, expires at {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(expire_timestamp))}")
                
            except Exception as yt_error:
                logger.error(f"Error extracting with yt-dlp: {str(yt_error)}")
                return {"error": f"Error extracting audio: {str(yt_error)}"}
        
        # Prepare headers for the request to YouTube (optimized)
        headers = {
            "Accept-Encoding": "gzip, deflate",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        }
        
        # Forward the Range header if present (critical for seeking)
        if "range" in request.headers:
            headers["Range"] = request.headers["range"]
            logger.info(f"Forwarding Range header: {headers['Range']}")
        
        # Make the request to YouTube with optimized settings
        try:
            # Use session for connection pooling with optimized settings
            session = requests.Session()
            session.mount('https://', requests.adapters.HTTPAdapter(
                pool_connections=10,
                pool_maxsize=20,
                max_retries=1
            ))
            
            response = session.get(
                audio_url, 
                headers=headers, 
                stream=True, 
                timeout=10  # Reduced timeout for faster failure
            )
            
        except requests.exceptions.Timeout:
            logger.error(f"Timeout when requesting audio URL: {audio_url}")
            return {"error": "Timeout when requesting audio stream"}
        except requests.exceptions.RequestException as e:
            logger.error(f"Request error: {str(e)}")
            return {"error": f"Error requesting audio stream: {str(e)}"}
        
        # Prepare response headers (optimized)
        response_headers = {
            "Content-Type": content_type,
            "Content-Disposition": f'inline; filename="{video_id}.mp3"',
            "Cache-Control": "max-age=3600",
            "Accept-Ranges": "bytes"
        }
        
        # Forward important headers from YouTube's response
        important_headers = [
            "Content-Length", "Content-Range", "Content-Encoding"
        ]
        
        for header in important_headers:
            if header in response.headers:
                response_headers[header] = response.headers[header]
        
        # Use optimized chunk size for faster streaming (128KB chunks)
        chunk_size = 131072  # 128KB chunks for better performance
        
        # Return the streaming response with optimized settings
        return StreamingResponse(
            response.iter_content(chunk_size=chunk_size),
            status_code=response.status_code,
            headers=response_headers
        )
        
    except Exception as e:
        logger.error(f"Error in yt_audio: {str(e)}", exc_info=True)
        return {"error": f"Error streaming audio: {str(e)}"}



@app.get("/download_audio")
async def download_audio(video_id: str = Query(..., description="YouTube video ID")):
    """
    Optimized endpoint for downloading audio files (not for streaming).
    This endpoint is optimized for download speed rather than streaming playback.
    """
    try:
        # Get audio URL (reusing existing function)
        audio_url, expire_timestamp, content_type = get_or_cache_audio_url(video_id)
        
        if not audio_url:
            return {"error": "Could not extract audio URL"}
            
        # Make request with optimized settings
        session = requests.Session()
        
        # Use a HEAD request to get content info
        head_response = session.head(
            audio_url, 
            timeout=10,
            headers={"Accept-Encoding": "gzip, deflate"}
        )
        
        # Check if the source supports range requests
        supports_ranges = "accept-ranges" in head_response.headers and head_response.headers["accept-ranges"] == "bytes"
        
        # Get response headers
        response_headers = {
            "Content-Type": content_type,
            "Content-Disposition": f'attachment; filename="{video_id}.mp3"',
            "Cache-Control": "max-age=3600"
        }
        
        # Forward content length if available
        if "content-length" in head_response.headers:
            response_headers["Content-Length"] = head_response.headers["content-length"]
            
        # If range requests supported, prepare for faster download
        if supports_ranges:
            # Use a custom streaming response generator for parallel range requests
            async def download_generator():
                content_length = int(head_response.headers.get("content-length", "0"))
                
                # Only use parallel downloads for files over 1MB
                if content_length > 1024 * 1024:
                    # Use 4MB chunks for parallel downloading
                    chunk_size = 4 * 1024 * 1024
                    chunk_count = (content_length + chunk_size - 1) // chunk_size
                    
                    # Limit chunks to 5 to avoid too many parallel requests
                    chunk_count = min(chunk_count, 5)
                    
                    # Define chunk ranges
                    ranges = []
                    for i in range(chunk_count):
                        start = i * chunk_size
                        end = min(start + chunk_size - 1, content_length - 1)
                        ranges.append((start, end))
                    
                    # Download chunks in parallel
                    responses = []
                    for start, end in ranges:
                        range_header = f"bytes={start}-{end}"
                        responses.append(session.get(
                            audio_url,
                            headers={"Range": range_header, "Accept-Encoding": "gzip, deflate"},
                            stream=True,
                            timeout=30
                        ))
                    
                    # Yield chunks in order
                    for resp in responses:
                        for chunk in resp.iter_content(65536):  # 64KB chunks
                            yield chunk
                            
                else:
                    # For smaller files, use a simple download
                    response = session.get(
                        audio_url, 
                        headers={"Accept-Encoding": "gzip, deflate"},
                        stream=True, 
                        timeout=30
                    )
                    
                    # Use larger chunk size for faster downloads
                    for chunk in response.iter_content(65536):  # 64KB chunks
                        yield chunk
            
            # Return streaming response with improved download performance
            return StreamingResponse(
                download_generator(),
                headers=response_headers
            )
            
        else:
            # Fallback to simple download if range requests not supported
            response = session.get(
                audio_url, 
                headers={"Accept-Encoding": "gzip, deflate"},
                stream=True, 
                timeout=30
            )
            
            # Return streaming response with 64KB chunks for better performance
            return StreamingResponse(
                response.iter_content(chunk_size=65536),  # 64KB chunks
                headers=response_headers
            )
            
    except Exception as e:
        logger.error(f"Error in download_audio: {str(e)}", exc_info=True)
        return {"error": f"Error downloading audio: {str(e)}"}

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
    Ultra-optimized fallback endpoint with instant cache and minimal extraction
    """
    try:
        # Check cache first with optimized key
        fallback_cache_key = f"{video_id}_fallback"
        if fallback_cache_key in audio_url_cache:
            cached_data = audio_url_cache[fallback_cache_key]
            if isinstance(cached_data, dict):
                # New cache format
                if time.time() < cached_data['expires_at'].timestamp():
                    logger.info(f"Using cached fallback audio URL for {fallback_cache_key}")
                    return RedirectResponse(url=cached_data['url'], status_code=302)
            else:
                # Old cache format
                audio_url, expire_timestamp, content_type = cached_data
                if time.time() < expire_timestamp:
                    logger.info(f"Using cached fallback audio URL for {fallback_cache_key}")
                    return RedirectResponse(url=audio_url, status_code=302)
        
        # If not in cache, use the main extraction function for consistency
        logger.info(f"Audio fallback for ID: {video_id}")
        
        try:
            # Use the same optimized extraction as main endpoint
            info = extract_video_info_fast(video_id)
            
            if not info:
                return {"error": "Could not extract video information"}
            
            # Get audio URL from formats
            formats = info.get('formats', [])
            audio_url = None
            
            # Find the best audio format quickly
            for fmt in formats:
                if fmt.get('acodec') != 'none' and fmt.get('url'):
                    audio_url = fmt['url']
                    break
            
            if not audio_url:
                return {"error": "No suitable audio URL found"}
            
            # Cache the fallback URL with shorter TTL
            fallback_url_info = {
                'url': audio_url,
                'expires_at': datetime.now() + timedelta(hours=1),  # 1 hour TTL for fallback
                'content_type': 'audio/mp4'
            }
            audio_url_cache[fallback_cache_key] = fallback_url_info
            
            logger.info(f"Cached fallback audio URL for {fallback_cache_key}")
            
            return RedirectResponse(url=audio_url, status_code=302)
            
        except Exception as yt_error:
            logger.error(f"Error extracting with yt-dlp in fallback: {str(yt_error)}")
            return {"error": f"Error extracting audio in fallback: {str(yt_error)}", "video_id": video_id}
        
    except Exception as e:
        logger.error(f"Audio fallback error for {video_id}: {str(e)}", exc_info=True)
        return {"error": f"Audio fallback failed: {str(e)}", "video_id": video_id}

if __name__ == "__main__":
    import uvicorn
    import platform
    import argparse
    
    # Parse command line arguments
    parser = argparse.ArgumentParser(description="NOVA Music API Server")
    parser.add_argument("--host", default="0.0.0.0", help="Host to bind to")
    parser.add_argument("--port", type=int, default=8000, help="Port to bind to")
    parser.add_argument("--fast", action="store_true", help="Fast startup mode")
    
    args = parser.parse_args()
    
    # Configure uvicorn settings
    if args.fast:
        # Fast startup mode - minimal features
        uvicorn.run(
            app, 
            host=args.host, 
            port=args.port,
            workers=1,
            reload=False,
            access_log=False,
            log_level="warning",
            timeout_keep_alive=30
        )
    else:
        # Normal mode with full features
        if platform.system() == "Windows":
            uvicorn.run(
                app,
                host=args.host,
                port=args.port,
                timeout_keep_alive=65,
                log_level="info"
            )
        else:
            import multiprocessing
            workers = min(4, multiprocessing.cpu_count() + 1)
            if workers > 1:
                uvicorn.run(
                    "main:app",
                    host=args.host, 
                    port=args.port,
                    workers=workers,
                    timeout_keep_alive=65,
                    log_level="info"
                )
            else:
                uvicorn.run(
                    app, 
                    host=args.host, 
                    port=args.port,
                    timeout_keep_alive=65,
                    log_level="info"
                ) 