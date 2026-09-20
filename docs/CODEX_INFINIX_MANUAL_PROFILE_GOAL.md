# Codex goal — manual X6855 Baseline Profile fallback

Use this as the next local Codex `/goal` after pulling latest `main`.

---

/goal

Complete the VS Launcher Baseline Profile validation on my only physical device:
Infinix X6855, Android 16 / API 36, XOS.

Do not retry the frozen AndroidX Macrobenchmark controller path. That blocker is
already documented and reproduced with:
- exact live PID sticky-unfreeze
- DeviceConfig freezer disable attempt
- Developer Options cached-app-freezer disable

Use the repository's instrumentation-free manual workflow instead.

Read first:

- `docs/INFINIX_MANUAL_PROFILE.md`
- `docs/INFINIX_DEVICE_TEST.md`
- `docs/device-tests/2026-09-20-infinix-569e80b.md`
- `docs/device-tests/infinix-x6855-freezer-issue.md`
- `scripts/infinix-manual-profile.sh`
- `scripts/infinix-manual-ab.sh`
- `scripts/analyze-startup.py`

Do not change XOS freezer settings during this workflow.

## Phase 1 — sync and validate repo

1. Pull latest `main`.
2. Confirm the working tree is clean before starting.
3. Record the exact tested commit.
4. Run the normal build/lint once.
5. Confirm the connected device is still the expected Infinix X6855 / API 36.
6. Record the current HOME role holder.
7. If VS Launcher is the current HOME app, temporarily select the stock XOS
   launcher as default Home before capture/measurement. Do not bypass the
   scripts' HOME-role safety check.
8. Do not uninstall `com.vslauncher`.

## Phase 2 — collect a manual Baseline Profile candidate

Run:

~~~sh
CAPTURE_ITERATIONS=5 bash scripts/infinix-manual-profile.sh capture
~~~

This must:
- use the non-R8, non-debuggable `baselineProfile` app variant
- install with `adb install -r`
- write ProfileInstaller's skip file successfully
- reset ART to verify and clear app profiles
- drive VS Launcher through direct ADB input, not instrumentation
- cover Home, All Apps, fling, Settings, search, and query input
- wait for profile stabilization
- receive SAVE_PROFILE result code 12
- run API-34+ `pm dump-profiles --dump-classes-and-methods`
- pull a non-empty HRF into a gitignored device-test-results directory

Do not copy or commit the profile yet.

Inspect the generated `baseline-prof.txt` and reject it if it is empty,
obviously malformed, or contains command/log output instead of ART HRF rules.

Record the capture result directory.

## Phase 3 — same-APK manual A/B

Use the captured HRF:

~~~sh
ITERATIONS=20 STATE_SETTLE_SECONDS=2 \
  bash scripts/infinix-manual-ab.sh all \
  <CAPTURE_RESULT_DIR>/baseline-prof.txt
~~~

The script must:
- build and record a no-profile benchmark APK size before staging the candidate
- stage the candidate only in the working tree
- build one release-like benchmark APK containing the candidate
- verify the APK visibly contains Baseline Profile assets
- install that same APK once for both A and B
- preserve its SHA-256
- keep ProfileInstaller auto-install skipped during A/B

A must, before every sample:
- force-stop
- compile to verify
- clear app profiles
- refuse the sample if speed-profile remains
- settle
- force-stop
- measure MainActivity with `am start-activity -W`

B must, before every sample:
- force-stop
- reset compiled/profile state
- explicitly INSTALL_PROFILE and require ProfileInstaller result code 1
- force-stop
- compile with `speed-profile`
- require dumpsys dexopt to report `status=speed-profile`
- settle
- force-stop
- measure the same MainActivity command as A

Do not change the APK between A and B.

Do not use any frozen Macrobenchmark run as data.

## Phase 4 — inspect comparison

Read:

- `<AB_SESSION>/state-a/startup.csv`
- `<AB_SESSION>/state-b/startup.csv`
- `<AB_SESSION>/comparison.md`
- A/B per-sample prep/dexopt evidence
- APK hashes and size delta
- device state snapshots before/after

