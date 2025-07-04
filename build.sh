#!/bin/bash

# Get the current highest log number
LAST_LOG=$(ls -1 *.log 2>/dev/null | grep -E '^[0-9]+\.log$' | sort -n | tail -1)

if [ -z "$LAST_LOG" ]; then
  # No logs found, start at 91
  NEXT_LOG="91"
else
  # Extract the number and increment it
  CURRENT_NUM=$(echo $LAST_LOG | cut -d '.' -f 1)
  NEXT_LOG=$((CURRENT_NUM + 1))
fi

# Default to build command if no args provided
BUILD_CMD="${@:-assembleDebug --info --stacktrace}"

# Create log file name
LOG_FILE="${NEXT_LOG}.log"

# Check if changes have been made since the last build
CHANGES_FOUND=0

# Check for changes in git-tracked files
if command -v git &> /dev/null && git rev-parse --is-inside-work-tree &> /dev/null; then
  GIT_CHANGES=$(git status --porcelain 2>/dev/null)
  if [ -n "$GIT_CHANGES" ]; then
    CHANGES_FOUND=1
    echo "Changes detected in git-tracked files."
  fi
fi

# Also check for any file changes in the last 5 minutes in case we're not using git
# or changes are in non-tracked files
RECENT_CHANGES=$(find . -type f -not -path "*/\.*" -not -path "*/build/*" -mmin -5 | grep -v "\.log$" | grep -v "c37import.txt")
if [ -n "$RECENT_CHANGES" ]; then
  CHANGES_FOUND=1
  echo "Recent file changes detected in the project."
fi

# Check if this is a special command like 'projects' or 'tasks' that doesn't need a build
SPECIAL_CMD=0
if [[ "$BUILD_CMD" == "projects" || "$BUILD_CMD" == "tasks" || "$BUILD_CMD" == "-v" || "$BUILD_CMD" == "--version" || "$BUILD_CMD" == "help" ]]; then
  SPECIAL_CMD=1
fi

# If it's a force build or there are changes or it's a special command
if [[ "$1" == "--force" || $CHANGES_FOUND -eq 1 || $SPECIAL_CMD -eq 1 ]]; then
  # Remove --force from args if present
  if [[ "$1" == "--force" ]]; then
    shift
    BUILD_CMD="${@:-assembleDebug --info --stacktrace}"
  fi
  
  echo "Building with log file: $LOG_FILE"
  
  # Run gradlew with the specified arguments
  # --info and --stacktrace are now configured in gradle.properties
  CMD="./gradlew ${BUILD_CMD}"
  echo "Executing: $CMD"
  
  # Run the command and tee the output to both the console and a log file
  # Ensure the exit code reflects command failures in pipelines
  set -o pipefail
  $CMD 2>&1 | tee "$LOG_FILE"
  BUILD_RESULT=$?
  set +o pipefail  # Reset pipefail to default
  
  # Check if the build was successful
  if [ $BUILD_RESULT -eq 0 ]; then
    echo -e "\n--- Build successful ---" | tee -a "$LOG_FILE"
    # Add the timestamp to the log
    echo "Build completed at $(date)" >> "$LOG_FILE"
    echo "Build log saved to: $LOG_FILE"
  else
    echo -e "\n--- Build failed ---" | tee -a "$LOG_FILE"
    # Add the timestamp to the log
    echo "Build failed at $(date)" >> "$LOG_FILE"
    echo "Build log saved to: $LOG_FILE"
  fi
  
  # Append build information to c37import.txt
  echo -e "\n=== Build #${NEXT_LOG} ($(date)) ===" >> c37import.txt
  echo "Command: $CMD" >> c37import.txt
  echo "See ${LOG_FILE} for complete build output" >> c37import.txt
  
  # Always echo the log filename at the end
  echo -e "\n📄 Log file: $LOG_FILE"
  
  exit $BUILD_RESULT
else
  echo "No changes detected since last build."
  echo "Using most recent log file: $LAST_LOG"
  echo "To force a build, use: ./build.sh --force $BUILD_CMD"
  
  # If user wants to see the last log, show it
  read -p "Do you want to see the most recent build log? (y/n) " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    cat "$LAST_LOG"
  fi
  
  # Always echo the log filename at the end
  echo -e "\n📄 Log file: $LAST_LOG"
  
  exit 0
fi
