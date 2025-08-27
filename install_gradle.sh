#!/bin/bash

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo "Options:"
    echo "  -c    Clear logs"
    echo "  -u    Uninstall app"
    echo "  -i    Install app (default if no options specified)"
    echo "  -a    All operations (clear logs, uninstall, install)"
    echo "  -d    Device ID (e.g., 21151FDF6007CJ)"
    echo "  -h    Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0              # Install app only"
    echo "  $0 -c           # Clear logs only"
    echo "  $0 -u           # Uninstall app only"
    echo "  $0 -u -i        # Uninstall and install app"
    echo "  $0 -a           # Clear logs, uninstall, and install app"
    echo "  $0 -d 21151FDF6007CJ -i  # Install app on specific device"
}

# Parse command line arguments
CLEAR_LOGS=false
UNINSTALL_APP=false
INSTALL_APP=false
SHOW_HELP=false
DEVICE_ID=""

while getopts "cuiahd:" opt; do
    case $opt in
        c)
            CLEAR_LOGS=true
            ;;
        u)
            UNINSTALL_APP=true
            ;;
        i)
            INSTALL_APP=true
            ;;
        a)
            CLEAR_LOGS=true
            UNINSTALL_APP=true
            INSTALL_APP=true
            ;;
        d)
            DEVICE_ID="$OPTARG"
            ;;
        h)
            SHOW_HELP=true
            ;;
        \?)
            echo "Invalid option: -$OPTARG" >&2
            show_usage
            exit 1
            ;;
        :)
            echo "Option -$OPTARG requires an argument." >&2
            show_usage
            exit 1
            ;;
    esac
done

if [ "$SHOW_HELP" = true ]; then
    show_usage
    exit 0
fi

# If no specific operations were requested, default to install
if [ "$CLEAR_LOGS" = false ] && [ "$UNINSTALL_APP" = false ] && [ "$INSTALL_APP" = false ]; then
    INSTALL_APP=true
fi

# Function to run adb command with optional device specification
run_adb() {
    if [ -n "$DEVICE_ID" ]; then
        adb -s "$DEVICE_ID" "$@"
    else
        adb "$@"
    fi
}

# Check if device is connected
if [ -n "$DEVICE_ID" ]; then
    # Check if specified device is connected
    if ! adb devices | grep -q "$DEVICE_ID.*device$"; then
        echo -e "\033[0;31mError: Device '$DEVICE_ID' is not connected or not authorized\033[m"
        echo "Available devices:"
        adb devices
        exit 1
    fi
    echo -e "\033[0;32mUsing device: $DEVICE_ID\033[m"
else
    # Check if any device is connected
    if ! adb devices | grep -q "device$"; then
        echo -e "\033[0;31mError: No Android device/emulator connected\033[m"
        echo "Please connect a device or start an emulator and try again."
        echo "Available devices:"
        adb devices
        exit 1
    fi
fi

# Execute requested operations
if [ "$CLEAR_LOGS" = true ]; then
    echo -e "\033[0;32m========================\nClearing logs\n========================\n\033[m"
    run_adb logcat -c && echo "Logs cleared successfully"
    echo ""
fi

if [ "$UNINSTALL_APP" = true ]; then
    echo -e "\033[0;32m========================\nUninstalling GPS2REST\n========================\n\033[m"
    
    # Use Gradle to uninstall
    if [ -n "$DEVICE_ID" ]; then
        ./gradlew uninstallDebug -PtargetDeviceId="$DEVICE_ID"
    else
        ./gradlew uninstallDebug
    fi
    
    echo "App uninstalled successfully"
    echo ""
fi

if [ "$INSTALL_APP" = true ]; then
    echo -e "\033[0;32m========================\nInstalling GPS2REST\n========================\n\033[m"
    
    # Use Gradle to build and install
    if [ -n "$DEVICE_ID" ]; then
        ./gradlew installDebug -PtargetDeviceId="$DEVICE_ID"
    else
        ./gradlew installDebug
    fi
    
    echo "App installed successfully"
    echo ""
fi

echo -e "\033[0;32m========================\nOperation completed\033[m"

# Launch the app
if [ "$INSTALL_APP" = true ]; then
    echo -e "\033[0;32m========================\nLaunching GPS2REST\n========================\n\033[m"
    run_adb shell am start -n com.coffeebreak.gps2rest/.MainActivity
    echo "App launched"
fi
