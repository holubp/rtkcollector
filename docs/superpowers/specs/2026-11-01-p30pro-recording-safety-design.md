# P30 Pro Recording Startup And Finalization Safety

Status: design approved by human:holubp on 2026-11-01 after the in-conversation
summary; implementation plan subsequently approved. Decision reference:
`2026-11-01-p30pro-recording-safety-design-approval`.

Independent Sol-high builder check found no consequential unanswered questions.
This records design approval, not execution, field validation or approval of
future canonical requirement/inventory digests.

## Intent And Evidence

Fix the RC10 startup ANR risk and Stop-time raw-file tail loss observed on a
Huawei P30 Pro (VOG-L29, Android 10). Keep existing receiver, workflow, alarm,
NTRIP, storage and recording behavior except for correcting these failures.

The two private October 31 recordings show 5.861 s and 5.847 s between session
creation and capture-worker startup. The service performs initialization in
`onStartCommand`: 33 command delays, 500 ms settling and 2 s draining account
for approximately 5.8 s on Android's main thread. Android retained an ANR trace
at 12:55:24 local. The shorter recording has no finalization and ends shortly
afterward; the retained evidence does not establish the exact process-kill action.

The longer recording's raw stream ends at receiver time 12:35:05.450 local,
while its derived solution reaches 12:35:05.950. User Stop reports
`recorder-close-error` and `CLOSE_FAILED`. Stop interrupts the capture thread;
Android 29's channel-backed stream construction can close a file channel on
interruption. A temporary local reproducer of that construction loses buffered
bytes and reports message-less interrupt/closed-channel exceptions. The newer
host JDK's Files factory does not reproduce that behavior; regression tests must
explicitly cover the older Android construction. Historical exception identity
remains inference because RC10 omitted it. Captures remain local, uncommitted.

## Scope And Approaches

The user requested implementation of the three diagnosed remedies: off-main
initialization, cancellation-safe finalization and improved failure evidence.

Chosen approach: retain foreground-service ownership and current transport,
command and artifact boundaries; introduce an explicitly owned startup task;
make cancellation and writer ownership safe; protect app-private append streams
against interrupt-triggered channel closure; improve existing diagnostics.

Rejected alternatives:
- Increasing timeouts or shortening command waits does not remove blocking IO
  from Android callbacks and risks receiver configuration reliability.
- An untracked background thread leaves Stop free to close writers during
  startup or lets a canceled start publish a new transport/capture worker.
- A new queued raw-writing architecture would change the authoritative capture
  path unnecessarily. No queue is added to move raw writes away from capture.

## Startup Ownership

The main-thread callback admits one validated Start, establishes STARTING and
foreground-service notification promptly, then submits blocking work to a
service-owned startup worker. Main-thread admission may use short in-memory
locks but performs no USB/file IO, command sleeps, baud-drain loops or worker
joins. Foreground admission is not delayed until USB initialization completes.

The startup task owns an immutable accepted setup and generation. Validation,
storage opening, command compilation/transmission, baud transitions and startup
draining run off the main thread. Preserve pre-switch commands, receiver baud
switch, host reconfiguration without purging, recorded RX drain and post-switch
commands in their existing order. RX observed during startup remains byte-exact
and is appended before advisory processing.

Stop synchronously fences the generation and prevents new publication; slow
cancellation/cleanup remains off the main thread. Startup cancellation is
cooperative and must not interrupt a file operation. Stop can cancel only USB
handles owned by that generation. Every newly opened resource must be either
owned by the still-current task/session or closed by the canceled task; a
publication race must never orphan a handle or writer lease.

Writer finalization waits for startup to relinquish ownership, as it already
waits for capture/correction/advisory workers. A timed wait is not proof of exit;
retain STOPPING, writers and actionable progress until the owner actually exits.
Do not wait while holding a lock the startup task needs. Stop during storage
opening, a baud change or partial initialization preserves already created
artifacts. A canceled task must not publish RECORDING, launch capture/NTRIP,
overwrite newer state, clear another session's alarm, or revive the service.

Repeated Start is rejected while startup or non-diagnostic cleanup remains
owned. Repeated Stop remains idempotent. Service destruction also fences startup
and requests service-owned cleanup; Activity recreation does not cancel capture.
State queries, notifications, USB consent and user Stop remain responsive.

## Raw File And Stop Safety

Use an app-private append stream implementation that is not closed merely by
thread interruption, keeping append order, buffering and artifact separation.
The implementation choice is BufferedOutputStream over FileOutputStream in
append mode rather than Files.newOutputStream's older channel-backed path.
Do not change metadata atomic replacement or SAF URI selection semantics.

Do not blindly interrupt capture/persistent-command workers while they can be
inside raw/file IO. Use cooperative cancellation and transport cancellation-close
to unblock USB operations; make command/retry waits cancellation-aware without
interrupting storage. Retain the worker ownership barrier until all possible
writer users have stopped, for both app-private and SAF sessions. Other threads
or libraries may still set interrupt status: app-private writers must preserve
bytes in that case, and genuine provider/storage failures remain explicit errors.

