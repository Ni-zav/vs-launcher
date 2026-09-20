# Infinix Android 15+ local device test runbook

This runbook is the required handoff for a local Codex/agent or developer testing VS Launcher on Nigel's physical Infinix.

It covers:

- reproducible local build
- safe ADB update/install
- Android 15+ startup regression testing
- install-error triage without destroying launcher state
- Macrobenchmark execution
- Baseline Profile generation on the physical phone
- controlled Baseline Profile A/B measurement
- real sideload/dexopt inspection
- how to document the result

Do not declare a device test PASS from compilation alone.

---

## Current X6855 path

The AndroidX controller-freezer investigation is complete for the current
Infinix X6855 firmware. Exact-PID sticky unfreeze and both documented AOSP
freezer controls failed to keep the instrumentation controller runnable.

Do **not** repeat the Macrobenchmark/freezer workaround loop on this firmware.

Use the instrumentation-free manual path instead:

- [INFINIX_MANUAL_PROFILE.md](INFINIX_MANUAL_PROFILE.md)
- [CODEX_INFINIX_MANUAL_PROFILE_GOAL.md](CODEX_INFINIX_MANUAL_PROFILE_GOAL.md)

Primary commands:

```sh
CAPTURE_ITERATIONS=5 bash scripts/infinix-manual-profile.sh capture

ITERATIONS=20 STATE_SETTLE_SECONDS=2 \
  bash scripts/infinix-manual-ab.sh all \
  device-test-results/manual-profile-.../baseline-prof.txt
```

The older controller/freezer tooling and research remain in the repository as
historical evidence and for any future firmware/reference-device retest:

- [INFINIX_FREEZER_RESEARCH.md](INFINIX_FREEZER_RESEARCH.md)
- [CODEX_INFINIX_RETRY_GOAL.md](CODEX_INFINIX_RETRY_GOAL.md)

---

## 1. Safety rules

1. **Never automatically uninstall `com.vslauncher`.**
   Uninstalling clears SharedPreferences, aliases, hidden apps, Home slots, and other local launcher configuration.
2. Prefer `adb install -r` for an in-place update.
3. If signing mismatch prevents an update, preserve the failure log and ask before uninstalling.
4. Export the launcher JSON configuration before a deliberate uninstall.
5. Do not change refresh-rate, battery, thermal, or animation settings between A/B performance runs.
6. Do not use emulator numbers as Infinix performance evidence.
7. Do not commit a generated Baseline Profile merely because generation succeeded. Measure it first.

Connected Gradle tests also install the target APK. `gradle.properties` keeps
APKs installed after the run and disables uninstalling incompatible APKs so
test cleanup cannot erase the existing launcher configuration. Keep these
settings enabled on a personal device. Restore the normal debug APK with
`adb install -r` after testing. Capture and measurement test classes live in
their respective variant source sets; do not move them into the shared `main`
source set.

---

## 2. Required local tools

The development computer needs:

- JDK 17
- Android SDK 35
- Android Build Tools 35.0.0
- Gradle 8.7
- ADB
- Git

The Infinix needs:

- Android 15+ target firmware
- Developer Options enabled
- USB debugging enabled
- RSA authorization accepted for this computer

Verify:

```sh
java -version
gradle --version
adb version
adb devices -l
```

If multiple Android devices are connected, set:

```sh
export ADB_SERIAL=<serial>
```

Before a performance run, record the existing HOME role holder. If VS Launcher
is the default HOME, temporarily select the stock launcher: force-stopping the
default HOME can make Android restart it before the intended cold-start sample.
Restore the original HOME selection after measurements.

On vendor firmware, a test can stall even while instrumentation remains active.

For the Android 16 / Infinix X6855 controller-freezer investigation, read
[`INFINIX_FREEZER_RESEARCH.md`](INFINIX_FREEZER_RESEARCH.md) first. It records
the AOSP semantics, ruled-out false leads, XOS evidence, measurement caveats,
and the reversible workaround order.

The repository also provides:

```sh
bash scripts/infinix-freezer-session.sh status
bash scripts/infinix-freezer-session.sh verify
bash scripts/infinix-freezer-session.sh sticky
```

Do not use its device-wide `disable --yes` mode until the same live controller
PID has been tested with sticky unfreeze and the research doc's safeguards are
understood.

