# Recording Stop Confirmation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent accidental explicit recording Stop requests using configurable, session-bound confirmation and identify the origin of future stop requests.

**Architecture:** A pure deadline policy supplies a shared Compose dialog. The service owns a one-use confirmation authorization bound to its current recording/startup identity and elapsed-time window, and validates it before stopping. Existing confirmed base transitions and automatic cleanup remain separate typed paths; diagnostics never authorize or block capture.

**Tech Stack:** Kotlin, Compose Material 3, Android SharedPreferences, existing JVM/Robolectric tests and GitHub CI.

**Spec:** `docs/superpowers/specs/2026-11-01-recording-stop-confirmation-design.md` (approved).

## Global Constraints

- Defaults: disabled delay 2 seconds; enabled window 10 seconds beginning after the delay.
- Persist whole-second delay 1-60 and timeout 1-120; invalid values cannot be saved.
- Button countdowns update each second; Cancel is always available. Expiry, dismissal, background and recreation never send Stop.
- Active/startup owner identity and confirmation expiry must be rechecked by the service, not only Compose.
- No receiver/raw/storage/transport changes, no alarms acknowledged by opening a dialog.
- Preserve the dirty root checkout and all private captures. Work in the clean integration worktree; do not retag RC11.
- Repository assurance rules, test-compilation gate and full-host CI remain mandatory before reporting verification.

## Review Focus

1. Stop/Start between confirmation and service dispatch: an old request must not stop the new session (Task 2).
2. Startup has no artifact directory yet: confirmation must bind to service ownership, not a filename or UI boolean (Task 2).
3. Background timer delays and exact boundary taps: elapsed deadlines determine validity independently of display ticks (Tasks 1-3).
4. Recovery UI/legacy controls and base-transition bypasses: every explicit recording Stop is protected, existing deliberate transitions stay distinct (Tasks 2-3).
5. Diagnostics unavailable or preferences corrupt: raw recording continues; settings safely default and confirmation never becomes an unguarded Stop (Tasks 1-3).

## Checkpoint Ledger

Baseline: `d38686a6d75592be7b41663919029aaf1cd2e1de`, branch `codex/p30pro-recording-safety` in `.worktrees/ntrip-tls-integration`. Only the approved design and this plan are new at planning time. No product changes yet.

The supplied captures show USER/FINALIZED, not the initiating control; original touch/trigger remains unproven. Do not describe confirmation as a reproduced root-cause fix.

Execution evidence belongs here per task: changed files, RED/GREEN commands, remaining work and commit. Run Gradle sequentially. Use no children by default; one independent Sol review is warranted for the service/session boundary. If delegated, bounded policy/settings work may use Luna/medium; do not delegate tightly coupled service/UI ownership decisions.

## File Responsibilities

- New `app/src/main/kotlin/org/rtkcollector/app/recording/StopConfirmationPolicy.kt`: immutable timing values, monotonic phase/countdown, one-use owner-bound authorization policy.
- New `app/src/main/kotlin/org/rtkcollector/app/settings/StopConfirmationSettings.kt`: persisted validated timing only; do not rename/migrate existing logging preferences.
- New `app/src/main/kotlin/org/rtkcollector/app/ui/common/StopRecordingConfirmation.kt`: shared dialog host, lifecycle cancellation and timing display.
- Modify `recording/RecordingForegroundService.kt`, `recording/RecordingStopContext.kt`: authorization, typed origin, bounded essential provenance.
- Modify `ui/MainActivity.kt`, `MainActivity.kt` (legacy), `recording/RecordingNotificationRuntime.kt`: route/audit all stop call sites.
- Modify `ui/diagnostics/AppDiagnosticsScreen.kt`, `ui/settings/SettingsHub.kt`: Advanced settings label and timing fields using the existing custom text-editor component.
- Tests mirror new policy/settings/common files; extend existing `recording/RecordingAlarmServiceTest.kt` for host lifecycle coverage rather than adding a new Termux-excluded class.
- Formal requirements/verification/traceability and user docs updated in the same change; no unrelated specification overhaul.

### Task 1: Timing Policy And Persistent Settings

**Interfaces:** `StopConfirmationTiming(delaySeconds: Int = 2, timeoutSeconds: Int = 10)` validates ranges. `StopConfirmationWindow(openedAtMillis: Long, timing: StopConfirmationTiming)` exposes `phase(nowMillis: Long): StopConfirmationPhase` and `remainingSeconds(nowMillis: Long): Int`; phases WAITING, ACTIVE, EXPIRED. `StopConfirmationSettings(context: Context).timing` reads/writes the immutable timing atomically.

