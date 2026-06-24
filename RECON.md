# Recon — `com.anki.overdrive` v3.4.0 architecture map

Source: `apktool` (smali+res) + `ilspycmd` decompile of `Assembly-CSharp.dll` (3.2 MB →
1,750 `.cs` files in `csharp-decompiled/Assembly-CSharp/`). This maps what's editable and
where, so Track 1 (modernization) and Track 2 (refactor/items) become concrete edit lists.

## Layered architecture

```
┌─ Android/Java shell (smali — apktool) ──────────────────────────────┐
│  com.anki.AnkiActivity              (Unity activity host)            │
│  com.anki.bluetooth.le.LeService    (Android BLE GATT — a Service)   │  ← Track 1 perms + lifecycle
│  com.anki.bluetooth.le.LePeripheral, com.anki.utils.DeviceUtils      │
│  com.anki.util.http.HttpAdapter, com.anki.daslib.DAS (telemetry)     │
└─────────────────────────────────────────────────────────────────────┘
            │ JNI / UnitySendMessage
┌─ Unity + Mono managed C# (assets/bin/Data/Managed/*.dll) ───────────┐
│  UI / view-controllers (Anki.Core.UI, *_ViewController, sb_*)        │  ← Track 2: UI/QoL
│  MetaGame: garage, inventory, upgrades, cosmetics, loot, item shop   │  ← Track 2: cars/items
│  Campaign/Match/GameMode orchestration (data-driven via JSON)        │  ← Track 2: modes
│  Account/* UI flow; AccountService                                   │  ← Priority 4 (offline)
│  Anki.Core.Utils.NativeLibMessageReceiver  (native→C# inbox)         │
│  Anki.DriveEngine.ExternalInterface.*  (CLAD messages ↔ native)      │
└─────────────────────────────────────────────────────────────────────┘
            │ [DllImport] P/Invoke  +  CLAD-serialized messages
┌─ Native C++ (lib/armeabi-v7a/) ─────────────────────────────────────┐
│  libDriveEngine.so (15M)  — BLE protocol, vehicle control,           │  ← Ghidra ONLY if needed
│                             networking, ACCOUNT AUTH, physics        │
│  libunity.so, libmono.so, libDAS.so, libc++_shared.so, FMOD          │
└─────────────────────────────────────────────────────────────────────┘
```

**Key insight:** account auth + networking + BLE are in **native `libDriveEngine.so`**,
surfaced to C# only as CLAD callbacks (`AccountLoginCallback`, `CreateAccount`,
`ConnectVehicleForPlayer`, …). The real backend host is compiled into native, not in C#
strings — so the dead-backend bypass may need to happen at the C#-orchestration or
CLAD-message level, not by editing a URL constant.

## Subsystem map (Track 2 targets)

### Cars / tuning / items  — `Anki.DriveEngine.ExternalInterface[.MetaGame]/`
`VehicleType`, `UpdateVehicleTypes`, `GameUpgrade`, `GameVehicleAddOn`, `UpdateGameUpgrades`,
`AddItemsToVehicleInventory`, `AddUpgradesToVehicleInventory`, `EquipUpgradesToVehicleInventory`,
`NewUpgradesUpdated`, `GameItem`, `LootDropBox`/`LootReceived`, `Commander`/`Avatar*` (driver
cosmetics). Garage/shop UI: `CarPoolView/Filter`, `ItemShopService`, `VehicleSkinData`.
→ "Add items" maps directly onto the inventory/upgrade CLAD messages + `MetaGame` data.

### Game modes / campaign / match
`Anki.MatchService/` (`Match`, `Player`, `Slot`, `Vehicle`), `Anki.RequestService/GameModeRequest`,
`RematchRequest`, `Campaign*` (`CampaignService`, `CampaignConfig`, `CampaignMission`,
`LoadCampaignData`), score messages in `BaseStation/` (`RaceScoreMessage`, `BattleScoreMessage`,
`TimeTrialScoreMessage`), `TournamentLobbyListener`.
→ New modes = C# work in MatchService/GameMode + (likely) a JSON config.

### Login / account / offline  (Priority 4)
UI: `AccountLogin_ViewController`, `AccountCreation*`, `sb_AccountFlow_ViewController`,
`AccountService`. Native callbacks: `AccountLoginCallback`, `CreateAccount`, `AccountLogoutCallback`.
**Offline/guest exists:** `OpenPlayGuestHostSelect_ViewController`, local "Open Play",
`migrateGuestAccount`, `ProfileData` GUEST handling, `PlayerService.SendStopScaningForGuests()`.
→ Likely a playable local/guest path that avoids backend; needs a focused read of
`AccountService.cs` + the FTUE (`sb_FTUE_VC.cs`) to confirm login can be skipped cleanly.

