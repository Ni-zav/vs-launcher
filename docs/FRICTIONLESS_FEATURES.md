# Frictionless feature model

VS Launcher can become more capable without becoming more visible.

The product rule is:

> A new feature does not earn permanent pixels merely because it exists. If it can live behind an existing gesture, semantic tap, long-press, search action, conditional profile container, or transient overlay, it should.

This keeps Home quiet while making the launcher faster for deliberate interaction.

## 0.6 interaction contract

### Home

Home keeps only glanceable state and explicitly chosen apps.

- Swipe left: enter Apps in search mode, focus the search field, and request the keyboard.
- Swipe right: Settings.
- Swipe up: configured quick app.
- Swipe down: intentionally unused.
- Tap the first empty `+ ADD APP` slot: open the picker directly.
- Long-press an assigned Home row: advanced slot actions.
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

A stable single result auto-launches after a short debounce. Keyboard Go/Enter immediately launches the first ranked result.

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

The A–Z rail exists only in Apps browse mode.

- no rail on Home
- no rail while search is visible
- right-edge touch/drag jumps to the nearest available initial
- first rows for A–Z are precomputed when the browse list changes
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

## Native Android app shortcuts

VS Launcher uses Android `LauncherApps` shortcut APIs only when an app is long-pressed.

Possible long-press menu:

```text
TELEGRAM

New message
Saved Messages
────────────
Add to Home
Hide
App info
Uninstall
```

Rules:

- no shortcut discovery during launcher idle
- no shortcut discovery while merely browsing
- dynamic/manifest shortcuts are queried on demand
- up to four shortcut actions are exposed before launcher-level actions
- stale shortcuts fail safely

Android references:

- LauncherApps: https://developer.android.com/reference/android/content/pm/LauncherApps
- ShortcutQuery: https://developer.android.com/reference/android/content/pm/LauncherApps.ShortcutQuery

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

Rejected because the strict binary visual system is an intentional product constraint.

### Large configurable gesture vocabulary

Rejected because memorization/configuration becomes its own source of friction. VS keeps a small stable gesture grammar.

## Performance invariants

0.6 features must preserve:

1. no continuous idle render loop
2. no PackageManager/LauncherApps/SharedPreferences calls from `LauncherSurface`
3. no allocation, text measurement, resource lookup, or dp/sp conversion in draw hot paths
4. search remains one traversal of the visible searchable apps per query change
5. app initials/aliases/A–Z first indices are cached outside frame-critical paths
6. shortcut queries happen only after deliberate long-press
7. work/private containers are plain Canvas rows, not nested view hierarchies
8. profile apps that Android marks unavailable never leak into search
9. normal production UI remains native Java + Canvas, without Compose/RecyclerView
10. strict black/white palette remains unchanged

CI enforces the renderer/design portions of this contract and JVM tests cover search ranking, geometry, and profile privacy policy.
