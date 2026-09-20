# Codex goal — install and validate VS Launcher 0.7 on Infinix X6855

Use this after pulling the merged 0.7 branch/main.

---

/goal

Build, install in place, and smoke-test VS Launcher 0.7 on my connected Infinix X6855 / Android 16 without uninstalling `com.vslauncher`.

## Safety

- Never uninstall automatically.
- Preserve existing Home slots, aliases, hidden apps, quick launch, profile visibility, and pinned shortcut configuration.
- Use `adb install -r`.
- If signing mismatch requires uninstall, stop and report instead.
- Do not retry the known XOS Macrobenchmark-controller freezer investigation during this smoke test.

## Sync and validate

1. Pull latest `main`.
2. Confirm a clean/understood working tree.
3. Record exact tested Git SHA.
4. Run:

~~~sh
python3 scripts/check-ui-contract.py
gradle --no-daemon :app:testDebugUnitTest
bash scripts/build-debug-apk.sh
bash scripts/check-apk-size.sh
~~~

5. Confirm the expected app version is `0.7.0` / versionCode `7`.

## Install

Locate the real generated APK and install:

~~~sh
adb install -r dist/VS-Launcher-0.7.0-debug.apk
~~~

Record APK SHA-256 and install output.

## Core startup

- force-stop and explicitly start `com.vslauncher/.MainActivity`
- confirm no FATAL EXCEPTION
- confirm current HOME role
- verify existing launcher preferences survived the update

## 0.7 visual hierarchy

Verify on the real display:

- background remains absolute black
- text is no longer pure white everywhere
- clock/primary focus is brightest
- normal app rows are calmer
- metadata/settings values recede
- section labels and A–Z navigation are quieter still
- pressed rows brighten text without a full-white background flash
- search input is borderless
- native dialogs use the same calmer monochrome hierarchy
- if battery <=20% and not charging, battery becomes more prominent only through luminance

## Search / browse navigation

1. Home → swipe left.
   - focused search opens
   - keyboard requested
2. Start scrolling.
   - search/query/keyboard disappear
   - Apps viewport expands
   - A–Z + # rail appears
3. Tap `APPS`.
   - focused search returns
4. Return to browse, scroll to top, pull downward.
   - focused search returns
5. Back behavior:
   - Search → Back → Browse
   - Browse → Back → Home

## Search behavior

Verify representative queries:

- normal prefix/substring
- alias
- initials such as `gm` or `ytm` when matching apps exist
- accent normalization where practical
- punctuation normalization where practical
- bounded fuzzy query

Verify:

- one remaining app auto-launches after the short stability debounce
- a SYSTEM/DIAL/OPEN row does not block lone-app auto-launch
- command-only results never auto-launch
- keyboard Go/Enter executes the top row

## Command layer

Test available rows:

- `wifi`
- `internet`
- `volume`
- `bluetooth`
- `battery`
- `settings`
- `home settings`
- `alarm`
- `calendar`
- `storage`
- `keyboard`
- `nfc` if device supports it
- `display`
- `sound`
- `location`
- `notifications`

Confirm actions execute only after tap/Go and unsupported vendor screens fail gracefully.

## Dial / URL

- type a harmless test number such as `5551234`
- confirm a `DIAL` row appears
- execute it and confirm the dialer opens without placing a call

- type `example.com`
- confirm an `OPEN` row appears
- execute it and confirm Android resolves the URL

## A–Z + #

In browse mode:

- available letters are visible but quiet
- unavailable letters are dimmer
- active scrub letter becomes prominent only while touching
- scrub A/Z
- verify `#` handles a numeric/symbol/non-A–Z app if one exists
- no permanent overlay remains after release

## Home pinned shortcuts

Use an installed app exposing native shortcuts.

1. Long-press app.
2. Confirm direct native shortcut actions still launch.
3. Select `Pin shortcut…`.
4. Choose a shortcut and Home slot.
5. Confirm the Home slot remains one text row.
6. Tap it and verify the shortcut launches.
7. Move the slot and verify it remains a shortcut.
8. Replace it with a normal app and verify stale shortcut state is removed.
9. Pin again, export config, import config, and verify the shortcut remains usable.

## Undo

Verify one operation at a time:

- long-press app → Hide → transient message + UNDO → Undo restores app
- Home normal slot → Clear → transient message + UNDO → Undo restores slot
- pinned shortcut → Remove shortcut → transient message + UNDO → Undo restores and launches correctly

Confirm Undo disappears automatically after roughly 2.5 seconds and no queue/history persists.

## Existing 0.6 features

Regression-check:

- + ADD APP direct tap
- Home rename/move
- time/date/battery/weather semantic taps
- swipe-up quick launch
- Settings scrolling/actions
- hidden apps
- work profile container if present
- Private Space container/lock/privacy if present
- locked Private Space apps never appear in normal search or persistent Home/quick launch

## Evidence

Create a gitignored result directory under:

~~~text
device-test-results/v07-install-YYYYMMDD-HHMMSS/
~~~

Record:

- Git SHA
- APK path + SHA-256
- device metadata
- install output
- installed version
- startup `am start -W`
- crash/logcat check
- HOME role
- preference-preservation result
- concise checklist results above

If a real bug is found, fix it on a small branch/commit, rerun only the affected checks first, then the normal CI-equivalent checks.

At completion report:

1. PASS / FAIL / PARTIAL
2. installed Git SHA
3. version
4. data preserved yes/no
5. visual hierarchy result
6. search/browse/back result
7. command layer result
8. dial/URL result
9. A–Z + # result
10. pinned shortcut result
11. Undo result
12. existing-feature regression result
13. crashes/errors
14. APK SHA-256
15. evidence path
16. any fix commits
