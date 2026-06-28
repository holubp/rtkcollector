# Base RTCM NTRIP Caster Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add V1 support for streaming fixed-base RTCM output from RtkCollector to an upstream NTRIP caster while preserving byte-exact receiver recording, adding the missing manual base-coordinate entry path, and making temporary-base-to-fixed-base transfer explicit and persistent enough for real field use.

**Architecture:** Keep receiver RX recording as the first-priority foreground-service path. Extract RTCM frames from the already-recorded receiver RX stream on an advisory worker, validate that the receiver command profile can emit minimum RTCM base data before upload starts, and feed a bounded uploader queue that can fail without stopping recording. Store base coordinates as accepted base-position metadata and never require the user to hand-create `base-position.json`; that JSON is the canonical import/export/session artifact format.

**Tech Stack:** Kotlin, Android foreground service, Compose, `core:correction`, `core:session`, `core:workflow`, app profile stores, UM980 command-profile validation, JUnit tests, `git diff --check`, `sh gradlew :app:compileDebugKotlin`, targeted pure Kotlin tests.

---

## File Structure

Create or update these files:

```text
app/src/main/kotlin/org/rtkcollector/app/base/AcceptedBaseCoordinate.kt
app/src/main/kotlin/org/rtkcollector/app/base/AcceptedBaseCoordinateStore.kt
app/src/main/kotlin/org/rtkcollector/app/base/BaseCoordinateForm.kt
app/src/main/kotlin/org/rtkcollector/app/base/BasePositionJsonCodec.kt
app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt
app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt
app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt
app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt
app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt
app/src/main/kotlin/org/rtkcollector/app/recording/RecordingSessionWriters.kt
app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt
app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt
app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt
core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripCasterUploadClient.kt
core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripCasterUploadController.kt
core/correction/src/main/kotlin/org/rtkcollector/core/correction/RtcmBaseOutputSanity.kt
core/session/src/main/kotlin/org/rtkcollector/core/session/SessionArtifacts.kt
core/session/src/main/kotlin/org/rtkcollector/core/session/SessionModels.kt
docs/ntrip-and-corrections.md
docs/session-format.md
docs/user-workflows.md
docs/workflows.md
docs/superpowers/plan-status.md
```

Add tests:

```text
app/src/test/kotlin/org/rtkcollector/app/base/AcceptedBaseCoordinateTest.kt
app/src/test/kotlin/org/rtkcollector/app/base/BasePositionJsonCodecTest.kt
app/src/test/kotlin/org/rtkcollector/app/profile/CasterUploadProfileTest.kt
app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigCasterUploadTest.kt
core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripCasterUploadClientTest.kt
core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripCasterUploadControllerTest.kt
core/correction/src/test/kotlin/org/rtkcollector/core/correction/RtcmBaseOutputSanityTest.kt
```

---

## Implementation Tasks

### 1. Accepted Base Coordinate Model And JSON Codec

- [ ] Add `AcceptedBaseCoordinate` in `app/src/main/kotlin/org/rtkcollector/app/base/AcceptedBaseCoordinate.kt`.

Use this exact model shape:

```kotlin
data class AcceptedBaseCoordinate(
    val id: String,
    val name: String,
    val latDeg: Double,
    val lonDeg: Double,
    val ellipsoidalHeightM: Double,
    val frame: String,
    val epoch: String?,
    val method: String,
    val durationSeconds: Long?,
    val horizontalUncertaintyM: Double?,
    val verticalUncertaintyM: Double?,
    val antennaHeightM: Double?,
    val antennaReferencePoint: String?,
    val sourceSessionId: String?,
    val sourceDescription: String,
)
```

- [ ] Add `validate()` on `AcceptedBaseCoordinate` with these rules:
  - `id`, `name`, `frame`, `method`, and `sourceDescription` are non-blank.
  - `latDeg` is within `-90.0..90.0`.
  - `lonDeg` is within `-180.0..180.0`.
  - `ellipsoidalHeightM` is finite.
  - optional uncertainty and antenna values are finite and non-negative when present.
