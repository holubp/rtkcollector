# NTRIP Caster Upload Monitor And Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add complete end-to-end NTRIP caster upload monitoring and RTK2go-compatible safety controls, from upload profile policy through runtime enforcement, session diagnostics, dashboard cards, detailed UI, tests and docs.

**Architecture:** The upload feature remains advisory and downstream of byte-exact receiver capture. Profile policy is compiled into an explicit runtime policy, the upload controller owns retry/safety/statistics, the foreground service persists redacted upload events and broadcasts snapshots, and Compose renders a dedicated compact card plus detailed monitor without touching raw capture.

**Tech Stack:** Kotlin/JVM, Android foreground service, Jetpack Compose, Gradle unit tests, existing RTCM3 extraction/parsing helpers, existing profile JSON models.

---

## Checkpoints And Model Budget

- Checkpoint 0: Commit this plan by itself.
- Checkpoint 1: Profile policy model, validation, JSON migration and active config mapping pass targeted tests.
- Checkpoint 2: Core upload controller retry, safety, no-data and RTCM-rate tests pass.
- Checkpoint 3: Foreground service extras/events and dashboard mapper tests pass.
- Checkpoint 4: Compose compact card and detailed monitor compile and are reviewed against the mockup semantics.
- Checkpoint 5: Documentation, verification matrix and plan status are updated.
- Checkpoint 6: Final review, `git diff --check`, targeted tests and `sh gradlew :app:compileDebugKotlin`, then commit and push.

Use model capacity deliberately:

- Use `gpt-5.3-codex-spark` for Task 1 profile/model tests and Task 5 docs/status because these are bounded and mechanical.
- Use `gpt-5.4` or the coordinating GPT-5-class agent for Task 2 and Task 3 because controller/service concurrency and Android state propagation are integration-sensitive.
- Use `gpt-5.4` for Task 4 Compose UI because visual matching, navigation and state modelling need judgment.
- Use `gpt-5.4-mini` for per-task spec/quality reviews when the diff is small; use the coordinating GPT-5-class agent for final cross-cutting review.

## File Structure

- Modify `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
  - Extend `NtripCasterUploadProfile` with retry and safety policy fields.
  - Add `NtripCasterUploadRetryMode`, RTK2go host detection, effective safety helpers and validation.
  - Preserve JSON migration by defaulting missing fields.
- Modify `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt`
  - Carry the explicit upload retry/safety policy into `ActiveCasterUploadConfig`.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  - Add profile editor fields and validation warnings.
  - Pass upload policy into the foreground service.
  - Add detailed upload monitor navigation.
- Create `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripCasterUploadPolicy.kt`
  - Runtime retry policy, safety policy, statistics snapshot and event models.
- Modify `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripCasterUploadClient.kt`
  - Add explicit failure kinds for no-data and safety stop if needed by controller exceptions.
- Modify `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripCasterUploadController.kt`
  - Queue RTCM chunks with optional message type.
  - Compute uploaded byte/bitrate/RTCM Hz/per-message Hz statistics from actually written chunks.
  - Enforce fixed/adaptive retry, consecutive-failure stops, no-data watchdog, bitrate safety and session-volume safety.
  - Emit redacted lifecycle events.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
  - Add caster upload bitrate, total RTCM Hz, per-message rates, retry/safety fields and stop reason.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
  - Build runtime upload policy from start extras.
  - Offer `Rtcm3Frame.bytes` and `Rtcm3Frame.messageType` to the controller.
  - Broadcast upload stats and persist redacted upload events.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
  - Add `CasterUploadCardState` separate from rover correction `NtripCardState`.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
  - Map service extras to `CasterUploadCardState`.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
  - Render the dedicated `Caster upload` compact card whenever upload is configured and enabled.
  - Add detailed monitor composable.
- Modify tests:
  - `app/src/test/kotlin/org/rtkcollector/app/profile/CasterUploadProfileTest.kt`
  - `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigCasterUploadTest.kt`
  - `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripCasterUploadControllerTest.kt`
  - `app/src/test/kotlin/org/rtkcollector/app/recording/RecordingServiceStateTest.kt`
  - `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt`
  - `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`
- Modify docs:
  - `docs/specification/functional-requirements.md`
  - `docs/specification/android-runtime.md`
  - `docs/specification/ui-requirements.md`
  - `docs/specification/verification-matrix.md`
  - `docs/ntrip-and-corrections.md`
  - `docs/user-workflows.md`
  - `docs/superpowers/plan-status.md`

## Task 1: Profile Policy Models And Validation

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/profile/CasterUploadProfileTest.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigCasterUploadTest.kt`

