# Parity Initiative — Handoff / Resume Doc

Snapshot for continuing the full-parity work in a fresh chat. The project memory
(`anki-overdrive-modernization`) also auto-loads with this state; this file is the human-readable
version. Plan: `/Users/jgilgen/.claude/plans/using-this-url-https-chaostree-xyz-games-mossy-lemon.md`.
Recon details: `PARITY-RECON.md`.

## App identity (important!)
- **Our app = `dev.overdrive`, label "OverdriveX"** — this is the rebuild we work on.
- The DDL 4.0.4 reference app (label "Overdrive", `com.digitaldreamlabs.retrodrive`) was installed for
  screenshots then **UNINSTALLED** because the duplicate "Overdrive" icon caused a wasted test cycle
  (the user tested it by mistake and hit *4.0.4's* native bugs). Reinstall from `reference/ddl404`
  (re-sign: it's unsigned — `zipalign` + `apksigner` with `~/.android/debug.keystore`, pass `android`)
  only when you need 4.0.4 screenshots; remove it again after.

## Build / install / test (toolchain)
```
export JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=$HOME/Library/Android/sdk
cd android && ./gradlew --no-daemon --console=plain assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p dev.overdrive -c android.intent.category.LAUNCHER 1
```
- Device `R52Y606JA8N`, **landscape 2800×1752** for `input tap`. Screencap works when unlocked.
- `tools/screenshot.sh <out.png> [xfrac yfrac] [sleep]` — tap a fractional coord + screencap.
- Logs: `adb logcat -d -s OverdriveX:I` (clear first with `adb logcat -c`; the system BLE scanner
  spams the buffer, but our app now stops scanning while driving so driving logs are visible).
- Backend: `cd server && npm start` + `adb reverse tcp:8080 tcp:8080`.
- The 4 cars (Nuke=11, Dynamo=19, Freewheel=15, Mammoth=18) connect over BLE. **Don't drive cars that
  aren't confirmed on a track** (can roll off a surface).

## Status by phase
- **Phases 0–5: DONE** (pre-initiative) — UI shell, 3.4.0 content, BLE driving, campaign, backend.
- **Phase 6 DONE** (`0889037`) — acquisition/recon/extraction. Key results in `PARITY-RECON.md`:
  4.0.4 Godot project fully unpacked in its apk `assets/`; 20 UX screenshots in
  `reference/screenshots/ddl404/`; Drive 2.4.5 decompiled + config extracted (11-commander ladder);
  **original audio is Wwise** (2788 `.wem`→`reference/audio/wav/` via `tools/extract_audio.sh`).
- **Phase 7 DONE** (`0f9dccf`,`19a62fa`) — `data/ItemRepository` real item catalog, `Profile.loadout`,
  real loot.
- **Phase 8 DONE** (`0bec808`,`3912321`) — `game/race/Combat.kt`: weapons fire in races (client-side
  virtual; speed/lane modulation), health/energy/cooldowns/effects/disable, mode-gated (Race/TimeTrial
  weapon-free), HUD bay triggers.
- **On-track fixes** (`d5d7071`,`6b4f989`,`f0b5479`,`7896e96`) — see below.
- **Phase 9 IN PROGRESS** (`dc675da`) — comprehensive catalog + HUD weapon icons (see below).

## Verified on-track (our app drives correctly)
Logs show OUR engine drives clean: laps count, curve cap engages, `off=false` (no spin/delocalize) —
the earlier "spinning/backwards" was the **DDL 4.0.4 app**, not ours. Confirmed working: scan lap,
fair staged start, lap target + winner finish, off-track stop, curve cap.

## The lap-counting bug (fixed `7896e96`)
Laps were counted on every entry to finish-piece **id 33**, but that id **re-registers >1×/physical
lap** (logs: 4–5-segment "laps" amid ~8-segment real laps → a 5-lap race ended after ~4 laps,
mid-track). Fix: measure **segments-per-lap during the scan**, then **debounce** — a lap only counts
if the car drove ≥¾ of a lap's segments since the last one. Each counted/IGNORED lap is logged.
`RaceEngine.kt`: `segsPerLap`, `lapStartSeg`, the debounce in `onPosition`. **Needs a user retest**
(short 3-lap Race) to confirm; that test was queued when we paused.

## Race flow (current)
Match Setup (LAPS-TO-FINISH stepper) → **Scan Track** (`RaceEngine.startScan`: map a lap @300mm/s,
then `stage` each car at finish piece for a fair start) → Countdown → Go (`start()`) → HUD → first car
to `lapTarget` ends the race (`finishRace`) → Results. Default throttle 0.55.

## Phase 9 — done so far (`dc675da`)
- **Comprehensive weapon catalog:** Overdrive **3.4.0 `items.json` is a complete SUPERSET** (208 real
  items incl. F&F Dynamo/Ghost/Mammoth + Gen1; DDL-2.6-only = 0). Now bundled as
  `assets/gamedata/items.json` (was DDL's 141). `ItemRepository` loads 208 (113 atk/86 sup/9 special).
- **Item art:** already decoded in `assets-extracted/ddl/images/` (`<id>-large.png` + hitzone icons)
  → bundled to `assets/items/` (164 files). `ItemRepository.imageAsset(id)` resolves art (detail
  render, else weapon-class hitzone icon). **In-race HUD bay buttons show the weapon icon + ATTACK/
  SUPPORT label**; Race mode shows "NO WEAPONS — play BATTLE".

## Latest test findings (campaign tutorial mission, mission_01)
- **Lap debounce CONFIRMED working** — log: `RACE.lap IGNORED finish re-trigger @seg 10 (gap 4 < 5)`
  then clean laps 1→2→3, finish at 3. The over-count bug is fixed.
- **Scan hang ROOT CAUSE + FIX (committed):** the 2nd car (Dynamo) **never localized** (`piece=-1`
  the whole run — not on the track / charger / weak link), and scan required *all* cars staged → hung.
  Fixed: scan now completes when every car that has **localized** (roadPieceId ≥ 0) is staged; a
  `piece=-1` car no longer blocks it (45s timeout still the backstop). Scan screen shows "place on
  track" for a non-localized car.
- **"AI didn't race"** = same dead 2nd car (stayed `piece=-1 spd=0` in the race too). Hardware, not
  software — ensure both cars are on the track and charged before racing.
- **Weapons in tutorial:** mode `tutorial` ≠ race, so weapons ARE enabled and the HUD bay buttons show.
  Two reasons it felt off: (a) firing hit nothing (the only opponent was the dead car), and (b) the
  **default loadout is abstract base items** (`base_machine_gun`/`base_shield`) which only have generic
  hitzone icons, not canister art — so icons looked "not updated". Fix in the garage task below:
  default the loadout to a real leveled item (with `-large.png` art) and let users equip real weapons.
- Note: campaign mission game_types — mission_01 `tutorial`, 02 `onboarding_battle_cam`, 03
  `onboarding_battle_brick`, 04/05 `race`, 06 `battle`. Only `race`/`time*` disable weapons.

## Phase 9 — remaining (next chat starts here)
1. **Garage weapon-select loadout** — the 4.0.4 flow: Garage → car → **WEAPONS** card → 3 bay slots →
   **weapon picker** (horizontal cards w/ art + name + level, like screenshots
   `reference/screenshots/ddl404/16_car_detail.png`,`17_weapons_loadout.png`,`18_weapon_picker.png`).
   Equip into `Profile.loadout` (`carId:bay→itemId`, helper `equipItem` exists). The in-race HUD + combat
   already read `loadoutFor(carId)`, so equipping will immediately drive what fires. Screens to rebuild
   from stubs: `ui/screens/GarageScreens.kt` (GarageItems / ItemShop are WireframeScreens). Use
   `rememberAsset(ItemRepository.imageAsset(id))` for art.
2. **Full-roster car select** — Match Setup currently lists only powered-on BLE cars; user wants the
   4.0.4 selector: **all cars** (`GameData.cars`, tabs Supercars/Supertrucks/Drive) with the
   nearby/connected ones selectable (match `mgr.found`/`mgr.cars` by `modelId`), others greyed
   "power on". File: `ui/screens/RaceScreens.kt` `MatchSetupScreen`.
3. **F&F weapon detail art** — 60 F&F items fall back to hitzone icons (DDL 2.6 had no F&F cars). RE the
   real canister art: `foxtrot.xapk` OBB Unity assets via UnityPy (`scratchpad/extract_all.py` pattern),
   or 4.0.4 `.ctex` (`Assets/CarWeapons/<Car>/<id>-large.png`) via gdsdecomp; drop PNGs into
   `assets/items/`.
4. **4.0.4 visual restyle** — the user wants screens to look like 4.0.4 (main menu EXTRAS|SINGLE PLAYER|
   MULTIPLAYER; the card styling in the screenshots). 4.0.4 in-race HUD spec = its `controller.scn`
   (TouchScreenButtons w/ weapon metadata + canister art) + `Assets/BaseUI/controller_overlay.png` +
   icons (damage/defense/incoming/accel). User OK'd reinstalling 4.0.4 to screenshot the live in-race
   controller for exact parity.

## Phases 10–13 (not started)
- **10 Story parity:** unblock the 31 non-evaluable Overdrive stars (PREF_CAR/BANNED_ITEM/NO_DEATHS —
  combat now tracks deaths; add item/vehicle tracking) in `CampaignEngine`; fold Drive 2.4.5's
  11-commander ladder + cutscene/overlay playback (`MissionStory` overlay exists).
- **11 UX completion:** fill remaining `WireframeScreen` stubs (Account/Profile/Store/Tracks), visual
  track-scan map, FTUE.
- **12 Local multiplayer (FULL, user choice):** server WebSocket race-room (host-authoritative), each
  device owns its BLE cars, broadcast state; lobby (join_game/multiplayer).
- **13 Audio + animations:** wire the extracted Wwise corpus (`reference/audio/wav/`: 446 weapon SFX,
  music, UI, per-commander VO) via SoundPool/ExoPlayer to UI/race/weapon/countdown; opening/countdown/
  hit animations in Compose (no source videos — cutscenes are scripted overlays). User wants ORIGINAL
  audio only. Precise event→wem mapping needs wwiser on the `.bnk`.

## Key files
- Driving/combat: `android/app/src/main/java/dev/overdrive/game/race/{RaceEngine,Combat,RoadPieces}.kt`,
  `Protocol.kt`, `AnkiCar.kt`.
- Items/economy: `data/ItemRepository.kt`, `data/model/Items.kt`, `game/MetaGame.kt`,
  `profile/ProfileRepository.kt`, `net/BackendClient.kt`, `server/`.
- UI: `ui/screens/{Race,Garage,Campaign,...}Screens.kt`, `ui/theme/Theme.kt` (`rememberAsset`),
  `ui/components/`, `nav/Overlay.kt`.
- Spec (read-only): `csharp-decompiled/` (3.4.0), `reference/drive-2.4.5/csharp/`, `reference/config-3.4.0/`,
  `reference/ddl404/` (4.0.4 Godot), `reference/screenshots/ddl404/`.

## Gotchas
- Never `import androidx.compose.foundation.layout.weight` (internal-prop clash). `LaunchedEffect`/
  `width` must be explicitly imported.
- KDoc comments must not contain `*/` (e.g. write "Gen1/Groundshock" not "Gen1*/Groundshock*").
- vgmstream `-o -` (stdout) only emits a header stub — write `.wem`→ a real `.wav` file.
- BSD `xargs -I{}` caps the command at 255 bytes — use a helper script for long commands.
- `reference/`, `assets-extracted/`, `*.apk/xapk/obb/zip`, and `android/app/src/main/assets/` are
  gitignored (derived/proprietary) — bundled catalog/images/audio are local, regenerate via extraction.
