# VS Launcher 0.4 audit

This document records the architectural, performance, and visual constraints for the 0.4 customization release.

## Current contract

| Area | 0.4 state |
| --- | --- |
| Production UI | Native Java + one custom Canvas surface; no production UI framework |
| Palette | Strict `#000000` + `#FFFFFF` only |
| Idle rendering | No scheduled frame loop |
| Geometry | Cached when size/insets/UI configuration change |
| App lookup | O(1) component lookup map |
| App indexing | Background executor; O(n log n) sort |
| Search | Alias-aware ranked O(n) scan only when query changes |
| All Apps | Visible-row drawing + `OverScroller` |
| Settings | Visible-row drawing + `OverScroller` |
| Page motion | Direct drag + configurable settle animation |
| Home slots | Persistent 1–8 slots, aliases, move/clear/change |
| Hidden apps | Removed from drawer/search only; still launchable from assigned Home/quick slots |
| Status | Individually toggleable time/date/weather/battery with discrete layouts/formats |
| Configuration | Typed SharedPreferences + versioned JSON import/export |
| Release build | R8 optimization + resource shrinking |
| Benchmarking | Separate benchmark build/test module; not packaged into normal launcher runtime |
| Android 15+ | `setContentView()` happens before fullscreen/system-UI application |

## Complexity targets

Let `n` be installed launcher activities and `v` be visible rows.

- Idle frame work: **O(1)** with no scheduled frame loop.
- Home draw: **O(min(8, visible rows))**.
- All Apps draw: **O(v)**.
- Settings draw: **O(v)**.
- Query change: **O(n)** across a fixed number of ranking passes.
- App indexing: **O(n log n)** after startup/package changes.
- Saved component resolution: **O(1)**.
- App-row hit testing: **O(1)**.
- Battery/date/time rendering: **O(1)**.
- Weather network/location work: outside the UI thread.

A trie/prefix tree is intentionally not used. Typical launcher app counts do not justify the extra state and maintenance cost, and filtering does not execute per frame.

## Draw-path rules

Do not add these to `LauncherSurface.onDraw()` or per-visible-row drawing:

- disk/network I/O
- PackageManager queries
- SharedPreferences reads
- list filtering/sorting
- `Typeface.create(...)`
- formatter construction
- bitmap/icon decoding
- per-frame Paint/Path/Drawable allocation
- logging
- continuously running animation

Major geometry belongs in the cached layout state and should be recalculated only after a size, inset, density, position, or related configuration change.

## Strict two-color design

The production UI may use only:

- `#000000`
- `#FFFFFF`

Do not use alpha-white as hierarchy because it visually produces gray on the black background.

Hierarchy should come from:

- font family/weight
- text size
- spacing
- position
- line/outline geometry
- black/white inversion when a selection treatment is truly needed

Do not add blur, gradients, shadows, wallpapers, colored accents, or decorative motion.

System fonts only:

- display: `sans-serif-light`
- body: `sans-serif`
- label: `sans-serif-medium`

## Interaction contract

Home:
- left → All Apps
- right → Settings
- up → quick-launch app
- down → focused search
- long press Home row → change/rename/move/clear
- tap weather → permission/refresh

All Apps:
- tap → launch
- long press → Add to Home / Hide / App info / Uninstall
- hidden apps are presentation-only hiding, not a security feature

Settings:
- text-only rows
- discrete presets instead of arbitrary sliders
- vertical scrolling only while needed

## Persistence and portability

`LauncherPreferences` is the only typed source for user configuration.

The JSON export format is versioned. Import:
- uses Android's Storage Access Framework
- requires no broad storage permission
- is capped at 256 KB
- must validate the format version before applying

Do not store secrets in exported launcher configuration.

## Weather failure states

Weather must never invent a value.

Explicit/fallback states cover:
- missing coarse-location permission
- location services disabled
- network/location failure with no cache
- cached valid weather after a refresh failure

Coordinates are rounded before network use and are not persisted.

## Android 15+ invariant

Do not move fullscreen/system-bar manipulation back before the content view is installed.

The known-safe startup order is:

```text
construct root/surface
setContentView(root)
applyMinimalSystemUi()
requestApplyInsets()
```

This preserves the Android 15+ startup fix used on the current target device.

## Validation checklist

Before merging launcher behavior/rendering changes:

1. `gradle --no-daemon :app:assembleDebug :app:lintDebug`
2. Confirm CI publishes the debug APK artifact.
3. Install on a physical Android 15+ device.
4. Verify launcher selection after pressing Home.
5. Repeatedly swipe Settings ↔ Home ↔ All Apps.
6. Fling long All Apps and Settings lists.
7. Verify swipe-left browsing does not force the keyboard.
8. Verify swipe-down opens focused search.
9. Test alias prefix/substring search ordering.
10. Change, rename, move, and clear Home slots.
11. Hide/unhide apps and verify assigned hidden apps remain launchable.
12. Test quick-launch selection.
13. Test all position/density/text presets.
14. Toggle each status module and cycle its format/layout modes.
15. Test instant/fast/normal page motion and haptics off/on.
16. Export and re-import configuration.
17. Install/uninstall an app and confirm indexing/state remains safe.
18. Test 12h/24h/system time and all date styles.
19. Test battery charging/unplugged states and display modes.
20. Test weather permission denied/granted, location disabled, offline cache, refresh.
21. Test IME/system insets and Android 15+ fullscreen startup.
22. Run the Macrobenchmark suite on the same physical device before/after performance-sensitive changes.

## Measurement boundary

Build + lint prove source/API correctness, not a universal FPS number.

For performance changes:
- use the same physical device
- use the same launcher configuration
- compare the same gesture/scenario
- inspect Macrobenchmark frame/startup results and generated Perfetto traces
- avoid optimizing from visual intuition alone