Evaluate:
- median TotalTime
- p90
- p95
- sample standard deviation
- invalid samples
- B-A percentage delta
- whether the distribution shift is larger than ordinary noise

Do not decide based on min/best sample.

If the first result is good enough to consider KEEP, perform a reversed-order
replication before committing:

~~~sh
bash scripts/infinix-manual-ab.sh unstage <FIRST_AB_SESSION>

ORDER=BA ITERATIONS=20 STATE_SETTLE_SECONDS=2 \
  bash scripts/infinix-manual-ab.sh all <CAPTURED_PROFILE>
~~~

If results are noisy or marginal, increase the replication to 30 samples with a
3-second state-settling delay.

Only accept a candidate when the direction is repeatable across the available
AB and BA evidence.

## Phase 5 — launcher smoke validation

After the manual A/B:
- verify Home renders
- All Apps works
- Settings works
- swipe-left opens focused Apps search
- vertical Apps browsing collapses search and reveals A–Z navigation
- quick launch works if configured
- aliases/hidden apps remain intact
- no FATAL EXCEPTION is introduced

Do not alter user configuration unnecessarily.

## Phase 6 — cleanup

Confirm ProfileInstaller's skip file was deleted by the A/B helper.

If needed:

~~~sh
bash scripts/infinix-manual-profile.sh restore
~~~

Do not leave any freezer/global XOS settings changed.

Restore VS Launcher as the default Home app and verify it resumes normally.

If the candidate is rejected:

~~~sh
bash scripts/infinix-manual-ab.sh unstage <AB_SESSION>
~~~

Confirm `app/src/main/baseline-prof.txt` is gone.

If accepted, leave it staged for the production commit.

## Phase 7 — device report

Create/update:

~~~text
docs/device-tests/YYYY-MM-DD-infinix-<shortsha>.md
~~~

Use the existing template but explicitly document the manual fallback.

Include:

- exact tested commit
- device/API/build fingerprint
- why Macrobenchmark was not used
- capture build type
- capture result directory
- HRF rule count
- profile SHA-256
- no-profile APK size
- candidate APK size
- APK size delta
- candidate benchmark APK SHA-256
- A sample count
- B sample count
- A median/p90/p95/stdev
- B median/p90/p95/stdev
- B-A median delta and percentage
- invalid sample count
- A dexopt state evidence
- B `status=speed-profile` evidence
- same-APK proof
- first-session order (AB or BA)
- reversed-order replication result when a KEEP decision is considered
- smoke-test result
- ProfileInstaller skip-file cleanup result
- limitations: manual A/B, not Macrobenchmark
- current profile delivery status for normal debug/release sideload APKs
- final decision: KEEP / REJECT / INCONCLUSIVE

Do not claim `CompilationMode.None` or
`CompilationMode.Partial(BaselineProfileMode.Require)`; those were not run.

## Phase 8 — production decision

Only if the candidate shows a meaningful repeatable improvement with no obvious
regression:

1. commit `app/src/main/baseline-prof.txt`
2. commit the device report
3. run build/lint and performance variant assembly
4. push the commits
5. do not add a fabricated `startup-prof.txt`
6. explicitly report that normal debug/release variants currently do not include
   ProfileInstaller; do not silently add it in this run
7. recommend a separate delivery follow-up if direct-sideload users need the
   measured Baseline Profile benefit

If the candidate is slower, noisy, or inconclusive:
- remove the staged candidate
- commit only the report/tooling fixes if needed
- state that no production Baseline Profile was accepted

If a script/repo bug is discovered:
- fix it in a small focused commit
- rerun the affected stage
- do not weaken validation checks merely to make the run pass

At completion report only:
1. PASS / FAIL / PARTIAL
2. capture result and rule count
3. A median/p90/p95
4. B median/p90/p95
5. median delta and percentage
6. APK size delta
7. KEEP / REJECT / INCONCLUSIVE
8. whether `baseline-prof.txt` was committed
9. device-test report path
10. commits pushed
11. raw evidence paths
