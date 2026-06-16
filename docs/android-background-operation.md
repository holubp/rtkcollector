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
- Users must receive visible warnings when battery optimisation may affect long
  sessions.

Start failures must be reported before or at recording start with a specific
category such as USB, storage or NTRIP. A failed USB open, missing Android USB
permission, missing serial driver or unwritable storage target must not appear
to the user as a silent empty recording.

USB permission and access are separate gates. The UI may request Android USB
permission, but the foreground service must verify actual USB open/claim access
before session writers are opened. If Start requests permission, the user must
approve the Android dialog and press Start again; V1 does not auto-start
recording from the permission callback.

Mock-location publishing, when enabled, is owned by the same foreground service
as recording. It is not an Activity-owned feature and must stop when the
recording/session stops.

## Limits

Android cannot guarantee survival after:

- user force-stop;
- USB power loss or cable disconnect;
- phone shutdown;
- aggressive vendor task killing;
- storage removal or unrecoverable storage failure.
