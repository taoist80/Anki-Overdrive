#!/usr/bin/env bash
# Convert one Wwise .wem -> .wav (lossless). Args: $1=wem file. Reads $OUTDIR for destination.
# Helper for extract_audio.sh (separate file so xargs -P can fan out without hitting BSD
# xargs' 255-byte -I command limit). Corpus is kept as wav; transcode to ogg at bundle time.
f="$1"
id="$(basename "$f" .wem)"
vgmstream-cli -o "$OUTDIR/$id.wav" "$f" >/dev/null 2>&1
