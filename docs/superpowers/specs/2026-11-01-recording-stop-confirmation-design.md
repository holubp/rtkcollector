# Recording Stop Confirmation

Status: Approved by human:holubp in this conversation, including the proposed
ranges and lifecycle-cancellation behaviour, after selecting approach 1.

## Intent And Evidence

Prevent accidental explicit Stop actions without interrupting receiver recording
while the user decides. Improve attribution of intentional stop requests.

The supplied RC11 session ended at 2026-11-01T14:04:52.434Z with a USER stop
request and successful finalization, corroborated by Android foreground-service
history. Neither capture identifies which control originated the request or
proves a deliberate touch. The original trigger remains unproven; confirmation
is an approved direction for prevention, not a demonstrated root-cause fix.
Private bugreports and recordings remain outside version control.

## Approaches

1. Shared, session-bound confirmation with elapsed-time deadlines: consistent
   protection, stale-session rejection and testable timing. Selected by the
   user; recommended because multiple explicit Stop controls exist.
2. Separate per-control dialogs: smaller initial changes but duplicated timing
   and inconsistent protection. Not selected.

## Agreed Behaviour

- Explicit recording Stop controls open one shared confirmation flow rather
  than dispatching Stop immediately, including the settings-recovery screen
  and any reachable legacy recording Stop control.
- Recording, correction intake, USB recovery and alarms continue normally
  while the dialog is open. Opening it does not acknowledge an alarm.
- Confirmation is initially disabled, with a countdown inside its button,
  updated each second: for example, `Confirm (2s)` then `Confirm (1s)`.
- Once enabled, the second countdown begins inside the same button: for
  example, `Confirm (10s)` down to `Confirm (1s)`.
- The default disabled interval is 2 seconds. The default enabled window is
  10 seconds, measured from when confirmation becomes active, not from opening.
- At expiry, dismiss without stopping. Cancel, Back and outside dismissal
  likewise continue recording; Cancel is available during both phases.
- Only an enabled, unexpired confirmation may request Stop, and only for the
  same recording/startup owner that opened the dialog. Duplicate taps cannot
  send duplicate accepted requests. An old confirmation cannot stop a new
  session, including a session not yet assigned a directory during startup.
- Already-confirmed fixed-base transitions retain their existing confirmation
  and transition flow; they do not gain a second Stop dialog. Automatic failure
  cleanup remains immediate. A terminal `Stop alarm` with no active recording
  remains an alarm action, not a recording confirmation.

## Advanced Settings

Rename the existing `App logs and performance monitoring` screen and its menu
entry to `Advanced settings`, preserving logging/performance/export controls.
Add persisted app-wide fields for `Stop confirmation delay` and
`Stop confirmation timeout`, in whole seconds; existing installations receive
the 2-second and 10-second defaults without changing other preferences.

Approved implementation choices: delay 1-60 seconds, timeout
1-120 seconds, numeric inputs consistent with the app's custom text editor;
invalid values cannot be saved. Each dialog snapshots the saved values on open.
Backgrounding, screen navigation or Activity recreation cancels the dialog
without sending Stop; returning requires a fresh request and both fresh timers.
Use monotonic deadlines rather than counting coroutine wakeups, and recheck
time and owner identity when confirmation is pressed.

## Diagnostics And Verification

Record bounded essential provenance for accepted Stop requests: explicit
confirmed control versus fixed-base transition versus terminal alarm action,
plus session/owner identity and existing stop correlation. Record confirmation
opened/confirmed/cancelled/expired outcomes without credentials or coordinates;
diagnostic failure must never affect capture or authorization to stop.

Tests cover countdown boundaries, the full enabled window after the delay,
timeout/cancel/background/recreation leaving recording running, duplicate taps,
startup ownership, stale sessions, persistent settings and input validation.
Service tests prove an expired/stale request cannot stop a replacement session.
Existing raw-finalization and alarm behaviour must remain intact. Update formal
requirements, traceability and user documentation during implementation; run
the repository pre-push gate and host CI. Device touch/accessibility and
background behaviour remain explicit manual validation, not inferred from CI.

No receiver protocol, raw byte format, USB/NTRIP transport or recording storage
changes are intended. No new release or retag is implied by this design alone.
