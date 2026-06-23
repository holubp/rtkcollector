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

### ANDROID-DIAGNOSTICS-001: Diagnostics Do Not Block Recording

Status: Normative

Runtime logging and performance monitoring MUST NOT run on the raw receiver
capture path. When disabled, they MUST avoid timers, file I/O, JSON
construction and metric aggregation. When enabled, diagnostic failures MUST NOT
stop recording or mutate session artifacts.

Verification:
- Automated: diagnostics controller disabled-state tests.
- Review: service hooks are guarded and outside receiver byte loops.

### ANDROID-JNI-001: JNI And Native Boundaries Are Explicitly Sanitized

Status: Normative

Any JNI, NDK or other native-library boundary MUST validate all Java-provided
strings, arrays, handles and object allocation results before dereferencing or
passing them to C/C++ code. Native adapters MUST follow each downstream C API's
documented nullability contract exactly: use `NULL` only where that API
explicitly defines it as valid, and use empty strings or other sentinel values
where the API documents those instead. Native wrappers MUST prefer scoped or
RAII ownership for JNI resources, release acquired resources on every return
path, and return clear Java-facing errors for invalid inputs or unavailable
native resources rather than crashing the app process.

Verification:
- Automated: source-structure tests for JNI/native bridge nullability contracts
  and resource guards.
- Review: all JNI/native call sites document which pointer arguments may be
  null and which must be valid non-null sentinels.
- Manual: native features that call external libraries are exercised on a host
  or device with a working native build.

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

### ANDROID-USB-003: Silent Receiver RX Stalls Are Degraded USB Failures

Status: Normative

During an active recording, repeated empty receiver reads MUST NOT be treated as
healthy raw recording indefinitely. If no receiver-originated bytes arrive for
the configured stall threshold, the foreground service MUST mark USB capture as
degraded, write a session event, keep already written artifacts, and attempt to
reopen the selected receiver and rerun the required receiver init/baud sequence.
Recovery MUST be based on actual receiver bytes resuming, not on unrelated UI,
NTRIP or TX activity.

Verification:
- Automated: recording health monitor stall/recovery tests.
- Manual: long-running session with receiver disconnect/silence and reconnect.

### ANDROID-USB-004: Receiver Protocol Stalls Are Degraded USB Failures

Status: Normative

During an active recording, receiver-originated bytes that do not produce valid
frames for the selected receiver protocol MUST NOT be treated as healthy
recording indefinitely. If bytes continue to arrive but no valid UM980 binary
or ASCII/NMEA frame, u-blox UBX/NMEA frame, or generic NMEA frame is observed
for the configured protocol-stall threshold, the foreground service MUST mark
USB capture as degraded, write a session event, keep recording raw bytes
byte-exactly, and attempt to reopen the selected receiver and rerun the
required receiver init/baud sequence. Parser failures and invalid bytes MUST
NOT be written into `receiver-rx.raw` as metadata and MUST NOT stop the
recording session while storage and transport still accept bytes.

Verification:
- Automated: recording health monitor protocol-stall/recovery tests.
- Automated: service compile check showing protocol-health hooks are advisory.
- Manual: long-running UM980 binary session where USB keeps producing bytes but
  valid UM980 sync disappears.

### ANDROID-CORRECTION-001: Correction Stream Stalls Are Reconnected Without Stopping Raw Capture

Status: Normative

When the selected workflow expects NTRIP or another correction stream, absence
of correction bytes beyond the configured stall threshold MUST put corrections
into a degraded state, write a session event and request reconnection where the
correction source supports it. This MUST NOT stop receiver raw recording while
USB capture and storage remain functional.

Verification:
- Automated: NTRIP reconnect tests for unexpected stream end and correction
  stall/recovery monitor tests.
- Manual: rover + NTRIP session with transient network/caster interruption.

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

### ANDROID-MOCK-004: Mock Location Uses Maximum Safe Public Location Fields

Status: Normative

When Android mock-location publishing is enabled, RtkCollector MUST populate
every public `Location` field that is supported on the running Android version
and available from the selected best solution. At minimum this includes
latitude, longitude, ellipsoidal altitude, timestamp, elapsed realtime,
horizontal accuracy, API-gated vertical accuracy and API-gated MSL altitude.
RtkCollector MUST NOT invent unavailable speed, bearing or accuracy values.
Satellite counts MAY be attached as best-effort extras under common aliases,
but RtkCollector MUST NOT claim that these extras are equivalent to Android
`GnssStatus`. Failure to apply an optional mock-location field MUST NOT stop
receiver recording.

Verification:
- Automated: mock-location mapper tests.
- Review: Android sink uses API guards for version-specific setters.
- Manual: compare mock altitude, vertical accuracy and MSL altitude behaviour
  on a device where consumer apps expose these fields.

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