- [ ] **Step 1: Add failing tests for profile defaults, migration, validation and RTK2go safety**

Add tests equivalent to:

```kotlin
@Test
fun `caster upload profile defaults to adaptive retry and disabled manual safety`() {
    val profile = NtripCasterUploadProfile(id = "upload", name = "Upload")

    assertEquals(NtripCasterUploadRetryMode.ADAPTIVE, profile.retryMode)
    assertEquals(10, profile.fixedReconnectDelaySeconds)
    assertEquals(10, profile.adaptiveInitialDelaySeconds)
    assertEquals(300, profile.adaptiveMaxDelaySeconds)
    assertTrue(profile.stopAfterFailuresEnabled)
    assertEquals(5, profile.stopAfterConsecutiveFailures)
    assertFalse(profile.safetyRulesEnabled)
    assertEquals(35, profile.safetyMaxBitrateKbps)
    assertEquals(60, profile.safetyBitrateWindowSeconds)
    assertEquals(500, profile.safetyMaxSessionUploadMb)
}

@Test
fun `caster upload profile migrates missing retry and safety fields`() {
    val json = JSONObject()
        .put("id", "upload")
        .put("name", "Upload")
        .put("host", "rtk2go.com")
        .put("port", 2101)
        .put("mountpoint", "TEST")

    val profile = NtripCasterUploadProfile.fromJson(json)

    assertEquals(NtripCasterUploadRetryMode.ADAPTIVE, profile.retryMode)
    assertTrue(profile.isRtk2goHost)
    assertTrue(profile.effectiveSafetyRulesEnabled)
}

@Test
fun `fixed reconnect delay below ten seconds is invalid`() {
    val profile = NtripCasterUploadProfile(
        id = "upload",
        name = "Upload",
        retryMode = NtripCasterUploadRetryMode.FIXED,
        fixedReconnectDelaySeconds = 9,
    )

    assertTrue(profile.validate().any { it.contains("10 seconds") })
}

@Test
fun `active caster upload config preserves retry and safety policy`() {
    val config = activeRecordingConfigWithCasterUpload(
        uploadProfile = NtripCasterUploadProfile(
            id = "rtk2go",
            name = "RTK2go",
            host = "www.rtk2go.com",
            retryMode = NtripCasterUploadRetryMode.FIXED,
            fixedReconnectDelaySeconds = 15,
            safetyRulesEnabled = false,
        ),
    )

    assertEquals(NtripCasterUploadRetryMode.FIXED, config.casterUpload.retryMode)
    assertEquals(15, config.casterUpload.fixedReconnectDelaySeconds)
    assertTrue(config.casterUpload.effectiveSafetyRulesEnabled)
}
```

- [ ] **Step 2: Run the failing profile tests**

Run: `sh gradlew :app:testDebugUnitTest --tests '*CasterUploadProfileTest' --tests '*ActiveRecordingConfigCasterUploadTest'`

Expected before implementation: unresolved references to the new retry/safety fields or assertions fail.

- [ ] **Step 3: Implement the profile fields and helpers**

In `ProfileModels.kt`, add:

```kotlin
enum class NtripCasterUploadRetryMode { ADAPTIVE, FIXED }

private val RTK2GO_HOSTS = setOf("rtk2go.com", "www.rtk2go.com")
```

Extend `NtripCasterUploadProfile` constructor with:

```kotlin
val retryMode: NtripCasterUploadRetryMode = NtripCasterUploadRetryMode.ADAPTIVE,
val fixedReconnectDelaySeconds: Int = 10,
val adaptiveInitialDelaySeconds: Int = 10,
val adaptiveMaxDelaySeconds: Int = 300,
val stopAfterFailuresEnabled: Boolean = true,
val stopAfterConsecutiveFailures: Int = 5,
val safetyRulesEnabled: Boolean = false,
val safetyMaxBitrateKbps: Int = 35,
val safetyBitrateWindowSeconds: Int = 60,
val safetyMaxSessionUploadMb: Int = 500,
```

