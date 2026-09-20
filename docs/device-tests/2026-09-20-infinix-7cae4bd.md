# VS Launcher physical-device test report

## Identity

- Date: 2026-09-20 (Asia/Jakarta).
- Tester: Codex, physical USB-connected device.
- Repository: Ni-zav/vs-launcher.
- Starting revision: clean `main`, fast-forwarded from `a19eb18` to fetched
  `7cae4bdab6ef9d14229e45bf80ba546686fc05c6` before building.
- Test/fix branch: `test/infinix-04-20260920`.
- Production source: 0.4.0, versionCode 4, unchanged from the starting revision.
- APKs: locally debug-signed debug, optimized benchmark, and unoptimized
  baselineProfile capture variants; release APK also built.
- Evidence root (gitignored): `device-test-results/20260920-001511/`.
- Status: PARTIAL. Functional validation passed as detailed below; physical
  performance validation is blocked by the vendor freezing the test harness.

## Device

- Manufacturer / brand / model: INFINIX / Infinix / Infinix X6855.
- Android / API: 16 / 36.
- Security patch: 2026-04-01.
- Build fingerprint:
  `Infinix/X6855-OP/Infinix-X6855:16/BP2A.250605.031.A3/201350029:user/release-keys`.
- Display: 1080 x 2436, 440 dpi.
- Supported display modes: 60, 120, approximately 144 Hz. Initial observed
  mode 3 / render rate 60 Hz. Per-run display dumps retained.
- ART APEX: `com.google.android.art`, versionCode 371000140.
- Initial battery: 62%, USB charging, 32.9 C. Per-run battery/thermal dumps
  retained; overnight-interrupted runs are excluded from comparisons.
- Initial installed launcher: 0.3.0, versionCode 3; existing HOME role holder
  `com.vslauncher`.
- For performance testing only, HOME was temporarily assigned to XOS so a
  force-stopped launcher would not restart itself through the system HOME role.

## Build

- `ITERATIONS=20 bash scripts/infinix-adb-smoke.sh`: debug build and lint PASS.
- Initial debug lint: 0 errors, 7 warnings (see lint report).
- Debug APK: `dist/VS-Launcher-0.4.0-debug.apk`, 55,298 bytes initially.
- `gradle --no-daemon :app:assembleBenchmark :macrobenchmark:assembleBenchmark
  :app:assembleBaselineProfile :macrobenchmark:assembleBaselineProfile
  :app:assembleRelease :app:lintRelease`: PASS.
- Evidence: `build.txt`, `variants-build.txt`, `variant-isolation-build.txt`,
  `app/build/reports/lint-results-debug.html` and `lint-results-release.html`.
- Toolchain: Temurin JDK 17.0.20.1, Gradle 8.7, SDK 35 / Build Tools 35.0.0,
  ADB 37.0.1. Tools located under `/tmp/vs-launcher-android-build/`.

## ADB install/update

- `adb install -r dist/VS-Launcher-0.4.0-debug.apk`: PASS, `Success`.
- In-place update retained existing Home slots and Camera quick launch.
- No uninstall or app-data clear was performed.
- Preference backup: `preferences-before.tar`, retained privately in evidence.
- After interaction tests, `preferences-restored.xml` was byte-for-byte equal
  to the original `launcher_preferences.xml` in that archive.
- Evidence: `install.txt`, `device.txt`, `sideload-package.txt`.

## Android 15+ startup regression

- Initial loop: 20 requested, 20 `Status: ok`, 20 `LaunchState: COLD` samples.
- FATAL EXCEPTION: none in the initial smoke log.
- Initial TotalTime median: 510 ms; range: 394–686 ms. Debug `am start -W` timings are smoke
  evidence, not optimized Macrobenchmark or Baseline Profile measurements.
- Startup ordering remains `setContentView(root)`, `applyMinimalSystemUi()`,
  `root.requestApplyInsets()` in `MainActivity.java`.
