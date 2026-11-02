# Recording Stop Confirmation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent accidental explicit recording Stop requests using configurable, session-bound confirmation and identify the origin of future stop requests.

**Architecture:** A pure deadline policy supplies a shared native Android dialog used by Compose and legacy screens. The service owns a one-use confirmation authorization bound to its current recording/startup identity and elapsed-time window, and validates it before stopping. Existing confirmed base transitions and automatic cleanup remain separate typed paths; diagnostics never authorize or block capture.

**Tech Stack:** Kotlin, Compose Material 3, Android SharedPreferences, existing JVM/Robolectric tests and GitHub CI.

**Spec:** `docs/superpowers/specs/2026-11-01-recording-stop-confirmation-design.md` (approved).

## Global Constraints

### 2026-11-02 Button Separation Follow-Up

Owner requested visibly separated Stop-dialog buttons and another RC12 rebuild.
Reuse this approved plan; do not change service, authorization or countdown logic.

- [x] Diagnose missing explicit spacing after filled backgrounds replaced the
  framework's inset backgrounds; add a failing resource-spacing regression.
- [x] Add symmetric 6dp margins to both action styles (12dp between fills),
  independent of horizontal/stacked placement or layout direction.
- [ ] Verify actual button bounds after layout in both directions, narrow/wide
  windows and both countdown phases; retain existing recording/reconnect tests.
- [ ] Independent review, assurance refresh, full pre-push and clean-host CI.
- [ ] Record this explicit new owner-authorized RC12 replacement, preserve prior
  source/checksum history, rebuild with stable key, verify and republish assets.

Checkpoint: implementation in progress; physical-device appearance pending.

Host evidence: CI41513847336 and diagnostic CI41516342991 failed only the new
presentation visibility assertion on SDK33/35; all other 70 service cases passed.
The diagnostic root-only probe found `rootVisible=false`, root size `-1 x -1`,
and measured decor `220 x 275`; the first-case gap and ancestor layout bounds
passed. The fixture measured decor without initializing the window root.
Correct the fixture through actual window layout/traversal, retaining gap,
direction, stacked/horizontal, phase and visibility assertions. No production
clipping conclusion or successful whole-host gate is inferred from this evidence.
CI41519517827 proved initialization (`root=220x1000`, `decor=220x275`) but the
fixture incorrectly required equal wrap-content/root heights. Source review of
Robolectric 4.16.1 confirms its default available-frame height behavior. Require
positive dimensions, exact requested width and full decor containment by the
root-only visibility probe, not height equality; retain all button assertions.

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
- [x] Run targeted policy tests, record actual RED; compile-check settings tests locally and run them in host Robolectric where required.
- [x] Implement only policy/settings contracts and rerun targeted tests GREEN.

### Task 2: Service-Owned Stop Authorization And Provenance

**Interfaces:** `StopRequestOrigin` distinguishes CONFIRMED_CONTROL, FIXED_BASE_TRANSITION and TERMINAL_ALARM. A process-local service-owned authority exposes immutable pending confirmation snapshots (request ID, current owner ID, timing/deadlines) to the shared UI; `requestConfirmation(controlId: String)`, `cancelConfirmation(requestId: String, outcome: String)` and `consumeConfirmation(requestId: String, nowMillis: Long, ownerId: String): Boolean` are serialized against stop/start ownership. Service uses its own monotonic time, not caller-supplied deadlines. Owner IDs must survive startup-to-capture promotion and never be reused.

- [x] Write failing authorization tests: early/expired/missing/replayed token, second tap, old-session token, startup token, owner retirement and cancellation all reject appropriately; active in-window token consumes exactly once.
- [x] Extend service tests: queue confirmed Stop for A, finish A/start B, deliver old request and prove B remains running; confirm during startup; prove opening/cancel/timeout has no producer/alarm effect.
- [x] Add service tests for failed diagnostics and legitimate fixed-base transition; test terminal Stop alarm cannot stop an unrelated active session through a stale generation.
- [x] Run targeted tests and record RED; introduce authority and a distinct confirmed-stop intent/action. Do not let unscoped legacy UI intents bypass authorization. Migrate every internal caller deliberately; preserve generation guards on terminal alarms and immediate automatic failure cleanup.
- [x] Add bounded request-origin/control ID to accepted stop evidence and confirmation outcome events. Retain existing USER initiator/contributors/correlation semantics and avoid secrets/coordinates.
- [x] Run policy/authority/stop-context tests GREEN plus existing startup, raw finalization and alarm regressions; host-only service cases remain explicit until CI executes them.

