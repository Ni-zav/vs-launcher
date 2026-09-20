#!/usr/bin/env bash
set -euo pipefail

CONTROLLER="com.vslauncher.macrobenchmark"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PID_WAIT_SECONDS="${PID_WAIT_SECONDS:-120}"
RUN_TIMEOUT_SECONDS="${RUN_TIMEOUT_SECONDS:-900}"

adb_cmd=(adb)
if [[ -n "${ADB_SERIAL:-}" ]]; then
  adb_cmd+=( -s "$ADB_SERIAL" )
else
  mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  if [[ ${#devices[@]} -ne 1 ]]; then
    echo "Expected exactly one authorized ADB device; found ${#devices[@]}." >&2
    echo "Set ADB_SERIAL=<serial> when more than one device is connected." >&2
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

controller_pid() {
  adb_run shell pidof "$CONTROLLER" 2>/dev/null | tr -d '\r' | awk '{print $1}'
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
    echo "use_freezer=$(adb_run shell device_config get activity_manager_native_boot use_freezer | tr -d '\r')"
  } > "$out"
}

preflight() {
  local result_dir="${1:-device-test-results/codex-preflight-$(timestamp)}"
  mkdir -p "$result_dir"

  record_device "$result_dir/device.txt"
  adb_run devices -l > "$result_dir/adb-devices.txt"
  adb_run shell dumpsys activity instrumentation > "$result_dir/instrumentation.txt" 2>&1 || true
  adb_run shell dumpsys activity     | grep -A 50 "CachedAppOptimizer settings"     > "$result_dir/cached-app-optimizer.txt" || true

  echo "Preflight captured: $result_dir"
  cat "$result_dir/device.txt"
}

profile_class_for() {
  case "${1:-all}" in
    startup)
      printf '%s' 'com.vslauncher.macrobenchmark.BaselineProfileGenerator#startupProfile'
      ;;
    journeys)
      printf '%s' 'com.vslauncher.macrobenchmark.BaselineProfileGenerator#commonLauncherJourneys'
      ;;
    all)
      printf '%s' 'com.vslauncher.macrobenchmark.BaselineProfileGenerator'
      ;;
    *)
      echo "Unknown profile selection: $1" >&2
      exit 3
      ;;
  esac
}

benchmark_class_for() {
  case "${1:-cold}" in
    cold)
      printf '%s' 'com.vslauncher.macrobenchmark.LauncherMacrobenchmark#coldStartup'
      ;;
    profile)
      printf '%s' 'com.vslauncher.macrobenchmark.LauncherMacrobenchmark#coldStartupWithBaselineProfile'
      ;;
    apps)
      printf '%s' 'com.vslauncher.macrobenchmark.LauncherMacrobenchmark#homeToAppsFrames'
      ;;
    settings)
      printf '%s' 'com.vslauncher.macrobenchmark.LauncherMacrobenchmark#homeToSettingsFrames'
      ;;
    fling)
      printf '%s' 'com.vslauncher.macrobenchmark.LauncherMacrobenchmark#allAppsFlingFrames'
      ;;
    search)
      printf '%s' 'com.vslauncher.macrobenchmark.LauncherMacrobenchmark#searchFilterFrames'
      ;;
    all)
      printf '%s' 'com.vslauncher.macrobenchmark.LauncherMacrobenchmark'
      ;;
    *)
      echo "Unknown benchmark selection: $1" >&2
      exit 3
      ;;
  esac
}

