#!/usr/bin/env python3
"""Generate the bundled, engine-facing roadPieceGeometry.json (mm/int based).

Schema:
  { "mu":0.87, "g":9.81, "laneSpacingMm":9.0,
    "pieces":[ {id, lengthMm, radiusMm(signed,0=straight), isCurve, maxSpeedMmps(0=no cap), numLanes}, ... ] }
maxSpeedMmps is the PARITY value sqrt(mu*g*|R|); the engine applies a tunable scale on top.
"""
import json, math, os, sys

CFG = sys.argv[1]
OUT = sys.argv[2]
D = os.path.join(CFG, "roadPieceDefinitionFiles", "racing")
MU, G = 0.87, 9.81

pieces = []
for fn in sorted(os.listdir(D), key=lambda s: int(s[:-4]) if s.endswith(".txt") else 1e9):
    if not fn.endswith(".txt"):
        continue
    pid = int(fn[:-4])
    with open(os.path.join(D, fn)) as f:
        lines = [ln.strip() for ln in f if ln.strip()]
    if len(lines) < 8:
        continue
    try:
        arc = float(lines[0]); radius = float(lines[1]); num_lanes = int(float(lines[6]))
    except ValueError:
        continue
    abs_r = abs(radius)
    turn = (arc / abs_r) if abs_r > 1e-9 else 0.0
    is_curve = (radius != 0.0) and abs_r < 1.5 and turn > 0.15
    vmax = int(round(math.sqrt(MU * G * abs_r) * 1000)) if is_curve else 0
    pieces.append(dict(id=pid,
                       lengthMm=int(round(arc * 1000)),
                       radiusMm=int(round(radius * 1000)),
                       isCurve=is_curve,
                       maxSpeedMmps=vmax,
                       numLanes=num_lanes))

out = {"mu": MU, "g": G, "laneSpacingMm": 9.0, "pieces": pieces}
with open(OUT, "w") as f:
    json.dump(out, f, separators=(",", ":"))
print(f"Wrote {len(pieces)} pieces -> {OUT}")
print("curves:", sum(1 for p in pieces if p['isCurve']),
      "straights:", sum(1 for p in pieces if not p['isCurve']))