### Data-driven content  (cheapest "add items" wins — JSON, no code)
`LoadCampaignData` / `CampaignService`, `GameModeDataSource`, **`MutatorsJSONData`** (rule
modifiers!), `ParadeJSONData`, `GameTipsJSONData`, `VehicleSkinData`, `ItemShopService`,
`CarouselData`. Campaigns, modes, mutators, skins, shop items, tips load from JSON/assets.

## Dead backend hosts (in C# — mostly cosmetic/marketing)
`go.anki.com/*` (store/marketing redirects), `support.anki.com`, `anki.com/terms`,
`rink.hockeyapp.net` (HockeyApp crash reporting — service dead since 2019),
`play.google.com`. Broken links are harmless; HockeyApp calls should be stubbed if they hang.
The gameplay/auth backend is **not here** (it's native).

## Native bridge mechanism
- **native → C#:** `NativeLibMessageReceiver : MonoBehaviour` (DontDestroyOnLoad), receives
  string messages via `UnitySendMessage`; also has `OnApplicationPause(bool)` — relevant to
  the focus-loss bug.
- **C# → native:** `[DllImport]` P/Invoke + CLAD-serialized `ExternalInterface` messages.

## Track 2 entry points, ranked (leverage vs risk)
1. **UI / QoL** — pure C# view-controllers + resources. Lowest risk. Good first patch.
2. **Content via JSON** — mutators/skins/campaign/shop. High value, low risk, no recompile.
3. **Cars / tuning / items** — C# MetaGame + inventory CLAD messages; mostly C#, some native
   state sync to verify.
4. **New game modes** — C# MatchService/GameMode + JSON; moderate effort.
5. **Anything touching auth/BLE protocol internals** — native; reserve Ghidra for this only.

## Offline / login analysis — RESOLVED: the game runs fully offline

The dead Anki backend does **not** block gameplay. Evidence from the C#:

- **Default account is a guest.** `AccountData` ctor sets `Status="guest"`,
  `ConnectionState=InvalidSession` (`AccountData.cs:46`). `IsLoggedIn()` requires `active` +
  `ValidSession` (`:53`) → false for guests.
- **`IsLoggedIn()` is an *optional* gate** in ~20 places (garage, loot, profile, results, sync).
  Logged-out simply skips cloud features — the game is designed to degrade gracefully.
- **First-class offline path:** `OnSkipAccountButton()` → `States.SignUpOffline`
  (`sb_AccountFlow_ViewController.cs:609`); the account-option screen always wires the Skip
  button (`:397`). Offline signup creates a local driver profile.
- **No startup network call.** `AppLoadingView.LoadApplication()` is fully local: native plugin
  check → `DriveEngine.Initialize()` (`InitializePlugin()` is `return true`) → load engine
  *data* (local assets/OBB) → `LoadServices()` (just subscribes message dispatchers) → load
  local settings → home. Login is never auto-triggered.
- **No forced-update gate.** `IsAppOutOfDate` is set only by a post-login server callback and
  shown cosmetically on the Profile screen; it blocks nothing.
- Online-only surfaces (account login/create, cloud sync, Store purchases, leaderboards,
  DynamicDownload, DAS/HockeyApp telemetry) either no-op, show a graceful "no internet" popup,
  or fail in fire-and-forget coroutines.

**Implication:** Priority 4 (dead login) and Priority 5 (cloud sync) are largely **non-issues**
for playability — route the user through "Skip Account" to local play. The only Track-1 work
here is defensive: suppress dead-telemetry calls (DAS, HockeyApp) *if* they cause ANRs/hangs on
a dead host (verify on device), and optionally hide dead online buttons (that's Track-2 QoL).

## Open questions (not blocking)
- Do DAS/HockeyApp telemetry calls do synchronous DNS to a dead host on a path that could ANR?
  (verify with logcat on device; stub `HttpAdapter`/`DAS` if so).
- Is car/upgrade *definition* data in JSON/assets, or authoritative in native? (decides how
  cheap "add a car/upgrade" really is — inspect OBB `assets/` + `MetaGame` data loaders).
