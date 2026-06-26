#!/usr/bin/env python3
"""
Extract campaign art from the Anki Overdrive **3.4.0** build's Unity AssetBundles into assets/ui/.

Unlike 4.0.4 (Godot .ctex — see extract_ctex_art.py) and DDL 2.6.10 (RAMS manifests → dead CDN, art
not local), the 3.4.0 OBB (inside overdrive.xapk) bundles the art directly as Unity AssetBundles under
`assets/rams/overdrive/asset-bundles/<name>` (no file extension). UnityPy reads them by content.

Pulls:
  - the shared chapter/tournament background  -> assets/ui/chapter_bg.webp
  - every commander portrait (torso bust)     -> assets/ui/commanders/<key>.png
    (key = bundle name minus the "commander" prefix + edition suffix, e.g.
     commandercrashbot_overdrive -> crashbot, commanderdom_foxtrot -> dom)

assets/ are gitignored — regenerate with:  python3 tools/extract_unity_art.py [path/to/overdrive.xapk]

NOTE: our bundled campaign is the Drive Gen2 ladder (27 opponents); only ~5 of its cutscene names
(CrashBot/Brick/Charge/Vice + F&F) match a portrait bundle here. Full per-mission portraits is a
Phase-10 story-parity remap, not just asset wiring — the rest of the portraits are bundled for then.
"""
import os, sys, io, zipfile, tempfile, UnityPy

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "android/app/src/main/assets")
SRC = next((a for a in sys.argv[1:] if a.endswith((".xapk", ".obb"))), os.path.join(ROOT, "overdrive.xapk"))
BUNDLES = "assets/rams/overdrive/asset-bundles/"


def open_obb(path):
    """Return a ZipFile for the 3.4.0 main OBB (the OBB is itself a zip; xapk wraps it)."""
    if path.endswith(".obb"):
        return zipfile.ZipFile(path)
    xz = zipfile.ZipFile(path)
    obb = next(n for n in xz.namelist() if n.endswith(".obb") and "/main." in n)
    return zipfile.ZipFile(io.BytesIO(xz.read(obb)))


def load_bundle(obb, name):
    with tempfile.NamedTemporaryFile(suffix=".bundle", delete=False) as f:
        f.write(obb.read(BUNDLES + name))
        tmp = f.name
    return UnityPy.load(tmp), tmp


def images(env):
    """[(type_name, object_name, PIL.Image)] for every Texture2D/Sprite in the bundle."""
    out = []
    for o in env.objects:
        if o.type.name in ("Texture2D", "Sprite"):
            d = o.read()
            nm = getattr(d, "m_Name", None) or getattr(d, "name", "")
            try:
                out.append((o.type.name, nm, d.image))
            except Exception:
                pass
    return out


def save_named(env, want, out):
    for _, nm, img in images(env):
        if nm == want and img is not None:
            os.makedirs(os.path.dirname(out), exist_ok=True)
            img.save(out)
            return True
    return False


def best_portrait(env):
    """The bust portrait (Sprite '*_torso_large' preferred; then any torso; then fullbody/first)."""
    imgs = images(env)
    for typ in ("Sprite", "Texture2D"):
        for t, nm, img in imgs:
            if t == typ and "torso" in nm.lower() and img is not None:
                return img
    for _, nm, img in imgs:
        if "fullbody" in nm.lower() and img is not None:
            return img
    return imgs[0][2] if imgs else None


def main():
    obb = open_obb(SRC)
    names = [n[len(BUNDLES):] for n in obb.namelist()
             if n.startswith(BUNDLES) and n != BUNDLES and "." not in n[len(BUNDLES):]]

    if "chapters" in names:
        env, tmp = load_bundle(obb, "chapters")
        ok = save_named(env, "ui_tournament_trackBackground", f"{A}/ui/chapter_bg.webp")
        os.unlink(tmp)
        print(f"chapter bg: {'ui/chapter_bg.webp' if ok else 'NOT FOUND'}")

    os.makedirs(f"{A}/ui/commanders", exist_ok=True)
    n = 0
    for b in names:
        if not b.startswith("commander"):
            continue
        key = b[len("commander"):].split("_")[0]   # crashbot / dom / hobbs / brick ...
        env, tmp = load_bundle(obb, b)
        img = best_portrait(env)
        os.unlink(tmp)
        if img is not None:
            img.save(f"{A}/ui/commanders/{key}.png")
            n += 1
    print(f"commanders: extracted {n} portraits -> assets/ui/commanders/")


if __name__ == "__main__":
    main()
