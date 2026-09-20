# Android 16 / Infinix Macrobenchmark freezer research

This document is intentionally updated in small research batches. It separates:

- facts verified from AOSP / Android Developers
- device observations from the Infinix X6855
- candidate workarounds still under investigation

Do not treat a candidate as a recommendation until its scope and measurement impact are verified.

## Current device symptom

Observed on Infinix X6855, Android 16 / API 36:

- VS Launcher (`com.vslauncher`) works normally and can be the active HOME app.
- AndroidX Macrobenchmark / Baseline Profile instrumentation begins its UI journey.
- The instrumentation controller process `com.vslauncher.macrobenchmark` later stops making progress.
- Threads show `do_freezer_trap`.
- The controller's cgroup reports `frozen 1`.
- Manual unfreeze/background allow-list attempts have not produced a stable benchmark run.

This is not yet sufficient evidence to label the root cause as an Android 16 platform regression or an Infinix/XOS bug.

---

## Batch 1 — verified AOSP freezer semantics

### 1. Cached app freezer is standard Android behavior

AOSP documents the cached-app freezer as a cgroup-v2 mechanism. On Android 14+:

- cached app processes are normally frozen after roughly 10 seconds in cached state
- lifecycle events that promote a process should unfreeze it
- frozen processes receive no CPU time
- `ActivityManagerService` and `CachedAppOptimizer` manage the policy

Primary source:

- https://source.android.com/docs/core/perf/cached-apps-freezer

### 2. A normal active instrumentation process should not be cached

Current AOSP `OomAdjuster` explicitly checks:

```java
app.getActiveInstrumentation() != null
```

and gives such a process foreground OOM importance and a foreground-service process state.

Therefore, if the Macrobenchmark controller is still actively registered as instrumentation yet is moved into a frozen cached cgroup, that behavior is not what stock AOSP's normal OOM/freezer policy path suggests.

This does **not** yet prove an XOS bug. Possible explanations still include:

- instrumentation state being lost/changed before the freeze
- an Android 16 regression outside the normal OomAdjuster path
- vendor/XOS freezer or power-management policy overriding or supplementing AOSP behavior
- the process being frozen by a separate cgroup/power mechanism that resembles the cached-app freezer

Primary source:

- https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/am/OomAdjuster.java

Relevant AOSP behavior is around the `getActiveInstrumentation()` branch.

### 3. `use_freezer` is device-wide, not package-scoped

AOSP documents:

```sh
adb shell device_config put activity_manager_native_boot use_freezer false
adb reboot
```

as disabling the cached-app freezer.

This changes freezer behavior for the device, not only for `com.vslauncher.macrobenchmark`.

It must therefore be treated as a **device-wide diagnostic workaround**, not a package exemption.

Primary source:

- https://source.android.com/docs/core/perf/cached-apps-freezer

### 4. Always capture the original `use_freezer` value first

Before modifying it:

```sh
adb shell device_config get activity_manager_native_boot use_freezer
```

Record the output verbatim in the device-test result bundle.

Possible outputs include:

- `true`
- `false`
- `null` / no explicit override

If the original value was explicit:

```sh
adb shell device_config put activity_manager_native_boot use_freezer <original-value>
adb reboot
```

If there was no explicit override:

```sh
adb shell device_config delete activity_manager_native_boot use_freezer
adb reboot
```

Then verify:

```sh
adb shell device_config get activity_manager_native_boot use_freezer
adb shell dumpsys activity | grep -A 30 "CachedAppOptimizer settings"
```

Do not replace a previously-unset value with a permanent `true` merely to "restore" it; deleting the override is the closer rollback.

### 5. Supported `am freeze` / `am unfreeze` commands are diagnostic, not persistent exemptions

AOSP documents:

```sh
adb shell am freeze <process>
adb shell am unfreeze <process>
```

for testing/troubleshooting the freezer.

These commands do not establish a documented permanent package exemption. A process can become eligible for freezing again later when Activity Manager recalculates policy.