- Evidence: `startup.txt`, `logcat-vslauncher.txt`, `home.png`.
- Final smoke on `da495f3`: debug build/lint and in-place debug restore PASS;
  20/20 successful COLD starts, zero FATAL EXCEPTION lines, median 581.5 ms,
  range 492–1,058 ms. Evidence: `final-smoke/` and `final-smoke-console.txt`.
  Summary paths and HOME role now render correctly, verifying both script fixes.
- Final preferences differ from the initial restored backup (Home layout/slots,
  quick app and other presets changed during the long interruption). These
  current settings were preserved in `preferences-final.xml`, not overwritten.
  The two smoke runs therefore are not a controlled performance comparison.

## Launcher smoke test

| Check | Physical-device observation / evidence |
| --- | --- |
| Home renders | PASS, existing three Home apps visible (`home.png`) |
| Swipe left | PASS, All Apps without keyboard (`all-apps.png`) |
| Swipe right | PASS, scrollable Settings (`settings.png`, `settings-bottom.png`) |
| Swipe up | PASS, configured Camera resumed (`quick-launch-activity.txt`) |
| Swipe down | PASS, search field focused and keyboard visible (`focused-search.png`, XML) |
| Home long press | PASS, change/rename/move/clear menu (`home-long-press.png`) |
| Rename and alias search | PASS, temporary `VSProbeAlias` displayed on Home; substring search resolves canonical ChatGPT (`alias-home.png`, `alias-search.png`) |
| Move / clear | PASS, moved to slot 2 then cleared (`moved-home.png`, `cleared-home.png`) |
| All Apps long press | PASS, Add to Home / Hide / App info / Uninstall menu (`all-apps-long-press.png`) |
| Hide / unhide | PASS, hidden result absent from search, checked in manager, then unchecked (`hidden-search.png`, `hidden-manager.xml`) |
| Hidden Home launch | PASS, assigned hidden app still launches (`hidden-home-launch.txt`) |
| Export / import | PASS, Storage Access Framework JSON round trip restores slots, alias and hidden state (`config-export.json`, `preferences-imported.xml`, `imported-home.png`) |
| Weather | Cached real weather rendered; permission-denial/offline permutations not exercised |
| System bars | Launcher status bar hidden and navigation gesture bar retained in screenshots |

Initial automation sent some gestures before Home transitions finished and
used Home as a page reset. Those captures were discarded and checks repeated
from an explicitly restarted activity. Native Canvas app rows are not exposed
as individual UIAutomator nodes; screenshots and package/activity state were
used alongside native dialog/search XML. No personal messages were sent and
no app uninstall was confirmed.

## Macrobenchmark

- Initial `:macrobenchmark:connectedBenchmarkAndroidTest`: FAIL before tests
  could run, test instrumentation incorrectly targeted `com.vslauncher`.
- Exact error: `NoClassDefFoundError: kotlin.jvm.internal.Intrinsics`.
- Evidence: `initial-suite/`, including original connected-test reports.
- Corrected suite started six tests but froze in `homeToAppsFrames` before
  completing any result. The Gradle host process disappeared during interruption;
  the device harness remained frozen and was explicitly force-stopped.
- No valid Macrobenchmark JSON or completed Perfetto trace was produced.
  `device-test-output/` contains only two temporary shell scripts and a trace
  configuration. These are not measurement results.
- Evidence: `corrected-suite/gradle.txt`, `power-disabled-freezer.txt`.

## Baseline Profile generation

- Capture variant: `baselineProfile`, non-minified, non-resource-shrunk.
- Initial direct instrumentation: 2 failures before journeys,
  `NoClassDefFoundError: kotlin.enums.EnumEntriesKt`.
- Fixed instrumentation manifest targets `com.vslauncher.macrobenchmark`;
  the test APK now contains the required Kotlin class.
- First corrected connected run: interrupted/incomplete, empty test failure
  after a long device/host interruption; not performance evidence.
- Current retry uses a 300,000 ms per-test timeout.
- The retry and a whitelisted retry both stalled with the harness UID frozen:
  parent `cgroup.freeze=1`, child `cgroup.events` reported `frozen 1`, and all
  inspected threads were in `do_freezer_trap`. Both were deliberately terminated
  with `am force-stop com.vslauncher.macrobenchmark`; the resulting runner
  `Process crashed` messages are termination evidence, not launcher crashes.
