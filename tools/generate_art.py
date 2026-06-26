#!/usr/bin/env python3
"""
Generate the missing OverdriveX art with Stability AI on Bedrock — on-brand, background-removed PNGs
that match the extracted 3.4.0 commander-portrait look. Same repeatable pattern as the extract_*.py
tools; assets/ are gitignored so regenerate via this tool.

NOTE: Amazon Nova Canvas / Titan are LEGACY-gated in this account; the Stability suite is ACTIVE in
us-west-2, so we use stable-image-ultra (text->image) + stable-image-remove-background. The
stable-image-style-guide model can later enforce tighter consistency with a reference portrait.
Uses the ambient AWS profile/SSO (Bedrock access for the Stability models required).

Usage:
  python3 tools/generate_art.py commanders --test [--out DIR]   # 2-image style test (raw + no-bg)
  python3 tools/generate_art.py commanders                      # full set -> assets/ui/commanders/
"""
import os, sys, json, base64, boto3

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "android/app/src/main/assets")
REGION = "us-west-2"
GEN_MODEL = "stability.stable-image-ultra-v1:1"
BG_MODEL = "stability.stable-image-remove-background-v1:0"

# Match the extracted 3.4.0 commander busts: FLAT painted comic-book / cel-shaded character art,
# head-and-shoulders, facing viewer, plain muted dark studio background, stylized (NOT photoreal / NOT 3D).
STYLE = (
    "flat 2D character portrait illustration of {desc}, head-and-shoulders bust facing the viewer, "
    "confident expression, an AI commander rival in the Anki Overdrive arcade street-racing game, "
    "painterly comic-book cel-shaded art style with bold clean linework, smooth color gradients and "
    "stylized semi-realistic proportions, soft rim light, plain muted dark teal-grey studio backdrop, "
    "centered, character art only"
)
NEGATIVE = ("photograph, photorealistic, 3d render, octane render, unreal engine, cgi, realistic skin "
            "pores, scene, room, garage, racetrack, background environment, props, vehicle, white "
            "background, text, words, watermark, signature, logo, frame, border, full body, hands, "
            "weapons, extra limbs, deformed, blurry, low quality")

# The 6 Gen2 campaign opponents with no shipped portrait (Cam/Charge/Crush/Fuzz/Gearswitch/Metro/Rev/
# Sever/Vice are the distinct opponents; Charge/Metro/Vice already have 3.4.0 art). Original concepts
# art-directed from each name — these are Anki's original Overdrive commander names (original IP).
COMMANDERS = {
    "cam": "a sharp tech-savvy young racer wearing a sleek augmented-reality visor over the eyes, "
           "teal and silver racing jacket, smug confident smirk, short cyber-styled hair",
    "crush": "a hulking muscular brute racer, bald with a heavy armored chest rig, scarred jaw, "
             "menacing glare, crimson and gunmetal-grey gear",
    "fuzz": "a stern ex-police enforcer turned racer, tactical dark-blue armored jacket with a faded "
            "badge motif, buzz-cut hair, steely authoritative stare",
    "gearswitch": "a grease-streaked gearhead mechanic-racer, welding goggles pushed up on the "
                  "forehead, oil-stained tan coveralls, wrench tucked in collar, grinning",
    "rev": "a wild adrenaline-junkie speed freak racer, spiky orange-tipped hair, flame-pattern racing "
           "jacket, manic excited grin, glowing orange accents",
    "sever": "a cold calculating racer with sharp angular features, slicked-back black hair, sleek "
             "black-and-violet bodysuit with blade-like edge motifs, icy piercing stare",
}


def client():
    return boto3.client("bedrock-runtime", region_name=REGION)


def _decode(out):
    # Stability returns {"images":[b64,...]} (some editing models use {"image": b64}).
    b64 = (out.get("images") or [out.get("image")])[0]
    if not b64:
        raise RuntimeError(f"no image in response: {json.dumps(out)[:300]}")
    return base64.b64decode(b64)


def generate(brt, text, seed):
    r = brt.invoke_model(modelId=GEN_MODEL, body=json.dumps({
        "prompt": text, "negative_prompt": NEGATIVE,
        "aspect_ratio": "1:1", "seed": seed, "output_format": "png",
    }))
    return _decode(json.loads(r["body"].read()))


def remove_bg(brt, png_bytes):
    r = brt.invoke_model(modelId=BG_MODEL, body=json.dumps({
        "image": base64.b64encode(png_bytes).decode(), "output_format": "png",
    }))
    return _decode(json.loads(r["body"].read()))


def main():
    mode = next((a for a in sys.argv[1:] if not a.startswith("-")), "commanders")
    test = "--test" in sys.argv
    out = next((sys.argv[i + 1] for i, a in enumerate(sys.argv) if a == "--out"), None) \
        or (os.path.join(ROOT, "scratch_gen") if test else f"{A}/ui/commanders")
    os.makedirs(out, exist_ok=True)
    brt = client()

    # The portrait prompt bakes a dark neutral background that sits naturally in the violet cards
    # (which crop to a rounded square), so background removal is optional — it needs a Bedrock
    # inference profile (not on-demand), so we only attempt it when --rmbg is passed.
    rmbg = "--rmbg" in sys.argv
    items = list(COMMANDERS.items())
    if test:
        items = items[:2]
    for i, (key, desc) in enumerate(items):
        try:
            img = generate(brt, STYLE.format(desc=desc), seed=i + 7)
            if rmbg:
                try:
                    img = remove_bg(brt, img)
                except Exception as e:
                    print(f"  (bg-removal skipped for {key}: {e})")
            open(f"{out}/{key}.png", "wb").write(img)
            print(f"  generated {key}")
        except Exception as e:
            print(f"  FAILED {key}: {e}")
            if "AccessDenied" in str(e) or "not authorized" in str(e):
                print("  -> request Bedrock model access for the Stability models in us-west-2.")
                break
    print(f"done -> {out}")


if __name__ == "__main__":
    main()
