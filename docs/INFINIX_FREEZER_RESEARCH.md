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
