# Parity Initiative — Phase 6 Recon (Acquisition, Extraction, UX Capture)

Foundation pass for the full-parity initiative (Phases 6–13). Everything here is the **reference
material** that Phases 7–13 build from. All large binaries / extracted assets live under
`reference/` (gitignored); the reusable extraction scripts (`tools/`) and this doc are committed.

## 1. Acquisition (Phase 6a) — all verified

Downloaded from `https://chaostree.xyz/CDN/Resources/OV/` → `reference/downloads/`:

| Build | sha256 (head) | Verdict |
|---|---|---|
| `Overdrive 4.0.4.apk` (85.3 MB) | `5402dadc…` | **Identical** to existing `reference/ddl404/Overdrive-4.0.4.apk` |
| `Drive 2.4.5.xapk` (132.9 MB) | `a352baa9…` | **Identical** to on-disk `drive.xapk` (matches archive.org Drive 2.4.5) |
| `Overdrive 3.4 FT.apk` (198.7 MB) | `a7fc612c…` | **NEW** but **CORRUPT** — see below |

**F&F "patched" apk is unusable as-downloaded.** Its zip central directory is malformed
(`unzip`/`jar`/`python zipfile` all reject it; `adb install` → `INSTALL_PARSE_FAILED_NOT_APK`).
The Google-Drive mirror could be retried later, but it is **not a blocker**: F&F 3.4 content
overlaps Overdrive 3.4.0 (which we already extracted in full), and `foxtrot.xapk` (the real F&F
edition, `com.anki.foxtrot`) is on disk for any F&F-specific content. Action: deprioritized.

## 2. 4.0.4 UX capture (Phase 6b) — the UX spec to mirror

4.0.4 (`com.digitaldreamlabs.retrodrive`, code 404) is **unsigned**; re-signed with the debug key
(`tools/`… via apksigner) and installed over 2.6.10. 20 screenshots in
`reference/screenshots/ddl404/` walking the full tree. Capture helper: `tools/screenshot.sh`.

**4.0.4 is a Godot build whose entire project is unpacked in `assets/`** — every UI scene is present
(`reference/ddl404/Overdrive-4.0.4.apk` → `assets/.godot/exported/...`):
`menu`, `start`, `game_modes`, `play`, `setup`, `controller`, `garage`, `truckersGarage`,
`weaponPanel`, `select_weapon_panel`, `select_car_panel`, `car_panel`, `track_scanning`,
`loot_box_panel`, `results`, `leaderboard_panel`, `multiplayer`, `join_game`, `login`,
`setup_account`, `player_panel`, `profile_car_select_button`. Game logic is in compiled
`assets/**/*.gdc` (GDScript bytecode — decompile with gdsdecomp if needed). Notable extra game
modes seen in `.gdc`: **HyperRace, KingOfTheTrack, OneShot** (on top of Race/Battle/BattleRace).

UX tree (screenshot → meaning):
- `00`–`01` boot: location-permission gate → **main menu = EXTRAS | SINGLE PLAYER | MULTIPLAYER**.
- `02` Single Player = **CAMPAIGN (COMING SOON) | OPEN PLAY | TEST TRACK**. *4.0.4's campaign was
  never shipped* → story content must come from 3.4.0 + Drive (as planned); 4.0.4 = UX shell only.
- `03` Open Play = mode cards (RACE / BATTLE / BATTLE RACE / …) with per-mode tint.
- `04` Race match-setup = **PLAYER(+RANK) / ADD BOT** | **YOUR VEHICLE** | **GAME SETTINGS (lap ±)**.
- `05` Car select = tabs **SUPERCARS | SUPERTRUCKS | DRIVE** (Gen-1 cars appear here), hero art + level + SELECT.
- `14` EXTRAS = **item store** (weapon canisters w/ coin prices) + tabs `? | SETTINGS | GARAGE`.
- `15`–`18` Garage: car roster (tabs Supercars/Supertrucks/Drive) → car detail (**WEAPONS** + **UPGRADES** cards)
  → **WEAPONS loadout = 3 bay slots** ("NO WEAPON") → **weapon picker** (horizontal scroller: NO WEAPON / BOOST / EMP / …, each with art + flavor text + level slider). This is `select_weapon_panel`.