Primary source:

- https://source.android.com/docs/core/perf/cached-apps-freezer

### 6. Correction: `freeze_exempt_inst_pkg` is **not** a Macrobenchmark/instrumentation-package exemption

The name is easy to misread.

Current AOSP source documents:

```text
freeze_exempt_inst_pkg
```

as freezer exemption for **INSTALL_PACKAGES**.

The relevant source comments state:

```text
Returns whether freezer exempts INSTALL_PACKAGES.
```

and the per-process freezer record describes this exemption as applying to:

```text
system apps with INSTALL_PACKAGES permission
```

Therefore:

- `inst_pkg` here means **install-packages**, not instrumentation package.
- enabling this flag would not provide a documented exemption for the ordinary
  `com.vslauncher.macrobenchmark` instrumentation controller.
- it is not a package-name selector.
- the DeviceConfig key is still device-global configuration.
- it should **not** be used as a VS Launcher Macrobenchmark workaround.

Primary AOSP sources:

- https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/am/CachedAppOptimizer.java
- https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/am/ProcessCachedOptimizerRecord.java

### 7. What this rules out

Do **not** add this command to the test workflow:

```sh
adb shell device_config put activity_manager_native_boot freeze_exempt_inst_pkg true
```

It would change a global ActivityManager freezer policy whose documented target
is system apps with `INSTALL_PACKAGES`, while providing no documented
package-specific protection for the Macrobenchmark controller.

This false lead is useful because it narrows the remaining investigation:

1. verify whether the controller is still registered as active instrumentation
   at the exact moment XOS freezes it
2. verify its ActivityManager proc state / OOM adjustment at that moment
3. determine whether XOS is freezing it outside the normal AOSP
   CachedAppOptimizer eligibility path
4. investigate Android 16 / AndroidX Benchmark regressions before resorting to
   the device-wide `use_freezer=false` diagnostic

---

## Verification commands for the current Infinix symptom

Before changing freezer policy, capture a minimal evidence bundle.

### Controller PID and instrumentation state

```sh
adb shell pidof com.vslauncher.macrobenchmark
adb shell dumpsys activity instrumentation
```

Record whether the controller still appears as active instrumentation when frozen.

### Activity Manager freezer state

```sh
adb shell dumpsys activity | grep -A 50 "CachedAppOptimizer settings"
adb shell dumpsys activity | grep -A 30 "Apps frozen:"
```

### Process importance

Replace `<pid>`:

```sh
adb shell dumpsys activity processes | grep -A 20 -B 5 "pid=<pid>"
```

Record:

- proc state
- OOM adjustment if exposed
- instrumentation association if exposed

### Cgroup evidence

```sh
adb shell cat /proc/<pid>/cgroup
```

Using the returned cgroup path, inspect:

```sh
adb shell cat <cgroup-path>/cgroup.events
```

A `frozen 1` result is strong evidence that the kernel cgroup freezer is actually suspending the process.

### Freezer logs

```sh
adb logcat -d | grep -i -E 'freez|CachedAppOptimizer|ActivityManager'
```

AOSP also exposes freezer events in Perfetto under the `Freezer` track in `system_server`.

Primary source:

- https://source.android.com/docs/core/perf/cached-apps-freezer

---

## Current conclusion after Batch 1

Confidence:

- **High:** the controller is demonstrably being frozen at the cgroup level if `cgroup.events` reports `frozen 1`.
- **High:** `use_freezer=false` is a device-wide freezer disable, not a package-only workaround.
- **High:** stock AOSP gives an active instrumentation process foreground importance, so a continuously active instrumentation controller should not normally fall into the standard cached-app freezer path.
- **Medium:** the symptom is therefore likely an Android 16/OEM interaction or a state transition not yet captured, rather than simply "Macrobenchmark is a background app".
- **Not yet established:** whether `freeze_exempt_inst_pkg=true` is the safest practical workaround for this device.

