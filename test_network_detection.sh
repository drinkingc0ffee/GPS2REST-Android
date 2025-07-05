#!/bin/bash

# Test script for network detection logic
# This simulates the private IP address detection that the app uses

echo "Testing Private IP Address Detection"
echo "===================================="

# Function to check if an IP is private (matching the app's logic)
is_private_ip() {
    local ip=$1
    
    if [[ $ip =~ ^192\.168\. ]] || 
       [[ $ip =~ ^10\. ]] || 
       [[ $ip =~ ^172\.(1[6-9]|2[0-9]|3[0-1])\. ]] || 
       [[ $ip =~ ^127\. ]] || 
       [[ $ip == "localhost" ]]; then
        return 0  # Is private
    else
        return 1  # Is public
    fi
}

# Test cases
test_ips=(
    "192.168.1.1"
    "192.168.1.100"
    "10.0.0.1"
    "172.16.0.1"
    "127.0.0.1"
    "localhost"
    "8.8.8.8"
    "google.com"
    "github.com"
    "1.1.1.1"
)

echo "Testing IP addresses:"
echo "--------------------"

for ip in "${test_ips[@]}"; do
    if is_private_ip "$ip"; then
        echo "✓ $ip -> PRIVATE (will use WiFi)"
    else
        echo "✗ $ip -> PUBLIC (will use default)"
    fi
done

echo ""
echo "Network Status:"
echo "---------------"

# Show current network information
if command -v ifconfig &> /dev/null; then
    echo "WiFi Interface (en0):"
    ifconfig en0 | grep "inet " | awk '{print "IP: " $2}'
    
    echo ""
    echo "All network interfaces:"
    ifconfig | grep "inet " | grep -v "127.0.0.1" | awk '{print $2}'
elif command -v ip &> /dev/null; then
    echo "Network interfaces:"
    ip addr show | grep "inet " | grep -v "127.0.0.1"
fi

echo ""
echo "Current route to 192.168.1.1:"
if command -v route &> /dev/null; then
    route -n get 192.168.1.1 2>/dev/null || echo "No route found"
elif command -v ip &> /dev/null; then
    ip route get 192.168.1.1 2>/dev/null || echo "No route found"
fi

echo ""
echo "The app will:"
echo "- Use WiFi network for private IPs (192.168.x.x, 10.x.x.x, etc.)"
echo "- Use default network for public IPs (8.8.8.8, google.com, etc.)"
echo "- This should work even with cellular data enabled" 