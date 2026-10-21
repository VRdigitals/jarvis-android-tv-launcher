# Jarvis Android TV Launcher

A free, ad-free, J.A.R.V.I.S.-inspired home screen for **Google TV / Android TV**, with a
scripted assistant that reacts to what you do, a cinematic start-up intro, and a 3D blue glow
when you select something. No ads, no "recommended for you" rows, no tracking.

> Fan-made project. Not affiliated with or endorsed by Marvel, Disney, Google or any TV maker.
> No Marvel artwork, audio or video is included.

## Features

- **Home screen**: clock and date, live weather, Featured app cards, an Apps panel with an
  All-apps page, and an Inputs panel (TV, HDMI, AV, USB).
- **Assistant card**: an arc-reactor avatar with a speech bubble that types out its lines.
  It's scripted, not a real AI. It:
  - comments when you rest on an app ("Good choice, Prime…");
  - speaks before switching inputs ("Switching to HDMI. Going for gaming, huh?");
  - suggests something after 15 seconds of no button presses (press OK on the card to open it);
  - holding OK on the card mutes it.
- **Start-up intro**: plays when you turn the TV on (and after a restart). Any button skips it.
  If you use a Bluetooth speaker, a pulsing "Initialising…" screen shows (up to 5 s) while it
  reconnects, so the intro's sound isn't cut off.
- **Cast & Connect / Quick Settings**: AirPlay hint, screen mirroring, Bluetooth headphones,
  Wi-Fi, Bluetooth, picture and sound settings.
- **Smooth on modest TV hardware**: only transforms and fades animate, and nothing animates while
  you're not moving.
- **Ready to go**: ships with a hologram wallpaper and arc-reactor avatar; swap in your own, and
  add your own assistant voice.

## Requirements

- A Google TV or Android TV running **Android 12 or newer**. Tested on a TCL Google TV (Android 12).
- A Mac (the scripts use zsh and macOS audio tools; the app itself is plain Android).
- **JDK 17**, e.g. [Eclipse Temurin](https://adoptium.net/).
- **Android SDK** command-line tools with `platforms;android-34` and `build-tools;34.0.0`
  (Android Studio installs these too). Point `ANDROID_HOME` at it if it isn't in
  `~/Library/Android/sdk`.
- **adb**, from [Android platform-tools](https://developer.android.com/tools/releases/platform-tools).

No Gradle and no Android Studio needed: `build.sh` uses the SDK tools directly.

## Easiest: set up with Claude Code

Open this folder in [Claude Code](https://claude.com/claude-code) and say **"set up the launcher
on my TV"**. Following [CLAUDE.md](CLAUDE.md), Claude:
1. checks for adb, Java and the Android SDK, and offers to install what's missing;
2. walks you through turning on developer mode on the TV;
3. finds your TV on the Wi-Fi and asks you to press Allow;
4. asks for your city and favourite apps;
5. then builds and installs everything.

## Install by hand

1. **On the TV**, turn on developer options: *Settings → System → About → Android TV OS build*,
   press OK 7 times. Then *Settings → System → Developer options* → turn on **USB debugging**
   (on some TVs also **Network debugging**).
2. Find the TV's IP address: run `./find_tv.sh`, or look in *Settings → Network & Internet → (your Wi-Fi)*.
3. **On the Mac**, in this folder:
   ```bash
   ./install.sh 192.168.1.50
   ```
   (use your TV's IP). The first time, press **Allow** on the TV when it asks to trust your Mac.
4. Press **Home** on the remote: the launcher opens.

## Make it yours

| What | Where |
|---|---|
| Weather city | `src/com/vr/tvhome/Config.java`: `WEATHER_CITY`, `WEATHER_LAT`, `WEATHER_LON` |
| Featured apps | `Config.java` → `FEATURED` (package names + subtitles) |
| What the assistant says | `voice/lines.txt` |
| Assistant voice | Your recordings in `voice/recorded/<key>.mp3`, or a macOS voice: `VOICE=Daniel ./make_voice.sh` |
| Wallpaper / avatar | `assets/wallpaper.jpg`, `assets/arc_reactor.png` (see `assets/README.md`) |
| Start-up intro | Replace `res/raw/intro.mp4` (1080p H.264 + AAC recommended) |

After any change: `./make_voice.sh` (only if you changed the voice) and then `./install.sh <tv-ip>`.

Please only use voices you have the right to use: **don't clone a real person's voice** without
their permission.

## How it works (and its limits)

- **Home button.** Google TV doesn't let another app fully replace its home screen: disabling
  the Google TV home makes Google's setup app loop. So the Google TV home stays installed, and an
  accessibility service (limited to watching the Google TV home's package) opens this launcher
  whenever the Google TV home appears. You may see the Google TV home for a split second when
  pressing Home.
- **Accessibility.** `install.sh` enables the launcher's accessibility service and grants it
  `WRITE_SECURE_SETTINGS`, so it can switch itself back on after a restart. This replaces any
  other accessibility services you had enabled.
- **Start-up.** The real boot animation lives on a read-only system partition and can't be
  changed without rooting. The intro plays right after it instead.
- **Bluetooth.** Many TVs switch Bluetooth off in standby, so a Bluetooth speaker needs a few
  seconds to reconnect after you turn the TV on; that's what the "Initialising…" screen covers.
- **TCL TVs** block apps from starting in the background; `install.sh` allows it for this app.

## Uninstall

```bash
./uninstall.sh 192.168.1.50
```

This turns off the Home override and removes the app. The Google TV home is untouched, so Home
goes straight back to it.

## License

[MIT](LICENSE). Credits for the intro and weather data are in [CREDITS.md](CREDITS.md).
