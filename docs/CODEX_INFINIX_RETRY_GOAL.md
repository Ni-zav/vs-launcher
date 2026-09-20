# Historical goal — X6855 Macrobenchmark freezer retry

This goal has been completed on the current Infinix X6855 firmware.

Do not run it again unless the firmware changes or a different physical device is being tested.

Current path:

- [CODEX_INFINIX_MANUAL_PROFILE_GOAL.md](CODEX_INFINIX_MANUAL_PROFILE_GOAL.md)
- [INFINIX_MANUAL_PROFILE.md](INFINIX_MANUAL_PROFILE.md)

Confirmed outcome of this older path:

- exact live PID `am unfreeze --sticky` did not prevent `frozen 1`
- `activity_manager_native_boot/use_freezer=false` was not retained across reboot
- `Settings.Global.cached_apps_freezer=disabled` did not prevent the controller cgroup from freezing
- all global changes were restored
- no valid Macrobenchmark A/B was produced

---

# Codex goal — retry Infinix Android 16 Macrobenchmark / Baseline Profile

Use this as the next local Codex `/goal` prompt after pulling the latest `main`.

---

/goal

Resume the VS Launcher Android 16 Infinix X6855 physical-device validation from the repository state on latest `main`.

Do not re-research the freezer problem from scratch. The repository now contains the verified research and guarded tooling. Read these first:

- `docs/INFINIX_FREEZER_RESEARCH.md`
- `docs/INFINIX_DEVICE_TEST.md`
- `docs/BENCHMARK.md`
- `docs/device-tests/TEMPLATE.md`
- `scripts/infinix-freezer-session.sh`
- `scripts/infinix-codex-retry.sh`

Device context:

- Infinix X6855
- Android 16 / API 36
- VS Launcher package: `com.vslauncher`
- Macrobenchmark controller package: `com.vslauncher.macrobenchmark`
- launcher itself works and is the active/usable HOME app
- previous AndroidX Macrobenchmark / Baseline Profile controller froze in a cgroup with `frozen 1` and threads in `do_freezer_trap`
- ordinary package/background exemptions were already tried
- `freeze_exempt_inst_pkg` is NOT an instrumentation exemption; do not use it
- do not invent undocumented XOS shell settings
- do not move Macrobenchmark in-process

Primary objective:

Obtain reproducible Baseline Profile generation and defensible physical-device A/B Macrobenchmark measurements while changing the device as little as possible and restoring any global freezer change afterward.

## Phase 1 — sync and preflight

1. Ensure the repository is clean and on latest `main`.
2. Record the tested Git SHA.
3. Run:

~~~sh
bash scripts/infinix-codex-retry.sh preflight
~~~

4. Confirm:
   - device is the expected Infinix X6855
   - Android/API are recorded
   - ADB is authorized
   - current `use_freezer` value is recorded
5. Do not uninstall `com.vslauncher`.

## Phase 2 — process-scoped workaround first

Try Baseline Profile generation with the narrowest supported intervention:

~~~sh
bash scripts/infinix-codex-retry.sh sticky-profile startup
bash scripts/infinix-codex-retry.sh sticky-profile journeys
~~~

The runner will:
- start the connected Baseline Profile test
- wait for the live controller PID
- apply `am unfreeze --sticky` to that exact PID
- reapply only if the controller process is recreated
- record controller/instrumentation/cgroup evidence

Do not use any timing output from these monitored runs as performance evidence.

If both profile-generation runs complete, preserve the generated:
- baseline profile text
- startup profile text
- result logs

If a run still stalls/freezes, inspect its result directory and determine whether:
- the same controller PID remained alive
- instrumentation was still active
- that same PID entered a frozen cgroup

If the same live PID still freezes despite sticky-unfreeze, proceed to Phase 3.

## Phase 3 — guarded device-wide fallback only if needed

Use the repository helper, not ad-hoc commands:

~~~sh
bash scripts/infinix-freezer-session.sh disable --yes
~~~

This must:
- save the exact original `activity_manager_native_boot/use_freezer` state
- save device/CachedAppOptimizer evidence
- set the freezer off
- reboot
- verify the changed state
- print the result directory and restore command

After reboot, unlock the phone if needed and allow it to reach a stable state.

Then rerun profile generation:

~~~sh
bash scripts/infinix-codex-retry.sh sticky-profile startup
bash scripts/infinix-codex-retry.sh sticky-profile journeys
~~~

If generation succeeds, preserve the generated files and evidence.

Do not leave the phone in this modified state longer than necessary.

Restore using the exact result directory from the disable step:

~~~sh
bash scripts/infinix-freezer-session.sh restore <RESULT_DIR>
~~~

Verify rollback after reboot.

## Phase 4 — prepare a candidate Baseline Profile

