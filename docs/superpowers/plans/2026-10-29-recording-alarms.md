# Recording Reliability Alarms Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Alert the backpack-carrying operator to disrupted capture or lost
position, automatically clear recovered alarms, and never make recording depend
on notification/audio success.

**Architecture:** A pure session-scoped policy consumes bounded advisory
evidence. Independent notification and audio effect lanes reconcile its latest
desired state. RecordingForegroundService owns policy and an explicit alert-only
lifetime after terminal failure; raw resources retain their existing ownership.

**Tech Stack:** Kotlin/JVM, existing Android foreground service, Compose,
SharedPreferences, Android notifications/audio/ringtone picker, bundled PCM WAV;
no new runtime dependencies and no native RTKLIB changes.

**Spec:** `docs/superpowers/specs/2026-10-29-recording-alarms-design.md`

## Global Constraints

- Three categories: receiver connection lost, position lost, recording interrupted.
- Default low single repeating beep/pause for position; high repeating triple
  beep/pause for connection and other disruptions. Visual/audio enabled initially.
- Independent visual/audio/sound settings for each; persistent global preferences,
  not profile/settings-set fields. Built-ins and Android notification sounds.
- Position arms only after first fresh valid solution in that recording;
  5 s default delay, configurable 1-60 s. No RTK-quality downgrade alarm.
- Existing receiver no-RX threshold remains 10 s; confirmed transport failure
  alerts without waiting for it. Never change reconnect timing to implement alarms.
- Recovery automatically stops the recovered sound and removes its warning,
  without acknowledgement. Other unresolved causes remain active.
- Deliberate Stop fences effects immediately; no waits for audio/Binder or session
  finalization. Unexpected terminal failure retains its alarm, not raw resources.
- Sound priority: recording interrupted > receiver loss > position loss; one player.
- Respect volume/DND/routing. No full-screen intents, exact alarms, volume changes,
  boot resurrection, watch app, microphone access or automatic recording restart.
- Focus denial/delayed/transient recovery ownership is bounded to 30 s; permanent
  loss/rejection/exhaustion releases terminal audio ownership, not failure evidence.
- Never issue notification/audio/provider/preferences IO or wait for effects on
  capture, correction routing, receiver commands, reconnect or writer finalization.
- Process kill/force-stop/power loss cannot be covered reliably; no such promise.
- Do not touch existing ownership draft files, private settings or samples.
- This plan does not authorize a version bump/release or inherit the RC8-specific
  strict-checker exception. Resolve the current required push gate before pushing.

## Review Focus

1. Stop races with delayed playback/provider callbacks: no old sound after Stop
   or new recording (Tasks 2/4 generation and blocked-effect tests).
2. Terminal failure after acknowledged corrections loss: new fatal episode sounds,
   but acknowledging it cannot reveal obsolete alarms (Tasks 1/4 cause tests).
3. Mock output disabled or stale dashboard/RTKLIB snapshots: position alarm still
   works, but old data never refreshes it (Task 3 independent clock/revision tests).
4. USB reconnect without positions: connection clears, positioning remains lost;
   recording/finalization do not wait for either effect lane (Tasks 1/3/4 tests).
5. Android denies playback or notification permission while finalizing: raw files
   close, no alert-shell resurrection or hidden writer lease (Tasks 2/4 SDK tests).

## Checkpoint Ledger And Routing

Resume decision: human explicitly requested implementation and RC9 publication;
this supersedes the earlier no-release authorization limit for RC9 only. Native
execution with one independent final review is retained. RC9 strict-gate exception
has been requested explicitly and is not inferred from RC8. Remote main and local
source both verified as `0d2a042` at execution start; unrelated drafts preserved.

