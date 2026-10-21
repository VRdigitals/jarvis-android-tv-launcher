# Setting up the Jarvis Android TV Launcher (instructions for Claude Code)

When the user asks to set up, install, update or customise the launcher on their TV, guide them
through it step by step. They may not be technical: explain each step in one plain sentence,
ask before installing anything on their Mac, and never ask them for passwords or keys.

## 1. Check the Mac
- `adb`: `command -v adb || ls ~/platform-tools/adb`. If missing, offer Homebrew
  (`brew install --cask android-platform-tools`) or Google's official zip from
  https://dl.google.com/android/repository/platform-tools-latest-darwin.zip unpacked to `~/platform-tools`.
- JDK 17: `/usr/libexec/java_home -v 17` or `~/jdk-17/bin/javac`. If missing, offer Eclipse
  Temurin 17 from adoptium.net (verify the SHA-256 against Adoptium's published checksum).
- Android SDK with `platforms/android-34` and `build-tools/34.*` in `$ANDROID_HOME` or
  `~/Library/Android/sdk`. If missing, offer the command-line tools and
  `sdkmanager "platforms;android-34" "build-tools;34.0.0"`.

## 2. Prepare the TV
Ask the user to do this with the remote (you can't):
1. Settings → System → About → press OK on "Android TV OS build" 7 times (developer mode).
2. Settings → System → Developer options → turn on **USB debugging** (and **Network debugging** if shown).
3. TV and Mac on the same Wi-Fi.

## 3. Find and connect to the TV
- Run `./find_tv.sh`. If it lists more than one, ask which TV; if none, ask for the IP
  (Settings → Network & Internet → their Wi-Fi) and check step 2.
- `adb connect <ip>:5555`, and tell the user to **press Allow on the TV** (tick "Always allow").
  Confirm with `adb -s <ip>:5555 get-state` returning `device`.

## 4. Personalise (ask; all optional)
- Weather city → set `WEATHER_CITY`, `WEATHER_LAT`, `WEATHER_LON` in `src/com/vr/tvhome/Config.java`.
- Favourite apps → list installed apps with `adb -s <tv> shell pm list packages -3` and update
  `FEATURED` in `Config.java` (keep 4–6).
- Wallpaper → `assets/wallpaper.jpg` (1920×1080). Avatar → `assets/arc_reactor.png` (included).
- Voice → an AI voice pack is included in `voice/recorded/`. They can replace files with their own
  recordings (`<key>.mp3`, names from `voice/lines.txt`) or fill gaps with a macOS voice:
  `VOICE=Daniel ./make_voice.sh`. Don't help clone a real person's voice.
- Intro → replace `res/raw/intro.mp4` (convert to 1080p H.264 with
  `avconvert --preset Preset1920x1080` if needed).

## 5. Build and install
- `./make_voice.sh` (if the voice changed), then `./install.sh <ip>`.
- The signing key is created automatically on the first build in `keystore/` (git-ignored).
  Updates must be signed with the same key: tell the user to keep that folder. If they lost it,
  they must `./uninstall.sh <ip>` before installing again.
- `install.sh` replaces any other enabled accessibility services on the TV; mention this first.
- Ask the user to press Home on the remote to confirm the launcher opens.

## Troubleshooting
- Home goes to Google TV home: check `adb shell dumpsys accessibility | grep -c "label=TV Home"`
  is 1; re-run `./install.sh`. On TCL, `cmd appops get com.vr.tvhome APP_AUTO_START` must be `allow`.
- No intro after power-on: check the accessibility service is bound (above).
- Laggy animations: `adb shell settings get global animator_duration_scale` must not be 0.
- Never disable `com.google.android.apps.tv.launcherx`: Google's setup app then loops on Home.

## Undo
`./uninstall.sh <ip>` turns off the Home override and removes the app.
