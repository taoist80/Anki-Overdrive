# OverdriveX — native rebuild (reusing DDL/Anki assets + logic)

## Decision (2026-06-25)
The closed DDL 2.6 binary runs on the Tab S10+ but is unfixable here (no source; native crash +
erratic driving + connection leak all in compiled `libDriveEngine`/IL2CPP). Our own app already
proved **rock-solid 4-car BLE**. So: **build the real experience in our Kotlin app (`android/`),
reusing as much of the original as possible** — assets, data, and logic — reverse-engineering the
parts we must.

## What we already have (the RE is largely done)
- **Full game logic, readable:** Anki 3.4.0 is **Mono** → `csharp-decompiled/` has **1,750 .cs
  files** (Assembly-CSharp): menus (`sb_*_ViewController`), `MatchService`, `CampaignService`,
  garage/XP/loot (`MetaGame`), driving via `DriveEngine.ExternalInterface` CLAD messages. This is
  the authoritative **spec** for how everything works. Map in [RECON.md](RECON.md).
- **BLE protocol:** UUIDs from the binary + message layouts from `anki/drive-sdk`
  (see `android/app/.../Protocol.kt`). Proven working multi-car.
- **Config / economy data (JSON):** `vehicleTypes`, `items`, `lootDrops`, `upgradeData`,
  `mutators`, `gameTypes/games`, `itemShopSchedule`, `medals`, localized strings — extracted to
  `reference/ddl-gamedata/`; reusable as-is.
- **Backend** (optional, for sync): scaffold in `server/`, spec in [BACKEND-REVIVAL.md](BACKEND-REVIVAL.md).

## Architecture: Kotlin + Compose (2D), not Unity 3D
Overdrive is a **physical-car** game — the race happens on the real track; the screen is
**menus + HUD + control**, with mostly **pre-rendered 2D car art** (the 3D was menu flourish).
So Compose + our BLE stack covers the real experience without a 3D engine, and keeps the toolchain
we already have building/installing. (A Unity rebuild would look more faithful but must reconstruct
the project *and* reimplement the native engine — far larger, and brings back the dependency we're
escaping. Revisit only if on-screen 3D becomes a must-have.)

## What we bring over
| From | What | How |
|---|---|---|
| DDL 2.6 + 3.4.0 Unity bundles | art (car renders, UI, icons, logo), audio (SFX/VO/music), fonts (`UniversLTStd`) | extract (AssetRipper / UnityPy) → Compose drawables/assets |
| OBB JSON config | car catalog, items/weapons, loot tables, upgrades, mutators, modes, strings | reuse directly (`reference/ddl-gamedata/`) |
| 3.4.0 decompiled C# | game logic/flow (modes, garage, XP, loot, menus, control messages) | port to Kotlin, using the .cs as spec |
| drive-sdk + our impl | BLE protocol + multi-car connection mgmt | done (`Protocol.kt`, `CarManager`) |

## Subsystems / phases (incremental)
1. **BLE foundation** — multi-car connect, clean connection lifecycle (no leak), auto-reconnect.
   *(largely done in the lab; harden + keep.)*
2. **Driving control** — localization (0x27 position updates) → lane-keeping / speed / lane-change
   control loop. This is the part the DDL game did badly; reimplement properly. Refs: 3.4.0 C#
   driving + `drive-sdk` + OpenOverdrive/Partydrive.
3. **Track model** — build the track map from car-reported piece IDs (scan lap).
4. **Car catalog + garage** — `vehicleTypes.json` + extracted car art; upgrades/inventory UI.
5. **Game modes** — race / battle / time-trial from `gameTypes/games.json` + 3.4.0 `MatchService`.
6. **Meta-game** — XP / loot / inventory / upgrades (configs + 3.4.0 logic) + local profile
   (optional sync to `server/`).
7. **UI/UX** — menus + in-race HUD with the extracted assets + `UniversLTStd`.

## Reference reimplementations (clean-room, study/borrow)
- `MasterAirscrachDev/Anki-Partydrive` — C#/Unity client+server, most complete control logic.
- `dschwen/OpenOverdrive` — Android BLE drive app (reliable-drive focus).
- `anki/drive-sdk` — protocol of record.

## Immediate next steps
1. **Full asset extraction** from DDL 2.6 (APK `assets/bin/Data` + OBB bundles) → organized
   art/audio/fonts. (UnityPy got in-APK; OBB asset bundles need targeting / AssetRipper.)
2. **Map the 3.4.0 driving/control code** as the spec for Subsystem 2 (the control loop).
3. Fold the config data + assets into the `android/` app and start Subsystem 4 (garage/car-select)
   as the first visible screen using real art.
