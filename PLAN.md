# Anki Overdrive Modernization — Plan

## Context

The Anki Overdrive Android app (v3.4.0) is end-of-life. Anki shut down in 2019; Digital
Dream Labs (DDL) acquired the IP, kept distributing the APK for free, and has since wound
down too — **their official download host is now dead** (see Source of Truth). The last
build targets an old SDK and breaks on modern Android (12+): the split Bluetooth permission
model is ignored, the app crashes on focus loss, and the dead backend stalls login/sync.

Goal: get v3.4.0 running well on a **Samsung Galaxy Tab S10+ (Android 14+)** with the
original feel, assets, and gameplay intact, supporting the full car collection (Supercars,
Supertrucks, Fast & Furious, Drive Gen 1). **Then**, on that working base, investigate a
refactor / adding items — with the *approach* for that second phase chosen only after a
recon pass tells us what's actually feasible.

Two tracks:
- **Track 1 — Modernization** (well-scoped): smali/manifest patching to make it run. Keep
  the native stack as-is.
- **Track 2 — Refactor / add items** (recon-gated): decided after the audit. User's target
  additions: **car support/tuning, new game modes/features, UI & quality-of-life.**

---

## Status (as of this session)

- [x] Toolchain: `apktool` 3.0.1, `jadx`, `adb` present. **Android SDK build-tools 34.0.0
      installed** at `~/Library/Android/sdk/build-tools/34.0.0` (`apksigner`, `zipalign`,
      `aapt2` verified). `keytool` + JDK 22 present.
- [x] APKs acquired (see Source of Truth) — all three XAPK bundles downloaded & verified.
- [x] Extract main APK from XAPK (Phase 0) → `overdrive-bundle/com.anki.overdrive.apk`
- [x] **Recon / audit** — architecture mapped (see Recon Findings). On-device crash repro
      still TODO (needs the Tab S10+).
- [x] Decompile `Assembly-CSharp.dll` (`ilspycmd` 8.0.0.7345) → 1,750 `.cs` files in
      `csharp-decompiled/`; full subsystem map in **[RECON.md](RECON.md)**
- [x] Offline/login analysis — **game runs fully offline as a guest; boot path has no network
      call** (see RECON.md "Offline / login analysis"). Priority 4/5 downgraded.
- [x] Track 2 investigation: local-server/DB profiles — **decided Option A** (revive original
      backend); API surface confirmed feasible. Spec in **[BACKEND-REVIVAL.md](BACKEND-REVIVAL.md)**.
- [x] Backend revival — device-independent parts: **server scaffold built + verified**
      (`server/`, Node+Fastify+SQLite, capture-first; create/login/ankival-KV all tested) and
      **capture+redirect smali patch prepared** (`patches/httpadapter-capture-redirect.md`).
- [ ] Backend revival — needs device: A1 install redirected APK → A2 live capture → A3 refine
      routes from captured shapes → A4 React/DaisyUI dashboard.