Add helpers:

```kotlin
val isRtk2goHost: Boolean
    get() = host.trim().lowercase() in RTK2GO_HOSTS

val effectiveSafetyRulesEnabled: Boolean
    get() = safetyRulesEnabled || isRtk2goHost
```

Update `validate()` with exact checks:

```kotlin
if (retryMode == NtripCasterUploadRetryMode.FIXED && fixedReconnectDelaySeconds < 10) {
    errors += "Fixed reconnect delay must be at least 10 seconds."
}
if (adaptiveInitialDelaySeconds < 10) {
    errors += "Adaptive initial reconnect delay must be at least 10 seconds."
}
if (adaptiveMaxDelaySeconds < adaptiveInitialDelaySeconds) {
    errors += "Adaptive maximum reconnect delay must be greater than or equal to the initial delay."
}
if (stopAfterConsecutiveFailures < 1) {
    errors += "Stop-after-failures count must be at least 1."
}
if (safetyMaxBitrateKbps < 1) {
    errors += "Safety bitrate threshold must be positive."
}
if (safetyBitrateWindowSeconds < 1) {
    errors += "Safety bitrate window must be positive."
}
if (safetyMaxSessionUploadMb < 1) {
    errors += "Safety session upload limit must be positive."
}
```

Update `toJson()` and `fromJson()` to round-trip all fields. Parse unknown retry mode as default `ADAPTIVE`.

- [ ] **Step 4: Carry the fields into active recording config**

Extend `ActiveCasterUploadConfig` with the same runtime policy fields plus `effectiveSafetyRulesEnabled`.

When building it from `NtripCasterUploadProfile`, copy the fields directly and set:

```kotlin
effectiveSafetyRulesEnabled = uploadProfile.effectiveSafetyRulesEnabled
```

- [ ] **Step 5: Run profile tests and commit checkpoint**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests '*CasterUploadProfileTest' --tests '*ActiveRecordingConfigCasterUploadTest'
git add app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt app/src/test/kotlin/org/rtkcollector/app/profile/CasterUploadProfileTest.kt app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigCasterUploadTest.kt
git commit -m "Add caster upload retry and safety profile policy"
```

Expected: targeted tests pass and commit is created.

## Task 2: Core Upload Controller Retry, Safety And RTCM Statistics

**Files:**
- Create: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripCasterUploadPolicy.kt`
- Modify: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripCasterUploadClient.kt`
- Modify: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripCasterUploadController.kt`
- Test: `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripCasterUploadControllerTest.kt`

- [ ] **Step 1: Add failing controller tests**

Add focused tests covering:

```kotlin
@Test
fun `fixed retry uses configured delay of at least ten seconds`() { /* fail until policy exists */ }

@Test
fun `adaptive retry backs off to configured maximum`() { /* two retryable failures, assert 10000 then 20000 or max */ }

@Test
fun `auth failure stops immediately without retry`() { /* existing behaviour preserved */ }

@Test
fun `stop after consecutive retryable failures`() { /* assert STOPPED or RETRY_LIMIT state and no extra attempts */ }

@Test
fun `connected upload with no RTCM for watchdog interval is retryable no data failure`() { /* fake clock/delay */ }

@Test
fun `successful uploaded RTCM resets consecutive failure count`() { /* write one chunk then fail then retry count restarts */ }

@Test
fun `statistics count only actually written uploaded chunks by message type`() { /* offer 1005, 1077, 1077, assert total hz and per type */ }

@Test
fun `bitrate safety stop stops upload and reports safety reason`() { /* small threshold, write enough bytes */ }

@Test
fun `session volume safety stop stops upload and reports safety reason`() { /* tiny MB threshold */ }
```

Use the existing `uploadOnce` constructor and a fake `OutputStream`. For time-sensitive tests, inject a `clockMillis: () -> Long` into the controller and advance a mutable fake clock from the test.

- [ ] **Step 2: Run the failing controller tests**

Run: `sh gradlew :core:correction:test --tests '*NtripCasterUploadControllerTest'`

