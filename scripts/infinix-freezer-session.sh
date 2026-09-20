#!/usr/bin/env bash
set -euo pipefail

CONTROLLER="com.vslauncher.macrobenchmark"
NAMESPACE="activity_manager_native_boot"
KEY="use_freezer"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

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

wait_for_boot() {
  adb_run wait-for-device
  local completed=""
  for _ in $(seq 1 120); do
    completed="$(adb_run shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    if [[ "$completed" == "1" ]]; then
      return 0
    fi
    sleep 1
  done
  echo "Device returned to ADB but sys.boot_completed did not become 1." >&2
  return 1
}

show_status() {
  echo "== DeviceConfig =="
  printf '%s/%s=' "$NAMESPACE" "$KEY"
  adb_run shell device_config get "$NAMESPACE" "$KEY" | tr -d '\r'
  echo
  echo "== CachedAppOptimizer =="
  adb_run shell dumpsys activity     | grep -A 50 "CachedAppOptimizer settings"     || true
}

controller_pid() {
  adb_run shell pidof "$CONTROLLER" 2>/dev/null | tr -d '\r' | awk '{print $1}'
}

verify_controller() {
  local pid
  pid="$(controller_pid)"
  if [[ -z "$pid" ]]; then
    echo "Controller process is not currently running: $CONTROLLER" >&2
    exit 3
  fi

  echo "controller=$CONTROLLER"
  echo "pid=$pid"
  echo

  echo "== Active instrumentation =="
  adb_run shell dumpsys activity instrumentation || true
  echo

  echo "== Process cgroup =="
  local cgroup_text
  cgroup_text="$(adb_run shell cat "/proc/$pid/cgroup" | tr -d '\r')"
  printf '%s\n' "$cgroup_text"

  local rel
  rel="$(printf '%s\n' "$cgroup_text" | awk -F: '$1=="0" {print $3; exit}')"
  if [[ -n "$rel" ]]; then
    local events="/sys/fs/cgroup${rel}/cgroup.events"
    echo
    echo "== cgroup.events =="
    echo "$events"
    adb_run shell cat "$events" || true
  else
    echo "No unified cgroup-v2 0:: entry found; inspect the cgroup output manually." >&2
  fi

  echo
  echo "== Process status =="
  adb_run shell cat "/proc/$pid/status" || true
}

sticky_unfreeze() {
  local pid
  pid="$(controller_pid)"
  if [[ -z "$pid" ]]; then
    echo "Start the Macrobenchmark/Baseline Profile instrumentation first." >&2
    echo "No live controller PID found for $CONTROLLER." >&2
    exit 3
  fi

  echo "Applying AOSP sticky unfreeze to current controller PID $pid."
  adb_run shell am unfreeze --sticky "$pid"
  echo
  echo "This applies only to this process lifetime."
  echo "If the controller PID changes, run the command again for the new PID."
  verify_controller
}

disable_global() {
  if [[ "${1:-}" != "--yes" ]]; then
    cat >&2 <<'EOF'
Refusing to change the device-wide cached-app freezer without explicit acknowledgement.

Run:
  bash scripts/infinix-freezer-session.sh disable --yes [RESULT_DIR]

This changes ActivityManager freezer behavior for the whole device and reboots it.
EOF
    exit 4
  fi
  shift

  local timestamp result_dir original disabled
  timestamp="$(date +%Y%m%d-%H%M%S)"
  result_dir="${1:-device-test-results/freezer-session-$timestamp}"
  mkdir -p "$result_dir"

  original="$(adb_run shell device_config get "$NAMESPACE" "$KEY" | tr -d '\r')"
  printf '%s\n' "$original" > "$result_dir/use_freezer.before.txt"

  {
    echo "tested_at=$(date -Iseconds)"
    echo "git_commit=$(git rev-parse HEAD 2>/dev/null || true)"
    echo "manufacturer=$(adb_run shell getprop ro.product.manufacturer | tr -d '\r')"
    echo "model=$(adb_run shell getprop ro.product.model | tr -d '\r')"
    echo "android=$(adb_run shell getprop ro.build.version.release | tr -d '\r')"
    echo "api=$(adb_run shell getprop ro.build.version.sdk | tr -d '\r')"
    echo "fingerprint=$(adb_run shell getprop ro.build.fingerprint | tr -d '\r')"
  } > "$result_dir/session.txt"

  adb_run shell dumpsys activity     | grep -A 50 "CachedAppOptimizer settings"     > "$result_dir/cached-app-optimizer.before.txt" || true

  echo "Saved original $NAMESPACE/$KEY value: '$original'"
  echo "Result directory: $result_dir"
  echo
  echo "Disabling cached-app freezer device-wide and rebooting..."

  adb_run shell device_config put "$NAMESPACE" "$KEY" false
  adb_run reboot
  wait_for_boot

  disabled="$(adb_run shell device_config get "$NAMESPACE" "$KEY" | tr -d '\r')"
  printf '%s\n' "$disabled" > "$result_dir/use_freezer.disabled.txt"

  adb_run shell dumpsys activity     | grep -A 50 "CachedAppOptimizer settings"     > "$result_dir/cached-app-optimizer.disabled.txt" || true

  if [[ "$disabled" != "false" ]]; then
    cat >&2 <<EOF
Cached-app freezer disable was not verified after reboot.
Expected $NAMESPACE/$KEY=false, got '$disabled'.

No benchmark/profile run should use this session as freezer-disabled evidence.
The original state remains recorded in: $result_dir/use_freezer.before.txt
EOF
    exit 7
  fi

  cat <<EOF

Freezer diagnostic session is prepared.

IMPORTANT:
- unlock the phone if needed
- allow the device to return to a stable thermal/idle state
- run the previously failing benchmark/profile journey
- use:
    bash scripts/infinix-freezer-session.sh verify
- restore afterward with:
    bash scripts/infinix-freezer-session.sh restore "$result_dir"
EOF
}

