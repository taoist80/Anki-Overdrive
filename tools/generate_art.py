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
  python3 tools/generate_art.py avatars --test                  # 2-image style test
  python3 tools/generate_art.py avatars                         # 8 player avatars -> assets/ui/avatars/
  python3 tools/generate_art.py medals                          # 8 achievement medals -> assets/ui/medals/
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
    "stylized semi-realistic proportions, soft neon rim light, set against a solid dark teal-grey "
    "studio background, definitely not a white or light background, centered, character art only"
)
NEGATIVE = ("photograph, photorealistic, 3d render, octane render, unreal engine, cgi, realistic skin "
            "pores, scene, room, garage, racetrack, background environment, props, vehicle, white "
            "background, light background, pale background, bright background, light grey background, "
            "text, words, watermark, signature, logo, frame, border, full body, hands, "
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


# --- Player avatars (Profile > Choose Avatar). Same cel-shaded look as the commanders, but neutral
# heroic "driver you play as" archetypes rather than named rivals. 8 distinct, diverse picks. ---
AVATAR_STYLE = (
    "flat 2D character portrait illustration of {desc}, head-and-shoulders bust facing the viewer, "
    "a selectable player driver avatar in the Anki Overdrive arcade street-racing game, painterly "
    "comic-book cel-shaded art style with bold clean linework, smooth color gradients and stylized "
    "semi-realistic proportions, soft neon rim light, set against a solid dark charcoal-violet studio "
    "background, definitely not a white or light background, centered, character art only"
)
AVATAR_NEGATIVE = NEGATIVE + (", bright background, light background, pale background, white backdrop, "
                              "light grey background")
AVATARS = {
    "ace":      "a confident female ace racer, sleek aerodynamic racing helmet with the tinted visor "
                "raised, violet-and-cyan racing suit, calm focused expression",
    "rookie":   "an eager young male rookie driver, short tousled brown hair, simple grey-and-orange "
                "racing jacket, optimistic grin",
    "android":  "a sleek humanoid AI android with a smooth chrome faceplate and a glowing cyan eye-band, "
                "exposed neon circuitry at the neck, serene",
    "veteran":  "a grizzled older veteran racer, grey stubble and a scar, weathered leather racing "
                "jacket, calm steely gaze",
    "hacker":   "a tech hacker racer with a holographic data visor over the eyes, hood up, magenta neon "
                "glow lighting the face, sly half-smile",
    "mechanic": "a cheerful female mechanic-racer with goggles pushed up on the forehead, freckles, "
                "ponytail, a grease smudge on the cheek, tan coveralls",
    "captain":  "a stern team captain, peaked racing cap, dark uniform jacket with a small chevron "
                "insignia, authoritative composed look",
    "drift":    "a stylish street-drift racer with neon-streaked undercut hair, headphones around the "
                "neck, bomber jacket, cool confident smirk",
}

# --- Achievement medals (Profile > Medals). Circular metallic emblems on dark, neon-accented; in-UI we
# crop to a circle and frame by tier, so a single base emblem per achievement is enough. ---
MEDAL_STYLE = (
    "a polished circular metallic achievement medal emblem for a futuristic arcade racing game, the "
    "centered symbol is {desc}, sleek beveled metal rim with a subtle glowing neon edge, bold clean "
    "symmetrical vector icon design, dramatic rim lighting, plain dark charcoal studio background, "
    "crisp game-UI achievement badge, highly detailed"
)
MEDAL_NEGATIVE = ("photograph, photorealistic, text, words, letters, numbers, typography, watermark, "
                  "signature, cluttered busy background, scene, room, person, human face, hands, "
                  "vehicle, asymmetrical, off-center, blurry, low quality, jpeg artifacts")
MEDALS = {
    "first_win":     "a victory laurel wreath encircling a single bright star, warm gold",
    "champion":      "a regal crown resting atop a checkered-flag shield, radiant gold",
    "untouchable":   "a glowing hexagonal energy shield with a deflected spark, electric cyan",
    "speed_demon":   "a stylized speedometer needle wrapped in a sweeping flame, fiery orange-red",
    "sharpshooter":  "a precise circular crosshair locked on a small diamond target, steel blue",
    "demolisher":    "two crossed futuristic cannons over a burst impact, molten crimson",
    "marathon":      "a checkered race flag curling around a looping circular track, vivid green",
    "perfectionist": "three stars arcing above a faceted gemstone, violet and gold",
}

MODES = {
    # mode: (items, style, negative, default_subdir)  -- lambdas so dicts defined lower still resolve
    "commanders":   lambda: (COMMANDERS, STYLE, NEGATIVE, "ui/commanders"),
    "commanders26": lambda: (COMMANDERS_2_6, STYLE, NEGATIVE, "ui/commanders"),
    "avatars":      lambda: (AVATARS, AVATAR_STYLE, AVATAR_NEGATIVE, "ui/avatars"),
    "medals":       lambda: (MEDALS, MEDAL_STYLE, MEDAL_NEGATIVE, "ui/medals"),
}


# The 17 real 2.6 campaign commanders with no shipped portrait. Visual art direction derived from each
# one's real bio in the 2.6 asset-strings (commander_NN_desc). Same cel-shaded look as the other busts.
COMMANDERS_2_6 = {
    "gaston":      "a smug arrogant aristocratic stock-car crew boss, slicked-back hair and a thin "
                   "moustache, flashy red-and-gold racing blazer, chin raised haughtily",
    "shadowx":     "a cocky young top-fuel hotshot racer, asymmetric dark hair with a neon-blue streak, "
                   "sleek black racing jacket with a stylized X motif, overconfident smirk",
    "roofus":      "a cybernetically-enhanced anthropomorphic guard dog, German-shepherd features with a "
                   "glowing robotic eye implant, police-K9 tactical vest, alert and loyal",
    "bighouse":    "a heavyset cheerful gamer-turned-racer, gaming headset, oversized hoodie covered in "
                   "pixel-art patches, big confident grin",
    "blueshift":   "a charismatic two-faced racer with an easy grin that doesn't reach the eyes, cool "
                   "blue-toned swept hair and jacket, glowing cyan accents",
    "anchor":      "a massive broad-shouldered heavyweight racer, shaved head, an anchor tattoo on the "
                   "neck, calm immovable expression, navy-and-steel gear",
    "apeocalypse": "a cybernetically-enhanced military gorilla, a transparent brain-implant dome on the "
                   "head, armored combat harness, snarling with one glowing red eye",
    "sniper":      "a fiercely determined female crew boss, sharp focused eyes, tactical ponytail, "
                   "olive-and-orange racing suit with a crosshair emblem, stern no-nonsense look",
    "winger":      "a warm experienced female mentor racer, greying hair in a practical bun, kind but "
                   "tough eyes, a worn teal flight-jacket with a wing emblem",
    "dark":        "an otherworldly sinister racer, pale gaunt face, jet-black hooded racing suit with a "
                   "violet inner glow, piercing hollow eyes, eerily calm",
    "maximus":     "a stoic powerfully-built veteran racer, square jaw, close-cropped grey hair, "
                   "understated charcoal racing suit, quietly supremely confident",
    "breach":      "an intense ex-special-forces crew boss, buzz cut and a tactical face scar, heavy "
                   "armored grey-and-orange racing rig, locked-on glare",
    "roadkill":    "a wiry cybernetically-rebuilt racer, half his face a chrome prosthetic with a red "
                   "optic, scrappy patched jacket, manic grin",
    "mainframe":   "a cold humanoid combat robot racer, sleek angular matte-black chassis, a single "
                   "horizontal red sensor bar for eyes, utterly emotionless",
    "oblivion":    "a charismatic cult-leader assassin boss, shaved head with ritual tattoos, "
                   "black-and-gold ceremonial racing robe, hypnotic unsettling stare",
    "kon":         "a confident southeast-asian underground death-race champion, undercut hair, a "
                   "dragon-motif racing jacket, scarred eyebrow, cocky and calm",
    "apollo":      "a regal menacing final-boss racer (Alexander Zeross), golden cybernetic accents, an "
                   "immaculate white-and-gold executive racing suit, cold superior smile, glowing gold eyes",
}


def client():
    return boto3.client("bedrock-runtime", region_name=REGION)


def _decode(out):
    # Stability returns {"images":[b64,...]} (some editing models use {"image": b64}).
    b64 = (out.get("images") or [out.get("image")])[0]
    if not b64:
        raise RuntimeError(f"no image in response: {json.dumps(out)[:300]}")
    return base64.b64decode(b64)


def generate(brt, text, seed, negative=NEGATIVE):
    r = brt.invoke_model(modelId=GEN_MODEL, body=json.dumps({
        "prompt": text, "negative_prompt": negative,
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
    if mode not in MODES:
        sys.exit(f"unknown mode '{mode}' — pick one of {', '.join(MODES)}")
    item_dict, style, negative, subdir = MODES[mode]()
    test = "--test" in sys.argv
    out = next((sys.argv[i + 1] for i, a in enumerate(sys.argv) if a == "--out"), None) \
        or (os.path.join(ROOT, "scratch_gen") if test else f"{A}/{subdir}")
    os.makedirs(out, exist_ok=True)
    brt = client()

    # The prompt bakes a dark neutral background that sits naturally in the violet cards (which crop to
    # a rounded square / circle), so background removal is optional — it needs a Bedrock inference
    # profile (not on-demand), so we only attempt it when --rmbg is passed.
    rmbg = "--rmbg" in sys.argv
    items = list(item_dict.items())
    if test:
        items = items[:2]
    for i, (key, desc) in enumerate(items):
        try:
            img = generate(brt, style.format(desc=desc), seed=i + 7, negative=negative)
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
