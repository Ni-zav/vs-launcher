# VS Launcher physical-device test report

## Identity

- Date:
- Tester/agent:
- Repository:
- Branch:
- Commit:
- VS Launcher version:
- versionCode:
- APK type:
- APK signer context:

## Device

- Manufacturer:
- Brand:
- Model:
- Android version:
- API level:
- Security patch:
- Build fingerprint:
- Display size:
- Density:
- Refresh rate/mode:
- ART APEX version:
- Battery/charging state:
- Thermal notes:

> Do not record the device serial number in the committed report.

## Build

- Command:
- Result: PASS / FAIL
- Lint:
- APK path:
- APK size:
- Notes:

## ADB install/update

- Command:
- Existing VS Launcher installation present: yes / no
- In-place update preserved data: yes / no / unknown
- Result: PASS / FAIL
- Exact failure code if any:
- Resolution:
- Config exported before destructive uninstall: yes / no / not needed

## Android 15+ startup regression

- Cold-start iterations:
- Successful starts:
- Failed starts:
- FATAL EXCEPTION found: yes / no
- Median TotalTime:
- Range:
- Notes:

## Launcher smoke test

- Set/select as HOME app:
- Home renders:
- Swipe left → All Apps:
- Swipe right → Settings:
- Swipe up quick launch:
- Swipe down search:
- Home long-press menu:
- All Apps long-press menu:
- Hidden app behavior:
- Alias behavior:
- Config export/import:
- Weather permission/refresh:
- Navigation/system bar behavior:
- Notes:

## Macrobenchmark

- Command:
- Result:
- JSON output path:
- Perfetto trace path:
- coldStartup:
- homeToAppsFrames:
- homeToSettingsFrames:
- allAppsFlingFrames:
- searchFilterFrames:

## Baseline Profile generation

- Capture build type: baselineProfile
- Generator command:
- startupProfile result:
- commonLauncherJourneys result:
- Generated baseline file(s):
- Generated startup file(s):
- Startup baseline rule count:
- Journey baseline rule count:
- Deduplicated baseline rule count:
- Candidate committed to production: no / yes

## Baseline Profile A/B

### A — CompilationMode.None

- Command:
- Median startup:
- p90:
- p95:
- Trace:

### B — CompilationMode.Partial(BaselineProfileMode.Require)

- Command:
- Median startup:
- p90:
- p95:
- Trace:

### Comparison

- Median delta:
- p90 delta:
- p95 delta:
- Repeatable improvement observed:
- Runtime/frame regression observed:
- APK size delta:
- Decision: keep candidate / reject candidate / needs more runs

## Manual Baseline Profile fallback

Use this section when AndroidX Macrobenchmark cannot run on the device.

### Capture

- Why manual fallback was required:
- Capture command:
- Capture build type:
- Capture result directory:
- HRF path:
- HRF SHA-256:
- HRF rule count:
- ProfileInstaller WRITE_SKIP_FILE result:
- SAVE_PROFILE result:
- API 34+ dump command result:

### Same-APK manual startup A/B

- Benchmark APK SHA-256:
- No-profile APK size:
- Candidate APK size:
- APK size delta:
- A iterations:
- A invalid samples:
- A median TotalTime:
- A p90:
- A p95:
- A stdev:
- B iterations:
- B invalid samples:
- B median TotalTime:
- B p90:
- B p95:
- B stdev:
- Median B-A delta:
- Median percentage delta:
- Same APK used for A and B: yes / no
- A reset verified as non-speed-profile: yes / no
- B INSTALL_PROFILE result=1 verified: yes / no
- B status=speed-profile verified: yes / no
- ProfileInstaller skip file deleted after test: yes / no
- Normal sideload delivery path: Play/DM / ProfileInstaller / none / not evaluated
- Comparison path:
- Decision: KEEP / REJECT / INCONCLUSIVE
- Limitation: manual same-device A/B; not Macrobenchmark

## Sideload dexopt observation

- dumpsys package dexopt state:
- speed-profile observed:
- reason:
- Notes:

## Errors encountered

| Error | Evidence | Root cause | Resolution | Regression? |
| --- | --- | --- | --- | --- |
| | | | | |

## Final result

- Overall: PASS / FAIL / PARTIAL
- Merge/release blocker:
- Follow-up:
- Evidence summary:
