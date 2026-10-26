# Android Background Operation

Recording is owned by a foreground service. Activity and UI code only control
and observe recording state.

Experimental V1 follows this rule with `RecordingForegroundService`: the service
opens USB, owns the capture loop, writes session artifacts, runs optional NTRIP,
holds the wake lock while recording and performs stop/shutdown cleanup.
Advisory parsing and RTCM extraction run behind a bounded queue; when the queue
is full, advisory bytes may be dropped but raw receiver recording continues.
Stop is idempotent. Repeated stop requests must not duplicate receiver shutdown
commands or surface expected thread cancellation as a user error. Raw writer
flush and close are prioritised over derived sidecar finalisation.

## Requirements

- A persistent notification must be visible while recording.
- A partial wake lock is allowed only while recording.
- Recording must survive screen off.
- Recording must survive the app being minimised.
- Files must be written in a crash-recoverable way.
- UI lifecycle and Compose must not own the capture path.
- Users receive an in-app warning when Android battery optimisation may affect
  long recordings. V1 warns but does not request battery-optimisation exemption
  automatically; users remain in control of vendor-specific battery settings.
  The warning's `Fix it` button opens Android battery-optimisation settings,
  falling back to RtkCollector's app-details settings if needed. Select the
  appropriate unrestricted/not-optimised option there, then return to the app;
  it rechecks the standard exemption and hides the warning when exempt. The
  button does not change settings or recording state itself. Manufacturers may
  impose additional background restrictions not covered by this standard check.

On Android 13 and newer, recording start must request notification permission
before foreground recording begins when that permission is still missing. If the
user denies it, recording does not start and the app explains that Android
needs notification permission to show the foreground recording notification.

Start failures must be reported before or at recording start with a specific
category such as USB, storage or NTRIP. A failed USB open, missing Android USB
permission, missing serial driver or unwritable storage target must not appear
to the user as a silent empty recording.

USB permission and access are separate gates. The UI may request Android USB
permission, but the foreground service must verify actual USB open/claim access
before session writers are opened. If Start requests permission, the user must
approve the Android dialog and press Start again; V1 does not auto-start
recording from the permission callback.

Silent USB reads are not healthy recording. If the receiver produces no bytes
for the configured watchdog interval, the foreground service reports degraded
USB capture, writes a session event and attempts to reopen the selected receiver
and rerun the configured init/baud sequence. Successful NTRIP activity or serial
TX does not clear a USB RX-stall error; only actual receiver bytes or a
successful receiver reconnect does.

Mock-location publishing, when enabled, is owned by the same foreground service
as recording. It is not an Activity-owned feature and must stop when the
recording/session stops.

## Limits

Minimal diagnostics retain explicit stop requests and service lifecycle evidence
without enabling Verbose/Debug logging. A deliberate user Stop is not an error.
Events are flushed by a dedicated worker; shutdown must release producers and
drain essential writes before closing the event stream. A delayed essential
writer retains only its event-stream ownership; the service waits up to 150 ms,
finalizes raw/other streams and releases recording state independently. A retained
writer lease prevents unsafe maintenance of that old session, not a new recording.
Successful stop metadata is committed only after the required close outcomes are
known; a late event tail does not retroactively claim success.
Log failures are best-effort diagnostics, not a reason to stop healthy capture.

An Activity restart does not imply service interruption. Session completion
assessment checks service-owned active-session identity. After abrupt process
loss, absent finalization means incomplete with unknown cause, not a fabricated
user stop or diagnosed Android kill. Historical sessions are not rewritten.

Android cannot guarantee survival after:

- user force-stop;
- USB power loss or cable disconnect;
- phone shutdown;
- aggressive vendor task killing;
- storage removal or unrecoverable storage failure.