Expected before implementation: unresolved references or failing assertions.

- [ ] **Step 3: Add runtime policy and event models**

Create `NtripCasterUploadPolicy.kt`:

```kotlin
package org.rtkcollector.core.correction

enum class NtripCasterUploadRetryMode { ADAPTIVE, FIXED }

data class NtripCasterUploadRetryPolicy(
    val mode: NtripCasterUploadRetryMode = NtripCasterUploadRetryMode.ADAPTIVE,
    val fixedReconnectDelayMillis: Long = 10_000,
    val adaptiveInitialDelayMillis: Long = 10_000,
    val adaptiveMaxDelayMillis: Long = 300_000,
    val stopAfterFailuresEnabled: Boolean = true,
    val stopAfterConsecutiveFailures: Int = 5,
) {
    init {
        require(fixedReconnectDelayMillis >= 10_000) { "Fixed reconnect delay must be at least 10 seconds." }
        require(adaptiveInitialDelayMillis >= 10_000) { "Adaptive initial reconnect delay must be at least 10 seconds." }
        require(adaptiveMaxDelayMillis >= adaptiveInitialDelayMillis) { "Adaptive max reconnect delay must not be below initial delay." }
        require(stopAfterConsecutiveFailures >= 1) { "Stop-after-failures count must be at least 1." }
    }
}

data class NtripCasterUploadSafetyPolicy(
    val enabled: Boolean = false,
    val forced: Boolean = false,
    val maxBitrateKbps: Double = 35.0,
    val bitrateWindowMillis: Long = 60_000,
    val maxSessionUploadBytes: Long = 500L * 1024L * 1024L,
    val noDataTimeoutMillis: Long = 12_000,
) {
    init {
        require(maxBitrateKbps > 0.0) { "Safety bitrate threshold must be positive." }
        require(bitrateWindowMillis > 0) { "Safety bitrate window must be positive." }
        require(maxSessionUploadBytes > 0) { "Safety session upload limit must be positive." }
        require(noDataTimeoutMillis > 0) { "No-data timeout must be positive." }
    }
}

data class NtripCasterUploadPolicy(
    val retry: NtripCasterUploadRetryPolicy = NtripCasterUploadRetryPolicy(),
    val safety: NtripCasterUploadSafetyPolicy = NtripCasterUploadSafetyPolicy(),
)

data class NtripCasterUploadMessageRate(
    val messageType: Int,
    val hz: Double,
)

data class NtripCasterUploadEvent(
    val kind: String,
    val message: String,
    val timestampMillis: Long,
)
```

- [ ] **Step 4: Extend controller snapshot and queue items**

Replace the queue payload with:

```kotlin
private data class UploadChunk(
    val bytes: ByteArray,
    val messageType: Int?,
)
```

Extend `NtripCasterUploadRuntimeConfig`:

```kotlin
val policy: NtripCasterUploadPolicy = NtripCasterUploadPolicy(),
```

Keep `reconnectDelayMillis` only as a deprecated compatibility constructor path if existing tests need it; internally convert it to fixed policy with a minimum of 10 seconds.

Extend `NtripCasterUploadSnapshot` with:

```kotlin
val bitrateKbps: Double,
val totalRtcmHz: Double,
val messageRates: List<NtripCasterUploadMessageRate>,
val currentRetryDelayMillis: Long?,
val consecutiveFailures: Int,
val stopReason: String?,
val safetyEnabled: Boolean,
val safetyForced: Boolean,
```

- [ ] **Step 5: Implement retry, watchdog, safety and stats**

Required controller behaviour:

```kotlin
fun offer(bytes: ByteArray, messageType: Int? = null): Boolean
```

When a chunk is actually written to the output stream:

```kotlin
output.write(next.bytes)
output.flush()
bytesUploaded.addAndGet(next.bytes.size.toLong())
stats.recordUploaded(nowMillis = clockMillis(), bytes = next.bytes.size, messageType = next.messageType)
checkSafetyOrThrow(config.policy.safety)
```

Retry delay rules:

