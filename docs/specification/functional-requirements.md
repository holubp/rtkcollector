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