Inspect the harness PID and its cgroup state before declaring a timeout:

```sh
adb shell pidof com.vslauncher.macrobenchmark
adb shell cat /proc/<harness-pid>/cgroup
```

Use the returned cgroup path to inspect `cgroup.events` and its parent
`cgroup.freeze`. A frozen UID can also freeze the test's timeout thread.
Retain this evidence, stop the stalled harness, and resolve the vendor's
background restriction before retrying. Do not treat an interrupted or frozen
run as a performance sample, or change phone-wide power settings silently.

---

## 3. Preferred Codex/local smoke test

Run from the repository root:

```sh
bash scripts/infinix-adb-smoke.sh
```

Optional:

```sh
ITERATIONS=20 ADB_SERIAL=<serial> bash scripts/infinix-adb-smoke.sh
```

The script deliberately does **not** uninstall on failure.

It:

1. builds the debug APK through `scripts/build-debug-apk.sh`
2. records Git SHA/branch
3. records Infinix manufacturer/model/Android/API/security patch/fingerprint
4. records display size/density/available refresh-rate information
5. records current HOME role holder when Android exposes it
6. runs `adb install -r`
7. classifies common installation failures
8. clears logcat
9. force-stops and cold-starts VS Launcher repeatedly
10. captures `am start -W` timing output
11. captures package state
12. captures VS Launcher/FATAL logcat lines
13. writes a result bundle under:

```text
device-test-results/YYYYMMDD-HHMMSS/
```

That directory is gitignored.

Read at minimum:

```text
SUMMARY.md
device.txt
build.txt
install.txt
startup.txt
logcat-vslauncher.txt
```

A successful script exit is necessary but not enough for PASS; inspect the logs.

---

## 4. Manual ADB equivalent

Build:

```sh
bash scripts/build-debug-apk.sh
```

Expected APK:

```text
dist/VS-Launcher-0.8.0-debug.apk
```

Verify device:

```sh
adb devices -l
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.fingerprint
```

Install/update without deleting data:

```sh
adb install -r dist/VS-Launcher-0.8.0-debug.apk
```

Verify installed version:

```sh
adb shell dumpsys package com.vslauncher \
  | grep -E 'versionName=|versionCode=|firstInstallTime|lastUpdateTime'
```

Cold start:

```sh
adb shell am force-stop com.vslauncher
adb shell am start -W -n com.vslauncher/.MainActivity
```

Repeat Android 15+ startup path:

```sh
for i in $(seq 1 20); do
  echo "=== $i ==="
  adb shell am force-stop com.vslauncher
  adb shell am start -W -n com.vslauncher/.MainActivity \
    | grep -E 'Status:|LaunchState:|TotalTime:|WaitTime:'
done
```

Then inspect crashes:

```sh
adb logcat -d -v threadtime \
  | grep -E 'FATAL EXCEPTION|AndroidRuntime|com\.vslauncher'
```

The Android 15+ invariant being tested is:

```text
setContentView(root)
applyMinimalSystemUi()
requestApplyInsets()
```

A startup crash or repeated failure here is a release blocker.

---

## 5. Install error triage

### `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

Meaning: installed VS Launcher and the candidate APK were signed by different keys.

Required response:

1. save the exact `adb install` output
2. do **not** automatically uninstall
3. export launcher JSON config if the current installation is usable
4. inspect the candidate signer:

```sh
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify \
  --verbose --print-certs \
  dist/VS-Launcher-0.8.0-debug.apk
```

5. only uninstall after intentionally accepting the local-data loss:

```sh
adb uninstall com.vslauncher
```

Then reinstall.

### `INSTALL_FAILED_VERSION_DOWNGRADE`

The candidate `versionCode` is older than the installed app.

Preferred fix: build with a higher versionCode.

Do not silently use a forced downgrade as the normal test path.

### `INSTALL_FAILED_USER_RESTRICTED`

The phone/vendor policy rejected USB/APK installation.

Record the error first. Then unlock the phone and inspect the USB installation/security prompt or vendor developer settings.

Do not disable unrelated security features blindly.

### `INSTALL_FAILED_INSUFFICIENT_STORAGE`

Record free space and clean storage before retrying.

### `unauthorized`

Run:

```sh
adb devices -l
```

Unlock the phone and accept the RSA authorization prompt.

### Multiple devices

