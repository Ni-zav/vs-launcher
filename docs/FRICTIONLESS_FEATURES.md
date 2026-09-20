# Frictionless feature model

VS Launcher can become more capable without becoming more visible.

The product rule is:

> A new feature does not earn permanent pixels merely because it exists. If it can live behind an existing gesture, semantic tap, long-press, search action, conditional profile container, or transient overlay, it should.

This keeps Home quiet while making the launcher faster for deliberate interaction.

## 0.7 interaction contract

### Home

Home keeps only glanceable state and explicitly chosen apps.

- Swipe left: enter Apps in search mode, focus the search field, and request the keyboard.
- Swipe right: Settings.
- Swipe up: configured quick app.
- Swipe down: intentionally unused.
- Tap the first empty `+ ADD APP` slot: open the picker directly.
- Long-press an assigned Home row: advanced slot actions.
- Home slots can hold either a normal app or a launcher-pinned native app shortcut while remaining one text row.
- Tap time: show alarms.
- Tap date: open today's calendar.
- Tap battery: battery saver settings, with general Settings as fallback.
- Tap weather: existing permission/refresh flow.

No extra Home buttons are required for these capabilities.

### Apps: search first, browse second

Entering Apps from Home is optimized for the common "I know what I want" path:

```text
Home
→ swipe left
→ SEARCH + keyboard
→ type
→ singleton result
→ auto-launch
```

A stable single **app** result auto-launches after a short debounce. Quiet non-app command rows do not block that singleton-app behavior and never auto-launch themselves. Keyboard Go/Enter immediately executes the first ranked row.

Browse mode can return to search without going Home:

- tap the `APPS` heading
- pull downward while already at the top of the app list

Back is progressive:

```text
SEARCH
→ Back
BROWSE
→ Back
HOME
```

If the user instead starts dragging the app list:

```text
SEARCH
→ first vertical browse gesture
→ query cleared
→ keyboard/search field removed
→ list expands
→ APPS + A–Z rail
```

This transition is deliberate: search and browse are two modes of one page, not two persistent controls competing for space.

### Alphabet fast scroll

The A–Z + # rail exists only in Apps browse mode.

- no rail on Home
- no rail while search is visible
- right-edge touch/drag jumps to the nearest available initial
- `#` catches numeric, symbolic, and non-A–Z initials
- first rows for A–Z + # are precomputed when the browse list changes
- letters with no target use the disabled luminance role
- the enlarged active letter exists only while scrubbing

The rail is navigation, not decoration.

## Search ranking

The entire app list is traversed once for each changed query. Expensive source data is precomputed outside the keystroke path.

Ranking order:

1. canonical prefix
2. alias prefix
3. canonical initials
4. alias initials
5. canonical substring
6. alias substring
7. bounded canonical subsequence
8. bounded alias subsequence

Initials support word and camel-case boundaries, for example:

```text
YouTube Music → ytm
Google Maps   → gm
```

The fuzzy fallback is not Levenshtein/edit distance. It accepts ordered characters only when their matched span remains bounded. That keeps runtime linear in app-label length and avoids fuzzy results outranking stronger deterministic matches.

## Search normalization and latent actions

Search normalization is shared by app labels, aliases, and typed queries:

- Unicode NFD decomposition
- combining-mark removal
- punctuation → word separator
- repeated whitespace collapse
- lowercase matching

This makes `Pokémon` match `pokemon` and `my-app` match `my app` without adding fuzzy edit-distance work.

After ranked app results, search may append latent action rows:

- `SYSTEM` for Android/launcher actions such as Wi-Fi, Internet, Volume, Bluetooth, Battery, Settings, Launcher settings, Alarms, Calendar, Storage, Keyboard, NFC, Display, Sound, Location, and Notifications
- `DIAL` for phone-like numeric input using permissionless `ACTION_DIAL`
- `OPEN` for domain/HTTP(S) input using `ACTION_VIEW`

Rules:

- apps stay before non-app rows
- commands require at least two normalized characters
- command-only results never auto-fire
- no contacts, history, browser engine, AI parser, or call permission are introduced
- Settings Panels are preferred for Wi-Fi/Internet/Volume/NFC on API 29+ when available

## Calm monochrome hierarchy

Minimal does not mean every foreground element is pure white.

The background stays absolute black. Foreground roles are fixed neutral grays:

```text
focus / press       #F0F0F0
primary             #DCDCDC
app/body            #C2C2C2
secondary/meta      #909090
quiet/navigation    #646464
disabled            #464646
divider             #2C2C2C
```

This scale is semantic, not customizable. It exists to stop metadata, section labels, app rows, fast-scroll navigation, and active focus from competing at the same visual volume.

Pressed rows keep the black surface and brighten text rather than inverting the entire row. The focused search EditText is borderless. Low battery may temporarily promote its luminance but never changes hue.

Settings rows are intentionally dividerless: section labels plus whitespace provide structure without making every row look equally important. WORK/PRIVATE headers get a small leading gap inside their existing row so profile namespaces separate from personal apps without adding a new permanent surface.

Search emphasis is contextual. With an empty query, visible app rows stay at the same luminance. Once normalized input exists, only the deterministic first-ranked result may rise to primary luminance to communicate the Go/Enter target.

## Native Android app shortcuts

