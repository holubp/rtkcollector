# Stop Confirmation Timer Repair

Status: Approved by human:holubp ("Go ahead. Also resume goal") after clarification
that the repair uses normal review/pre-push/CI gates, without a diagnostic exception.
Implementation and release qualification remain to be verified.

This is an implementation repair addendum to the approved
`2026-11-01-recording-stop-confirmation-design.md`. It does not replace that
design or change its timing, configuration ranges, session ownership or
recording-isolation requirements.

## Review And Evidence

The owner requested Astra review and a fix design. Runtime metadata independently
confirms reviewer model `gpt-6-astra`, reasoning `high`. The review is source and
dependency analysis, not a host-test execution certificate.

Confirmed:

- Commit `3528ee47ac5d2e66a6a0dad96ed8b19830e52384`, CI `41349342786`, has two
  failures in the same timer activation assertion on SDK 33 and 35. The service
  test class reports 58/60 passing. No callback timing trace was captured.
- Initial button state and successful programmatic confirmation now pass after
  the earlier synchronous button-initialization repair.
- Current refresh code renders, then schedules a fresh `postDelayed(100)`.
  Rendering cost therefore moves the next refresh away from its original target.
- Robolectric 4.16.1 has a Choreographer setting separate from PAUSED Looper mode.
  Its default unpaused frame scheduling advances virtual time, normally by 15 ms.
- `performClick()` directly invokes the installed listener. Passing the current
  dispatch test does not prove that the button was enabled and usable.

The clock behavior is supported by the installed dependency bytecode and upstream:

- [ShadowChoreographer 4.16.1](https://raw.githubusercontent.com/robolectric/robolectric/robolectric-4.16.1/shadows/framework/src/main/java/org/robolectric/shadows/ShadowChoreographer.java)
- [ShadowDisplayEventReceiver 4.16.1](https://raw.githubusercontent.com/robolectric/robolectric/robolectric-4.16.1/shadows/framework/src/main/java/org/robolectric/shadows/ShadowDisplayEventReceiver.java)

An illustrative drift sequence is a 1000 ms label render advancing time to
1015 ms, followed by polls at 1115 through 1915 ms and then 2015 ms. The policy
can be ACTIVE at 2000 ms while the displayed button is still disabled. This
sequence is a reproducible target hypothesis, NOT observed CI callback history.
Cancellation, barriers or later state overwrite are not excluded by the XML.

## Approaches

1. Preserve absolute deadlines and repair refresh scheduling, with a small
   internal clock/scheduler/render seam for deterministic tests. Astra recommends
   this narrow production repair because it removes a demonstrated source-level
   drift mechanism without changing the operator contract.
2. Only change the host-test frame mode or add waiting time. Rejected as the
   default repair: it could conceal drift, and no captured trace yet establishes
   that the failure is exclusively a test-environment artifact.
3. Restart the ten-second window when a delayed callback enables the button.
   Rejected: it changes the approved immutable deadline contract. A blocked main
   thread cannot guarantee a full physically rendered window without changing
   that contract; such a change would require a separate explicit decision.

Approach 1 is approved for implementation using normal gates.

## Repair Design

Keep synchronous button initialization and the existing service-owned one-use
authority. Defaults remain `activeAt = openedAt + 2000 ms` and
`expiresAt = activeAt + 10000 ms`, using the saved timing snapshot.

Extract only refresh coordination into an internal pure Kotlin unit supplied
with monotonic elapsed and uptime clocks, scheduling/cancellation operations,
current-request identity, rendering and dismissal callbacks. Android remains a
thin adapter; do not introduce another recording lifecycle or token authority.

For each refresh:

1. Reject cancelled/stale identity and take one pre-render elapsed-time sample.
   Use that same sample for expiry, phase, remaining seconds and target selection;
   dismiss expiry before rendering.
2. Choose the next absolute
   elapsed-time target BEFORE rendering: the earlier of the next countdown or
   phase boundary and the existing maximum 100 ms owner-check interval.
3. Render the sampled label and enabled state. Do not repeatedly assign an
   unchanged label.
4. Recheck cancellation and identity. Read elapsed and uptime clocks after
   rendering; calculate `remainingMs = max(0, targetElapsed - elapsedNow)` and
   schedule at `uptimeNow + remainingMs`. Post immediately if overdue. Do not
   subtract rendering duration from the absolute timestamp, choose a later target
   after rendering, or add a new 100 ms delay after rendering finishes.
5. Late callbacks render the current state directly, never replay missed seconds,
   extend expiry or revive a cancelled dialog. Keep at most one pending refresh.

Positive-button dispatch must check the button's enabled state, current request,
fresh ACTIVE phase and one-shot submission state. Service owner/deadline/replay
validation remains the final authorization boundary.

Cancellation removes the pending refresh and fences an already-delivered callback
from scheduling more work. Opening, rendering, cancellation and expiry never
stop recording or acknowledge alarms. No service, raw capture, USB, NTRIP,
storage-format or receiver-command changes are intended.

## Required Verification

- First reproduce the old scheduling drift in a pure deterministic test whose
  renderer advances its fake clock by 15 ms on label changes. Observe RED with
  the old order and GREEN with boundary-aligned scheduling.
- Use literal independent expectations: at offsets 0 and 999 ms, disabled `2`;
  at 1000 and 1999 ms, disabled `1`; at 2000 ms, enabled `10`; each subsequent
  second counts down through `1` at 11000 and 11999 ms; at 12000 ms dismiss.
- Test delayed callbacks, rendering across expiry, different elapsed/uptime
  offsets, cancellation during rendering, stale identity and no remaining
  scheduled callback after cancellation.
- Preserve the host test's original 2/9/1-second advances and assertions on
  SDK 33 and 35. Keep diagnostic clock/window/token/next-task information.
- Strengthen successful dialog-to-service confirmation by asserting enabled state
  and the displayed label before clicking. Retain replay, expiry, outstanding
  token A-to-B replacement, startup, navigation and raw-finalization tests.
- On a supported host, capture bounded refresh entry, pre/post-render timestamps,
  next absolute target, cancellation reason and Choreographer settings if the
  remaining failure still needs attribution. An ACTIVE policy with matching token,
  stale WAITING rendering and a future refresh supports drift; an ACTIVE last
  rendering with a disabled button suggests overwrite instead.

The Termux local probe is not a host substitute: Robolectric requires unavailable
Linux/aarch64 native components. No further unsupported local native retries,
relaxed assertions, test exclusion changes or unrecorded gate bypasses are part
of this design.

## Gate And Release Boundary

The earlier host failure remains open. Source analysis and a proposed repair do
not constitute host GREEN or a passing full-scope semantic attestation.
Pure old-order RED to repaired-order GREEN alone proves neither historical CI
causality nor closure of the outstanding host finding or full semantic gate.

Implement the source-supported repair first, prove the scheduling invariant with
deterministic tests, then independently review the new source and pass normal
pre-push gates before ordinary CI. Historical failures belong to the old source;
keep them visible and do not claim host GREEN for the new source before CI.
No diagnostic-only workflow, branch exception or release-check waiver is approved
or required for this route. If the repaired source still fails CI, investigate the
new evidence without silently relaxing assertions or gates.

After implementation: independent affected review, all normal repository gates,
both-variant host tests, native/lint/assembly checks, exact-source stable APK build
and downloaded artifact verification must pass before publishing RC12. Physical
P30 Pro validation remains separately reported, not inferred from CI.
