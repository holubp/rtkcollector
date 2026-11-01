# P30 Pro Recording Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate main-thread receiver initialization and cancellation-induced
raw-tail loss, while retaining useful bounded failure evidence on Android 10.

**Architecture:** Keep the foreground service as owner, with a generation-fenced
startup task and cooperative cancellation. Extend the existing shutdown barrier
to include startup; use interruption-safe app-private append streams and the
existing essential diagnostics, without adding a raw-writing queue.

**Tech Stack:** Kotlin/JVM, Android service/USB/SAF, Java IO, JUnit 5, existing
Robolectric service tests, Gradle and GitHub Actions. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-11-01-p30pro-recording-safety-design.md`.

**State:** Design and plan approved by human:holubp on 2026-11-01; executing.
Decision reference: `2026-11-01-p30pro-recording-safety-plan-approval`.
The subsequent user instruction authorizes the next RC release after verification.
Comparison base: `e6221f2e9aa568464f16158a3d17a321af537674`.
Branch: `codex/p30pro-recording-safety` in the existing isolated integration
worktree; the dirty root worktree is out of scope.

## Execution Checkpoint

2026-11-01: production changes are implemented and the fresh Termux clean-checkout
gate passes app JVM compilation, feasible app/module tests and IDE task aliases.
Startup/token, old-Android raw-tail and bounded diagnostic regressions have
recorded RED/GREEN evidence in the ignored task ledger. Ten Robolectric classes
are compile-checked locally, not executed. Independent Sol-high whole-delta and
additional explicitly requested Astra-high safety reviews are active; their
findings, complete host execution, final full gate and publication remain open.
The owner explicitly requires RC11 to be actually published without further
human approval checkpoints; scope remains the previously approved design/plan.
Canonical authorization follows that narrow instruction and never substitutes
for test, review, signing or real-device evidence.

## Global Constraints

- Keep existing receiver, workflow, alarm, NTRIP, storage and recording behavior
  except for correcting these failures.
- No queue is added to move raw writes away from capture.
- Main-thread admission may use short in-memory locks but performs no USB/file
  IO, command sleeps, baud-drain loops or worker joins.
- A timed wait is not proof of exit; retain STOPPING, writers and actionable
  progress until the owner actually exits.
- Do not change metadata atomic replacement or SAF URI selection semantics.
- Keep existing bounded raw-only periodic flushing; do not flush advisory
  sidecars from capture.
- Android 10 remains supported. No new operator setting is needed to make
  recording safe.
- Captures remain local, uncommitted. RC10 must not be retagged or replaced.
  The subsequent approved delivery change authorizes RC11 with a stable-signed
  GitHub-built APK after successful checks and artifact verification.

## Review-Driven Repair Checkpoint

Independent Sol and the subsequently user-requested Astra review identified
additional in-scope safety work. The owner explicitly requested correction of
anything affecting recording safety/reliability before RC11, without another
human approval checkpoint. Preserve the original contract while completing:

The final review additionally requires preserving active raw-FATAL state through
sidecar/shutdown failures, retaining final metadata-write identity, and retaining
USB acquisition/close ownership until actual retirement. In-flight raw append,
flush and NTRIP correction-writer tests use the real service Stop barriers and
existing fake-client seam. These are in-scope repairs, not new operator features.

- cleanup-only retention of late opened writers and their close outcomes;
- genuine post-cancel storage/open/partial-cleanup failure identity and outcome;
- actual USB/SAF/baud/drain/destruction/mock/maintenance race regressions using
  platform shadows, not a production test endpoint;
- exclusive process-wide recording/idle-maintenance connection admission;
- removal of redundant USB reopening and fenced mock-provider acquisition;
- independent event cleanup when destruction rejects its normal executor;
- non-interrupting advisory/upload callbacks, cancellation-aware retry waits and
  protection against stale test-delay interrupts reaching a later writer.

The bounded advisory/uploader patch was implemented by Luna/medium in an isolated
worktree; integration, additional race tests, service/UI repairs and final quality
remain controller-owned. Reuse the same Sol and Astra reviewers for affected
re-review. Refresh mappings/evidence/semantic scope after repairs, then run full
local/host gates and publish the verified stable-signed APK. Partial repair test
results do not complete the plan or authorize skipping host execution.

## Review Focus

1. Stop while a file/USB open has not returned: retain ownership, then retire
   the late resource without reviving startup (Task 1).
2. Stop between startup completion and queued main-thread publication: suppress
   old RECORDING state, capture/NTRIP launches and alarm changes (Task 1).
3. Stop during raw append/flush, correction write or receiver maintenance: exact
   accepted bytes survive and no writer closes before its users exit (Task 2).
4. Failure of worker launch, cancellation-close or diagnostics: keep truthful
   lifecycle/evidence and retain resources requiring cleanup (Tasks 1 and 3).
5. Android 10 channel behavior differs from the host JDK: include an explicit
   interruptible-channel negative control and real file byte comparisons
   rather than a mock flush call (Task 2).

## Files And Interfaces

New production files:
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingCancellation.kt`:
  one session's cooperative cancel/wake token.
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingStartupOwner.kt`:
  startup generation, resource/state publication fencing and termination barrier.
- `app/src/main/kotlin/org/rtkcollector/app/diagnostics/FailureDiagnostics.kt`:
  bounded cause-class extraction and a process-lifetime correlation identity.

Existing production files touched:
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`:
  responsive admission, startup integration, cancellation, Stop and stage evidence.
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingSessionWriters.kt`:
  matching SAF failure-detail reporting without changing URI/provider semantics.
- `app/src/main/kotlin/org/rtkcollector/app/diagnostics/DiagnosticsModels.kt` and
  `SessionDiagnostics.kt`: allowlisted structured evidence fields.
- `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`: attach process
  correlation to the existing UI-attachment record, not new UI behavior.
- `core/session/src/main/kotlin/org/rtkcollector/core/session/SessionWriters.kt`
  and `SessionWriterLifecycle.kt`: interruption-safe append and failure identity.
- `core/capture/src/main/kotlin/org/rtkcollector/core/capture/CaptureContracts.kt`
  and `CaptureRuntime.kt`: retain bounded recorder/transport-close error identity
  in advisory events instead of losing message-less exception information.

### Task 0: Establish The Approved Contract And Baseline

**Files:** canonical `docs/specification/android-runtime.md`,
`functional-requirements.md`, `capability-map.json`, `requirement-approvals.json`,
`evidence-receipts.json`, `verification-matrix.md`, `source-ledger.md` and the
existing shared plan tracker. Use the installed specification-updater process.

- [x] Record the user's design decision reference and all three proposed atomic
  requirements: ANDROID-SERVICE-003, ARCH-WRITER-003 and
  ANDROID-DIAGNOSTICS-005, copying their normative intent from the approved design.
- [x] Declare the exact new files above, their active capabilities and mappings;
  include startup in ARCH-WRITER-002's implementation coverage. Prepare exact
  canonical requirement and inventory digests for human approval; do not treat
  design approval as approval of unseen future digests. Obtain any approval
  required by the checker before publication; do not create approvals yourself.
- [x] Give each new requirement AUTO/REVIEW/MANUAL obligations; start unexecuted
  obligations as `not-run`. Identify affected existing receipts and supporting
  docs; do not copy stale passing evidence.
- [x] Confirm clean source baseline and rerun targeted existing ownership,
  session-writer and diagnostic tests before introducing regression tests.
  Checkpoint any pre-existing failure rather than attributing it to this change.

**Checkpoint:** decision, base, paths, affected IDs, invalidated receipts and
approval state recorded; baseline test command/result retained.

### Task 1: Responsive, Cancelable Service-Owned Startup

**Tests:** create
`app/src/test/kotlin/org/rtkcollector/app/recording/RecordingStartupOwnerTest.kt`
and `RecordingCancellationTest.kt`; extend existing `ReceiverRecoveryOwnershipTest.kt`,
`RecordingAlarmServiceTest.kt` and `Um980BaudTransitionTest.kt`.

**Interfaces:**
- `RecordingCancellation.isCancelled: Boolean`, `cancel(): Unit`,
  `awaitDelay(delayMillis: Long): Boolean`: wake on cancellation without
  interrupting the thread; true means the delay completed while still active.
- `RecordingStartupLease(sessionId: String, generation: Long,
  cancellation: RecordingCancellation)` is immutable session identity.
- `RecordingStartupOwner.start(sessionId: String,
  initialize: (RecordingStartupLease) -> Unit,
  failed: (RecordingStartupLease, Throwable) -> Unit): Boolean` admits one job
  before submitting it to its injectable worker launcher.
- `isCurrent(lease): Boolean`, `publish(lease, publication: () -> Unit): Boolean`,
  `cancel(): Unit`, `awaitStopped(timeoutMillis: Long): Boolean`,
  `isBusy: Boolean`. Publication locks protect short assignments only. A lease
  remains current after successful startup until cancellation; completed startup
  alone does not invalidate its queued state publication. No newer admission
  while an earlier startup worker is still alive. Service finalization remains
  an additional gate even after startup exits.
- Constructor injection is `launch: (Runnable) -> Unit`; production starts an
  owned thread and tests control admission/execution. Keep the startup owner
  accessible to the existing service-test reflection pattern; do not add an
  exported test action, configurable production callback or second start path.

- [x] Write failing owner/token tests with bounded latches. Assert blocked
  initialization does not hold the caller; duplicate Start is rejected; cancel
  wakes command/retry delays without setting thread interrupt status; cancellation
  rejects late resource/state publication; timed `awaitStopped` returns false
  until the worker actually exits; failed worker launch does not leave a phantom
  busy owner. Failure callback exceptions must not escape the worker or lose
  ownership accounting. Await timeout is never interpreted as successful Stop.
- [x] Run those tests and retain expected RED results, then implement the two
  small production helpers. Rerun for GREEN before service integration.
- [x] Extend existing Robolectric service tests using the owner's injected worker
  launcher and a valid staged setup fixture. Assert a foreground notification
  and STARTING are established while
  worker initialization is blocked; ACTION_QUERY, ACTION_STOP and onDestroy
  return without waiting; no canceled task starts capture/NTRIP or repaints state.
  Cover Stop before launch, during USB open, during storage open, after writer
  ownership and between completion/publication. Use the existing Robolectric
  class so no extra Termux exclusion is required. These tests execute in CI;
  source scanning alone is not proof of Android callback responsiveness.
- [ ] Observe integration RED on a clean host/CI. In Termux, compile these tests
  and execute the pure ownership tests; retain host execution as pending rather
  than claiming Robolectric passed locally.
- [x] Split `startRecording` into prompt main-thread admission and worker-owned
  initialization. Keep validated setup acceptance, foreground admission, alarm
  generation and service start-ID handling correct. Move profile preparation,
  storage probes/opening, USB opening, command IO and draining to the worker.
  Use the owner's injectable launcher as the service-test boundary.
- [x] Register ownership before launching the task. Stage each newly acquired
  resource locally until generation-checked publication; canceled acquisition
  closes its own resource and preserves created artifacts. Distinguish deliberate
  cancellation from START_FAILURE/STORAGE failure. No metadata/ownership rollback
  may affect a later session.
- [x] Add startup to normal/deferred Stop barriers before existing producer
  shutdown and writer finalization. Cancel it without interrupting file IO.
  Never join it while holding runtime/TX/publication locks. Partial startup must
  still clean up correctly, including failure before `writers` is published.
- [x] Keep baud order and recorded RX draining unchanged; propagate the same
  cancellation token to capture/reconnect/maintenance pauses. Do not shorten
  receiver command waits to make a responsiveness test pass.
- [x] Run owner/token/baud/USB/alarm regressions. Checkpoint RED/GREEN results,
  touched interfaces and any host-only verification still outstanding.

### Task 2: Preserve Accepted Raw Bytes Across Cancellation

**Files:** session writers/lifecycle, service cancellation sites and the existing
`SessionWritersTest.kt`, `SessionWriterLifecycleTest.kt`,
`PathRecordingSessionWritersTest.kt`, `RecordingAlarmIsolationTest.kt` and
`SessionDiagnosticsIsolationTest.kt`.

**Interfaces:** existing append/flush/close APIs remain byte-compatible.
`SessionWriterIssue` gains optional defaulted `operation: String?`,
`exceptionClass: String?` and `causeClasses: List<String>` fields; existing
constructors/callers remain compatible. Task 3 serializes them safely.

- [x] Write a real-files regression test supplying distinct RX byte blocks,
  interrupting the writer thread before/during append/flush, clearing interruption
  only after that operation, and checking exact concatenated bytes after clean
  finalization. Include small buffered writes and writes larger than the buffer.
- [x] Add an explicit negative control using
  `Channels.newOutputStream(FileChannel.open(...))` to reproduce Android 29's
  interruption-sensitive path. Assert it demonstrates the expected closed-channel
  failure and missing tail; a modern host `Files.newOutputStream` is not a valid
  negative control. Exercise the production stream factory under interruption,
  not only a replacement mock factory, so the regression cannot pass accidentally.
- [x] Run for RED, then replace app-private append streams with buffered
  `FileOutputStream(path.toFile(), true)`. Keep metadata atomic writes, append
  order, all artifact names and raw-only periodic flush semantics unchanged.
- [x] Remove unconditional capture/persistent-worker interruptions from service
  Stop; use the Task 1 cancellation signal and off-main transport cancellation.
  Do not remove unrelated cancellation of NTRIP/RTKLIB/event workers without
  checking their existing contracts. Audit every interrupt that can reach raw
  or sidecar writer code; no intentional cancellation may close those streams.
- [x] Add latch-controlled tests for Stop while raw append, a correction callback,
  startup draining and persistent maintenance use writers. A SAF-style
  interrupt-sensitive fake must remain un-interrupted. No writer closes until
  its producer relinquishes ownership; repeated Stop must not double-close or
  reopen/truncate. Reconnect still preserves the same recording artifacts.
- [x] Inject flush/close failures with null messages and assert artifact,
  operation and exception identity survive; all later artifacts still receive
  close attempts. A true raw failure must remain STORAGE/FATAL and CLOSE_FAILED.
- [x] Run targeted writer, raw durability, shutdown, USB and diagnostic-tail
  regressions. Compare exact binary output, not only byte counters. Checkpoint
  the old-channel control and production byte-preservation results separately.

### Task 3: Bounded Startup And Failure Diagnostics

**Files:** listed diagnostics/core capture files, service and existing
`RuntimeDiagnosticsTest.kt`, `SessionDiagnosticsTest.kt`, `DiagnosticsRedactionTest.kt`,
`SessionDiagnosticsIsolationTest.kt`, plus new `FailureDiagnosticsTest.kt` in
`app/src/test/kotlin/org/rtkcollector/app/diagnostics/`.

**Interfaces:**
- `failureDiagnosticAttributes(error: Throwable, artifact: String? = null,
  operation: String? = null): Map<String, String>` in `FailureDiagnostics.kt`.
  Preserve exception identity for null messages; traverse at most four causes
  with cycle protection, using existing 256-character attribute and 2048-character
  message limits. No full stack/payload dumps or unbounded traversal.
- `CaptureEvent` adds defaulted `failure: Throwable? = null`. Core capture can
  pass recorder/transport-close failures without depending on the Android app
  or on core/session. The app's SessionEventSink immediately converts this to
  bounded allowlisted strings before enqueueing; never retain Throwable or its
  stack/cause graph in the diagnostic queue. Existing positional constructors
  remain compatible.
- Runtime/session allowlists admit `exceptionClass`, `causeClasses`, `artifact`,
  `operation`, `stage`, `stageElapsedMillis`, `startupGeneration`, `processId`
  and `processInstanceId`. Process instance identity is one randomly generated
  identifier per process, not persisted user/device identity. Runtime Android PID
  acquisition stays outside pure JVM helpers and is injectable in tests.

- [x] Write failing tests for null messages, nested/cyclic causes, oversized
  values, unknown fields, secret-bearing messages and diagnostic executor/write
  failures. Minimal delivery must remain enabled and bounded independently of
  optional logging; raw finalization must not wait for diagnostic success.
- [x] Run for RED, then implement identity extraction, allowlists and event
  forwarding. Keep core/session's issue construction self-contained; it records
  the same bounded class fields without taking an Android dependency. Preserve
  existing redaction, queue/drop accounting and JSON schema
  compatibility; introduce no synchronous diagnostic disk IO on capture.
- [x] Emit bounded startup stage transitions: accepted, storage-open, receiver-open,
  init-commands, device-baud-switch, host-baud, rx-drain, mode-commands,
  capture-started, canceled and failed. Record only stages actually entered;
  do not invent a baud-switch stage for same-baud starts. Include session,
  generation, process and monotonic elapsed correlation.
- [x] Attach process correlation to existing UI-attachment records. Forward
  actual operation/artifact and exception identity from recorder close, writer
  finalization and startup failure. Do not overwrite the initiating Stop reason
  or label the confirmed ANR as a proven historical kill reason.
- [x] Run targeted diagnostics/session/capture tests and retain GREEN results.
  Checkpoint the emitted field examples with synthetic data only.

### Task 4: Integrated Assurance, Documentation And Push

- [x] Update `docs/android-background-operation.md`, `docs/architecture.md`,
  `docs/session-format.md` and `docs/user-workflows.md`: asynchronous STARTING,
  Stop cancellation/finalization, meaningful failure evidence and bounded
  process-kill loss versus deliberate Stop. Update `docs/superpowers/plan-status.md`
  to reflect actual implementation and unexecuted hardware checks.
- [ ] Reconcile exact new/changed runtime paths, requirements and supporting
  documentation through the repository checker. Obtain still-required exact
  human requirement/inventory approvals. Refresh pending digest bindings; create
  passing receipts only from executed checks with valid tracked evidence.
- [ ] Freeze writers and have one independent Sol-high reviewer audit the whole
  task delta, original design and exact regenerated assurance context. This
  effort is justified by lifecycle concurrency and raw-data loss. Fix findings
  and re-review affected paths; reuse unchanged qualified baseline evidence.
- [ ] Run full repository gates sequentially in Termux. Keep every app JVM test
  compile-checked and all feasible tests executed. Do not count Robolectric
  exclusions as passed tests. Preserve Android Studio compatibility aliases.
- [ ] Commit only scoped source/tests/docs/assurance changes after local gates.
  Push the branch, then require GitHub host CI to pass full tests including
  Robolectric service regressions, both distributions, release lint and native
  assembly. Inspect actual outcomes and fix failures; never publish a false green.
- [ ] After exact-source host CI, publish RC11 with version code 12 and a
  stable-signed GitHub-built APK. Independently download and verify checksum,
  signer, package/version and both ARM ABIs before publishing the draft release.
  Integrate into main without disturbing the dirty root worktree or RC10.
- [ ] Final report: commit/remote state, executed tests, CI/release links,
  APK source identity and remaining P30 Pro/SAF/vendor validation.

## Verification Commands

Use the existing configured SDK/JDK; invoke `sh gradlew`, with one local Gradle
compile at a time. For focused RED/GREEN module runs:

```sh
sh gradlew :core:session:test --tests '*SessionWritersTest' --tests '*SessionWriterLifecycleTest' --no-parallel
sh gradlew :core:capture:test --no-parallel
```

For app tests in Termux, first compile production Kotlin and seed the required
test R.jar using the existing `seed_termux_unit_test_r_jar` helper, then invoke
the established task (never skip compilation of tests):

```sh
sh gradlew :app:compileSideloadDebugKotlin --no-parallel
python3 -c 'import sys; from pathlib import Path; sys.path.insert(0,"tools"); from check_android_test_compilation import seed_termux_unit_test_r_jar; seed_termux_unit_test_r_jar(Path.cwd())'
sh gradlew :app:termuxTestDebugUnitTest -x :app:processSideloadDebugResources --tests '*RecordingStartupOwnerTest' --tests '*RecordingCancellationTest' --no-parallel
```

Adjust `--tests` selectors to each task's named tests. Full mandatory gate:

```sh
git diff --check
python3 tools/specification/spec_check.py --root . --full --summary-json
sh scripts/pre_push_check.sh
```

The full pre-push cleans/recompiles, checks all app test inputs and unit tests,
and dry-runs `:app:unitTestClasses :app:androidTestClasses`. No local aapt2
assembly or invented host execution. CI is authoritative for full Android tests.

## Checkpoint Ledger

| Checkpoint | State | Evidence / Next Action |
| --- | --- | --- |
| Design | Approved | human:holubp, 2026-11-01; independent builder check found no unanswered consequential questions |
| Plan | Approved | human:holubp approved the plan and subsequently requested next-RC delivery; controller implementation plus one independent final audit |
| Contract / baseline | Recorded | Three canonical requirements and inventory bound to the owner's explicit no-further-approval decision; unexecuted receipts remain not-run |
| Startup | Implemented, host verification pending | Owned worker/barrier, real USB/SAF/mock-provider service fixtures compile; final Astra startup-failure/Stop race repair included |
| Raw Stop safety | Implemented, host verification pending | Android-29 production negative control and exact raw-tail regressions pass locally; cooperative sidecar cancellation reviewed and tested |
| Diagnostics | Implemented | Bounded identities, stages, process correlation and rejected cleanup fallback; pure JVM regressions pass locally |
| Assurance / CI / push | In progress | Sol and Astra affected rereview, exact-scope attestation, full pre-push and GitHub CI/release remain mandatory |
| Field validation | Not run | P30 Pro Start/Stop at initialization phases, background USB recovery, working-storage raw tail, SAF/provider behavior |

At each checkpoint record commands/results, actual files and pending ownership
or approval issues here. Do not rerun unaffected evidence unnecessarily. Resume
by inspecting Git state and exact context, not by trusting a stale summary.