VS Launcher uses Android `LauncherApps` shortcut APIs only when an app is long-pressed.

Possible long-press menu:

```text
TELEGRAM

New message
Saved Messages
Pin shortcut…
Add to Home
Hide
App info
Uninstall
```

`Pin shortcut…` lets one of the native actions occupy a normal Home text slot. VS uses `LauncherApps.pinShortcuts()` and re-submits the full package shortcut-ID set because Android's launcher pin API is non-cumulative.

Rules:

- no shortcut discovery during launcher idle
- no shortcut discovery while merely browsing
- dynamic/manifest shortcuts are queried on demand
- up to four shortcut actions are exposed before launcher-level actions
- stale shortcuts fail safely

Android references:

- LauncherApps: https://developer.android.com/reference/android/content/pm/LauncherApps
- ShortcutQuery: https://developer.android.com/reference/android/content/pm/LauncherApps.ShortcutQuery

## Transient Undo

Reversible launcher actions should not require confirmation dialogs.

VS keeps one in-memory Undo opportunity for 2.5 seconds after:

- hiding an app from long-press
- clearing a Home slot
- removing a pinned Home shortcut

The Canvas draws one quiet transient message with a brighter `UNDO` affordance. There is no Snackbar dependency, history list, queue, or persistent notification.

## Profile-aware launcher model

App discovery uses `LauncherApps`, which is Android's launcher-facing interface for launchable activities and accessible user profiles.

Personal-profile preference keys remain unchanged from 0.5. Non-personal apps add the Android user serial to the internal component key.

### Work profile

A work container exists only if Android exposes a work/managed profile.

Example:

```text
APPS

Chrome
Discord
...

WORK                           PAUSE
Gmail
Meet
Slack
```

When quiet/paused:

```text
WORK                          PAUSED
```

Work apps are absent until Android makes the profile available again.

### Android 15 Private Space

Private Space is conditional and isolated.

Unlocked:

```text
PRIVATE                         LOCK
Bitwarden
Gallery
...
```

Locked:

```text
PRIVATE                       LOCKED
```

Privacy rules:

- locked Private Space apps are not enumerated into the normal app/search lists
- Private Space can be hidden from the existing Hidden apps manager
- if no Private Space exists, no related setting is shown
- Private Space apps cannot be assigned to persistent Home slots
- Private Space apps cannot be assigned as swipe-up quick launch
- profile lock/unlock uses Android's quiet-mode mechanism rather than custom authentication

Android references:

- Android 15 Private Space launcher requirements:
  https://developer.android.com/about/versions/15/behavior-changes-all#private-space
- UserManager quiet mode:
  https://developer.android.com/reference/android/os/UserManager#requestQuietModeEnabled(boolean,%20android.os.UserHandle)
- LauncherUserInfo:
  https://developer.android.com/reference/android/content/pm/LauncherUserInfo

## Semantic status actions

Status text remains information first. Its meaning determines its tap action:

- time → alarms
- date → calendar at the current day/time
- battery → battery saver settings
- weather → refresh/permission behavior

References:

- AlarmClock: https://developer.android.com/reference/android/provider/AlarmClock
- Calendar intents/provider: https://developer.android.com/guide/topics/providers/calendar-provider
- Settings: https://developer.android.com/reference/android/provider/Settings

## Features deliberately not added

The following may be useful in other minimalist launchers, but they conflict with VS Launcher's current interaction model:

### Widgets

Rejected because they convert quiet Home into a persistent dashboard and introduce host/widget lifecycle complexity.

### Notification feed/filter

Rejected because it adds notification access, persistent unread state, and another attention surface to Home/launcher navigation.

### Folders

Rejected because they add persistent hierarchy and an extra open/find/launch step. Search and direct Home assignments remain faster.

### Usage-ranked/adaptive apps

Rejected because the app order becomes less predictable and requires behavior tracking. VS Launcher should respond to explicit user intent.

### Icon packs, wallpaper, theme systems

Rejected because the fixed monochrome hierarchy is an intentional product constraint. The neutral luminance scale is semantic UI infrastructure, not a user theme system.

### Large configurable gesture vocabulary

Rejected because memorization/configuration becomes its own source of friction. VS keeps a small stable gesture grammar.

## Performance invariants

0.7 features must preserve:

1. no continuous idle render loop
2. no PackageManager/LauncherApps/SharedPreferences calls from `LauncherSurface`
3. no allocation, text measurement, resource lookup, or dp/sp conversion in draw hot paths
4. search remains one traversal of the visible searchable apps per query change
5. app initials/aliases/A–Z + # first indices are cached outside frame-critical paths
6. shortcut discovery happens only after deliberate long-press; pinned Home shortcut launch uses the stored ID without startup queries
7. work/private containers are plain Canvas rows, not nested view hierarchies
8. profile apps that Android marks unavailable never leak into search
9. normal production UI remains native Java + Canvas, without Compose/RecyclerView
10. all foreground/background design tokens remain neutral grayscale; the background remains absolute black
11. command/dial/URL rows never participate in automatic launch
12. only one transient Undo action is retained in memory
13. Settings/command execution stays outside `LauncherSurface`

CI enforces the renderer/design portions of this contract and JVM tests cover search ranking, geometry, and profile privacy policy.
