#!/bin/bash

# Script to check connected devices and identify client/server devices

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}Checking connected devices...${NC}"

# Get list of connected devices
DEVICES=$(adb devices | grep -v "List" | grep "device$" | cut -f1)

if [ -z "$DEVICES" ]; then
    echo -e "${RED}No devices connected!${NC}"
    exit 1
fi

echo -e "${GREEN}Found $(echo "$DEVICES" | wc -l | tr -d ' ') device(s)${NC}"

# Check each device
for DEVICE in $DEVICES; do
    echo -e "${YELLOW}Checking device: $DEVICE${NC}"
    
    # Try to get Android version
    ANDROID_VERSION=$(adb -s $DEVICE shell getprop ro.build.version.release 2>/dev/null)
    ANDROID_CHECK=$?
    
    # Try to get Linux version
    LINUX_VERSION=$(adb -s $DEVICE shell uname -a 2>/dev/null)
    LINUX_CHECK=$?
    
    # Check if it's an Android device (has getprop and doesn't have Linux kernel version)
    if [[ $ANDROID_CHECK -eq 0 && -n "$ANDROID_VERSION" && ! "$ANDROID_VERSION" =~ "not found" ]]; then
        echo -e "${GREEN}Device $DEVICE is an Android device (version $ANDROID_VERSION)${NC}"
        echo -e "${GREEN}This is a suitable CLIENT device for GPS2REST-Android${NC}"
        CLIENT_DEVICE=$DEVICE
    else
        # Check if it's a Linux device
        if [ $LINUX_CHECK -eq 0 ] && [ -n "$LINUX_VERSION" ]; then
            echo -e "${BLUE}Device $DEVICE is a Linux device: $LINUX_VERSION${NC}"
            echo -e "${BLUE}This is likely the SERVER device${NC}"
            SERVER_DEVICE=$DEVICE
        else
            echo -e "${RED}Device $DEVICE is of unknown type${NC}"
        fi
    fi
    
    echo ""
done

# Summary
echo -e "${YELLOW}Device Summary:${NC}"
if [ -n "$CLIENT_DEVICE" ]; then
    echo -e "${GREEN}Client Device: $CLIENT_DEVICE${NC}"
else
    echo -e "${RED}No client device found. Please connect an Android device.${NC}"
fi

if [ -n "$SERVER_DEVICE" ]; then
    echo -e "${BLUE}Server Device: $SERVER_DEVICE${NC}"
else
    echo -e "${RED}No server device found.${NC}"
fi

# Save device IDs to a file for other scripts to use
echo "CLIENT_DEVICE=$CLIENT_DEVICE" > device_ids.txt
echo "SERVER_DEVICE=$SERVER_DEVICE" >> device_ids.txt

echo -e "${GREEN}Device IDs saved to device_ids.txt${NC}"