Next research batch:

1. check current AndroidX Benchmark / Macrobenchmark Android 16 release notes and known issues
2. check AOSP / Android Issue Tracker for instrumentation-controller freezing on Android 16
3. keep the existing device-wide `use_freezer=false` option only as a diagnostic candidate until measurement impact is assessed


---

## Batch 3 — AndroidX Benchmark / Android 16 check

### 1. The project is already on the current stable Benchmark generation

As of 2026-09-20, AndroidX Benchmark stable is:

```text
1.5.0 — released 2026-09-09
```

Primary source:

- https://developer.android.com/jetpack/androidx/releases/benchmark

The 1.5.0 release history includes Android/Perfetto/UiAutomator fixes and
benchmark-safety changes, but the published release notes do **not** document a
fix or known issue matching:

```text
Android 16 instrumentation controller enters a frozen cgroup / do_freezer_trap
```

Therefore there is currently no primary-source basis for recommending a
Benchmark library upgrade/downgrade as the freezer workaround.

Do not change Benchmark versions merely to probe this device unless the test is
explicitly treated as an experiment and the before/after versions are recorded.

### 2. Supported Benchmark instrumentation arguments do not expose a freezer exemption

Android Developers documents Macrobenchmark instrumentation arguments such as:

- compilation controls
- tracing/profiling controls
- error suppression
- output location
- `SideEffectRunListener`

Primary source:

- https://developer.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args

There is no documented Benchmark instrumentation argument that makes the
controller process exempt from ActivityManager/cgroup freezing.

### 3. `SideEffectRunListener` is useful later, but it is not this workaround

Android Developers recommends:

```text
androidx.benchmark.junit4.SideEffectRunListener
```

to reduce unrelated background work during benchmark runs.

That can improve measurement consistency after the controller-freeze problem is
solved.

It does **not**:

- change the controller's OOM adjustment
- create an ActivityManager freezer exemption
- change cgroup freezer policy
- replace the need to diagnose why an active instrumentation controller is frozen

Do not present it as a fix for `frozen 1`.

### 4. No matching public Android 16 issue was established in this bounded search

A targeted search of the public Android Issue Tracker did not surface a
confirmed issue matching the specific combination:

```text
Android 16
+ active instrumentation / Macrobenchmark controller
+ cgroup frozen
+ do_freezer_trap
```

Absence of a surfaced public issue is **not** evidence that the bug does not
exist.

For now the classification remains:

```text
confirmed standard mechanism: cgroup cached-app freezer exists
confirmed abnormality: active instrumentation normally receives foreground importance
unconfirmed root cause: Android 16 platform regression vs Infinix/XOS policy interaction
```

### 5. Capture instrumentation state at the freeze boundary

The next physical-device run should answer one discriminating question:

> Is `com.vslauncher.macrobenchmark` still active instrumentation in
> ActivityManager at the exact time its cgroup reports `frozen 1`?

Capture immediately when the run stalls:

```sh
PID="$(adb shell pidof com.vslauncher.macrobenchmark | tr -d '\r')"

adb shell dumpsys activity instrumentation   > device-test-results/instrumentation-frozen.txt

adb shell dumpsys activity processes   > device-test-results/processes-frozen.txt

adb shell cat "/proc/$PID/cgroup"   > device-test-results/controller-cgroup.txt

adb shell cat "/proc/$PID/status"   > device-test-results/controller-status.txt
```

Then record the actual cgroup path from `controller-cgroup.txt` and capture
its `cgroup.events`.

Interpretation:

- **still active instrumentation + foreground-ish proc state + `frozen 1`**:
  strongly inconsistent with the normal AOSP cached-process eligibility path;
  prioritize OEM/XOS or platform regression investigation.
- **instrumentation no longer registered / proc became cached before freeze**:
  investigate why the instrumentation state or process importance changed;
  this may be an AndroidX/runner lifecycle failure rather than an OEM freezer override.

Do not use a frozen/stalled run as a performance sample.

