# Your own files (optional)

Drop these here and run `./install.sh <tv-ip>`. Anything missing falls back to a built-in look.

| File | What it's for | Fallback |
|---|---|---|
| `wallpaper.jpg` | Home screen background, 1920×1080. The left third, between the weather and assistant cards, stays visible. **Included** (AI-generated hologram over Earth); replace it if you like | Deep-navy gradient |
| `arc_reactor.png` | Assistant avatar and power-on "Initialising…" screen. Square PNG with transparent background, ~512 px. **Included** (AI-generated); replace it if you like | Arc reactor drawn in code |
| `voice/` | Made by `./make_voice.sh` (run automatically by `install.sh`) from `voice/lines.txt` and `voice/recorded/`. Don't edit by hand | Lines shown as text only |

Apart from the included `wallpaper.jpg` and `arc_reactor.png`, your own files here are git-ignored, so your personal pictures and recordings stay out of the repo.
