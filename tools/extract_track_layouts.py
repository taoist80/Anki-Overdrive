#!/usr/bin/env python3
"""Parse the 3.4 modular track map files into per-track piece sequences.

Each named Overdrive track (track_01..08) maps to a `modular_*.txt` map file. The file's first block
lists pieces in loop order; token[2] is a pieceId resolved to a letter via tracks.json's
`pieceToString.visualizationString.normal`, then to a canonical piece type. Emits a compact JSON
bundled into the app (assets/gamedata/track_layouts.json) so TrackDetail can render the real layout.
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAPDIR = os.path.join(ROOT, "reference/config-3.4.0/basestation/config/mapFiles")
OUT = os.path.join(ROOT, "android/app/src/main/assets/gamedata/track_layouts.json")

# Named track -> modular map file (Hook has no modular file; it falls back to a representative strip).
TRACK_FILE = {
    "track_01": "modular_wedge",    # The Wedge
    "track_02": "modular_capsule",  # Capsule
    "track_03": "modular_point",    # The Point
    "track_05": "modular_loopback", # Loopback
    "track_06": "modular_overpass", # Overpass
    "track_07": "modular_micro",    # MicroLoop
    "track_08": "modular_quadra",   # Quadra
}
LETTER2TYPE = {
    "S": "straight", "L": "curve", "I": "intersection", "J": "jump", "U": "landing",
    "Z": "zone", "B": "start", "E": "start", "H": "special", "K": "special",
}

legend = json.load(open(os.path.join(MAPDIR, "tracks.json")))["pieceToString"]["visualizationString"]["normal"]


def parse(stem):
    lines = [l.strip() for l in open(os.path.join(MAPDIR, "racing", stem + ".txt")) if l.strip()]
    n = int(lines[0])
    seq = []
    for ln in lines[1:1 + n]:
        pid = ln.split()[2]
        t = LETTER2TYPE.get(legend.get(pid, "?"), "straight")
        if t == "start" and seq and seq[-1] == "start":
            continue  # collapse the B/E start-finish halves into one piece
        seq.append(t)
    return seq


layouts = {t: parse(stem) for t, stem in TRACK_FILE.items()}
json.dump(layouts, open(OUT, "w"), indent=0)
for t, seq in layouts.items():
    print(f"{t}: {len(seq)} pieces  {seq}")
print(f"\nwrote {OUT}")