- [x] Add draft formal requirements for confirmation/timing and stop provenance, identify affected existing UI/runtime/diagnostics requirement IDs, and map approved design without inventing approvals.
- [x] Write failing `StopConfirmationPolicyTest`: default window WAITING at 0/1999 ms, ACTIVE at 2000/11999, EXPIRED at 12000; displayed ceiling seconds 2/1 then 10/1. Test custom extrema, rejected out-of-range/overflow input, delayed ticks and wall-clock independence.
- [x] Write settings tests for fresh defaults, persistence across instances, atomic pair save, corrupt stored values reverting safely and unchanged diagnostic preferences.
- [ ] Run targeted policy tests, record actual RED; compile-check settings tests locally and run them in host Robolectric where required.
- [ ] Implement only policy/settings contracts and rerun targeted tests GREEN.

### Task 2: Service-Owned Stop Authorization And Provenance

**Interfaces:** `StopRequestOrigin` distinguishes CONFIRMED_CONTROL, FIXED_BASE_TRANSITION and TERMINAL_ALARM. A process-local service-owned authority exposes immutable pending confirmation snapshots (request ID, current owner ID, timing/deadlines) to the shared UI; `requestConfirmation(controlId: String)`, `cancelConfirmation(requestId: String, outcome: String)` and `consumeConfirmation(requestId: String, nowMillis: Long, ownerId: String): Boolean` are serialized against stop/start ownership. Service uses its own monotonic time, not caller-supplied deadlines. Owner IDs must survive startup-to-capture promotion and never be reused.

- [ ] Write failing authorization tests: early/expired/missing/replayed token, second tap, old-session token, startup token, owner retirement and cancellation all reject appropriately; active in-window token consumes exactly once.
- [ ] Extend service tests: queue confirmed Stop for A, finish A/start B, deliver old request and prove B remains running; confirm during startup; prove opening/cancel/timeout has no producer/alarm effect.
- [ ] Add service tests for failed diagnostics and legitimate fixed-base transition; test terminal Stop alarm cannot stop an unrelated active session through a stale generation.
- [ ] Run targeted tests and record RED; introduce authority and a distinct confirmed-stop intent/action. Do not let unscoped legacy UI intents bypass authorization. Migrate every internal caller deliberately; preserve generation guards on terminal alarms and immediate automatic failure cleanup.
- [x] Add bounded request-origin/control ID to accepted stop evidence and confirmation outcome events. Retain existing USER initiator/contributors/correlation semantics and avoid secrets/coordinates.
- [ ] Run policy/authority/stop-context tests GREEN plus existing startup, raw finalization and alarm regressions; host-only service cases remain explicit until CI executes them.

### Task 3: Shared Dialog And Advanced Settings UI

**Interfaces:** shared host consumes the service snapshot and cancel/confirm callbacks. It uses Task 1 phases for presentation, never authorizes Stop itself. Numeric field edits use a draft; Save writes one validated timing pair. Each confirmation snapshots saved values when opened.

- [ ] Write failing UI/source-contract and host lifecycle tests: disabled/enabled button labels, click disabled has no effect, Cancel/Back/outside/timeout continue recording, background/navigation/recreation dismiss, repeated opens do not create overlapping authorizations.
- [ ] Test both settings bounds and invalid/empty/nonnumeric input; verify default 2/10 values and persistence. Test recovery-screen Stop and any reachable legacy control through the same authorization path.
- [ ] Implement shared dialog with per-second display updates driven by monotonic deadlines; observe recording identity and lifecycle, cancel on change/background/disposal. If service/session authority is unavailable, do not fall back to unguarded Stop.
- [x] Route main/recovery/legacy explicit Stop actions to the host; preserve base handoff's existing final confirmation and alarm-only semantics. Reconcile every `stopIntent`/ACTION_STOP caller in a checked inventory.
- [x] Rename screen/menu title to `Advanced settings`; retain all logging/performance/export controls and add both numeric settings with seconds labels using the app's existing editor behaviour.
- [ ] Run focused tests GREEN and compile all app test sources. Check compact portrait/landscape/accessibility countdown text; record actual screenshots/device checks or mark them not run.

### Task 4: Assurance, Independent Review And Publication To Main

**Interfaces:** stable full delta from baseline, approved design, Task 1-3 test evidence and remaining manual checks.

