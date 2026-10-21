#!/bin/zsh
# Finds Android/Google TVs with network debugging on your Wi-Fi: adb's mDNS discovery plus a
# scan of your local /24 network for port 5555. Prints one "ip:port" per line.
set -uo pipefail
ADB=$(command -v adb || echo "$HOME/platform-tools/adb")
[[ -x "$ADB" ]] || { echo "adb not found. Install Android platform-tools first." >&2; exit 1; }

found=()
"$ADB" start-server >/dev/null 2>&1
sleep 2
for hp in $("$ADB" mdns services 2>/dev/null | awk '/_adb._tcp/ {print $NF}'); do found+=("$hp"); done

IFACE=$(route -n get default 2>/dev/null | awk '/interface:/ {print $2}')
MYIP=$(ipconfig getifaddr "${IFACE:-en0}" 2>/dev/null)
if [[ -n "$MYIP" ]]; then
  NET=${MYIP%.*}
  tmp=$(mktemp)
  for i in $(seq 1 254); do
    (nc -z -G 1 -w 1 "$NET.$i" 5555 >/dev/null 2>&1 && echo "$NET.$i:5555" >> "$tmp") &
  done
  wait
  while read -r hp; do found+=("$hp"); done < "$tmp"
  rm -f "$tmp"
fi

printf '%s\n' "${found[@]}" | sort -u | sed '/^$/d'
