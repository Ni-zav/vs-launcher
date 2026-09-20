#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MAX_DEBUG_APK_BYTES="${MAX_DEBUG_APK_BYTES:-1048576}"

mapfile -t apks < <(find dist -maxdepth 1 -type f -name 'VS-Launcher-*-debug.apk' -print | sort)
if [[ ${#apks[@]} -eq 0 ]]; then
  echo "No debug APK found under dist/. Build first." >&2
  exit 2
fi

apk="${apks[${#apks[@]}-1]}"
if stat --version >/dev/null 2>&1; then
  bytes="$(stat -c '%s' "$apk")"
else
  bytes="$(stat -f '%z' "$apk")"
fi

printf 'APK: %s\n' "$apk"
printf 'Size: %s bytes\n' "$bytes"
printf 'Budget: %s bytes\n' "$MAX_DEBUG_APK_BYTES"

if (( bytes > MAX_DEBUG_APK_BYTES )); then
  echo "Debug APK exceeds the VS Launcher size budget." >&2
  exit 1
fi

echo "APK size budget passed."
