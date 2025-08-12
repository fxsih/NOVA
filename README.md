NOVA – Free Music Streaming App

NOVA is a modern Android music streaming application developed as a BCA mini-project. Built with Kotlin (Jetpack Compose) for the frontend and a Python FastAPI backend, it delivers a premium-level music experience with YouTube Music integration, Firebase synchronization, and offline playback — all free of cost.

📌 Features

🎵 Unlimited Streaming – High-quality audio without ads or subscription fees.

📥 Offline Downloads – Save songs locally for offline playback.

🔍 Advanced Search – Search by songs, artists, albums with filters & history.

📂 Playlist Management – Create, edit, share, and sync playlists in real-time.

🎧 Background Playback – ExoPlayer integration with media controls & mini player.

🌗 Material 3 UI – Adaptive light/dark themes, smooth animations.

☁ Cloud Sync – Firebase Firestore for playlists, preferences, and history.

🔐 Privacy-First – Minimal data collection, secure authentication.

⚡ Optimized Performance – Sub-2s startup, sub-1s search response.


🛠 Tech Stack

Frontend (Android)

Language: Kotlin (95%), Java (5%)

UI: Jetpack Compose (Material 3)

Architecture: MVVM

Media: ExoPlayer

Networking: Retrofit

Local Storage: Room Database


Backend

Framework: FastAPI (Python 3.8+)

Database: SQLite (local dev)

APIs: YouTube Music integration

Hosting: Localhost (for project demo)


Cloud Services

Firebase Authentication

Firebase Firestore

Firebase Storage

Firebase Analytics & Crashlytics


📱 Requirements

Android 6.0 (API 23) or higher

Minimum 2GB RAM device

Internet connection for streaming (offline mode available)


🚀 Installation & Setup

1. Clone the repository

git clone https://github.com/fxsih/NOVA-Music-App.git


2. Backend Setup

Install Python dependencies:

pip install -r requirements.txt

Run FastAPI server (localhost):

uvicorn main:app --reload



3. Frontend Setup

Open Nova Android project in Android Studio.

Update backend API URL in configuration.

Build & run on an emulator or physical device.




📊 Performance Metrics

Startup Time: < 2 seconds

Search Response: < 1 second

Crash Rate: 0% (v1.0.0)


📅 Future Enhancements

AI-powered voice search

iOS & Web versions

Wearable device integration

Social sharing & collaborative playlists


👨‍💻 Author

Faseeh Jaleel - faseehj005@gmail.com
