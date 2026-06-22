# RTKLIB Solution Output And Mock GPS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure every valid RTKLIB real-time solution is written to RTKLIB session artifacts, published as a common solution candidate, shown consistently on the dashboard, and eligible for Android mock GPS according to the selected solution policy.

**Architecture:** Keep raw receiver and correction recording authoritative. RTKLIB remains a lazy advisory worker fed after raw appends. Add one small mapper from `RtklibSolutionSnapshot` to `SolutionCandidate`, wire it into the foreground service snapshot path, and add replay/output checks so calculation, artifacts, best-solution selection and mock publication are all verified separately.

**Tech Stack:** Kotlin/JVM unit tests, Android foreground service Kotlin, RTKLIB JNI bridge, existing session writer abstractions, local M8T replay fixture under `samples/debug/`, Gradle Kotlin/JVM test tasks.

---

## File Structure

- Create `app/src/main/kotlin/org/rtkcollector/app/recording/RtklibSolutionCandidateMapper.kt`
  - Converts a valid RTKLIB snapshot into a `SolutionCandidate`.
  - Maps RTKLIB fix classes to common solution fix classes.
  - Rejects snapshots without coordinates or with `NONE`/`INVALID` fix.
- Create `app/src/test/kotlin/org/rtkcollector/app/recording/RtklibSolutionCandidateMapperTest.kt`
  - Focused mapper tests.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
  - When `rtklibWorker.snapshot()` contains a valid solution, publish it into `solutionCandidates`.
  - Keep RTKLIB card state and best-solution/mock state driven by the same snapshot.
  - Preserve raw recording and advisory RTKLIB failure isolation.
- Modify `app/src/test/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogicTest.kt`
  - Add explicit `RTKLIB_ONLY` stale/no-fallback and publish cases if missing.
- Modify `core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibWorkerTest.kt`
  - Strengthen worker output test to require latest solution retention along with NMEA/POS writes.
- Create `tools/rtklib_replay_session_check.py`
  - Lightweight replay/session-output checker for debug ZIPs.
  - Verifies RTKLIB artifacts and status outputs are consistent with a session where RTKLIB should be enabled.
- Modify `docs/superpowers/plan-status.md`
  - Track this follow-up as the active blocker closure for RTKLIB output/mock publication.
- Modify `docs/specification/verification-matrix.md`
  - Add verification rows for RTKLIB snapshot publication and mock policy.

## Task 1: Add RTKLIB Snapshot To Solution Candidate Mapper

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/recording/RtklibSolutionCandidateMapper.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/recording/RtklibSolutionCandidateMapperTest.kt`

- [ ] **Step 1: Write mapper tests**

Create `app/src/test/kotlin/org/rtkcollector/app/recording/RtklibSolutionCandidateMapperTest.kt`:

```kotlin
package org.rtkcollector.app.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.rtkcollector.core.rtklib.RtklibFixClass
import org.rtkcollector.core.rtklib.RtklibSolutionSnapshot
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionEngine

class RtklibSolutionCandidateMapperTest {
    @Test
    fun `rtklib fixed solution becomes realtime solution candidate`() {
        val candidate = RtklibSolutionCandidateMapper.toCandidate(
            snapshot = RtklibSolutionSnapshot(
                fixClass = RtklibFixClass.RTK_FIXED,
                timestampMillis = 1_000L,
                latDeg = 50.123456,
                lonDeg = 14.654321,
                ellipsoidalHeightM = 321.5,
                horizontalAccuracyM = 0.012,
                verticalAccuracyM = 0.034,
                satellitesUsed = 15,
            ),
            receiverFamily = "ublox-m8t",
        )

        requireNotNull(candidate)
        assertEquals("RTKLIB", candidate.sourceId)
        assertEquals("ublox-m8t", candidate.receiverFamily)
        assertEquals(SolutionEngine.RTKLIB_REALTIME, candidate.engine)
        assertEquals(FixClass.RTK_FIXED, candidate.fixClass)
        assertEquals(1_000L, candidate.updatedAtMillis)
        assertEquals(50.123456, candidate.latDeg)
        assertEquals(14.654321, candidate.lonDeg)
        assertEquals(321.5, candidate.ellipsoidalHeightM)
        assertEquals(0.012, candidate.horizontalAccuracyM)
        assertEquals(0.034, candidate.verticalAccuracyM)
        assertEquals(15, candidate.satellitesUsed)
    }

