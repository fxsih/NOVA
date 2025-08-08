#!/usr/bin/env python3
"""
Test script to measure YouTube extraction performance improvements
"""
import time
import yt_dlp
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def test_extraction_speed(video_id, use_optimized=True):
    """Test extraction speed with different configurations"""
    url = f"https://www.youtube.com/watch?v={video_id}"
    
    if use_optimized:
        ydl_opts = {
            'format': 'bestaudio/best',
            'quiet': True,
            'no_warnings': True,
            'noplaylist': True,
            'skip_download': True,
            'socket_timeout': 5,  # Even faster timeout
            'retries': 0,  # No retries for maximum speed
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
            # Skip unnecessary data extraction
            'writesubtitles': False,
            'writeautomaticsub': False,
            'writethumbnail': False,
            'writedescription': False,
            'writeinfojson': False,
            # Additional speed optimizations
            'no_check_certificate': True,  # Skip SSL verification for speed
            'prefer_insecure': True,  # Prefer HTTP over HTTPS when possible
            'nocheckcertificate': True,  # Alternative SSL skip
            'geo_bypass': True,  # Skip geo-restriction checks
            'extractor_args': {
                'youtube': {
                    'skip': ['dash', 'hls'],  # Skip DASH and HLS formats
                    'player_client': ['android'],  # Use Android client only
                }
            },
        }
    else:
        ydl_opts = {
            'format': 'bestaudio/best',
            'quiet': False,
            'no_warnings': False,
            'noplaylist': True,
            'skip_download': True,
            'socket_timeout': 15,
        }
    
    start_time = time.time()
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            elapsed = time.time() - start_time
            logger.info(f"{'Optimized' if use_optimized else 'Standard'} extraction for {video_id}: {elapsed:.2f}s")
            return elapsed, info is not None
    except Exception as e:
        elapsed = time.time() - start_time
        logger.error(f"Error extracting {video_id}: {str(e)}")
        return elapsed, False

def main():
    """Test extraction speed with popular videos"""
    test_videos = [
        "dQw4w9WgXcQ",  # Rick Roll
        "kJQP7kiw5Fk",  # Despacito
        "9bZkp7q19f0",  # Gangnam Style
    ]
    
    logger.info("Testing YouTube extraction performance...")
    
    for video_id in test_videos:
        logger.info(f"\nTesting video: {video_id}")
        
        # Test standard extraction
        std_time, std_success = test_extraction_speed(video_id, use_optimized=False)
        
        # Wait a bit between tests
        time.sleep(2)
        
        # Test optimized extraction
        opt_time, opt_success = test_extraction_speed(video_id, use_optimized=True)
        
        if std_success and opt_success:
            improvement = ((std_time - opt_time) / std_time) * 100
            logger.info(f"Performance improvement: {improvement:.1f}%")
        else:
            logger.warning("One or both extractions failed")

if __name__ == "__main__":
    main()
