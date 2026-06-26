#!/usr/bin/env python3
"""
Carve weapon + car art out of the DDL Overdrive 4.0.4 Godot apk and bundle the renders our catalog is
missing (the Fast & Furious cars/weapons DDL 2.6 never shipped). Two modes:

  weapons (default) -> fills missing item detailImages into android/.../assets/items/  (as .webp)
  cars              -> replaces blank SILHOUETTE car sprites in assets/cars/           (as .png)

Why this works without gdsdecomp/Ghidra: 4.0.4 imports these textures as CompressedTexture2D with
"vram_texture": false, so each assets/.godot/imported/*.ctex embeds a lossless WebP blob (not a
GPU-compressed BCn/ETC image). We find the RIFF/WEBP magic and carve it. Android BitmapFactory
decodes WebP by content (ignoring extension), so weapon art ships as .webp and car art is converted
to .png (via Pillow) so GameData's `<asset>-left-720.png` resolution is unchanged.

Re-run after re-unzipping the apk if assets/ is wiped (assets/ is gitignored).

Usage:  python3 tools/extract_ctex_art.py [weapons|cars|all] [path/to/Overdrive-4.0.4.apk]
"""
import os, sys, json, re, struct, zipfile, tempfile, io

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "android/app/src/main/assets")
MODE = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1] in ("weapons", "cars", "all") else "all"
APK = next((a for a in sys.argv[1:] if a.endswith(".apk")), os.path.join(ROOT, "reference/ddl404/Overdrive-4.0.4.apk"))


def carve_webp(data):
    if data[:4] != b"GST2":
        return None
    i = data.find(b"RIFF")
    if i >= 0 and data[i + 8:i + 12] == b"WEBP":
        return data[i:i + struct.unpack("<I", data[i + 4:i + 8])[0] + 8]
    return None


def index_ctex(apk):
    """Extract every imported .ctex to a temp dir; return {sourceName: path} (sourceName w/o .png)."""
    tmp = tempfile.mkdtemp()
    with zipfile.ZipFile(apk) as z:
        z.extractall(tmp, [n for n in z.namelist() if "/.godot/imported/" in n and n.endswith(".ctex")])
    imp = os.path.join(tmp, "assets/.godot/imported")
    idx = {}
    for fn in os.listdir(imp):
        m = re.match(r"(.+?)\.png-[0-9a-f]{32}\.ctex$", fn)
        if m:
            idx[m.group(1)] = os.path.join(imp, fn)
    return idx


def needed_weapon_details():
    items = json.load(open(f"{A}/gamedata/items.json"))
    strings = json.load(open(f"{A}/gamedata/asset-strings-en.json"))
    def s(k):
        v = strings.get(k) if k else None
        return (v.get("translation") if isinstance(v, dict) else v)
    bundled = set(os.listdir(f"{A}/items"))
    by = {**{i["id"]: i for i in items["items"]["abstract_items"] if i.get("id")},
          **{i["id"]: i for i in items["items"]["real_items"] if i.get("id")}}
    def chain(i):
        o, seen, c = [], set(), i
        while c and c.get("id") not in seen:
            seen.add(c.get("id")); o.append(c); c = by.get(c.get("extends"))
        return o
    def f(i, k):
        return next((c[k] for c in chain(i) if c.get(k) is not None), None)
    need = set()
    for i in items["items"]["real_items"]:
        d = f(i, "image_detail")
        if s(f(i, "name_localized")) and d and not any(
                f"{d}-{sz}.{e}" in bundled for sz in ("large", "medium") for e in ("png", "webp")):
            need.add(d)
    return need


def silhouette_cars():
    """Car sprites whose opaque pixels are near-monochrome (placeholder silhouettes)."""
    from PIL import Image
    out = []
    for fn in os.listdir(f"{A}/cars"):
        if not fn.endswith(".png"):
            continue
        im = Image.open(f"{A}/cars/{fn}").convert("RGBA")
        op = [p[:3] for p in im.getdata() if p[3] > 20]
        if op and len({(r >> 5, g >> 5, b >> 5) for r, g, b in op}) <= 3:
            out.append(fn[:-4])   # strip .png
    return out


def main():
    idx = index_ctex(APK)
    if MODE in ("weapons", "all"):
        need = needed_weapon_details()
        n = 0
        for base in list(need):
            for src in (f"{base}-large", f"{base}-medium"):
                if src in idx:
                    blob = carve_webp(open(idx[src], "rb").read())
                    if blob:
                        open(f"{A}/items/{src}.webp", "wb").write(blob); n += 1
                    break
        print(f"weapons: filled {n}/{len(need)} missing detail renders")
    if MODE in ("cars", "all"):
        from PIL import Image
        sil = silhouette_cars()
        n = 0
        for src in sil:                                   # e.g. dynamo-left-720
            if src in idx:
                blob = carve_webp(open(idx[src], "rb").read())
                if blob:
                    Image.open(io.BytesIO(blob)).convert("RGBA").save(f"{A}/cars/{src}.png", "PNG"); n += 1
            elif src.endswith(("-420",)):                 # 4.0.4 ships only 720 -> downscale
                big = f"{A}/cars/{src[:-3]}720.png"
                if os.path.exists(big):
                    im = Image.open(big).convert("RGBA")
                    im.resize((420, round(im.height * 420 / im.width)), Image.LANCZOS).save(f"{A}/cars/{src}.png"); n += 1
        print(f"cars: replaced {n}/{len(sil)} silhouettes (run twice so 720s exist before 420 downscale)")


if __name__ == "__main__":
    main()
