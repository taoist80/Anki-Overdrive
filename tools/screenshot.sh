#!/usr/bin/env bash
# Tap a fractional coordinate (of 2800x1752 landscape) then screenshot.
# Usage: shot.sh <out.png> [xfrac yfrac] [sleep_s]
#   shot.sh out.png            -> just capture (no tap)
#   shot.sh out.png 0.49 0.85  -> tap then capture (default 2.5s settle)
#   shot.sh out.png 0.49 0.85 4
set -e
W=2800; H=1752
OUT="$1"; XF="$2"; YF="$3"; SL="${4:-2.5}"
if [ -n "$XF" ] && [ -n "$YF" ]; then
  X=$(python3 -c "print(int($W*$XF))"); Y=$(python3 -c "print(int($H*$YF))")
  adb shell input tap "$X" "$Y"
  adb shell sleep "$SL"
fi
adb exec-out screencap -p > "$OUT"
echo "captured $OUT ${XF:+(tapped $XF,$YF)}"
