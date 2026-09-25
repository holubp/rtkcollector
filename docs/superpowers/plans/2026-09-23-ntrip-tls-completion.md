# NTRIP TLS Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> Historical plan, superseded for RC4 by `2026-09-25-ntrip-rc4.md`. Its Play-only TLS and unsafe-TLS proposals are not supported behavior.

**Original goal:** Deliver secure TLS NTRIP correction download and source upload for Google Play, with plaintext and explicitly accepted unsafe TLS restricted to sideload builds.

**Architecture:** Core correction owns a single validated `NtripEndpointSecurityPolicy`, endpoint parsing, socket security and NTRIP v1/v2 framing. Every correction, sourcetable and upload route, including retries, uses that policy. App profiles persist only transport, verification and local unsafe acknowledgement; distribution validation runs at policy construction and service ingress. Custom CA support is deliberately absent.

**Tech Stack:** Kotlin, JSSE `SSLSocket`, Android Gradle product flavors, Compose, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-23-ntrip-tls-design.md`.

## Global Constraints

- TLS system trust and hostname verification are the default for all new profiles.
- Google Play variants accept only TLS with system trust and hostname verification.
- Plaintext and unsafe accept-any are sideload-only and unsafe requires a persisted, explicit confirmation.
- No TLS fallback may occur after a failed handshake; no request, credentials, GGA or RTCM payload may be written before a successful TLS handshake.
- Custom CA is unsupported in every distribution and must not be stored in profile JSON, session metadata, intents or logs.
- Sourcetable fetch, correction download, source upload, reconnect and protocol compatibility retry use the same validated endpoint-security policy.
- Unsafe acknowledgement is cleared on import, migration, copy/derivation, endpoint change, transport change and verification change.
- Accept only canonical DNS/IPv4/bracketed-IPv6 endpoints and TLS 1.2 or newer.
- NTRIP failures remain advisory: receiver RX capture and session writers continue.
- V1 `SOURCE` and V2 chunked `POST` bytes remain unchanged after socket establishment.

## Review Focus

- A legacy plaintext profile imported into `googlePlay` must fail at preflight and service ingress before a socket is constructed (Task 4).
- A TLS connection to an IP literal must complete hostname verification without malformed SNI (Task 2).
- Unsafe TLS selected without accepted confirmation must fail in a sideload build (Task 3).
- A failed TLS handshake must not cause a protocol fallback from NTRIP v2 to v1 over plaintext (Task 2).
- Existing BKG V1 source upload and V2 chunked upload bytes must remain byte-identical over the selected TLS socket (Task 5).

### Task 1: Core Endpoint and Security Policy

**Files:**
- Modify: `docs/superpowers/specs/2026-09-23-ntrip-tls-design.md`
- Modify: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripTransportSecurity.kt`
- Modify: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripClient.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
- Test: `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripTransportSecurityTest.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/profile/CasterUploadProfileTest.kt`

**Interfaces:** Produces `NtripEndpoint(host: String, port: Int, hostHeader: String, sniName: String?)` and `NtripEndpointSecurityPolicy(endpoint, transport, verification, allowInsecure, unsafeAcknowledged)`. `NtripTlsVerification` has only `SystemTrust` and `Unsafe`; plaintext permits only `SystemTrust` marker.

- [ ] Write failing tests for every legal-state-table row, DNS/IPv4/bracketed-IPv6 parsing, invalid host syntax, `Host` rendering and `CustomCa` class absence via `Class.forName`.
- [ ] Implement endpoint parsing/IDNA normalization and a single policy validator. Make unqualified `NtripSocketConnector.connect(host, port)` internal or remove it; `NtripRequest`, `NtripSourcetableRequest` and `NtripCasterUploadRequest` require the validated policy.
- [ ] Remove `CustomCa`, DER/Base64 handling and custom-CA editor fields from core and app models.
- [ ] Verify the design specification continues to state that private casters require platform trust or explicitly accepted sideload unsafe TLS.
- [ ] Run `sh gradlew :core:correction:test --tests org.rtkcollector.core.correction.NtripTransportSecurityTest --no-parallel`.
- [ ] Commit as `feat: validate NTRIP endpoint security policy`.

### Task 2: Harden the TLS Connector

**Files:**
- Modify: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripClient.kt`
- Test: `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripTlsSocketConnectorTest.kt`

**Interfaces:** `NtripSocketConnector.connect(policy: NtripEndpointSecurityPolicy)` returns only after plain TCP connection or `SSLSocket.startHandshake()` success. System-trust mode sets `endpointIdentificationAlgorithm = "HTTPS"`; unsafe mode intentionally omits it.

- [ ] Add checked-in test-only PKCS#12/certificate fixtures with DNS and IP SANs plus a deterministic local TLS test server; do not expose fixture trust material to production models.
- [ ] Write failing local-server tests for TLS ClientHello before zero NTRIP bytes, trusted DNS/IP success, hostname mismatch, TLS-1.1 rejection and no fallback after handshake failure.
- [ ] Implement bounded TCP connect/read timeouts, DNS-only SNI, HTTPS endpoint identification, enabled protocols filtered to TLS 1.2+, negotiated-protocol verification and immediate handshake.
- [ ] Add a V2-to-V1 response fallback test asserting both connector calls receive the same TLS policy and no plaintext connector call occurs.
- [ ] Run `sh gradlew :core:correction:test --tests org.rtkcollector.core.correction.NtripTlsSocketConnectorTest --no-parallel`.
- [ ] Commit as `fix: harden NTRIP TLS handshake`.

### Task 3: Persisted Profiles and Sideload Consent

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsBackupModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsImportModels.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/profile/SettingsBackupModelsTest.kt`