- [ ] Add `toFixedBaseModeCommand(comPort: String = "COM1")` returning `MODE BASE %.10f %.10f %.4f` with `Locale.US`.
- [ ] Add `BaseCoordinateForm` with string fields for manual UI entry and `validateForSave()` returning structured messages, not exceptions.
- [ ] Add `BasePositionJsonCodec` using `org.json.JSONObject` with keys aligned to the existing session artifact naming:
  - `latDeg`
  - `lonDeg`
  - `heightM`
  - `frame`
  - `epoch`
  - `method`
  - `durationSeconds`
  - `horizontalUncertaintyM`
  - `verticalUncertaintyM`
  - `antennaHeightM`
  - `antennaReferencePoint`
  - `sourceSessionId`
  - `sourceDescription`
- [ ] Preserve import compatibility with the current dashboard-generated JSON keys `latDeg`, `lonDeg`, `heightM`, `source`, and `sampleCount`.
- [ ] Add `AcceptedBaseCoordinateTest` covering validation, invalid latitude, invalid longitude, invalid height, and command generation.
- [ ] Add `BasePositionJsonCodecTest` covering full JSON round trip and legacy dashboard JSON import.
- [ ] Commit checkpoint:

```text
Add accepted base coordinate model
```

Run:

```sh
git diff --check
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.base.*'
```

If `:app:testDebugUnitTest` is unavailable in the local Termux environment, run `sh gradlew :app:compileDebugKotlin` and record the test limitation in the final report.

### 2. Persist Accepted Base Coordinates

- [ ] Add `AcceptedBaseCoordinateStore` using `Context.getSharedPreferences("accepted-base-coordinates", Context.MODE_PRIVATE)`.
- [ ] Store coordinates as a JSON array under key `acceptedBaseCoordinates`.
- [ ] Store selected coordinate id under key `selectedAcceptedBaseCoordinateId`.
- [ ] Expose:
  - `coordinates(): List<AcceptedBaseCoordinate>`
  - `saveCoordinates(coordinates: List<AcceptedBaseCoordinate>)`
  - `selectedCoordinateId(): String?`
  - `saveSelectedCoordinateId(id: String?)`
  - `selectedCoordinate(): AcceptedBaseCoordinate?`
  - `upsert(coordinate: AcceptedBaseCoordinate)`
  - `delete(id: String)`
- [ ] Reject deletion of the selected coordinate by clearing `selectedAcceptedBaseCoordinateId` first.
- [ ] Do not store secrets or NTRIP credentials in this store.
- [ ] Update `MainActivity` to create one `AcceptedBaseCoordinateStore` with the existing profile stores.
- [ ] Commit checkpoint:

```text
Persist accepted base coordinates
```

Run:

```sh
git diff --check
sh gradlew :app:compileDebugKotlin
```

### 3. Manual Base Coordinate Entry UI

- [ ] Add a menu entry named `Base coordinates` in the settings/menu group near receiver and storage configuration.
- [ ] The screen lists accepted coordinates latest-created first, with active coordinate shown using the existing light-green active styling.
- [ ] Add `Add`, `Edit`, `Copy`, `Rename`, `Delete`, `Import JSON`, and `Export JSON` actions.
- [ ] Manual entry fields:
  - Name
  - Latitude degrees
  - Longitude degrees
  - Ellipsoidal height meters
  - Frame/datum
  - Epoch
  - Method selection from `MANUAL_KNOWN_POINT`, `STATIC_RTK`, `PPP_STATIC`, `RECEIVER_PPP`, `LONG_AVERAGE`, `RECEIVER_SURVEY_IN`, `EXTERNAL_BASE_POSITION_JSON`
  - Duration seconds
  - Horizontal uncertainty meters
  - Vertical uncertainty meters
  - Antenna height meters
  - Antenna reference point
  - Source description
