# Infinix X6855 manual Baseline Profile workflow

This is the primary Baseline Profile path for VS Launcher on the current Infinix
X6855 / Android 16 / XOS firmware.

AndroidX Macrobenchmark remains in the repository for future/reference devices,
but the current X6855 firmware freezes the out-of-process instrumentation
controller even after exact-PID sticky unfreeze and documented freezer controls.
Do not spend additional device time retrying that controller unless the firmware
changes.

This fallback uses Android's documented manual Baseline Profile collection and
manual startup measurement mechanisms. It never runs a Macrobenchmark
instrumentation controller.

## What is valid here

This workflow can produce:

- a real human-readable Baseline Profile collected from the physical X6855
- a release-like R8 benchmark APK containing that candidate
- a same-device, same-APK startup A/B
- explicit ART state evidence for both arms
- median/p90/p95 startup comparisons
- APK size delta

It does **not** produce:

- Macrobenchmark JSON
- Macrobenchmark Perfetto traces
- `CompilationMode.None` / `CompilationMode.Partial` results
- automatic disk-cache normalization
- a claim that the measurements are as stable as Macrobenchmark

Android explicitly recommends Macrobenchmark when available, but also documents
manual collection and manual measurement as a supported fallback.

Primary Android documentation:

- https://developer.android.com/topic/performance/baselineprofiles/manually-create-measure
- https://developer.android.com/topic/performance/baselineprofiles/overview

AndroidX ProfileInstaller implementation used to validate the broadcast
operations/result codes:

- https://android.googlesource.com/platform/frameworks/support/+/HEAD/benchmark/benchmark-macro/src/main/java/androidx/benchmark/macro/ProfileInstallBroadcast.kt
- https://android.googlesource.com/platform/frameworks/support/+/HEAD/profileinstaller/profileinstaller/src/main/java/androidx/profileinstaller/ProfileInstallReceiver.java

---

## 1. Why the capture build is correct

The existing `baselineProfile` app build type is:

- non-debuggable
- non-R8/minified
- profileable
- signed with the local debug key
- dependent on ProfileInstaller 1.4.1

That matches Android's requirement that a manually captured profile come from a
release-like build that is not R8-optimized and is not debuggable.

The capture APK is assembled with:

```sh
gradle --no-daemon :app:assembleBaselineProfile
```

The script discovers the actual output path instead of assuming an APK filename.

---

## 2. Capture a real candidate on the X6855

Before capture, temporarily select the stock Infinix/XOS launcher as the default
Home app. The scripts intentionally refuse to run while `com.vslauncher` is the
HOME role holder, because Android may relaunch the default Home immediately
after `force-stop`, invalidating cold-process capture/measurement state.

Record the previous HOME choice and restore VS Launcher after all testing.

Run:

```sh
bash scripts/infinix-manual-profile.sh capture
```

Optional:

```sh
CAPTURE_ITERATIONS=5 bash scripts/infinix-manual-profile.sh capture
```

The script performs these stages.

### Prepare

1. require API 34+
2. build the non-R8/non-debuggable `baselineProfile` APK
3. install it with `adb install -r` — never uninstall
4. force-stop VS Launcher
5. write ProfileInstaller's skip file
6. compile the package to `verify`
7. clear existing app profiles

The skip-file command follows AndroidX's actual broadcast implementation:

```sh
adb shell am broadcast \
  -a androidx.profileinstaller.action.SKIP_FILE \
  -e EXTRA_SKIP_FILE_OPERATION WRITE_SKIP_FILE \
  com.vslauncher/androidx.profileinstaller.ProfileInstallReceiver
```

The script requires broadcast result code `10`.

For API 34+ the ART reset is:

```sh
adb shell cmd package compile -f -m verify com.vslauncher
adb shell pm art clear-app-profiles com.vslauncher
```

### Direct-ADB critical user journeys

The script drives the target app itself through `adb shell input`; there is no
instrumentation package/process.

Each pass covers:

```text
cold app process
→ Home
→ All Apps
→ vertical fling
→ Home
→ Settings
→ Home
→ swipe-left focused Apps search
→ enter "cal"
```

Display coordinates are derived from `wm size`.

The target launcher remains the only app process whose execution profile is
being collected.

### Save and dump

After the final CUJ, the script waits at least five seconds, then sends:

```sh
adb shell am broadcast \
  -a androidx.profileinstaller.action.SAVE_PROFILE \
  com.vslauncher/androidx.profileinstaller.ProfileInstallReceiver
```

