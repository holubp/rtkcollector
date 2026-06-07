# Profiled Recording Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the UM980 V1 Android recorder repeatable and field-testable with NTRIP v2-preferred authentication, reusable profiles, configurable storage, functional NMEA export, crash-resilient artifacts, and robust foreground-service recording while minimised.

**Architecture:** Keep pure protocol/session/profile behaviour in JVM-testable modules where possible, and Android-only persistence/UI/service behaviour in `:app`. The foreground service remains the owner of recording and receives a snapshot of selected profiles/policies at start. Raw RX remains authoritative; NMEA/JSON/GPX exports are append-only derived sidecars.

**Tech Stack:** Kotlin/JVM modules, Android SDK programmatic views, Android SharedPreferences/Keystore-backed secret store, Storage Access Framework, JUnit 5 tests, Gradle.

---

## File Structure

- `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripClient.kt`: NTRIP request versions, response classification, fallback, cancellation.
- `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripClientTest.kt`: NTRIP v2/auth/fallback/cancellation tests.
- `core/session/src/main/kotlin/org/rtkcollector/core/session/SessionWriters.kt`: append-only sidecars, NMEA sidecar, atomic session metadata write.
- `core/session/src/main/kotlin/org/rtkcollector/core/session/SessionModels.kt`: add profile/policy/storage/protocol metadata fields.
- `core/session/src/test/kotlin/org/rtkcollector/core/session/SessionWritersTest.kt`: NMEA sidecar and no-truncate/atomic metadata tests.
- `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`: Android-facing local profile data classes and validation.
- `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`: SharedPreferences JSON profile repositories and copy helpers.
- `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileModelsTest.kt`: profile validation and copy tests.
- `app/src/main/kotlin/org/rtkcollector/app/storage/RecordingStorage.kt`: app-private and SAF storage resolution.
- `app/src/main/kotlin/org/rtkcollector/app/MainActivity.kt`: profile manager UI sections, profile selection, storage picker, recording policy controls, NTRIP visibility.
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`: consume profile/policy/storage snapshot, avoid non-NTRIP connections, NMEA export, background-safe service ownership, stop cancellation.
- `docs/user-workflows.md`, `docs/ntrip-and-corrections.md`, `docs/session-format.md`, `docs/android-background-operation.md`, `AGENTS.md`: user/developer documentation updates.

## Task 1: NTRIP v2 Preferred Protocol And Cancellation

**Files:**
- Modify: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripClient.kt`
- Modify: `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripClientTest.kt`

- [ ] **Step 1: Add failing tests**

Add tests for:

```kotlin
@Test
fun `request rendering uses ntrip v2 headers by default`() {
    val request = NtripRequest(
        host = "caster.example",
        port = 2101,
        mountpoint = "MOUNT",
        credentials = NtripCredentials("rover", "secret"),
        userAgent = "RtkCollectorTest/1",
    )

    val rendered = request.render()

    assertTrue(rendered.startsWith("GET /MOUNT HTTP/1.1\r\n"))
    assertTrue(rendered.contains("Host: caster.example:2101\r\n"))
    assertTrue(rendered.contains("Ntrip-Version: Ntrip/2.0\r\n"))
    assertTrue(rendered.contains("Authorization: Basic cm92ZXI6c2VjcmV0\r\n"))
}

@Test
fun `http 403 is classified as authorization failure`() {
    val client = NtripClient(
        request = defaultRequest(),
        connector = FakeNtripSocketConnector(FakeNtripSocket("HTTP/1.1 403 Forbidden\r\n\r\n".toByteArray())),
    )

    val result = client.connectOnce()

    assertInstanceOf(NtripConnectionResult.Failure::class.java, result)
    assertEquals(NtripFailureKind.AUTHORIZATION_FAILED, (result as NtripConnectionResult.Failure).failure.kind)
}

@Test
fun `cancel during reconnect delay returns stopped instead of throwing interrupted exception`() {
    val states = mutableListOf<NtripConnectionState>()
    val delayStarted = CountDownLatch(1)
    val client = NtripClient(
        request = defaultRequest(),
        connector = FakeNtripSocketConnector(FakeNtripSocket("HTTP/1.1 503 Service Unavailable\r\n\r\n".toByteArray())),
        reconnectPolicy = NtripReconnectPolicy(maxAttempts = 2, delayMillis = 5_000),
        delay = {
            delayStarted.countDown()
            Thread.sleep(it)
        },
    )
    val resultRef = AtomicReference<NtripConnectionResult>()
    val thread = Thread {
        resultRef.set(client.runWithReconnect(onState = { states += it.state }))
    }

    thread.start()
    assertTrue(delayStarted.await(2, TimeUnit.SECONDS))
    client.cancel()
    thread.interrupt()
    thread.join(2_000)

    assertFalse(thread.isAlive)
    assertTrue(states.contains(NtripConnectionState.STOPPED))
    assertInstanceOf(NtripConnectionResult.Failure::class.java, resultRef.get())
}
```

