#!/usr/bin/env bash
# Extract Anki Overdrive 3.4.0 original audio (Wwise) to ogg.
# Source: 3.4.0 OBB -> assets/rams/overdrive/basestation/audio/*.zip
#   each .zip = one Wwise SoundBank: a .bnk (event graph) + N .wem (Wwise Vorbis media).
# Pipeline: .wem --vgmstream--> wav (lossless), organised by bank. Transcode to ogg at bundle time.
# Deps: vgmstream-cli.  Usage: tools/extract_audio.sh [banks_dir] [out_dir]
HERE="$(cd "$(dirname "$0")" && pwd)"
BANKS="${1:-reference/audio/3.4.0-banks/assets/rams/overdrive/basestation/audio}"
OUT="${2:-reference/audio/wav}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

total=0
for z in "$BANKS"/*.zip; do
  bank="$(basename "$z" .zip)"
  d="$TMP/$bank"; mkdir -p "$d" "$OUT/$bank"
  unzip -o -q "$z" -d "$d" 2>/dev/null
  export OUTDIR="$OUT/$bank"
  find "$d" -name '*.wem' -print0 | xargs -0 -P 8 -n 1 "$HERE/_wem2ogg.sh"
  n="$(ls "$OUT/$bank"/*.wav 2>/dev/null | wc -l | tr -d ' ')"
  total=$((total+n))
  echo "bank $bank: $n wav"
done
echo "TOTAL: $total wav files in $OUT"
