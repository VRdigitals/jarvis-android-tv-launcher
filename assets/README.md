# Your own files (optional)

Drop these here and run `./install.sh <tv-ip>`. Anything missing falls back to a built-in look.

| File | What it's for | Fallback |
|---|---|---|
| `wallpaper.jpg` | Home screen background, 1920×1080. The left third, between the weather and assistant cards, stays visible | Deep-navy gradient |
| `arc_reactor.png` | Assistant avatar and power-on "Initialising…" screen. Square PNG with transparent background, ~512 px. **Included** (AI-generated); replace it if you like | Arc reactor drawn in code |
| `voice/` | Made by `./make_voice.sh` from `voice/lines.txt`. Don't edit by hand | Lines shown as text only |

Apart from the included `arc_reactor.png`, these are git-ignored, so your personal pictures and recordings stay out of the repo.
