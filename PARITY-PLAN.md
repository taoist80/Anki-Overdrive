# Nav-Tree Parity Plan — match every screen to its source apk

**Context.** The 4.0.4 restyle + art passes are landed; now we walk the full navigation logic tree
(artifact: https://claude.ai/code/artifact/ea0e5fa4-88c1-484a-aff0-10df85042a6e) branch by branch and
bring each screen's **assets, layout, and behavior** to parity with the right original build. No single
apk covers everything, so each branch maps to its best source.

**Decisions (locked with the user):**
- **Mockup depth:** build screens that have an exact 4.0.4 scene *directly* (read the `.tscn`, match it);
  make an `/artifact-design` reference only where there's **no clean source or a real design call**
  (campaign, profile, store). Visual sign-off there before building — saves blind deploys.
- **Order:** full tree, **top to bottom**.
- **Source priority:** **4.0.4 default** (it's the restyle target and we have every scene), **2.6 for
  single-player/campaign content** and as a **general gap-filler** alongside **3.4.0**. Where a branch
  has no 4.0.4 scene, prefer whichever of 2.6 / 3.4.0 has the richer/clearer reference.

**Toolchain for sources:**
- **4.0.4 (Godot):** fully **GDRE-recovered** to text at `scratchpad/gdre_out/` — read the real `.tscn`
  for exact node tree / anchors / textures. Art via `tools/extract_ctex_art.py`.
- **3.4.0 (Unity Mono):** OBB Unity AssetBundles via **UnityPy** (`tools/extract_unity_art.py`) — art +
  some prefabs. Config/JSON readable.
- **2.6.10 (Unity IL2CPP):** OBB is RAMS→dead CDN for art; config JSON is readable (the bigger campaign
  ladder). UI **layouts not cleanly extractable** → those become design-call mockups.

---

## Per-branch matrix

Status: ✅ done · ◐ partial (restyled, refine to scene) · ☐ todo · 🎨 mockup-then-build (design call)

### Root / menu
| Branch | Route | Source | Action | Status |
|---|---|---|---|---|
| Main menu | `Home` | 4.0.4 `start.tscn` | reorder + rose wordmark | ✅ |
| Single Player hub | `SinglePlayer` | 4.0.4 `play.tscn` | refine 3-card to scene | ◐ |
| Extras hub | `Extras` | 4.0.4 extras tab | refine tiles | ◐ |
| Multiplayer | `Multiplayer` | 4.0.4 `multiplayer.tscn` + `join_game.tscn` | placeholder → real lobby (Phase 12) or keep | ☐ |

### Garage graph
| Branch | Route | Source | Action | Status |
|---|---|---|---|---|
| Garage home (carousel) | `GarageHome` | 4.0.4 `garage.tscn` (+`truckersGarage.tscn`) | refine to scene | ◐ |
| Vehicle detail | `VehicleDetail` | 4.0.4 `select_car_panel.tscn` | refine | ◐ |
| Weapon loadout | `WeaponLoadout` | 4.0.4 `select_weapon_panel.tscn` | refine | ◐ |
| Weapon picker | `WeaponPicker` | 4.0.4 `weaponPanel.tscn` | refine | ◐ |
| Upgrades | `GarageUpgrades` | 4.0.4 garage upgrade panel | build from scene | ☐ |
| Items / Daily / Item shop | `GarageItems`/`GarageDailySpecials`/`ItemShop(+Detail)` | 3.4.0 (4.0.4 is free-play, no shop) | 🎨 design call | 🎨 |

### OpenPlay graph
| Branch | Route | Source | Action | Status |
|---|---|---|---|---|
| Game mode select | `GameModeSelect` | 4.0.4 `game_modes.tscn` | refine to scene | ◐ |
| Game mode detail | `GameModeDetail` | 4.0.4 | build from scene | ☐ |
| Player select | `PlayerSelect` | 4.0.4 `player_panel.tscn` | build from scene | ☐ |
| Vehicle select | `VehicleSelect` | 4.0.4 `select_car_panel.tscn` | build from scene | ☐ |

### Race graph (shared)
| Branch | Route | Source | Action | Status |
|---|---|---|---|---|
| Match setup | `MatchSetup` | 4.0.4 `setup.tscn` | refine roster to scene | ◐ |
| Track scan + **live map** | `TrackScan` | 4.0.4 `track_scanning.tscn` + `TrackScanningMap.gd` | tiles carved (`ui/track/`); algorithm ported in notes; needs engine piece-string + on-track verify | ◐ |
| Countdown | `Countdown` | 4.0.4 `controller.tscn` countdown anim | done (3·2·1·GO) | ✅ |
| In-race HUD | `InRaceHud` | 4.0.4 `controller.tscn` | rebuilt 1:1 (3-col) | ✅ |
| Game over / Results | `GameOver`/`RaceResults` | 4.0.4 `results.tscn` + `leaderboard_panel.tscn` | build from scene | ☐ |
| Loot reveal (overlay) | `Overlay.LootReveal` | 4.0.4 `loot_box_panel.tscn` | refine to scene | ◐ |

### Campaign graph  — **no 4.0.4 scene (was "coming soon")**
| Branch | Route | Source | Action | Status |
|---|---|---|---|---|
| Chapter select | `ChapterSelect` | 2.6 content + 3.4.0 art (tint+commander) | 🎨 design call | 🎨 |
| Mission select | `MissionSelect` | 2.6 content + 3.4.0 commander art | 🎨 design call | 🎨 |
| Mission detail | `MissionDetail` | 2.6 content + commander portraits | 🎨 (portraits already wired) | 🎨 |

### Profile graph — **no 4.0.4 scene**
| Branch | Route | Source | Action | Status |
|---|---|---|---|---|
| Profile home / Avatar select+detail / Medals | `ProfileHome`/`AvatarSelect`/`AvatarDetail`/`ProfileMedals` | 3.4.0 (avatars/medals) if present, else design | 🎨 design call | 🎨 |

### Account graph
| Branch | Route | Source | Action | Status |
|---|---|---|---|---|
| Login / forgot / saved / signup flow | `AccountHome`/`AccountLogin`/… | 4.0.4 `login.tscn` + `setup_account.tscn` | build from scene | ☐ |

### Store / Tracks / Settings / singletons
| Branch | Route | Source | Action | Status |
|---|---|---|---|---|
| Store home + checkout | `StoreHome`/`StoreCheckout*` | 3.4.0 (4.0.4 free-play) | 🎨 design call | 🎨 |
| Coin shop | `CoinShop` | 3.4.0 | 🎨 design call | 🎨 |
| Tracks home / detail | `TracksHome`/`TrackDetail` | 3.4.0 track topology | build from data | ☐ |
| Settings (+Dev, BLE Lab) | `AppSettings`/`DevSettings`/`BleLab` | 4.0.4 settings (verify scene) / 3.4.0 | build from scene | ☐ |
| Guide / Notifications / Acknowledgements | singletons | keep (chrome-only) | restyle inherited | ✅ |

---

## Mockups (design-call screens only)
1. **Campaign** — chapter + mission select + mission detail. ✅ PUBLISHED
   https://claude.ai/code/artifact/f28b0362-48fa-4c31-ac86-cccc861ef143 (real campaign data + 3.4.0 portraits)
2. **Store / Coin shop / Item detail** — 3.4.0 economy in violet restyle. ✅ PUBLISHED
   https://claude.ai/code/artifact/a7f678b4-a11c-4996-b10e-40f0f3b2274b (real 135-icon item art)
3. **Profile** — home + avatar select + medals. ⛔ BLOCKED — **no avatar/medal art exists in any build**
   (4.0.4 / 2.6 / 3.4.0). Needs an art-generation decision (Stable Image Ultra, like the portraits) before mockup.
Each: one `/artifact-design` reference → user sign-off → build.

## Extraction still needed
- 2.6 OBB campaign config (chapter/mission ladder) — JSON, readable.
- 3.4.0 avatars/medals/store art via UnityPy — verify existence.
- 4.0.4 scene reads (per branch, on build) from `gdre_out/menu/pages/*.tscn`.

## Execution order (top to bottom)
Root menu → Garage → OpenPlay → Race (incl. **scan map**, results) → Campaign (mockup) →
Profile (mockup) → Account → Store/Coin (mockup) → Tracks → Settings → singletons.
Build-from-scene branches go straight in; design-call branches get a mockup + sign-off first.

## Verify
Per branch: read the 4.0.4 `.tscn` (or extract 2.6/3.4.0), build, `assembleDebug`, install, and where a
car isn't required, screencap on-device (landscape; uiautomator for taps). Merge `phase9-hud`→main once
the core loop (Race/Garage/Single Player) is parity-verified.

---

## Appendix — Scan track-map algorithm (ported from 4.0.4 `menu/TrackScanningMap.gd`)
The 4.0.4 minimap is a **turtle-walk** that lays the scanned piece-string onto an auto-growing 2D grid,
then renders each cell with a rotated track-piece tile. To implement in Compose:

**Inputs we must produce in the engine (not yet recorded):** the *ordered* sequence of pieces seen during
the scan lap, as a char string. Char map (`top_to_vis_char`): `S`=straight, `L`/`R`=left/right curve
(both draw the `left` tile, rotated), `I`=intersection, `G`=start/finish (built from `EB` = pre-finish +
finish), `J`/`H`=jump, `U`/`K`=landing, `Z`/`W`=FnF zone. Start/finish piece **id 33 or 34**; rotate the
string so it begins at `G`.

**Layout (`rebuild_track_pieces`):** turtle at (x,y)=(1,1), heading (dx,dy)=(1,0) on a 3×3 grid. For each
char: place piece at (x+dx, y+dy); `R` turns the heading right, `L` turns left (see the dx/dy swap tables,
lines 502–527); straights/zones/jumps keep heading. When the turtle reaches a grid edge, `pad_grid` grows
the grid on that side. Per-piece **rotation** (0/90/180/270) is a function of heading + type (match tables
at lines 133–170 / rot_val computed per char at 448–482). Cell size = `min(W,H)/gridDim`.

**Render:** each grid cell → the matching `ui/track/track-piece-*.png` tile (already carved), rotated,
sized to the cell, drawn in scan-arrival order (sorted by piece timestamp, line 190). Live car dot =
`ui/track/scanCar.png` placed on the current piece (the engine already tracks `roadPieceId`/`transitions`).

**⚠ Verification gap (must confirm on-track):** the 0x27 localization update gives `roadPieceId` +
`parsingFlags` but **not** left-vs-right curve handedness directly. 4.0.4 carried `L`/`R` in its
`RoadPiece.code`. We need to confirm how to derive L/R for a curve from `parsingFlags` + drive direction
(likely the reverse-parse bit). Until confirmed, the turtle layout can't be trusted — so this is the one
scan-map piece that needs the physical track to validate. Everything else (string recording, tiles, draw)
is verifiable offline.
