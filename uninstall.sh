#!/bin/zsh
# Turns off the Home button override and removes the launcher. Your Google TV home is untouched,
# so the Home button simply goes back to it.
# Usage: ./uninstall.sh <tv-ip>[:5555]
set -euo pipefail
[[ $# -ge 1 ]] || { echo "Usage: ./uninstall.sh <tv-ip>"; exit 1; }
TV="$1"; [[ "$TV" == *:* ]] || TV="$TV:5555"
ADB=$(command -v adb || echo "$HOME/platform-tools/adb")
"$ADB" connect "$TV" >/dev/null
"$ADB" -s "$TV" shell settings delete secure enabled_accessibility_services
"$ADB" -s "$TV" shell settings put secure accessibility_enabled 0
"$ADB" -s "$TV" uninstall com.vr.tvhome
echo "Removed. Press Home on the remote to get the Google TV home back."