C0: added ANDROID-ALARM-001 through 006 and matrix rows (Needs review). Existing
requirement consistency checker passed; strict checker is absent. C1 RED:
`c1-red.log` failed for missing Alarm model/policy APIs before production code.
`c1-green.log` subsequently exposed the new foreground/evidence APIs still absent;
that was additional RED, not a passing suite. Pure rerun is `c1-c2-pure.log`.
Sound generator test was RED for absent module, then GREEN for exact PCM rate,
pulse count, pause and 440/880 Hz pitch. Bundled WAVs generated reproducibly.
`c1-c2-pure.log` passed: policy, dispatcher and foreground/evidence focused tests;
all app JVM-test sources compile. New settings Robolectric test is registered
for compile-only Termux handling and still requires execution on clean-host CI.
Checkpoint foundation is implemented; Android effects/integration remain open.
C2: `c2-invalidation-red.log` ran 23 tests and reproduced rejection of a Stop
clearing update. Fixed invalidation, then `c2-close-red.log` reproduced loss of
final visual clearing on close (4 tests, one failure). Effects now drain the final
visual state without joining; generation-tagged notifications and per-app epoch
fencing prevent a late old clear from canceling a new session. `c2-final-green.log`
passed the focused suite after those fixes. Platform tests compile locally;
Robolectric execution remains a clean-host gate. No device audio claim is made.
Ruling: use RTKLIB's existing decoded GNSS solution timestamp as the epoch identity;
the native bridge obtains it from `sol.time`, not snapshot wall time. Deduplicate
that identity in the app and timestamp receipts monotonically; no native/core
revision field is needed. Tests must include duplicate snapshots and clock rollback.

Baseline: worktree `.worktrees/ntrip-tls-integration`, branch
`codex/ntrip-tls-integration`; inspected source commit `0d2a042` (RC8).
Design approved, including automatic recovery; no product implementation yet.
One Sol-medium read-only design builder check completed; all three ambiguities
are resolved in the approved design. No extra planning reviewer is required.

| Checkpoint | Owner / scope | State | Required receipt |
| --- | --- | --- | --- |
| C0 | Controller: canonical contract and environment | Open | Requirement map, baseline/gate decision |
| C1 | Pure policy + persistent settings | Open | RED/GREEN tests and commit |
| C2 | Independent effect lanes and Android adapters | Open | Rejection/hang/Stop tests and commit |
| C3 | Source evidence and detection | Open | Receiver/engine/clock tests and commit |
| C4 | Service lifecycle and terminal alerts | Open | Isolation/SDK lifecycle tests and commit |
| C5 | Settings, sound picker and operational actions | Open | UI/model/persistence tests and commit |
| C6 | Integration, docs and independent audit | Open | Gate results, requirement matrix and final disposition |

Record command, outcome, source commit, review fixes and remaining manual checks
at each checkpoint. Resume by reading this ledger and actual status/diffs.
No checkbox turns green from compilation alone when execution was required.

Recommend native implementation for tightly coupled policy/service integration,
with one independent Sol-high final audit justified by concurrency/data-integrity
risk. If the owner chooses subagent-driven execution, batch bounded policy,
settings or assets work on Luna-medium with exact interfaces below; controller
owns evidence mapping/lifecycle. Respect the repository's three-child ceiling,
reuse qualifying review, and never run concurrent Gradle builds on shared storage.
Do not start workers merely to emulate model switching. Routing/weekly usage
telemetry is unknown unless surfaced by the runtime; do not invent it.

## File Map And Interfaces

New production files under `app/src/main/kotlin/org/rtkcollector/app/alarms/`:

- `AlarmModels.kt`: categories, cause keys, session/episode tokens, settings,
  position evidence, desired outputs and delivery status. No Android types.
- `RecordingAlarmPolicy.kt`: pure reducer and monotonic deadline checks.
- `AlarmSettingsStore.kt`: one global preference snapshot/observer; no profile IO.
- `AlarmEffectDispatcher.kt`: independent bounded latest-state effect lanes,
  rejection isolation and generation cancellation; no session-writer references.
- `AlarmNotificationPublisher.kt`: channels, stable IDs and safe action tokens.
- `AlarmAudioPlayer.kt`: built-in/system URI playback, focus, fallback and preview.
- `AlarmForegroundPolicy.kt`: pure recording/alert-only/idle foreground decisions.
- `RecordingAlarmEvidence.kt`: source validity, deduplication and cause mapping.
- `RecordingAlarmController.kt`: bounded evidence mailbox, independent ticker,
  policy/effect coordination and immediate user-Stop fencing.