    @Test
    fun `rtklib float and dgps map to common fix classes`() {
        assertEquals(
            FixClass.RTK_FLOAT,
            candidateFor(RtklibFixClass.RTK_FLOAT)?.fixClass,
        )
        assertEquals(
            FixClass.DGPS,
            candidateFor(RtklibFixClass.DGPS)?.fixClass,
        )
        assertEquals(
            FixClass.SINGLE,
            candidateFor(RtklibFixClass.SINGLE)?.fixClass,
        )
        assertEquals(
            FixClass.PPP_CONVERGED,
            candidateFor(RtklibFixClass.PPP)?.fixClass,
        )
    }

    @Test
    fun `invalid or coordinate-less rtklib snapshots are not candidates`() {
        assertNull(candidateFor(RtklibFixClass.NONE))
        assertNull(candidateFor(RtklibFixClass.INVALID))
        assertNull(
            RtklibSolutionCandidateMapper.toCandidate(
                snapshot = RtklibSolutionSnapshot(
                    fixClass = RtklibFixClass.RTK_FIXED,
                    timestampMillis = 1_000L,
                    latDeg = null,
                    lonDeg = 14.0,
                ),
                receiverFamily = "ublox-m8t",
            ),
        )
    }

    private fun candidateFor(fix: RtklibFixClass) =
        RtklibSolutionCandidateMapper.toCandidate(
            snapshot = RtklibSolutionSnapshot(
                fixClass = fix,
                timestampMillis = 1_000L,
                latDeg = 50.0,
                lonDeg = 14.0,
            ),
            receiverFamily = "ublox-m8t",
        )
}
```

- [ ] **Step 2: Run the new test and verify it fails**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest --tests org.rtkcollector.app.recording.RtklibSolutionCandidateMapperTest
```

Expected before implementation: compilation fails because `RtklibSolutionCandidateMapper` does not exist. If Termux fails before Kotlin test execution because Android resource tooling cannot run, record that environment limitation and continue with `:app:compileDebugKotlin` after implementation.

- [ ] **Step 3: Implement the mapper**

Create `app/src/main/kotlin/org/rtkcollector/app/recording/RtklibSolutionCandidateMapper.kt`:

```kotlin
package org.rtkcollector.app.recording

import org.rtkcollector.core.rtklib.RtklibFixClass
import org.rtkcollector.core.rtklib.RtklibSolutionSnapshot
import org.rtkcollector.core.solution.FixClass
import org.rtkcollector.core.solution.SolutionCandidate
import org.rtkcollector.core.solution.SolutionEngine

internal object RtklibSolutionCandidateMapper {
    private const val SOURCE_ID = "RTKLIB"

    fun toCandidate(
        snapshot: RtklibSolutionSnapshot,
        receiverFamily: String,
    ): SolutionCandidate? {
        val lat = snapshot.latDeg ?: return null
        val lon = snapshot.lonDeg ?: return null
        val fix = snapshot.fixClass.toCommonFixClass() ?: return null
        return SolutionCandidate(
            sourceId = SOURCE_ID,
            receiverFamily = receiverFamily,
            engine = SolutionEngine.RTKLIB_REALTIME,
            fixClass = fix,
            updatedAtMillis = snapshot.timestampMillis,
            latDeg = lat,
            lonDeg = lon,
            ellipsoidalHeightM = snapshot.ellipsoidalHeightM,
            horizontalAccuracyM = snapshot.horizontalAccuracyM,
            verticalAccuracyM = snapshot.verticalAccuracyM,
            satellitesUsed = snapshot.satellitesUsed,
        )
    }

    private fun RtklibFixClass.toCommonFixClass(): FixClass? =
        when (this) {
            RtklibFixClass.NONE,
            RtklibFixClass.INVALID,
            -> null
            RtklibFixClass.SINGLE -> FixClass.SINGLE
            RtklibFixClass.DGPS -> FixClass.DGPS
            RtklibFixClass.RTK_FLOAT -> FixClass.RTK_FLOAT
            RtklibFixClass.RTK_FIXED -> FixClass.RTK_FIXED
            RtklibFixClass.PPP -> FixClass.PPP_CONVERGED
        }
}
```