### Task 3: Shared Dialog And Advanced Settings UI

**Interfaces:** shared host consumes the service snapshot and cancel/confirm callbacks. It uses Task 1 phases for presentation, never authorizes Stop itself. Numeric field edits use a draft; Save writes one validated timing pair. Each confirmation snapshots saved values when opened.

- [x] Write failing UI/source-contract and host lifecycle tests: disabled/enabled button labels, click disabled has no effect, Cancel/Back/outside/timeout continue recording, background/navigation/recreation dismiss, repeated opens do not create overlapping authorizations.
- [x] Automate settings bounds, default 2/10 values, persistence and corrupt-store fallback. Source-review empty/nonnumeric editor validation and recovery/legacy routing through the shared authorization path; source review is not executed UI evidence.
- [ ] Execute empty/nonnumeric editor interactions and reachable recovery/legacy Stop UI flows explicitly on a device or UI test host; these interaction cases remain pending.
- [x] Implement shared dialog with per-second display updates driven by monotonic deadlines; observe recording identity and lifecycle, cancel on change/background/disposal. If service/session authority is unavailable, do not fall back to unguarded Stop.
- [x] Route main/recovery/legacy explicit Stop actions to the host; preserve base handoff's existing final confirmation and alarm-only semantics. Reconcile every `stopIntent`/ACTION_STOP caller in a checked inventory.
- [x] Rename screen/menu title to `Advanced settings`; retain all logging/performance/export controls and add both numeric settings with seconds labels using the app's existing editor behaviour.
- [x] Run focused tests GREEN and compile all app test sources. Host tests execute in both distributions on SDK33/35.
- [ ] Physical-device compact portrait/landscape/accessibility countdown checks remain not run; no screenshot or field-validation claim is made.

### Task 4: Assurance, Independent Review And Publication To Main

**Interfaces:** stable full delta from baseline, approved design, Task 1-3 test evidence and remaining manual checks.

- [x] Update user docs (`docs/user-workflows.md`, logging/background docs where affected), formal capability/requirement mappings, verification evidence and `docs/superpowers/plan-status.md`. Keep unexecuted device checks explicitly pending; refresh only digest-valid approvals/attestations through the repository's prescribed assurance flow.
- [x] Review all callers and original acceptance points; audit Stop alarm versus recording Stop, configuration-recovery early return, initial startup and fixed-base transition. No unexplained generic USER source remains in app-generated stop requests.
- [x] Obtain one independent Sol/high review of the whole delta for ownership/lifecycle/data-integrity consequences; justify high effort on this boundary. Fix in-scope findings, then re-review affected parts. Never treat an agent timeout as a review.
- [x] Run `git diff --check`; `sh scripts/pre_push_check.sh`; IDE alias dry-runs `sh gradlew :app:unitTestClasses :app:androidTestClasses --dry-run`. No parallel Gradle builds. Record exact results, including Termux compile-only tests.
- [x] Stage only scoped files; commit, push and observe clean-host CI (both variants/tests/resource/native/release-lint checks). Repair real failures before declaring verified. Do not run local APK assembly in Termux.
- [x] Push final scoped integration to main using explicit source ref, preserving the dirty root checkout. Update checkpoint evidence and report implemented-versus-device-tested status. Owner separately authorized and resumed RC12 publication; RC11 is unchanged.

## Plan Self-Review

Every approved design section is assigned above: timing/settings (1), authority/origins (2), shared UI/lifecycle/rename (3), assurance/docs/CI (4). All five review-focus conditions have explicit tests. Existing owner/session identity is reused, not a second recording lifecycle; exact snapshot plumbing is an implementation decision constrained by Task 2's serialized service contract. No implementation is authorized to infer a human stop from the current USER label.

## Execution Checkpoint

### 2026-11-02 RC12 Replacement (Owner Approved)

The owner subsequently requested updating RC12 and explicitly selected option 2:
replace the existing tag and APK as a one-off exception, rather than publish a
new immutable revision tag. This later decision supersedes only the earlier
RC12 no-retag/no-release restriction. RELEASE-SIGN-001 records the exact allowed
source transition; all stable-key and verification gates remain in force.