- `10`–`12` Multiplayer = **online** account gate (SELECT ACCOUNT TYPE → LOGIN/REGISTER, "pre-2025
  accounts will not work"). 4.0.4 MP is DDL-server based; **our Phase-12 MP is local same-Wi-Fi**, a
  different model (mirror the lobby look, not the online backend).
- `20` Settings = IN GAME (steering sensitivity), **AUDIO (music volume)**, HAPTICS, DEBUG.

## 3. Drive 2.4.5 content (Phase 6c) — extracted, decompiled

- APK `Assembly-CSharp.dll` → **341 .cs** in `reference/drive-2.4.5/csharp/` (Mono; the Drive logic
  spec). Campaign UI classes: `CommanderModule`, `CommanderChallengesModule`, `CommanderGameData`,
  `CommanderSelectBehaviour`, `CommanderDetailBehaviour`, `CommanderScoreTallyOverlay`, …
- OBB config → `reference/drive-2.4.5/obb_main/assets/resources/basestation/config/`:
  - **Campaign = a "Commanders" ladder.** `campaign/{campaigns,missionGroups,missions}.json` →
    `mgroup_commanders` (10) → `mgroup_bosses`. 11 commander battles with display names from
    `missions.json` `opponent_name`: Noobzor, Debby Diesel, Trancendent Joe, Darkwyve, Rufus
    Barksworth, Lt. Payne, HK-47, Sparrow, TruckNutz, Sid Turbo (boss). Each = a battle game at
    rising `ai_difficulty`/`vehicle_level`.
  - `characters/star_challenges.json` (**30**, same `rule/param1/param2/description/reward_points`
    shape as Overdrive — folds straight into our CampaignEngine model).
  - `gameData/items.json` (Gen-1 items; same `{item_bays, items, effects}` schema as DDL),
    `vehicleTypes.json`, `medals.json`, `progression/upgrade_tree_base.json` + `upgradeData.json`.
- **Sprites** (commander portraits `commander_*.png`, item icons) live in the **277 MB patch OBB**
  (`patch.56…obb` → Unity `sharedassets*.assets.split*`); extract via UnityPy in Phase 10 (deferred).

## 4. Original audio (Phase 6d) — Wwise, fully extracted ✅

**The original audio is Audiokinetic Wwise, NOT FMOD** (the prior "FMOD .bank" note was wrong).
3.4.0 OBB → `assets/rams/overdrive/basestation/audio/*.zip`; each zip = one SoundBank: a `.bnk`
(event graph) + N `.wem` (Wwise Vorbis media). `.wem` → wav via **vgmstream-cli**
(`tools/extract_audio.sh` + `tools/_wem2ogg.sh`). **2788/2788 .wem converted (100%)** →
`reference/audio/wav/<bank>/<wwiseId>.wav` (609 MB), categorized:

| Bank | wav | Use |
|---|---|---|
| `OverDrive_SFXBank_Items` | **446** | weapon/item SFX (maps to items.json `sound_` keys) |
| `OverDrive_SFXBank_UI` | 88 | menu/nav SFX |
| `OverDrive_SFXBank_Car` / `_Throttle` / `_Ambience` | 79 / 14 / 5 | engine/throttle/ambience |
| `OverDrive_MusicBank` | 45 | music |
| `OverDrive_VOBank_{A5H,Skits,Metro,Charge,Vice,Cam}_{English(US),German}` | 474/243/84/85/84/85 ×2 | **commander voice-over** (campaign/cutscenes) |

`SoundbankBundleInfo.json` maps bundle→soundbank→language. **Open item for Phase 13:** precise
event-name→wwiseId mapping (e.g. `boost_use`→a specific .wem) needs `.bnk` HIRC parsing (wwiser);
until then, bank category + duration is the selector. Minor gap: `Shared_SFXBank_Rev_*` (engine-rev
per real car) and `Init/Global_Data/Onboarding` banks held only `.bnk` (no inline `.wem`).

4.0.4 music (DDL's newer tracks) extracted too → `reference/audio/4.0.4/`: `midrace.mp3`,
`postrace.mp3` (carved from Godot `.mp3str`), `main.wav` (Godot `.sample`).

**No video assets exist** in any build. "Cutscenes" are **scripted overlays**
(`reference/config-3.4.0/.../campaign/cutscenes.json` = dialogue/camera/emotion sequences) and
intro/countdown were in-engine animations → Phase 13 reproduces them in Compose (no video playback).

## 5. Tooling produced (committed)
- `tools/extract_audio.sh` + `tools/_wem2ogg.sh` — Wwise bank → wav (vgmstream, parallel).
- `tools/screenshot.sh` — tap a fractional coord on the tablet (2800×1752 landscape) + screencap.
- Deps confirmed: vgmstream-cli, ffmpeg, ilspycmd (`~/.dotnet/tools`), UnityPy 1.25, Ghidra 12.1.2,
  apksigner/zipalign/aapt2 (build-tools 34/35). Device `R52Y606JA8N`, screencap works unlocked.

## 6. Net effect on the plan
- **Weapons (P7–9):** items/effects schema confirmed identical across DDL/Drive; combat stays
  client-side virtual; 4.0.4 weapon-picker + 3-bay loadout UX captured; 446 weapon SFX in hand.
- **Story (P10):** Overdrive 3.4 campaign already bundled; Drive = an 11-commander ladder + 30 stars,
  extracted; commander VO available for both.
- **UX (P11):** every 4.0.4 scene name + screenshots captured as the mirror spec.
- **Multiplayer (P12):** 4.0.4 MP is online-only; build our own local same-Wi-Fi model.
- **Audio/anim (P13):** unblocked — full original Wwise corpus; animations are Compose-reproduced.
