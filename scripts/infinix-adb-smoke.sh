#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.vslauncher"
ACTIVITY="com.vslauncher/.MainActivity"
ITERATIONS="${ITERATIONS:-10}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

timestamp="$(date +%Y%m%d-%H%M%S)"
RESULT_DIR="${RESULT_DIR:-device-test-results/${timestamp}}"
mkdir -p "$RESULT_DIR"

adb_cmd=(adb)
if [[ -n "${ADB_SERIAL:-}" ]]; then
  adb_cmd+=( -s "$ADB_SERIAL" )
else
  mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  if [[ ${#devices[@]} -ne 1 ]]; then
    echo "Expected exactly one authorized ADB device; found ${#devices[@]}."
    echo "Set ADB_SERIAL=<serial> when more than one device is connected."
    adb devices -l
    exit 2
  fi
  adb_cmd+=( -s "${devices[0]}" )
fi

adb_run() {
  "${adb_cmd[@]}" "$@"
}

record_prop() {
  local key="$1"
  printf '%s=%s\n' "$key" "$(adb_run shell getprop "$key" | tr -d '\r')" >> "$RESULT_DIR/device.txt"
}

echo "Building debug APK..."
bash scripts/build-debug-apk.sh | tee "$RESULT_DIR/build.txt"

APK="$(find dist -maxdepth 1 -type f -name 'VS-Launcher-*-debug.apk' -print | sort | tail -n 1)"
if [[ -z "$APK" ]]; then
  echo "No debug APK found under dist/." >&2
  exit 3
fi

{
  echo "adb_serial=$(adb_run get-serialno | tr -d '\r')"
  echo "apk=$APK"
  echo "git_commit=$(git rev-parse HEAD 2>/dev/null || true)"
  echo "git_branch=$(git branch --show-current 2>/dev/null || true)"
  echo "tested_at=$(date -Iseconds)"
} > "$RESULT_DIR/device.txt"

record_prop ro.product.manufacturer
record_prop ro.product.brand
record_prop ro.product.model
record_prop ro.build.version.release
record_prop ro.build.version.sdk
record_prop ro.build.version.security_patch
record_prop ro.build.fingerprint
record_prop ro.build.version.incremental

{
  echo
  echo "[display]"
  adb_run shell wm size || true
  adb_run shell wm density || true
  adb_run shell dumpsys display | grep -E 'refreshRate|mRefreshRate|modeId|fps' | head -n 30 || true
  echo
  echo "[home-role]"
  adb_run shell cmd role holders android.app.role.HOME 2>&1 || true
  echo
  echo "[package-before]"
  adb_run shell dumpsys package "$PACKAGE" 2>&1 | grep -E 'versionName=|versionCode=|firstInstallTime|lastUpdateTime' || true
} >> "$RESULT_DIR/device.txt"

echo "Installing $APK..."
set +e
adb_run install -r "$APK" >"$RESULT_DIR/install.txt" 2>&1
install_status=$?
set -e
cat "$RESULT_DIR/install.txt"

if [[ $install_status -ne 0 ]]; then
  {
    echo
    echo "INSTALL FAILED — classification:"
    if grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' "$RESULT_DIR/install.txt"; then
      echo "- Signature mismatch with installed com.vslauncher."
      echo "- Do NOT auto-uninstall: uninstalling clears launcher preferences."
      echo "- Export VS Launcher config first if you choose to uninstall."
    elif grep -q 'INSTALL_FAILED_VERSION_DOWNGRADE' "$RESULT_DIR/install.txt"; then
      echo "- APK versionCode is lower than installed version."
      echo "- Prefer building a newer versionCode; do not silently force downgrade."
    elif grep -q 'INSTALL_FAILED_USER_RESTRICTED' "$RESULT_DIR/install.txt"; then
      echo "- Device/vendor policy rejected USB/APK installation."
      echo "- Unlock phone and inspect the device's USB install/security prompt/settings."
    elif grep -q 'INSTALL_FAILED_INSUFFICIENT_STORAGE' "$RESULT_DIR/install.txt"; then
      echo "- Device storage is insufficient."
    elif grep -qi 'unauthorized' "$RESULT_DIR/install.txt"; then
      echo "- ADB authorization is missing; unlock phone and accept the RSA prompt."
    else
      echo "- Unclassified ADB install failure. Preserve install.txt and investigate before changing device state."
    fi
  } | tee -a "$RESULT_DIR/install.txt"
  echo "Result bundle: $RESULT_DIR"
  exit "$install_status"
fi

adb_run logcat -c || true

echo "Running $ITERATIONS cold starts..."
: > "$RESULT_DIR/startup.txt"
for i in $(seq 1 "$ITERATIONS"); do
  {
    echo "=== iteration $i ==="
    adb_run shell am force-stop "$PACKAGE"
    adb_run shell am start -W -n "$ACTIVITY"
  } >> "$RESULT_DIR/startup.txt" 2>&1
done

{
  echo "[package-after]"
  adb_run shell dumpsys package "$PACKAGE" 2>&1 | grep -E 'versionName=|versionCode=|firstInstallTime|lastUpdateTime' || true
  echo
  echo "[resumed-activity]"
  adb_run shell dumpsys activity activities 2>&1 | grep -E 'mResumedActivity|topResumedActivity' | head -n 10 || true
} >> "$RESULT_DIR/device.txt"

adb_run logcat -d -v threadtime > "$RESULT_DIR/logcat.txt" 2>&1 || true
grep -E 'FATAL EXCEPTION|AndroidRuntime|com\.vslauncher' "$RESULT_DIR/logcat.txt"   > "$RESULT_DIR/logcat-vslauncher.txt" || true

fatal_count="$(grep -c 'FATAL EXCEPTION' "$RESULT_DIR/logcat-vslauncher.txt" 2>/dev/null || true)"
startup_count="$(grep -c '^TotalTime:' "$RESULT_DIR/startup.txt" 2>/dev/null || true)"

{
  echo "# Local device test summary"
  echo
  echo "- Result directory: `$RESULT_DIR`"
  echo "- APK: `$APK`"
  echo "- Requested cold starts: $ITERATIONS"
  echo "- Recorded TotalTime samples: $startup_count"
  echo "- FATAL EXCEPTION lines: $fatal_count"
  echo
  echo "Review install.txt, startup.txt, device.txt and logcat-vslauncher.txt before declaring PASS."
} > "$RESULT_DIR/SUMMARY.md"

echo
echo "ADB smoke test finished."
echo "Result bundle: $RESULT_DIR"
echo "Read: $RESULT_DIR/SUMMARY.md"