- [ ] **Step 4: Run mapper test**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest --tests org.rtkcollector.app.recording.RtklibSolutionCandidateMapperTest
```

Expected: mapper tests pass on a host that can run Android unit tests. In Termux, if app unit tests hit local Android tooling limitations, run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:compileDebugKotlin
```

Expected: Kotlin compile passes.

- [ ] **Step 5: Commit checkpoint**

Run:

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/RtklibSolutionCandidateMapper.kt \
  app/src/test/kotlin/org/rtkcollector/app/recording/RtklibSolutionCandidateMapperTest.kt
git commit -m "test: map rtklib snapshots to solution candidates"
```

## Task 2: Publish RTKLIB Snapshots Into The Common Candidate Set

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogicTest.kt`

- [ ] **Step 1: Add explicit mock policy tests**

In `app/src/test/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogicTest.kt`, add these tests near the existing policy tests:

```kotlin
    @Test
    fun `rtklib only mock policy publishes fresh rtklib candidate`() {
        val device = candidate("UBX-NAV-PVT", FixClass.DGPS, updatedAtMillis = 1_000L)
        val rtklib = candidate(
            sourceId = "RTKLIB",
            fixClass = FixClass.RTK_FLOAT,
            updatedAtMillis = 1_200L,
            engine = SolutionEngine.RTKLIB_REALTIME,
        )

        val out = BestSolutionTickLogic.compute(
            input(
                candidates = listOf(device, rtklib),
                nowMillis = 1_500L,
                mockEnabled = true,
            ).copy(mockPolicy = SolutionSourcePolicy.RTKLIB_ONLY),
        )

        val publish = out.publishAction as PublishAction.Publish
        assertEquals("RTKLIB", publish.snapshot.sourceId)
        assertEquals(SolutionEngine.RTKLIB_REALTIME, publish.snapshot.engine)
        assertEquals(FixClass.RTK_FLOAT, publish.snapshot.fixClass)
    }

    @Test
    fun `rtklib only mock policy does not fall back to device candidate`() {
        val device = candidate("UBX-NAV-PVT", FixClass.DGPS, updatedAtMillis = 1_000L)

        val out = BestSolutionTickLogic.compute(
            input(
                candidates = listOf(device),
                nowMillis = 1_500L,
                mockEnabled = true,
            ).copy(mockPolicy = SolutionSourcePolicy.RTKLIB_ONLY),
        )

        assertEquals(MockLocationPublishResult.STALE, out.stateDelta.mockResult)
        assertTrue(out.publishAction is PublishAction.None)
    }
```

- [ ] **Step 2: Run policy tests**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest --tests org.rtkcollector.app.recording.BestSolutionTickLogicTest
```

Expected: tests pass if current policy logic is correct; failures identify the smallest policy fix needed before service wiring.

- [ ] **Step 3: Add RTKLIB candidate publication in service snapshot path**

In `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`, inside `broadcastState()`, in the existing `rtklibWorker?.snapshot()?.let { snapshot -> ... }` block, immediately after:

```kotlin
val solution = snapshot.latestSolution
```

insert:

```kotlin
            solution?.let { rtklibSolution ->
                RtklibSolutionCandidateMapper.toCandidate(
                    snapshot = rtklibSolution,
                    receiverFamily = activeReceiverFamily,
                )?.let { candidate ->
                    solutionCandidates[candidate.sourceId] = candidate
                }
            }