Other changes: `RecordingForegroundService.kt`, `RecordingServiceState.kt`,
`ui/MainActivity.kt`, `ui/settings/SettingsHub.kt`; create
`ui/alarms/AlarmSettingsScreen.kt` and `AlarmSettingsUiModel.kt`.
Add manifest playback permission/type, small notification drawable resources and
two raw sounds. Add reproducible `tools/generate_alarm_sounds.py` and its test.
For genuine RTKLIB update identity, extend `core/rtklib/.../RtklibOutputModels.kt`
and `RtklibWorker.kt` with an additive decoded-solution revision if needed; do not
infer a new fix from repeated backend status polling or change native protocols.

Matching tests live under `app/src/test/kotlin/org/rtkcollector/app/alarms/`;
service lifecycle/isolation tests under existing `.../recording/`; RTKLIB revision
tests beside `core/rtklib/.../RtklibWorkerTest.kt`. Settings/SDK Android tests use
existing Robolectric conventions and must be registered with Termux exclusions
in `app/build.gradle.kts`; update gate tests if their class inventory changes.

Define these contracts in Task 1 and preserve names across tasks:

```kotlin
enum class AlarmCategory { CONNECTION_LOST, POSITION_LOST, RECORDING_INTERRUPTED }
enum class BuiltInAlarmSound { SINGLE_LOW, TRIPLE_HIGH }
sealed interface AlarmSoundChoice {
    data class BuiltIn(val pattern: BuiltInAlarmSound) : AlarmSoundChoice
    data class SystemNotification(val uri: String) : AlarmSoundChoice
}
data class AlarmCategorySettings(val visual: Boolean, val acoustic: Boolean,
    val sound: AlarmSoundChoice)
data class AlarmSettingsSnapshot(val categories: Map<AlarmCategory, AlarmCategorySettings>,
    val positionLossDelayMillis: Long = 5_000)
data class AlarmCauseKey(val category: AlarmCategory, val component: String, val code: String)
data class AlarmEpisodeId(val generation: Long, val cause: AlarmCauseKey, val revision: Long)
data class AlarmPositionEvidence(val sourceId: String, val sequence: Long,
    val receivedElapsedMillis: Long, val valid: Boolean)
data class AlarmEpisode(val id: AlarmEpisodeId, val detail: String, val terminal: Boolean)
data class AlarmAudioSelection(val episodeId: AlarmEpisodeId, val sound: AlarmSoundChoice)
data class AlarmDesiredState(val generation: Long, val revision: Long,
    val visibleEpisodes: List<AlarmEpisode>, val audio: AlarmAudioSelection?,
    val terminalAlertPending: Boolean)
enum class AlarmEffectStatus { IDLE, PENDING, ACTIVE, BLOCKED, FAILED }
data class AlarmDeliveryStatus(val desiredRevision: Long,
    val visual: AlarmEffectStatus, val audio: AlarmEffectStatus, val detail: String?)
```

Define sealed `AlarmCommand` variants with these constructor fields (all carry
`generation: Long`; elapsed values below are `Long`): `Begin(settings, nowElapsedMillis)`,
`Position(evidence)`, `CauseRaised(key, detail, nowElapsedMillis)`,
`CauseRecovered(key, nowElapsedMillis)`, `Tick(nowElapsedMillis)`,
`Acknowledge(episodes: Set<AlarmEpisodeId>, nowElapsedMillis)`,
`SettingsChanged(settings, nowElapsedMillis)`, `UserStop(nowElapsedMillis)`,
`TerminalStop(key, detail, nowElapsedMillis)`,
`DeliveryChanged(status: AlarmDeliveryStatus, nowElapsedMillis)`.
Here `settings: AlarmSettingsSnapshot`, `evidence: AlarmPositionEvidence`,
`key: AlarmCauseKey`, `detail: String`. Ack carries exact episode IDs, not category
alone. Policy API: `accept(command: AlarmCommand): AlarmDesiredState`.
Platform errors return `AlarmDeliveryStatus`; they never become capture errors.

## Task 0: Establish Canonical Contract And Baseline (C0)

**Files:** approved design, `docs/specification/android-runtime.md`,
`verification-matrix.md`, `docs/superpowers/plan-status.md` and this ledger.

- [ ] Verify branch/base/dirty state and available Gradle tasks; preserve unrelated
  files. Check `tools/specification/spec_check.py` availability and current approval
  infrastructure. RC8 waiver is historical, not authority for this change's push.