```kotlin
private fun nextRetryDelay(policy: NtripCasterUploadRetryPolicy, failures: Int): Long =
    when (policy.mode) {
        NtripCasterUploadRetryMode.FIXED -> policy.fixedReconnectDelayMillis
        NtripCasterUploadRetryMode.ADAPTIVE -> {
            var delay = policy.adaptiveInitialDelayMillis
            repeat((failures - 1).coerceAtLeast(0)) {
                delay = (delay * 2).coerceAtMost(policy.adaptiveMaxDelayMillis)
            }
            delay
        }
    }
```

No-data watchdog:

```kotlin
if (config.policy.safety.noDataTimeoutMillis > 0 &&
    lastUploadedAtMillis == null &&
    clockMillis() - connectedAtMillis >= config.policy.safety.noDataTimeoutMillis
) {
    throw NtripCasterUploadNoDataException("No RTCM data uploaded within 12 seconds.")
}
```

Safety stops:

```kotlin
if (policy.enabled || policy.forced) {
    if (stats.currentBitrateKbps(nowMillis) > policy.maxBitrateKbps) {
        throw NtripCasterUploadSafetyException("BITRATE_LIMIT", "Upload bitrate exceeded ${policy.maxBitrateKbps} kbps.")
    }
    if (bytesUploaded.get() > policy.maxSessionUploadBytes) {
        throw NtripCasterUploadSafetyException("SESSION_VOLUME_LIMIT", "Upload volume exceeded session limit.")
    }
}
```

Emit events for connect attempt, connected, retry scheduled, auth stop, no-data failure, safety stop, queue drop and final summary through an optional `eventSink: (NtripCasterUploadEvent) -> Unit` constructor argument.

- [ ] **Step 6: Run controller tests and commit checkpoint**

Run:

```bash
sh gradlew :core:correction:test --tests '*NtripCasterUploadControllerTest'
git add core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripCasterUploadPolicy.kt core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripCasterUploadClient.kt core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripCasterUploadController.kt core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripCasterUploadControllerTest.kt
git commit -m "Add caster upload retry safety and RTCM statistics"
```

Expected: targeted controller tests pass and commit is created.