```

Do not call `applyPrimaryScreenCandidate()` from this block. RTKLIB should enter the common candidate map; `runBestSolutionTick()` remains responsible for policy-based screen/mock selection.

- [ ] **Step 4: Ensure disabled RTKLIB leaves no stale RTKLIB candidate**

In `RecordingForegroundService.kt`, wherever a new recording clears `solutionCandidates`, no change is needed if `solutionCandidates.clear()` already executes at recording start and stop. Confirm these existing calls remain in place:

```kotlin
solutionCandidates.clear()
```

If there is a path that disables RTKLIB during a live recording, add:

```kotlin
solutionCandidates.remove("RTKLIB")
```

on that path only. Do not remove receiver-internal candidates.

- [ ] **Step 5: Compile service wiring**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:compileDebugKotlin
```

Expected: compile passes.

- [ ] **Step 6: Commit checkpoint**

Run:

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt \
  app/src/test/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogicTest.kt
git commit -m "fix: publish rtklib solutions to best solution path"
```

## Task 3: Strengthen RTKLIB Worker Output Retention Tests

**Files:**
- Modify: `core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibWorkerTest.kt`

- [ ] **Step 1: Extend existing worker output test**

In `RtklibWorkerTest.kt`, in `worker writes backend NMEA and POS output separately`, change the fake backend output to include a solution:

```kotlin
            output = RtklibNativeOutputBatch(
                nmeaLines = listOf("\$GPGGA,fixture"),
                posLines = listOf("%  GPST latitude(deg) longitude(deg)", "2026/01/01 1.0 2.0"),
                solution = RtklibSolutionSnapshot(
                    fixClass = RtklibFixClass.RTK_FIXED,
                    timestampMillis = 1_234L,
                    latDeg = 50.1,
                    lonDeg = 14.2,
                    ellipsoidalHeightM = 300.0,
                    horizontalAccuracyM = 0.01,
                    verticalAccuracyM = 0.02,
                    satellitesUsed = 12,
                ),
            ),
```

After existing output assertions and before `worker.stop()` or immediately after `backend.await()`, capture the snapshot before stop:

```kotlin
        val snapshot = worker.snapshot()
        assertEquals(RtklibFixClass.RTK_FIXED, snapshot.latestSolution?.fixClass)
        assertEquals(50.1, snapshot.latestSolution?.latDeg)
        assertEquals(14.2, snapshot.latestSolution?.lonDeg)
        assertEquals(0.01, snapshot.latestSolution?.horizontalAccuracyM)
```

If the current test stops the worker before checking snapshot, move `worker.stop()` after the snapshot assertions.

- [ ] **Step 2: Run RTKLIB worker tests**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :core:rtklib:test --tests org.rtkcollector.core.rtklib.RtklibWorkerTest
```

Expected: tests pass. If they fail because latest solution is lost after writing outputs, fix `RtklibWorker.runLoop()` so `latestSolution = batch.solution ?: latestSolution` remains inside the successful write path.

- [ ] **Step 3: Commit checkpoint**

Run:

```bash
git add core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibWorkerTest.kt
git commit -m "test: retain rtklib latest solution with outputs"
```

## Task 4: Add Session Replay/Output Consistency Checker

**Files:**
- Create: `tools/rtklib_replay_session_check.py`

- [ ] **Step 1: Create the checker**

Create `tools/rtklib_replay_session_check.py`:

```python
#!/usr/bin/env python3
"""Check RTKLIB artifacts inside an RtkCollector session ZIP.

This is not a native RTKLIB solver. It verifies that a session which claims
RTKLIB was enabled contains coherent RTKLIB output/status artifacts. It is a
fast regression guard for the publication/output gates and complements native
replay tests on hosts where the RTKLIB shared library can run.
"""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path


RTKLIB_STATUS = "rtklib-status.jsonl"
RTKLIB_NMEA = "rtklib-solution.nmea"
RTKLIB_POS = "rtklib-solution.pos"
SESSION_JSON = "session.json"


@dataclass(frozen=True)
class CheckResult:
    errors: list[str]
    warnings: list[str]

    @property
    def ok(self) -> bool:
        return not self.errors


def read_text(zip_file: zipfile.ZipFile, name: str) -> str:
    try:
        with zip_file.open(name) as handle:
            return handle.read().decode("utf-8", errors="replace")
    except KeyError:
        return ""


def session_rtklib_enabled(session_json: str) -> bool:
    if not session_json.strip():
        return False
    data = json.loads(session_json)
    return bool(data.get("rtklibEnabled", False))


def parse_status_lines(text: str) -> list[dict]:
    rows: list[dict] = []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        rows.append(json.loads(line))
    return rows


def has_solution_status(rows: list[dict]) -> bool:
    for row in rows:
        fix = row.get("fix")
        lat = row.get("latDeg")
        lon = row.get("lonDeg")
        if fix and fix not in {"NONE", "INVALID"} and lat is not None and lon is not None:
            return True
    return False


def check_session(path: Path) -> CheckResult:
    errors: list[str] = []
    warnings: list[str] = []
    with zipfile.ZipFile(path) as zip_file:
        names = set(zip_file.namelist())
        session_json = read_text(zip_file, SESSION_JSON)
        enabled = session_rtklib_enabled(session_json)
        if not enabled:
            warnings.append("RTKLIB is not enabled in session metadata.")
            return CheckResult(errors, warnings)

        for required in (RTKLIB_STATUS, RTKLIB_NMEA, RTKLIB_POS):
            if required not in names:
                errors.append(f"Missing {required}")

        status_rows = parse_status_lines(read_text(zip_file, RTKLIB_STATUS))
        nmea = read_text(zip_file, RTKLIB_NMEA)
        pos = read_text(zip_file, RTKLIB_POS)

        if not status_rows:
            errors.append("RTKLIB enabled session has empty rtklib-status.jsonl")
        if has_solution_status(status_rows):
            if not any(line.startswith("$") for line in nmea.splitlines()):
                errors.append("RTKLIB status has a solution but rtklib-solution.nmea has no NMEA sentences")
            if not any(line and not line.startswith("%") for line in pos.splitlines()):
                errors.append("RTKLIB status has a solution but rtklib-solution.pos has no solution rows")
        else:
            warnings.append("No valid RTKLIB solution found in status artifact.")

    return CheckResult(errors, warnings)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("session_zip", type=Path)
    args = parser.parse_args(argv)
    result = check_session(args.session_zip)
    for warning in result.warnings:
        print(f"warning: {warning}", file=sys.stderr)
    for error in result.errors:
        print(f"error: {error}", file=sys.stderr)
    return 0 if result.ok else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
```

- [ ] **Step 2: Run checker on the M8T debug session**

Run against the latest M8T RTKLIB debug ZIP if present:

```bash
python3 tools/rtklib_replay_session_check.py samples/debug/session-2026-06-22T10-36-23.537047Z-840c434b-47cd-41cd-afb3-ed6015576885-1782128440548.zip
```

Expected before the output/publishing fix may be failure or warnings, depending on the exact artifact contents. The command must not modify `samples/`.

- [ ] **Step 3: Add executable bit if supported**

Run:

```bash
chmod +x tools/rtklib_replay_session_check.py
```

Expected: script remains in git as a normal executable helper on file systems that support mode bits. If Android shared storage ignores mode bits, this is acceptable; the script remains runnable with `python3`.

- [ ] **Step 4: Commit checkpoint**

Run:

```bash
git add tools/rtklib_replay_session_check.py
git commit -m "test: check rtklib session output artifacts"
```

## Task 5: Update Formal Verification And Plan Status

**Files:**
- Modify: `docs/specification/verification-matrix.md`
- Modify: `docs/superpowers/plan-status.md`

