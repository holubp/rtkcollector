# Android Background Operation

Recording is owned by a foreground service. Activity and UI code only control
and observe recording state.

Experimental V1 follows this rule with `RecordingForegroundService`: the service
opens USB, owns the capture loop, writes session artifacts, runs optional NTRIP,
holds the wake lock while recording and performs stop/shutdown cleanup.

## Requirements

- A persistent notification must be visible while recording.
- A partial wake lock is allowed only while recording.
- Recording must survive screen off.
- Recording must survive the app being minimised.
- Files must be written in a crash-recoverable way.
- UI lifecycle and Compose must not own the capture path.
- Users must receive visible warnings when battery optimisation may affect long
  sessions.

## Limits

Android cannot guarantee survival after:

- user force-stop;
- USB power loss or cable disconnect;
- phone shutdown;
- aggressive vendor task killing;
- storage removal or unrecoverable storage failure.
