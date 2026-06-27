#!/usr/bin/env python3
"""Overlay the MODULAR road-piece curve geometry onto roadPieceGeometry.json.

Racing tracks are built from modularRoadPieceDefinitionFiles/racing/<v>_<w>_<id>.txt — and those curve
pieces (e.g. ids 17/18/20/23/24/27 = the standard 90° R0.28 Anki corner) are what the firmware reports for
a scanned oval. Our base table came from the non-modular roadPieceDefinitionFiles, which mis-valued some
curve ids and omitted 20-27. This decodes each modular piece's centerline pose-pair (the first 6-float line:
x0 y0 θ0 x1 y1 θ1) → arc/radius, and for CURVES (|turn|>threshold) updates/adds them in the table. Modular
straights are skipped (the simple pose-line decode is unreliable for them; the base table keeps those).

Usage: python3 tools/overlay_modular_curves.py <modular_dir> <roadPieceGeometry.json>
"""
import os, math, json, sys

MOD, OUT = sys.argv[1], sys.argv[2]
MU, G = 0.87, 9.81

def decode(path):
    for l in (x.strip() for x in open(path) if x.strip()):
        t = l.split()
        if len(t) == 6:
            try: x0, y0, h0, x1, y1, h1 = map(float, t)
            except ValueError: continue
            chord = math.hypot(x1 - x0, y1 - y0)
            arc = h1 - h0
            while arc > math.pi: arc -= 2 * math.pi
            while arc < -math.pi: arc += 2 * math.pi
            if abs(arc) < 0.05 or chord < 1e-6:
                return chord, 0.0          # straight
            R = chord / (2 * math.sin(abs(arc) / 2))
            return R * abs(arc), (R if arc > 0 else -R)
    return None

doc = json.load(open(OUT))
by = {p["id"]: p for p in doc["pieces"]}
updated, added = [], []
for f in os.listdir(MOD):
    if not f.endswith(".txt"):
        continue
    pid = int(f[:-4].split("_")[-1])
    r = decode(os.path.join(MOD, f))
    if not r:
        continue
    arc, radius = r
    if radius == 0.0 or abs(radius) >= 1.5 or arc / max(abs(radius), 1e-6) <= 0.15:
        continue   # not a real curve — skip (keep base-table value)
    vmax = int(round(math.sqrt(MU * G * abs(radius)) * 1000))
    rec = dict(id=pid, lengthMm=int(round(arc * 1000)), radiusMm=int(round(radius * 1000)),
               isCurve=True, maxSpeedMmps=vmax, numLanes=20)
    if pid in by:
        if by[pid] != rec: by[pid].update(rec); updated.append(pid)
    else:
        doc["pieces"].append(rec); added.append(pid)

doc["pieces"].sort(key=lambda p: p["id"])
json.dump(doc, open(OUT, "w"), separators=(",", ":"))
print(f"modular curves → updated {sorted(updated)}, added {sorted(added)}")