wait_for_controller_and_sticky() {
  local result_dir="$1"
  local gradle_pid="$2"
  local waited=0
  local seen_pid=""
  local current=""

  while kill -0 "$gradle_pid" 2>/dev/null && (( waited < PID_WAIT_SECONDS )); do
    current="$(controller_pid)"
    if [[ -n "$current" ]]; then
      seen_pid="$current"
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done

  if [[ -z "$seen_pid" ]]; then
    echo "Controller PID did not appear within ${PID_WAIT_SECONDS}s."       | tee "$result_dir/controller-not-found.txt"
    return 10
  fi

  echo "$seen_pid" > "$result_dir/controller.pid"
  echo "Controller PID detected: $seen_pid"

  {
    echo "== instrumentation before sticky =="
    adb_run shell dumpsys activity instrumentation || true
    echo
    echo "== cgroup before sticky =="
    adb_run shell cat "/proc/$seen_pid/cgroup" || true
    echo
    echo "== status before sticky =="
    adb_run shell cat "/proc/$seen_pid/status" || true
  } > "$result_dir/controller-before-sticky.txt"

  adb_run shell am unfreeze --sticky "$seen_pid"     > "$result_dir/sticky-command.txt" 2>&1 || true

  {
    echo "== instrumentation after sticky =="
    adb_run shell dumpsys activity instrumentation || true
    echo
    echo "== cgroup after sticky =="
    adb_run shell cat "/proc/$seen_pid/cgroup" || true
    echo
    echo "== status after sticky =="
    adb_run shell cat "/proc/$seen_pid/status" || true
  } > "$result_dir/controller-after-sticky.txt"

  # Watch only for controller recreation. We intentionally do not poll cgroup
  # state continuously because that adds device work. If the PID changes,
  # re-apply sticky to the replacement controller.
  while kill -0 "$gradle_pid" 2>/dev/null; do
    current="$(controller_pid)"
    if [[ -n "$current" && "$current" != "$seen_pid" ]]; then
      {
        echo "$(date -Iseconds) controller pid changed: $seen_pid -> $current"
        adb_run shell am unfreeze --sticky "$current" || true
      } >> "$result_dir/controller-recreation.txt" 2>&1
      seen_pid="$current"
      echo "$seen_pid" > "$result_dir/controller.pid"
    fi
    sleep 2
  done
}

run_profile_with_sticky() {
  local selection="${1:-all}"
  local test_class result_dir gradle_pid watcher_pid status

  test_class="$(profile_class_for "$selection")"
  result_dir="device-test-results/codex-sticky-profile-$(timestamp)"
  mkdir -p "$result_dir"
  record_device "$result_dir/device.txt"

  echo "Profile generation with process-scoped sticky-unfreeze."
  echo "Selection: $selection"
  echo "This is profile capture, not a performance timing sample."

  set +e
  timeout --signal=TERM "$RUN_TIMEOUT_SECONDS"     gradle --no-daemon :macrobenchmark:connectedBaselineProfileAndroidTest       -P "android.testInstrumentationRunnerArguments.class=$test_class"       > "$result_dir/gradle.txt" 2>&1 &
  gradle_pid=$!

  wait_for_controller_and_sticky "$result_dir" "$gradle_pid" &
  watcher_pid=$!

  wait "$gradle_pid"
  status=$?
  wait "$watcher_pid" || true
  set -e

  adb_run shell dumpsys activity instrumentation     > "$result_dir/instrumentation-after.txt" 2>&1 || true
  adb_run logcat -d -v threadtime     | grep -i -E 'freez|CachedAppOptimizer|AndroidRuntime|com\.vslauncher'     > "$result_dir/logcat-relevant.txt" || true

  find macrobenchmark/build/outputs -type f     \( -name '*baseline-prof.txt' -o -name '*startup-prof.txt' -o -name '*.json' -o -name '*.perfetto-trace' \)     -print > "$result_dir/generated-files.txt" 2>/dev/null || true

  {
    echo "# Sticky profile-generation result"
    echo
    echo "- Selection: $selection"
    echo "- Test class: $test_class"
    echo "- Exit status: $status"
    echo "- Result directory: $result_dir"
    echo "- Performance-valid timing sample: NO"
    echo
    echo "This run is for controller survivability/profile capture."
    echo "Inspect gradle.txt, controller-*.txt and generated-files.txt."
  } > "$result_dir/SUMMARY.md"

  cat "$result_dir/SUMMARY.md"
  return "$status"
}

