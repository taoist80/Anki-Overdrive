# Backend Revival — self-hosted Anki account + profile server (Track 2, Option A)

Goal: replace Anki's dead cloud with a self-hosted server so the game's **original** account +
profile-sync system works — giving backup/restore, multiple profiles, multi-device sync, and a
DB/dashboard, all from the game's built-in features. Chosen by the user over a C#-mod approach.

## Confirmed API surface (from `libDriveEngine.so` static analysis)

The native class `Anki::DriveEngine::AccountService::ProfileManager` makes HTTPS/JSON calls to
Anki hosts (literals in the binary):

| Host | Purpose | Needed for profiles? |
|---|---|---|
| `https://accounts.api.anki.com/1/` | account create/login/refresh/account-data | **Yes** |
| `https://ankival.api.anki.com/1/` | key-value store — **profile game data sync** (likely) | **Yes** |
| `https://storegate.api.anki.com/1/` | store / billing / purchases | later |
| `https://virtualrewards.api.anki.com/1/` | loot / virtual rewards | later |
| `https://app.bronto.com/...` | email marketing (`SubscribeToBrontoList`) | no |

**Operations** (ProfileManager methods → REST endpoints): `CreateOnlineAccount`,
`LoginCloudProfileWithUsername`, `VerifyProfileUserNamePassword`, `RefreshSessionToken`,
`GetCurrentAccountData`, `UpdateAccountData`, `SendForgotPassword`, `ResendVerificationEmail`,
`CheckUsernameAvailability`, `GetUsernameSuggestions`, `DeleteDeviceSessionForProfile`,
`GetCurrentKeyValueDataForAccount` + `PostNextKeyValRequest` (profile KV sync), `SaveGameStats`.

**Auth** (`OnlineAccountAPIClient`): `Anki-App-Key` header (static app key) + session token in
`Authorization` header, refreshed via `RefreshSessionToken` (`kAccountSessionTokenKey`).
Responses parsed by `ParseJsonResponse` (jsoncpp → `Json::Value`). REST/JSON.

**TLS:** stock OpenSSL verification (`SSL_CTX_set_verify`, `load_verify_locations`) — **no cert
pinning strings found.** (The `SSL_CTX_*`/WebDAV/CGI strings are an embedded Mongoose/Civetweb
HTTP *server* used for local-multiplayer hosting, separate from this cloud client.)

For our own server the real `Anki-App-Key` value is irrelevant — our server validates, so it can
accept whatever the client sends.

## Ghidra findings (libDriveEngine.so — analyzed, 114 fns extracted → `re/libDriveEngine_api_extract.md`)

- **Endpoint config is centralized.** Native holds a `ServerConfig` mapping endpoint *names* →
  base URLs; e.g. `GetKeyValueStoreEndPointURL` does `ServerConfig::GetEndpoint(cfg,"ankival",…)`.
  Names seen: `accounts`, `ankival`, `store`, `itemshop`, `storegate`, `virtualrewards`. Account
  endpoints add paths `/accounts`, `/accounts/`, `/verify_email`, `/sendemail`, `/email`.
- **The native engine does NOT do HTTP itself — it delegates to an injected `IHttpAdapter`**
  (`RushHour::Init(… IHttpAdapter*, IPaymentAdapter*)`), which is the Java
  `com.anki.util.http.HttpAdapter` over JNI. **This is the key simplification** (see A1/A2).
