# Functional Requirements

## Capture Architecture

### ARCH-RAW-001: Receiver RX Raw Stream Is Authoritative

Status: Normative

The app MUST record receiver-originated bytes into `receiver-rx.raw` without
inserting timestamps, markers, parsed fields or app metadata.

Rationale:
The raw receiver stream is the source of truth for replay, diagnostics and
post-processing.

Verification:
- Automated: session writer byte-preservation test.
- Manual: replay a known byte stream and compare output bytes.
- Review: parser, UI, NTRIP and RTKLIB paths must not write into
  `receiver-rx.raw`.

### ARCH-RAW-002: Advisory Failures Must Not Stop Raw Capture

Status: Normative

Parser, UI, NTRIP, quality-monitor and future RTKLIB failures MUST NOT stop or
mutate raw recording while transport and storage still function.

Verification:
- Automated: parser-failure isolation test.
- Manual: run recording with malformed mixed stream and confirm raw file grows.
- Review: capture loop does not wait on advisory consumers.

### ARCH-TX-001: App TX Is Recorded Separately

Status: Normative

Commands and correction bytes transmitted from RtkCollector to the receiver MUST
NOT be written into `receiver-rx.raw`. They MUST be recorded in
`tx-to-receiver.raw` or an explicitly documented TX artifact.

Verification:
- Automated: session artifact writer tests.
- Review: all serial write paths pass through TX recording or documented
  exceptions.

### ARCH-CORR-001: Correction Input Is Recorded Separately

Status: Normative

NTRIP or other correction-source bytes received by the app SHOULD be recorded in
`correction-input.raw` before or while they are forwarded to the receiver.

Verification:
- Automated: NTRIP runtime/session writer tests.
- Manual: rover + NTRIP session contains usable RTCM3 correction stream.

### CORR-UPLOAD-001: Caster Upload Uses RTCM3 Frames Only

Status: Normative

Base upload MUST send only valid RTCM3 frames extracted from `receiver-rx.raw` to
the caster upload pipeline. Non-RTCM payloads, including OBSVM/OBSVMCMPB/BESTSAT
and NMEA records, MUST NOT be uploaded. Validity is a precondition for upload;
invalid RTCM must remain in `base-caster-upload.rtcm3` for audit and be marked
as dropped from upload.

Verification:
- Automated: `NtripCasterUploadControllerTest` valid/invalid frame coverage.
- Manual: replay session with mixed OBSVM/RTCM confirms only RTCM bytes are offered
  to upload.

### CORR-UPLOAD-002: Caster Upload Is Isolated From Receiver Capture

Status: Normative

Caster upload MUST be advisory and downstream from byte-exact raw recording. Upload
shall consume receiver data only after bytes have been written to `receiver-rx.raw`.
Upload failures, backoff and safety stops MUST NOT stop, delay or mutate
`receiver-rx.raw` while USB transport and storage are functional.

Verification:
- Automated: uploader-controller tests and service call-site review.
- Manual: live upload outage while recording keeps `receiver-rx.raw` growing and
  upload state degrades only.

### CORR-UPLOAD-003: Caster Upload Monitor Is Always Discoverable When Configured

Status: Normative

When caster upload is configured and enabled, the runtime and dashboard state must
expose upload monitor fields before and during recording. The monitor MUST show at
least current state, retry status, last error, uploaded/dropped bytes, upload
bitrate, valid RTCM frame rate and per-RTCM-type rates.

Verification:
- Automated: dashboard state/model mapper tests.
- Manual: home dashboard and detailed upload monitor show upload telemetry.

### CORR-UPLOAD-004: Caster Upload Uses Bounded Retry Policy

Status: Normative

The uploader retry policy MUST be explicit and bounded. The app MUST support
fixed-reconnect and adaptive-reconnect modes with bounded delays. Fixed reconnect
delay MUST be at least `10s`.

