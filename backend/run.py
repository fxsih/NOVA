#!/usr/bin/env python
"""
NOVA Music API Server Launcher
This script updates yt-dlp to the latest version before starting the server
"""
import subprocess
import sys
import os
import signal
import time
from update_ytdlp import update_ytdlp

def handle_signal(sig, frame):
    """Handle termination signals gracefully"""
    print("\nShutting down NOVA Music API Server...")
    sys.exit(0)

def main():
    """Main function to run the server with yt-dlp updates"""
    print("=== NOVA Music API Server Launcher ===")
    
    # Set up signal handlers for graceful shutdown
    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)
    
    # Quick dependency check
    print("\nStep 1: Checking dependencies...")
    try:
        import fastapi
        import yt_dlp
        import ytmusicapi
        import requests
        import cachetools
        print("✅ All dependencies available")
    except ImportError as e:
        print(f"❌ Missing dependency: {e}")
        return 1
    
    # Update yt-dlp first
    print("\nStep 2: Updating yt-dlp to the latest version...")
    update_ytdlp()
    
    # Start the server
    print("\nStep 3: Starting NOVA Music API Server...")
    try:
        # Use this script's directory as the working directory
        script_dir = os.path.dirname(os.path.abspath(__file__))
        os.chdir(script_dir)
        
        # Run the main.py module with subprocess and optimized settings
        server_process = subprocess.Popen([
            sys.executable, 
            "main.py",
            "--fast"  # Use fast startup mode
        ], 
        stdout=None,  # Show output in real-time
        stderr=None,  # Show errors in real-time
        text=True
        )
        
        print("Server started successfully!")
        print("Press Ctrl+C to stop the server...")
        
        # Give the server a moment to start up
        time.sleep(2)
        
        # Check if server started successfully
        if server_process.poll() is not None:
            print("Server failed to start!")
            return server_process.returncode
        
        print("✅ NOVA Music API Server is running on http://0.0.0.0:8000")
        print("   Accessible via:")
        print("   • http://127.0.0.1:8000 (localhost)")
        print("   • http://192.168.29.154:8000 (local network)")
        
        # Test server connectivity
        try:
            import requests
            # Test localhost
            response = requests.get("http://127.0.0.1:8000/", timeout=5)
            if response.status_code == 200:
                print("✅ Server is responding to localhost requests")
            else:
                print(f"⚠️ Server responded with status code: {response.status_code}")
                
            # Test network IP
            response = requests.get("http://192.168.29.154:8000/", timeout=5)
            if response.status_code == 200:
                print("✅ Server is responding to network requests")
            else:
                print(f"⚠️ Network request failed with status code: {response.status_code}")
                
        except Exception as e:
            print(f"⚠️ Could not test server connectivity: {e}")
        
        # Wait for the server process to complete
        while server_process.poll() is None:
            time.sleep(1)
            
        # Check if the server exited with an error
        if server_process.returncode != 0:
            print(f"Server exited with error code {server_process.returncode}")
            return server_process.returncode
            
    except KeyboardInterrupt:
        print("\nReceived keyboard interrupt. Shutting down gracefully...")
        if 'server_process' in locals() and server_process.poll() is None:
            server_process.terminate()
            try:
                server_process.wait(timeout=10)  # Longer timeout for graceful shutdown
            except subprocess.TimeoutExpired:
                print("Force killing server process...")
                server_process.kill()
                server_process.wait()
    except Exception as e:
        print(f"Error starting server: {str(e)}")
        return 1
        
    return 0

if __name__ == "__main__":
    sys.exit(main()) 