- [ ] **Step 1: Update verification matrix**

In `docs/specification/verification-matrix.md`, add or extend RTKLIB verification rows so they name these checks:

```markdown
- RTKLIB snapshot publication: `RtklibSolutionCandidateMapperTest` verifies
  RTKLIB fix/coordinate snapshots become `SolutionCandidate(engine=RTKLIB_REALTIME)`.
- RTKLIB mock policy: `BestSolutionTickLogicTest` verifies `RTKLIB_ONLY` uses
  only fresh RTKLIB candidates and does not fall back to receiver-internal fixes.
- RTKLIB session outputs: `tools/rtklib_replay_session_check.py` verifies
  enabled RTKLIB sessions contain coherent status, NMEA and POS artifacts.
```

Keep any native `.so` build or real hardware validation status as `Implemented, not field-tested` or `In progress` unless it has actually been run.

- [ ] **Step 2: Update plan status**

In `docs/superpowers/plan-status.md`, update the RTKLIB section with:

```markdown
RTKLIB calculation can surface on the dashboard, but the active 2026-06-22
follow-up verifies the complete publication path: RTKLIB snapshot, session
NMEA/POS/status artifacts, `SolutionCandidate(engine=RTKLIB_REALTIME)`,
dashboard best-solution selection and Android mock GPS publishing.
```

Use the repository taxonomy exactly: `Done`, `Implemented, not field-tested`, `In progress`, or `Open`.

- [ ] **Step 3: Verify terminology**

Run:

```bash
rg -n "RTKLIB\\+|RTKGPS\\+|RtkGps|RTK GPS" . \
  -g '!samples/**' -g '!build/**' -g '!**/.gradle/**'
```

Expected: Android app references say `RTKGPS+`; no stale `RTKLIB+` Android-app wording remains.

- [ ] **Step 4: Commit checkpoint**

Run:

```bash
git add docs/specification/verification-matrix.md docs/superpowers/plan-status.md
git commit -m "docs: track rtklib output publication verification"
```

## Task 6: Full Verification And Final Review

**Files:**
- Review all changed files from Tasks 1-5.

- [ ] **Step 1: Run whitespace check**

Run:

```bash
git diff --check HEAD~5..HEAD
```

Expected: no whitespace errors.

- [ ] **Step 2: Run Kotlin compile**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:compileDebugKotlin
```

Expected: compile passes.

- [ ] **Step 3: Run focused JVM tests**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :core:rtklib:test :core:solution:test
```

Expected: tests pass.

- [ ] **Step 4: Run app unit tests if environment supports them**

Run:

```bash
sh gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest --tests org.rtkcollector.app.recording.RtklibSolutionCandidateMapperTest --tests org.rtkcollector.app.recording.BestSolutionTickLogicTest
```

Expected on a host with runnable Android unit-test tooling: tests pass. In Termux/aarch64, if the task fails before running tests because of local `aapt2` or Android test tooling limitations, document that limitation and rely on `:app:compileDebugKotlin` plus core tests locally.

- [ ] **Step 5: Run RTKLIB session checker when sample exists**

Run:

```bash
python3 tools/rtklib_replay_session_check.py samples/debug/session-2026-06-22T10-36-23.537047Z-840c434b-47cd-41cd-afb3-ed6015576885-1782128440548.zip
```

Expected: after implementation and on a session where RTKLIB produced a valid status solution, the checker passes. If the sample still contains old broken output, record the failure as historical evidence and rerun on a fresh session after field testing.

- [ ] **Step 6: Request code review**

Use `superpowers:requesting-code-review` and ask the reviewer to focus on:

- raw recording isolation;
- RTKLIB lazy/advisory behaviour;
- no fake RTKLIB solution fallback;
- mock policy correctness;
- output artifact consistency.

- [ ] **Step 7: Final commit or review-and-commit**

If all changes are already committed by task checkpoints, create no extra commit unless review fixes are needed. If the user asked for `$review-and-commit`, run that skill before pushing.