- **Profile payload is JSON** (boost property-tree: `ReadFromPtree`/`WriteToPtree`,
  `ReportInvalidDataInOnlineProfileJson`), **version-stamped + hashed** for conflict detection
  (`StoreLastOnlineProfile` → `profile_sync.last_online_profile_hash`; "online profile has a
  newer version number than the running app"). Synced as a value in the `ankival` **key-value
  store** (`GetCurrentKeyValueDataForAccount`, `PostNextKeyValRequest`, key `app.user_profile_mapping`,
  `$profile_id`).
- **Local cache exists**: native writes the profile JSON to disk (`SaveProfileToDisk`,
  `LoadProfileFromDisk`, `ReadInProfileTOCJsonFromDisk` — a "profile TOC"), and caches the last
  online profile (`ReadLastOnlineProfileCached`). Useful for the file-level fallback (Option C).
- Account ops recovered as functions: `CreateOnlineAccount`, `LoginCloudProfileWithUsername`,
  `VerifyProfileUserNamePassword`, `RefreshSessionToken`, `GetCurrentAccountData`,
  `UpdateAccountData`, `SendForgotPassword`, `CheckUsernameAvailability`, `GetUsernameSuggestions`,
  `ResendVerificationEmail`, `DeleteSession`, `CheckForProfileConflict`.

### The intercept point (one Java method)
`HttpAdapter.startRequest(long id, String url, String[] headers, String[] params, byte[] body,
int method, String, int)` is the single chokepoint for every cloud request. Responses return
via `HttpAdapter.NativeHttpRequestCallback(long id, int statusCode, String[] headers, byte[] body)`.
Hooking `startRequest` gives us full plaintext request capture **and** host redirect at the Java
layer — no native patching, no TLS pinning concerns.

## Strategy: redirect → capture → emulate (don't pure-static-RE the whole protocol)

The efficient path is to make the app talk to our server, observe the real requests, and
implement responses iteratively — using Ghidra only where live traffic is ambiguous.

### Phase A1 — Redirect at the Java layer (needs device)
Patch `HttpAdapter.startRequest` (smali) to rewrite the `url` String param: any
`https://*.api.anki.com/1/...` → `http(s)://<our-server>/...`. One edit covers all endpoints;
no native `.so` patch. (Fallbacks if ever needed: device DNS override via Pi-hole/router, or
patch the `ServerConfig`/host literals in `libDriveEngine.so`.)

TLS: trivial here — `HttpAdapter` does the TLS to wherever we point it. On a trusted LAN, redirect
to `http://`; or give our server a cert the device trusts (old `targetSdk` trusts user-installed
CAs, and we control `targetSdk`). No cert pinning exists to defeat.

### Phase A2 — Capture (needs device)
Add logging at the top of `HttpAdapter.startRequest` to dump `url`, `method`, `headers`, and the
`body` bytes to logcat — **plaintext, before TLS, complete**. Drive the create-account / login /
play / sync flows and record exact requests + (via `NativeHttpRequestCallback`) the responses the
app expects. This yields the real schemas faster and more reliably than reading decompiled jsoncpp.

### Phase A3 — Implement (device-independent build, device to validate)
Build the server to satisfy captured requests:
- Account endpoints (create/login/refresh/account-data/username-check) returning the JSON the
  `AccountUpdatedCallback` CLAD fields expect (userId, profileId, userName, email,
  isEmailVerified, connectionState, status, dateOfBirth, isSynced, isAppOutOfDate).
- Key-value profile store (`ankival`): per-account key→blob get/put with etag for the
  conflict-resolution path (`OnlineProfileConflict`, `overrideLocal`/`overrideOnline`).
- Persist to Postgres/SQLite. Issue + validate session tokens.

### Phase A4 — Dashboard + multi-device
Multi-device sync + multiple profiles + backup are inherent once the server is the source of
truth (that's what Anki's server did). Add a web dashboard over the DB to view/edit profiles
(coins, car levels, campaign stars) and back them up.

## What's device-independent vs needs the Tab S10+
- **No device:** Ghidra schema extraction — **done** (114 fns → `re/`); write the
  `HttpAdapter.startRequest` redirect+logging smali patch; scaffold the account + KV server + DB.
- **Needs device (adb):** A1 redirect (install patched APK), A2 live capture, A3/A4 validation.

## Open items → how to resolve
- Exact endpoint paths + JSON schemas per operation → **Ghidra** (decompile ProfileManager HTTP
  builders + `ParseJsonResponse`) cross-checked with **live capture** and the decrypted **iOS**
  binary (archive.org `overdrive-2.6-decrypted`) as a second reference.
- KV profile blob format (binary CLAD vs JSON) → Ghidra on `GetCurrentKeyValueDataForAccount` +
  capture.
- Session-token issuance/signing → capture; reissue our own (server controls validation).

## Risks
- KV blob may be opaque/binary (CLAD-serialized) — readable but needs format work.
- Token may be signed/validated in ways that constrain reissue (mitigated: our server signs+verifies).
- If old-targetSdk user-CA trust is undesirable, may need a host-string + `http://` patch instead.

## Reward economy & profile persistence (from a live DDL 2.6 run, 2026-06-25)

A full Open Play run (weapon use → XP → loot box opened) confirmed how the economy works, which
shapes what the backend must (and must not) do:

- **Loot / XP / weapons / upgrades are CLIENT-SIDE.** The native engine rolls them from config
  shipped in the OBB and writes the result to the **local profile** (heavy `labs.retrodrive` disk
  writes observed during the run; **no** backend call — only dead `test-rams` showed up). So in
  offline/guest mode the whole economy works with **no server**.
- **The profile lives in the app's PRIVATE internal storage** (`/data/data/<pkg>/files/`). The app
  is **not debuggable** and the device isn't rooted → we can't pull the blob directly. To capture
  the real profile/sync schema, **redirect `server_config.json`'s `ankival` host to our server and
  capture the sync upload** (this is the same redirect we already planned — see Strategy).
- **Backend role for the economy = persist + sync the profile blob** (coins, XP, per-vehicle
  progression, item/upgrade inventory, loot received) via the **ankival KV store**, plus serve
  `accounts` and optionally `virtualgoods`/`virtual_rewards`. **The backend does NOT generate loot.**
- **Economy config (the data model) is in the OBB**, extracted to `reference/ddl-gamedata/`
  (git-ignored): `lootDrops.json` (`open_play_loot`, `virtual_rewards`, car-specific prize boxes,
  separate `loot_list_gen1`), `items.json`, `upgradeData.json`, `mutators.json`,
  `virtualRewardsMapping.json`, `itemShopSchedule.json`, `medals.json`.
- **Car catalog** (`vehicleTypes.json`, 21 types): Supercars (Skull/Groundshock/Thermo/Nuke/
  Guardian/BigBang/Corax…), Supertrucks (x52/x52ice/Freewheel), **renamed F&F (Mammoth id18 /
  Dynamo id19 / Ghost id20)**, and an explicit **`Unrecognized` (id101)**. This is the lookup that
  decides "is this car recognized" — relevant to the earlier "only default cars" gap (a car shows
  as Unrecognized if its model id isn't mapped here).

Related: profile data model in [RECON.md](RECON.md); overall plan in [PLAN.md](PLAN.md).
