#!/bin/bash

# Test script for GPS2REST endpoint
# Usage: ./test_endpoint.sh [your_ip_address] [port] [path]

IP=${1:-"192.168.1.1"}
PORT=${2:-"8080"}
PATH=${3:-"/api/v1/gps"}

URL="http://$IP:$PORT$PATH"

echo "Testing GPS2REST endpoint..."
echo "URL: $URL"
echo "Method: POST"
echo "Data: Sample GPS coordinates"
echo "----------------------------------------"

# Test with sample coordinates (San Francisco)
SAMPLE_COORDS="37.7749,-122.4194"
FULL_URL="$URL/$SAMPLE_COORDS"

echo "Testing: $FULL_URL"

# Test if the server is reachable
if curl -s --connect-timeout 5 "$FULL_URL" > /dev/null 2>&1; then
    echo "✓ Server is reachable"
    
    # Test with POST request
    echo "Sending POST request..."
    RESPONSE=$(curl -s -X POST "$FULL_URL" 2>&1)
    
    if [ $? -eq 0 ]; then
        echo "✓ POST request successful"
        echo "Response: $RESPONSE"
    else
        echo "✗ POST request failed"
        echo "Error: $RESPONSE"
    fi
else
    echo "✗ Server is not reachable at $FULL_URL"
    echo "Common issues:"
    echo "  - Wrong IP address (check your router's IP)"
    echo "  - Server not running on port $PORT"
    echo "  - Firewall blocking the connection"
    echo "  - Not connected to the same WiFi network"
fi

echo "----------------------------------------"
echo "To test your own endpoint:"
echo "./test_endpoint.sh YOUR_IP YOUR_PORT YOUR_PATH"
echo "Example: ./test_endpoint.sh 192.168.1.100 3000 /location" 