#!/bin/zsh
# Builds the launcher, installs it on your TV over Wi-Fi adb and turns on the Home button override.
# Usage: ./install.sh <tv-ip>[:5555]        Undo: ./uninstall.sh <tv-ip>
set -euo pipefail
cd "${0:A:h}"
[[ $# -ge 1 ]] || { echo "Usage: ./install.sh <tv-ip>   (e.g. ./install.sh 192.168.1.50)"; exit 1; }
TV="$1"; [[ "$TV" == *:* ]] || TV="$TV:5555"
ADB=$(command -v adb || echo "$HOME/platform-tools/adb")
[[ -x "$ADB" ]] || { echo "adb not found. Install Android platform-tools."; exit 1; }
SERVICE=com.vr.tvhome/com.vr.tvhome.HomeOverrideService

# Build the assistant's voice pack from voice/recorded if it hasn't been made yet
[[ -f assets/voice/index.txt ]] || ./make_voice.sh
./build.sh
"$ADB" connect "$TV" >/dev/null
if [[ $("$ADB" -s "$TV" get-state 2>/dev/null) != device ]]; then
  echo "Can't reach the TV at $TV. Is it on, with network debugging enabled? Press Allow on the TV if asked."
  exit 1
fi

"$ADB" -s "$TV" install -r build/tvhome.apk
# Lets the app re-enable its own Home override after a restart (see BootReceiver.java)
"$ADB" -s "$TV" shell pm grant com.vr.tvhome android.permission.WRITE_SECURE_SETTINGS
# Some TVs (e.g. TCL) block apps from starting in the background; the override needs it.
# Harmless on TVs that don't have this restriction.
"$ADB" -s "$TV" shell cmd appops set com.vr.tvhome APP_AUTO_START allow 2>/dev/null || true
# Note: this replaces any other accessibility services you had enabled.
"$ADB" -s "$TV" shell settings put secure enabled_accessibility_services "$SERVICE"
"$ADB" -s "$TV" shell settings put secure accessibility_enabled 1
# In-app animations need the animator scale on (some "speed up your TV" guides set it to 0)
"$ADB" -s "$TV" shell settings put global animator_duration_scale 1

# Installing closes the launcher and the Google TV home takes over; open ours again
"$ADB" -s "$TV" shell am start -n com.vr.tvhome/.MainActivity >/dev/null
echo "Done. The launcher is open on the TV; the Home button now opens it."