- [ ] Write failing tests: new profiles use `TLS/SYSTEM_TRUST`; legacy plaintext remains explicit; legacy Custom CA becomes disabled with bytes removed; import, migration, copy/derivation and security/endpoint changes clear unsafe acknowledgement.
- [ ] Implement the JSON codec with exactly `transportMode`, `tlsVerification`, and `unsafeTlsAcknowledged`; remove all custom certificate persistence. Preserve endpoint metadata for disabled Custom-CA profiles.
- [ ] Add `toCore(allowInsecure: Boolean)` that delegates to `NtripEndpointSecurityPolicy` and rejects unsafe unless sideload capability and local acknowledgement are both present.
- [ ] Run `sh gradlew :app:testSideloadDebugUnitTest --tests org.rtkcollector.app.profile.CasterUploadProfileTest --tests org.rtkcollector.app.profile.ProfileStoresTest --tests org.rtkcollector.app.profile.SettingsBackupModelsTest --no-parallel`.
- [ ] Commit as `feat: persist NTRIP TLS transport policy`.

### Task 4: Distribution Policy and Runtime Boundary

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `tools/check_android_test_compilation.py`
- Modify: `tools/test_check_android_test_compilation.py`
- Test: `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt`
- Create test: `app/src/test/kotlin/org/rtkcollector/app/recording/RecordingForegroundServiceTest.kt`

- [ ] Write failing tests for Google Play plaintext/unsafe rejection, sideload plaintext acceptance, sideload unsafe-without-confirmation rejection, and service intent rejection before correction, sourcetable or upload request construction.
- [ ] Define `distribution` flavors: `googlePlay` emits `ALLOW_INSECURE_NTRIP=false`; `sideload` emits `true`; default config is false. Preserve aliases `unitTestClasses`, `androidTestClasses`, and `termuxTestDebugUnitTest` by wiring them explicitly to `sideloadDebug`.
- [ ] Pass only canonical endpoint fields, `mode`, `verification`, and acknowledgement in start/update intents. Service must parse strictly, reject unknown values, and repeat policy validation before every correction, sourcetable and upload connector path.
- [ ] Update native Play task names and bundle validation to `bundleGooglePlayRelease`; retain `validateGooglePlayReleaseBundle`.
- [ ] Update the Termux checker paths/tasks and tests to `sideloadDebug`; run `python3 tools/test_check_android_test_compilation.py` and `sh gradlew :app:unitTestClasses :app:androidTestClasses --dry-run --no-parallel`.
- [ ] On a full Android host run `sh gradlew :app:testGooglePlayDebugUnitTest :app:testSideloadDebugUnitTest :app:compileGooglePlayDebugKotlin :app:compileSideloadDebugKotlin --no-parallel`.
- [ ] Commit as `feat: enforce distribution-safe NTRIP TLS`.

### Task 5: Usable Profile Editor and Protocol Regression

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Modify: `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripCasterUploadClientTest.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileEditorModelsTest.kt`

- [ ] Write failing editor-model tests for disabled incompatible choices in Google Play, retained username/password values after transport change, visible unsafe confirmation requirement, and acknowledgement reset after endpoint/security changes.
- [ ] Render transport choices as TLS and plaintext. In Google Play, plaintext and unsafe are disabled with explanatory text. In sideload builds, unsafe requires a dedicated acknowledgement control and red danger copy; changing away from unsafe clears its acceptance.
- [ ] For V1 source upload, keep username value but visibly state it is ignored; V2 keeps Basic authentication. Do not alter either field when switching protocol versions.
- [ ] Add TLS-injected regression tests proving exact correction/sourcetable request, V1 `SOURCE` header and V2 chunked `POST` framing after a successful selected socket connection.
- [ ] Run focused core and app editor tests.
- [ ] Commit as `feat: expose safe NTRIP TLS controls`.

### Task 6: Specification, Evidence and Release Verification

**Files:**
- Modify: `docs/ntrip-and-corrections.md`
- Modify: `docs/play-publication.md`
- Modify: `GPLAY.md`
- Modify: affected files under `docs/specification/`
- Modify: `docs/superpowers/plan-status.md`

- [ ] Update docs with TLS ports/certificates, sideload-only compatibility behavior, and exact Google Play AAB task.
- [ ] Update requirement IDs, capability map and verification matrix. Mark manual TLS caster validation as `Needs review` until actual evidence exists; do not clear it based on JVM tests.
- [ ] Add redaction tests that induce TLS handshake and caster-response failures and assert diagnostics/session events contain neither password, Basic token, request bytes, certificate bytes nor private-key material.
- [ ] Run `python3 tools/check_spec_requirements.py docs/specification`, `git diff --check`, `sh scripts/pre_push_check.sh`, and both flavor compile tasks sequentially.
- [ ] Request a whole-branch code review focused on credential exposure, TLS verification bypasses, flavor leakage, raw-recording independence, and NTRIP v1/v2 framing.
- [ ] Fix all Critical/Important findings with RED-GREEN tests, then commit and push only with clean mandatory gates.