## Task 3: Foreground Service Runtime Integration And Dashboard State Mapping

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/recording/RecordingServiceStateTest.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`

- [ ] **Step 1: Add failing service and mapper tests**

Add assertions that service-state copies and intent mapping preserve:

```kotlin
baseCasterUploadBitrateKbps = 4.8
baseCasterUploadRtcmHz = 1.0
baseCasterUploadMessageRates = "1005=0.20,1077=1.00,1087=1.00"
baseCasterUploadRetryMode = "ADAPTIVE"
baseCasterUploadRetryDelaySeconds = 20
baseCasterUploadConsecutiveFailures = 2
baseCasterUploadSafetyEnabled = true
baseCasterUploadSafetyForced = true
baseCasterUploadStopReason = "BITRATE_LIMIT"
```

Add a dashboard mapper test:

```kotlin
assertTrue(state.casterUpload.enabled)
assertEquals("Streaming", state.casterUpload.statusLabel)
assertEquals("1005 0.2 Hz", state.casterUpload.messageRateLabels.first())
assertEquals("Safety stop", state.casterUpload.stopReasonLabel)
```

- [ ] **Step 2: Run failing app tests**

Run: `sh gradlew :app:testDebugUnitTest --tests '*RecordingServiceStateTest' --tests '*DashboardServiceMapperTest' --tests '*DashboardStateTest'`

Expected before implementation: unresolved fields or failed assertions.

- [ ] **Step 3: Add service-state fields and extras**

Add nullable/default-safe fields to `RecordingServiceState`:

```kotlin
val baseCasterUploadBitrateKbps: Double = 0.0,
val baseCasterUploadRtcmHz: Double = 0.0,
val baseCasterUploadMessageRates: String = "",
val baseCasterUploadRetryMode: String = "",
val baseCasterUploadRetryDelaySeconds: Int = 0,
val baseCasterUploadConsecutiveFailures: Int = 0,
val baseCasterUploadSafetyEnabled: Boolean = false,
val baseCasterUploadSafetyForced: Boolean = false,
val baseCasterUploadStopReason: String? = null,
```

Add matching `EXTRA_STATE_BASE_CASTER_UPLOAD_*` constants in `RecordingForegroundService.kt`.

- [ ] **Step 4: Build runtime policy and offer message types**

When constructing `NtripCasterUploadRuntimeConfig`, build:

```kotlin
policy = NtripCasterUploadPolicy(
    retry = NtripCasterUploadRetryPolicy(
        mode = if (retryMode == "FIXED") NtripCasterUploadRetryMode.FIXED else NtripCasterUploadRetryMode.ADAPTIVE,
        fixedReconnectDelayMillis = fixedReconnectDelaySeconds * 1000L,
        adaptiveInitialDelayMillis = adaptiveInitialDelaySeconds * 1000L,
        adaptiveMaxDelayMillis = adaptiveMaxDelaySeconds * 1000L,
        stopAfterFailuresEnabled = stopAfterFailuresEnabled,
        stopAfterConsecutiveFailures = stopAfterConsecutiveFailures,
    ),
    safety = NtripCasterUploadSafetyPolicy(
        enabled = safetyRulesEnabled,
        forced = safetyRulesForced,
        maxBitrateKbps = safetyMaxBitrateKbps.toDouble(),
        bitrateWindowMillis = safetyBitrateWindowSeconds * 1000L,
        maxSessionUploadBytes = safetyMaxSessionUploadMb * 1024L * 1024L,
    ),
)
```

In the RTCM extraction path, replace:

```kotlin
casterUploadController.offer(frame.bytes)
```

with:

```kotlin
casterUploadController.offer(frame.bytes, frame.messageType)
```

- [ ] **Step 5: Persist redacted upload events**

Pass an `eventSink` to `NtripCasterUploadController` that writes session events with safe fields only:

```kotlin
appendSessionEvent(
    "ntrip_caster_upload",
    mapOf(
        "kind" to event.kind,
        "message" to event.message,
        "timestampMillis" to event.timestampMillis,
    ),
)
```

Do not include passwords, auth headers, username/password pairs, or raw server replies.

- [ ] **Step 6: Add `CasterUploadCardState` and mapper**

In `DashboardModels.kt`, add a separate model:

```kotlin
data class CasterUploadCardState(
    val enabled: Boolean = false,
    val statusLabel: String = "Not configured",
    val mountpointLabel: String = "",
    val uploadedLabel: String = "0 B",
    val bitrateLabel: String = "0.0 kbps",
    val totalRtcmHzLabel: String = "0.0 Hz",
    val messageRateLabels: List<String> = emptyList(),
    val droppedLabel: String? = null,
    val lastErrorLabel: String? = null,
    val stopReasonLabel: String? = null,
    val retryLabel: String? = null,
    val safetyLabel: String? = null,
)
```

Add `val casterUpload: CasterUploadCardState = CasterUploadCardState()` to `DashboardState`.

In `DashboardServiceMapper.kt`, map service extras into the new card and format per-message rates by splitting `1005=0.20,1077=1.00` into labels like `1005 0.2 Hz`.

- [ ] **Step 7: Run integration tests and commit checkpoint**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests '*RecordingServiceStateTest' --tests '*DashboardServiceMapperTest' --tests '*DashboardStateTest'
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt app/src/test/kotlin/org/rtkcollector/app/recording/RecordingServiceStateTest.kt app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt
git commit -m "Integrate caster upload monitoring state"
```

Expected: targeted tests pass and commit is created.

