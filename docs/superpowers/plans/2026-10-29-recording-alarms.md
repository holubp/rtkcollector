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
the approved Bluetooth extension adds pinned Media3 local playback. No native
RTKLIB changes.

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
- The resumed goal authorizes RC9 publication, not inheritance of the RC8-specific
  strict-checker exception. Resolve the current required push gate before pushing.

## Review Focus

### Pre-RC9 Bluetooth Extension (Historical Intake)

This intake predates the owner's all-three approval. Current approvals and
execution checkpoints B0-B4 are in the ledger below; pending wording in this
historical intake must not be treated as a current approval blocker.

After local checkpoint `5fc760e`, the owner requested an Alarms-menu toggle to
route alarm audio through a connected Bluetooth audio device, before releasing
RC9. This is additional required scope, not implemented by that checkpoint and
not covered by its existing passing test receipts. Do not publish RC9 without
implementing and verifying this extension after its design is approved.

Proposal sent for discussion: persist an initially-off toggle, leave current
Android routing unchanged when off, and request a connected Bluetooth audio
output for all alarm categories (and preview) when on. This changes the original
no-routing-override design; it must use isolated per-player routing, not global
audio-mode, volume or DND changes, and cannot couple to raw capture.

Human decision received: YES, immediately fall back to the phone speaker if
Bluetooth disconnects or cannot play. Proposed default-off/preview behavior and
the complete design remain subject to approval in
`2026-10-29-alarm-bluetooth-routing-design.md`. After approval, extend the
checkpointable implementation plan and canonical requirements before coding.
Existing detached-device permission and RC9 strict-assurance decisions remain
pending; the Bluetooth fallback answer does not approve them.

Reconnaissance: the current player uses `Ringtone`, which does not expose a
per-player preferred-device setter. Android `AudioRouting` provides preferred
and actual-route APIs but explicitly does not guarantee that a preferred output
is the actual route. A routing-capable playback adapter and route verification
will therefore be needed; merely changing audio attributes is not proof of
Bluetooth delivery. Sources inspected:
https://developer.android.com/reference/android/media/Ringtone and
https://developer.android.com/reference/android/media/AudioRouting#getPreferredDevice().

The original review focus below still applies to the existing implementation.

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

Owner approval update (2026-10-29): human:holubp explicitly said "all three
approved" after reviewing (1) the Bluetooth design, (2) normal
CHANGE_NETWORK_STATE permission rationale and (3) RC9-specific strict-assurance
deferral with checked-in gates, independent review and GitHub CI. All three
decisions are approved, including the earlier explicit YES to immediate speaker
fallback. Historical pending-decision entries below are retained as receipts,
not current blockers. Continue the approved native execution method through the
Bluetooth extension, verification and RC9 publication; do not ask again for these
same approvals or claim the absent strict checker passed.

### Bluetooth Extension Checkpoints (B0-B4)

### Additional Owner Scope Before RC9 (2026-10-30)

The owner requests a live monitoring DND advisory and an Astra review/fix/
re-review covering alarm usability, persistent USB reconnection while recording
is intended on, and app stability. These are release prerequisites, not deferrals.

| Checkpoint | Owner | State | Completion evidence |
| --- | --- | --- | --- |
| A1 | Controller: DND advisory | Implemented; device check pending | ANDROID-ALARM-008; truth-table tests and monitoring live refresh; focused GREEN |
| A2 | Astra-medium: focused audit and fix plan | Done | Eight concrete findings F1-F8 below; read-only review |
| A3 | Controller: implement audit fixes | Done | Focused regressions and fresh clean full gate GREEN; Astra source-closed all fixes |
| A4 | Same Astra: independent re-review | Source-closed | Astra re-reviewed original scope and all F1-F8, then epoch-retirement correction; no remaining material source findings. Tests/CI/device evidence separate. |
| A5 | Controller: final gates and RC9 | Done; field checks separate | Local 1,341/192 and host 2,470/346 GREEN; full release lint/native assembly/signing-tool checks pass. Stable build 41019722166, independent artifact verification and published RC9 confirmed. |

Routing evidence: Astra child 01a18a4a-64a8-72a0-b4e5-b8db9eb4fb2f runtime
turn_context reports model gpt-6-astra, effort medium. Initial reviewer falsely
reported routing unavailable; controller supplied that runtime evidence and
asked the same session to proceed, without switching models or spawning again.

