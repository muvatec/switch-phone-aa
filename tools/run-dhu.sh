#!/bin/bash
# Keeps the Android Auto Desktop Head Unit attached to the dongle (acts as the "car" on your computer).
# If the dongle is unplugged or the link drops, DHU exits and is relaunched.
#   DONGLE_SERIAL=<usb serial> tools/run-dhu.sh
# Find the serial with:  ioreg -p IOUSB -l | grep -i "USB Serial Number"   (macOS)
: "${DONGLE_SERIAL:?set DONGLE_SERIAL to the dongle's USB serial number}"
DHU_DIR="${DHU_DIR:-$HOME/Library/Android/sdk/extras/google/auto}"
cd "$DHU_DIR" || { echo "DHU not found in $DHU_DIR (install: sdkmanager 'extras;google;auto')"; exit 1; }
while true; do
  echo "[$(date '+%F %T')] starting DHU for $DONGLE_SERIAL"
  ./desktop-head-unit --usb="$DONGLE_SERIAL"
  echo "[$(date '+%F %T')] DHU exited, retrying in 3s"
  sleep 3
done
