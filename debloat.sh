#!/bin/zsh
# Guided bloatware cleanup for Android/Google TV over Wi-Fi adb. Nothing is ever uninstalled:
# apps are only disabled with `pm disable-user --user 0`, and every change can be undone.
#
#   ./debloat.sh <tv-ip> audit                 free memory + every installed app, sorted SAFE/ASK/KEEP
#   ./debloat.sh <tv-ip> disable <pkg> [...]   disable the chosen apps (KEEP apps are refused)
#   ./debloat.sh <tv-ip> safe                  disable every SAFE app that's installed
#   ./debloat.sh <tv-ip> undo                  re-enable everything this tool disabled
#   ./debloat.sh <tv-ip> reapply               disable the same apps again (after a TV reset)
#   ./debloat.sh <tv-ip> memory                show free memory
#
# Changes are recorded in debloat/disabled.txt and debloat/log.txt (git-ignored).
set -uo pipefail
cd "${0:A:h}"
[[ $# -ge 2 ]] || { sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }
TV="$1"; [[ "$TV" == *:* ]] || TV="$TV:5555"
CMD="$2"; shift 2
ADB=$(command -v adb || echo "$HOME/platform-tools/adb")
[[ -x "$ADB" ]] || { echo "adb not found. Install Android platform-tools."; exit 1; }
LIST=debloat/packages.tsv
STATE=debloat/disabled.txt
LOG=debloat/log.txt
touch "$STATE" "$LOG"

"$ADB" connect "$TV" >/dev/null 2>&1
[[ $("$ADB" -s "$TV" get-state 2>/dev/null) == device ]] || {
  echo "Can't reach the TV at $TV. Turn on USB/network debugging and press Allow on the TV."; exit 1; }
sh_tv() { "$ADB" -s "$TV" shell "$@" | tr -d '\r'; }

category() { awk -F'\t' -v p="$1" '$1==p {print $2; exit}' "$LIST"; }
describe() { awk -F'\t' -v p="$1" '$1==p {print $3; exit}' "$LIST"; }

memory() {
  sh_tv cat /proc/meminfo | awk '/MemTotal|MemAvailable/ {printf "%s %d MB\n", $1, $2/1024}'
}

disable() {
  local p=$1 r
  if [[ "$(category "$p")" == KEEP ]]; then echo "  refused (needed by the TV): $p"; return; fi
  r=$(sh_tv pm disable-user --user 0 "$p" 2>&1)
  if [[ "$r" == *disabled-user* ]]; then
    grep -qx "$p" "$STATE" || echo "$p" >> "$STATE"
    echo "  disabled: $p"
  else
    echo "  failed: $p ($r)"
  fi
  echo "$(date '+%F %T') disable $p -> $r" >> "$LOG"
}

case "$CMD" in
  memory)
    memory ;;
  audit)
    echo "== Free memory"; memory
    echo "\n== Installed apps (category | package | what it is)"
    user=$(sh_tv pm list packages -3 | sed 's/package://')
    off=$(sh_tv pm list packages -d | sed 's/package://')
    for p in $(sh_tv pm list packages | sed 's/package://' | sort); do
      c=$(category "$p"); d=$(describe "$p")
      if [[ -z "$c" ]]; then
        if print -r -- "$user" | grep -qx "$p"; then c=YOURS; d="App you installed"; else c=UNKNOWN; d="System app (leave it unless you're sure)"; fi
      fi
      print -r -- "$off" | grep -qx "$p" && d="$d [already disabled]"
      printf "%-7s | %-45s | %s\n" "$c" "$p" "$d"
    done | sort -t'|' -k1,1 -s ;;
  disable)
    [[ $# -ge 1 ]] || { echo "Name at least one package."; exit 1; }
    for p in "$@"; do disable "$p"; done ;;
  safe)
    installed=$(sh_tv pm list packages | sed 's/package://')
    awk -F'\t' '$2=="SAFE" {print $1}' "$LIST" | while read -r p; do
      print -r -- "$installed" | grep -qx "$p" && disable "$p"
    done ;;
  reapply)
    while read -r p; do [[ -n "$p" ]] && disable "$p"; done < "$STATE" ;;
  undo)
    while read -r p; do
      [[ -z "$p" ]] && continue
      sh_tv pm enable "$p" >/dev/null && echo "  re-enabled: $p"
      echo "$(date '+%F %T') enable $p" >> "$LOG"
    done < "$STATE"
    : > "$STATE" ;;
  *)
    echo "Unknown command: $CMD"; exit 1 ;;
esac
