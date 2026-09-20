#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.vslauncher"
ACTIVITY="com.vslauncher/.MainActivity"
RECEIVER="$PACKAGE/androidx.profileinstaller.ProfileInstallReceiver"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ITERATIONS="${ITERATIONS:-20}"
STATE_SETTLE_SECONDS="${STATE_SETTLE_SECONDS:-2}"
BETWEEN_SAMPLES_SECONDS="${BETWEEN_SAMPLES_SECONDS:-1}"
TARGET_PROFILE="app/src/main/baseline-prof.txt"

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

hash_file() {
  local path="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$path" | awk '{print $1}'
  else
    shasum -a 256 "$path" | awk '{print $1}'
  fi
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

install_packaged_profile() {
  local log_file="$1"
  broadcast_expect 1 "$log_file"     -a androidx.profileinstaller.action.INSTALL_PROFILE     "$RECEIVER"
}

record_device_state() {
  local out="$1"
  {
    echo "captured_at=$(date -Iseconds)"
    echo "git_commit=$(git rev-parse HEAD 2>/dev/null || true)"
    echo "git_branch=$(git branch --show-current 2>/dev/null || true)"
    echo "manufacturer=$(adb_run shell getprop ro.product.manufacturer | tr -d '\r')"
    echo "model=$(adb_run shell getprop ro.product.model | tr -d '\r')"
    echo "android=$(adb_run shell getprop ro.build.version.release | tr -d '\r')"
    echo "api=$(adb_run shell getprop ro.build.version.sdk | tr -d '\r')"
    echo "security_patch=$(adb_run shell getprop ro.build.version.security_patch | tr -d '\r')"
    echo "fingerprint=$(adb_run shell getprop ro.build.fingerprint | tr -d '\r')"
    echo "use_freezer=$(adb_run shell device_config get activity_manager_native_boot use_freezer | tr -d '\r')"
    echo "cached_apps_freezer=$(adb_run shell settings get global cached_apps_freezer | tr -d '\r')"
    echo "home_role=$(adb_run shell cmd role get-role-holders android.app.role.HOME 2>/dev/null | tr -d '\r' | paste -sd ',' -)"
  } > "$out"

  {
    echo "== battery =="
    adb_run shell dumpsys battery || true
    echo
    echo "== thermal =="
    adb_run shell dumpsys thermalservice || true
    echo
    echo "== display =="
    adb_run shell wm size || true
    adb_run shell wm density || true
    adb_run shell dumpsys display | grep -E 'refreshRate|mRefreshRate|modeId|fps' | head -n 30 || true
  } >> "$out"
}

find_benchmark_apk() {
  find app/build/outputs/apk -type f     -path '*/benchmark/*' -name '*.apk' -print 2>/dev/null     | sort | tail -n 1
}

stage_candidate() {
  local source="$1"
  local session="$2"

  if [[ ! -s "$source" ]]; then
    echo "Candidate profile is missing or empty: $source" >&2
    return 3
  fi

  if [[ -e "$TARGET_PROFILE" ]]; then
    echo "$TARGET_PROFILE already exists; refusing to overwrite an existing production/candidate profile." >&2
    echo "Move or remove it deliberately before staging another candidate." >&2
    return 4
  fi

  mkdir -p "$session"
  cp "$source" "$session/captured-baseline-prof.txt"
  cp "$source" "$TARGET_PROFILE"

  {
    echo "source=$source"
    echo "source_sha256=$(hash_file "$source")"
    echo "staged_path=$TARGET_PROFILE"
    echo "staged_sha256=$(hash_file "$TARGET_PROFILE")"
    echo "rule_count=$(grep -cve '^[[:space:]]*$' "$TARGET_PROFILE" || true)"
  } > "$session/candidate.txt"

  echo "Candidate staged only in the working tree: $TARGET_PROFILE"
  echo "It is NOT committed."
}

build_reference_without_profile() {
  local session="$1"
  mkdir -p "$session"

  if [[ -e "$TARGET_PROFILE" ]]; then
    echo "Cannot build no-profile reference while $TARGET_PROFILE exists." >&2
    return 4
  fi

  gradle --no-daemon :app:assembleBenchmark     | tee "$session/build-reference-no-profile.txt"

  local apk
  apk="$(find_benchmark_apk)"
  if [[ -z "$apk" ]]; then
    echo "Could not find reference benchmark APK." >&2
    return 5
  fi

  printf '%s\n' "$apk" > "$session/reference-apk-path.txt"
  printf '%s\n' "$(hash_file "$apk")" > "$session/reference-apk-sha256.txt"
  stat -c '%s' "$apk" > "$session/reference-apk-size-bytes.txt" 2>/dev/null || wc -c < "$apk" > "$session/reference-apk-size-bytes.txt"

  echo "No-profile reference APK size: $(cat "$session/reference-apk-size-bytes.txt") bytes"
}

build_candidate() {
  local session="$1"
  mkdir -p "$session"

  gradle --no-daemon :app:assembleBenchmark     | tee "$session/build-benchmark.txt"

  local apk
  apk="$(find_benchmark_apk)"
  if [[ -z "$apk" ]]; then
    echo "Could not find assembled benchmark APK." >&2
    return 5
  fi

  printf '%s\n' "$apk" > "$session/apk-path.txt"
  printf '%s\n' "$(hash_file "$apk")" > "$session/apk-sha256.txt"
  stat -c '%s' "$apk" > "$session/apk-size-bytes.txt" 2>/dev/null || wc -c < "$apk" > "$session/apk-size-bytes.txt"

  unzip -l "$apk" > "$session/apk-contents.txt"
  if ! grep -qE 'assets/dexopt/baseline\.prof(m)?$' "$session/apk-contents.txt"; then
    echo "Benchmark APK does not visibly contain packaged Baseline Profile assets." >&2
    echo "Inspect $session/apk-contents.txt." >&2
    return 6
  fi

  echo "Release-like benchmark APK: $apk"
  echo "SHA-256: $(cat "$session/apk-sha256.txt")"
}

install_candidate() {
  local session="$1"
  local apk
  apk="$(cat "$session/apk-path.txt")"

  adb_run install -r "$apk" > "$session/install.txt" 2>&1 || {
    cat "$session/install.txt" >&2
    echo "Candidate APK install failed. The script did not uninstall VS Launcher." >&2
    return 7
  }

  adb_run shell am force-stop "$PACKAGE"
  write_skip_file "$session/skip-write-after-install.txt"

  {
    echo "local_apk=$apk"
    echo "local_sha256=$(cat "$session/apk-sha256.txt")"
    echo
    echo "== package =="
    adb_run shell dumpsys package "$PACKAGE"       | grep -E 'versionName=|versionCode=|firstInstallTime|lastUpdateTime' || true
  } > "$session/installed-package.txt"
}

reset_a_state() {
  local dir="$1"
  mkdir -p "$dir"

  adb_run shell am force-stop "$PACKAGE"
  write_skip_file "$dir/skip-write.txt"
  adb_run shell cmd package compile -f -m verify "$PACKAGE" > "$dir/compile-verify.txt" 2>&1
  adb_run shell pm art clear-app-profiles "$PACKAGE" > "$dir/clear-app-profiles.txt" 2>&1

  adb_run shell dumpsys package dexopt > "$dir/dexopt.txt" 2>&1 || true
  if grep -A 8 -B 2 "\[$PACKAGE\]" "$dir/dexopt.txt" | grep -q 'status=speed-profile'; then
    echo "A state still reports speed-profile after reset; refusing to measure contaminated A sample." >&2
    return 30
  fi
}

reset_b_state() {
  local dir="$1"
  mkdir -p "$dir"

  adb_run shell am force-stop "$PACKAGE"
  write_skip_file "$dir/skip-write.txt"
  adb_run shell cmd package compile -f -m verify "$PACKAGE" > "$dir/compile-verify.txt" 2>&1
  adb_run shell pm art clear-app-profiles "$PACKAGE" > "$dir/clear-app-profiles.txt" 2>&1

  install_packaged_profile "$dir/install-profile.txt"
  adb_run shell am force-stop "$PACKAGE"

  adb_run shell cmd package compile -f -m speed-profile "$PACKAGE"     > "$dir/compile-speed-profile.txt" 2>&1

  adb_run shell dumpsys package dexopt > "$dir/dexopt.txt" 2>&1 || true
  if ! grep -A 8 -B 2 "\[$PACKAGE\]" "$dir/dexopt.txt" | grep -q 'status=speed-profile'; then
    echo "B state did not report status=speed-profile; refusing to measure." >&2
    echo "Inspect $dir/dexopt.txt." >&2
    return 31
  fi
}

parse_field() {
  local field="$1"
  local file="$2"
  sed -n "s/^$field:[[:space:]]*//p" "$file" | tail -n 1 | tr -d '\r'
}

measure_state() {
  local state="$1"
  local session="$2"
  local state_lc
  state_lc="$(printf '%s' "$state" | tr '[:upper:]' '[:lower:]')"

  if [[ "$state" != "A" && "$state" != "B" ]]; then
    echo "State must be A or B." >&2
    return 32
  fi

  local out_dir="$session/state-$state_lc"
  local raw_dir="$out_dir/raw"
  mkdir -p "$raw_dir"
  record_device_state "$out_dir/device-before.txt"

  local csv="$out_dir/startup.csv"
  echo "iteration,total_time_ms,wait_time_ms,launch_state,status,timestamp" > "$csv"

  local failures=0
  for i in $(seq 1 "$ITERATIONS"); do
    echo "State $state sample $i/$ITERATIONS"

    local prep_dir="$out_dir/prep-$i"
    if [[ "$state" == "A" ]]; then
      reset_a_state "$prep_dir"
    else
      reset_b_state "$prep_dir"
    fi

    sleep "$STATE_SETTLE_SECONDS"
    adb_run shell am force-stop "$PACKAGE"

    local raw="$raw_dir/start-$i.txt"
    adb_run shell am start-activity -W -n "$ACTIVITY" > "$raw" 2>&1 || true

    local total wait launch status
    total="$(parse_field TotalTime "$raw")"
    wait="$(parse_field WaitTime "$raw")"
    launch="$(parse_field LaunchState "$raw")"
    status="$(parse_field Status "$raw")"

    if [[ ! "$total" =~ ^[0-9]+$ || "$status" != "ok" ]]; then
      failures=$((failures + 1))
    fi

    printf '%s,%s,%s,%s,%s,%s\n'       "$i" "$total" "$wait" "$launch" "$status" "$(date -Iseconds)" >> "$csv"

    sleep "$BETWEEN_SAMPLES_SECONDS"
  done

  record_device_state "$out_dir/device-after.txt"
  adb_run shell dumpsys package dexopt > "$out_dir/dexopt-after.txt" 2>&1 || true
  printf '%s\n' "$failures" > "$out_dir/failure-count.txt"

  if [[ "$failures" -ne 0 ]]; then
    echo "State $state completed with $failures invalid startup samples." >&2
    return 33
  fi
}

compare_states() {
  local session="$1"
  local a="$session/state-a/startup.csv"
  local b="$session/state-b/startup.csv"

  if [[ ! -s "$a" || ! -s "$b" ]]; then
    echo "Missing A/B CSV files under $session." >&2
    return 34
  fi

  python3 scripts/analyze-startup.py "$a" "$b"     --output "$session/comparison.md"     | tee "$session/comparison.stdout.txt"

  {
    echo
    echo "## Evidence"
    echo
    echo "- Candidate profile SHA-256: $(awk -F= '/staged_sha256=/{print $2}' "$session/candidate.txt")"
    echo "- Benchmark APK SHA-256: $(cat "$session/apk-sha256.txt")"
    echo "- No-profile APK size: $(cat "$session/reference-apk-size-bytes.txt") bytes"
    echo "- Candidate APK size: $(cat "$session/apk-size-bytes.txt") bytes"
    echo "- APK size delta: $(( $(cat "$session/apk-size-bytes.txt") - $(cat "$session/reference-apk-size-bytes.txt") )) bytes"
    echo "- A CSV: $a"
    echo "- B CSV: $b"
    echo
    echo "This is a manual same-device, same-APK ART-state A/B. It is not a Macrobenchmark result."
  } >> "$session/comparison.md"

  cat "$session/comparison.md"
}

cleanup_session() {
  local session="$1"
  mkdir -p "$session"
  set +e
  delete_skip_file "$session/skip-delete-cleanup.txt"
  local skip_status=$?
  set -e
  adb_run shell am force-stop "$PACKAGE" || true

  if [[ $skip_status -ne 0 ]]; then
    echo "WARNING: could not verify deletion of the ProfileInstaller skip file." >&2
    echo "Inspect $session/skip-delete-cleanup.txt." >&2
    return "$skip_status"
  fi

  echo "ProfileInstaller skip file restored to normal behavior."
  echo "The candidate file remains staged at $TARGET_PROFILE for review."
  echo "Do not commit it until the comparison is accepted."
}

unstage_candidate() {
  local session="$1"
  if [[ ! -f "$TARGET_PROFILE" ]]; then
    echo "No staged candidate exists at $TARGET_PROFILE."
    return 0
  fi

  local expected current
  expected="$(awk -F= '/staged_sha256=/{print $2}' "$session/candidate.txt" 2>/dev/null || true)"
  current="$(hash_file "$TARGET_PROFILE")"

  if [[ -z "$expected" || "$current" != "$expected" ]]; then
    echo "Refusing to delete $TARGET_PROFILE because its hash no longer matches the staged candidate." >&2
    return 35
  fi

  rm "$TARGET_PROFILE"
  echo "Removed uncommitted staged candidate: $TARGET_PROFILE"
}

prepare_session() {
  local source="$1"
  local session="$2"
  mkdir -p "$session"
  record_device_state "$session/device-session-start.txt"
  build_reference_without_profile "$session"
  stage_candidate "$source" "$session"
  build_candidate "$session"
  install_candidate "$session"
}

run_all() {
  local source="$1"
  local session="device-test-results/manual-ab-$(timestamp)"
  local status=0
  local installed=0

  mkdir -p "$session"

  prepare_session "$source" "$session" || status=$?
  if [[ $status -eq 0 ]]; then
    installed=1
    measure_state A "$session" || status=$?
  fi
  if [[ $status -eq 0 ]]; then
    measure_state B "$session" || status=$?
  fi
  if [[ $status -eq 0 ]]; then
    compare_states "$session" || status=$?
  fi

  if [[ $installed -eq 1 ]]; then
    set +e
    cleanup_session "$session"
    local cleanup_status=$?
    set -e
    if [[ $cleanup_status -ne 0 && $status -eq 0 ]]; then
      status=$cleanup_status
    fi
  fi

  record_device_state "$session/device-session-end.txt" || true

  echo
  echo "Manual A/B session: $session"
  echo "Candidate remains uncommitted at: $TARGET_PROFILE"
  if [[ $status -eq 0 ]]; then
    echo "A/B completed. Review $session/comparison.md before deciding whether to commit the profile."
  else
    echo "A/B stopped with status $status. Do not commit the candidate from this incomplete run." >&2
  fi
  return "$status"
}

usage() {
  cat <<'EOF'
Usage:
  bash scripts/infinix-manual-ab.sh all <CAPTURED_BASELINE_PROFILE>
  bash scripts/infinix-manual-ab.sh prepare <CAPTURED_BASELINE_PROFILE> [SESSION_DIR]
  bash scripts/infinix-manual-ab.sh measure-a <SESSION_DIR>
  bash scripts/infinix-manual-ab.sh measure-b <SESSION_DIR>
  bash scripts/infinix-manual-ab.sh compare <SESSION_DIR>
  bash scripts/infinix-manual-ab.sh cleanup <SESSION_DIR>
  bash scripts/infinix-manual-ab.sh unstage <SESSION_DIR>

all
  Stages the captured HRF into app/src/main/baseline-prof.txt (working tree only),
  builds one release-like benchmark APK, installs it in-place, then measures:
    A: verify compilation + cleared app profiles
    B: packaged profile installed + forced speed-profile compilation
  Each startup sample re-establishes its ART state before measurement.
  A and B therefore use the same APK hash.

prepare
  Stage/build/install only. Refuses to overwrite an existing baseline-prof.txt.

measure-a / measure-b
  Run one arm. Default ITERATIONS=20.

compare
  Compute median/mean/p90/p95/min/max/stdev using scripts/analyze-startup.py.

cleanup
  Delete ProfileInstaller's skip file and force-stop the app. Leaves the
  candidate staged for review.

unstage
  Remove app/src/main/baseline-prof.txt only if its hash still matches the
  candidate recorded in SESSION_DIR.

Environment:
  ADB_SERIAL=<serial>
  ITERATIONS=20
  STATE_SETTLE_SECONDS=2
  BETWEEN_SAMPLES_SECONDS=1

Important:
  This produces manual same-device startup evidence, not Macrobenchmark output.
  Do not commit the candidate unless the measured distribution shows a
  meaningful repeatable benefit and the device report documents the limitation.
EOF
}

case "${1:-help}" in
  all)
    shift
    [[ -n "${1:-}" ]] || { usage >&2; exit 1; }
    run_all "$1"
    ;;
  prepare)
    shift
    [[ -n "${1:-}" ]] || { usage >&2; exit 1; }
    prepare_session "$1" "${2:-device-test-results/manual-ab-$(timestamp)}"
    ;;
  measure-a)
    shift
    [[ -n "${1:-}" ]] || { usage >&2; exit 1; }
    measure_state A "$1"
    ;;
  measure-b)
    shift
    [[ -n "${1:-}" ]] || { usage >&2; exit 1; }
    measure_state B "$1"
    ;;
  compare)
    shift
    [[ -n "${1:-}" ]] || { usage >&2; exit 1; }
    compare_states "$1"
    ;;
  cleanup)
    shift
    [[ -n "${1:-}" ]] || { usage >&2; exit 1; }
    cleanup_session "$1"
    ;;
  unstage)
    shift
    [[ -n "${1:-}" ]] || { usage >&2; exit 1; }
    unstage_candidate "$1"
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