- [ ] Use selection controls for method and checkboxes where fields are boolean. Do not use free text for enumerated values.
- [ ] If the user presses Back with unsaved changes, show a `Save`, `Discard`, `Cancel` dialog.
- [ ] Import JSON validates structure before saving and shows a red error box without modifying existing coordinates on failure.
- [ ] Export JSON uses `BasePositionJsonCodec` and Android share intent.
- [ ] Commit checkpoint:

```text
Add base coordinate settings screen
```

Run:

```sh
git diff --check
sh gradlew :app:compileDebugKotlin
```

### 4. Temporary Base Transfer To Accepted Coordinate

- [ ] Replace the current UI-local `manualBaseCoordinate` as the durable source for fixed-base starts:
  - On `Base` action from a temporary-base workflow, create an `AcceptedBaseCoordinate`.
  - If an average is active or has at least one valid sample, prefer average latitude, longitude, and ellipsoidal height.
  - If no average is available, use the current selected solution latitude, longitude, and ellipsoidal height.
  - Store it via `AcceptedBaseCoordinateStore.upsert()`.
  - Select it via `saveSelectedCoordinateId()`.
  - Switch workflow to `fixed-base`.
- [ ] Keep the current confirmation message but include:
  - coordinate name;
  - latitude;
  - longitude;
  - ellipsoidal height;
  - source `TEMPORARY_BASE_AVERAGE` or `TEMPORARY_BASE_INSTANT`.
- [ ] If the current solution has no ellipsoidal height, do not switch to fixed base. Show a red error: `Fixed base requires ellipsoidal height.`
- [ ] Stop clearing the base coordinate a few tens of seconds after transfer. The accepted coordinate stays selected until the user changes or deletes it.
- [ ] Update `DashboardModels.BaseCoordinateCandidate.toManualBasePositionJsonOrNull()` to delegate to `BasePositionJsonCodec` or remove it after the new store path is used.
- [ ] Commit checkpoint:

```text
Persist temporary base transfer coordinates
```

Run:

```sh
git diff --check
sh gradlew :app:compileDebugKotlin
```

### 5. Use Accepted Coordinate For Fixed-Base Start

- [ ] Update `buildRecordingStartIntent()` in `MainActivity.kt` so `fixed-base` reads the selected `AcceptedBaseCoordinate`.
- [ ] If no coordinate is selected, reject start with: `Fixed base requires an accepted base coordinate.`
- [ ] Generate the fixed-base mode command from `AcceptedBaseCoordinate.toFixedBaseModeCommand()` and append it using the existing `withFixedBaseModeCommand()` path.
- [ ] Write `base-position.json` into the session using `BasePositionJsonCodec`.
- [ ] Pass extras:
  - `EXTRA_BASE_POSITION_JSON`
  - `EXTRA_COORDINATE_SOURCE`
  - new `EXTRA_BASE_COORDINATE_ID`
  - new `EXTRA_BASE_COORDINATE_NAME`
- [ ] Update `SessionMetadata` with nullable fields:
  - `baseCoordinateId`
  - `baseCoordinateName`
  - `baseCoordinateMethod`
- [ ] Update `exportSessionMetadata()` so those fields are written when present.
- [ ] Commit checkpoint:

```text
Start fixed base from accepted coordinate
```

Run:

```sh
git diff --check
sh gradlew :app:compileDebugKotlin
```

### 6. Add Caster Upload Profile Models

- [ ] Add `NtripCasterUploadProfile` to `ProfileModels.kt`.

Use this shape:

```kotlin
data class NtripCasterUploadProfile(
    val id: String,
    val name: String,
    val host: String = "",
    val port: Int = 2101,
    val mountpoint: String = "",
    val username: String = "",
    val secretId: String = "",
    val protocolPolicy: String = "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY",
    val enabledByDefault: Boolean = false,
    val isProtected: Boolean = false,
)
```

- [ ] Add `ntripCasterUploadSecretId(profileId: String): String` returning `ntrip-caster-upload-profile:$profileId`.
- [ ] Validate:
  - id and name are non-blank;
  - host is non-blank before start, but profile editing allows blank host while draft is unsaved;
  - port is within `1..65535`;
  - mountpoint is non-blank before start;
  - protocolPolicy is one of `NTRIP_V2_ONLY`, `NTRIP_V2_PREFERRED_WITH_COMPATIBILITY`, `NTRIP_V1_ONLY`.
