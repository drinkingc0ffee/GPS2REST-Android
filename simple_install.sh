#!/bin/bash

# Simple installation script for GPS2REST-Android
# This script uses direct ADB commands for better compatibility

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

# Load device IDs from file
if [ -f "device_ids.txt" ]; then
    source device_ids.txt
fi

# Use the client device ID if available
if [ -n "$CLIENT_DEVICE" ]; then
    DEVICE_ID="$CLIENT_DEVICE"
    echo -e "${GREEN}Using client device: $DEVICE_ID${NC}"
else
    # Check if any device is connected
    if ! adb devices | grep -q "device$"; then
        echo -e "${RED}Error: No Android device connected${NC}"
        echo "Please connect a device and try again."
        echo "Available devices:"
        adb devices
        exit 1
    fi
fi

# Build the APK
echo -e "${GREEN}Building GPS2REST-Android...${NC}"
./gradlew assembleDebug

# Check if build was successful
if [ ! -f "./app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo -e "${RED}Error: Build failed, APK not found${NC}"
    exit 1
fi

# Uninstall existing app
echo -e "${YELLOW}Uninstalling existing app...${NC}"
if [ -n "$DEVICE_ID" ]; then
    adb -s $DEVICE_ID uninstall com.coffeebreak.gps2rest
else
    adb uninstall com.coffeebreak.gps2rest
fi

# Install the new APK
echo -e "${GREEN}Installing GPS2REST-Android...${NC}"
if [ -n "$DEVICE_ID" ]; then
    adb -s $DEVICE_ID install -r ./app/build/outputs/apk/debug/app-debug.apk
else
    adb install -r ./app/build/outputs/apk/debug/app-debug.apk
fi

# Check if installation was successful
if [ $? -eq 0 ]; then
    echo -e "${GREEN}Installation successful!${NC}"
    
    # Launch the app
    echo -e "${GREEN}Launching GPS2REST-Android...${NC}"
    if [ -n "$DEVICE_ID" ]; then
        adb -s $DEVICE_ID shell am start -n com.coffeebreak.gps2rest/.MainActivity
    else
        adb shell am start -n com.coffeebreak.gps2rest/.MainActivity
    fi
else
    echo -e "${RED}Installation failed!${NC}"
    exit 1
fi

echo -e "${GREEN}Done!${NC}"
