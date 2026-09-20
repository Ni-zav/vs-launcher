# Changelog

## 0.5.0 — 2026-09-20

### UI / UX

- Made Home visually quieter by removing repetitive app-row dividers.
- Made All Apps visually quieter by removing repetitive row dividers.
- Added binary black/white pressed-state inversion for Home, All Apps, and Settings rows.
- Changed empty Home-slot presentation so only the first empty slot shows `+ ADD APP`.
- Added a live visible/result app count to the All Apps header.
- All Apps now switches its header label from `APPS` to `SEARCH` while a non-empty query is active.
- Reworked Settings into explicit sections:
  - HOME
  - STATUS
  - GESTURES
  - APPS
  - DATA
- Kept the existing three-page gesture model:
  - Home ←/→ Apps and Settings
  - swipe up quick launch
  - swipe down focused search
- Preserved the strict `#000000` / `#FFFFFF` production palette with no gray hierarchy, wallpaper, blur, shadow, or decorative UI dependency.

### Search and code performance

- Cached each launchable app's flattened component key instead of recomputing it repeatedly.
- Cached and normalized aliases outside the search keystroke path.
- Removed `SharedPreferences.getAll()` alias loading from search typing.
- Replaced four full app-list search passes with one ranked traversal while preserving the ranking contract:
  1. canonical prefix
  2. alias prefix
  3. canonical substring
  4. alias substring
- Extracted deterministic search ranking into pure Java `SearchRanking`.
- Extracted renderer geometry math into pure Java `LauncherLayout`.
- Precomputed Settings row/section geometry rather than recalculating row offsets during draw/hit testing.
- Shared visible-range, hit-test, and scroll-bound math between production rendering and JVM tests.

### CI / regression protection

- Added pure JVM tests for:
  - search ranking precedence
  - row hit-testing boundaries
  - visible-row fitting across compact/normal/spacious row heights
  - Settings section spacing
  - scroll bounds
  - visible-range clamping
- Added a UI/performance architecture contract that fails CI if:
  - draw hot paths allocate objects
  - draw hot paths call `measureText()`
  - draw hot paths convert dp/sp
  - renderer code reaches SharedPreferences or PackageManager
  - production adds Compose or RecyclerView
  - the strict black/white token contract expands
  - required Settings sections disappear
- Added a debug APK size budget check with a 1 MiB default ceiling.
- CI now runs the JVM UI/search suite before normal build/lint and performance-variant assembly.

### Version / docs

- Bumped app version to `0.5.0` / versionCode `5`.
- Updated weather client User-Agent to `VS-Launcher/0.5`.
- Updated active APK build/device documentation for the 0.5.0 artifact name.
- Updated README architecture and performance invariants.

### Unchanged architectural constraints

- Native Java + Android platform APIs.
- Custom Canvas production UI.
- No Compose.
- No RecyclerView.
- No continuous idle render loop.
- Release R8 optimization/resource shrinking remains enabled.
- Existing Android 15+ startup ordering remains unchanged:
  `setContentView(root) → applyMinimalSystemUi() → requestApplyInsets()`.
- AndroidX Macrobenchmark/Baseline Profile tooling remains isolated from the normal production UI/runtime.
