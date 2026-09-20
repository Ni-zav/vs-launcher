# VS Launcher

**First public release: 0.9.1 · Android 8.0+ · package `com.vslauncher`**

Download the APK and read the complete release breakdown in [`docs/releases/0.9.1.md`](docs/releases/0.9.1.md). The current architecture, verification evidence, known boundaries, and release identity are tracked in [`docs/AUDIT.md`](docs/AUDIT.md); this replaces the old 0.4-era audit.

VS Launcher is a native, text-first Android home screen written in Java with platform APIs. Version 0.9 keeps the production APK deliberately small: an absolute-black Canvas, a fixed neutral monochrome hierarchy, direct gestures, latent utilities, lazy virtual accessibility nodes, no continuous render loop, and no production UI framework.

## Interaction

- **Swipe left** from Home → Apps opens in search mode with the field focused and keyboard requested.
- **Start scrolling Apps** → search/query/keyboard disappear, the list expands, and the right-side `#–Z` fast-scroll rail becomes available.
- **Hold/drag the `#–Z` rail** → jump to the nearest available bucket; the actual active initial stays bright on the rail, matching apps are promoted, and unrelated apps temporarily recede.
- **Tap APPS** or **pull downward at the top of browse mode** → re-enter focused search without going Home.
- **Swipe right** from Home → Settings.
- **Swipe up** from Home → the configured quick-launch app.
- **Swipe down** from Home → intentionally unused.
- **Type until one app remains** → the singleton stays auto-launchable, but only after an intent-aware quiet period. Continued typing cancels the pending launch; exact app/alias matches resolve faster; composition-sensitive CJK IMEs, forming calculator/timer/alarm/URL intent, structured actions, and general-text WEB intent suppress it. Numeric-only singleton queries get a longer grace period so keyboard long-press operators can arrive before an app fires.
- **Keyboard Go/Enter** → launches the first ranked result immediately.
- **Utility queries** → `timer 10m`, `alarm 07:30`, `23*17`, system commands, direct dial/URL actions, and explicit web fallback stay inside the same search surface.
- **Tap + ADD APP** → open the Home app picker directly.
- **Long-press a Home row** → change, rename, move, or clear that slot.
- **Long-press an app** → native app shortcuts (when exposed), optional Pin shortcut…, Add to Home, Hide, App info, and personal-profile Uninstall.
- **Tap time / date / battery / weather** → alarms / today's calendar / battery saver settings / weather refresh.
- **Back** is progressive: Search → Browse → Home; Settings → Home.
- **Hide / clear Home / remove pinned shortcut** → immediate action plus one transient Undo opportunity.

## Customization

Settings stays text-only and scrollable. Tapping a row cycles a small preset or toggles the option.

### Home
- Visible apps: 1–8.
- Vertical position: Top / Center / Bottom.
- Horizontal alignment: Left / Center / Right.
- Density: Dense / Compact / Normal / Spacious.
- Home text: Small / Medium / Large.
- Persistent per-slot app or pinned app-shortcut assignment.
- Per-app aliases on Home.
- Move Home slots up/down or clear them.

### Status
- Toggle Time / Date / Weather / Battery independently.
- Status layout: Time first / Date first / Compact.
- Time format: System / 24h / 12h.
- Date style: Weekday / Short / Numeric.
- Weather detail: Both / Temperature / Condition.
- Battery detail: Both / Icon / Percent.

### Apps / interaction
- Apps text: Small / Medium / Large, independent from Home text.
- Quick-launch app for swipe up.
- Search-first Apps entry with automatic browse-mode collapse.
- Native app shortcuts are loaded only after long-press.
- Animation: Instant / Fast / Normal.
- Long-press haptics: On / Off.
- Hidden-app manager.
- JSON configuration export/import through Android's document picker. No storage permission, account, database, or cloud service is required.
- Bottom `HELP → How to use` opens the compact interaction guide; typing `help` opens the same guide from search.

## Search

Search remains a compact O(n) app-ranking pass performed only when the query changes. Alias data, word/camel-case initials, and normalized app labels are cached outside the keystroke path.

App ranking remains deterministic:

1. canonical app-name prefix
2. alias prefix
3. canonical initials
4. alias initials
5. canonical app-name substring
6. alias substring
7. bounded canonical subsequence fallback
8. bounded alias subsequence fallback

Normalization is forgiving but deterministic: accents are stripped for matching and punctuation/repeated whitespace collapse to word separators. For example, `Pokémon` matches `pokemon`, and `my-app` matches `my app`.

Passive SYSTEM rows remain behind apps, but explicit structured intent is allowed to take the first row: DIAL, OPEN, TIMER, ALARM, CALC, or deliberate WEB. Those rows never auto-launch; tap or keyboard Go/Enter is required. A lone app still auto-launches after an intent-aware quiet period when the query remains app-like. Every edit cancels the previous pending launch. Latin Gboard composing spans do not suppress normal app launch; composition-sensitive CJK input still does. Numeric-only queries wait longer, and unfinished arithmetic/time/URL input blocks app auto-launch before the corresponding utility is fully parseable.

Examples include `wifi`, `internet`, `volume`, `bluetooth`, `battery`, `settings`, `timer 10m`, `alarm 07:30`, `23*17`, `example.com`, `help`, `calendar`, `storage`, `keyboard`, `nfc`, `display`, `sound`, `location`, and `notifications`. `Search web` appears when nothing direct matches and may also coexist with weak app matches for sentence-like multi-word text, so a coincidental fuzzy app cannot steal a general query. VS itself performs no network search.