Use a temporary branch or worktree.

Do not commit candidate profile rules yet.

Create the candidate from the actual generated files as documented in:
- `docs/INFINIX_DEVICE_TEST.md`
- `docs/BENCHMARK.md`

Ensure:
- baseline rules come from the non-obfuscated `baselineProfile` variant
- startup rules are kept semantically startup-only
- duplicate baseline rules are removed
- release-like benchmark build packages the candidate

Build:

~~~sh
gradle --no-daemon :app:assembleBenchmark :macrobenchmark:assembleBenchmark
~~~

## Phase 5 — preferred normal-policy A/B

After the global freezer has been restored and verified, try real measurements with no controller watcher:

A — no precompilation:

~~~sh
bash scripts/infinix-codex-retry.sh measure cold
~~~

B — packaged Baseline Profile required:

~~~sh
bash scripts/infinix-codex-retry.sh measure profile
~~~

The `profile` test uses:
`CompilationMode.Partial(BaselineProfileMode.Require)`.

If it fails because the profile is not actually packaged/usable, fix the candidate packaging rather than weakening the requirement.

If both complete under normal freezer policy, this is the preferred evidence.

## Phase 6 — fallback A/B only if normal policy still freezes the controller

First prove the normal-policy run is freezing again. You may use:

~~~sh
bash scripts/infinix-codex-retry.sh sticky-probe cold
~~~

This is a survivability probe only. Its timing output is NOT performance evidence.

If normal-policy Macrobenchmark cannot complete reliably even after the process-scoped test:

1. run the guarded global disable again
2. reboot/stabilize
3. run BOTH A and B under the exact same freezer-disabled state:

~~~sh
bash scripts/infinix-codex-retry.sh measure cold
bash scripts/infinix-codex-retry.sh measure profile
~~~

4. restore the exact original freezer state immediately afterward
5. reboot and verify rollback

Never compare:
- A with freezer enabled
- B with freezer disabled

or the reverse.

Any fallback result must be labeled:

`controlled A/B under device-wide cached-app freezer disabled`

and absolute timings must be described as non-default-device-condition results.

## Phase 7 — frame/regression checks

If the profile candidate appears beneficial, also run the unmonitored frame-oriented measurements in the same chosen environment:

~~~sh
bash scripts/infinix-codex-retry.sh measure apps
bash scripts/infinix-codex-retry.sh measure settings
bash scripts/infinix-codex-retry.sh measure fling
bash scripts/infinix-codex-retry.sh measure search
~~~

Check for regressions in frame timing/overrun metrics.

Preserve JSON and Perfetto traces.

## Phase 8 — sideload/dexopt observation

After restoring the intended normal device state, record the actual sideloaded app's ART/dexopt state using the commands already documented in `docs/INFINIX_DEVICE_TEST.md`.

Do not claim Play-style install-time Baseline Profile behavior merely because the sideloaded APK launches.

## Phase 9 — report

Create a completed report from:

`docs/device-tests/TEMPLATE.md`

Write:

`docs/device-tests/YYYY-MM-DD-infinix-<shortsha>.md`

The report must include:

- exact tested commit
- exact device/Android/API/build fingerprint
- original `use_freezer` state
- whether sticky exact-PID unfreeze worked
- whether the same PID ever returned to `frozen 1`
- whether global freezer disable was needed
- exact global-disable result directory
- proof rollback completed
- Baseline Profile generation result
- generated profile rule counts
- A results
- B results
- whether A/B used normal policy or freezer-disabled fallback
- startup median/p90/p95 where available
- frame metrics
- JSON paths
- Perfetto paths
- sideload dexopt state
- errors and resolutions
- PASS / FAIL / PARTIAL

## Phase 10 — production decision

Do NOT commit `baseline-prof.txt` or `startup-prof.txt` merely because generation succeeded.

Only recommend/commit the profile if:

1. the candidate is actually required/recognized by the B test
2. A and B used identical freezer policy
3. repeated physical-device measurements show a benefit or at minimum no meaningful regression
4. frame-oriented checks do not regress meaningfully
5. APK size impact is recorded
6. Android 16 launcher smoke test remains good
7. all global device changes were rolled back successfully
8. the report contains the evidence

If profile benefit is inconclusive, leave the generated profile out of production and say so.

If you discover a repository bug:
- fix it on a small branch
- make small focused commits
- rerun only the affected validation first
- then rerun the required final measurements
- document the bug and fix in the device report

At completion, tell me only:
1. PASS / FAIL / PARTIAL
2. whether sticky-unfreeze solved controller survival
3. whether global freezer disable was required
4. whether rollback was verified
5. A vs B measurements
6. whether the Baseline Profile should be committed
7. device-test report path
8. any code/docs commits you made
9. paths to raw evidence
