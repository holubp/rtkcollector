# RTKLIB Solution Output And Mock GPS Design

Date: 2026-06-22

## Purpose

RtkCollector already starts RTKLIB for RTKLIB-enabled workflows and the
dashboard can show RTKLIB float/fix state with plausible horizontal and
vertical accuracy. The remaining blocker is therefore not simply "RTKLIB does
not calculate"; it is that RTKLIB results are not proven to flow completely and
consistently through every downstream consumer:

- session artifacts: `rtklib-status.jsonl`, `rtklib-solution.nmea`,
  `rtklib-solution.pos`;
- the common `SolutionCandidate` set;
- dashboard best-solution selection;
- Android mock-location publishing.

This design closes that gap without weakening the raw recording architecture.
Replay is required as a reproducible diagnostic, but the plan must not stop at
replay. Acceptance requires complete live-result publication and mock GPS
eligibility for RTKLIB solutions.

## Non-Goals

- Do not move RTKLIB, mock GPS, dashboard monitoring or replay diagnostics onto
  the raw receiver recording path.
- Do not start RTKLIB workers, load native RTKLIB libraries or allocate RTKLIB
  queues unless the validated workflow/profile explicitly enables RTKLIB.
- Do not fake RTKLIB solutions from receiver-internal solutions.
- Do not add maps, GIS features or RTKGPS+ UI behaviour.

## Terminology

References to the Android app used for comparison must say `RTKGPS+`. The
string `RTKLIB+` may refer to RTKLIB-derived software only if that is literally
the intended project name, but it must not be used as the Android app name.

The implementation plan must include a repository-wide terminology check outside
local debug captures:

```bash
rg -n "RTKLIB\\+|RTKGPS\\+|RtkGps|RTK GPS" . \
  -g '!samples/**' -g '!build/**' -g '!**/.gradle/**'
```

## Architecture

The RTKLIB result path has four explicit gates:

1. **Native calculation gate**
   - RTKLIB receives rover observations and RTCM3 corrections through the
     existing advisory RTKLIB worker queues.
   - The native backend reports decoded rover/correction counts, engine state,
     latest solution and output text batches.
   - Valid RTKLIB float/fix/DGPS/single solutions are represented as
     `RtklibSolutionSnapshot`.

2. **Session artifact gate**
   - Every valid RTKLIB output batch that contains NMEA or POS lines is written
     to `rtklib-solution.nmea` and/or `rtklib-solution.pos`, according to the
     active RTKLIB profile.
   - Bounded status snapshots are written to `rtklib-status.jsonl` while RTKLIB
     is enabled.
   - Missing NMEA/POS output while the dashboard shows valid RTKLIB fixes is a
     bug, not an acceptable limitation.

3. **Solution candidate gate**
   - Every fresh `RtklibSolutionSnapshot` with coordinates becomes a
     `SolutionCandidate` with `engine=RTKLIB_REALTIME`.
   - The RTKLIB card and the general best-solution/mock path must either consume
     the same candidate source or have tests proving both are updated from the
     same RTKLIB snapshot.
   - `RTKLIB_ONLY` policies must not select receiver-internal candidates.

4. **Mock GPS gate**
   - `RTKLIB_ONLY`: publish only fresh RTKLIB candidates; if no fresh RTKLIB
     candidate exists, report stale/no RTKLIB solution and do not fall back.
   - `AUTO_BEST`: publish RTKLIB only when it wins normal best-solution
     selection; otherwise use the best receiver-internal solution.
   - Mock `Location.altitude` continues to use ellipsoidal height.
   - Mock resampling remains separate from recording and dashboard monitoring.

## Data Flow

```text
receiver-rx.raw append
  -> advisory RTKLIB rover queue
  -> native RTKLIB backend
  -> RtklibSolutionSnapshot
  -> rtklib-status.jsonl
  -> rtklib-solution.nmea / rtklib-solution.pos
  -> SolutionCandidate(engine=RTKLIB_REALTIME)
  -> dashboard best solution
  -> mock GPS publisher, if enabled by policy

correction-input.raw append
  -> advisory RTKLIB correction queue
  -> native RTKLIB backend
```

Raw receiver and correction sidecar writes happen before advisory RTKLIB
processing. Any RTKLIB or mock failure must be recorded as advisory state only
and must not stop receiver recording while transport and storage still work.

## Error Handling

- Native RTKLIB backend errors set RTKLIB state/error fields and stop accepting
  RTKLIB input, but do not stop recording.
- RTKLIB output writer errors mark RTKLIB output as failed and preserve raw
  receiver/correction recording.
- If the RTKLIB card has a valid latest solution but `SolutionCandidate` does
  not, this must be surfaced by tests and diagnostics as a publication failure.
- If `RTKLIB_ONLY` mock is enabled and RTKLIB has no fresh candidate, the mock
  monitor reports stale/no RTKLIB solution rather than silently publishing a
  receiver-internal solution.

## Testing Strategy

The implementation must add tests for all four gates:

1. **Replay fixture test**
   - Use the M8T session recording as a canonical fixture.
   - Feed `receiver-rx.raw` as UBX rover input and `correction-input.raw` or
     `correction-input.rtcm3` as RTCM3 correction input.
   - Assert that replay produces at least one valid RTKLIB solution and nonempty
     RTKLIB output when the profile enables output.

2. **Worker/output test**
   - Use a fake RTKLIB backend that emits a valid `RtklibSolutionSnapshot`,
     NMEA line and POS line.
   - Assert that the worker stores the latest solution and writes the expected
     output lines through `RtklibOutputWriters`.

3. **Foreground-service publication test**
   - Prove that a valid RTKLIB snapshot becomes a
     `SolutionCandidate(engine=RTKLIB_REALTIME)`.
   - Prove that dashboard RTKLIB state and best-solution state do not diverge
     for the same snapshot.

4. **Mock policy test**
   - `RTKLIB_ONLY` publishes a fresh RTKLIB candidate.
   - `RTKLIB_ONLY` reports stale when only receiver-internal candidates exist.
   - `AUTO_BEST` can publish a receiver-internal candidate when RTKLIB has no
     fresh candidate.

## Acceptance Criteria

- A session where the dashboard shows RTKLIB float/fix also records corresponding
  RTKLIB status and, when enabled, NMEA/POS solution lines.
- RTKLIB solutions are available to the common best-solution selector as
  `engine=RTKLIB_REALTIME`.
- Mock GPS can publish RTKLIB results when the active mock policy selects
  RTKLIB and a fresh RTKLIB candidate exists.
- Mock GPS does not publish receiver-internal fixes under `RTKLIB_ONLY`.
- RTKLIB remains lazy and advisory when disabled.
- Documentation uses `RTKGPS+` for the Android reference app.

## Verification Commands

Use the strongest feasible local checks:

```bash
git diff --check
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:compileDebugKotlin
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :core:rtklib:test :core:solution:test
```

On hosts where native Android tooling is available, additionally build the APK
and run the native RTKLIB replay tests. In the Termux/aarch64 environment, do
not misreport native `aapt2` or Android packaging limits as source failures.