- [ ] Add JSON encode/decode and copy/rename behavior consistent with `NtripCasterProfile`.
- [ ] Add `ntripCasterUploadProfiles()` and `saveNtripCasterUploadProfiles()` to `ProfileStores`.
- [ ] Default list is empty.
- [ ] Add settings-set fields:
  - `ntripCasterUploadProfileId: String?`
  - `baseCasterUploadEnabled: Boolean`
- [ ] Add settings-set override fields for host, port, mountpoint, username, and secret id.
- [ ] Update settings import/export to include caster upload profiles. Password export keeps the existing optional plaintext-password checkbox behavior and uses the upload secret id.
- [ ] Add `CasterUploadProfileTest` covering validation, JSON round trip, copy secret id, and empty-password preservation.
- [ ] Commit checkpoint:

```text
Add NTRIP caster upload profiles
```

Run:

```sh
git diff --check
sh gradlew :app:compileDebugKotlin
```

### 7. Caster Upload Settings UI

- [ ] Add menu entry `NTRIP caster upload` under the NTRIP-related settings group.
- [ ] List upload profiles with active profile shown using the same active styling as other profiles.
- [ ] Add `Add`, `Edit`, `Copy`, `Rename`, `Delete`.
- [ ] Editor fields:
  - Name
  - Host
  - Port
  - Mountpoint
  - Username
  - Password with eye icon to reveal
  - Protocol policy as a selection control, not text
  - Enabled by default checkbox
- [ ] Empty password is valid and must save as an empty password for that upload profile only.
- [ ] Bind the password to `ntrip-caster-upload-profile:<profileId>`, not to the correction-download caster secret id.
- [ ] Add `Refresh sourcetable` only if the same request format can query upload target mountpoints without mutating current mountpoint on failure. Persist refreshed mountpoints under the upload profile if implemented in this task.
- [ ] Disable editing while recording is active and show: `Stop recording before changing caster upload profile.`
- [ ] Commit checkpoint:

```text
Add caster upload profile UI
```

Run:

```sh
git diff --check
sh gradlew :app:compileDebugKotlin
```

### 8. Receiver RTCM Base Output Sanity Validator

- [ ] Add `RtcmBaseOutputSanity.kt` in `core/correction`.
- [ ] Implement:

```kotlin
data class RtcmBaseOutputSanityResult(
    val canUpload: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val detectedMessageTypes: Set<Int>,
)
```

- [ ] Implement `Um980RtcmBaseOutputSanity.validateCommands(commands: List<String>): RtcmBaseOutputSanityResult`.
- [ ] Recognize UM980 commands case-insensitively:
  - `RTCM1005`
  - `RTCM1006`
  - `RTCM1033`
  - `RTCM1074`, `RTCM1075`, `RTCM1077`
  - `RTCM1084`, `RTCM1085`, `RTCM1087`
  - `RTCM1094`, `RTCM1095`, `RTCM1097`
  - `RTCM1114`, `RTCM1115`, `RTCM1117`
  - `RTCM1124`, `RTCM1125`, `RTCM1127`
  - `RTCM1230`
- [ ] `canUpload` is true only when:
  - at least one base-position message exists: 1005 or 1006;
  - at least one MSM observation message exists from the GPS/GLO/GAL/BDS/QZSS groups above.
- [ ] Warn when:
  - 1033 is missing;
  - 1230 is missing while any GLONASS MSM message exists;
  - no ephemeris messages are present in the command script.
- [ ] Return an error if only app-monitoring logs are found, such as BESTNAV, ADRNAV, PPPNAV, STADOP, OBSVM, GGA, GSA, GSV.
- [ ] Add tests:
  - built-in `UM980_BASE_CONFIG_SCRIPT` passes;
  - rover binary profile fails;
  - profile with only 1006 fails;
  - profile with only MSM fails;
  - GLONASS MSM without 1230 warns.