- [ ] Add canonical `ANDROID-ALARM-001` detection, `002` independent persistent
  configuration, `003` outputs/privacy, `004` recovery/ack/Stop, `005` isolation,
  `006` foreground/audio ownership. Link each to Tasks 1-6 tests/manual criteria;
  initial status Needs review. Do not copy approvals or claim unrun evidence.
- [ ] Run `python3 tools/check_spec_requirements.py docs/specification` and record
  its actual scope. Resolve any normative conflict interactively; a missing strict
  gate is a push blocker, not a reason to weaken tests or alter private root work.
- [ ] Record baseline test commands and environment; no local APK/native build
  requirement on Termux. Commit approved design/canonical requirements separately.

## Task 1: Pure Policy And Persistent Settings (C1)

**Files:** `AlarmModels.kt`, `RecordingAlarmPolicy.kt`, `AlarmSettingsStore.kt`;
`RecordingAlarmPolicyTest.kt`, `AlarmSettingsStoreTest.kt`.
**Consumes:** typed evidence/commands, elapsed time supplied by caller.
**Produces:** contracts above; store `read(): AlarmSettingsSnapshot`,
`write(snapshot: AlarmSettingsSnapshot): Unit`, `observeChanges(listener: () -> Unit): Closeable`.

- [ ] Write failing pure tests: acquisition never arms; first valid fix then
  stale at exactly 5,000 ms raises position loss; 4,999 ms does not; invalid
  frames do not refresh; a valid recovery clears visual/audio without Ack;
  no-fix after reconnect stays alarmed; RTK fixed/float/single are all valid.
  Use fake monotonic time, no sleeps. Exact oracle:
  `assertFalse(policy.accept(Tick(g, 4_999)).visibleEpisodes.isNotEmpty())`
  after initial valid receipt at zero; at 5,000 assert POSITION_LOST and SINGLE_LOW.
- [ ] Add RED tests for independent toggles/sounds, one-player priority, disabled
  urgent sound not suppressing enabled position sound, cause-specific Ack,
  correction Ack then fatal storage, terminal subsumption, old generation events,
  deliberate Stop even while already STOPPING, fresh Start, and post-recovery
  recurring episode with a new token. Valid-then-invalid conflation must retain
  evidence that the first valid fix occurred.
- [ ] Run focused Termux tests to observe RED (missing API/assertion), then
  implement reducer. Preserve first cause, suppress redundant position while
  receiver is known lost, and distinguish unresolved causes within one category.
- [ ] Add/observe RED persistence tests: all defaults on, correct per-category
  sounds, delay 1-60 s, restart round trip, corrupt values safely default,
  independently retained system URI and no reset by settings-set application.
  Implement one atomic SharedPreferences snapshot and observer off capture.
- [ ] Run pure tests GREEN, compile all Android tests; execute persistence tests
  on clean-host CI. Maintain exact Robolectric exclusions for Termux (compile
  checked there, not claimed runtime-tested). Commit `feat: add recording alarm policy`.

## Task 2: Bounded Effects, Notification And Audio Adapters (C2)

**Files:** dispatcher/notification/audio/foreground-policy files above, raw/drawable
resources, generator and corresponding `AlarmEffectDispatcherTest`,
`AlarmNotificationPublisherTest`, `AlarmAudioPlayerTest`, `AlarmForegroundPolicyTest`.
**Consumes:** `AlarmDesiredState` and immutable settings.
**Produces:** dispatcher `offer(desired: AlarmDesiredState): Boolean`,
`invalidate(generation: Long): Unit`, `close(): Unit` (non-waiting);
notification `apply(desired): AlarmDeliveryStatus`; audio `apply(desired): AlarmDeliveryStatus`,
`preview(sound: AlarmSoundChoice): Unit`, `stopPreview(): Unit`.
Pure foreground API: `mode(recordingOwnsService: Boolean, terminalPending: Boolean,
audioStatus: AlarmEffectStatus, audioEnabled: Boolean, focusDeadlineActive: Boolean):
AlarmServiceMode`, with `AlarmServiceMode { RECORDING, ALERT_ONLY, IDLE }`.
`recordingOwnsService` includes outstanding normal recording finalization, not
merely the service's `running` flag.