- [ ] **Step 2: Run NTRIP tests and verify failure**

Run: `ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :core:correction:test`

Expected: new tests fail because v2 rendering, auth failure classification and interrupt cancellation are not implemented.

- [ ] **Step 3: Implement NTRIP request version and failures**

Add `NtripProtocolVersion`, default `NTRIP_V2`, v1 compatibility rendering, `AUTHENTICATION_FAILED`, `AUTHORIZATION_FAILED`, `CANCELLED`, and response first-line classification. Do not log or expose plaintext credentials.

- [ ] **Step 4: Handle cancellation during reconnect delay**

Wrap reconnect delay in `try/catch (InterruptedException)` and, when cancelled or interrupted, emit `STOPPED` and return a typed cancelled/stopped result instead of surfacing an exception.

- [ ] **Step 5: Run NTRIP tests**

Run the same `:core:correction:test` command.

Expected: all NTRIP tests pass.

## Task 2: Session Durability And NMEA Export

**Files:**
- Modify: `core/session/src/main/kotlin/org/rtkcollector/core/session/SessionWriters.kt`
- Modify: `core/session/src/main/kotlin/org/rtkcollector/core/session/SessionArtifactFile.kt` or equivalent artifact enum file if separate.
- Modify: `core/session/src/test/kotlin/org/rtkcollector/core/session/SessionWritersTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

- [ ] **Step 1: Add failing session writer tests**

Add tests that:

```kotlin
@Test
fun `nmea sidecar appends nmea lines without truncating existing file`() {
    val dir = tempDir.resolve("session")
    SessionWriters.open(dir).use { it.appendReceiverSolutionNmea("\$GPGGA,1*00\r\n") }
    SessionWriters.open(dir).use { it.appendReceiverSolutionNmea("\$GPGGA,2*00\r\n") }

    assertEquals("\$GPGGA,1*00\r\n\$GPGGA,2*00\r\n", Files.readString(dir.resolve("receiver-solution.nmea")))
}

@Test
fun `session json rewrite is atomic and leaves valid json`() {
    val dir = tempDir.resolve("session")
    SessionWriters.open(dir).use {
        it.writeSessionJson("""{"sessionUuid":"one","stoppedAt":null}""")
        it.writeSessionJson("""{"sessionUuid":"one","stoppedAt":"now"}""")
    }

    assertEquals("""{"sessionUuid":"one","stoppedAt":"now"}""", Files.readString(dir.resolve("session.json")))
    assertFalse(Files.exists(dir.resolve("session.json.tmp")))
}
```

- [ ] **Step 2: Run session tests and verify failure**

Run: `ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :core:session:test`

- [ ] **Step 3: Add NMEA sidecar and atomic metadata write**

Add `receiver-solution.nmea` to artifacts. Open sidecar streams with `CREATE`, `WRITE`, `APPEND`. Write `session.json` via temp file in the same directory and atomic move with replace fallback.

- [ ] **Step 4: Append NMEA lines in service advisory path**

When GGA/NMEA lines are observed, append the original complete NMEA sentence to `receiver-solution.nmea` while keeping parsed JSONL advisory output. Parser/export failure must not stop raw recording.

- [ ] **Step 5: Run session and app compile tests**

Run: `ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :core:session:test :app:compileDebugKotlin`

Expected: pass.

## Task 3: Profile Models And Stores

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
- Create: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileModelsTest.kt`

- [ ] **Step 1: Add profile model tests**

Test validation and copy for command, USB/baud, NTRIP caster, NTRIP mountpoint, recording policy and storage profiles. Include tests that copied profiles get new IDs and names, passwords are represented only by secret IDs, and invalid ports/baud rates fail.

- [ ] **Step 2: Run tests and verify failure**

Run: `ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :app:testDebugUnitTest`

- [ ] **Step 3: Implement profile models**

Use Kotlin data classes with explicit `validate()` methods and `copyAsProfile(newId, newName)` helpers. Keep JSON serialisation simple through `org.json` in app code to avoid adding dependencies.

- [ ] **Step 4: Implement SharedPreferences stores**

Add repositories that load/save profile lists as JSON arrays, seed sane defaults when empty, and never persist plaintext NTRIP passwords.

