# Infinix X6855 Android 16 freezes active Macrobenchmark instrumentation

## Summary

On an Infinix X6855 running Android 16, an active AndroidX Macrobenchmark or
Baseline Profile instrumentation controller is moved into a frozen cgroup. The
controller stops progressing even though the target launcher remains visible.
This prevents on-device Macrobenchmark and Baseline Profile generation.

## Device and software

- Device: Infinix X6855
- Android: 16, API 36
- Security patch: 2026-04-01
- Build fingerprint:
  `Infinix/X6855-OP/Infinix-X6855:16/BP2A.250605.031.A3/201350029:user/release-keys`
- Target app: `com.vslauncher`
- Test controller: `com.vslauncher.macrobenchmark`
- Test library: AndroidX Benchmark 1.5.0

No device serial number, personal app data, screenshots, or full bugreport is
included in this report.

## Reproduction

1. Install a profileable release-like target APK and its AndroidX
   Macrobenchmark test APK.
2. Start a Baseline Profile or Macrobenchmark instrumentation journey that
   drives the target through UI Automator.
3. After the controller process starts, apply the documented process-scoped
   command to its live PID:

   ```sh
   adb shell am unfreeze --sticky <controller-pid>
   ```

4. Inspect the controller while the test stalls:

   ```sh
   adb shell pidof com.vslauncher.macrobenchmark
   adb shell cat /proc/<controller-pid>/cgroup
   adb shell cat /sys/fs/cgroup/apps/uid_<uid>/pid_<controller-pid>/cgroup.events
   ```

## Observed result

The same live controller PID that received `am unfreeze --sticky` enters a
unified cgroup whose events are:

```text
populated 1
frozen 1
```

The capture does not complete. Earlier thread inspection showed the frozen
controller threads waiting in `do_freezer_trap`.

## Expected result

An active instrumentation controller should remain runnable for the duration of
the test. AndroidX Macrobenchmark runs its controller out of process by design,
so freezing that controller prevents its UI journey, profile capture, and test
completion.

## Controls already tested

These measures did not prevent `frozen 1` on the controller:

- XOS Unrestricted battery usage
- Device Idle allowlist
- `RUN_ANY_IN_BACKGROUND` allowance
- clearing inactive state
- foreground-service delegation
- `am unfreeze --sticky` applied to the exact live PID
- XOS background power-saving toggle

Two documented Android cached-app-freezer controls were also tested and
restored:

1. `device_config put activity_manager_native_boot use_freezer false`
   followed by reboot. XOS did not retain the override after reboot.
2. Android Developer Options backend `Settings.Global.cached_apps_freezer` set
   from `device_default` to `disabled`. The setting read back as `disabled`,
   but the controller still entered a cgroup reporting `frozen 1`.

The Developer Options value was restored to `device_default`. The DeviceConfig
value was restored to its original unset state (`null`).

## Request

Please investigate why an active instrumentation controller can be frozen on
this XOS build, and provide one of the following:

- a supported package or process exemption for AndroidX instrumentation and
  Macrobenchmark controllers, or
- a firmware fix that preserves the foreground importance of active
  instrumentation and prevents this freezer transition.

## Available sanitized evidence

The project retains local, private raw evidence for the maintainer. The
following paths are suitable references when attaching selected outputs through
an approved support channel:

- `device-test-results/codex-sticky-profile-20260920-110129/`
- `device-test-results/codex-sticky-profile-20260920-113942/`
- `device-test-results/freezer-session-20260920-110839/`
- `device-test-results/freezer-developer-option-20260920-114400/`

Review any raw log before upload because it may contain installed-package names
or other device metadata.
