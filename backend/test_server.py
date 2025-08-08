#!/usr/bin/env python3
"""
Simple test to verify the server can start without syntax errors
"""
import sys
import os

def test_imports():
    """Test that all imports work correctly"""
    try:
        # Test basic imports
        import fastapi
        import yt_dlp
        import ytmusicapi
        import requests
        import cachetools
        print("✅ All basic imports successful")
        
        # Test main module import
        import main
        print("✅ Main module import successful")
        
        # Test specific functions
        from main import extract_video_info_fast, get_or_cache_audio_url
        print("✅ Function imports successful")
        
        return True
    except Exception as e:
        print(f"❌ Import error: {str(e)}")
        return False

def test_syntax():
    """Test that the main.py file has valid syntax"""
    try:
        with open('main.py', 'r', encoding='utf-8') as f:
            source = f.read()
        
        # Try to compile the source
        compile(source, 'main.py', 'exec')
        print("✅ Syntax check passed")
        return True
    except SyntaxError as e:
        print(f"❌ Syntax error: {str(e)}")
        return False
    except Exception as e:
        print(f"❌ Error: {str(e)}")
        return False

if __name__ == "__main__":
    print("Testing NOVA Music API server...")
    
    # Change to backend directory
    if os.path.exists('main.py'):
        os.chdir('.')
    elif os.path.exists('backend/main.py'):
        os.chdir('backend')
    else:
        print("❌ Could not find main.py")
        sys.exit(1)
    
    # Run tests
    syntax_ok = test_syntax()
    imports_ok = test_imports()
    
    if syntax_ok and imports_ok:
        print("🎉 All tests passed! Server should start successfully.")
    else:
        print("❌ Tests failed. Please fix the issues above.")
        sys.exit(1)
