# VS Launcher

VS Launcher is a native, text-first Android home screen written in Java with platform APIs. Version 0.6 keeps the production APK deliberately small: a pure-black Canvas, pure-white typography and linework, direct gestures, no continuous render loop, and no production UI framework.

## Interaction

- **Swipe left** from Home → Apps opens in search mode with the field focused and keyboard requested.
- **Start scrolling Apps** → search/query/keyboard disappear, the list expands, and the right-side A–Z fast-scroll rail becomes available.
- **Drag the A–Z rail** → jump directly to the nearest available app initial; the active letter is transient.
- **Swipe right** from Home → Settings.
- **Swipe up** from Home → the configured quick-launch app.
- **Swipe down** from Home → intentionally unused.
- **Type until one result remains** → the stable singleton result auto-launches after a short debounce.
- **Keyboard Go/Enter** → launches the first ranked result immediately.
- **Tap + ADD APP** → open the Home app picker directly.
- **Long-press a Home row** → change, rename, move, or clear that slot.
- **Long-press an app** → native app shortcuts (when exposed) plus Add to Home, Hide, App info, and personal-profile Uninstall.
- **Tap time / date / battery / weather** → alarms / today's calendar / battery saver settings / weather refresh.
- **Back** from a side page → Home.

## Customization

Settings stays text-only and scrollable. Tapping a row cycles a small preset or toggles the option.

### Home
- Visible apps: 1–8.
- Position: Top / Center / Bottom.
- Density: Compact / Normal / Spacious.
- Text size: Small / Medium / Large.
- Persistent per-slot app assignment.
- Per-app aliases on Home.
- Move Home slots up/down or clear them.

### Status
- Toggle Time / Date / Weather / Battery independently.
- Status layout: Time first / Date first / Compact.
- Time format: System / 24h / 12h.
- Date style: Weekday / Short / Numeric.
- Weather detail: Both / Temperature / Condition.
- Battery detail: Both / Icon / Percent.

### Interaction
- Quick-launch app for swipe up.
- Search-first Apps entry with automatic browse-mode collapse.
- Native app shortcuts are loaded only after long-press.
- Animation: Instant / Fast / Normal.
- Long-press haptics: On / Off.
- Hidden-app manager.
- JSON configuration export/import through Android's document picker. No storage permission, account, database, or cloud service is required.

## Search

Search remains a compact O(n) pass performed only when the query changes. Alias data and word/camel-case initials are normalized and cached outside the keystroke path, and all ranking tiers are decided in one traversal of visible apps.

Ranking is deterministic:

1. canonical app-name prefix
2. alias prefix
3. canonical initials
4. alias initials
5. canonical substring
6. alias substring
7. bounded canonical subsequence fallback
8. bounded alias subsequence fallback

Examples: `ytm` can resolve **YouTube Music** through cached initials, while the fuzzy fallback only accepts ordered characters inside a bounded span. It never outranks exact prefixes, initials, or substrings and does not use edit-distance/Levenshtein work.

Aliases affect Home and search ranking; Apps still shows the application's canonical label. Hidden apps are excluded from Apps/search only. Existing personal/work Home slots and quick-launch assignments can still launch a hidden app.

## Strict black / white design

The production visual system uses only:

```text
#000000
#FFFFFF
```

There are no alpha-gray hierarchy tokens, gradients, blur, shadows, wallpapers, or decorative animation. Hierarchy comes from:

- system font family/weight
- text size
- spacing
- geometry
- binary pressed-state inversion
- dividers only where structure benefits from them
- placement

The full interaction/privacy rationale is documented in **[docs/FRICTIONLESS_FEATURES.md](docs/FRICTIONLESS_FEATURES.md)**.

The launcher uses Android system fonts only:

| Role | Typeface | Default size |
| --- | --- | ---: |
| Time | sans-serif-light | 62sp |
| App names | sans-serif | 19sp |
| Date | sans-serif-medium | 13sp |
| Metadata | sans-serif | 13sp |
| Section labels | sans-serif-medium | 11sp |

Home app text size and row density can be changed with discrete presets.

## Profiles and Private Space

VS Launcher uses Android's launcher APIs rather than duplicating profile state.

- Personal apps remain the normal default list.
- Work-profile apps appear only when Android exposes a work profile.
- A compact `WORK  PAUSE/PAUSED` container is shown only when relevant.
- Android 15 Private Space appears as a separate `PRIVATE  LOCK/LOCKED` container only when present.
- Locked/paused profile apps are not enumerated into Apps or search.
- Private Space can be hidden from the existing Hidden apps manager; users without Private Space get no extra setting.
- Private Space apps are never eligible for persistent Home slots or swipe-up quick launch.
- Personal-profile preference keys remain compatible with 0.5.

## Performance model

The UI thread should be almost idle while Home is not moving.

