# Dynamic Base URL Feature Guide

## Overview

The NOVA music app now supports dynamic base URL configuration, allowing you to change the API server URL without rebuilding the app. This is particularly useful when switching between different networks (home WiFi, college WiFi, etc.).

## How to Use

### 1. Access the Settings
1. Open the NOVA app
2. Navigate to the **Profile** screen
3. Tap on **"Server URL"** in the Account Settings section

### 2. Change the Server URL
1. Enter the new server URL in the text field
2. Examples:
   - `http://192.168.1.100:8000/` (Home WiFi)
   - `http://10.0.0.50:8000/` (College WiFi)
   - `http://localhost:8000/` (Same device)
   - `http://192.168.29.154:8000/` (Default)

### 3. Save and Restart
1. Tap **"Save & Restart"** to apply the changes
2. The app will restart automatically to use the new server URL

## Technical Implementation

### Components Added

1. **DynamicBaseUrlInterceptor** (`app/src/main/java/com/nova/music/util/DynamicBaseUrlInterceptor.kt`)
   - Intercepts HTTP requests and dynamically changes the base URL
   - Uses the URL stored in SharedPreferences

2. **Updated AppModule** (`app/src/main/java/com/nova/music/di/AppModule.kt`)
   - Modified OkHttpClient to include the dynamic base URL interceptor
   - Updated Retrofit to use the dynamic base URL from PreferenceManager

3. **ProfileScreen Enhancement** (`app/src/main/java/com/nova/music/ui/screens/profile/ProfileScreen.kt`)
   - Added "Server URL" option in Account Settings
   - Implemented dialog for changing the base URL
   - Added restart functionality to apply changes

### How It Works

1. **URL Storage**: The base URL is stored in SharedPreferences using `PreferenceManager`
2. **Dynamic Interception**: Each API request is intercepted and the base URL is replaced dynamically
3. **Runtime Changes**: The URL can be changed at runtime without rebuilding the app
4. **App Restart**: The app restarts after URL changes to ensure all components use the new URL

## Benefits

- ✅ **No Rebuild Required**: Change server URL without rebuilding the app
- ✅ **Network Flexibility**: Easy switching between home and college networks
- ✅ **User-Friendly**: Simple UI for changing the URL
- ✅ **Persistent**: URL changes are saved and persist across app restarts
- ✅ **Safe**: Includes validation and restart confirmation

## Troubleshooting

### Common Issues

1. **Connection Failed**
   - Verify the server is running on the specified IP and port
   - Check if the IP address is correct for your current network
   - Ensure the server is accessible from your device

2. **App Not Connecting**
   - Try restarting the app after changing the URL
   - Verify the URL format (should include `http://` and port number)
   - Check if the server is running on the backend

3. **URL Not Saving**
   - Make sure to tap "Save & Restart" after entering the new URL
   - The app will restart automatically to apply changes

### Network Examples

**Home Network:**
```
http://192.168.1.100:8000/
```

**College Network:**
```
http://10.0.0.50:8000/
```

**Same Device (if running locally):**
```
http://localhost:8000/
```

## Development Notes

- The feature uses OkHttp interceptors for dynamic URL switching
- URL changes require an app restart to ensure all components are updated
- The default URL is `http://192.168.29.154:8000/`
- All API requests will use the dynamically set base URL 