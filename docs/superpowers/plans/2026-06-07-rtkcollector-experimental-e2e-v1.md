# RtkCollector Experimental E2E V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a minimal experimental Android E2E path for UM980 USB serial capture, session writing, NTRIP input, receiver correction TX, and recording observables.

**Architecture:** Keep pure, testable logic in JVM modules and Android-only USB/service/UI code in `app`. The foreground service owns recording; raw RX writes are authoritative; NTRIP, parsing, and UI remain side channels.

**Tech Stack:** Kotlin/JVM modules, Android SDK 36 app, Android USB Host API, Java sockets, JUnit 5 for pure tests.

---

## File Responsibilities

- `receiver/unicore-n4/.../Um980RuntimeProfile.kt`: validate and render safe UM980 runtime command profiles.
- `receiver/unicore-n4/.../Um980RuntimeProfiles.kt`: built-in experimental UM980 profiles.
- `core/session/.../SessionWriters.kt`: create session directories and append session artifacts.
- `core/correction/.../NtripClient.kt`: build NTRIP requests and stream bytes with reconnect state.
- `core/capture/.../CaptureRuntime.kt`: coordinate serial RX, TX queue, correction input, and observables against abstract transports.
- `app/.../usb/AndroidUsbSerialTransport.kt`: Android USB Host implementation, initially FTDI-style serial bridges.
- `app/.../recording/RecordingForegroundService.kt`: owns real recording lifecycle.
- `app/.../MainActivity.kt`: minimal config/start/observe/stop UI.
- `docs/user-workflows.md`, `docs/android-background-operation.md`, `docs/ntrip-and-corrections.md`: user-facing E2E V1 documentation.

## Task 1: UM980 Runtime Profiles

**Files:**
- Create: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980RuntimeProfile.kt`
- Create: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980RuntimeProfiles.kt`
- Test: `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980RuntimeProfileTest.kt`

- [ ] Write tests that safe profile lines pass, persistent/risky commands fail, shell metacharacters fail, disabled profiles are not executable, and default V1 profile includes `OBSVMCMPB COM1 1` plus `BESTNAVB`.
- [ ] Run `./gradlew :receiver:unicore-n4:test` and confirm the new tests fail because the classes do not exist.
- [ ] Implement `Um980RuntimeProfile`, `Um980RuntimeCommandValidator`, and `Um980RuntimeProfiles.experimentalRoverBasePreparation()`.
- [ ] Re-run `./gradlew :receiver:unicore-n4:test` and confirm pass.

## Task 2: Session Writers

**Files:**
- Create: `core/session/src/main/kotlin/org/rtkcollector/core/session/SessionWriters.kt`
- Test: `core/session/src/test/kotlin/org/rtkcollector/core/session/SessionWritersTest.kt`

- [ ] Write tests using a temporary directory that verify `receiver-rx.raw`, `tx-to-receiver.raw`, `correction-input.raw`, `events.jsonl`, and `quality-live.jsonl` are distinct append-only files.
- [ ] Write a redaction test proving session metadata export does not include NTRIP plaintext passwords/tokens.
- [ ] Run `./gradlew :core:session:test` and confirm failure.
- [ ] Implement session directory and append writer classes with explicit `flush()` and `close()`.
- [ ] Re-run `./gradlew :core:session:test` and confirm pass.

## Task 3: Capture Runtime With Fake Transport

**Files:**
- Modify: `core/transport/src/main/kotlin/org/rtkcollector/core/transport/SerialTransport.kt`
- Create: `core/capture/src/main/kotlin/org/rtkcollector/core/capture/CaptureRuntime.kt`
- Test: `core/capture/src/test/kotlin/org/rtkcollector/core/capture/CaptureRuntimeTest.kt`

- [ ] Write tests with a fake transport and fake writers proving RX bytes are written byte-exactly, TX bytes are recorded before send, correction bytes are recorded before receiver injection, and parser/correction callback exceptions do not stop RX recording.
- [ ] Run `./gradlew :core:capture:test` and confirm failure.
- [ ] Implement serial transport suspend/blocking contracts and a minimal threaded capture runtime that uses bounded TX/correction queues.
- [ ] Re-run `./gradlew :core:capture:test` and confirm pass.

## Task 4: NTRIP Client Core

**Files:**
- Create: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripClient.kt`
- Test: `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripClientTest.kt`

- [ ] Write tests for request rendering, Basic auth header construction from runtime credentials, no secret exposure in `toRedactedMetadata()`, sourcetable/non-ICY rejection, RTCM byte callback, optional GGA upload, and reconnect state transitions using a local fake socket connector.
- [ ] Run `./gradlew :core:correction:test` and confirm failure.
- [ ] Implement a small socket-connector abstraction and `NtripClient` with reconnect delay configuration.
- [ ] Re-run `./gradlew :core:correction:test` and confirm pass.

## Task 5: Android USB Transport

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/usb/AndroidUsbSerialTransport.kt`
- Create: `app/src/main/kotlin/org/rtkcollector/app/usb/UsbSerialModels.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] Implement Android USB device discovery summary, permission request action, endpoint selection, FTDI control transfers for 8N1 baud, FTDI two-byte status stripping on RX, bulk TX, and close.
- [ ] Add app manifest USB host feature and foreground service/data sync permissions.
- [ ] Keep implementation minimal and local to `app`; do not add external USB libraries in V1.
- [ ] Run `./gradlew :app:compileDebugKotlin` and confirm compile.

## Task 6: Foreground Recording Service

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Create: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`

- [ ] Implement service actions: start experimental recording, stop recording, send shutdown.
- [ ] Service creates session directory under app external files, validates workflow/command plan, starts foreground notification, acquires partial wake lock, starts USB capture, optional NTRIP client, and broadcasts/simple static-observable state.
- [ ] Ensure stop closes NTRIP, drains/sends shutdown if available, closes writers, releases wake lock, and stops foreground.
- [ ] Run `./gradlew :app:compileDebugKotlin`.

## Task 7: Minimal UI Wiring

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/MainActivity.kt`

- [ ] Add editable fields for session name, serial baud, profile baud, NTRIP host/port/mountpoint/user/password, init sequence, mode sequence, and shutdown sequence.
- [ ] Add USB device summary, request permission button, start real recording button, stop button, and observable counters/status.
- [ ] Keep dry-run path available when no USB permission/device is selected.
- [ ] Ensure UI text contains no maps/GIS/shapefile concepts except explicit exclusion.
- [ ] Run `./gradlew :app:compileDebugKotlin`.

## Task 8: Documentation And Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/user-workflows.md`
- Modify: `docs/android-background-operation.md`
- Modify: `docs/ntrip-and-corrections.md`
- Modify: `docs/session-format.md`
- Modify: `docs/testing-plan.md`

- [ ] Document experimental E2E V1 operation, UM980 profile safety, NTRIP secret handling, artifacts, stop/shutdown behavior, and hardware risks.
- [ ] Run pure tests: `./gradlew clean test`.
- [ ] Run Android compile: `./gradlew :app:compileDebugKotlin`.
- [ ] Attempt `./gradlew assembleDebug`; if local Termux `aapt2` cannot execute, report that separately with the exact error.
- [ ] Run subagent spec review and code-quality review. Fix must-fix issues and request re-review.
- [ ] Commit with message `Add experimental UM980 E2E recording path`.
