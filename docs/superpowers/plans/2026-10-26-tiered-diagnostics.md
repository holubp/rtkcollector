# Tiered Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Always retain essential session diagnostics independently of optional,
persistent Verbose/Debug logging and its configurable 10 MB initial cap.

**Architecture:** Essential lifecycle events go through a dedicated bounded
writer into session `events.jsonl`, with an app-private Minimal journal fallback.
Optional app logs use independent queues and rotation. Service-owned stop
provenance and finalization are observable without changing raw capture ownership.

**Tech Stack:** Existing Kotlin/JVM core modules, Android foreground service,
SharedPreferences, Compose, JSONL and ZIP; no new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-26-tiered-diagnostics-design.md`

## Global Constraints

- Initial optional cap: 10 MB = 10,000,000 bytes; menu choices 5/10/25/50 MB.
- Optional age retention: 1/7/30 days, initially 7; enforce size and age limits.
- Minimal always enabled; levels cumulative; choice and cap persist on restart.
- Essential app journal: 1 MB; session events retained with the session.
- No logging I/O, JSON construction, export, pruning or waits on raw capture.
- No credentials, raw stream duplication or routine precise position dumps.
- Performance monitoring remains independent. No native/receiver changes.
- Do not modify unrelated dirty root work or the two untracked ownership drafts.
- Treat approval and evidence honestly; no inherited release-gate exemption.

## Review Focus

- A full optional queue cannot suppress the initiating stop reason (Task 3).
- A deferred finalizer cannot close events before their writes finish (Task 4).
- Activity recreation cannot mark a still-running session interrupted (Task 4).
- Rotation/deletion while exporting cannot produce corrupt ZIP content (Task 2).
- A lower cap while recording cannot cause raw capture I/O or a restart (Task 5).

## Current Evidence And Execution Ledger

- Release decision 2026-10-26, human:holubp: approved both explicitly enumerated
  RC8 exceptions after the final local checkpoint: (1) the isolated diagnostic
  tail/new-recording behavior, now reflected in ARCH-WRITER-002; (2) use of the
  checked-in pre-push gate plus clean-host GitHub CI for RC8, leaving integration
  of the separate strict-assurance framework for later. This is RC8-specific,
  not a passing strict-checker receipt or a waiver of ordinary tests/review.
  Earlier pending/blocker entries below are historical and superseded by this
  decision. Implementation is locally verified; CI/release publication follow.

- Base worktree: `.worktrees/ntrip-tls-integration`, branch
  `codex/ntrip-tls-integration`; existing unrelated drafts remain untracked.
- Design approved 2026-10-26, including explicit user-settable initial 10 MB cap.
- Fresh design builder review completed; no consequential planning questions.
- Existing boolean runtime toggle defaults false; shared optional queue capacity
  is 16, and store trimming rewrites retained text. Session metadata has no
  structured stop summary. These are implementation observations, not causes of
  the unexplained historical stop.
- Strict checker `tools/specification/spec_check.py` is absent in this worktree.
  Reconcile the required assurance gate without copying unrelated root drafts
  or assuming old release-specific waivers. Block push if unresolved.
- Plan approved by human:holubp on 2026-10-26. Native execution started.
- Task 1: formal contract updated; policy and storage tests written before code.
  Baseline production Kotlin compilation passed. Missing-API RED compilation
  observed, followed by a passing initial focused suite before service wiring.
- Tasks 2-5: implementation present; integrated verification in progress.
  Current local evidence is under ignored `.superpowers/sdd/2026-10-26-tiered-diagnostics/`.
  `red-final-storage.log`: 25 tests, four failures (SOURCE redaction, Minimal ZIP
  evidence, interrupted drain, oversized task failure stack). These were corrected;
  `green-focused-2.log` is the current rerun after fixing a remaining renamed caller.
- Independent whole-delta review requested from `gpt-6.1-sol` / `high` for
  shutdown ownership and data-integrity risk. Requested routing recorded; runtime
  model/effort telemetry unavailable. Reviewer is read-only; no extra workers.
- Focused integrated rerun `green-focused-2.log` passed: 26 app tests, 32 core
  session tests and 24 core capture tests, zero failures. Production app Kotlin
  and all app JVM-test sources compiled. Robolectric persistence test is compiled
  locally but requires clean-host CI execution. This is not the full pre-push gate.
- Ruling: checkpoint commits wait for the coupled app API to compile and focused
  tests to pass; do not commit an intentionally non-compiling intermediate state.
- Ruling: preserve essential/performance retention lifetime; optional age controls
  do not introduce a new age-expiry policy for those independent stores.
- Ruling: use decimal MB consistently (10,000,000 bytes initial), matching the
  operator's explicit 10 MB instruction rather than binary MiB.
- Execution recommendation: native implementation for tightly coupled lifecycle
  integration; at most one bounded Luna worker if independently useful, followed
  by one independent Sol review. No automatic Astra or repeated context forks.
- Update this ledger after each task: actual files, test commands/results,
  review findings, outstanding gates and manual validation.

## File Ownership And Interfaces

Review correction ledger (2026-10-26): eight substantive findings require repair:
unbounded event drain; premature success metadata; synchronous task-error I/O;
unguarded executor/thread submissions; structured-secret redaction; share lifetime
coordination; missing shutdown/NTRIP transitions; missing durable recovery assessment.

Proposed policy, pending explicit human confirmation against ARCH-WRITER-002:
split non-event writer close from event close. A diagnostic worker owns
its event stream, with a retained session-writer lease until it exits. The service
waits only 150 ms for essential finalization, then finalizes raw/other artifacts
and recording lifecycle independently. Late evidence remains conservatively
incomplete; old-session maintenance is blocked by the writer lease, but starting
a new session is not. Write successful metadata only after actual close outcomes.
This replaces the indefinite event-finalizer loop and preserves the raw-first
contract, but the new-session exception changes the existing normative barrier.
Do not claim compliance or publish until that exception is approved or removed.

Resume checkpoint: latest focused verification is running in `resume-focused.log`
after correcting the missing MainActivity BuildConfig import. Legacy-log export
now sanitizes bounded JSONL records without rewriting original logs and rejects
sharing before snapshot allocation when free space is insufficient. Previous
export boundary runs failed compilation and are not passing evidence. The human's
short approval is being clarified for the two specific outstanding release and
writer-barrier decisions; no blanket waiver is inferred.

Verification checkpoint: `resume-focused.log` passed after the BuildConfig import
correction. Independent reviewer found one remaining startup-rejection ordering
defect: event cleanup was scheduled before producers stopped. Strengthening
`SessionDiagnosticsIsolationTest` reproduced it in `full-gate-final.log`: 810
app tests, one failure (the new ordering assertion); that is RED evidence, not a
passing full gate. Cleanup now schedules once at `seal()`, and closed is signalled
only after the close attempt/lease release. `owner-seal-green.log` is the focused
rerun; all final full-gate evidence must be regenerated after this correction.

`owner-seal-green.log` passed. The same independent reviewer cleared the focused
startup-rejection correction: pre-seal writes remain possible, duplicate sealing
schedules once, closure releases the lease before completion, rejected cleanup
retains protection. No remaining blocking code finding was reported within that
review scope. Final checked-in gate is running in `full-gate-owner-fixed.log`;
strict-gate exception and ARCH-WRITER-002 policy confirmation remain pending.

Final local checkpoint (2026-10-26): `full-gate-owner-fixed.log` completed with
`Pre-push checks passed`. XML results contain 1,245 tests in 171 suites, zero
failures/errors/skips across app Termux and core/receiver suites. Production
Kotlin and all app JVM-test sources compiled; Android Studio unitTestClasses and
androidTestClasses aliases dry-ran successfully. Python gate/signing tests and
the checked-in formal requirement checker passed. This is not evidence of the
absent strict checker, clean-host Robolectric execution, native packaging or
Android/device visual validation. `git diff --check HEAD` passed.

Tasks 1-5: code and operator documentation implemented, locally verified as
above; persistence Robolectric execution and device checks remain unexecuted.
Task 6: independent review findings repaired and targeted regression verified;
publication blocked on explicit strict-gate release exception and diagnostic-tail
policy confirmation. No RC8 commit/tag/push/release has been published. Next safe
action: reconcile the human decisions, update ARCH-WRITER-002 if approved (or
restore its strict new-start barrier), rerun invalidated checks, commit scoped
changes and use clean-host GitHub CI before stable-key APK publication.

Ruling: exported ZIPs use a shared, cross-instance lock and one-hour recipient
grace. Cleanup removes only expired copies; three copies bound private share
cache use. This avoids deleting an in-flight/just-returned share across Activity
recreation. Operator documentation describes the grace and bounded-copy limit.

Existing app files live beneath `app/src/main/kotlin/org/rtkcollector/app/`;
tests use the corresponding `app/src/test/kotlin/` package.

- `diagnostics/DiagnosticsModels.kt`, `DiagnosticsSettings.kt`: levels, retention,
  migration and status. Add `DiagnosticLevel { MINIMAL, VERBOSE, DEBUG }` and
  `DiagnosticsRetention(maxOptionalBytes: Long, maxOptionalAgeDays: Int)`.
- `diagnostics/DiagnosticsStore.kt`, `DiagnosticsZipExporter.kt`: bounded files,
  retention, stable export snapshots and essential app journal.
- `diagnostics/RuntimeDiagnostics.kt`: optional filtering and drop accounting.
  Add `SessionDiagnostics.kt` for independent essential event scheduling/draining.
- Add `recording/RecordingStopContext.kt`: stop initiator/correlation/secondary
  causes. Integrate through `RecordingForegroundService.kt` and
  `RecordingSessionWriters.kt`, not Activity capture ownership.
- `core/session/.../SessionModels.kt`, metadata serialization and existing tests:
  additive nullable stop summary, preserving old-session readability.
- `ui/diagnostics/AppDiagnosticsScreen.kt`, `ui/MainActivity.kt`: controls,
  worker-dispatched maintenance/export and compact logging advisory.
- Formal specs/matrix, session format, architecture, operator documentation and
  shared plan status: approvals, contract and evidence alignment.

### Task 1: Formal Contract, Settings And Migration

**Produces:** `DiagnosticsSettings.level: DiagnosticLevel`,
`retention: DiagnosticsRetention`; a normalized policy for every store instance.

- [ ] Update `ANDROID-DIAGNOSTICS-001` to distinguish always-on Minimal from
  disabled optional detail; add stable requirements for lifecycle evidence,
  retention, UI, privacy and recovery. Update verification matrix as unverified.
- [ ] Write settings/model tests: fresh install MINIMAL, 10,000,000 bytes, seven
  days; legacy false -> MINIMAL and true -> VERBOSE; explicit new level wins;
  cap/age/level survive a fresh settings instance; performance unchanged.
- [ ] Run new tests and confirm expected failures before implementation.
- [ ] Implement level/policy and persistence; invalid saved values normalize to
  documented safe defaults, never implicitly DEBUG. Preserve compatibility only
  where existing callers need a transition; do not leave conflicting authorities.
- [ ] Run targeted tests; inspect all old runtime-toggle call sites. Record
  evidence and make a scoped checkpoint commit after review.

### Task 2: Rotation, Maintenance And Stable Export

**Consumes:** Task 1 level/retention. **Produces:** store methods for essential
append, optional append, `applyRetention(policy)`, status and stable export
snapshot. Public store calls perform I/O only from diagnostics/maintenance workers.

- [ ] Write `DiagnosticsStoreTest` and `DiagnosticsZipExporterTest` cases for
  aggregate byte bounds including active files, UTF-8 byte sizes, oversized
  events, age expiry with injected clock, cap reduction and legacy file discovery.
  Exercise concurrent rotate/delete/export with independent ZIP readability checks.
- [ ] Confirm failures, then replace text-tail rewrites with bounded rotation;
  enforce separate essential 1,000,000-byte journal and optional policy. Bound
  event size and preserve complete JSONL records. Retention checks on append and
  startup; cap edits schedule pruning without capture-thread I/O.
- [ ] Implement generation-stable streaming snapshots; a store lock must not
  span arbitrary ZIP streaming or raw recording. Cleanup temporary snapshots on
  success/failure and prevent deletion races or stale sharing paths.
- [ ] Include essential journal, optional files, app/schema/level and diagnostic
  failure/drop counters in app-log exports; keep performance export independent.
- [ ] Prove delete affects only diagnostic generations, never sessions or level;
  failure injection leaves recording independent. Run targeted tests and record
  exact results before checkpoint commit.

### Task 3: Level Filtering And Essential Event Delivery

**Produces:** `SessionDiagnostics.record(event)`, `drain(timeoutMillis): Boolean`
and `close()` with explicit outcomes; optional `RuntimeDiagnostics.record(record,
minimumLevel)` defaults to VERBOSE. Queued records capture their level at emission.

- [ ] Add tests with injected writers/executors for cumulative filtering, live
  level changes, queue saturation, write failure, no I/O/serialization on caller
  thread, repeated-event coalescing, observable drops and reserved lifecycle slots.
  An exhausted optional queue must still admit `stop-requested` essential events.
- [ ] Confirm failures, implement independent bounded essential and optional
  scheduling, lifecycle reserved capacity, bounded messages and aggregate counters.
  Do not reuse the current shared optional executor as an essential dependency.
- [ ] Add versioned structured essential event schema: stable code, severity,
  session ID, wall clock, process-monotonic time and correlation. Sanitize an
  allowlisted attribute schema; prevent secret-bearing arbitrary keys/messages.
- [ ] Test redaction against credentials in exceptions/attributes/commands at
  every level. Audit existing call sites; classify actionable state transitions
  Minimal and summary/detail Verbose/Debug, rather than only renaming the toggle.
- [ ] Verify direct-write test hooks cannot be enabled in production capture.
  Run targeted tests; record outcomes and checkpoint.

### Task 4: Stop Provenance, Finalization And Recovery

**Consumes:** Task 3 essential delivery. **Produces:** service stop provenance and
nullable metadata summary; normal user stops are not errors.

- [ ] Write `RecordingStopContextTest` for first-cause preservation, unique
  correlation, additional failure causes and idempotent duplicate requests.
  Add service tests for ACTION_STOP, startup failure, fatal storage failure,
  onDestroy, deferred drain and diagnostic write failure.
- [ ] Confirm failures. Change internal stop entry points to explicit reason
  codes while preserving existing intent/UI API. Emit creation/start/failure,
  accepted stop, significant shutdown progress and final outcome events outside
  capture loops; use the essential app journal when session writers unavailable.
- [ ] Extend metadata serialization additively. Drain essential writes after
  producers release writers, before event writer close. Track failed evidence
  writes explicitly; no fabricated success summary. Do not hold runtime locks
  while waiting on diagnostics, and never wait indefinitely for optional logs.
- [ ] Add session tests for unknown legacy fields/absent summaries and a session
  ZIP with nonempty Minimal events despite optional logging disabled.
- [ ] Implement recovery assessment against the active session registry/service:
  incomplete sessions without final evidence remain cause unknown; no raw file
  repair or fabricated stop time. Tests cover Activity recreation, active service,
  process-restart stale session and available verified finalization evidence.
- [ ] Test a failing/blocked event sink while raw RX remains byte-identical and
  capture progresses; test no writes after final event-writer closure. Run app,
  core session/capture tests, record evidence and checkpoint.

### Task 5: Logging Settings And Main-Screen Advisory

**Consumes:** level/policy/status and worker-dispatched export/maintenance APIs.

- [ ] Add UI/model tests for Minimal/Verbose/Debug, cap choices 5/10/25/50 MB,
  initial 10 MB, age 1/7/30 days, separate usage counters, restart persistence
  and performance independence. Assert a lower cap schedules worker pruning,
  not a recording restart or main/capture-thread I/O.
- [ ] Replace runtime boolean UI with level and retention selectors; display
  essential/optional/performance storage separately. Explain optional app logs
  span sessions and session deletion is separate. Keep built-in app controls.
- [ ] Show `Verbose logging enabled` / `Debug logging enabled` below setup and
  above cards, including idle; link to diagnostics. No Minimal advisory, modal
  loop or substitution of real errors. Test coexistence with operational errors.
- [ ] Apply level edits live and record a Minimal transition, even when another
  component changes the preference. Profile application/import cannot reset it.
- [ ] Move maintenance/export/status scanning off Compose/main/capture loops;
  report failures and keep existing share actions usable with Minimal evidence.
- [ ] Run tests/compilation and verify compact layout, light/dark and portrait/
  landscape on an Android host; mark unavailable visual evidence honestly.
  Update operator docs and checkpoint.

### Task 6: Integration, Independent Review And Completion Audit

- [ ] Update `docs/session-format.md`, `docs/architecture.md`,
  `docs/android-background-operation.md`, relevant user docs and
  `docs/superpowers/plan-status.md`. Link superseded historical logging policy
  without rewriting historical approval/evidence. Align formal matrix to results.
- [ ] Review whole delta for raw-path dependencies, races, missing stop callers,
  sensitive data, disabled tests, resource leaks and misleading durability claims.
- [ ] Run targeted app tests via supported Termux test task, then
  `sh gradlew :core:session:test :core:capture:test :app:compileDebugKotlin` and
  `sh scripts/pre_push_check.sh`. Confirm actual module task names before use;
  preserve Android Studio test aliases. Record full output location and results.
- [ ] Resolve strict specification gate absence explicitly before any push; do
  not silently copy unapproved root assurance work or expand release exceptions.
- [ ] Obtain one independent Sol review focused on the approved design and whole
  delta, high effort warranted for shutdown/data-integrity races. Fix findings
  and rerun invalidated checks. Respect maximum three children including prior
  design reviewer; no redundant blanket second audit.
- [ ] Run clean-host CI JVM/Robolectric/native packaging gates when authorized
  push is possible. Full local APK build is not expected to work on Termux.
- [ ] Record manual high-rate UM980, background/USB recovery, termination/restart,
  export while recording and repeated start/stop results separately from tests.
  Do not claim field-tested or fully verified if required evidence is absent.
- [ ] Reconcile each design requirement with code/tests/manual evidence, finalize
  scoped commits and report remaining genuine gates. Do not mark goal complete
  with unresolved required criteria.