- [ ] Write RED injection tests with independently blocked notification/audio
  fakes: offer never blocks; at most one in-flight and one latest pending state
  per lane; rejection does not leak capacity; late work is generation-fenced;
  old completed effects cannot overwrite newer delivery status. No per-beep
  notification posting, no unbounded threads/players or finalization join.
- [ ] Implement dispatcher using separate bounded lanes, guarded submission and
  immutable snapshots. Terminal/Stop changes cannot be lost through ordinary
  status conflation. Expose delivery-failed status, not a recursive alarm.
- [ ] Test/generate two tracked deterministic mono 16-bit WAV resources: 440 Hz
  single and 880 Hz triple, 180 ms beeps/inter-beep gaps, 1,600 ms cycle pause.
  Generator tests validate WAV parameters, pulse counts and distinct pitches.
- [ ] Write RED platform tests: stable distinct titles/icons/IDs; high-importance
  initially silent alarm channels, quiet existing recording channel untouched;
  no location/credentials in payload; no local-only/full-screen flags; blocked
  permission/channel is explicit; toggles do not alter foreground requirements.
  Implement publisher with immutable explicit action PendingIntents carrying
  exact generation/episode scope; stale or external forged actions cannot Stop
  another session. Only app-owned alarm IDs may be canceled.
- [ ] Write RED audio tests: one repeat source, real alarm outranks preview,
  preview stops within 3 s, Stop/recovery cancellation, inaccessible URI uses
  built-in fallback without overwriting choice, focus-loss pause/restore,
  30 s monotonic limit, permanent loss, delayed callback after Stop and denied
  foreground promotion. Use USAGE_ALARM without volume/routing/DND manipulation.
  Allow only system-picker notification content/settings/resource URIs; no network
  sound URLs or unbounded file buffering. System sound cycles never overlap.
- [ ] Implement Android adapters and pure foreground decision function. Active
  audio may retain an alert-only shell, never writers/USB. Focus callback retry
  uses bounded backoff (1/2/4/8 s, capped within 30 s); exhaustion retains visible
  failure where enabled and stops terminal playback ownership. Disabling sound
  or acknowledgement also releases ownership. Use one application-context-scoped
  audio player/coordinator shared by service and settings preview, not two players.
  A preview token is separate from real alarm tokens: Activity teardown or late
  preview completion cannot stop a real alarm that took over playback. Preview
  alone must not create/start a recording service. Compile/test GREEN; commit.

## Task 3: Trustworthy Receiver/Engine Evidence (C3)

**Files:** `RecordingAlarmEvidence.kt`, `RecordingAlarmController.kt`, new evidence/controller tests; service parser/health
hooks; additive RTKLIB revision files/tests as above if no existing decoded-fix
identity suffices. No core raw writer or receiver protocol changes.
**Consumes:** accepted-session identity, typed health/transitions, actual decoded
solutions, configured workflow dependencies. **Produces:** bounded latest
`AlarmCommand` evidence via service-owned controller
`post(command: AlarmCommand): Boolean`, `userStop(generation: Long): Unit`,
`snapshot(): AlarmDesiredState`, `close(): Unit`, plus a distinct 250 ms monotonic
policy ticker. `userStop` invalidates effects without waiting for mailbox drain;
`close` cancels scheduling without waiting for an effect lane.

- [ ] Write RED tests for fresh structured UM980 binary/ASCII, generic NMEA,
  u-blox and selected RTKLIB evidence. Finite valid coordinates and usable fix
  status count; invalid status, cached coordinates, non-position telemetry,
  duplicate snapshots, wall-clock jumps and foreign sessions do not refresh.
  Low output rates use the configured delay without changing parsing semantics.
- [ ] Pin engine selection tests to existing primary/main-engine policy: no
  mock-output requirement, no silently choosing another engine to avoid alarm,
  stationary repeated valid frames remain fresh. A real source change must use
  new-source evidence, not relabel stale old coordinates. A raw-only session
  never arms position loss.
- [ ] Run RED, implement hooks at actual accepted decoded-position events after
  raw append. Use a source revision plus monotonic receipt, not display strings
  or `SolutionCandidate.updatedAtMillis` alone. If RTKLIB needs revision, increment
  on actual newly decoded solution, not every status snapshot; cover both in
  `RtklibWorkerTest` without loading native code on ordinary direct workflows.