## Task 4: Profile Editor, Compact Upload Card And Detailed Monitor UI

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModelsTest.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModelsTest.kt`

- [ ] **Step 1: Add failing UI model tests where possible**

Add tests for profile editor model construction or saving:

```kotlin
assertTrue(fields.any { it.label.contains("Fixed reconnect delay") })
assertTrue(fields.any { it.helper.contains("10 seconds") })
assertTrue(fields.any { it.label.contains("RTK2go safety rules") })
```

Add a dashboard layout/model test that `DashboardState(casterUpload = enabled)` produces a visible card descriptor before recording.

- [ ] **Step 2: Run failing UI tests**

Run: `sh gradlew :app:testDebugUnitTest --tests '*ProfileEditorModelsTest' --tests '*DashboardLayoutModelsTest'`

Expected before implementation: missing fields/card descriptor.

- [ ] **Step 3: Add upload policy fields to the existing profile editor**

In the existing `ProfileKind.NTRIP_CASTER_UPLOAD` edit screen, add controls:

- Retry mode segmented/slider choice: `Adaptive` / `Fixed`.
- Fixed reconnect delay seconds numeric field with helper text: `Minimum 10 seconds. Values under 10 cannot be entered.`
- Adaptive initial delay seconds numeric field, minimum 10.
- Adaptive maximum delay seconds numeric field.
- Stop after consecutive failures switch and count field.
- Safety rules switch.
- If `host` is RTK2go, show the safety switch as enabled/locked with text: `Required for RTK2go`.
- Safety max bitrate kbps, window seconds and session MB fields.

On save, coerce or reject values under 10 using the same validation as `NtripCasterUploadProfile.validate()`. The UI must warn the user directly; it must not silently accept a sub-10 reconnect delay.

- [ ] **Step 4: Pass policy fields in start intent**

In `buildDashboardStartIntent`, add extras for all policy fields:

```kotlin
putExtra(EXTRA_BASE_CASTER_UPLOAD_RETRY_MODE, casterUpload.retryMode.name)
putExtra(EXTRA_BASE_CASTER_UPLOAD_FIXED_RECONNECT_DELAY_SECONDS, casterUpload.fixedReconnectDelaySeconds)
putExtra(EXTRA_BASE_CASTER_UPLOAD_ADAPTIVE_INITIAL_DELAY_SECONDS, casterUpload.adaptiveInitialDelaySeconds)
putExtra(EXTRA_BASE_CASTER_UPLOAD_ADAPTIVE_MAX_DELAY_SECONDS, casterUpload.adaptiveMaxDelaySeconds)
putExtra(EXTRA_BASE_CASTER_UPLOAD_STOP_AFTER_FAILURES_ENABLED, casterUpload.stopAfterFailuresEnabled)
putExtra(EXTRA_BASE_CASTER_UPLOAD_STOP_AFTER_CONSECUTIVE_FAILURES, casterUpload.stopAfterConsecutiveFailures)
putExtra(EXTRA_BASE_CASTER_UPLOAD_SAFETY_RULES_ENABLED, casterUpload.safetyRulesEnabled)
putExtra(EXTRA_BASE_CASTER_UPLOAD_SAFETY_RULES_FORCED, casterUpload.effectiveSafetyRulesEnabled && !casterUpload.safetyRulesEnabled)
putExtra(EXTRA_BASE_CASTER_UPLOAD_SAFETY_MAX_BITRATE_KBPS, casterUpload.safetyMaxBitrateKbps)
putExtra(EXTRA_BASE_CASTER_UPLOAD_SAFETY_BITRATE_WINDOW_SECONDS, casterUpload.safetyBitrateWindowSeconds)
putExtra(EXTRA_BASE_CASTER_UPLOAD_SAFETY_MAX_SESSION_UPLOAD_MB, casterUpload.safetyMaxSessionUploadMb)
```

- [ ] **Step 5: Render the dedicated compact card**

In `HomeDashboard.kt`, add `CasterUploadCard` next to the existing monitoring cards. It is visible when `state.casterUpload.enabled`.

Required compact card visual semantics:

- Title: `Caster upload`
- Primary status pill/text.
- Redacted host:port/mountpoint.
- Total upload volume.
- Current bitrate.
- Total RTCM Hz.
- Inline compact RTCM type grid, e.g. `1005 0.2`, `1077 1.0`, `1087 1.0`.
- Dropped bytes/frames only when non-zero.
- Last error or safety-stop reason promoted with warning color.

Keep it compact enough for the main dashboard; do not bury upload state inside the rover correction card.

- [ ] **Step 6: Add detailed upload monitor screen**

Add a dashboard navigation target from tapping the compact card. The detailed screen shows:

- profile/mountpoint metadata;
- current state;
- last error/stop reason;
- retry mode, current delay and consecutive failures;
- uploaded bytes, bitrate, dropped bytes;
- total RTCM Hz and per-message type table;
- safety enabled/forced, thresholds and current measured values;
- no-data watchdog status.

Ensure credentials are never displayed.

- [ ] **Step 7: Run compile/UI tests and commit checkpoint**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests '*ProfileEditorModelsTest' --tests '*DashboardLayoutModelsTest'
sh gradlew :app:compileDebugKotlin
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModelsTest.kt app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModelsTest.kt
git commit -m "Add caster upload monitor UI"
```