- [ ] Commit checkpoint:

```text
Validate base RTCM output commands
```

Run:

```sh
git diff --check
sh gradlew :core:correction:test
```

### 9. Active Recording Config For Upload

- [ ] Add `ActiveCasterUploadConfig` in `ActiveRecordingConfig.kt`:

```kotlin
data class ActiveCasterUploadConfig(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val mountpoint: String,
    val username: String,
    val secretRef: String?,
    val password: String?,
)
```

- [ ] Add `casterUpload: ActiveCasterUploadConfig` to `ActiveRecordingConfig`.
- [ ] Resolve it from the selected settings set, selected upload profile, and password lookup.
- [ ] Caster upload is allowed only for:
  - `fixed-base`;
  - `base-calibration` when a base coordinate has already been accepted and the command profile configures fixed/base RTCM output.
- [ ] Caster upload is disabled for:
  - `plain-rover`;
  - `rover-ntrip`;
  - `base-calibration` without accepted coordinate.
- [ ] `validateForStart()` errors:
  - enabled upload with blank host;
  - enabled upload with blank mountpoint;
  - enabled upload in rover workflow;
  - enabled upload without accepted base coordinate;
  - enabled upload where `Um980RtcmBaseOutputSanity` returns `canUpload=false`.
- [ ] `validateForStart()` warnings are surfaced on dashboard/settings when sanity validator returns warnings.
- [ ] Add tests in `ActiveRecordingConfigCasterUploadTest`.
- [ ] Commit checkpoint:

```text
Resolve active caster upload config
```

Run:

```sh
git diff --check
sh gradlew :app:compileDebugKotlin
```

### 10. NTRIP Caster Upload Client

- [ ] Add `NtripCasterUploadRequest` in `core/correction`.

Use fields:

```kotlin
data class NtripCasterUploadRequest(
    val host: String,
    val port: Int,
    val mountpoint: String,
    val credentials: NtripCredentials?,
    val userAgent: String = DEFAULT_NTRIP_USER_AGENT,
    val protocolVersion: NtripProtocolVersion = NtripProtocolVersion.NTRIP_V2,
)
```

- [ ] Render a caster-upload request as `POST /<mountpoint> HTTP/1.1` with chunked transfer for NTRIP v2-compatible upload.
- [ ] Include:
  - `Host`;
  - `User-Agent`;
  - `Ntrip-Version: Ntrip/2.0`;
  - `Connection: close`;
  - `Authorization: Basic ...` when credentials are present.
- [ ] Reject CR/LF in host, mountpoint, userAgent, username, and password.
- [ ] Add `NtripCasterUploadClient` with:
  - `connectOnce(onState, writeRtcmBytes)` path;
  - accepted response for `ICY 200`, `HTTP/1.1 200`, or `HTTP/1.0 200`;
  - auth error for 401/403;
  - retryable network error for socket/connect/write failure;
  - cancel support that closes active socket.
- [ ] Do not reuse `NtripClient` directly because download reads from the socket while upload writes to it.
- [ ] Tests use a fake `NtripSocketConnector` and fake socket streams.
- [ ] Commit checkpoint:

```text
Add NTRIP caster upload client
```

Run:

```sh
git diff --check
sh gradlew :core:correction:test
```

### 11. Bounded Upload Controller

- [ ] Add `NtripCasterUploadController` in `core/correction`.
- [ ] Use one worker thread named `rtkcollector-caster-upload`.
- [ ] Use an `ArrayBlockingQueue<ByteArray>` with fixed capacity of 256 chunks.
- [ ] Public API:
  - `start(config: NtripCasterUploadRuntimeConfig)`
  - `offer(bytes: ByteArray): Boolean`
  - `stop()`
  - `snapshot()`
- [ ] If the queue is full, drop the offered chunk, increment `droppedBytes`, and emit degraded state. Do not block the caller.
- [ ] Track:
  - state;
  - bytesUploaded;
  - bytesDropped;
  - lastError;
  - active mountpoint URL.