After producer quiescence, flush and close every artifact using existing
failure-isolated finalization. Intentional Stop must preserve all receiver bytes
successfully accepted by the raw append API when storage remains functional.
Keep existing bounded raw-only periodic flushing; do not flush advisory sidecars
from capture. Do not silently suppress close errors, reopen/truncate a failed
raw stream, claim success after a timeout, or write diagnostics into raw bytes.
This does not promise survival of unflushed data after process kill, power loss
or a genuine storage/provider failure.

## Failure Evidence

Extend the existing bounded essential/session diagnostics, not a new reporting
service. Record startup stage transitions with session/generation identity and
elapsed time; include process identity so Activity attachment is not mistaken
for process restart. Keep Minimal independent of Verbose/Debug.

For storage/startup/finalization failures, preserve actual artifact and operation
plus exception class and a bounded cause-class chain even when messages are null.
Messages use existing redaction and size limits; never include raw payloads,
credentials, authorization headers or private profile contents. Diagnostic
failure must not block or abort recording/finalization. Preserve initiating and
contributing Stop reasons and honest incomplete-session classification.

No automatic upload, crash-reporting SDK, UI watchdog or Android-11-only exit
history dependency is added. Android 10 remains supported. No new operator
setting is needed to make recording safe.

## Canonical Specification Impact

Existing requirements remain in force: ARCH-RAW-001/002/003/004,
ARCH-WRITER-001/002, ARCH-TX-001, ARCH-CORR-001, ANDROID-SERVICE-001/002,
ANDROID-CAPTURE-001, ANDROID-DIAGNOSTICS-001/002/003/004,
ANDROID-USB-001/002 and ANDROID-ALARM-004/005.

After approval, add these atomic requirements and their automated/review/manual
evidence obligations to the canonical specification and capability mappings:

- **ANDROID-SERVICE-003: Responsive Owned Startup.** Recording startup MUST
  perform blocking receiver/storage work off the Android main thread while the
  foreground service retains generation-scoped ownership. Stop MUST fence and
  wait for startup before finalizing its writers; canceled startup MUST NOT
  publish active state or workers. Main-thread callbacks MUST NOT wait for it.
- **ARCH-WRITER-003: Cancellation Preserves Accepted Bytes.** Intentional Stop
  and worker cancellation MUST NOT close a session's raw file through thread
  interruption. With functional storage, finalization MUST preserve all receiver
  bytes successfully accepted before producer quiescence and MUST retain ownership
  until writer users have exited. Genuine storage failures MUST remain explicit.
- **ANDROID-DIAGNOSTICS-005: Bounded Failure Identity.** Essential diagnostics
  MUST identify startup stages and failure operations/artifacts with session and
  process correlation. Failure records MUST preserve exception class and bounded
  cause-class information even for null messages, respecting existing privacy,
  bounded delivery and recording-isolation requirements.

Approval of this design authorizes those behaviors; exact canonical requirement
and inventory digests must still be presented for the repository's human approval
record. Do not fabricate approval, copy stale passing receipts or weaken gates.
Owner-authored changes retain the approved GitHub governance exception, not an
exception from source review, tests, strict assurance or signing requirements.

## Verification And Completion

Use RED-to-GREEN tests for prompt Start callbacks with deliberately blocked
initialization, Stop at each opening/publication stage, cancellation/restart
fences, worker-start failure, and responsive state queries. Use injected latches
and deterministic scheduling rather than real multi-second sleeps in unit tests.

Writer tests compare exact bytes across interrupted append/flush, concurrent
Stop, all-artifact close failures and null-message errors. Test the older Android
channel behavior explicitly so a modern host JDK cannot hide the regression.
Exercise SAF-style interrupt-sensitive fake streams and existing writer leases.
Test unchanged baud order, startup RX capture, NTRIP cancellation, alarm Stop/
recovery, metadata and diagnostics redaction/queue-failure behavior.

Run targeted tests, full repository pre-push, the strict assurance checker and
an independent concurrency/data-integrity audit on the complete change. Full
Android/Robolectric/native assembly and both distribution variants run on GitHub
CI; do not attempt local aapt2 packaging in Termux. Record exact commands and
results without treating compiled tests as executed or manual evidence as passed.

Update architecture/background-operation/session-format/operator troubleshooting
documentation and the shared plan tracker. Keep startup and Stop diagnostics
discoverable without implying Android 10 provides historical system kill reasons.

Before claiming a device fix, manually test the P30 Pro profile, responsive Stop
during each initialization phase, repeated Start/Stop, background USB recovery,
app-private/SAF recording, and complete raw tails with working storage. Record
unexecuted hardware/provider checks as pending, never silently waived.

## Delivery Boundary

Implement in the existing clean isolated worktree on
`codex/p30pro-recording-safety`; preserve the dirty root worktree and all private
captures. Keep one checkpointable implementation plan with test and review
evidence. Tightly coupled service lifecycle work stays with the controller; use
one independent concurrency/data-integrity reviewer, not parallel writers in the
shared worktree. Commit and push only after prescribed approval/check gates.
The subsequent user instruction approves next-RC delivery after implementation:
publish RC11 with a GitHub-built stable-signed APK after verification. Do not
rewrite RC10. Device/provider evidence remains separate from build verification.
