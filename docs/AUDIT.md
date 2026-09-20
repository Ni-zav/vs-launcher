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
- left → Apps with focused borderless search
- right → Settings
- up → quick-launch app
- down → intentionally unused
- Apps heading tap / pull-at-top → focused search
- Back → Search → Browse → Home
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
2. Confirm CI publishes the debug APK artifact and passes the UI/performance source contract.
3. Install on a physical Android 15+ device when device validation is available.
4. Verify launcher selection after pressing Home.
5. Repeatedly swipe Settings ↔ Home ↔ Apps.
6. Fling long Apps and Settings lists.
7. Verify swipe-left enters focused search and requests the keyboard.
8. Verify the first vertical Apps browse gesture removes search/query/keyboard and reveals the A–Z rail.
9. Verify A–Z + # scrubbing jumps to the nearest available initial and missing buckets stay visually quieter.
10. Test accent/punctuation normalization plus prefix/alias/initials/substring/bounded-fuzzy ordering.
11. Verify a lone app auto-launches beside passive SYSTEM rows, while explicit DIAL/OPEN/TIMER/ALARM/CALC/WEB/GUIDE intent suppresses app auto-launch; non-app results never auto-fire.
12. Verify typed search follows the cached normalized-query path.
13. Verify SYSTEM plus DIAL/OPEN actions execute only after deliberate tap/Go.
14. Verify `timer 10m`, `timer 1h 30m`, and `alarm 07:30` produce deliberate TIMER/ALARM rows.
15. Verify `23*17`, parentheses, divide-by-zero rejection, and CALC copy feedback.
16. Verify an unmatched ordinary query produces exactly one explicit WEB fallback and VS performs no network request itself.
17. Type `help` and open Settings → HELP → How to use; confirm both expose the same interaction model.
18. Pin a native app shortcut into Home, launch it, replace/remove it, Undo removal, export/import it, and verify package pin state remains sane.
19. Change, rename, move, and clear Home slots; tap + ADD APP directly; verify clear has transient Undo.
20. Hide an app from long-press and verify transient Undo restores it.
21. Verify the semantic monochrome scale, dividerless Settings rows, profile-header spacing, and no full-row white press inversion.
22. Hide/unhide apps and verify assigned hidden personal/work apps remain launchable.
23. If present, verify WORK/PRIVATE containers and that locked Private Space apps never enter search or persistent Home/quick-launch state.
24. Test quick-launch selection, all position/density/text presets, animation speeds, and haptics.
25. Toggle each status module and cycle its format/layout modes.
26. Export and re-import configuration from a 0.7-era config and confirm no migration is required.
27. Install/uninstall an app and confirm indexing/state remains safe.
28. Test 12h/24h/system time, date styles, battery states/modes, and weather permission/offline paths.
29. Test IME/system insets and Android 15+ fullscreen startup.
30. With TalkBack/touch exploration available, verify Home/status, visible Apps/search rows, profile headers, Settings, scrolling, long-click actions, browse-to-search, and transient Undo are reachable without changing visual rendering.
31. Run the Macrobenchmark suite on the same physical device before/after performance-sensitive changes when the device controller permits it.
## Measurement boundary

Build + lint prove source/API correctness, not a universal FPS number.

For performance changes:
- use the same physical device
- use the same launcher configuration
- compare the same gesture/scenario
- inspect Macrobenchmark frame/startup results and generated Perfetto traces
- avoid optimizing from visual intuition alone