Use:

```sh
ADB_SERIAL=<serial> bash scripts/infinix-adb-smoke.sh
```

or:

```sh
adb -s <serial> ...
```

---

# 6. Macrobenchmark on the Infinix

Normal benchmark APK/test compilation is checked in CI.

Run actual timing only on the physical phone:

```sh
gradle --no-daemon :macrobenchmark:connectedBenchmarkAndroidTest
```

If Gradle task naming changes, inspect:

```sh
gradle :macrobenchmark:tasks --all | grep -i benchmark
```

Included scenarios:

- cold startup without pre-compilation
- cold startup requiring a packaged Baseline Profile
- Home → All Apps frames
- Home → Settings frames
- All Apps fling
- search/filter frames

Results/traces are under the module's connected-test output directories. Find them with:

```sh
find macrobenchmark/build/outputs -type f \
  \( -name '*.json' -o -name '*.perfetto-trace' \) -print
```

Keep the raw JSON and Perfetto traces for any performance claim.

---

# 7. Generate a candidate Baseline Profile on the Infinix

## Why there is a separate build variant

The production/release-like `benchmark` build remains R8 optimized.

Profile **generation** uses a separate `baselineProfile` app variant because captured profile signatures must come from a non-obfuscated/non-optimized build.

The repository therefore has:

```text
benchmark
  release-like
  R8/resource shrinking
  used for performance measurement

baselineProfile
  non-obfuscated
  non-optimized
  profileable
  used only to capture profile rules
```

Do not swap these responsibilities.

## Compile the generator variants

```sh
gradle --no-daemon \
  :app:assembleBaselineProfile \
  :macrobenchmark:assembleBaselineProfile
```

## Run profile generation on the connected Infinix

Expected connected-test task:

```sh
gradle --no-daemon :macrobenchmark:connectedBaselineProfileAndroidTest
```

If the local AGP exposes a different task spelling, discover it instead of guessing:

```sh
gradle :macrobenchmark:tasks --all | grep -i baseline
```

The generator contains two journeys:

### `startupProfile`

Only startup:

```text
Home
→ start VS Launcher
→ initial Home display
```

It is marked eligible for Startup Profile generation.

### `commonLauncherJourneys`

Covers:

```text
startup
→ All Apps
→ fling
→ Home
→ Settings
→ Home
→ swipe-left focused Apps search
→ enter query
```

It contributes broader Baseline Profile rules but is not marked as startup-only.

Run just one generator when debugging:

```sh
gradle --no-daemon :macrobenchmark:connectedBaselineProfileAndroidTest \
  -P android.testInstrumentationRunnerArguments.class=com.vslauncher.macrobenchmark.BaselineProfileGenerator#startupProfile
```

Find generated text profiles:

```sh
find macrobenchmark/build/outputs -type f \
  \( -name '*baseline-prof.txt' -o -name '*startup-prof.txt' \) -print
```

Copy the raw generated files into the device-test result bundle before editing them.

---

# 8. Candidate profile packaging for A/B testing

Do this on a temporary test branch/worktree first.

A manually supplied Baseline Profile can live at:

```text
app/src/main/baseline-prof.txt
```

A Startup Profile can live at:

```text
app/src/main/startup-prof.txt
```

For the Baseline Profile candidate, merge/deduplicate the startup and common-journey baseline rule outputs rather than duplicating rules blindly.

Example pattern after locating the real generated filenames:

```sh
cat <startup-baseline-prof.txt> <journeys-baseline-prof.txt> \
  | sort -u \
  > app/src/main/baseline-prof.txt

cp <startup-startup-prof.txt> app/src/main/startup-prof.txt
```

Do not commit these candidate files yet.

Build the release-like benchmark target again:

```sh
gradle --no-daemon :app:assembleBenchmark :macrobenchmark:assembleBenchmark
```

---

# 9. Baseline Profile A/B measurement

Run the same Infinix, same refresh rate, same thermal state.

## A — no ahead-of-time compilation

`LauncherMacrobenchmark#coldStartup` uses `CompilationMode.None`.

Run:

```sh
gradle --no-daemon :macrobenchmark:connectedBenchmarkAndroidTest \
  -P android.testInstrumentationRunnerArguments.class=com.vslauncher.macrobenchmark.LauncherMacrobenchmark#coldStartup
```