- [ ] Track 1 patches (BLE permissions + focus-loss crash) — needs device.
- [ ] Rebuild, sign, install, test on device
- [ ] Track 2 execution (C# modding)

> Note on JDK: system Java is 22. apktool 3.x is fine on it. If any smali/dex or `apksigner`
> step misbehaves, fall back to JDK 17 (`brew install openjdk@17`) — known-good for this tooling.

---

## Recon Findings (Phase 1 — main `com.anki.overdrive`)

**Engine: Unity + Mono (NOT IL2CPP).** `lib/armeabi-v7a/` (single ABI): `libunity.so` 17M,
`libmono.so` 3.6M (→ managed C# backend), **`libDriveEngine.so` 15M** (Anki's native
BLE/vehicle core, 314 BT-related strings), `libDAS.so` (analytics), `libmain.so` (Unity
bootstrap), `libc++_shared.so`, FMOD audio. No `global-metadata.dat` → confirmed Mono.

**Game logic = editable C#.** `assets/bin/Data/Managed/` (in the APK, not the OBB) holds the
managed assemblies: **`Assembly-CSharp.dll`** (3.2 MB, confirmed Mono/.NET PE — the main game
logic), **`Mecanki.dll`** (Anki framework), `AnkiStoryboard.dll`, `EventScheduler.dll`,
`UIComponents.dll`, `AnkiAutomation.dll`, `UserFeedback.dll`, `Assembly-UnityScript.dll`,
etc. These decompile to readable C# and can be edited/recompiled — the Track 2 surface.

**Track 1 surfaces are in smali (editable):**
- BLE Android plumbing: `com.anki.bluetooth.le.LeService` (already an Android **Service** —
  helps the lifecycle fix), `LePeripheral`, `com.anki.AnkiActivity`, `com.anki.utils.DeviceUtils`.
- Network/telemetry: `com.anki.util.http.HttpAdapter`, `com.anki.daslib.DAS`, Play Games.

**SDK: minSdk 19 / targetSdk 19** (Android 4.4). Older than the doc assumed → legacy
permission compat applies, so the *actual* BLE failure on Android 14 must be confirmed by
running the **unmodified** app first (still TODO — needs device).

**Content is partly data-driven (good for Track 2):** C# types include `LoadCampaignJson`
(campaign/missions from JSON), `CarPoolView/Filter`, `DriveCars`, **`Carlevelup`** (car
leveling/tuning), `ChooseGameMode`, `Campaign`, `TimeTrial`, `Battle`, `Anki.MatchService`.

**Offline/guest path exists:** `ACCOUNT_STATUS_GUEST_KEY`, `OnLoginButtonPressed`,
`HandleAccountLoginCallback`, `Session`, `GameSyncState` → Priority-4 "force offline/guest"
looks achievable in C#.

**Ghidra verdict: hold in reserve.** Confirmed installed
(`/usr/local/Cellar/ghidra/12.1.2/libexec/support/analyzeHeadless`). Not needed for Track 1
(smali) or Track 2 (C#). Reserve it for reversing `libDriveEngine.so` *only if* the
community-documented BLE protocol proves insufficient for a specific feature.

**Decompiler tooling:** `dotnet` present (`/usr/local/share/dotnet`); `mono`/ILSpy absent.
Next: `dotnet tool install -g ilspycmd` to decompile the Anki assemblies to C#.

---

## Source of Truth — where the APK comes from

The plan doc's "primary source" (DDL support site) is **dead**: the JS download buttons point
at `https://assets.digitaldreamlabs.com/anki-apks/*.zip`, but that host is a CNAME to a
**deleted CloudFront distribution** (`d3m9ko681fe8tx.cloudfront.net` → NXDOMAIN on public
resolvers 8.8.8.8 / 1.1.1.1). APKPure is Cloudflare-walled and rejected automated access.

**Working source: archive.org** (no bot-wall, direct download). XAPK = a zip bundling the
base APK + OBB expansion files + `manifest.json`. Files downloaded into repo root:

| Local file | archive.org identifier | Bundle SHA-256 | Contents |
|---|---|---|---|
| `overdrive.xapk` | `anki-overdrive-v-3.4.0` | `656e548cc9b10790bc34b67b7e643d8df388d633566bf81a7f126581d824abe2` | `com.anki.overdrive.apk` (22.3 MB) + `main.1502` + `patch.1502` OBB |
| `drive.xapk` | `anki-drive-v-2.4.5` | `a352baa96cdce5c0ccf2714b8f8ca030ea25d16612f631a3180f6132cd7825a3` | `com.anki.drive.apk` (16.1 MB) + `main.56` + `patch.56` OBB |
| `foxtrot.xapk` | `anki-overdrive-fast-furious-edition` | `5919bba157f63f55e839f8c8534d6b5c9fe0aef6b9a05eed7f53de9396d68eac` | `com.anki.foxtrot.apk` (22.3 MB) + `main.403` + `patch.403` OBB |

Binaries are git-ignored. **Observation:** code is in the ~22 MB APK; the ~193 MB of OBB is
engine/assets — this shapes Track 2 (asset/content work likely means OBB work, not smali).

---

## Phase 0 — Extract

For each XAPK: `unzip <pkg>.xapk -d <pkg>-bundle/`. This yields the APK, the
`Android/obb/<pkg>/*.obb` files, and `manifest.json`. We work from the extracted `.apk`;
the OBB files are pushed to the device unchanged (unless Track 2 touches assets).

---

## Phase 1 — Recon / Audit  ← **Track 2 decision gate**

Decompile and document before changing anything. This pass produces the facts that decide
Track 2's strategy, so do it thoroughly.

```
apktool d <pkg>-bundle/com.anki.overdrive.apk -o overdrive-decompiled     # smali + resources
jadx -d overdrive-jadx <pkg>-bundle/com.anki.overdrive.apk                 # readable Java (analysis only)
```

Audit checklist — answer each, write findings into this file under "Recon Findings":

1. **SDK targets** — `overdrive-decompiled/apktool.yml` (`minSdkVersion`, `targetSdkVersion`).
2. **Native vs Java split** — `ls overdrive-decompiled/lib/`. Is there a game engine `.so`
   (Unity → `libunity.so`/`libil2cpp.so`; or custom)? **How much logic is native vs smali?**
   This is the single biggest determinant of Track 2 feasibility.
3. **Where is BLE?** — Java/smali (`BluetoothGatt`, `BluetoothAdapter`, `startScan`,
   `connectGatt`) vs native. Grep:
   `grep -rl "BluetoothGatt\|BluetoothAdapter\|startScan\|connectGatt" overdrive-decompiled/smali*/`
4. **Is content data-driven?** — inspect APK `assets/` and OBB for car/track definitions
   (JSON/config) vs hardcoded. Decides whether "add cars/tracks" is cheap.
5. **Login / network architecture** — find the login flow and what backend hosts it calls
   (dead). Locate the offline/guest path if any.
6. **Manifest components** — list Activities/Services/Receivers lacking `android:exported`
   (must be set when targetSdk ≥ 31).
7. **Crash root cause** — install the *unmodified* app first, reproduce the focus-loss crash,
   capture the stack: `adb logcat -v threadtime | grep -iE "anki|overdrive|Bluetooth|FATAL|AndroidRuntime"`.

**Reference implementations** (protocol + clean-room BLE) to lean on, especially for Track 2:
OpenOverdrive, Anki-Partydrive, anki/drive-sdk (see plan doc links).

---

## Phase 2 — Track 1 modernization patches

Apply in this order (smali patching keeps the native stack, per your "keep native languages").

1. **`AndroidManifest.xml`**
   - Add `BLUETOOTH_SCAN` (`usesPermissionFlags="neverForLocation"`), `BLUETOOTH_CONNECT`,
     `BLUETOOTH_ADVERTISE`.
   - Constrain legacy perms to old APIs: `BLUETOOTH`, `BLUETOOTH_ADMIN` and the location
     perms get `android:maxSdkVersion="30"` (with `neverForLocation`, scanning on 31+ no
     longer needs location).
   - Add `android:exported` to every component flagged in recon (launcher = `true`, others
     `false` unless they legitimately need an intent-filter entry point).
2. **`apktool.yml`** — bump `targetSdkVersion` to **33** first (not 34) and test before going
   further. Leave `minSdkVersion` as shipped. *Decision/experiment:* 33 forces the new
   permission model + exported enforcement without Android 14's extra restrictions.
3. **Runtime permission request (smali)** — on API 31+, `BLUETOOTH_SCAN`/`CONNECT` are
   runtime grants. Inject a `requestPermissions` call gating BLE init (the hardest patch;
   exact injection point comes from recon step 3).
4. **Focus-loss crash fix (smali)** — based on recon step 7's stack. Likely null/state guards
   around `disconnect()`/`close()` in `onPause`/`onStop` and in BLE callbacks firing on a
   dead Activity. Escalate to a foreground Service only if guards aren't enough.
5. **Login / sync — mostly already handled by design.** Recon showed the game boots offline as
   a guest with no network call and login is never auto-triggered (RECON.md). So Priority 4/5
   need almost nothing for playability: just route the user via "Skip Account", and *only if*
   on-device logcat shows the dead telemetry (`DAS`, HockeyApp) ANR/hang, stub those calls in
   `com.anki.util.http.HttpAdapter` / `com.anki.daslib.DAS`. (Reviving profile persistence on a
   self-hosted server is a separate Track 2 feature — see Phase 4.)

---

## Phase 3 — Rebuild, sign, install, test

```
apktool b overdrive-decompiled -o overdrive-patched.apk
~/Library/Android/sdk/build-tools/34.0.0/zipalign -v -p 4 overdrive-patched.apk overdrive-aligned.apk
# one-time debug keystore:
keytool -genkey -v -keystore debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 \
  -validity 10000 -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US"
~/Library/Android/sdk/build-tools/34.0.0/apksigner sign --ks debug.keystore \
  --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android \
  --out overdrive-signed.apk overdrive-aligned.apk

adb install -r overdrive-signed.apk
adb push <pkg>-bundle/Android/obb/com.anki.overdrive/ /sdcard/Android/obb/com.anki.overdrive/
```

(Repeat for `com.anki.foxtrot` and `com.anki.drive` once the main flow works.)

---

## Phase 4 — Track 2: C# / Unity assembly modding

Recon settles the strategy: the game is Unity **Mono** with managed assemblies shipped in the
APK, so Track 2 is **C# modding** — not smali, and a companion app is no longer required.

- **UI & quality-of-life** → edit view-controllers in `UIComponents.dll` /
  `Assembly-CSharp.dll`; resource/layout tweaks for the Tab S10+. Lowest risk → first target.
- **Cars / tuning** → `CarPool*`, `DriveCars`, `Carlevelup` in C#; some content is JSON
  (`LoadCampaignJson`). Adjust values/definitions and add tuning logic in C#.
- **New game modes / features** → extend the `GameMode` / `Anki.MatchService` / `Campaign`
  subsystem. Implementation style (decide when starting the first mode):
  (a) patch `Assembly-CSharp.dll` directly (dnSpy/ILSpy edit + save), or
  (b) a Unity-Mono mod-loader approach to add code without modifying originals.
- **Login/offline** → already free (boots as guest); no work needed for basic play.

### Track 2 feature: local-server / DB profile storage (under investigation)

Goal: persist player profiles to a self-hosted local server/DB instead of Anki's dead cloud.
Architecture facts (from `ProfileData.cs`, `ProfileService.cs`, `AccountService.cs`): native
`libDriveEngine.so` owns the canonical profile + local persistence + the etag-based sync engine
(server URL compiled into native); C# is a message facade but can **read the entire profile**
(typed fields + a CLAD blob via `requestProfileDataClad`) and trigger saves.

**Decision: Option A** — revive the game's original account/sync against a self-hosted server,
**full scope** (backup + restore + multiple profiles + multi-device + web dashboard). Confirmed
feasible by static analysis of `libDriveEngine.so`: REST/JSON API at `accounts.api.anki.com/1/`
(+ `ankival.api.anki.com/1/` KV profile store), `Anki-App-Key` + session-token auth, and **no
cert pinning**. Execution strategy is **redirect → capture → emulate**. Full surface, phased
roadmap (A1–A4), and device-dependency split are in **[BACKEND-REVIVAL.md](BACKEND-REVIVAL.md)**.

(Options B — a C# sync mod — and C — file-level save backup — remain cheaper fallbacks if A hits
a wall such as an opaque KV blob format or non-reissuable token signing.)

Rebuild note: a modded managed DLL is swapped into `assets/bin/Data/Managed/` before
`apktool b`, then re-signed per Phase 3. (Mono has no AOT signature check on these DLLs, so
swapping is clean.) Ghidra/`libDriveEngine.so` only enters the picture for raw-protocol work.

---

## Verification

- **Per patch:** rebuild → install → `adb logcat` for the targeted failure (permission grant,
  no focus-loss crash, login reaches offline play).
- **End-to-end on the Tab S10+:** launch, grant BLE permissions, scan & connect to a real car
  (Supercar first), drive, then background the app / lock the screen / pull the shade and
  confirm **no crash** and that the connection recovers. Repeat for a Supertruck, an F&F car
  (note F&F cars renamed Mammoth/Dynamo/Nuke Phantom post-license), and a Drive Gen 1 car.
- **Regression baseline:** keep the unmodified-app logcat from recon step 7 to confirm the
  crash is actually gone, not just moved.

## Open questions / risks

- Re-signing changes the APK signature — fine for sideload; only a problem if the app does
  in-app signature/license checks (watch for this in recon).
- targetSdk 33 vs 34: start at 33; 34 adds foreground-service-type and other constraints that
  may bite if we move BLE into a Service.
- If core gameplay/BLE turns out to be native, Track 2 "game modes" realistically becomes a
  companion app rather than in-app edits — set expectations accordingly.
