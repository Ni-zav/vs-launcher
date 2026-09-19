# VS Launcher 0.2 audit

This document records the 0.2 redesign audit and the constraints that should remain true after future changes.

## Audit summary

| Area | Before | 0.2 state |
| --- | --- | --- |
| Render path | Formatters, typefaces, strings, and filtered lists could be created during redraw/touch work | Drawing uses cached Paint/Typeface/status state; only visible app rows are drawn |
| Idle work | Clock invalidated every 30 seconds | Clock is aligned to minute boundaries; no continuous animation loop |
| App discovery | Package query + labels/sort on UI thread | Dedicated single-thread app index executor |
| Search | Lowercasing/filtering repeated across draw and touch paths | Normalized label cached once; one O(n) filter pass when query changes |
| App list motion | Scroll updated after finger-up | Direct touch tracking + frame-synced `OverScroller` fling |
| Page motion | Discrete page switch | 1:1 drag with short frame-synced settle transition |
| Package visibility | `QUERY_ALL_PACKAGES` | Scoped MAIN/LAUNCHER `<queries>` visibility |
| Battery | Text-only state | Cached percentage/charging label + custom vector-like Canvas glyph |
| Clock | Hard-coded 24h formatting | Device 12/24h preference and current timezone |
| Weather | Placeholder | Coarse location + Open-Meteo + 20-minute local cache |
| Location freshness | N/A | Last-known location accepted only if <=30 minutes old |
| Insets | Fixed bottom margins | System bar + IME-aware layout |
| Touch hit testing | Row index could resolve outside visible region | Explicit viewport/visible-row bounds |
| Theme | Graphite + amber | True black + alpha-based white hierarchy |
| Validation | No repository build gate | GitHub Actions assembles debug APK and runs Android lint |

## Complexity targets

Let `n` be the number of launcher apps and `v` the number of visible rows.

- Idle frame work: **O(1)** and no scheduled frame loop.
- Home draw: **O(min(8, visible rows))**.
- All Apps draw: **O(v)**, independent of total list length.
- Search query change: **O(n)**.
- App indexing after package change/startup: **O(n log n)** because results are sorted once.
- App launch/hit test: **O(1)**.
- Battery/date/time update: **O(1)**.
- Weather render: **O(1)**; network/location work is off the UI thread.

A prefix index or trie is intentionally not used for search: Android launcher app counts are normally small enough that a compact O(n) scan has lower implementation/memory overhead while remaining outside the frame loop.

## Render-path rules

Do not add these to `LauncherSurface.onDraw()` or methods called per visible row:

- network or disk I/O
- package-manager queries
- `SimpleDateFormat` construction
- `Typeface.create(...)`
- list filtering/sorting
- bitmap decoding
- new Paint/Path/Drawable allocation per frame
- logging in the frame loop
- continuously running animators

Animation should exist only while the user is dragging, a page is settling, or a fling is active.

## Design-system rules

All reusable visual values belong in `DesignTokens.java`.

Hierarchy:
1. **Primary (96% white):** time, titles, app names.
2. **Secondary (72% white):** date, battery/weather metadata, secondary copy.
3. **Tertiary (46% white):** section labels / low-priority guidance.
4. **Divider (14% white):** one-pixel-equivalent separators.
5. **Selected surface (9% white):** subtle setting selection.

Typography is system-only:
- display: `sans-serif-light`
- body: `sans-serif`
- label: `sans-serif-medium`

Do not bundle a custom font unless visual identity clearly outweighs APK size, load cost, fallback behavior, and maintenance.

## Functional failure states

Weather must never invent data. It renders an explicit state when:
- location permission is missing
- device location is disabled
- location/network request fails and no cache exists

When a cache exists, failed refreshes keep the last valid cached weather instead of flashing an error.

If launching an app fails because the package/activity changed, the launcher refreshes its app index.

## Validation checklist

Before merging rendering or interaction changes:

1. `gradle --no-daemon :app:assembleDebug :app:lintDebug`
2. Install on at least one physical Android device.
3. Verify Home selection after pressing the system Home key.
4. Swipe repeatedly Settings ↔ Home ↔ All Apps; look for frame hitching.
5. Fling a long All Apps list and tap rows after scrolling.
6. Search for an app, clear the query, launch a result.
7. Install/uninstall an app and confirm the list refreshes.
8. Test 12-hour and 24-hour clock settings.
9. Test charging/unplugged battery states.
10. Test weather permission denied, permission granted, location disabled, offline cached state, and manual refresh.
11. Open/close the IME and rotate through supported system-bar/navigation modes.
12. Profile any suspected jank with Perfetto/System Trace before optimizing based on intuition.

## Remaining measurement boundary

Build + lint proves source/API correctness, not a universal FPS number. Frame timing must be measured on target hardware. For performance regressions, record frame timing before and after a change and compare the same gesture on the same device/build configuration.