Verification:
- Automated: upload policy and controller retry tests.
- Manual: network interruption reproduces capped bounded reconnect in logs/monitor.

### CORR-UPLOAD-005: Caster Upload Safety Is Enforced and Bounded

Status: Normative

When safety is enabled (or RTK2go host-enforced), caster upload MUST:

- stop after consecutive failure limit is exceeded;
- stop when a post-connect no-RTCM watchdog expires;
- stop when bitrate exceeds configured sustained bitrate limit over the configured
  window; and
- stop when per-session uploaded volume exceeds configured MB cap.

Safety stop reasons MUST be surfaced in monitor state and final upload status.

Verification:
- Automated: upload controller tests covering retry limit, no-data watchdog, bitrate
  cap and session cap.
- Review: service runtime path maps controller stop reasons to persisted redacted
  session metadata and state.

### CORR-UPLOAD-006: RTK2go Hosts Must Enforce Safety

Status: Normative

When the uploader host is RTK2go, safety rules MUST be enforced regardless of user
selection. This includes enabling bitrate/session-volume caps and session watchdog
logic where applicable. Non-RTK2go hosts MAY keep safety disabled by default.

Verification:
- Automated: RTK2go host unit coverage and active-config mapping tests.
- Review: runtime extras and service config show `safetyForced` and selected host.

### CORR-UPLOAD-007: Credentials Are Redacted From Upload Metadata

Status: Normative

Runtime upload metadata and diagnostics must not include credentials. Passwords,
tokens and full secret values MUST be redacted before writing upload events,
session metadata, and exported artifacts.

Verification:
- Automated: session metadata/event redaction tests where available.
- Manual: confirm session metadata and events omit plaintext credentials.

## Product Boundaries

### PRODUCT-NONGOAL-001: No GIS Application Scope

Status: Normative

The app MUST NOT introduce maps, shapefiles, GIS editing, field-feature
collection or cartographic survey project-management dependencies.

Verification:
- Review: dependency and UI review for every feature branch.

### PRODUCT-USB-001: External USB Receiver Scope Is Explicit

Status: Normative

RtkCollector currently targets external USB GNSS receivers. Features that
display receiver, correction, RTKLIB or satellite-monitoring state MUST NOT
imply Android internal GNSS is a supported receiver input unless a future
specification explicitly adds that workflow.

Verification:
- Review: user-facing copy and UI labels avoid Android internal GNSS input
  claims.

## Satellite Monitoring

### SATMON-ARCH-001: Satellite Monitor Is Advisory

Status: Normative

Satellite-monitor UI, parser, aggregation and selected-engine usage failures
MUST NOT stop, alter or delay byte-exact receiver recording, app-to-receiver TX
capture, correction input capture, NTRIP routing, RTKLIB workers or session
writers.

Verification:
- Automated: satellite monitor model/UI tests where practical.
- Review: satellite monitor call sites run after raw byte persistence and
  remain advisory.

### SATMON-STATE-001: Satellite Source Semantics Stay Separate

Status: Normative

Satellite monitoring MUST keep rover-observed, base-present and
selected-solution-used states separate. Missing source data MUST be displayed as
unsupported, unavailable or stale rather than inferred from another source.

Verification:
- Automated: satellite monitor model tests.
- Manual: receiver/caster smoke tests for supported profiles.

### SATMON-PROFILE-001: Satellite Telemetry Capability Is Explicit

Status: Normative

Command profiles MUST declare whether they support satellite telemetry and, if
they do, which receiver/protocol telemetry family they enable. Satellite
monitoring MUST use that declared capability instead of inferring support from
profile names, receiver family strings or command-script text. Profiles without
declared telemetry support MUST show an explicit unsupported state rather than
referring to a hidden or unavailable monitor profile.

Verification:
- Automated: command-profile JSON/default-profile tests.
- Manual: built-in telemetry-capable profiles show a supported waiting state
  while unsupported profiles show an explicit unsupported state.
