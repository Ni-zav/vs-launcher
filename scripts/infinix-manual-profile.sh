#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.vslauncher"
ACTIVITY="com.vslauncher/.MainActivity"
RECEIVER="$PACKAGE/androidx.profileinstaller.ProfileInstallReceiver"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

CAPTURE_ITERATIONS="${CAPTURE_ITERATIONS:-3}"
GESTURE_SLEEP="${GESTURE_SLEEP:-0.7}"

adb_cmd=(adb)
if [[ -n "${ADB_SERIAL:-}" ]]; then
  adb_cmd+=( -s "$ADB_SERIAL" )
else
  mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  if [[ ${#devices[@]} -ne 1 ]]; then
    echo "Expected exactly one authorized ADB device; found ${#devices[@]}." >&2
    echo "Set ADB_SERIAL=<serial> if multiple devices are attached." >&2
    adb devices -l
    exit 2
  fi
  adb_cmd+=( -s "${devices[0]}" )
fi

adb_run() {
  "${adb_cmd[@]}" "$@"
}

timestamp() {
  date +%Y%m%d-%H%M%S
}

require_vs_not_default_home() {
  local holders
  holders="$(adb_run shell cmd role get-role-holders android.app.role.HOME 2>/dev/null | tr -d '\r')"
  if printf '%s\n' "$holders" | grep -qx "$PACKAGE"; then
    cat >&2 <<EOF
VS Launcher is currently the HOME role holder.

For manual profile capture/startup measurement, temporarily select the stock
launcher as the default Home app first. Otherwise Android may relaunch
com.vslauncher immediately after force-stop and invalidate cold-start state.

Current HOME holder(s):
$holders
EOF
    return 40
  fi
}

require_api34() {
  local api
  api="$(adb_run shell getprop ro.build.version.sdk | tr -d '\r')"
  if [[ -z "$api" || "$api" -lt 34 ]]; then
    echo "This workflow requires API 34+; device reports API '$api'." >&2
    exit 3
  fi
}

record_device() {
  local out="$1"
  {
    echo "tested_at=$(date -Iseconds)"
    echo "git_commit=$(git rev-parse HEAD 2>/dev/null || true)"
    echo "git_branch=$(git branch --show-current 2>/dev/null || true)"
    echo "manufacturer=$(adb_run shell getprop ro.product.manufacturer | tr -d '\r')"
    echo "model=$(adb_run shell getprop ro.product.model | tr -d '\r')"
    echo "android=$(adb_run shell getprop ro.build.version.release | tr -d '\r')"
    echo "api=$(adb_run shell getprop ro.build.version.sdk | tr -d '\r')"
    echo "security_patch=$(adb_run shell getprop ro.build.version.security_patch | tr -d '\r')"
    echo "fingerprint=$(adb_run shell getprop ro.build.fingerprint | tr -d '\r')"
    echo "home_role=$(adb_run shell cmd role get-role-holders android.app.role.HOME 2>/dev/null | tr -d '\r' | paste -sd ',' -)"
  } > "$out"
}

broadcast_expect() {
  local expected="$1"
  local log_file="$2"
  shift 2

  local output result
  set +e
  output="$(adb_run shell am broadcast "$@" 2>&1 | tr -d '\r')"
  local status=$?
  set -e
  printf '%s\n' "$output" > "$log_file"

  if [[ $status -ne 0 ]]; then
    cat "$log_file" >&2
    echo "Broadcast command failed with status $status." >&2
    return "$status"
  fi

  result="$(printf '%s\n' "$output" | sed -n 's/.*Broadcast completed: result=\(-\{0,1\}[0-9][0-9]*\).*/\1/p' | tail -n 1)"
  if [[ "$result" != "$expected" ]]; then
    cat "$log_file" >&2
    echo "Expected ProfileInstaller broadcast result=$expected, got '${result:-missing}'." >&2
    return 20
  fi
}

write_skip_file() {
  local log_file="$1"
  broadcast_expect 10 "$log_file"     -a androidx.profileinstaller.action.SKIP_FILE     -e EXTRA_SKIP_FILE_OPERATION WRITE_SKIP_FILE     "$RECEIVER"
}

delete_skip_file() {
  local log_file="$1"
  broadcast_expect 11 "$log_file"     -a androidx.profileinstaller.action.SKIP_FILE     -e EXTRA_SKIP_FILE_OPERATION DELETE_SKIP_FILE     "$RECEIVER"
}

save_profile() {
  local log_file="$1"
  broadcast_expect 12 "$log_file"     -a androidx.profileinstaller.action.SAVE_PROFILE     "$RECEIVER"
}

find_capture_apk() {
  find app/build/outputs/apk -type f     -path '*/baselineProfile/*' -name '*.apk' -print 2>/dev/null     | sort | tail -n 1
}

build_capture() {
  local result_dir="$1"
  echo "Building non-R8, non-debuggable baselineProfile capture APK..."
  gradle --no-daemon :app:assembleBaselineProfile     | tee "$result_dir/build-baseline-profile.txt"

  local apk
  apk="$(find_capture_apk)"
  if [[ -z "$apk" ]]; then
    echo "Could not find assembled baselineProfile APK." >&2
    exit 4
  fi
  printf '%s\n' "$apk" > "$result_dir/capture-apk-path.txt"
  sha256sum "$apk" > "$result_dir/capture-apk-sha256.txt"
}

prepare_capture() {
  local result_dir="$1"
  require_api34
  require_vs_not_default_home
  mkdir -p "$result_dir"
  record_device "$result_dir/device.txt"
  build_capture "$result_dir"

  local apk
  apk="$(cat "$result_dir/capture-apk-path.txt")"

  echo "Installing capture APK in place (no uninstall)..."
  adb_run install -r "$apk" > "$result_dir/install.txt" 2>&1 || {
    cat "$result_dir/install.txt" >&2
    echo "Capture APK install failed. Existing launcher data was not deliberately uninstalled." >&2
    exit 5
  }

  adb_run shell am force-stop "$PACKAGE"

  # Prevent ProfileInstaller's packaged profile from polluting manual collection.
  write_skip_file "$result_dir/skip-write.txt"

  # Android's API 34+ manual collection reset.
  adb_run shell cmd package compile -f -m verify "$PACKAGE"     > "$result_dir/compile-verify.txt" 2>&1
  adb_run shell pm art clear-app-profiles "$PACKAGE"     > "$result_dir/clear-app-profiles.txt" 2>&1

  adb_run shell dumpsys package dexopt     > "$result_dir/dexopt-before-capture.txt" 2>&1 || true

  cat <<EOF
Manual profile capture prepared.

Result directory:
  $result_dir

Next:
  bash scripts/infinix-manual-profile.sh journey "$result_dir"

Or run the complete workflow in one command:
  bash scripts/infinix-manual-profile.sh capture
EOF
}

display_size() {
  local raw size
  raw="$(adb_run shell wm size | tr -d '\r')"
  size="$(printf '%s\n' "$raw" | grep -Eo '[0-9]+x[0-9]+' | tail -n 1)"
  if [[ ! "$size" =~ ^([0-9]+)x([0-9]+)$ ]]; then
    echo "Unable to parse display size from: $raw" >&2
    return 1
  fi
  printf '%s %s\n' "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}"
}

run_one_journey() {
  local width="$1"
  local height="$2"

  local x20=$((width * 20 / 100))
  local x50=$((width * 50 / 100))
  local x80=$((width * 80 / 100))
  local y20=$((height * 20 / 100))
  local y25=$((height * 25 / 100))
  local y50=$((height * 50 / 100))
  local y75=$((height * 75 / 100))
  local y80=$((height * 80 / 100))

  adb_run shell am force-stop "$PACKAGE"
  adb_run shell am start-activity -W -n "$ACTIVITY" >/dev/null
  sleep "$GESTURE_SLEEP"

  # Home -> All Apps.
  adb_run shell input swipe "$x80" "$y50" "$x20" "$y50" 250
  sleep "$GESTURE_SLEEP"

  # Exercise visible-row / fling path.
  adb_run shell input swipe "$x50" "$y80" "$x50" "$y20" 200
  sleep "$GESTURE_SLEEP"

  # All Apps -> Home.
  adb_run shell input swipe "$x20" "$y50" "$x80" "$y50" 250
  sleep "$GESTURE_SLEEP"

  # Home -> Settings.
  adb_run shell input swipe "$x20" "$y50" "$x80" "$y50" 250
  sleep "$GESTURE_SLEEP"

  # Settings -> Home.
  adb_run shell input swipe "$x80" "$y50" "$x20" "$y50" 250
  sleep "$GESTURE_SLEEP"

  # Home -> focused search.
  adb_run shell input swipe "$x50" "$y25" "$x50" "$y75" 250
  sleep "$GESTURE_SLEEP"
  adb_run shell input text cal
  sleep "$GESTURE_SLEEP"
}

run_journeys() {
  local result_dir="$1"
  local iterations="${2:-$CAPTURE_ITERATIONS}"
  if [[ ! -d "$result_dir" ]]; then
    echo "Result directory does not exist: $result_dir" >&2
    exit 6
  fi

  local width height
  read -r width height < <(display_size)
  printf 'display_width=%s\ndisplay_height=%s\niterations=%s\n'     "$width" "$height" "$iterations" > "$result_dir/journey-config.txt"

  : > "$result_dir/journey.txt"
  for i in $(seq 1 "$iterations"); do
    echo "Running manual ADB CUJ $i/$iterations..."
    {
      echo "=== journey $i ==="
      echo "started_at=$(date -Iseconds)"
    } >> "$result_dir/journey.txt"

    run_one_journey "$width" "$height"

    echo "finished_at=$(date -Iseconds)" >> "$result_dir/journey.txt"
  done

  echo "CUJs complete. Leave the final app process running and wait for profile stabilization."
}

finish_capture() {
  local result_dir="$1"
  if [[ ! -d "$result_dir" ]]; then
    echo "Result directory does not exist: $result_dir" >&2
    exit 6
  fi

  echo "Waiting 5 seconds for ART profile data to stabilize..."
  sleep 5

  save_profile "$result_dir/save-profile.txt"
  sleep 1
  adb_run shell am force-stop "$PACKAGE"

  adb_run shell pm dump-profiles --dump-classes-and-methods "$PACKAGE"     > "$result_dir/dump-profiles.txt" 2>&1 || {
    cat "$result_dir/dump-profiles.txt" >&2
    exit 7
  }

  local remote="/data/misc/profman/${PACKAGE}-primary.prof.txt"
  local output="$result_dir/baseline-prof.txt"
  adb_run pull "$remote" "$output" > "$result_dir/pull-profile.txt" 2>&1 || {
    cat "$result_dir/pull-profile.txt" >&2
    echo "Profile dump completed but the HRF could not be pulled from $remote." >&2
    exit 8
  }

  if [[ ! -s "$output" ]]; then
    echo "Pulled Baseline Profile is empty: $output" >&2
    exit 9
  fi

  local rules startup_rules
  rules="$(grep -cve '^[[:space:]]*$' "$output" || true)"
  startup_rules="$(grep -c '^.*S.*L' "$output" || true)"

  {
    echo "# Manual Baseline Profile capture"
    echo
    echo "- Raw candidate: $output"
    echo "- Non-empty rules: $rules"
    echo "- Lines carrying an S startup flag (approximate textual count): $startup_rules"
    echo "- Capture build: $(cat "$result_dir/capture-apk-path.txt" 2>/dev/null || echo unknown)"
    echo "- Production profile committed: NO"
    echo
    echo "The candidate remains under device-test-results/ and is not copied into app/src/main automatically."
  } > "$result_dir/SUMMARY.md"

  cat "$result_dir/SUMMARY.md"
}

restore_profileinstaller() {
  local result_dir="${1:-device-test-results/manual-profile-restore-$(timestamp)}"
  mkdir -p "$result_dir"
  delete_skip_file "$result_dir/skip-delete.txt"
  adb_run shell am force-stop "$PACKAGE" || true
  echo "ProfileInstaller skip file deleted. Normal automatic profile installation is allowed again."
  echo "Evidence: $result_dir/skip-delete.txt"
}

capture_all() {
  local result_dir="device-test-results/manual-profile-$(timestamp)"
  prepare_capture "$result_dir"
  run_journeys "$result_dir" "$CAPTURE_ITERATIONS"
  finish_capture "$result_dir"
  echo
  echo "Capture complete: $result_dir"
  echo "Keep the skip file in place until manual A/B setup is complete."
}

usage() {
  cat <<'EOF'
Usage:
  bash scripts/infinix-manual-profile.sh capture
  bash scripts/infinix-manual-profile.sh prepare [RESULT_DIR]
  bash scripts/infinix-manual-profile.sh journey <RESULT_DIR> [ITERATIONS]
  bash scripts/infinix-manual-profile.sh save <RESULT_DIR>
  bash scripts/infinix-manual-profile.sh restore [RESULT_DIR]

capture
  Runs prepare + ADB-driven critical user journeys + save/pull.

prepare
  Builds/installs the non-R8, non-debuggable baselineProfile app variant,
  writes ProfileInstaller's skip file, compiles to verify, and clears profiles.

journey
  Drives VS Launcher directly with 'adb shell input'. No instrumentation
  controller is used.

save
  Waits for ART profile stabilization, sends SAVE_PROFILE, runs API 34+
  'pm dump-profiles --dump-classes-and-methods', and pulls the HRF into the
  gitignored RESULT_DIR as baseline-prof.txt.

restore
  Deletes ProfileInstaller's skip file after capture/A-B testing is complete.

Environment:
  ADB_SERIAL=<serial>       select a device when more than one is connected
  CAPTURE_ITERATIONS=3      number of CUJ passes
  GESTURE_SLEEP=0.7         delay between direct ADB gestures
EOF
}

case "${1:-help}" in
  capture)
    capture_all
    ;;
  prepare)
    shift
    prepare_capture "${1:-device-test-results/manual-profile-$(timestamp)}"
    ;;
  journey)
    shift
    [[ -n "${1:-}" ]] || { usage >&2; exit 1; }
    run_journeys "$1" "${2:-$CAPTURE_ITERATIONS}"
    ;;
  save)
    shift
    [[ -n "${1:-}" ]] || { usage >&2; exit 1; }
    finish_capture "$1"
    ;;
  restore)
    shift
    restore_profileinstaller "${1:-}"
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
