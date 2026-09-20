# Changelog

## Unreleased

### Calm visual follow-up

- Removed per-row Settings dividers; section labels and whitespace now carry grouping.
- Added a 16dp visual lead before WORK/PRIVATE profile headers without adding another persistent row or container.
- Kept empty-query search rows uniform; the first result is promoted only after a normalized query is actually present.
- Added CI source guards for these rendering invariants.

## 0.7.0 — 2026-09-20

### Calm monochrome hierarchy

- Replaced the previous pure-white-everywhere treatment with a fixed neutral luminance scale on absolute black:
  - focus: `#F0F0F0`
  - primary: `#DCDCDC`
  - app/body: `#C2C2C2`
  - secondary/meta: `#909090`
  - quiet/navigation: `#646464`
  - disabled/unavailable: `#464646`
  - dividers: `#2C2C2C`
- Removed full-row white pressed inversion.
- Pressed rows now brighten text only while retaining the black background.
- Search's deterministic first-ranked result is slightly stronger than lower-ranked rows.
- Settings section labels, row labels, values, metadata, and A–Z navigation now occupy distinct luminance roles.
- Native AlertDialogs use the same calmer monochrome hierarchy.
- Low battery temporarily rises one luminance level rather than introducing warning color.
- A–Z letters without a matching app recede further; the active scrub letter remains transient and prominent.

### Search / browse flow

- Kept swipe-left as the single Home → focused-search gesture.
- Added two zero-chrome ways to re-enter search from Apps browse mode:
  - tap the `APPS` heading
  - pull downward while already at the top of the list
- Back navigation is now progressive:
  - Search → Browse
  - Browse → Home
  - Settings → Home
- The focused search field is now borderless with no rounded rectangle or divider.
- Search retains automatic keyboard focus on entry.
- Stable singleton app auto-launch now counts only app results; quiet command rows do not prevent the lone app from launching.

### Search normalization

- Added shared Unicode NFD normalization.
- Diacritics are removed for matching:
  - `Pokémon` ↔ `pokemon`
  - `Résumé` ↔ `resume`
- Punctuation and repeated whitespace collapse to a single word separator:
  - `my-app` ↔ `my app`
- App labels are normalized once during indexing.
- Aliases are normalized once when the alias cache refreshes.
- Typed queries use the same normalization contract.

### Latent command layer

- Search can now surface quiet non-app rows without adding Home UI.
- Apps always remain before command rows.
- Command rows use small right-side semantic labels such as `SYSTEM`, `DIAL`, and `OPEN`.
- System commands include:
  - Wi-Fi
  - Internet
  - Volume
  - Bluetooth
  - Battery
  - Android Settings
  - VS Launcher Settings
  - Alarms
  - Calendar
  - Storage
  - Keyboard/input methods
  - NFC
  - Display
  - Sound
  - Location
  - Notifications
- Wi-Fi, Internet, Volume, and NFC use Android Settings Panels on API 29+ where available, with documented Settings fallbacks.
- Command-only results never auto-launch; the user must tap or press keyboard Go/Enter.

### Lightweight query actions

- Phone-like numeric queries produce a permissionless `DIAL` row using `Intent.ACTION_DIAL`.
- Domain/HTTP(S) queries produce an `OPEN` row using `Intent.ACTION_VIEW`.
- Neither action participates in automatic singleton launch.
- No contact indexing, call permission, AI parsing, search history, or browser engine was added.

### Home-pinned app shortcuts

- Native Android app shortcuts can now be pinned into ordinary Home text slots.
- App long-press keeps direct shortcut launch and adds a latent `Pin shortcut…` action when shortcuts exist.
- A pinned shortcut remains a single text row on Home—no icon, badge, card, or extra container.
- Shortcut component/id/label metadata is stored separately from the existing app-slot component key, preserving 0.6 configuration compatibility.
- Launcher pinning uses `LauncherApps.pinShortcuts()` and always submits the package's complete VS-pinned ID set because the Android API is non-cumulative.
- Replacing/removing a pinned shortcut re-pins the old package correctly.
- JSON export/import includes optional shortcut IDs/labels and re-pins configured shortcuts after import.
- Private Space apps remain ineligible for persistent Home shortcuts.

### Transient Undo

- Added a single Canvas-native transient message with a brighter `UNDO` affordance.
- No Snackbar/Material dependency, history screen, or permanent notification surface was added.
- Long-press Hide is immediately reversible.
- Home slot clear and pinned-shortcut removal are immediately reversible.
- Undo lives for 2.5 seconds and stores only one pending reversal at a time.

### Alphabet navigation

- Extended A–Z to `A–Z + #`.
- `#` catches numeric, symbolic, and non-A–Z initial labels.
- Available and unavailable rail letters use different quiet luminance levels.
- Fast-scroll indices remain precomputed whenever the browse list changes.

### CI / performance contracts

- Added JVM tests for tolerant search normalization.
- Added JVM tests for command matching, dial recognition, and URL recognition.
- Extended the no-allocation/no-`measureText`/no-dp-sp renderer contract to:
  - `drawSearchRows`
  - `drawTransientMessage`
- CI now validates that all design tokens remain neutral grayscale and that the launcher background remains absolute black.
- Existing restrictions remain:
  - no Compose
  - no RecyclerView
  - no `QUERY_ALL_PACKAGES`
  - no SharedPreferences/PackageManager/LauncherApps calls from `LauncherSurface`
  - no continuous idle frame loop