Expected: targeted tests and Kotlin compile pass.

## Task 5: Specifications, User Docs And Verification Matrix

**Files:**
- Modify: `docs/specification/functional-requirements.md`
- Modify: `docs/specification/android-runtime.md`
- Modify: `docs/specification/ui-requirements.md`
- Modify: `docs/specification/verification-matrix.md`
- Modify: `docs/ntrip-and-corrections.md`
- Modify: `docs/user-workflows.md`
- Modify: `docs/superpowers/plan-status.md`

- [ ] **Step 1: Add formal requirements**

Add requirement entries for:

- upload monitor visibility whenever upload is configured and enabled;
- upload retry policy minimum 10 second fixed delay;
- RTK2go forced safety rules;
- no-data watchdog;
- bitrate and session-volume safety stops;
- total and per-message RTCM upload Hz display;
- redaction of credentials and raw auth material;
- raw capture isolation.

- [ ] **Step 2: Update public-facing docs**

In `docs/ntrip-and-corrections.md` and `docs/user-workflows.md`, explain:

- only valid RTCM3 frames are uploaded;
- OBSVM/OBSVMCMPB/BESTSAT and other telemetry are not uploaded;
- upload monitoring shows state, errors, retry, volume, bitrate and RTCM type rates;
- RTK2go profiles force safety rules;
- any reconnect delay below 10 seconds is rejected;
- high-rate RTCM output consumes mobile data and public-caster capacity.

- [ ] **Step 3: Update verification matrix and plan status**

Add automated/manual verification rows for every new requirement. Mark implementation status as `Implemented, not field-tested` for any behaviour that still needs private-caster or Android hardware validation.

- [ ] **Step 4: Run docs diff check and commit checkpoint**

Run:

```bash
git diff --check
git add docs/specification/functional-requirements.md docs/specification/android-runtime.md docs/specification/ui-requirements.md docs/specification/verification-matrix.md docs/ntrip-and-corrections.md docs/user-workflows.md docs/superpowers/plan-status.md
git commit -m "Document caster upload monitoring and safety"
```

Expected: whitespace check passes and commit is created.

## Task 6: Final Review, Verification And Push

**Files:**
- Review all touched files.

- [ ] **Step 1: Run final code review**

Use `superpowers:requesting-code-review` with reviewers focused on:

- Android robust background-capture architecture;
- NTRIP/RTCM upload correctness;
- UI completeness from the user's perspective;
- privacy/redaction.

- [ ] **Step 2: Fix review findings**

Fix only concrete bugs, missing requirements, build failures or documentation mismatches. Do not refactor unrelated code.

- [ ] **Step 3: Run final verification**

Run:

```bash
git diff --check
sh gradlew :core:correction:test --tests '*NtripCasterUploadControllerTest'
sh gradlew :app:testDebugUnitTest --tests '*CasterUploadProfileTest' --tests '*ActiveRecordingConfigCasterUploadTest' --tests '*RecordingServiceStateTest' --tests '*DashboardServiceMapperTest' --tests '*DashboardStateTest'
sh gradlew :app:compileDebugKotlin
```

If Termux environment limitations affect broader Android packaging, do not misreport them as source failures. The required final compile check is `:app:compileDebugKotlin`.

- [ ] **Step 4: Final commit and push**

Run:

```bash
git status --short
git add <remaining touched files>
git commit -m "Complete caster upload monitor and safety controls"
git push
```

Expected: clean worktree except intentionally untracked local evidence files, commits pushed to `origin/main`.

## Self-Review Of Plan

- Spec coverage: The plan maps every design-spec section to profile data, runtime behaviour, monitoring card, detailed monitor, RTCM upload statistics, diagnostics, docs and tests.
- Placeholder scan: No task contains `TBD`, `TODO`, or open-ended "add tests" without concrete behaviour.
- Type consistency: Runtime and app retry modes intentionally share enum names but live in their existing modules; conversion happens in the foreground service boundary.
