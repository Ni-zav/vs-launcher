# Build and install the VS Launcher APK

VS Launcher does **not** require a runtime, framework, package manager, Java, Gradle, Android Studio, or any third-party library on the Android device.

The phone only needs:

- Android 8.0 / API 26 or newer
- permission to install an APK from the app you use to open it
- internet + coarse location permission only if you want live weather

Everything needed to run the launcher is packaged inside the APK. The project uses Android platform APIs and Android resource formats only.

---

## Fastest method: build the APK on GitHub

This is the recommended method if you do not want to install Android build tools locally.

### 1. Run the build

On GitHub:

1. Open the repository.
2. Open **Actions**.
3. Open **Android CI**.
4. Click **Run workflow**.
5. Select the branch you want to build. For the normal installable build, use `main`.
6. Click **Run workflow**.

The workflow installs its own JDK/Android SDK/Gradle environment on GitHub's runner and runs:

```sh
bash scripts/build-debug-apk.sh
```

That script also runs Android lint.

### 2. Download the APK

After the workflow is green:

1. Open the completed workflow run.
2. Scroll to **Artifacts**.
3. Download **vs-launcher-debug-apk**.
4. GitHub downloads the artifact as a ZIP.
5. Extract the ZIP.

Inside it is:

```text
VS-Launcher-0.9.0-debug.apk
```

The artifact is retained for 14 days.

### 3. Put it on the phone

Any normal file-transfer method is fine:

- USB cable
- Nearby Share / Quick Share
- Syncthing
- cloud storage
- browser download
- `adb push`

No build tools are needed on the phone.

### 4. Install it

On the phone:

1. Open the APK from Files, browser, Drive, etc.
2. Android may ask you to allow **Install unknown apps** for that specific source app.
3. Allow it.
4. Install **VS Launcher**.
5. Press the system **Home** button.

Android should offer VS Launcher as a Home app. You can also select it from:

```text
Settings
→ Apps
→ Default apps
→ Home app
→ VS Launcher
```

The exact Settings wording varies by Android vendor.

### CI debug-key warning

A debug APK is automatically signed and is installable, but GitHub-hosted runners are disposable. A later workflow run can therefore use a different generated debug signing key.

If Android reports something like:

```text
App not installed
INSTALL_FAILED_UPDATE_INCOMPATIBLE
```

when installing a newer CI debug build, either:

- uninstall the previous debug build first, then install the new one, or
- use the persistent release-signing method below for stable in-place updates.

Uninstalling VS Launcher removes its app preferences/cache.

---

# Local debug APK

Use this if the development computer already has Android build tools.

## Build-machine requirements

These are required on the **computer**, not the Android phone:

- JDK 17
- Android SDK platform 35
- Android Build Tools 35.0.0
- Gradle 8.7

The repo intentionally does not depend on AndroidX UI libraries, Compose, Flutter, React Native, Kotlin runtime, or another application framework.

## Ubuntu / Linux / macOS

From the repository root:

```sh
bash scripts/build-debug-apk.sh
```

The script:

1. finds `./gradlew` if a wrapper is added later, otherwise system `gradle`
2. checks that Java exists
3. builds `:app:assembleDebug`
4. runs `:app:lintDebug`
5. copies the final APK into `dist/`

Output:

```text
dist/VS-Launcher-0.9.0-debug.apk
```

The raw Android Gradle output also remains at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install from a computer with ADB

Enable Developer Options + USB debugging on the phone, connect it, then:

```sh
adb install -r dist/VS-Launcher-0.9.0-debug.apk
```

If the installed copy was signed with a different key, uninstall it first:

```sh
adb uninstall com.vslauncher
adb install dist/VS-Launcher-0.9.0-debug.apk
```

Then press Home and select VS Launcher.

---

# Android Studio method

If Android Studio is already installed:

1. Open the repository folder.
2. Let Android Studio sync the Gradle project.
3. Make sure JDK 17 and Android SDK 35 are selected.
4. Use **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

You can then copy that APK to the device and install it normally.

---

# Stable release APK for repeated updates

Use this method if you want to keep installing newer VS Launcher APKs **over the existing installation without uninstalling it**.

The signing key becomes the identity of the application. Keep it backed up securely and do not commit it to Git.

The repo's `.gitignore` excludes `*.jks`, `*.keystore`, and `keystore.properties`.

## 1. Create the signing key once

Run on the build computer:

```sh
keytool -genkeypair \
  -v \
  -keystore vs-launcher-release.jks \
  -alias vslauncher \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Store `vs-launcher-release.jks` and its password somewhere safe.

**Do not regenerate this key for every version.**

## 2. Build the unsigned release APK

```sh
gradle --no-daemon :app:assembleRelease
```

Expected output:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

## 3. Align the APK

`zipalign` must happen before APK signing.

```sh
mkdir -p dist

"$ANDROID_HOME/build-tools/35.0.0/zipalign" \
  -f -p 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  dist/VS-Launcher-0.9.0-aligned.apk
