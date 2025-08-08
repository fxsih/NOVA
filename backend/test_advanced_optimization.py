#!/usr/bin/env python3
"""
Advanced test script to measure YouTube extraction performance with new optimizations
"""
import time
import yt_dlp
import logging
import requests
from cachetools import TTLCache

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Create caches for testing
video_info_cache = TTLCache(maxsize=4096, ttl=7200)
popular_video_cache = TTLCache(maxsize=1000, ttl=86400)

def test_extraction_with_cache(video_id, use_cache=True):
    """Test extraction speed with caching"""
    url = f"https://www.youtube.com/watch?v={video_id}"
    
    # Check cache first
    if use_cache and video_id in video_info_cache:
        logger.info(f"Using cached video info for {video_id}")
        return 0.01, True  # Cache hit is very fast
    
    ydl_opts = {
        'format': 'bestaudio/best',
        'quiet': True,
        'no_warnings': True,
        'noplaylist': True,
        'skip_download': True,
        'socket_timeout': 5,
        'retries': 0,
        'fragment_retries': 0,
        'extractor_retries': 0,
        'file_access_retries': 0,
        'http_chunk_size': 10485760,
        'max_downloads': 1,
        'concurrent_fragment_downloads': 1,
        'format_sort': ['asr', 'abr', 'size', 'quality'],
        'extract_flat': False,
        'ignoreerrors': False,
        'prefer_ffmpeg': False,
        'postprocessors': [],
        'writesubtitles': False,
        'writeautomaticsub': False,
        'writethumbnail': False,
        'writedescription': False,
        'writeinfojson': False,
        'no_check_certificate': True,
        'prefer_insecure': True,
        'nocheckcertificate': True,
        'geo_bypass': True,
        'extractor_args': {
            'youtube': {
                'skip': ['dash', 'hls'],
                'player_client': ['android'],
            }
        },
    }
    
    start_time = time.time()
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            elapsed = time.time() - start_time
            
            if info and use_cache:
                # Cache the result
                video_info_cache[video_id] = info
                logger.info(f"Cached video info for {video_id}")
            
            return elapsed, info is not None
    except Exception as e:
        elapsed = time.time() - start_time
        logger.error(f"Error extracting {video_id}: {str(e)}")
        return elapsed, False

def test_connection_pooling():
    """Test connection pooling for faster requests"""
    session = requests.Session()
    
    # Test URLs
    test_urls = [
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://www.youtube.com/watch?v=kJQP7kiw5Fk",
        "https://www.youtube.com/watch?v=9bZkp7q19f0",
    ]
    
    logger.info("Testing connection pooling...")
    
    for url in test_urls:
        start_time = time.time()
        try:
            response = session.head(url, timeout=3)
            elapsed = time.time() - start_time
            logger.info(f"HEAD request to {url}: {elapsed:.2f}s")
        except Exception as e:
            logger.error(f"Error with {url}: {str(e)}")

def main():
    """Test advanced optimizations"""
    test_videos = [
        "dQw4w9WgXcQ",  # Rick Roll
        "kJQP7kiw5Fk",  # Despacito
        "9bZkp7q19f0",  # Gangnam Style
        "y6120QOlsfU",  # Sandstorm
        "ZZ5LpwO-An4",  # Never Gonna Give You Up
    ]
    
    logger.info("Testing advanced YouTube extraction optimizations...")
    
    # Test 1: First extraction (no cache)
    logger.info("\n=== Test 1: First extraction (no cache) ===")
    first_times = []
    for video_id in test_videos[:3]:
        elapsed, success = test_extraction_with_cache(video_id, use_cache=False)
        if success:
            first_times.append(elapsed)
            logger.info(f"First extraction for {video_id}: {elapsed:.2f}s")
    
    if first_times:
        avg_first = sum(first_times) / len(first_times)
        logger.info(f"Average first extraction time: {avg_first:.2f}s")
    
    # Test 2: Cached extraction
    logger.info("\n=== Test 2: Cached extraction ===")
    cached_times = []
    for video_id in test_videos[:3]:
        elapsed, success = test_extraction_with_cache(video_id, use_cache=True)
        if success:
            cached_times.append(elapsed)
            logger.info(f"Cached extraction for {video_id}: {elapsed:.2f}s")
    
    if cached_times:
        avg_cached = sum(cached_times) / len(cached_times)
        logger.info(f"Average cached extraction time: {avg_cached:.2f}s")
    
    # Test 3: Connection pooling
    logger.info("\n=== Test 3: Connection pooling ===")
    test_connection_pooling()
    
    # Summary
    if first_times and cached_times:
        improvement = ((avg_first - avg_cached) / avg_first) * 100
        logger.info(f"\n=== Summary ===")
        logger.info(f"First extraction average: {avg_first:.2f}s")
        logger.info(f"Cached extraction average: {avg_cached:.2f}s")
        logger.info(f"Performance improvement: {improvement:.1f}%")

if __name__ == "__main__":
    main()