- [ ] **Step 5: Run app unit tests**

Run: `ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :app:testDebugUnitTest`

Expected: profile tests pass. If local Android resource processing invokes an incompatible `aapt2`, record the exact failure and run `ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :app:compileDebugKotlin` before continuing.

## Task 4: Storage Profile And SAF Resolution

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/storage/RecordingStorage.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/MainActivity.kt`

- [ ] **Step 1: Add storage validation tests for pure profile fields**

For pure functions, test app-private profile validation and SAF URI string validation. Keep Android `ContentResolver` access in runtime code, not JVM tests.

- [ ] **Step 2: Implement storage resolution**

Add app-private default and SAF URI support. Persist URI permission when the picker returns. Block start if selected SAF URI cannot be resolved. Keep explicit app-private fallback.

- [ ] **Step 3: Pass selected storage root to service**

Add intent extras for storage kind and SAF URI. Service creates the session directory under the resolved root and never deletes an already-started session.

- [ ] **Step 4: Compile app**

Run: `ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :app:compileDebugKotlin`

Expected: pass.

## Task 5: Profile Manager UI And Recording Start Snapshot

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

- [ ] **Step 1: Add profile selectors and manager controls**

Add sections for command profile, USB/baud profile, NTRIP caster, NTRIP mountpoint, recording policy, storage profile and workflow defaults. Provide save/copy buttons for each profile type. Keep direct mountpoint text entry and sourcetable-selected value path.

- [ ] **Step 2: Add NTRIP visibility/enablement rules**

When selected workflow does not require NTRIP, hide or disable NTRIP profile selection and send an explicit `EXTRA_NTRIP_ENABLED=false` to the service. Service must check that flag before starting NTRIP.

- [ ] **Step 3: Add recording policy controls**

Show device RX as required. Show derived NMEA export as enabled by default. Show NTRIP correction input only for NTRIP workflows. Show remote-base raw observations only when mountpoint profile says available.

- [ ] **Step 4: Add show-password control**

For caster profile editing, add an explicit temporary show/hide password button. It reads the password from the secret store only for display and must not write it to session metadata or events.

- [ ] **Step 5: Compile app**

Run: `ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :app:compileDebugKotlin`

Expected: pass.

## Task 6: Session Metadata And Documentation

**Files:**
- Modify: `core/session/src/main/kotlin/org/rtkcollector/core/session/SessionModels.kt`
- Modify: `core/session/src/test/kotlin/org/rtkcollector/core/session/SessionMetadataTest.kt`
- Modify: `docs/user-workflows.md`
- Modify: `docs/ntrip-and-corrections.md`
- Modify: `docs/session-format.md`
- Modify: `docs/android-background-operation.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: Extend metadata tests**

Assert `session.json` exports profile IDs, recording policy ID, storage profile ID, NTRIP protocol/status metadata and artifact selections without plaintext passwords.

- [ ] **Step 2: Implement metadata fields**

Add nullable profile/policy/storage IDs and NTRIP protocol/status fields. Preserve backwards compatibility by keeping new fields optional.

- [ ] **Step 3: Update docs**

Document profile manager usage, NTRIP v2/fallback behaviour, NMEA sidecar, SAF storage, background recording and unclean-stop recovery.

- [ ] **Step 4: Run session tests and docs diff check**

Run `:core:session:test` and `git diff --check`.

Expected: pass.

## Task 7: Final Review And Verification

**Files:** all touched files.

- [ ] **Step 1: Run focused tests**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :core:correction:test :core:session:test :core:capture:test :receiver:unicore-n4:test :app:compileDebugKotlin
```

- [ ] **Step 2: Run pure JVM module tests**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :core:transport:test :core:capture:test :core:correction:test :core:session:test :core:quality:test :core:workflow:test :receiver:api:test :receiver:generic-nmea-rtcm:test :receiver:unicore-n4:test :receiver:ublox-m8:test
```

- [ ] **Step 3: Attempt Android APK build**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon assembleDebug
```

Expected: pass on compatible Android SDK host; may fail locally on aarch64 Termux with x86-64 `aapt2`. Report exact result.

- [ ] **Step 4: Request reviewer agents**

Use reviewer agents for:

- NTRIP protocol/auth/secrets correctness;
- Android background recording/session durability;
- Kotlin/profile model maintainability.

- [ ] **Step 5: Apply must-fix review feedback**

Only apply feedback that fixes correctness, safety, durability or maintainability issues within the approved V1 scope.

- [ ] **Step 6: Commit and push**

Use a clear commit message and include validation evidence in the body. Leave `.superpowers/` untracked.
