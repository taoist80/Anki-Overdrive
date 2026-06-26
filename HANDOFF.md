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
- **Phase 9 IN PROGRESS** (`dc675da` + garage/car-select work below) — catalog + HUD icons + garage
  weapon-loadout flow + full-roster car select (built & installed; on-device visual verify pending unlock).

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

## Phase 9 — garage loadout + car select (DONE, built+installed; on-device verify pending unlock)
Design spec artifact (4.0.4 layout, app palette, real data): published this session.
1. **Garage weapon-select loadout — DONE.** Flow: Garage → car → **Car Detail** (`VehicleDetailScreen`,
   was a wireframe stub; now hero card + WEAPONS/UPGRADES action cards, WEAPONS previews equipped
   attack+support canisters) → **WeaponLoadout** (`WeaponLoadoutScreen`, new route `WeaponLoadout(carId)`:
   one card per bay — ATTACK + SUPPORT always, SPECIAL only when the car has special items) → **WeaponPicker**
   (`WeaponPickerScreen`, new route `WeaponPicker(carId,bay)`: `LazyRow` of every equippable weapon + a
   "No Weapon" clear card; tap equips & pops back, highlights current). Equips via `ProfileRepository.equipItem`
   (`carId:bay→itemId`); HUD/combat read `loadoutFor(carId)` live. Art via `rememberAsset(ItemRepository.imageAsset(id))`.
   All in `ui/screens/GarageScreens.kt` (+ routes in `nav/Routes.kt` & `nav/OverdriveNavHost.kt`).
   **Default weapon w/ art:** `ItemRepository.defaultItem(bay,car)` / `defaultLoadout(car)` = lowest-level
   equippable WITH `-large` art (Groundshock → ElectroPulse / Tachyon Disruptor). `equippableFor` now
   junk-filters 4 nameless internal rows (`ZFXDisable`, `NukeParentShortRange*`). `Combat.init` defaults
   player-fallback + AI to `defaultLoadout` instead of the art-less `base_machine_gun`/`base_shield`.
2. **Full-roster car select — DONE.** `MatchSetupScreen` rebuilt: tabs **Supercars/Supertrucks/Drive**
   (new `CarType.category` in `GameData`, derived: `voice_id=="Gen1"`→Drive; `extends∈{102,103}`+Mammoth→
   Supertrucks; else Supercars) → `LazyVerticalGrid` of ALL cars in the tab. Per-card `Slot`: PLAYER(gold,
   "P1·YOU")/CONNECTED(green,"TAP TO SET P1")/NEARBY(blue,"TAP TO CONNECT"+rssi)/OFFLINE(grey,"POWER ON"),
   matched to `mgr.cars`/`mgr.found` by `modelId`. Tap = set P1 / connect / no-op. Connected cars whose
   modelId doesn't resolve are appended so none are lost. Kept the laps stepper + Scan Track CTA.
3. **Weapon/car art — DONE (carved from 4.0.4 .ctex).** 4.0.4 imports textures as
   CompressedTexture2D `vram_texture:false` → each `assets/.godot/imported/*.ctex` embeds a lossless
   **WebP** (carve the RIFF/WEBP blob; no gdsdecomp/Ghidra). `tools/extract_ctex_art.py [weapons|cars|all]`
   unzips the apk, carves, and bundles: **28 missing weapon renders** → `assets/items/*.webp`
   (`imageAsset` now checks `-large.webp`/`-medium.webp`; Android decodes WebP natively); **Dynamo +
   Mammoth car silhouettes** (DDL had no F&F cars) → real renders in `assets/cars/*.png` (carved→PIL→PNG,
   720 from 4.0.4 + 420 downscaled). Also: `ItemRepository.equippable()` = named (non-blank) AND
   `detailImage != null` → hides the art-less `NukeParent*` template family (was showing as generic
   class-icon placeholders that duplicated real Gen1 weapons) + `ZFXDisable`. Only `DynamoEbrakeL01`
   still lacks a render (no 4.0.4 source) → 1 hitzone-icon fallback.