### Batch 3 conclusion

No AndroidX configuration or current stable-version change is presently a
documented package-scoped solution.

The next research batch should focus only on:

1. Infinix/XOS-specific background/freezer controls and evidence
2. whether the global AOSP `use_freezer=false` diagnostic is acceptable for a
   controlled benchmark session when all package-scoped remedies fail
3. how that global change affects measurement validity and rollback


---

## Batch 4 — Infinix / XOS evidence

### 1. No official package-level XOS freezer control was found

A bounded search of current Infinix/XOS material did not surface an official
developer-facing control for:

- exempting one package from the kernel cgroup freezer
- exempting an instrumentation controller
- disabling an XOS freezer only for one package
- configuring Macrobenchmark/instrumentation process importance

Official Infinix XOS 16 product pages describe the OS and battery/platform
features at a product level, but do not document a supported ADB/API equivalent
to an ActivityManager package-specific freezer exemption.

Example official source:

- https://mx.pre.infinixmobility.com/note-60-pro

This is an **absence-of-documentation finding**, not proof that no hidden vendor
control exists.

Do not invent XOS shell settings or copy opaque vendor settings from unrelated
models into the automated benchmark workflow.

### 2. Community XOS 16 reports are consistent with unusually aggressive background management

Recent XOS 16 users report cases such as:

- apps being terminated despite being retained in Recents
- background activity continuing to fail after battery optimization changes
- auto-start/background apps behaving inconsistently
- battery optimization appearing to revert on some devices

Examples:

- https://www.reddit.com/r/InfinixSmartphones/comments/1urlh6k/apps_get_killed_everytime_even_when_in_recentsxos/
- https://www.reddit.com/r/InfinixSmartphones/comments/1uqh6od/infinix_gt_30_5g_delay_notification_issues_and/

These reports are **anecdotal**:

- they are different Infinix models
- they do not demonstrate the same cgroup path
- they do not establish an Android 16 framework defect
- they do not prove the X6855 Macrobenchmark freeze is caused by the same policy

They do, however, make an OEM/XOS-specific background-management interaction
plausible enough to keep investigating.

### 3. User-tested XOS/package remedies are already stronger evidence than generic advice

On the affected X6855, the following have already failed to produce a stable
Macrobenchmark controller:

- XOS Unrestricted battery use
- DeviceIdle whitelist
- `RUN_ANY_IN_BACKGROUND` allowance
- clearing inactive state
- `am unfreeze --sticky`
- temporary foreground-service delegation
- temporarily disabling XOS's global `background_power_saving_enable`

Therefore generic advice such as "set battery to unrestricted", "lock the app in
Recents", or "enable auto-start" should **not** be repeated as the recommended
solution unless a newly identified XOS control is materially different.

### 4. Current root-cause classification

Evidence currently supports this classification:

| Hypothesis | Current support |
| --- | --- |
| Normal AOSP cached-app freezer behavior | Weak as a full explanation: active instrumentation normally receives foreground importance |
| AndroidX Benchmark library defect with documented Android 16 fix | No matching documented issue/fix found in current 1.5.0 material |
| Android 16 framework regression | Plausible, not established |
| Infinix/XOS policy conflict or vendor override | Plausible; community reports are directionally consistent but anecdotal |
| Simple app battery-optimization misconfiguration | Low, because multiple package-level/background remedies were already tested |

### Batch 4 conclusion

There is currently no verified XOS package-scoped switch that is safer and more
specific than the already-tested remedies.

The next batch should therefore evaluate the AOSP-documented device-wide
diagnostic:

```sh
device_config put activity_manager_native_boot use_freezer false
```

with emphasis on:

1. exact original-value capture and rollback
2. reboot requirements
3. whether the setting is acceptable on a personal production/user build for a
   short controlled test session
4. whether disabling the freezer invalidates Baseline Profile generation
5. whether startup A/B remains defensible when both arms use the identical
   freezer-disabled environment
