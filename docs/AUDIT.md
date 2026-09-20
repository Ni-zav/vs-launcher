# VS Launcher 0.9.1 audit

This is the current release audit for the first public VS Launcher release. It replaces the historical 0.4-only audit.

## Release identity

| Item | Value |
| --- | --- |
| Version | `0.9.1` |
| Version code | `10` |
| Release commit | `v0.9.1` tag |
| Package | `com.vslauncher` |
| Minimum Android | API 26 / Android 8.0 |
| Target / compile SDK | API 35 |
| Debug APK | `dist/VS-Launcher-0.9.1-debug.apk` |
| APK size | 94,338 bytes |
| APK SHA-256 | `bad8d9b0877cfe4372f4b0e24b0fa9c8844b749c6729138710e70888d5338aa4` |

## Verified contract

- `python3 scripts/check-ui-contract.py` passes.
- `:app:assembleDebug` and `:app:lintDebug` pass with Gradle 8.7 and JDK 17.
- Production UI is native Java plus one custom Canvas surface; Compose and RecyclerView are not used.
- The renderer uses an absolute-black background and neutral grayscale semantic roles.
- Home, Apps/search, Settings, profile containers, and transient Undo use cached geometry and visible rows only.
- No idle animation loop, icon rasterization pipeline, or draw-time package/preferences/network I/O is allowed.
- Search ranking and utility recognition run only when the query changes; app indexing and weather stay off the UI thread.
- Accessibility nodes are lazy and are not created from `onDraw()`.
- Private Space and paused/locked profile apps are excluded from ordinary search and persistent Home/quick-launch state.

## Release behavior

0.9.1 keeps the 0.9 layout/search model and adds intent-aware singleton launch, numeric-query grace periods, explicit timer/alarm permission, and visible unavailable feedback when clock intents cannot be handled. Existing Home/Apps text-size preferences and JSON format compatibility are preserved.

See [`docs/releases/0.9.1.md`](releases/0.9.1.md) for the user-facing breakdown and [`CHANGELOG.md`](../CHANGELOG.md) for version history.

## Known boundaries

- The debug APK is for sideloading/testing and is not a stable production signing identity.
- Weather depends on coarse location and Open-Meteo; there is no account, history, or background weather service.
- FPS and startup performance are device-dependent; a build/lint pass is not a universal FPS claim.
- The AndroidX Macrobenchmark controller is unavailable on the current Infinix X6855/XOS firmware; manual device evidence is documented separately.
- Hidden apps remain launchable from an explicitly assigned Home/quick-launch slot; hiding is presentation-only.

## Validation checklist

Before changing renderer, search, persistence, or profile behavior, run:

1. `python3 scripts/check-ui-contract.py`
2. `gradle --no-daemon :app:testDebugUnitTest`
3. `bash scripts/build-debug-apk.sh`
4. `bash scripts/check-apk-size.sh`
5. Physical-device smoke tests for Home, Apps/search, Settings, Back navigation, gestures, persistence, profile privacy, and startup logs.

Do not claim a device/performance result without recording the device, commit, APK hash, scenario, and evidence location.
