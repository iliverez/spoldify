#!/bin/bash
# Publish the Spoldify Zeroconf service on the host's real network
# so the Spotify phone app can discover and pair with the emulator.
#
# Connection flow:
#   Phone → 192.168.88.17:48475 (socat) → 127.0.0.1:38475 (adb forward) → emulator:38475
#
# Prerequisites:
#   - Emulator is running (default networking is fine)
#   - App is installed and "Connect from Phone" has been tapped
#   - socat installed: sudo dnf install socat
#   - avahi-tools installed: sudo dnf install avahi-tools
#
# Usage: ./scripts/publish-zeroconf.sh
#
# Press Ctrl+C to stop publishing.

set -e

ADB="/home/iliverez/Android/Sdk/platform-tools/adb"
INTERNAL_PORT=38475
EXTERNAL_PORT=48475
SERVICE_NAME="Spoldify"
SERVICE_TYPE="_spotify-connect._tcp"
SOCAT_PID=""
AVAHI_PID=""

cleanup() {
    echo ""
    echo "Cleaning up..."
    kill $SOCAT_PID 2>/dev/null || true
    kill $AVAHI_PID 2>/dev/null || true
    $ADB forward --remove tcp:$INTERNAL_PORT 2>/dev/null || true
    echo "Done."
    exit 0
}

trap cleanup SIGINT SIGTERM EXIT

echo "=== Spoldify Zeroconf Publisher ==="
echo ""

# Check emulator is connected
if ! $ADB devices | grep -q "device$"; then
    echo "ERROR: No Android device/emulator connected via ADB"
    exit 1
fi

# Check dependencies
for cmd in avahi-publish-service socat; do
    if ! which $cmd &>/dev/null; then
        echo "ERROR: $cmd not found. Install: sudo dnf install $(echo $cmd | sed 's/avahi-publish-service/avahi-tools/')"
        exit 1
    fi
done

# Get host IP on the real network
HOST_IP=$(ip -4 addr show wlp0s20f3 2>/dev/null | grep -oP 'inet \K[\d.]+' || echo "")
if [ -z "$HOST_IP" ]; then
    echo "ERROR: Could not detect host IP on wlp0s20f3"
    exit 1
fi
echo "Host IP: $HOST_IP"

# Step 1: adb forward (localhost -> emulator)
echo "Setting up adb forward: localhost:$INTERNAL_PORT -> emulator:$INTERNAL_PORT ..."
$ADB forward tcp:$INTERNAL_PORT tcp:$INTERNAL_PORT
sleep 1

if ! $ADB forward --list | grep -q "tcp:$INTERNAL_PORT"; then
    echo "ERROR: adb forward failed"
    exit 1
fi
echo "adb forward active."

# Step 2: socat bridge (external IPv4+IPv6 -> localhost)
echo "Setting up socat bridge (dual-stack): [::]:$EXTERNAL_PORT -> localhost:$INTERNAL_PORT ..."
socat TCP6-LISTEN:$EXTERNAL_PORT,ipv6-v6only=0,reuseaddr,fork TCP4:127.0.0.1:$INTERNAL_PORT &
SOCAT_PID=$!
sleep 1

if ! kill -0 $SOCAT_PID 2>/dev/null; then
    echo "ERROR: socat failed to start"
    exit 1
fi
echo "socat bridge active."

# Step 3: publish mDNS
echo "Publishing mDNS service '$SERVICE_NAME' on $HOST_IP:$EXTERNAL_PORT ..."
echo ""
echo "Now open Spotify on your phone and tap the device/speaker icon."
echo "You should see '$SERVICE_NAME' in the device list."
echo ""
echo "Press Ctrl+C to stop."
echo ""

avahi-publish-service "$SERVICE_NAME" "$SERVICE_TYPE" "$EXTERNAL_PORT" \
    "CPath=/" "VERSION=1.0" "Stack=SP" &
AVAHI_PID=$!

wait $AVAHI_PID
