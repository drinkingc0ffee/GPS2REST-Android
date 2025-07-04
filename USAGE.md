# RayHunter GPS Source - Usage Guide

## Overview
RayHunter GPS Source is an Android app that tracks GPS location at configurable intervals and sends the data to a REST endpoint.

## Fixed Issues

### 1. Network Connection Error
- **Problem**: The app was trying to connect to a default placeholder URL `http://192.168.1.1:8080/api/v1/gps/`
- **Solution**: Changed the default to `http://your-server.com:8080/api/v1/gps` and improved URL validation

### 2. Configuration Access
- **Problem**: You mentioned not having a configuration option
- **Solution**: The "Configure" button on the main screen opens the configuration activity

## How to Use

### 1. Configure the GPS Endpoint
1. Open the app
2. Tap the **"Configure"** button on the main screen
3. Enter your GPS endpoint URL in the format: `http://your-server.com:8080/api/v1/gps`
   - Replace `your-server.com` with your actual server address
   - Replace `8080` with your actual port number
   - The app will automatically append `/<latitude>,<longitude>` to this URL
4. Tap **"Save"** to save the configuration

### 2. Set GPS Update Frequency
- Use the slider on the main screen to set how often GPS data is sent
- Range: 10 seconds to 30 minutes
- The current frequency is displayed above the slider

### 3. Grant Permissions
The app will request the following permissions:
- **Location Permission**: Required for GPS tracking
- **Notification Permission**: For background service notifications (Android 13+)
- **Background Location**: For continuous tracking when app is in background
- **Battery Optimization**: Recommended to disable for reliable background operation

### 4. API Format
The app sends GPS data to your endpoint in this format:
```
GET http://your-server.com:8080/api/v1/gps/<latitude>,<longitude>
```

Example:
```
GET http://your-server.com:8080/api/v1/gps/51.5074,-0.1278
```

## Troubleshooting

### Network Errors
1. **Check your URL**: Make sure it's in the correct format with `http://` or `https://`
2. **Test connectivity**: Ensure your server is reachable from your device
3. **Check firewall**: Make sure your server accepts connections on the specified port
4. **Local testing**: For local testing, use your computer's IP address instead of `localhost`

### GPS Not Working
1. Make sure location permissions are granted
2. Enable GPS/Location services in Android settings
3. For background tracking, ensure "Allow all the time" is selected for location permission

### Background Operation
1. Disable battery optimization for the app when prompted
2. Check that the notification shows "GPS tracking active"
3. The app uses a foreground service to maintain background operation

## Current APK Location
The built APK is located at:
```
/Users/beisenmann/rayhunter-gps-android/app/build/outputs/apk/debug/app-debug.apk
```

## Package Name
```
com.rayhunter.gpssource
```