A1 implementation contract (approved by the owner's explicit request):
- [ ] Add pure `alarmDndWarning(recording, acousticEnabled, dndEnabled)` policy
  and all eight Boolean combinations, exact advisory wording and unknown-state
  handling tests before implementation.
- [ ] Add a Home-scoped Compose observer in `ui/alarms/AlarmDndWarningState.kt`:
  observe AlarmSettingsStore changes; query NotificationManager interruption
  filter off-main, at most once per second while active/acoustic, and cancel on
  leaving Home. No new permission, global DND/volume setter or capture reference.
- [ ] Add the advisory to existing Home alarm/advisory content below setup
  controls and above monitoring cards, even without an active alarm episode.
  Do not populate service error fields or offer Ack for the DND condition.
- [ ] Update user docs, verification matrix and release limitations. Run focused
  tests and then the final combined gates after A3/A4, not duplicate full gates
  between every small correction.

Known A3 fix already independently confirmed before the new Astra audit:
speaker -> Bluetooth reconnection -> native error before actual BT confirmation
must be attributed to the new Bluetooth request, not the old speaker attempt.
Record nonfailed BT requests before their native setter; retain old Bluetooth
attribution while speaker is only pending. Add the exact regression and keep
displayed actual-route confirmation separate from request attribution.

2026-10-30 A1 execution receipt: DND truth table/filter mapping and requested-BT
reconnection attribution passed focused GREEN (`a1-reconnect-green.log`) after
observed missing-API RED (`a1-reconnect-red.log`); production/all app JVM tests
compiled. The pre-extension full clean gate completed with 1,319 tests/188 suites,
zero failures/errors/skips (`pre-push-final-bluetooth.log`), but does not cover A1
or the subsequent attribution correction. Astra A2 remains active. No release
or final combined gate is claimed.

Astra A2 source findings/fix ledger (paths under app/src/main/kotlin):
| Finding | Required correction | Regression oracle | State |
| --- | --- | --- | --- |
| F1 P1 | AlarmAudioPlayer results carry original desired revision/selection, never relabel old failure with current state | Block A, select B, A fails: B not suppressed and starts once | RED added; controller implementing |
| F2 P1 | Complete USB reconnect enumeration/permission/report/open attempt is recoverably guarded; Stop/storage semantics retained | Each platform step throws, later recovery uses unchanged artifacts; Stop prevents promotion | Open |
| F3 P1 | Routine foreground notification delivery bounded/conflated off capture; initial FGS separate | Block notify while RX continues byte-exact; finalization independent; no late status resurrection | Open |
| F4 P2 | Promoted USB device identity visible to detach listener and fenced by active session | A reconnects as B; detach B alerts immediately once; stale/unrelated detach ignored | Open |
| F5 P2 | Reopen/init without RX remains degraded awaiting bytes; preserve unrelated correction/protocol evidence | Empty reads after reopen stay inactive/retry; actual raw append alone recovers RX health | Open |
| F6 P2 | Terminal playback/preview failure abandons focus before native stop and fences late focus callbacks | Fail preview with no further UI input; focus released; no resurrection/player accumulation | RED added; controller implementing |

F2/F3/F5 are inherited baseline defects, not Bluetooth regressions. Locations:
RecordingForegroundService reconnect around1469-1527, routine notification
delivery1385/3478; AudioPlayer report214 and tick87-104 (lines shift with edits).
Remaining A2 continuation: USB cancellation/TX/permission recovery and test gaps.

A2 final scope (Astra-medium): F1-F8, no further initial-audit pass pending.
F7 P1: Stop takes TX/shutdown locks on the service callback before cancelling
capture; queued correction/init work lacks admission/per-command cancellation.
F8 P2: normal replacement closes the transport outside TX exclusion; persistent
baud mismatch windows/probes admit correction bytes. Permission guidance must
say approve and await reconnect while recording, not press Start again.

A3 concrete implementation/test checkpoints:
- [x] F1/F6: originating audio report identity, preview/service separation and
  focus-token invalidation/cleanup before native disposal. Focused RED/GREEN.
- [x] F3: `RecordingNotificationDelivery` bounded process-owned latest-update
  actor, epoch/revision-fenced clear; app-context Android builder off capture.
  Wire routine updates and lifecycle invalidation; initial FGS remains separate.
  RED/GREEN blocked post + continued byte-exact RX/close and newer-owner test.
- [x] F2/F4/F5: `runUsbReconnectAttempt` guards the complete attempt including
  enumeration/permission/reporting. `UsbReconnectIdentity` fences promotion and
  publishes the new ID. Reopen leaves RAW inactive/awaiting RX without resetting
  independent protocol/correction health. Test each throw, Stop/stale promotion,
  silent reopen, genuine RX recovery and same-artifact continuity.
- [x] F7/F8: `ReceiverTxGate` uses interruptible reentrant admission for writes
  and entire normal replacement/baud transitions/probes; recheck runtime/active
  ownership after admission and before each non-shutdown step. Stop first fences
  running/USB ownership, interrupts pending work and dispatches cleanup off main.
  Shutdown is best effort once: bounded TX admission, otherwise cancel-close
  without claiming commands were sent. Writer closure still waits for producers.
  Test blocked TX, queued cancellation, transition/probe exclusion, no promotion
  after Stop and at-most-once shutdown. Update permission guidance and docs.
- [x] A4 Astra re-review fixes and current DND/Bluetooth delta with actual receipts.
- [x] A5 one combined final local gate + GitHub CI; verify stable-signed RC9 APK.

A3 receipts (local, ignored `.superpowers/sdd/2026-10-29-recording-alarms/`):
`astra-audio-corrected.log`, `astra-integrated-green.log` and
`astra-a3-green.log` are BUILD SUCCESSFUL. The integrated run covered 52 tests
in ten suites; the last run includes retrying Stop cleanup after a transient
failure without repeating its cancellation fence. All app JVM sources compile.
Robolectric builder/persistence checks are compile-only locally, with SDK
26/27/33 execution required in host CI. These are not device audibility receipts.

A4 first re-review: Astra source-closed F1/F2/F4/F5/F6/F7/F8; F3 retained
one P2 race: a publisher passing the destruction check before `onDestroy` can
offer a later revision after the clear. Controller added atomic, irrevocable
epoch retirement to the pure delivery lane and a deterministic blocked-post /
late-revision regression. No additional material Bluetooth/DND findings.
`pre-push-astra-final.log` is RED at test compilation for the newly introduced
retirement API; it is not a final acceptance receipt. Production compilation
passed. `astra-retirement-green.log` reruns the focused test after implementation;
the final combined gate must run again on the complete corrected snapshot.

A4 final source closure: same Astra-medium reviewer inspected the retirement
correction and regression sources, and found no remaining material source
findings in F1-F8 or original alarms/Bluetooth/DND scope. Synchronized admission
is independent of platform delivery; older retirement cannot clear a newer
generation. Reviewer explicitly did not claim the still-running focused test,
final gate, host CI or physical-device checks passed. Review context retained;
agent closed after final result, not because of timeout.

A5 local gate: `pre-push-astra-closed.log` completed with pre-push checks passed
after a clean rebuild. XML totals: 1,340 tests / 192 suites, zero failures,
errors or skips. All app JVM sources compiled, including host-only Robolectric
checks, and both IDE compatibility aliases dry-ran successfully. The retirement
focused rerun also completed BUILD SUCCESSFUL. Host CI and signed APK verification
are the remaining automated release gates; physical-device checks remain open.

A5 host RED: GitHub run `40992251047` found nine failures (three cases on
SDK 26/27/33). The pinned Media3 builder forbids `BandwidthMeter.NO_OP`; use a
valid local fixed-estimate meter with no network observer. The callback test
must measure from post-registration query count, and resource URI tests require
merged Android resources. Controller fixed those without disabling any tests.
The local focused repair compiles and passes. Astra re-reviewed and source-closed
the repair, then identified missing unstable-API opt-in; narrow annotations were
added to both production declarations and their platform test. Full release
lint for both distributions is now an explicit CI gate after native-source
provisioning. Host RED-to-GREEN and final local gate remain required. The
interrupted `pre-push-release-record.log` has no terminal receipt and is not a pass.

Repair gate: `pre-push-ci-repair.log` passed after a clean rebuild, with 1,340
tests / 192 suites and zero failures/errors/skips. Both full release-lint task
names dry-ran successfully. Astra source-closed the narrow annotations and
reviewed CI ordering; it explicitly required actual host lint and SDK tests.

Host run `41014653701` no longer reports the original SDK 26/27/33 alarm
failures. Seven legacy default-SDK Robolectric suites instead fail sandbox
initialization: merged resources selects manifest API 36, requiring JDK 21;
CI was still on JDK 17. CI and release workflows now use JDK 21, as do host
instructions; source/bytecode targets remain 17. No SDK coverage is lowered.
The next exact-commit host run must establish complete GREEN, not infer it
from the absence of the earlier three failures.

Downloaded host XML positively confirms all 18 `AlarmPlatformTest` cases passed
in run `41014653701`, zero failures/errors/skips. Astra narrow environment review
found no conflicting test/toolchain pins; JDK 21 does not change bytecode targets.
`pre-push-jdk21-ci.log` passed the full local gate before the workflow update push.

Host run `41016179789`: downloaded XML proves 2,468 tests in 346 suites,
zero failures/errors/skips, including both distribution variants and API 36.
Full release lint then found one pre-existing legacy picker `WrongConstant`
error. Controller added explicit read/write grant validation before persisting
the exact permitted constants, preserving failure-before-profile-mutation.
`RecordingStorageTest` covers missing/partial grants and unrelated result flags.
This implements existing ANDROID-STORAGE-001, not a new access policy. CI now
retains full lint reports on failures as well as successes. Actual lint GREEN
and native assembly/signing are still required before RC9 tagging/publication.

SAF repair source review closed after controller/reviewer caught and corrected
the caller import. `saf-grants-corrected-green.log` passed all four storage tests;
`pre-push-saf-lint.log` then passed the clean full gate: 1,341 tests / 192 suites,
zero failures/errors/skips. The earlier `saf-grants-green.log` is a compilation
RED receipt, not a pass. No lint baseline or global suppression was added.

Final source gate: CI `41018587649` completed SUCCESS at
`1bab0d21ffed6eef298a3e3c2d1e2faffc64a3c8`. Downloaded XML proves 2,470 tests /
346 suites, zero failures/errors/skips. Both full release-lint tasks, pinned
native debug assembly and disposable signing-tool checks passed. Lint XML has
no errors. Alarm-related constant warnings are inlined values used behind
SDK checks; the singleton context warning is application-context-only ownership,
not a retained Activity. Style/dependency-update warnings do not waive any gate.
Tag `v1.0-RC9` points to this tested source. Stable-signed draft build
`41019722166` must finish and its APK be independently checked before publishing.

Publication receipt: build `41019722166` completed SUCCESS. Downloaded APK
checksum matched `e4378747c5745c204f0bc878f032263663c9d3f6c06083d1a852a2b53d590f43`;
`apksigner` verified v2/v3 and existing certificate
`d8047f43b2748198ae69ff773be13d3aa11c8ac36159dd4e6768a3ac2db3e329`.
Package `org.rtkcollector.app`, version `1.0-RC9` / code `10`, native ARM ABIs,
alarm WAVs/notices and merged permissions verified. Manifest-decoder type `0x12`
is the expected connectedDevice/mediaPlayback bitmask, not symbolic text.
GitHub confirms public prerelease `v1.0-RC9`, published 2026-10-30T07:28:50Z,
with APK/checksum/signing record and tagged source archive links. Source/tooling
provenance both equal `1bab0d21ffed6eef298a3e3c2d1e2faffc64a3c8`.
Physical device, routing, watch, vendor power and high-rate checks remain open;
publication is not their execution or a strict-assurance checker pass.

Final documentation gate: `pre-push-final-release-record.log` passed on the
unchanged released implementation. Specification consistency and diff checks
were repeated after recording publication evidence. The published source ZIP
was downloaded and its critical implementation/build/pinned-upstream files
compared byte-for-byte to the release tag. Only this release-status record and
matrix/plan documentation change after the tag; the APK source tag is not moved.

| Checkpoint | Scope | State | Evidence required |
| --- | --- | --- | --- |
| B0 | Approval, canonical requirements and implementation plan | Done | Owner statement above; ANDROID-ALARM-007 |
| B1 | Persistent toggle, UI and approved foreground qualifier | Done; physical checks separate | Host SDK 26/27/33 persistence/platform tests GREEN; approved qualifier declared |
| B2 | Pure device selection and route confirmation/fallback | Done | Focused route-policy and attempt-attribution tests GREEN; independent source review closed |
| B3 | Routing-capable playback integration | Implemented, not field-tested | Focused and full host tests GREEN; source/native-boundary reviews closed; physical routing/audibility checks open |
| B4 | Final audit, clean-host CI and RC9 release | Done; field checks separate | Local/host gates GREEN; stable APK independently verified and RC9 published |

2026-10-30 execution checkpoint: B1 toggle/store/UI and approved foreground
permission are implemented. B2 pure route policy and B3 clip ownership passed
focused Termux tests (`b3-compile.log`): 13 tests, no failures. Prior RED was
missing clip interfaces; route-label assertion caught capitalization and was
fixed. Local Media3 1.11.1 production compilation passed. Full clean pre-push
gate and renewed independent review are running; host-only/physical routing
evidence and publication remain pending. No task is declared complete here.

Independent B3 review found four blocking adapter defects: release timeout is
delivered as an error callback rather than playerError; Bluetooth preparation
failure skipped speaker; lifetime retry latch skipped fallback after reconnect;
system-sound repeats reused the original preparation deadline. Findings accepted
after checking pinned ExoPlayerImpl.release and local state transitions. Added
AlarmPlaybackProgressTest for all four oracles before correction. Interrupted
full gate produced no completion receipt and is not counted as a pass. Resume
uses one Gradle worker/1,536 MiB heap to reduce local resource pressure.

Native-boundary follow-up adds attempt-route attribution (simultaneous Bluetooth
disconnect/error must not reject the newly desired speaker), cancellation fences
around output creation and play, and a bounded 500 ms check of Media3's pending
native releases after player release. New tests first failed for missing APIs;
these APIs are now implemented and their focused run is pending.

Owner-requested cross-project comparison: inspected OSMTracker commit
967304e61e65031682fea14591e3b1bd2035e6c3 and VoiceRecDialog.createSoundPlayer.
Its NO_INIT root cause was preferred-device selection before setDataSource.
RtkCollector's pinned Media3 ExoPlayerImpl queues preference; DefaultAudioSink
stores it until an output exists, then applies it. RtkCollector now explicitly
tests source -> route -> prepare -> play ordering and guards direct AudioTrack
access with STATE_INITIALIZED, including replacement outputs. Do not transplant
OSMTracker's global communication/SCO setup or stop-cue/tail delays: GNSS user
Stop must silence alarms, not wait for another beep. Headset audibility is still
a device test, not proved by source order or Media3 preference acceptance.

2026-10-30 B3 source closure: independent reviewer closed every reported
adapter/callback/native-boundary finding, including explicit failed-device kind
after target changes. Final focused receipt `b3-complete-focused.log` passed
27 tests in five suites, zero failures/errors/skips; all app JVM tests compiled.
The source is frozen for the fresh full checked-in gate, then clean-host CI.
Bluetooth/watch/device validation remains unexecuted. B4/release is not done.

#### B1: Configuration And Foreground Eligibility

Files: `alarms/AlarmModels.kt`, `AlarmSettingsStore.kt`,
`ui/alarms/AlarmSettingsUiModel.kt`, `AlarmSettingsScreen.kt`,
`RecordingForegroundService.kt`, manifest and corresponding existing tests.

- [ ] Add failing tests for `AlarmSettingsSnapshot.preferBluetoothAudio: Boolean`
  (default false), `AlarmSettingsUiModel.bluetooth(enabled: Boolean)`, JSON
  round-trip and absent-field migration retaining false. Category/sound/delay
  edits must preserve the global routing choice. Run focused tests RED.
- [ ] Add the defaulted trailing field; persist JSON key `preferBluetoothAudio`.
  Add the Alarms switch "Use Bluetooth audio for alarms". Service's off-capture
  settings refresh and screen initialization/save call
  `AlarmAudioPlayer.setBluetoothPreference(enabled: Boolean)`; changes must
  wake playback without restarting recording or changing alarm episode IDs.
- [ ] Declare approved normal `CHANGE_NETWORK_STATE`; test declaration and
  document its detached connectedDevice eligibility rationale. Do not call
  network-changing APIs. Run focused GREEN and compile all JVM test sources.

#### B2: Pure Routing Contract

Create `alarms/AlarmRoutingPolicy.kt` and `AlarmRoutingPolicyTest.kt`.
Interfaces: `AlarmRouteDevice(id: Int, kind: AlarmRouteKind, priority: Int = 0)`;
`AlarmRouteKind { BLUETOOTH, SPEAKER, OTHER }`; decision fields `deviceId: Int?`,
`confirmed: Boolean`, `fallback: Boolean`, `failed: Boolean`, `label: String`,
`warning: String?`. `AlarmRoutingPolicy.decide(enabled: Boolean,
devices: List<AlarmRouteDevice>, actualDeviceId: Int?, now: Long,
availabilityVersion: Long, playing: Boolean = true): AlarmRouteDecision`;
`reject(deviceId: Int): Unit`, `reset(): Unit`.

- [ ] RED tests: disabled -> default/null, Bluetooth chosen/sticky/deterministic,
  media-capable outputs preferred to voice-only outputs, no Bluetooth -> speaker,
  disconnect -> immediate speaker, setter rejection -> immediate speaker,
  preferred != actual -> no false confirmation, 500 ms route-confirmation bound
  while actually playing -> speaker fallback, missing/rejected speaker -> failure,
  rejected Bluetooth not retried on every tick, availability change/new alarm
  permits retry, preparation does not consume the routing confirmation window.
- [ ] Implement bounded failed-device memory (at most 16 IDs), stable ordering
  by priority/ID, current usable Bluetooth retention and monotonic timing. No
  Android/platform calls in the policy. Actual route, not requested preference,
  decides confirmation. Fallback with no connected Bluetooth is metadata, not
  an operational error; an actual rejected route has actionable feedback.
- [ ] Run policy tests GREEN; retain existing alarm-policy regressions.

#### B3: Playback And Actual Route Verification

Files: `AlarmAudioPlayer.kt`; create `AlarmClipPlayback.kt`,
`Media3AlarmClip.kt`, `AlarmAudioDevices.kt`; dependency/notices and tests.

Engineering choice: pin `androidx.media3:media3-exoplayer:1.11.1` for the opted-in
path. Its downloaded AAR metadata confirms minCompileSdk 36 (the project floor);
upstream AudioTrack-based routing supports SDK 26, unlike MediaPlayer's API-28
setter. Use its existing decoder/streaming buffers rather than implementing a
codec/WAV engine. Preserve the legacy Ringtone path when routing is off. Record
Apache-2.0 attribution; no media UI, session, ads or network-streaming features.

Dependency inspection: Media3 declares optional ACCESS_NETWORK_STATE. Remove
that declaration through the manifest merger and use a local fixed-estimate
bandwidth meter with no network listener (Media3 rejects its NO_OP sentinel);
local alarm playback does not need another permission. Disable platform media
diagnostics. Verify the merged release manifest in CI before distribution.

- [ ] Define/inject `AlarmClipPlayback` state (PREPARING/PLAYING/ENDED/FAILED),
  `poll(now: Long)`, `stop()`, route metadata and failure feedback so native-free
  fakes can verify cancellation, fallback and one-player ownership. Observe RED
  for delayed preparation/route callbacks after Stop or a new alarm, blocked
  native operations/raw finalization, real-alarm/preview arbitration and release
  failure preventing another player from accumulating.
- [ ] Keep the one app-owned audio worker, using a HandlerThread/Looper for
  Media3 calls. Create Media3 lazily only for the Bluetooth-enabled path, with
  USAGE_ALARM and library focus handling disabled (existing focus policy owns it).
  Bounded load control: 500 ms minimum, 2,000 ms maximum, 250 ms initial playback,
  500 ms rebuffer and 512 KiB target. Accept only existing validated
  content/android.resource sound URIs. Preserve saved choices on fallback.
- [ ] Wrap `AudioTrackAudioOutputProvider` via `ForwardingAudioOutputProvider`
  to retain the current `AudioTrackAudioOutput.audioTrack` solely for route
  inspection. Use `ExoPlayer.setPreferredAudioDevice` and AudioTrack's supported
  per-player setter; never use global communication/SCO/speakerphone APIs.
  Audio device callbacks and all native queries stay on the alarm worker.
- [ ] Use policy decisions to verify actual output, update compact Alarms route
  metadata separately from generic error messages, immediately request speaker
  on disconnect/known Bluetooth failure and retry Bluetooth only on approved
  triggers. Guard every asynchronous callback by current selection/player.
- [ ] Reuse the routed player within an episode: built-in WAVs already include
  pause; system sounds retain 1,600 ms between repeats. Preview ends within 3 s.
  Toggle changes affect playback only; do not recreate focus for the same episode.
  Release before replacing a player; fail closed on unconfirmed/failed release.
  Preserve external pending/stalled-worker deadlines and terminal-shell teardown.
- [ ] Add SDK 26/27 coverage to the relevant Android tests without silently
  excluding them from clean-host CI; register any new Robolectric class in the
  exact Termux inventory. Run focused GREEN, both production Kotlin variants and
  byte-exact isolation tests. Physical Bluetooth routing remains a separate test.

#### B4: Review, Evidence And Publication

- [ ] Update canonical matrix, user workflows, privacy/Play rationale, README,
  notices, RC9 release notes and this ledger. Record actual approvals, not a
  fabricated strict-checker pass. Keep source version 1.0-RC9/code 10.
- [ ] Run `sh scripts/pre_push_check.sh` sequentially; preserve complete app test
  compilation. Reuse the same independent reviewer for the changed playback
  boundary; Sol-high is justified by native resource ownership/cancellation and
  capture isolation. Fix material findings and rerun invalidated checks.
- [ ] Recheck remote main, push scoped commits, obtain passing clean-host CI
  including both variants/Robolectric/native build. Create v1.0-RC9 and draft
  prerelease, run `release-debug-apk.yml`, download and independently verify APK
  hash, version/package and pinned stable signing certificate before publishing.
  Keep manual Bluetooth/watch/vendor/high-rate tests explicitly unexecuted if
  no device evidence exists; never call RC9 published before artifact checks.

Self-review: configuration/approval -> B1; routing/actual verification/retry ->
B2/B3; min SDK/sound formats/native isolation -> B3; docs, specialist review and
publication -> B4. Stop/release races, actual/preferred mismatch, old SDKs,
unavailable sounds and reconnect retry storms each have an owning test step.
Technical library choice is a controller decision, not a claim of human choosing
a dependency. Existing user authorization to extend the plan and implement is
retained; no additional product choice is being introduced.

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
C4 review correction: independent Sol-high review identified detached USB type
eligibility, main-thread foreground Binder work, rejected promotion retaining
playback, and missing independent timeout. Process-owned effect lanes now isolate
foreground work from notifications and main; a bounded independent exit lane
uses captured start IDs. `review-correction-red.log` confirmed absent heartbeat
expiry support; `review-correction-green.log` passed the focused suite after
adding pending/focus-loss deadlines and active-worker heartbeat checks. These
are local JVM results, not Android device playback evidence.
Ruling: remove unnecessary `dataSync` type/permission instead of using it as a
disconnected fallback: it carries Android 15+ six-hour background limits and does
not match continuous USB interaction. Capture uses `connectedDevice`, acoustic
ownership adds `mediaPlayback`. `foreground-types-red.log` confirmed the missing
type-mask API. The documented normal `CHANGE_NETWORK_STATE` alternative for
detached `connectedDevice` eligibility still awaits human approval; do not add
it without that approval. Foreground rationale and Play validation docs updated.
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
At plan creation, design was approved (including automatic recovery) and product
implementation had not started. Current status is the checkpoint table below.
One Sol-medium read-only design builder check completed; all three ambiguities
are resolved in the approved design. No extra planning reviewer is required.

| Checkpoint | Owner / scope | State | Required receipt |
| --- | --- | --- | --- |
| C0 | Controller: canonical contract and environment | Done | Canonical contract present; owner approved RC9-only alternate assurance gates and normal detached-device permission |
| C1 | Pure policy + persistent settings | Done; device checks separate | `bdd99b7` foundation; final host policy/persistence tests GREEN |
| C2 | Independent effect lanes and Android adapters | Implemented, not field-tested | `e9ff9d0` foundation; close/invalidation regressions and final host tests GREEN; source audit closed; physical delivery checks remain open |
| C3 | Source evidence and detection | Implemented; not field-tested | Source selection and epoch/freshness tests GREEN |
| C4 | Service lifecycle and terminal alerts | Implemented, not field-tested | Permission approved and declared; original review and Astra F1-F8 source-closed; host tests GREEN; physical lifecycle checks open |
| C5 | Settings, sound picker and operational actions | Implemented; device checks pending | Menu/controls wired; integrated Kotlin compilation GREEN |
| C6 | Integration, docs and independent audit | Done; field checks separate | Whole-delta source audits/gates closed; stable-signed RC9 published. Physical validation remains explicitly unexecuted. |

Integration gate receipt: `pre-push-integration.log` completed successfully on
2026-10-29 after a clean rebuild: 1,282 tests across 181 current app/core/receiver
suites, zero failures/errors/skips; all app JVM-test sources compiled and both IDE
compatibility aliases checked. Test counts exclude retained historical CI reports.
The current checker `tools/check_spec_requirements.py` passed; absent
`tools/specification/spec_check.py` was not claimed as executed. Independent final
audit is active. A subsequent Home delivery-feedback gap was reproduced RED in
`ui-delivery-red.log`; its fix requires fresh focused/full gate receipts before
commit, so the earlier gate is not a final passing receipt for that delta.

Independent full-delta Sol-high audit returned eight findings. Detached foreground
eligibility remains blocked on the requested permission approval. The seven code
findings were fixed together: asynchronous bounded alarm-state broadcast delivery;
atomic pending terminal ownership before policy consumption; mock-independent
AUTO_BEST evidence ranking; preserved recovery/re-failure edges (including equal
receipt timestamps); acoustic-only Home controls; generation/revision-scoped late
delivery failure handling; and bounded first/last episode evidence, including
later terminal cleanup evidence without rearming acknowledged episodes.
`review-edges-red.log` executed six tests with two expected failures (conflation
and hidden acoustic-only actions). `review-contracts-red.log` confirmed missing
evidence/broadcast/terminal APIs. `review-contracts-green.log` and
`review-fixes-green.log` passed all focused Alarm tests and compiled all app JVM
tests; service Robolectric execution still requires clean-host CI. The same
reviewer is checking the affected corrections, not repeating the whole audit.
No complex implementer children were used; controller kept coupled service work
inline and reused one requested Sol-high reviewer for material lifecycle risk.
Actual runtime model/effort and allowance attribution are not exposed.
Resume after process interruption: `pre-push-reviewed-fixes.log` ended during
production compilation, and no RtkCollector Gradle process survived; this is an
interrupted gate, not a pass. The first focused resume attempt
(`operational-episodes-red.log`) failed during configuration because dependency
JARs removed by clean had not been regenerated. It did not reach the intentional
`activeEpisodes` regression and is not counted as behavioral RED. Rebuild
production prerequisites and seed the Termux R.jar before rerunning that test.
The same independent reviewer was resumed by its existing ID, retaining context.
It confirmed prior findings 2-5 and 7-8 addressed in source, with finding 6 still
requiring the complete operational episode list independently of notification
filtering/audio arbitration. Verification and approval blockers remain open.
After production prerequisites were rebuilt, `operational-episodes-red-compiled.log`
reached the intended missing-`activeEpisodes` compilation failure. The model now
retains unresolved operational causes separately from visible notifications and
the one selected sound. Home text/tokens use operational episodes (with redundant
position warnings suppressed during known receiver loss), and nonterminal
acknowledged conditions remain until recovery. Stop clears those episodes and
advances the revision fence against queued pre-Stop delivery errors. Focused
GREEN and a fresh whole gate are still required for this latest delta.
`operational-episodes-green.log` passed after that final model/Home/Stop change,
including production and all app JVM-test compilation. The independent reviewer
then confirmed no additional defect in the narrow correction review and closed
findings 2-8 at source level. Finding 1 (detached foreground eligibility) remains
open pending explicit permission approval. `pre-push-final-source.log` is the
fresh whole-gate attempt for this snapshot; do not infer its outcome until read.

The checkpoint table and dated receipts are current implementation status. The
original composite task checkboxes below include clean-host/device acceptance
steps and must not be bulk-completed from source review or local compilation.
Final local gate receipt: `pre-push-final-source.log` completed successfully after
clean on the final source snapshot: 1,293 tests in 184 app/core/receiver suites,
zero failures/errors/skips, all app JVM-test sources compiled and both IDE aliases
dry-run. Sound generator, signing-tool tests, test-gate tests and structural
requirement checker also passed. This is the checked-in Termux gate only, not
the absent strict-assurance checker or clean-host/device validation. Separate
Google Play production Kotlin compilation also passed sequentially
(`google-play-production-compile.log`). This does not execute Google Play
Robolectric tests or assemble an APK/AAB; those remain clean-host CI gates.
Remaining authority: explicit normal `CHANGE_NETWORK_STATE` permission approval
and RC9-specific strict-gate resolution are required before push/release. Keep
this goal active; no RC9 publication/completion claim is authorized by local tests.

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
- `AlarmEffectsRuntime.kt`, `AlarmPlaybackDeadline.kt`, `AlarmStateBroadcaster.kt`:
  process-owned effect/foreground/broadcast lanes, independent playback deadline,
  scoped service exit and bounded asynchronous Home-state delivery.
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
data class AlarmEpisodeEvidence(val firstElapsedMillis: Long, val lastElapsedMillis: Long,
    val detail: String)
data class AlarmAudioSelection(val episodeId: AlarmEpisodeId, val sound: AlarmSoundChoice)
data class AlarmDesiredState(val generation: Long, val revision: Long,
    val visibleEpisodes: List<AlarmEpisode>, val audio: AlarmAudioSelection?,
    val terminalAlertPending: Boolean, val activeEpisodes: List<AlarmEpisode> = visibleEpisodes)
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
  Existing stable signing is unchanged. RC9 publication is explicitly authorized
  by the resumed goal; the strict-gate exception remains separately pending.
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