- [x] Preserve and verify the original three release assets and their source
  `d56d5d2` / APK checksum `041bd835...` before replacement.
- [x] Temporarily draft the release, update only RC12 using a force-with-lease
  against the previously observed tag object, and rebuild verified source
  `d1313d1ce90dabc6c47e4afb14ce8481a40f312c` through GitHub.
- [x] Verify the downloaded replacement APK and updated source archive before
  republishing: stable build41429387555 passed; source/signing receipts match
  d1313d1, package/version/code13 and v2/v3 signer match, both ARM ABIs and
  16 KiB alignment pass, and all 738 archive files match the source tree.
- [x] Republish the same RC12 release with checksum
  `e3882efac5f06c5827e759e17257e0ac7647aad1eb586a41bb3a4b562e800c16`,
  unchanged stable certificate and app version/code. Preserve old/new evidence
  in `docs/releases/1.0-RC12.md`. No other tag or signing identity is changed.

Repository integration of this replacement record still requires exact-scope
assurance/documentation review, the full normal pre-push gate and final CI.
The Git commit and its CI status provide that integration evidence; the owner
exception does not waive any of these checks.

Physical-device appearance, USB permission/reconnect and audible-delivery checks
remain pending; publication does not turn the stable-signed debug APK into a
Google Play production build.

### 2026-11-02 Dialog Follow-Up

Owner explicitly requests centered placement, filled colored confirmation and
gray Cancel, and unchanged recording/USB/position-alarm handling in either phase.
Baseline: `c21f422`; branch `codex/stop-dialog-presentation`. At implementation
time this follow-up did not authorize a new release. The later explicit RC12
replacement decision above supersedes that restriction for RC12 only.

Root-cause evidence: the native AlertDialog inherits platform/OEM window gravity
and borderless action backgrounds; neither is set explicitly by the app. Capture,
USB recovery and alarm ownership remain in the foreground service, with no dialog
state dependency found. No recording regression is inferred without evidence.

Design: keep the existing shared native dialog, deadlines and authorization.
Explicitly center its window and provide stateful filled action drawables with
readable text, touch feedback and a visibly disabled confirmation. Prefer this
scoped presentation repair over replacing the dialog with a second Compose host
or changing service ownership. Runtime health paths are changed only if a
combined regression demonstrates an actual defect.

Files: `ui/common/StopRecordingConfirmation.kt` for window/button presentation;
`res/values/styles.xml`, `res/drawable/stop_confirmation_*.xml` and
`res/color/stop_confirmation_text.xml` for native stateful fills and text;
`recording/RecordingAlarmServiceTest.kt` for host assertions and simulated USB
input; `ui/StopConfirmationPresentationTest.kt` for locally executable structured
resource/contrast assertions; existing `UI-STOP-001`, assurance records and user documentation for the
requested contract/evidence. No new runtime abstraction is planned.

- [x] Add presentation regression and observe an actual failing assertion where
  feasible. Termux cannot run native Robolectric; use host CI for those cases
  without claiming a local RED from compilation alone.
- [x] Implement explicit centering and filled enabled/disabled button styling;
  preserve existing guarded click dispatch and countdown assertions.
- [x] Test actual service capture with the dialog open in WAITING and ACTIVE:
  receiver bytes are recorded, USB loss raises/recovery clears its alarm and
  reconnect resumes byte-exact capture; position loss/recovery remains armed.
  Use condition-based waits and keep raw artifacts authoritative.
- [x] Independently review the affected safety boundary, run the normal full
  pre-push gate, push, and observe both-variant host CI. Update actual evidence;
  retain physical-device visual/USB/audio checks as pending until performed.

