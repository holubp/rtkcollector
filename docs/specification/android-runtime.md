# Android Runtime Requirements

## Recording Runtime

### ANDROID-SERVICE-001: Foreground Service Owns Recording

Status: Normative

The foreground service MUST own recording, wake lock, transport access and
session writers. Activity and Compose UI MAY control and observe recording but
MUST NOT own the capture lifecycle.

Verification:
- Review: recording loop and writers live in service/runtime code.
- Manual: recording continues with screen off and app backgrounded.

### ANDROID-CAPTURE-001: Capture Path Is Independent Of UI

Status: Normative

Raw capture MUST NOT depend on Activity lifecycle, Compose recomposition,
dashboard rendering, parser success, NTRIP state or future RTKLIB state.

Verification:
- Review: capture loop has no UI dependencies.
- Manual: background/minimised recording continues to grow `receiver-rx.raw`.

## USB

### ANDROID-USB-001: USB Permission And Open Access Are Separate Gates

Status: Normative

The app MUST treat Android USB permission and actual device-open success as
separate checks. If Android reports permission but opening fails, the user must
receive a clear actionable error.

Verification:
- Automated: USB permission decision model tests.
- Manual: Huawei/vendor Android USB smoke test.

### ANDROID-USB-002: USB Reconnect Attempts Do Not Stop Recording Session State

Status: Normative

During an active recording, USB disconnect/reconnect handling SHOULD attempt to
reopen the configured receiver and rerun required init sequencing without
discarding existing session artifacts.

Verification:
- Automated: reconnect policy tests where practical.
- Manual: disconnect and reconnect receiver during recording.