- A startup-only capture also froze. Android device-idle exemption,
  `RUN_ANY_IN_BACKGROUND allow`, an already-selected Unrestricted battery mode,
  process `am unfreeze --sticky`, and a temporary foreground-service delegate
  did not keep the harness UID running. The delegate was stopped.
- Evidence: `capture-freezer.txt`, `capture-whitelisted-freezer.txt`,
  `capture-whitelisted/`, `startup-capture-unfreeze.txt`,
  `harness-background.xml`.
- Evidence: `capture-initial-instrumentation.txt`, `capture-initial-manifest.xml`,
  `capture-fixed/`, `capture-retry/`.
- Generated baseline/startup files and rule counts: unavailable; neither
  automated capture journey completed.
- Diagnostic `pm dump-profiles --dump-classes-and-methods com.vslauncher`
  succeeded; the 4,646-byte `manual-primary.prof.txt` is retained with its command
  output. It is an optimized-app runtime dump, not a substitute for candidate
  rules captured from the required non-optimized variant.
- Candidate committed to production: no.

## Baseline Profile A/B

- A: `CompilationMode.None`: no completed measurement.
- B: `CompilationMode.Partial(BaselineProfileMode.Require)`: no completed
  measurement and no valid generated candidate available.
- Repeatability, median/p90/p95, frame regressions and APK size delta: unmeasured.
- Decision: reject adding profile rules to production for this run. This is
  insufficient evidence, not a measured finding that profiles are ineffective.

## Sideload dexopt observation

- Immediately after debug sideload/startup: `arm64 [status=verify] [reason=install]`.
- `speed-profile` was not observed for this debug sideload.
- Evidence: `sideload-dexopt.txt`, `art-apex.txt`.
- The all-users package query emitted a vendor user-999 permission error while
  still listing ART; use `--user 0` for subsequent package queries.

## Errors and fixes

| Issue | Resolution / commit |
| --- | --- |
| Smoke summary executed Markdown backticks as shell commands | Literal `printf` formatting, `a7d92d3` |
| Smoke script used unsupported `cmd role holders` | Use `get-role-holders`, `da495f3` |
| Capture and measurement tests shared both variants | Moved classes into variant source sets, `2070983` |
| Connected-test defaults could uninstall the target at cleanup | Keep APKs installed; disable incompatible-APK uninstall; documented in `2070983` |
| Test APK instrumented optimized target and lost required runtime classes | Self-instrumenting test process, `6d7a1be` |
| Implicit launch lookup requires a LAUNCHER activity, but VS Launcher exposes HOME only | Explicit HOME component intent; reset task for each journey, `11eebb2` |
| Profile journey silently skipped missing search | Fail explicitly when the search field is absent, `11eebb2` |

Production behavior, R8 optimization and resource shrinking were not weakened.
No emulator measurements were used. Raw device logs and screenshots remain
gitignored because they may contain private device information.

## Final result

- Overall: PARTIAL; full requested verification is not complete.
- Blocker: Infinix's UID freezer suspends the self-instrumenting benchmark app,
  including its timeout thread. Repeated package-specific exemptions failed.
- With explicit user approval, `background_power_saving_enable` was temporarily
  changed from 1 to 0. The corrected suite still reported `frozen 1`. It was
  stopped, and the setting restored to 1.
- HOME role restored to `com.vslauncher`; temporary device-idle exemption removed;
  `RUN_ANY_IN_BACKGROUND` restored to default; foreground-service delegate stopped.
  See `restored-device-settings.txt`.
- Exact build/test commands: `device-test-results/20260920-001511/commands.md`;
  individual Gradle runs retain `command.txt`; UI drivers retain input logs.
- Next requirement: a vendor-supported exemption that keeps the test harness
  running, followed by successful capture, same-device A/B and frame measurements.
- No production code changes or generated profile rules are included.