run_probe_with_sticky() {
  local selection="${1:-cold}"
  local test_class result_dir gradle_pid watcher_pid status

  test_class="$(benchmark_class_for "$selection")"
  result_dir="device-test-results/codex-sticky-probe-$(timestamp)"
  mkdir -p "$result_dir"
  record_device "$result_dir/device.txt"

  echo "Benchmark survivability probe with controller PID monitoring."
  echo "Selection: $selection"
  echo "DO NOT use timing output from this monitored run as A/B evidence."

  set +e
  timeout --signal=TERM "$RUN_TIMEOUT_SECONDS"     gradle --no-daemon :macrobenchmark:connectedBenchmarkAndroidTest       -P "android.testInstrumentationRunnerArguments.class=$test_class"       > "$result_dir/gradle.txt" 2>&1 &
  gradle_pid=$!

  wait_for_controller_and_sticky "$result_dir" "$gradle_pid" &
  watcher_pid=$!

  wait "$gradle_pid"
  status=$?
  wait "$watcher_pid" || true
  set -e

  adb_run shell dumpsys activity instrumentation     > "$result_dir/instrumentation-after.txt" 2>&1 || true
  adb_run logcat -d -v threadtime     | grep -i -E 'freez|CachedAppOptimizer|AndroidRuntime|com\.vslauncher'     > "$result_dir/logcat-relevant.txt" || true

  {
    echo "# Sticky benchmark survivability probe"
    echo
    echo "- Selection: $selection"
    echo "- Test class: $test_class"
    echo "- Exit status: $status"
    echo "- Result directory: $result_dir"
    echo "- Performance-valid timing sample: NO"
    echo
    echo "Use this only to determine whether exact-PID sticky unfreeze keeps"
    echo "the controller alive. Run an unmonitored measurement afterward."
  } > "$result_dir/SUMMARY.md"

  cat "$result_dir/SUMMARY.md"
  return "$status"
}

run_measurement() {
  local selection="${1:-cold}"
  local test_class result_dir status

  test_class="$(benchmark_class_for "$selection")"
  result_dir="device-test-results/codex-measurement-$selection-$(timestamp)"
  mkdir -p "$result_dir"
  record_device "$result_dir/device.txt"

  echo "Unmonitored physical-device Macrobenchmark measurement."
  echo "Selection: $selection"
  echo "No sticky watcher is active during this run."

  set +e
  timeout --signal=TERM "$RUN_TIMEOUT_SECONDS"     gradle --no-daemon :macrobenchmark:connectedBenchmarkAndroidTest       -P "android.testInstrumentationRunnerArguments.class=$test_class"       > "$result_dir/gradle.txt" 2>&1
  status=$?
  set -e

  find macrobenchmark/build/outputs -type f     \( -name '*.json' -o -name '*.perfetto-trace' \)     -print > "$result_dir/result-files.txt" 2>/dev/null || true

  {
    echo "# Unmonitored Macrobenchmark measurement"
    echo
    echo "- Selection: $selection"
    echo "- Test class: $test_class"
    echo "- Exit status: $status"
    echo "- use_freezer: $(adb_run shell device_config get activity_manager_native_boot use_freezer | tr -d '\r')"
    echo "- Result directory: $result_dir"
    echo
    echo "This run may be used for A/B evidence only if:"
    echo "- the controller completed normally"
    echo "- A and B used identical freezer policy"
    echo "- device/refresh/thermal preparation was equivalent"
  } > "$result_dir/SUMMARY.md"

  cat "$result_dir/SUMMARY.md"
  return "$status"
}

usage() {
  cat <<'EOF'
Usage:
  bash scripts/infinix-codex-retry.sh preflight [RESULT_DIR]
  bash scripts/infinix-codex-retry.sh sticky-profile [startup|journeys|all]
  bash scripts/infinix-codex-retry.sh sticky-probe [cold|profile|apps|settings|fling|search|all]
  bash scripts/infinix-codex-retry.sh measure [cold|profile|apps|settings|fling|search|all]

Purpose:
  preflight
    Read-only device/repo/freezer snapshot.

  sticky-profile
    Starts Baseline Profile generation, waits for the live instrumentation
    controller PID, applies 'am unfreeze --sticky' to that PID, and reapplies
    only if the controller process is recreated.
    Valid for profile capture; not a performance sample.

  sticky-probe
    Same controller-survivability mechanism around Macrobenchmark.
    Timing from this mode is intentionally NOT valid A/B evidence.

  measure
    Runs Macrobenchmark without a PID watcher or repeated ADB polling.
    Use for final A/B measurements after the environment/workaround is chosen.

For the device-wide fallback use the separate guarded helper:
  bash scripts/infinix-freezer-session.sh disable --yes
  bash scripts/infinix-freezer-session.sh restore <RESULT_DIR>

Set ADB_SERIAL=<serial> when multiple devices are attached.
Optional:
  PID_WAIT_SECONDS=120
  RUN_TIMEOUT_SECONDS=900
EOF
}

case "${1:-help}" in
  preflight)
    shift
    preflight "${1:-}"
    ;;
  sticky-profile)
    shift
    run_profile_with_sticky "${1:-all}"
    ;;
  sticky-probe)
    shift
    run_probe_with_sticky "${1:-cold}"
    ;;
  measure)
    shift
    run_measurement "${1:-cold}"
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