### Version / docs

- Bumped app version to `0.7.0` / versionCode `7`.
- Updated weather User-Agent to `VS-Launcher/0.7`.
- Updated README, frictionless feature model, build/install checks, audit checklist, Infinix runbook, and device-test template for the 0.7 interaction model.

### Still deliberately absent

0.7 continues to exclude persistent-surface feature categories that conflict with VS Launcher's product identity:

- widgets
- notification feeds/filtering
- folders
- usage-ranked/adaptive app ordering
- icon packs/wallpaper/theme systems
- a large configurable gesture vocabulary
- search history
- contact indexing
- AI/assistant behavior

The rule remains: add useful capability behind existing intent, not permanent pixels.


## 0.6.0 — 2026-09-20

### Frictionless Apps interaction

- Changed Home → Apps so swipe-left immediately enters focused search and requests the keyboard.
- Removed swipe-down search; downward Home swipe is intentionally unused.
- When the user starts vertically browsing Apps:
  - pending singleton auto-launch is cancelled
  - search query is cleared
  - keyboard/search field are removed
  - Apps viewport expands
  - the A–Z fast-scroll rail becomes available
- Added a transient right-edge A–Z scrubber with precomputed first-row indices.
- Added a transient enlarged active letter while scrubbing.
- Added a 160 ms stable-singleton search auto-launch path.
- Added keyboard Go/Enter as an immediate launch of the first ranked result.
- Changed the first visible empty Home slot so tapping `+ ADD APP` opens the app picker directly.

### Search

- Added cached canonical and alias initialisms:
  - `YouTube Music → ytm`
  - `Google Maps → gm`
- Expanded deterministic ranking to:
  1. canonical prefix
  2. alias prefix
  3. canonical initials
  4. alias initials
  5. canonical substring
  6. alias substring
  7. bounded canonical subsequence
  8. bounded alias subsequence
- Added a bounded ordered-subsequence fuzzy fallback instead of edit-distance/Levenshtein matching.
- Kept the complete ranking operation as one traversal of the searchable app list.
- Cached alias initials outside the TextWatcher path.
- Added JVM coverage for exact/initials/substring/fuzzy precedence.

### Semantic Home actions

- Time tap opens Android alarms.
- Date tap opens the calendar at the current date/time.
- Battery tap opens battery-saver settings with general Settings as fallback.
- Weather tap keeps its existing permission/refresh behavior.
- These actions add no permanent Home controls.

### Native app shortcuts

- Migrated app launching/discovery to Android `LauncherApps`.
- App long-press now queries dynamic/manifest shortcuts only on demand.
- Up to four enabled native app shortcuts appear before launcher-level actions.
- No shortcut query is performed during launcher idle or ordinary browsing.
- Stale activity/shortcut launches fail safely.

### Work profiles and Android 15 Private Space

- App entries are now profile-aware using `UserHandle` + user serial while preserving existing personal-profile preference keys.
- Added conditional WORK profile containers:
  - `WORK  PAUSE` when active
  - `WORK  PAUSED` while quiet
- Added Android 15 Private Space support with `ACCESS_HIDDEN_PROFILES`.
- Added conditional PRIVATE profile containers:
  - `PRIVATE  LOCK` when unlocked
  - `PRIVATE  LOCKED` while locked
- Locked/paused profiles remain represented by their container but their apps are not enumerated into browse/search.
- Private Space can be hidden from the existing Hidden apps manager and adds no setting when no private profile exists.
- Private Space apps are not eligible for persistent Home slots or swipe-up quick launch.
- Profile quiet/lock toggles use Android `UserManager.requestQuietModeEnabled()` on supported API levels.
- Paused work-profile Home assignments remain visibly reserved as `Work paused` instead of becoming false empty slots.
- Added `LauncherApps.Callback` and profile lifecycle refresh handling.
- Added pure-Java `ProfilePolicy` with JVM tests for privacy, visibility, and Home persistence rules.

### Canvas / performance

- Added flat `AppListItem` rows so profile containers stay inside the existing Canvas list rather than creating nested Views.
- A–Z rail widths and letter indices are cached outside draw-time and its spacing is derived from the live Apps viewport so A–Z always fits.
- Search and browse Apps layouts have distinct cached viewports.
- Extended the source architecture contract to `drawBrowseRows` and `drawAlphabetRail`.
- CI now rejects `LauncherApps` access from `LauncherSurface`.
- CI rejects `QUERY_ALL_PACKAGES` and requires the Private Space permission used by the profile implementation.
- Existing no-allocation/no-`measureText`/no-dp-sp draw-path contract remains intact.

### Automation and docs

- Updated Macrobenchmark search journeys to use swipe-left search-first Apps.
- Updated Baseline Profile generator journeys to use the 0.6 gesture contract.
- Updated the Infinix manual profile capture script to use swipe-left focused search.
- Added `docs/FRICTIONLESS_FEATURES.md` with the product, privacy, and performance model.
- Bumped the app to `0.6.0` / versionCode `6`.
- Updated weather User-Agent to `VS-Launcher/0.6`.

### Deliberately not added

To preserve a quiet, deterministic launcher, 0.6 does not add:

- widgets
- notification feed/filter
- folders
- usage-ranked/adaptive apps
- icon packs/wallpaper/theme systems
- a large configurable gesture vocabulary

The governing rule is: a capability should stay latent behind an existing gesture, tap, long-press, search action, conditional container, or transient overlay whenever possible.


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