Aliases affect Home and app search ranking; Apps still shows the application's canonical label. Hidden apps are excluded from Apps/search only. Existing personal/work Home slots and quick-launch assignments can still launch a hidden app.

## Calm monochrome design

The background remains absolute black:

```text
#000000
```

Foreground hierarchy uses a small fixed neutral scale rather than pure white everywhere:

| Role | Value |
| --- | --- |
| Focus / active press | `#F0F0F0` |
| Primary | `#DCDCDC` |
| App/body | `#C2C2C2` |
| Secondary/meta | `#909090` |
| Quiet/navigation | `#646464` |
| Disabled/unavailable | `#464646` |
| Dividers | `#2C2C2C` |

There are still no chromatic theme colors, gradients, blur, shadows, wallpapers, cards, or decorative animation. The hierarchy comes from luminance, system font weight, text size, whitespace, placement, and transient emphasis.

Pressed rows keep the black surface and brighten text instead of flashing a white rectangle. The focused search field is borderless. The `#–Z` rail is intentionally quiet until touched; scrubbing uses only the existing luminance hierarchy to focus one bucket. Low battery receives temporary luminance emphasis rather than color.


The full interaction/privacy rationale is documented in **[docs/FRICTIONLESS_FEATURES.md](docs/FRICTIONLESS_FEATURES.md)**.

The launcher uses Android system fonts only:

| Role | Typeface | Default size |
| --- | --- | ---: |
| Time | sans-serif-light | 62sp |
| App names | sans-serif | 19sp |
| Date | sans-serif-medium | 13sp |
| Metadata | sans-serif | 13sp |
| Section labels | sans-serif-medium | 11sp |

Home and Apps text sizes use separate discrete presets. Home rows also support discrete vertical position, horizontal alignment, and density without arbitrary sliders.

## Profiles and Private Space

VS Launcher uses Android's launcher APIs rather than duplicating profile state.

- Personal apps remain the normal default list.
- Work-profile apps appear only when Android exposes a work profile.
- A compact `WORK  PAUSE/PAUSED` container is shown only when relevant.
- Android 15 Private Space appears as a separate `PRIVATE  LOCK/LOCKED` container only when present.
- Locked/paused profile apps are not enumerated into Apps or search.
- Private Space can be hidden from the existing Hidden apps manager; users without Private Space get no extra setting.
- Private Space apps are never eligible for persistent Home slots or swipe-up quick launch.
- Existing personal/work Home slots and quick-launch assignments remain compatible across the 0.7–0.9 configuration format.

## Performance model

The UI thread should be almost idle while Home is not moving.

- Paints and Typefaces are created once.
- Major pixel geometry and hit regions are cached when size, insets, or UI configuration changes.
- Frequently measured status strings are cached when their underlying state changes.
- Saved app components cache their flattened keys and use an O(1) lookup map.
- Search aliases are cached and normalized outside the TextWatcher hot path.
- The typed query is normalized exactly once per edit and the normalized value is reused by app ranking, commands, alarm parsing, result emphasis, and singleton validation.
- Search ranking is one pass over visible apps while preserving deterministic tier order.
- Canonical and alias initials are precomputed outside typing.
- Fuzzy fallback is bounded ordered-subsequence matching rather than quadratic edit distance.
- `#–Z` first-row indices and each browse row's alphabet bucket are cached when the browse list changes.
- Search command definitions are static and only matched when the query is at least two normalized characters.
- Dial/URL recognition, timer/alarm parsing, and calculator parsing are local and permissionless; they do not index contacts or history.
- Search utility parsers are I/O-free and scale with query length; they do not introduce another app-list traversal.
- Canvas accessibility uses a lazy platform `AccessibilityNodeProvider`; accessibility objects are never created from draw hot paths, and no accessibility polling/render loop exists when services are off.
- Home shortcut IDs/labels are persisted without startup shortcut queries.
- Undo keeps only one in-memory reversal and schedules one delayed clear callback.
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
dist/VS-Launcher-0.9.1-debug.apk
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
├── SearchRanking         pure-Java deterministic app-search tiers
├── SearchNormalization   accent/punctuation tolerant normalization
├── SearchCommand         static latent system-command catalog
├── SearchResult          flat app/system/direct-utility search rows
├── QueryActions          permissionless dial/URL recognition
├── TimeQueryActions      pure-Java timer/alarm parsing
├── CalculatorAction      bounded local arithmetic parser
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
5. Home remains absolute-black, monochrome, text-first, and visually quiet
6. Home and Apps use whitespace rather than repetitive row dividers
7. Settings keeps explicit section structure
8. draw hot paths do not allocate, measure text, or convert dp/sp
9. new capabilities should remain latent behind existing gestures, taps, long-press, search, or transient overlays
10. locked Private Space apps never enter normal search or persistent Home/quick-launch state
11. new foreground colors must remain neutral grayscale semantic roles, not chromatic themes
12. non-app search actions never participate in automatic launch
13. reversible launcher actions prefer transient Undo over confirmation dialogs
14. typed search normalizes once, then reuses that cached value across matching/policy logic
15. latent utility parsers stay local/I/O-free and must not add another app-list traversal
16. accessibility semantics stay outside `onDraw()` and add no permanent visual surface
