#!/bin/zsh
# Builds the assistant's voice pack in assets/voice from voice/lines.txt.
#
# For each line it uses, in order:
#   1. your recording: voice/recorded/<key>.mp3 | .wav | .m4a
#   2. if VOICE is set (macOS only), the system voice, e.g.  VOICE=Daniel ./make_voice.sh
#   3. nothing: the line is shown on screen without sound
# Then run ./install.sh <tv-ip>.
set -euo pipefail
cd "${0:A:h}"
OUT=assets/voice
REC=voice/recorded
VOICE="${VOICE:-}"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

rm -rf "$OUT" && mkdir -p "$OUT"
: > "$OUT/index.txt"
voiced=0; silent=()
while IFS='|' read -r key text; do
  [[ -z "$key" || "$key" == \#* ]] && continue
  src=$(ls "$REC/$key".{mp3,wav,m4a} 2>/dev/null | head -1 || true)
  if [[ -z "$src" && -n "$VOICE" ]]; then
    say -v "$VOICE" -r "${RATE:-170}" -o "$TMP/line.aiff" "$text"
    src="$TMP/line.aiff"
  fi
  if [[ -n "$src" ]]; then
    afconvert -f m4af -d aac -b 96000 -c 1 "$src" "$OUT/$key.m4a"
    secs=$(afinfo "$OUT/$key.m4a" | awk '/estimated duration/ {print $3}')
    ms=$(printf '%.0f' $(( secs * 1000 )))
    voiced=$((voiced + 1))
  else
    ms=0
    silent+=("$key")
  fi
  echo "$key|$ms|$text" >> "$OUT/index.txt"
done < voice/lines.txt
echo "Voiced $voiced lines ($(du -sh "$OUT" | cut -f1))"
(( ${#silent} )) && echo "Text only (no recording): ${silent[*]}"
exit 0
