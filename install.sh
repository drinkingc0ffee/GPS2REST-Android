#!/bin/bash

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo "Options:"
    echo "  -c    Clear logs"
    echo "  -u    Uninstall app"
    echo "  -i    Install app (default if no options specified)"
    echo "  -a    All operations (clear logs, uninstall, install)"
    echo "  -d    Device name (e.g., emulator-5554, device-id)"
    echo "  -h    Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0              # Install app only"
    echo "  $0 -c           # Clear logs only"
    echo "  $0 -u           # Uninstall app only"
    echo "  $0 -c -u        # Clear logs and uninstall app"
    echo "  $0 -u -i        # Uninstall and install app"
    echo "  $0 -a           # Clear logs, uninstall, and install app"
    echo "  $0 -c -u -i     # Same as -a"
    echo "  $0 -d emulator-5554 -i  # Install app on specific device"
    echo "  $0 -d device-id -a      # All operations on specific device"
}

# Parse command line arguments
CLEAR_LOGS=false
UNINSTALL_APP=false
INSTALL_APP=false
SHOW_HELP=false
DEVICE_NAME=""

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
            DEVICE_NAME="$OPTARG"
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
    if [ -n "$DEVICE_NAME" ]; then
        adb -s "$DEVICE_NAME" "$@"
    else
        adb "$@"
    fi
}

# Check if device is connected
if [ -n "$DEVICE_NAME" ]; then
    # Check if specified device is connected
    if ! adb devices | grep -q "$DEVICE_NAME.*device$"; then
        echo -e "\033[0;31mError: Device '$DEVICE_NAME' is not connected or not authorized\033[m"
        echo "Available devices:"
        adb devices
        exit 1
    fi
    echo -e "\033[0;32mUsing device: $DEVICE_NAME\033[m"
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
    run_adb shell pm uninstall com.coffeebreak.gps2rest
    echo "App uninstalled successfully"
    echo ""
fi

if [ "$INSTALL_APP" = true ]; then
    echo -e "\033[0;32m========================\nInstalling GPS2REST\n========================\n\033[m"
    if [ -f "./app/build/outputs/apk/debug/app-debug.apk" ]; then
        run_adb install -r ./app/build/outputs/apk/debug/app-debug.apk
        echo "App installed successfully"
    else
        echo -e "\033[0;31mError: APK file not found\033[m"
        echo "Please build the project first using ./build.sh"
        exit 1
    fi
    echo ""
fi

echo -e "\033[0;32m========================\nOperation completed\033[m"
