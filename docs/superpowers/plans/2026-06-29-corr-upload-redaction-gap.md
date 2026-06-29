# CORR-UPLOAD-007 Redaction Gap Closure Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:test-driven-development` for the implementation steps. Keep the
> raw recording path untouched.

**Goal:** Close remaining gap item 7, interpreted from the formal verification
matrix as `CORR-UPLOAD-007`: NTRIP caster-upload metadata and events must not
persist passwords, tokens, Authorization headers or URL-embedded credentials.

**Architecture:** Caster upload remains advisory and downstream from raw capture.
The change is limited to rendering persisted caster-upload event JSON and
verification documentation. No receiver bytes, RTCM bytes or upload queue
semantics change.

## Task 1: Add Red-Phase Test For Upload Event Redaction

**Files:**
- Create: `app/src/test/kotlin/org/rtkcollector/app/recording/CasterUploadEventJsonTest.kt`

Write a failing test that builds an `NtripCasterUploadEvent` whose message
contains:

- `Authorization: Basic abc123`
- `password=secret`
- `token=abc`
- `ntrip://user:pass@example.org:2101/MOUNT`

Assert the rendered event JSON:

- keeps `type`, `kind`, `timestampMillis` and non-secret message context;
- contains `<redacted>`;
- does not contain `abc123`, `secret`, `token=abc`, `user:pass` or the literal
  password value.

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.recording.CasterUploadEventJsonTest'
```

Expected red result: the test does not compile because the focused event-renderer
helper does not exist yet, or it fails because the current service-only event
writer does not redact.

## Task 2: Implement Testable Redacted Event Renderer

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/recording/CasterUploadEventJson.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

Implement:

```kotlin
internal fun casterUploadEventJson(event: NtripCasterUploadEvent): String
```

Rules:

- redact event `kind` and `message` with the existing diagnostics redaction
  helper before JSON escaping;
- preserve timestamp and schema:
  `{"type":"base-caster-upload","kind":"...","message":"...","timestampMillis":...}`;
- keep JSON escaping local and deterministic;
- never expose credentials in persisted `events.jsonl`.

Change `RecordingSessionWriters.appendCasterUploadEvent(...)` to call the helper.

Run the same targeted test and expect it to pass.

## Task 3: Align Formal Verification Evidence

**Files:**
- Modify: `docs/specification/verification-matrix.md`

Fix the `CORR-UPLOAD-003` through `CORR-UPLOAD-007` evidence rows so each row
matches its requirement:

- `CORR-UPLOAD-003`: upload monitor fields and dashboard/service mapper tests;
- `CORR-UPLOAD-004`: bounded retry policy and controller/profile tests;
- `CORR-UPLOAD-005`: safety stop policy and controller/runtime review;
- `CORR-UPLOAD-006`: RTK2go host-enforced safety and profile/runtime mapping;
- `CORR-UPLOAD-007`: session metadata redaction plus the new
  `CasterUploadEventJsonTest`.

## Task 4: Verify, Review, Commit And Push

Run sequentially:

```sh
git diff --check
sh gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.recording.CasterUploadEventJsonTest'
sh gradlew :app:compileDebugKotlin
sh gradlew :app:unitTestClasses :app:androidTestClasses --dry-run
```

If Android unit-test execution is blocked by the known Termux/aarch64 `aapt2`
environment, record the exact failure and keep the compile and dry-run checks.

Review changed files for:

- no secrets in test literals beyond dummy values;
- no raw-capture path changes;
- no `samples/` or local agent artifacts staged.

Commit and push.
