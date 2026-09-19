#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -x "./gradlew" ]]; then
  GRADLE_CMD="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="gradle"
else
  cat >&2 <<'EOF'
Gradle was not found.

Use one of these options:
  1. Build from GitHub Actions (no local Android build tools required), or
  2. Install JDK 17, Android SDK 35, and Gradle 8.7.

See docs/BUILD_APK.md.
EOF
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java was not found. JDK 17 is required. See docs/BUILD_APK.md." >&2
  exit 1
fi

echo "Building VS Launcher debug APK..."
"$GRADLE_CMD" --no-daemon :app:assembleDebug :app:lintDebug

SOURCE_APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$SOURCE_APK" ]]; then
  echo "Build finished but APK was not found at $SOURCE_APK" >&2
  exit 1
fi

VERSION_NAME="$(
  sed -n "s/^[[:space:]]*versionName[[:space:]]*'\([^']*\)'.*/\1/p" app/build.gradle     | head -n 1
)"
if [[ -z "$VERSION_NAME" ]]; then
  VERSION_NAME="dev"
fi

mkdir -p dist
OUTPUT_APK="dist/VS-Launcher-${VERSION_NAME}-debug.apk"
cp "$SOURCE_APK" "$OUTPUT_APK"

echo
echo "APK ready:"
echo "  $OUTPUT_APK"
echo
echo "This debug APK is already signed by the Android build tools and can be sideloaded."