Final follow-up evidence: source `1d4d1b9bca41b40ca506f43cd9a604be88760530`
passed [CI41422558203](https://github.com/holubp/rtkcollector/actions/runs/41422558203)
with 2,865 JVM tests / 360 suites, zero failures/errors/skips. Each distribution
passed all 72 recording-service cases, including presentation and actual service
capture/USB recovery/position-alarm tests on SDK33/35 in both countdown phases.
Native boundary/startup checks passed 30 tests; both release lint checks,
debug assembly and signing smoke passed. Normal local pre-push checks passed
1,505 JVM tests / 199 suites and compiled all app JVM test sources. Sol's
independent affected-source and test reviews found no remaining blockers.
No production capture, USB recovery, position/alarm or authorization code needed
changing. Physical-device portrait/landscape/font-scale appearance, real USB
permission/reconnect and audible alarm checks remain pending. At completion of
the source follow-up, RC12's tag/APK were unchanged; the owner subsequently
authorized the replacement recorded above.

Local evidence: presentation resource tests first failed 3/3 actual assertions
for the absent explicit theme/backgrounds, then the normal Android compilation
gate passed with the implementation and all app test sources. Native combined
service cases remain unexecuted locally; successful host execution is recorded
above. The first
attempt to run that gate correctly rejected the not-yet-staged new test input;
it was staged before the successful rerun. No test-input exclusion was added.

Host CI41413869523 at40c8a00 stopped before unit tests: resource linking rejected
`android:windowGravity`, which is not a public style attribute. Centering already
uses the public [Window.setGravity API](https://developer.android.com/reference/android/view/Window#setGravity(int)).
The invalid redundant XML entry is removed; the native actual-window centering
assertion is unchanged. A new locally executable public-attribute check first
failed specifically on that entry (1/4 tests), so this error can now be caught
without native aapt2. Correcting the mistaken XML test expectation does not
replace the host geometry assertion or weaken recording/timing checks.

Host CI41417347368 atfb7531b linked resources and passed the combined actual
service capture/USB/position test on SDK33 and SDK35, including both phases in
each case. Its two failures were the presentation oracle expecting normal teal
but receiving the exact declared darker focused/pressed fill. The old log did
not capture the focus/pressed flags, so that state is inferred, not recorded.
The oracle now checks the actual focused/pressed state and
explicitly exercises enabled normal, focused and pressed drawable states. The
palette, native centering, disabled fill, Cancel fill and all recording/timing
assertions are unchanged. Sideload and later build checks were not reached in
that failed run; complete host verification subsequently passed as recorded above.

### Original RC12 Publication (Superseded)

Status: **Implemented, not field-tested**. Source `d56d5d26905c3543bf1e05c7b2e5b7182861c616`
was fast-forwarded to remote main and tagged `v1.0-RC12`. Full-host
[CI41359752906](https://github.com/holubp/rtkcollector/actions/runs/41359752906)
passed 2,849 JVM tests / 358 suites (zero failures/errors/skips), including
68 recording-service cases per variant on SDK33/35. Native boundary/startup
checks passed 30 tests; both release lint checks, assembly and signing smoke
passed. Normal local pre-push checks passed 1,501 JVM tests / 198 suites and
compiled all app JVM tests; IDE task aliases dry-ran successfully.

The same Sol/high independent review and focused Astra repair reviews found no
remaining blockers. No diagnostic exception, timing relaxation or added test
exclusion was used. Historical failed candidates remain recorded below.

[Stable release build41360543487](https://github.com/holubp/rtkcollector/actions/runs/41360543487)
passed against the exact tag. Downloaded APK source receipt, SHA-256, single
stable signer, v2/v3 signatures, package/version/code13, ARM64/ARMv7 and 16 KiB
ARM64 ELF/ZIP alignment were independently verified before publishing the
[RC12 prerelease](https://github.com/holubp/rtkcollector/releases/tag/v1.0-RC12).
See `docs/releases/1.0-RC12.md` for checksums and certificate identity.
Physical-device touch/accessibility/background/rotation checks remain pending;
the initiating control in the old RC11 USER-stop capture remains unproven.

### Approved Astra Repair Slice

Owner approved the Astra repair and resumed work, explicitly using normal gates,
not a diagnostic exception. The prior gate-exception prerequisite was overstated.
Source review of a newly repaired implementation must remain distinct from future
host execution; do not certify host GREEN or erase historical failures.

- [x] Add `recording/StopConfirmationRefreshLoop.kt` and matching pure tests.
  Interface: window, elapsed/uptime readers, current-request predicate,
  `(phase, seconds)` renderer, dismissal, `(Runnable, uptimeTarget)` scheduling
  and cancellation callbacks. At most one callback; cancel fences rescheduling.
- [x] Observe missing-class RED, then reproduce actual old-order drift with the
  extracted render-then-delay loop and 15 ms rendering-cost fake. Keep literal
  boundary expectations, not values generated through production policy.
- [x] Choose an absolute second/phase/100 ms owner-check target before rendering;
  afterward post at `uptimeNow + max(0, targetElapsed - elapsedNow)`. Test cancellation,
  stale identity, late wakeups, expiry during rendering and differing clocks.
- [x] Adapt the native dialog to the pure loop. Require enabled button, current
  request and fresh ACTIVE phase for dispatch. Strengthen successful UI tests with
  enabled/label assertions; preserve original 2/9/1 host timing assertions.
- [x] Update the narrow capability mappings/approved design provenance, invalidate
  affected evidence honestly, and obtain independent source review of repaired
  code. Run normal full pre-push gate, commit/push and full host CI; keep RC12
  unpublished until current-source tests, build and signed-APK verification pass.

Repair evidence: the extracted old render-then-delay order failed 3/7 actual
pure tests: WAITING rather than ACTIVE at 2000 ms, scheduled uptime1115 rather
than1100, and label2 rather than1 at1000. The boundary-aligned implementation
passed10 refresh tests plus5 policy tests, zero failures; all Android test sources
compiled. These results prove the source scheduling mechanism, not historical CI
callback causality. Final affected-source review, full gate, host CI and signed
release verification passed as recorded above. No diagnostic workflow/exception
was used.

### Earlier Checkpoints

- Repair2b96ec3 passed the normal local gate:1,501 JVM cases/198 suites,
  zero failures/errors/skips, with all app test sources compiled. Independent
  Sol source review passed. Ordinary host CI41357572741 attempt1 hit Maven
  Central HTTP429 fetching the Robolectric runtime; the unchanged retry fetched
  it successfully. Attempt2 passed the original timing/expiry assertions and
  hidden-dialog provenance regression, then failed both immediate post-pause
  authorization assertions. No timing threshold or assertion was relaxed.
- Astra's affected lifecycle review identified queued OnDismiss revocation.
  Pause must synchronously revoke an unsubmitted request; only successful
  dispatch (`outcome == "submitted"`), not a merely attempted dispatch, retains
  authorization for service delivery. Added Waiting/Active pause, delayed old
  cleanup versus replacement, failed dispatch, and successful dispatch followed
  by pause coverage. Current gate and ordinary host execution remain required.
- Sol's affected review extended the same synchronous captured-request cleanup
  to owner-scoped navigation and destruction, preventing an immediate reopened
  dialog from inheriting the dismissed request before queued cleanup executes.
  The navigation test now reopens before draining callbacks and checks a fresh
  WAITING request that survives the old dialog's cleanup. Successful-dispatch
  authorization remains exempt from all these cancellation paths.
- Astra's combined submit/reopen review found that an outstanding dispatched
  token must not be adopted by a new dialog after queued cleanup clears the old
  dialog. UI tracking now prevents this until service consumption or original
  expiry; an expired submitted request can be replaced by a fresh countdown.
  Tests defer actual service delivery across cleanup, reopen, navigation and
  pause, and separately cover expiry followed by retry.

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
- Candidate fd318cd reached host CI41348026809: 1,153 Google Play JVM cases,
  four failures (the two new initial-button/success tests on SDK33 and35).
  Queued OnShow initialization left the default native positive button enabled
  until dispatch; an early test click dismissed the dialog before installing the
  guarded listener. Product repair initializes buttons synchronously immediately
  after show returns. Original assertions remain unchanged as the host RED oracle.
  Other host cases, including real A/B ownership and cancellation/navigation,
  passed; sideload/native/release tasks were not reached in this failed run.
- Repair3528ee4 / host CI41349342786 passed the initial-disabled and successful
  confirmation tests, leaving two failures in the timer UI assertion at the
  two-second activation boundary (SDK33/35). The remaining cause is not proven.
  The same assertion now includes elapsed/uptime deltas, captured window/phase,
  label, visibility, token identities and next scheduled task; no deadline or
  assertion was relaxed. This diagnostic branch must not be released as verified.
- A focused local Robolectric probe cannot replace host evidence: SDK33 needs
  unavailable conscrypt Linux/aarch64 JNI and SDK35 reports unsupported native
  runtime on Linux/aarch64. The external diagnostic init script is not part of
  repository gates; no native/tooling bypass or test exclusion change is made.