- [ ] Implement a bounded mailbox preserving session/Stop/ever-valid and active
  cause state even when intermediate advisory updates conflate. No per-read
  effects or disk/preferences access. Tick independently of
  `startBestSolutionTicker()`, which currently returns when mock output is off.
- [ ] Test typed classifier against USB/RX health, raw storage failure, actual
  required corrections/upload/RTKLIB/command failures and recovery. Exclude normal
  connection progress, disabled/irrelevant components and optional diagnostic,
  export or mock-provider exceptions. Assert messages distinguish degraded
  workflow from stopped raw recording. Compile/test GREEN; commit.

## Task 4: Foreground Lifecycle And Raw Isolation (C4)

**Files:** service/state, `AndroidManifest.xml`, foreground policy;
`RecordingAlarmServiceTest.kt`, `RecordingAlarmIsolationTest.kt`.
**Consumes:** Tasks 1-3. **Produces:** service-owned generation, delivery snapshot,
typed Ack/Stop intents and explicit `RECORDING`, `ALERT_ONLY`, `IDLE` alarm ownership
(separate from existing recording lifecycle); state-query/broadcast alarm fields.

- [ ] Write RED lifecycle tests: initialize on accepted Start; user Stop fences
  before existing `stopping` early-return guard; recovery clears automatically;
  terminal stop closes all recording resources yet preserves active alert; Ack,
  sound-off, focus expiry or a new accepted Start ends old alert-only ownership.
  A plain state query must not recreate recording or audio. No process-restart
  replay; `onDestroy` must not recursively create an alarm/service.
- [ ] Write RED integration tests with blocked/throwing platform effects and
  saturated lanes while byte-exact RX/correction capture proceeds; raw flush,
  writer-close and existing diagnostic leases complete independently. Assert
  alert-only state holds no recorder, USB transport, writers or recording wake
  lock. Neither notification cancellation nor audio release joins capture.
- [ ] Implement service hooks at existing Start/Stop/failure/health boundaries;
  preserve stop provenance and first cause. Split the current unconditional
  `stopForeground`/`stopSelf` tail using pure foreground policy, without changing
  raw cleanup ordering or marking alert-only state as recording. Snapshot live
  settings via observer, never read prefs inside capture/advisory hot loops.
- [ ] Add mediaPlayback permission/type only for actual playback ownership and
  test API 26/33/34/35+ channel, notification permission, type-change/focus behavior.
  Foreground changes initiated within user-started recording lifetime; USB detach
  must not leave a connected-device-only service supporting terminal audio.
  Promotion denial must fail safe, retaining allowed visual evidence, not crash.
- [ ] Verify stale action tokens, Start during previous finalization (existing
  writer barrier preserved), Stop during STARTING/STOPPING/ALERT_ONLY, rapid
  reconnects and Activity recreation. Run app and relevant core suites sequentially;
  commit only after integration GREEN. Native app packaging remains a CI gate.

## Task 5: Alarm Settings And Operational Controls (C5)

**Files:** `ui/alarms/AlarmSettingsScreen.kt`, `AlarmSettingsUiModel.kt`,
`SettingsHub.kt`, `MainActivity.kt`, state intent mapper/tests and new UI tests.
**Consumes:** store snapshot, delivery state and existing service intents.
**Produces:** Menu -> Alarms, system sound picker, preview/stop-preview actions,
permission/channel shortcuts, accessible terminal Ack/Stop actions.

- [ ] Write RED UI-model tests for three separately labeled sections, independent
  visual/audio switches, retained sound when disabled, built-in defaults and
  system sound round trip/cancel. Show current sound name, playback failure and
  effective permission/channel restrictions without claiming watch delivery.
- [ ] Implement normal compact controls: switches for booleans, menu for sound,
  existing-library play/stop icons with accessible names/tooltips, numeric
  stepper/input for 1-60 s delay. No arbitrary unvalidated single-line text field.
  System picker uses TYPE_NOTIFICATION, selected/default URI and cancellation
  preserving prior choice. Preview runs only through the safe audio adapter;
  disable picker/preview while a real alarm owns playback.