restore_global() {
  local result_dir original
  result_dir="${1:-}"
  if [[ -z "$result_dir" || ! -f "$result_dir/use_freezer.before.txt" ]]; then
    echo "Usage: bash scripts/infinix-freezer-session.sh restore <RESULT_DIR>" >&2
    echo "RESULT_DIR must contain use_freezer.before.txt from the disable command." >&2
    exit 5
  fi

  original="$(cat "$result_dir/use_freezer.before.txt" | tr -d '\r\n')"

  adb_run shell device_config get "$NAMESPACE" "$KEY"     | tr -d '\r'     > "$result_dir/use_freezer.before-restore.txt"

  echo "Restoring original $NAMESPACE/$KEY state: '$original'"

  if [[ -z "$original" || "$original" == "null" ]]; then
    adb_run shell device_config delete "$NAMESPACE" "$KEY"
  elif [[ "$original" == "true" || "$original" == "false" ]]; then
    adb_run shell device_config put "$NAMESPACE" "$KEY" "$original"
  else
    echo "Unexpected saved value '$original'; refusing to guess rollback semantics." >&2
    exit 6
  fi

  adb_run reboot
  wait_for_boot

  adb_run shell device_config get "$NAMESPACE" "$KEY"     | tr -d '\r'     | tee "$result_dir/use_freezer.after-rollback.txt"

  adb_run shell dumpsys activity     | grep -A 50 "CachedAppOptimizer settings"     > "$result_dir/cached-app-optimizer.after-rollback.txt" || true

  echo
  echo "Rollback command completed."
  echo "Compare:"
  echo "  $result_dir/use_freezer.before.txt"
  echo "  $result_dir/use_freezer.after-rollback.txt"
  echo "and inspect CachedAppOptimizer before/after files."
}

usage() {
  cat <<'EOF'
Usage:
  bash scripts/infinix-freezer-session.sh status
  bash scripts/infinix-freezer-session.sh verify
  bash scripts/infinix-freezer-session.sh sticky
  bash scripts/infinix-freezer-session.sh disable --yes [RESULT_DIR]
  bash scripts/infinix-freezer-session.sh restore <RESULT_DIR>

Commands:
  status   Read current DeviceConfig and CachedAppOptimizer diagnostics.
  verify   Inspect the live Macrobenchmark controller PID, instrumentation, cgroup,
           cgroup.events, and /proc status.
  sticky   Apply AOSP 'am unfreeze --sticky' to the current controller PID, then verify.
           This lasts only for that process lifetime.
  disable  Save the original use_freezer state, set it false, and reboot.
           Requires --yes because this is device-wide.
  restore  Restore the exact captured state (put original true/false, or delete an
           originally-unset override), then reboot.

Set ADB_SERIAL=<serial> if multiple devices are connected.
EOF
}

case "${1:-status}" in
  status)
    show_status
    ;;
  verify)
    verify_controller
    ;;
  sticky)
    sticky_unfreeze
    ;;
  disable)
    shift
    disable_global "$@"
    ;;
  restore)
    shift
    restore_global "$@"
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
