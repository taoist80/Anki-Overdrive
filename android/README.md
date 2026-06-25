# OverdriveX (Android, arm64)

A native Kotlin app that drives Anki Overdrive / Drive cars over BLE — built because the
original 32-bit game can't run on the 64-bit-only Galaxy Tab S10+ (see `../PLAN.md`).

**Milestone 1 (this scaffold): BLE proof-of-concept** — scan → connect → SDK mode → drive one
car (speed + lane change) + live telemetry. Protocol in `Protocol.kt` (UUIDs verified from the
original app binary; message layouts from Anki's drive-sdk).

## Build & install

Uses Gradle **8.9** (wrapper), AGP 8.6.1, Kotlin 2.0.20, compileSdk 35, **JDK 17**.

```bash
cd android
# one-time: generate the wrapper at the pinned version (brew's gradle bootstraps it)
gradle wrapper --gradle-version 8.9

export JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME="$HOME/Library/Android/sdk"

./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.overdrive/.MainActivity
```

Then on the tablet: grant Bluetooth permissions → **Scan** → tap a car → **Connect** → use the
speed slider + Left/Right. Power on a car first (place it on the track / charged).

## Layout
- `Protocol.kt` — BLE GATT UUIDs + message encode/decode (SDK mode, speed, lane, telemetry).
- `AnkiCar.kt` — BLE manager: scan/connect/notify/SDK-mode/drive; state as Compose state.
- `MainActivity.kt` — Compose UI (permissions, scan list, drive controls, telemetry log).

## Roadmap
Telemetry/track mapping → multiple cars → game modes → profiles synced to the `../server`
local backend (the revived account/profile API).