- [ ] Auth errors stop retries until configuration changes.
- [ ] Network errors reconnect after 5 seconds while recording continues.
- [ ] Tests:
  - `offer()` returns false and does not block when full;
  - auth error stops retries;
  - network error retries;
  - `stop()` cancels socket and worker.
- [ ] Commit checkpoint:

```text
Add bounded caster upload controller
```

Run:

```sh
git diff --check
sh gradlew :core:correction:test
```

### 12. Session Artifacts For Uploaded RTCM

- [ ] Add `BASE_CASTER_UPLOAD_RTCM3("base-caster-upload.rtcm3")` to `SessionArtifactFile`.
- [ ] Add append method to `SessionWriters`:

```kotlin
fun appendBaseCasterUploadRtcm(bytes: ByteArray)
```

- [ ] Add matching method to `RecordingSessionWriters`, `PathRecordingSessionWriters`, and `SafRecordingSessionWriters`.
- [ ] For SAF, create the file at session start like the other binary sidecars.
- [ ] Closing this sidecar is `BINARY_SIDECAR` and degraded on failure.
- [ ] Add session metadata fields:
  - `baseCasterUploadEnabled`
  - `baseCasterUploadHost`
  - `baseCasterUploadPort`
  - `baseCasterUploadMountpoint`
  - `baseCasterUploadUsernamePresent`
  - `baseCasterUploadSecretRef`
  - `baseCasterUploadFinalStatus`
- [ ] Export redacted metadata only. Never write upload password or token to `session.json`.
- [ ] Update docs/session-format.md.
- [ ] Commit checkpoint:

```text
Record base caster upload sidecar metadata
```

Run:

```sh
git diff --check
sh gradlew :app:compileDebugKotlin
```

### 13. Foreground Service Integration

- [ ] Add `casterUploadController` field to `RecordingForegroundService`.
- [ ] In `startRecording()` after session writers and metadata are created:
  - evaluate `ActiveCasterUploadConfig`;
  - validate upload is enabled only for valid base workflows;
  - start the upload controller only after the raw capture runtime is open and receiver command setup has completed.
- [ ] Extract RTCM from receiver RX, not from NTRIP correction input, for base upload.
- [ ] Add an advisory consumer that receives capture bytes after `SessionRawRecorder.appendReceiverRx()` has succeeded.
- [ ] The consumer:
  - feeds bytes into `Rtcm3Extractor(validateCrc = true)`;
  - writes each valid frame to `base-caster-upload.rtcm3`;
  - offers each valid frame to `casterUploadController.offer(frame.bytes)`;
  - appends quality events for message type, payload length, CRC status, upload offered/dropped status.
- [ ] Do not call network code or blocking queue operations on the capture thread.
- [ ] Do not upload invalid-CRC RTCM frames.
- [ ] On upload failure, set service state to degraded with category `NTRIP`, but keep `rawRecordingActive=true` while capture continues.
- [ ] On stop, stop upload controller before closing writers, then write final upload status into session metadata if the existing metadata update path supports it. If not, append a final event JSON line:

```json
{"type":"base-caster-upload-final","state":"...","uploadedBytes":0,"droppedBytes":0}
```

- [ ] Commit checkpoint:

```text
Stream base RTCM to caster from recorded RX
```

Run:

```sh
git diff --check
sh gradlew :app:compileDebugKotlin
```

### 14. Dashboard And Runtime Status

- [ ] Add fields to `RecordingServiceState`:
  - `baseCasterUploadState: String`
  - `baseCasterUploadUrl: String`
  - `baseCasterUploadBytes: Long`
  - `baseCasterUploadDroppedBytes: Long`
  - `baseCasterUploadLastError: String?`
- [ ] Broadcast those extras from the service.
- [ ] Map them in `DashboardServiceMapper`.
- [ ] Add compact base upload status to the NTRIP/dashboard card for base workflows:
  - `Upload`
  - URL
  - uploaded bytes
  - dropped bytes
  - status