ProfileInstaller result code `12` is required.

On API 34+:

```sh
adb shell pm dump-profiles --dump-classes-and-methods com.vslauncher
```

The human-readable profile is then pulled from:

```text
/data/misc/profman/com.vslauncher-primary.prof.txt
```

into:

```text
device-test-results/manual-profile-YYYYMMDD-HHMMSS/baseline-prof.txt
```

The script deliberately does **not** copy it into `app/src/main/`.

---

## 3. Inspect the candidate before A/B

Read:

```text
device-test-results/manual-profile-.../SUMMARY.md
device-test-results/manual-profile-.../baseline-prof.txt
device-test-results/manual-profile-.../capture-apk-sha256.txt
```

Sanity-check that:

- the file is non-empty
- it contains plausible VS Launcher / dependency class and method descriptors
- it is not a log file or command error
- the rule count is plausible
- no generated profile has already been committed

The manual ART profile may contain H/S/P flags. Do not invent a separate
`startup-prof.txt` from this file. The first manual candidate should use
`baseline-prof.txt` only.

---

## 4. Run the same-APK manual A/B

Use:

```sh
bash scripts/infinix-manual-ab.sh all \
  device-test-results/manual-profile-.../baseline-prof.txt
```

Optional:

```sh
ITERATIONS=30 STATE_SETTLE_SECONDS=3 \
  bash scripts/infinix-manual-ab.sh all \
  device-test-results/manual-profile-.../baseline-prof.txt
```

The script refuses to overwrite an existing:

```text
app/src/main/baseline-prof.txt
```

It stages the candidate into the working tree only. It does not commit it.

### APK-size reference

Before staging the candidate, the script builds the release-like benchmark APK
without a profile and records:

- APK SHA-256
- APK size

It then stages the candidate, rebuilds the benchmark APK, and records the
candidate APK's SHA-256 and size.

This gives a real profile APK-size delta.

### Same APK for A and B

Only the candidate benchmark APK is installed for measurement.

Both A and B therefore use:

- identical APK bytes
- identical code/resources
- same signing
- same package/version
- same phone
- same launcher configuration

The changed variable is the ART compilation/profile state.

---

## 5. A state — no profile-guided compilation

Before **every A sample** the harness:

```sh
adb shell am force-stop com.vslauncher
adb shell cmd package compile -f -m verify com.vslauncher
adb shell pm art clear-app-profiles com.vslauncher
```

It also keeps ProfileInstaller's skip file active.

The harness checks `dumpsys package dexopt` and refuses to collect the sample
if the package still reports `status=speed-profile`.

Then, after a settling delay:

```sh
adb shell am force-stop com.vslauncher
adb shell am start-activity -W -n com.vslauncher/.MainActivity
```

The raw `Status`, `LaunchState`, `TotalTime`, and `WaitTime` are saved.

Re-establishing A state before every sample prevents the first few launches from
JIT-warming later A samples.

---

## 6. B state — packaged Baseline Profile + speed-profile

Before **every B sample** the harness first clears the previous compiled/profile
state exactly as A does.

It then explicitly installs the Baseline Profile packaged in the same APK:

```sh
adb shell am broadcast \
  -a androidx.profileinstaller.action.INSTALL_PROFILE \
  com.vslauncher/androidx.profileinstaller.ProfileInstallReceiver
```

ProfileInstaller result code `1` is required.

Then:

```sh
adb shell am force-stop com.vslauncher
adb shell cmd package compile -f -m speed-profile com.vslauncher
```

The harness refuses to measure unless `dumpsys package dexopt` reports:

```text
status=speed-profile
```

It then measures the same explicit MainActivity start used by A.

Re-establishing B state before every sample prevents earlier B runs from changing
later B samples through additional runtime profiling/JIT state.

---

## 7. Statistics

Default:

```text
20 A samples
20 B samples
```

Results:

```text
device-test-results/manual-ab-.../state-a/startup.csv
device-test-results/manual-ab-.../state-b/startup.csv
device-test-results/manual-ab-.../comparison.md
```

`scripts/analyze-startup.py` reports:

- valid sample count
- median
- mean
- p90
- p95
- min/max
- sample standard deviation
- invalid sample count
- B-A median delta
- B-A percentage delta

Negative B-A is faster.

Do not decide from one fastest run.

The default arm order is `AB`. If the first session appears promising enough
to consider a production commit, run a reversed-order replication after
unstaging the first candidate:

```sh
bash scripts/infinix-manual-ab.sh unstage <FIRST_SESSION>

ORDER=BA ITERATIONS=20 STATE_SETTLE_SECONDS=2 \
  bash scripts/infinix-manual-ab.sh all \
  device-test-results/manual-profile-.../baseline-prof.txt
```

This helps expose simple thermal/order drift.

If the median change is tiny relative to the distributions/stdev, the direction
does not repeat across AB/BA order, or the upper percentiles move in the wrong
direction, treat the result as inconclusive.

---

## 8. Measurement validity

The report must call this:

> manual same-device, same-APK Baseline Profile startup A/B

Do **not** call it a Macrobenchmark A/B.

This method is weaker than Macrobenchmark because it lacks some of
Macrobenchmark's automation/noise controls and tracing facilities.

It is still materially stronger than:

- subjective launch feel
- one `am start -W` sample
- comparing two different APKs
- comparing freezer-enabled vs freezer-disabled runs
- using frozen instrumentation runs

No XOS freezer setting needs to be changed for this workflow.

---

## 9. Delivery caveat for VS Launcher

The current Gradle configuration includes ProfileInstaller only in:

- `benchmarkImplementation`
- `baselineProfileImplementation`

The normal debug/release launcher APK therefore does **not** currently contain
ProfileInstaller.

Android supports two common delivery paths:

- Play/DexMetadata installs, where the installer provides the profile alongside
  the APK.
- Jetpack ProfileInstaller, which copies the packaged profile into ART's
  package profile area after a normal APK install.

For VS Launcher's current GitHub/direct-sideload workflow, committing
`baseline-prof.txt` proves/builds the profile but does not by itself guarantee
that a plain sideload of the normal APK receives profile-guided compilation.

Therefore keep two decisions separate:

1. **Does the candidate improve VS Launcher?** — answered by this manual A/B.
2. **How should direct-sideload users receive it?** — a follow-up delivery
   decision after benefit is measured.

Do not silently add ProfileInstaller to normal production variants before the
candidate is proven useful. If the candidate is accepted, evaluate either:

- a small explicit production ProfileInstaller dependency, with APK-size/runtime
  smoke measurement, or
- a distribution path that supplies matching DexMetadata.

The manual A/B harness intentionally uses the benchmark variant's
ProfileInstaller so it can force-install the candidate in a controlled test.

---

## 9. Production profile decision

A manually generated candidate can be considered for production only if:

1. capture completed successfully on the physical X6855
2. the HRF is non-empty and sane
3. the release-like APK visibly packages a Baseline Profile asset
4. A and B use the same benchmark APK SHA-256
5. every A sample resets to `verify` / cleared profiles
6. every B sample successfully installs the packaged profile
7. every B sample verifies `status=speed-profile`
8. the startup distribution shows a meaningful repeatable benefit or at minimum
   no meaningful regression
9. the APK-size delta is recorded
10. launcher smoke behavior remains correct
11. the device report clearly labels the manual methodology
12. the ProfileInstaller skip file is deleted after testing
13. a production KEEP decision is supported by a reversed-order replication
    when practical on the same device
14. the report states whether the intended distribution path actually delivers
    the profile to ART

If the candidate is inconclusive or slower:

```sh
bash scripts/infinix-manual-ab.sh unstage <SESSION_DIR>
```

Do not commit it.

If accepted, commit:

```text
app/src/main/baseline-prof.txt
docs/device-tests/YYYY-MM-DD-infinix-<shortsha>.md
```

in separate focused commits where practical.

---

## 10. Restore normal ProfileInstaller behavior

The capture/A-B flow writes a skip file to prevent automatic profile
installation from contaminating A.

After testing:

```sh
bash scripts/infinix-manual-ab.sh cleanup <SESSION_DIR>
```

or:

```sh
bash scripts/infinix-manual-profile.sh restore
```

Both use AndroidX's explicit `DELETE_SKIP_FILE` operation and require result
code `11`.

The A/B `all` command attempts cleanup automatically even though it leaves the
candidate file staged for review.

Finally, restore VS Launcher as the default Home app and verify normal launcher
behavior.

---

## 11. Existing Macrobenchmark code

Do not delete:

- `macrobenchmark/`
- `LauncherMacrobenchmark`
- `BaselineProfileGenerator`
- freezer research/docs

They remain useful when:

- Infinix ships a firmware fix
- another physical device becomes available
- CI/device-lab hardware is added

The manual workflow exists because the current X6855 firmware prevents the
AndroidX controller from remaining runnable, not because Macrobenchmark is the
wrong architecture.
