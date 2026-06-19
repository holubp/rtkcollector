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

## Storage

### ANDROID-STORAGE-001: SAF Tree Access Is Explicit And Persisted

Status: Normative

When a storage profile uses Android's Storage Access Framework, the app MUST
obtain the tree URI through the system folder picker, persist read/write access
where Android allows it, and verify that write access still exists before
starting recording. If SAF access is missing or revoked, recording MUST fail
with a storage-specific error before raw capture starts.

Verification:
- Manual: select a SAF folder, restart the app and start a recording.
- Review: service start path validates SAF write access before session writers
  are opened.

## Mock Location

### ANDROID-MOCK-001: Mock Location Uses Ellipsoidal Height

Status: Normative

When Android mock-location publishing is enabled, `Location.altitude` MUST be
populated with the selected solution's ellipsoidal height, not orthometric/MSL
altitude. Mock-provider failure MUST NOT stop receiver recording.

Verification:
- Automated: mock-location mapper tests.
- Manual: compare mock altitude against dashboard ellipsoidal height.

### ANDROID-MOCK-002: Satellite Status Is Best-Effort Metadata

Status: Informative

RtkCollector MAY attach satellite in-view/in-use counts as location extras
where available, but an ordinary Android app cannot inject full GNSS satellite
status or sky-position data for other apps through the public mock-location
API.

Verification:
- Review: user docs and UI do not promise synthetic `GnssStatus` injection.

### ANDROID-MOCK-003: Mock Provider Publishing Is Observable

Status: Normative

When mock-location publishing is enabled, the runtime state SHOULD expose the
latest mock publish result and, after at least two successful publishes, the
wall-clock interval between the last two successful mock-provider updates. This
monitoring MUST remain advisory and MUST NOT run on or block the raw receiver
capture path. V1 mock publishing defaults to a fixed-rate `1 Hz` cadence and
MAY be switched by the user to one of the supported fixed rates: `1 Hz`, `2 Hz`,
`5 Hz` or `10 Hz`. Unsupported rates MUST be rejected or normalised back to
`1 Hz`.

Verification:
- Automated: mock publish tick logic and dashboard mapper tests.
- Manual: enabled mock provider shows status/rate/last-interval on the Fix
  card during recording and the top dashboard chip shows Off or the selected
  rate.
