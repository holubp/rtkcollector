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

### ANDROID-CORRECTION-002: Correction Last-Update Display Uses Wall-Clock Time

Status: Normative

Dashboard correction last-update timestamps MUST be UTC wall-clock epochs.
Internal elapsed-realtime timestamps MAY be used for recentness and stall
detection, but MUST NOT be broadcast or rendered as correction last-update
wall-clock values.

Verification:
- Automated: dashboard service mapper rejects elapsed-realtime correction
  update values.
- Manual: rover + NTRIP dashboard last update stays near current UTC time
  during receiver RTCM telemetry updates.

### ANDROID-CORRECTION-003: Correction Callback Failure Has Explicit State

Status: Normative

An exception while recording or routing received correction bytes MUST NOT
silently terminate the NTRIP worker while its last published state remains
`STREAMING`. The controller MUST publish an explicit failed or stopped state;
receiver raw recording MUST remain independent.

Verification:
- Automated: NTRIP runtime callback-exception test.
- Review: callback exceptions cannot escape the worker without a final state.

### ANDROID-CORRECTION-004: NTRIP Shutdown Is Bounded And Confirmed

Status: Normative

Stopping, disabling or replacing NTRIP correction intake MUST deactivate new
callbacks without waiting indefinitely for callback code. Client cancellation
and worker join MUST use bounded waits. If callback quiescence or worker
termination is not confirmed, the controller MUST retain ownership so shutdown
can be retried; it MUST NOT discard a possibly live client or worker reference.
If replacement times out, the service MUST publish a non-streaming degraded
state and retry deliberately rather than presenting the requested configuration
as connected. Deferred replacement MUST use the correction-intake state rather
than overwrite an unrelated global error, and MUST transition out of that state
once replacement connection work starts. The service MUST retain ownership of any reconnect coordinator
until it has joined it or observed a quiescent self-release performed as that
coordinator's final state-accessing operation.
Recording finalisation MUST keep session writers open until NTRIP callbacks and
the correction worker and reconnect coordinator are confirmed unable to access
them.

Verification:
- Automated: blocked-callback and stubborn-client shutdown tests in
  `NtripRuntimeControllerTest`.
- Review: recording-service replacement handles a false controller result,
  scopes deferred status to correction intake, and finalisation retries unconfirmed
  coordinator and NTRIP shutdown.

## Base Caster Upload

### ANDROID-UPLOAD-001: Upload Must Remain Advisory and Not Block Capture

Status: Normative

Caster upload MUST never read from or block raw capture paths. The upload path can
only process bytes already appended to `receiver-rx.raw`, and upload failures,
retries or safety stops MUST NOT stop, delay or mutate raw recording while USB
transport and storage remain functional.

Verification:
- Automated: service call-site review confirms upload starts from the raw capture
  runtime after writers are open.
- Manual: force upload outage while recording is active; `receiver-rx.raw` growth
  remains continuous.

### ANDROID-UPLOAD-002: Upload Retry Policy Is Bounded and 10-Second Minimum

Status: Normative

Upload retries MUST be in configured fixed or adaptive mode with explicit minimum
bounds. Fixed reconnect delay MUST be `>=10s`. Adaptive initial delay MUST be
`>=10s`, and adaptive maximum delay MUST be at least the initial value.
Reconnect delays under ten seconds MUST be rejected at profile validation or
runtime mapping time.

Verification:
- Automated: profile validation tests and controller retry-schedule tests.
- Review: service/runtime policy mapping enforces lower bounds before controller
  start.

### ANDROID-UPLOAD-003: Upload No-Data Watchdog and Safety Stops Are Explicit

Status: Normative

The upload runtime MUST stop upload as a terminal safety action only if post-connection
no-data watchdog expires without successful uploads, or if safety limits trigger.
Safety triggers include sustained bitrate and per-session volume thresholds. Safety
stop reasons MUST remain in upload monitor and final summary while keeping receiver
capture active.

Verification:
- Automated: upload controller tests for no-data watchdog and safety stop paths.
- Manual: offline caster and high-rate upload simulation show explicit safety stop
  reason.

### ANDROID-UPLOAD-004: RTK2go Hosts Force Safety Rules

Status: Normative

When uploader host detection identifies RTK2go, safety rules MUST be forced and
non-disableable for that host. The runtime state MUST distinguish forced safety
from user-disabled safety.

Verification:
- Automated: mapping tests for RTK2go host detection and forced safety flag.
- Review: runtime policy payload carries forced safety state to service monitor.

### ANDROID-UPLOAD-005: Upload Telemetry Uses Actual RTCM Frames

Status: Normative

Upload telemetry MUST be derived from frames that are actually offered for upload,
including uploaded bytes, dropped bytes, current bitrate, total RTCM upload rate,
and per-message rates. It MUST NOT be derived from configured command output
settings alone.

Verification:
- Automated: uploader controller snapshot tests and dashboard mapper tests.
- Manual: detailed monitor shows total and per-message rates while upload is
  active.

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

### ANDROID-STATE-001: Recording State Delivery Is App-Private

Status: Normative

Recording-service state and USB-permission result receivers MUST be registered
as non-exported on every supported Android version. Another application MUST
NOT be able to forge dashboard recording state, session paths, coordinates or
errors through the app's private broadcast actions.

Verification:
- Automated: receiver-registration source test or cross-UID instrumentation
  test on a pre-Android-13 API.
- Review: private dynamic receivers use the AndroidX non-exported contract on
  all supported APIs.

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