- [ ] Update user docs (`docs/user-workflows.md`, logging/background docs where affected), formal capability/requirement mappings, verification evidence and `docs/superpowers/plan-status.md`. Keep unexecuted device/host checks explicitly pending; refresh only digest-valid approvals/attestations through the repository's prescribed assurance flow.
- [ ] Review all callers and original acceptance points; audit Stop alarm versus recording Stop, configuration-recovery early return, initial startup and fixed-base transition. No unexplained generic USER source remains in app-generated stop requests.
- [ ] Obtain one independent Sol/high review of the whole delta for ownership/lifecycle/data-integrity consequences; justify high effort on this boundary. Fix in-scope findings, then re-review affected parts. Never treat an agent timeout as a review.
- [ ] Run `git diff --check`; `sh scripts/pre_push_check.sh`; IDE alias dry-runs `sh gradlew :app:unitTestClasses :app:androidTestClasses --dry-run`. No parallel Gradle builds. Record exact results, including Termux compile-only tests.
- [ ] Stage only scoped files; commit, push and observe clean-host CI (both variants/tests/resource/native/release-lint checks). Repair real failures before declaring verified. Do not run local APK assembly in Termux.
- [ ] Push final scoped integration to main using explicit source ref, preserving the dirty root checkout. Update checkpoint evidence and report implemented-versus-device-tested status; a new RC requires separate release authorization and must not replace RC11.

## Plan Self-Review

Every approved design section is assigned above: timing/settings (1), authority/origins (2), shared UI/lifecycle/rename (3), assurance/docs/CI (4). All five review-focus conditions have explicit tests. Existing owner/session identity is reused, not a second recording lifecycle; exact snapshot plumbing is an implementation decision constrained by Task 2's serialized service contract. No implementation is authorized to infer a human stop from the current USER label.

## Execution Checkpoint

- User approved execution and next RC publication as an active goal; target is
  RC12, version code 13. RC11 remains immutable.
- Timing/authorization tests first failed compilation for missing policy symbols
  (recorded `/tmp` equivalent Termux log `stop-confirmation-red.log`). Initial
  invocation lacked ANDROID_HOME; rerun used the existing Android SDK and
  excluded only the documented non-runnable resource linker. No source failure
  was inferred from that environment error.
- Policy/settings, service authorization, dialog/settings UI are in progress;
  host tests and full assurance are not yet executed for this delta.
- Ruling: use one shared native Android AlertDialog instead of a Compose-only
  host, because the legacy Activity is not a Compose/LifecycleOwner Activity.
  Both screens use exactly the same policy/authorization and lifecycle callback;
  this avoids two divergent dialogs. Cost if wrong: styling/accessibility must
  be caught by host/manual checks, not a change to approved timing or safety.
- Existing lifecycle fixtures now generate genuine in-window confirmation tokens
  rather than relying on the removed unscoped Stop shortcut; their original raw
  finalization and exception assertions remain unchanged.
- Focused local suite: 14 tests passed, zero failures/errors/skips. Diagnostic
  serializer regression first failed twice (`controlId` absent), then passed
  after bounded allowlist and correlation repairs. All app test sources compile;
  Robolectric execution is still host-only.
- Independent Sol/high review (runtime metadata verified gpt-6.1-sol/high) found
  discarded diagnostic keys, synthetic ownership masking production activation,
  early failed-Start callback retention and locally repeated dialog submission.
  Repairs include real A-stop/B-start outstanding-token coverage, early rejected
  Start coverage, no resetting real service callbacks, and a one-shot UI guard.
  The new Android lifecycle oracles have not yet executed; do not claim RED/GREEN
  host execution from their successful local compilation.
- Full mandatory local gate passed before the final navigation edge: 1,491
  current-task results / 197 suites, zero failures/errors/skips; exact semantic
  review passed and all 139 assurance-tool tests passed. Controller acceptance
  audit then added explicit Compose screen-disposal cancellation, since Activity
  pause alone does not cover programmatic navigation. The new missing-method
  compile test was observed RED before implementation. Gate/review freshness
  must be renewed for that small delta before push; earlier results are not
  claimed as verification of the navigation hook.
- Ruling: accepted Stop origins use three explicit validated wire names rather
  than a separate enum; service-derived origin is never copied from arbitrary
  intent text. Cost if wrong: new caller strings require review/tests; current
  accepted branches and serialized provenance are tested and independently read.