```

If your SDK uses `ANDROID_SDK_ROOT` instead:

```sh
"$ANDROID_SDK_ROOT/build-tools/35.0.0/zipalign" ...
```

## 4. Sign it

```sh
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign \
  --ks vs-launcher-release.jks \
  --ks-key-alias vslauncher \
  --out dist/VS-Launcher-0.9.0.apk \
  dist/VS-Launcher-0.9.0-aligned.apk
```

Leave the password options off the command line so `apksigner` can prompt you instead of placing passwords in shell history.

## 5. Verify the APK

```sh
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify \
  --verbose \
  --print-certs \
  dist/VS-Launcher-0.9.0.apk
```

The installable release APK is now:

```text
dist/VS-Launcher-0.9.0.apk
```

Install it by opening it on the phone or with:

```sh
adb install -r dist/VS-Launcher-0.9.0.apk
```

Every future release that should update this installation must be signed with the same `vs-launcher-release.jks`.

---

# App icon: SVG source with no SVG runtime

The editable icon source is:

```text
art/launcher-icon.svg
```

The SVG is **source artwork only**. Android does not need an SVG library at runtime.

The APK packages the same artwork as Android-native resources:

```text
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml
app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml
```

The API-26 resources are adaptive icons:

- black native background layer
- white vector foreground layer

The API-33 resources add the monochrome layer used by Android themed icons.

The manifest points to them with:

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

## Editing the icon later

For the current minimal icon, keep `art/launcher-icon.svg` as the canonical artwork.

If you modify its vector paths, update the corresponding Android vector paths in:

```text
app/src/main/res/drawable/ic_launcher_foreground.xml
```

Alternatively, Android Studio can import an SVG:

```text
res/drawable
→ New
→ Vector Asset
→ Local file (SVG)
```

Then use the imported VectorDrawable as the adaptive-icon foreground.

Do not add an SVG rendering library just to display the launcher icon.

---

# Why the phone needs no framework

The final APK contains:

- compiled Java/Dex bytecode
- Android manifest
- native Android XML/vector resources
- the adaptive app icon
- all launcher code
- all design tokens

At runtime it uses APIs already provided by Android itself.

There is no requirement to install:

- Java on the phone
- Gradle
- Android Studio
- Node.js/npm
- Python
- Flutter
- React Native
- Compose runtime
- a separate SVG viewer
- an icon pack
- a weather SDK

Live weather calls Open-Meteo directly over HTTPS using Android's built-in networking APIs.

---

# Minimum compatibility

Current Gradle configuration:

```text
minSdk 26
targetSdk 35
compileSdk 35
```

Therefore the launcher is intended for Android 8.0 (API 26) or newer.

For APK installation on newer certified Android devices, Android's developer-verification and sideloading rules may vary by Android version, device policy, region, and distribution method. If the OS blocks installation despite allowing unknown-app installs, check the device's current Android installation/developer-verification requirements.

---

# Quick verification after installation

After installing:

1. Press Home and make VS Launcher the Home app.
2. Confirm the true-black launcher fills the top edge with no persistent status/notification bar.
3. Confirm the custom time/date, weather status, and battery indicator render correctly.
4. Long-press a Home app row, select a different app, and confirm the slot persists.
5. Swipe right to Settings, change **Visible apps**, and choose a **Swipe up** app.
6. Return Home and swipe up; the selected quick app should open directly.
7. Swipe left to Apps and confirm search is immediately focused with a borderless field and the keyboard requested.
8. Type until one app remains and confirm the stable singleton app launches even if a quiet SYSTEM row is also present; verify command-only rows never auto-launch.
9. Re-enter Apps, start vertically scrolling, and confirm search/query/keyboard disappear, the list expands, and the #–Z rail appears.
10. Tap APPS and confirm search returns; return to browse and pull down at the top to confirm the same search re-entry.
11. Verify Back performs Search → Browse → Home.
12. Scrub #–Z and confirm available/unavailable letters use different luminance while the active letter is transient.
13. Search `wifi`, `volume`, and `settings`; verify quiet SYSTEM rows execute only after tap/Go.
14. Search a safe phone number and domain; verify DIAL opens the dialer without placing a call and OPEN hands the URL to Android.
15. Long-press an app with native shortcuts, pin one into Home, launch it from the text row, export/import config, and confirm the shortcut remains usable.
16. Hide an app from long-press and clear/remove a Home row; verify the single transient UNDO action restores each case.
17. Verify the calm monochrome hierarchy: no white row flash, secondary/meta text recedes, Settings values/sections have distinct hierarchy, and low battery is emphasized only by luminance.
18. Tap `+ ADD APP`, rename/reorder Home rows, and confirm ordinary app-slot persistence.
19. Tap time/date/battery/weather and verify semantic actions.
20. If work/private profiles exist, verify their conditional containers, quiet/lock behavior, and Private Space search/Home privacy.
21. Change position, density, text size, status modules/formats, animation speed, and haptics in Settings.
22. Reboot once and confirm Home app/shortcut slots, aliases, hidden apps, visual preferences, Private Space visibility preference, and quick-launch app persist.

For repeatable physical-device performance validation, see `docs/BENCHMARK.md`. The benchmark tooling is isolated from the normal launcher APK.