- [ ] Add `AppScreen.ALARMS`, SettingsHub callback/row in Session setup and Back
  routing to Menu. Persist settings independently of profile stores/import/apply;
  live edits reconcile without restarting recording. Activity destruction cancels
  preview only, never recording or an active service alarm.
- [ ] Wire terminal actions to remain visible when `isRecording` is false. Keep
  existing Menu/Start/Stop outside scroll content; provide explicit Stop alarm
  without disguising the stopped recording as still running. Show actionable
  system notification settings links and concise unavailable-sound status.
- [ ] Run UI/model/persistence and action-intent tests GREEN; compile both variants.
  Inspect portrait/landscape, light/dark, large fonts, locked screen and touch
  target fit on an Android-capable host; record absent visual evidence as unrun.
  Commit `feat: expose recording alarm settings and actions`.

## Task 6: Documentation, Independent Audit And Closure (C6)

**Files:** `docs/user-workflows.md`, `docs/android-background-operation.md`,
`docs/architecture.md`, `docs/session-format.md` (redacted alarm events only),
`PRIVACY.md` if new local alarm metadata requires clarification, canonical
requirements/matrix, AGENTS guardrails, shared plan tracker and this ledger.

- [ ] Document setup, default patterns, recovery without Ack, intentional Stop,
  terminal failure, sound picker/preview, OS/watch restrictions and process-kill
  limit. No credentials/coordinates in notifications or new alarm event payloads.
- [ ] Map every design section to code/tests/manual evidence. Run focused tests,
  then `ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK sh gradlew
  :app:termuxTestDebugUnitTest :core:capture:test :core:solution:test :core:rtklib:test
  -x :app:processSideloadDebugResources --no-parallel`. Expected zero failures;
  no runnable test is silently disabled for green. Run generator tests with
  `python3 -m unittest discover -s tools -p test_generate_alarm_sounds.py -v`.
- [ ] Stage only scoped helpers/resources/tests/docs. Run `sh scripts/pre_push_check.sh`
  with environment/safe.directory configured for the execution worktree. It must
  compile every JVM test and reject untracked inputs, not merely dry-run tasks.
  Run full strict checker when available; if absent, get explicit gate resolution
  before push. Never extend a past release waiver silently.
- [ ] Obtain one stable-snapshot independent whole-delta review (Sol-high for
  isolation, audio/foreground lifecycle and privacy). Reuse it for focused fixes;
  don't repeat completed audits or terminate a progressing reviewer on timeout.
  Reproduce in-scope findings RED, fix and rerun invalidated gates. Scope is all
  commits/unstaged/untracked task files, not just the last change.
- [ ] Clean-host CI must execute new Robolectric classes, both Google Play and
  sideload test/compile variants, and native debug assembly with pinned upstream.
  Existing stable signing is unchanged. Do not claim a new release is authorized.
- [ ] Record device checks separately: UM980/M8T/selected RTKLIB and raw-only;
  initial acquisition; backpack screen-off no-fix/unplug/replug; connection restored
  without positioning; required NTRIP/upload loss; simulated storage failure;
  sound choices and unavailable URIs; DND/volume/routes; permission/channel denial;
  real watch and lock-screen delivery; rapid Start/Stop and long recording rate.
  Compare byte counts/rates and independent raw-stream checks with alarms on/off.
- [ ] Update tracker as Implemented, not field-tested unless actual field receipts
  exist. Finish scoped commits/push only after allowed gates; leave private inputs
  and unrelated drafts untouched. Report any genuinely blocked acceptance check,
  not a blanket complete claim.

## Plan Self-Review

Coverage: detection/arming/source freshness -> Tasks 1/3; configurable outputs and
sound assets -> Tasks 1/2/5; recovery, Ack and Stop -> Tasks 1/4/5; terminal shell,
focus and OS limits -> Tasks 2/4; hard isolation -> Tasks 2/3/4; watch/privacy/docs
and verification -> Tasks 2/5/6. Review Focus cases each have an owning test task.
Interfaces are single-sourced above; engine-specific revision is internal to Task
3. No mock-output ticker, Activity or alarm worker may own raw recording. No
implementation code has been changed by producing this plan.
