# VS Launcher

VS Launcher is a dependency-free native Android home screen written in Java with platform APIs only. Version 0.2 keeps the interface deliberately sparse: a true-black canvas, white typography, live device status, a fast text-first app list, and direct gestures.

## What works

- Android Home/launcher intent handling.
- Up to eight apps on Home and a searchable All Apps page.
- Smooth horizontal drag between Settings, Home, and All Apps.
- Kinetic All Apps scrolling with Android's `OverScroller`.
- Configurable swipe-up shortcut.
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
| Time | sans-serif-light | 52sp | 96% white |
| Titles | sans-serif | 22sp | 96% white |
| App names | sans-serif | 19sp | 96% white |
| Date | sans-serif | 14sp | 72% white |
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
- Swipe up on Home → the action selected in Settings.
- Type in the bottom search field on All Apps.
- Tap the weather line on Home to grant coarse location permission or force a refresh.
- Back from a side page → Home.

## Weather and privacy

Weather uses the Open-Meteo forecast API. VS Launcher requests only Android coarse location, rounds coordinates to two decimal places before sending them, stores only temperature/weather code/update time in local app preferences, and does not retain coordinates.

Open-Meteo data is attributed in the Settings screen. Check Open-Meteo's current terms before distributing the launcher for a commercial use case.

## Build

Requirements:

- JDK 17
- Android SDK 35
- Gradle 8.7 or Android Studio with a compatible Gradle setup

From the repository root:

```sh
gradle --no-daemon :app:assembleDebug :app:lintDebug
```

GitHub Actions runs the same build + lint gate for `main`, `feat/**`, and pull requests.

Install the debug APK on a device/emulator, press Home, and select **VS Launcher** as the default launcher.

## Architecture

```text
MainActivity
├── LauncherSurface   frame-synced drawing, gestures, hit testing
├── AppRepository     background app discovery, sort, search source
└── WeatherService    location, cache, network, weather mapping
```

The project intentionally stays small and dependency-free. New features should preserve three invariants: no blocking I/O on the UI thread, no avoidable allocation in the draw path, and no continuous work while the launcher is idle.