- Paints and Typefaces are created once.
- Major pixel geometry and hit regions are cached when size, insets, or UI configuration changes.
- Frequently measured status strings are cached when their underlying state changes.
- Saved app components cache their flattened keys and use an O(1) lookup map.
- Search aliases are cached and normalized outside the TextWatcher hot path.
- Search ranking is one pass over visible apps while preserving deterministic tier order.
- Canonical and alias initials are precomputed outside typing.
- Fuzzy fallback is bounded ordered-subsequence matching rather than quadratic edit distance.
- A–Z first-row indices are cached when the browse list changes.
- Native app shortcuts are queried only on long-press.
- App/profile discovery uses Android LauncherApps on the background app-index executor.
- Settings row geometry is precomputed when size/configuration changes.
- App discovery, labels, and sorting run on a dedicated background executor.
- Weather location/network work runs off the UI thread.
- All Apps and Settings draw only visible rows.
- Page movement and flings invalidate only while motion is active.
- There is no idle animation/frame loop.
- No app-icon decoding/rasterization pipeline exists.
- Release builds enable R8 optimization and resource shrinking.
- Package visibility is scoped to MAIN/LAUNCHER activities rather than `QUERY_ALL_PACKAGES`.

Actual FPS depends on the device, display refresh rate, compositor, and thermal state. The repository includes a separate Macrobenchmark module for physical-device startup/frame measurements; see **[docs/BENCHMARK.md](docs/BENCHMARK.md)**.

For Nigel's current Infinix X6855 / Android 16 firmware, the AndroidX Macrobenchmark controller is frozen by XOS. The primary on-device Baseline Profile path is now the instrumentation-free manual workflow in **[docs/INFINIX_MANUAL_PROFILE.md](docs/INFINIX_MANUAL_PROFILE.md)**, with the broader device runbook in **[docs/INFINIX_DEVICE_TEST.md](docs/INFINIX_DEVICE_TEST.md)**.

## Android 15+

The launcher keeps edge-to-edge/fullscreen setup compatible with the Android 15+ startup path used by the current project: the content view is installed first, then status-bar hiding/system-UI behavior is applied.

The system status/notification bar remains hidden while VS Launcher is active. The navigation bar is not forcibly removed.

## Weather and privacy

Weather uses Open-Meteo and Android coarse location.

- coordinates are rounded to two decimals before the request
- coordinates are not persisted
- only temperature, weather code, and update time are cached
- weather cache TTL: 20 minutes
- stale last-known locations older than 30 minutes are rejected
- connect/read timeouts: 4 seconds

## Build and install

The Android device needs no Java, Gradle, Android Studio, Node.js, Flutter, React Native, Compose runtime, SVG runtime, or weather SDK.

Minimum runtime: **Android 8.0 / API 26**.

### GitHub Actions

Open **Actions → Android CI → Run workflow**. A successful run publishes the `vs-launcher-debug-apk` artifact.

### Local debug APK

Build-machine requirements:

- JDK 17
- Android SDK 35
- Build Tools 35.0.0
- Gradle 8.7

Run:

```sh
bash scripts/build-debug-apk.sh
```

Output:

```text
dist/VS-Launcher-0.6.0-debug.apk
```

For sideloading, Android Studio, ADB, persistent release signing, and the SVG/adaptive-icon pipeline, see **[docs/BUILD_APK.md](docs/BUILD_APK.md)**.

## Architecture

```text
MainActivity
├── LauncherSurface       Canvas rendering, motion, hit testing
├── LauncherLayout        pure-Java geometry math used by renderer + JVM tests
├── LauncherPreferences   typed persisted configuration + JSON portability
├── LauncherUiConfig      immutable render/interaction snapshot
├── AppRepository         LauncherApps discovery, profiles, shortcuts, ranked search
├── AppListItem           flat Canvas browse rows for apps/profile containers
├── LauncherProfile       minimal work/private profile state
├── ProfilePolicy         pure-Java privacy/persistence rules
├── SearchRanking         pure-Java deterministic search tier contract
└── WeatherService        coarse location, cache, network, weather mapping

macrobenchmark/           physical-device performance tests only
```

The normal production APK remains intentionally framework-free. AndroidX benchmark/profile tooling is isolated to the special benchmark variant/test module and is not part of the normal debug/release launcher runtime.

## Project invariants

Future changes should preserve:

1. no blocking I/O on the UI thread
2. no avoidable allocation in the per-frame draw path
3. no continuous work while the launcher is idle
4. no production dependency merely for decoration
5. Home remains black, white, text-first, and visually quiet
6. Home and Apps use whitespace rather than repetitive row dividers
7. Settings keeps explicit section structure
8. draw hot paths do not allocate, measure text, or convert dp/sp
9. new capabilities should remain latent behind existing gestures, taps, long-press, search, or transient overlays
10. locked Private Space apps never enter normal search or persistent Home/quick-launch state