## B — packaged Baseline Profile required

`LauncherMacrobenchmark#coldStartupWithBaselineProfile` uses:

```text
CompilationMode.Partial(BaselineProfileMode.Require)
```

Run:

```sh
gradle --no-daemon :macrobenchmark:connectedBenchmarkAndroidTest \
  -P android.testInstrumentationRunnerArguments.class=com.vslauncher.macrobenchmark.LauncherMacrobenchmark#coldStartupWithBaselineProfile
```

This test is intentionally strict. If the candidate profile or ProfileInstaller is not actually packaged/usable, the benchmark should fail rather than silently pretending Baseline Profiles were measured.

Compare:

- startup median
- p90/p95 where reported
- frame overruns
- generated Perfetto traces
- ART/class-loading evidence when available

Do not compare A and B from different phones or thermal conditions.

---

# 10. Real sideload profile/dexopt observation

Macrobenchmark gives the controlled comparison.

Separately, record how the actual sideloaded Infinix installation behaves, because non-Play distribution can differ from Play-delivered Baseline Profile installation.

Inspect current dexopt state:

```sh
adb shell dumpsys package dexopt \
  | grep -A 4 -B 1 com.vslauncher
```

On Android 15/API 35 you can also inspect ART/profile information supported by the device.

Record the ART APEX version:

```sh
adb shell cmd package list packages --show-versioncode --apex-only art
```

Do not claim that opening a sideloaded APK automatically proves `speed-profile` install-time compilation.

---

# 11. API 34+ manual Baseline Profile fallback

On the current X6855 firmware, the AndroidX instrumentation controller is a
confirmed blocker, so manual collection is the primary device path.

Use:

```sh
bash scripts/infinix-manual-profile.sh capture
```

This follows Android's API 34+ manual flow, including:

```sh
adb shell cmd package compile -f -m verify com.vslauncher
adb shell pm art clear-app-profiles com.vslauncher
adb shell pm dump-profiles --dump-classes-and-methods com.vslauncher
```

The resulting HRF stays under `device-test-results/` until the same-device
manual A/B accepts or rejects it.

See [INFINIX_MANUAL_PROFILE.md](INFINIX_MANUAL_PROFILE.md).

---

# 12. What Codex must document

After a meaningful physical-device run, create a report from:

```text
docs/device-tests/TEMPLATE.md
```

Recommended filename:

```text
docs/device-tests/YYYY-MM-DD-infinix-<shortsha>.md
```

The committed report should contain facts, not generated-log dumps:

- Git commit and branch
- APK version/versionCode
- APK signer context
- exact Infinix model
- Android/API/security patch/build fingerprint
- refresh rate
- build result
- install result
- whether install preserved existing data
- HOME role/manual launcher selection result
- 0.8 search/browse + utility/help/accessibility result
- command/dial/URL action result
- pinned Home shortcut + import persistence result
- transient Undo result
- calm monochrome hierarchy result
- Android 15+ cold-start loop result
- crash/logcat result
- Macrobenchmark task/result locations
- Baseline Profile generator result
- candidate profile rule counts
- A/B startup metrics
- dexopt state after sideload
- known errors and exact resolution
- final PASS / FAIL / PARTIAL with reasons

Do not commit:

- full private device logs unnecessarily
- serial number
- personal notification content
- unrelated logcat data
- local paths containing secrets
- generated APKs

---

# 13. Merge gate for a generated profile

For the current X6855 manual path, a generated Baseline Profile should enter
`app/src/main/` only if all are true:

1. manual capture completed on the physical Infinix from the non-R8,
   non-debuggable `baselineProfile` variant
2. the human-readable profile is non-empty and sane
3. the release-like benchmark APK packages the candidate
4. A and B use the exact same benchmark APK SHA-256
5. every A sample resets to verify/cleared-profile state
6. every B sample explicitly installs the packaged profile and verifies
   `status=speed-profile`
7. repeated same-device startup measurements show a meaningful repeatable
   improvement or at minimum no meaningful regression
8. APK size change is recorded
9. launcher smoke behavior remains green
10. ProfileInstaller's skip file is restored/deleted after testing
11. debug/release build + lint remain green
12. the committed report labels the result as manual A/B, not Macrobenchmark

If the profile does not measurably help this tiny native launcher, leave it out
of production.