- [ ] Hide upload rows for rover workflows.
- [ ] On the main top selector, do not add a new top button unless the UI cannot reach upload settings from Menu. Keep the main dashboard compact.
- [ ] Commit checkpoint:

```text
Show base caster upload status
```

Run:

```sh
git diff --check
sh gradlew :app:compileDebugKotlin
```

### 15. Documentation Updates

- [ ] Update `docs/ntrip-and-corrections.md` with:
  - NTRIP client download path to receiver remains unchanged.
  - NTRIP caster upload is a base-only path.
  - Upload consumes RTCM extracted from receiver RX after raw write.
  - Upload failure does not stop recording.
  - Authentication failures stop upload retries until config changes.
  - Minimum receiver RTCM output sanity check.
- [ ] Update `docs/user-workflows.md` with:
  - permanent base workflow with manually entered coordinate;
  - temporary base workflow where accepted RTK/PPP/average coordinate becomes fixed-base coordinate;
  - caster upload setup and start sequence.
- [ ] Update `docs/workflows.md` with:
  - fixed base can have downstream caster upload target;
  - base calibration with NTRIP can produce an accepted coordinate but does not upload until fixed-base mode is active.
- [ ] Update `docs/session-format.md` with:
  - `base-caster-upload.rtcm3`;
  - redacted upload metadata fields;
  - `base-position.json` as canonical serialization for accepted coordinates.
- [ ] Update `docs/superpowers/plan-status.md` with status `Implemented, not field-tested` for each completed checkpoint after implementation.
- [ ] Commit checkpoint:

```text
Document base caster upload workflow
```

Run:

```sh
git diff --check
```

### 16. End-To-End Verification

- [ ] Run pure Kotlin tests:

```sh
sh gradlew :core:correction:test
```

- [ ] Run app Kotlin compile:

```sh
sh gradlew :app:compileDebugKotlin
```

- [ ] Run full Gradle tests only on an environment with working Android native build tools:

```sh
sh gradlew test
```

- [ ] Run APK packaging only on Windows/Android Studio or CI where Android SDK native tools are executable:

```sh
sh gradlew assembleDebug
```

- [ ] Manual field test matrix to document in the final report:
  - Fixed base with accepted manual coordinate starts and writes `base-position.json`.
  - Temporary base average transfer creates accepted coordinate and fixed-base workflow starts from it.
  - Fixed base command profile without RTCM output is rejected before upload starts.
  - Fixed base with RTCM output starts upload and raw recording continues if caster is offline.
  - Upload auth error is shown once and recording continues.
  - `receiver-rx.raw`, `base-caster-upload.rtcm3`, `events.jsonl`, and `quality-live.jsonl` are separate files.
  - `session.json` contains no caster-upload password.
- [ ] Commit final checkpoint:

```text
Complete base caster upload implementation
```

---

## Self-Review Checklist

- [ ] The plan includes manual base-coordinate entry because it does not exist yet.
- [ ] The plan does not require users to create `base-position.json` before using fixed-base workflows.
- [ ] Temporary-base coordinate transfer persists an accepted coordinate and does not rely on transient Compose state.
- [ ] Caster upload consumes RTCM extracted from receiver RX after raw recording, not before it.
- [ ] Upload queue is bounded and non-blocking from the capture path.
- [ ] Upload failure cannot mutate `receiver-rx.raw`, `tx-to-receiver.raw`, or `correction-input.raw`.
- [ ] Session metadata remains redacted and contains no passwords.
- [ ] Receiver command sanity check verifies minimum RTCM base output before upload starts.
- [ ] Rover workflows cannot enable caster upload.
- [ ] SAF and path-backed sessions both create the upload sidecar.
- [ ] Tests cover the reusable models, validators, client, and controller.

## Out Of Scope

- In-phone RTKLIB real-time solution.
- PPP/static solver on Android.
- NTRIP caster administration.
- Map, GIS, shapefile, feature collection, or cartographic survey UI.
- Non-UM980 receiver-specific RTCM base-output command generation.
