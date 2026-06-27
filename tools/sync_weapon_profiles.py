#!/usr/bin/env python3
"""Bring our weapon combat profiles into parity: 2.6 primary, 3.4 fallback for items 2.6 lacks, and list
anything in neither. Makes the combat fields EXACTLY match the source; preserves our structure + all
non-combat fields (cost/card_power/strings/art/upgrade ids)."""
import json, sys

OURS, V26, V34, OUT = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
COMBAT = [
    "damage_per_target", "damage_per_target_per_s", "damage_per_target_per_second_charged",
    "damage_type", "damage_multiplier_at_max_distance",
    "energy_use_per_s", "energy_use_in_charge_mode_per_s", "base_energy_cost",
    "cooldown_data", "recharge_time_s", "recharge_time_no_target_s", "shield_blocked_damage_percent",
]

def index(doc):
    idx = {}
    def rec(o):
        if isinstance(o, dict):
            if "id" in o and any(k in o for k in ("damage_per_target", "extends", "targeter",
                                                  "base_energy_cost", "cooldown_data")):
                idx[o["id"]] = o
            for v in o.values(): rec(v)
        elif isinstance(o, list):
            for v in o: rec(v)
    rec(doc); return idx

def sync(dst, src):
    n = 0
    for f in COMBAT:
        if f in src:
            if dst.get(f) != src[f]: dst[f] = src[f]; n += 1
        elif f in dst:
            del dst[f]; n += 1
    return n

ours_doc = json.load(open(OURS))
ours = index(ours_doc)
v26 = index(json.load(open(V26)))
v34 = index(json.load(open(V34)))

from26, from34, unaccounted, fields = [], [], [], 0
for iid, a in ours.items():
    if iid in v26:   fields += sync(a, v26[iid]); from26.append(iid)
    elif iid in v34: fields += sync(a, v34[iid]); from34.append(iid)
    else:            unaccounted.append(iid)

json.dump(ours_doc, open(OUT, "w"), separators=(",", ":"))
print(f"combat items in ours: {len(ours)} | from 2.6: {len(from26)} | from 3.4: {len(from34)} | "
      f"unaccounted: {len(unaccounted)} | fields changed: {fields}")
print(f"  3.4-sourced ({len(from34)}): {sorted(from34)}")
print(f"\n  UNACCOUNTED — in neither 2.6 nor 3.4 ({len(unaccounted)}):")
for iid in sorted(unaccounted):
    o = ours[iid]
    kind = "WEAPON" if (o.get("damage_per_target") or o.get("damage_per_target_per_s")
                        or "shotgun" in iid.lower() or "gun" in iid.lower() or "mine" in iid.lower()) else "support/other"
    print(f"    {iid:32} [{kind}] extends={o.get('extends','-')}")