4. **4.0.4 visual restyle — IN PROGRESS (centralized theme pass DONE, build-verified).**
   GOAL: reskin our screens to 4.0.4's look — **purple/violet nebula** backgrounds, **two-tone italic
   racing names**, translucent dark-violet **rounded cards** w/ soft borders, **glowing blue holo**
   accents; main menu reordered to **EXTRAS | SINGLE PLAYER | MULTIPLAYER**. Layout/structure is already
   4.0.4-shaped — this is mostly a *theme + chrome* pass, not new screens.
   **DESIGN SPEC ARTIFACT (palette/type/components/logic-tree/mocks/motion all calibrated to the 6 ddl404
   captures):** https://claude.ai/code/artifact/ea0e5fa4-88c1-484a-aff0-10df85042a6e (WebFetch it; built in
   the new violet system itself, with a live nebula canvas + the full navigable screen inventory).
   **DONE this pass (centralized, `assembleDebug` green):** `Theme.kt` `OverdriveColors` → violet palette
   (background `#140A22` void, panel `#241A3A`, `blue`→holo `#4FB0FF`, textPrimary `#F4ECFA`, textDim
   `#AE95C8`, panelBorder → white@14%, **new `rose #ECA9CE`** for the wordmark, gold kept as commit CTA).
   `Components.kt`: `OverdriveBackground` now paints a **procedural violet nebula** (layered radial clouds +
   vignette via `drawBehind`, no bundled art needed; `heroImage` defaults null, overlays carved art later);
   `OverdrivePanel` = 14dp gradient card + soft border; `BackChip` = rounded translucent square; `PrimaryButton`
   outline → holo. `RacingName` **promoted** to `Components.kt` (public, `hlColor` param, now italic) — private
   copy removed from `GarageScreens.kt`. `OverdriveScaffold`/`WireframeScreen` `heroImage` defaults → null.
   `HomeScreen` wordmark → rose ANKI/OVERDRIVE. **Not yet on-device verified** (needs unlock/screencap).
   **REMAINING:** (a) carve nebula/HUD/icon art (extend `extract_ctex_art.py` w/ a `ui` mode → `assets/ui/`);
   (b) HomeScreen menu **reorder** to EXTRAS|SINGLE PLAYER|MULTIPLAYER (needs the 3 grouping screens — Single
   Player = Campaign/Open Play/Test Track 3-card; this is the only "new screens" bit); (c) in-race HUD rebuild
   (hardest, below); (d) per-brand racing-name colors on car cards. KEY LEVER (still true for what's left):
   it's centralized in `Theme.kt` + `Components.kt`.
   ASSETS (carve from 4.0.4 via `tools/extract_ctex_art.py` — extend it with a UI mode; same WebP carve):
   `Assets/Background/{base,extended}.png` (nebula bg), `Assets/BaseUI/simplegradient.png` (card gradient),
   `Assets/BaseUI/controller_overlay.png` (in-race HUD frame), `Assets/BaseUI/icons/{damage,defense,
   incoming,accel,topSpeed,anki_coin_v4,lock,PlaceOnTrack}.png` (HUD/UI icons), `Assets/UILogo/*`,
   `Assets/BaseUI/title/*`. Bundle to `assets/ui/`.
   REFERENCE: `reference/screenshots/ddl404/` (01_main_menu, 02_singleplayer, 14_extras, 15_garage,
   16–18 garage/weapons, 20_settings) + the published design-spec artifact (palette/type already
   calibrated): https://claude.ai/code/artifact/ccf33bd9-d857-48d4-9965-864d6ce93d8c (WebFetch it).
   Load skill `artifact-design` for any new mockups — user confirmed it's available.
   HARDEST PART: in-race HUD parity — rebuild `InRaceHudScreen` (`ui/screens/RaceScreens.kt`) around
   `controller_overlay.png` + the damage/defense/incoming/accel/topSpeed icons (matches `controller.scn`:
   TouchScreenButtons w/ weapon metadata + canister art). User OK'd **reinstalling 4.0.4** to screenshot
   the live controller for exact parity — **uninstall it after** (duplicate "Overdrive" icon caused a
   wasted test cycle before; see "App identity" up top).
   VERIFY: build/install per toolchain; device is landscape — get tap coords from `adb shell uiautomator
   dump` (visual coordinate estimates were unreliable for small targets like tabs), screencap when unlocked.

## Phase 9 — economy/progression (DONE this session; user chose "Full 3.4.0 progression" over 4.0.4 free-play)
Verified on-device (device unlocked): Car Detail, Weapons Loadout, Weapon Picker (attack+support, real
art, ownership), Item Shop (prices+art), Match Setup roster (Supercars/Supertrucks tabs), Dynamo+Mammoth
renders. In-race upgrade EFFECTS need cars-on-track to confirm (not yet driven).
- **Upgrades are now functional + per-car** (`Routes.GarageUpgrades(carId)`, keyed `"$carId:$track"`):
  `MetaGame.speedMult/damageMult/defenseMult/energyMult(level)` (per-level: speed +5%, weapons +8%,
  defense −8% incoming, energy +12%; maxLevel 5). `RaceEngine.start()` reads the player car's levels →
  `playerSpeedMult` (applied in `effectiveSpeed`, straight-line only, hard-capped `SPEED_CEIL=1000`;
  curves stay 450 for hardware safety) + `Combat.PlayerMods(damage/defense/energy)` passed to `combat.init`
  (applied in `applyHit`/regen for the player car only; AI = 1f).
- **Weapon ownership gating:** `Profile.owns(item)` = **level-1 starters are free-owned**, L2+ must be
  looted/bought. Picker: owned→equip; locked→**Buy** (`spendCoins`+`addItem`+`equipItem`) or greyed if
  unaffordable. `ItemRepository.itemPrice` = `cost` else `200·level²`.
- **Item Shop + Daily Specials functional:** `ItemShopScreen` = grid of all buyable (L2+) weapons →
  `ItemShopDetail` (stats + Buy). `GarageDailySpecialsScreen` = epoch-day-seeded 6 picks at 25% off, inline
  buy. All in `GarageScreens.kt`.
- **4.0.4 weapon-impact research (answered):** weapons are client-side virtual (cars carry none) — hits
  drain virtual health (→disable/spin-out) + apply status effects expressed as BLE speed/lane changes
  (tractor ×0.35, gravity ×0.08+nosteer, scrambler=invert, shield/boost). Per-weapon stats (damage/energy/
  cooldown/range/effects/durations) already come from the real items.json. The *magnitudes* we approximate
  (MAX_HEALTH=100, the slow factors, respawn) live in the **compiled `libDriveEngine.so`** (3.4.0 Mono
  build, `overdrive-decompiled/lib/armeabi-v7a/libDriveEngine.so`) as `VehicleSpeedItemEffect.force` — NOT
  in the JSON config (effects there are booleans + duration) and NOT readable in 4.0.4 (.gdc). **Ghidra on
  libDriveEngine.so is the path** to extract the real combat constants if exact parity is wanted (user has
  Ghidra). AI item-use conditions + the real item-shop rotation ARE in config (`characters/itemUse/*.json`,
  `gameData/itemShopSchedule.json`) and can be wired for more fidelity.

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
