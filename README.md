# VS Launcher

VS Launcher is a dependency-free native Android home screen written in Java with platform APIs only. Version 0.3 keeps the interface deliberately sparse: a true-black canvas, white typography, live device status, a fast text-first app list, and direct gestures.

## What works

- Android Home/launcher intent handling.
- 1–8 persistent Home app slots; long-press any Home row to replace its app.
- Searchable All Apps page.
- Smooth horizontal drag between Settings, Home, and All Apps.
- Kinetic All Apps scrolling with Android's `OverScroller`.
- Swipe up on Home launches one user-selected quick app.
- Settings contains only Home visible-app count and the swipe-up app selector.
- System status bar is hidden while VS Launcher is active.
- Live battery percentage, charging state, and a custom battery glyph.
- Date/time aligned to minute boundaries and the device's 12/24-hour setting.
- Current weather from Open-Meteo using coarse location only.
- Weather cached locally for 20 minutes; stale last-known location is rejected after 30 minutes.
- App list refresh when packages are installed, removed, changed, or replaced.
- Insets for status/navigation bars and the on-screen keyboard.

## Design system

The visual system is intentionally monochrome and uses Android system fonts so there is no bundled font payload or font-loading work.

| Role | Typeface | Size | Tone |
| --- | --- | ---: | --- |
| Time | sans-serif-light | 62sp | 96% white |
| Titles | sans-serif | 20sp | 96% white |
| App names | sans-serif | 19sp | 96% white |
| Date | sans-serif-medium | 13sp | 72% white |
| Metadata | sans-serif | 13sp | 72% white |
| Section labels | sans-serif-medium | 11sp | 46% white |

Core layout uses a 24dp horizontal gutter, 54dp app rows, 12dp corners, subtle 14% white dividers, and a pure `#000000` background. Tokens live in `DesignTokens.java`; avoid one-off colors, type sizes, or spacing outside that file.

## Performance model

The launcher is designed so the UI thread is almost idle when the screen is not moving.

- Paints, typefaces, date formatters, normalized app labels, and status strings are cached instead of recreated inside `onDraw()`.
- App discovery/sorting and weather network work run on dedicated background executors.
- All Apps draws only rows intersecting the visible viewport instead of iterating/drawing the full app list.
- Horizontal page motion and fling scrolling invalidate on display frames with `postInvalidateOnAnimation()`.
- There is no continuous animation loop, blur, shadow pipeline, image decoding, icon rasterization, or third-party UI framework.
- Search filtering is a single linear pass over cached lowercase labels.
- Weather is rate-limited by a 20-minute cache and has 4-second connect/read timeouts.
- Package visibility uses a launcher-intent `<queries>` declaration rather than `QUERY_ALL_PACKAGES`.

Actual frame rate still depends on the device, refresh rate, thermal state, and Android compositor. Use a real device plus Perfetto/System Trace or Macrobenchmark when making future rendering changes instead of assuming a fixed FPS from code inspection alone.

## Navigation

- Swipe left from Home → All Apps.
- Swipe right from Home → Settings.
- Swipe horizontally back toward Home from either side page.
- Long-press a Home app row → choose the app for that exact slot.
- Swipe up on Home → open the quick-launch app selected in Settings.
- Settings → choose how many Home app rows are visible (1–8) and choose the swipe-up app.
- Type in the bottom search field on All Apps.
- Tap the weather status on Home to grant coarse location permission or force a refresh.
- Back from a side page → Home.

## Weather and privacy

Weather uses the Open-Meteo forecast API. VS Launcher requests only Android coarse location, rounds coordinates to two decimal places before sending them, stores only temperature/weather code/update time in local app preferences, and does not retain coordinates.

Weather data is provided by Open-Meteo. Check Open-Meteo's current terms before distributing the launcher for a commercial use case.

## Build and install

The Android device itself needs **no extra framework or runtime**. The APK contains the launcher code and native vector/adaptive icon resources; Android 8.0 / API 26+ is the runtime.

### Easiest: GitHub Actions

Open **Actions → Android CI → Run workflow**. A successful run publishes a downloadable **vs-launcher-debug-apk** artifact containing the installable APK.

### Local build

Build-machine requirements:

- JDK 17
- Android SDK 35 + Build Tools 35.0.0
- Gradle 8.7 or Android Studio

From the repository root:

```sh
bash scripts/build-debug-apk.sh
```

Output:

```text
dist/VS-Launcher-0.3.0-debug.apk
```

For exact sideload steps, ADB installation, persistent release signing, CI debug-key caveats, and the SVG/adaptive-icon workflow, see **[docs/BUILD_APK.md](docs/BUILD_APK.md)**.

## Architecture

```text
MainActivity
├── LauncherSurface   frame-synced drawing, gestures, hit testing
├── AppRepository     background app discovery, sort, search source
└── WeatherService    location, cache, network, weather mapping
```

The project intentionally stays small and dependency-free. New features should preserve three invariants: no blocking I/O on the UI thread, no avoidable allocation in the draw path, and no continuous work while the launcher is idle.
