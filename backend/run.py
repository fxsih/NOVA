#!/usr/bin/env python
"""
NOVA Music API Server Launcher
This script updates yt-dlp to the latest version before starting the server
"""
import subprocess
import sys
import os
from update_ytdlp import update_ytdlp

def main():
    """Main function to run the server with yt-dlp updates"""
    print("=== NOVA Music API Server Launcher ===")
    
    # Update yt-dlp first
    print("\nStep 1: Updating yt-dlp to the latest version...")
    update_ytdlp()
    
    # Start the server
    print("\nStep 2: Starting NOVA Music API Server...")
    try:
        # Use this script's directory as the working directory
        script_dir = os.path.dirname(os.path.abspath(__file__))
        os.chdir(script_dir)
        
        # Run the main.py module
        subprocess.run([sys.executable, "main.py"], check=True)
    except Exception as e:
        print(f"Error starting server: {str(e)}")
        sys.exit(1)

if __name__ == "__main__":
    main() 