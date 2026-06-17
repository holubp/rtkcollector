# Session Artifact Requirements

## Session Files

### SESSION-FILES-001: Required Artifact Separation

Status: Normative

Sessions MUST distinguish receiver RX, app TX to receiver, correction input,
events, quality logs, generated solution exports and metadata as separate
artifacts.

Verification:
- Automated: session writer and artifact model tests.
- Manual: completed session contains expected files for selected recording
  outputs.

### SESSION-META-001: Session Metadata Records Workflow Context

Status: Normative

`session.json` SHOULD record workflow, receiver, correction-source, artifact and
validation context without embedding secret values.

Verification:
- Automated: session metadata writer tests.
- Review: metadata fields align with `docs/session-format.md`.

## Generated Exports

### SESSION-NMEA-001: Generated NMEA Preserves Sub-Second UTC

Status: Normative

Generated `receiver-solution.nmea` MUST preserve sub-second UTC where source
telemetry provides it. It MUST NOT copy binary/noise fragments that merely look
like dollar-prefixed NMEA lines.

Verification:
- Automated: UM980 NMEA exporter/re-exporter tests.
- Manual: compare generated NMEA against known high-rate recording.

### SESSION-RTCM-001: NTRIP RTCM Is Exportable

Status: Normative

When NTRIP correction recording is enabled, the session MUST retain correction
input bytes in a form usable by downstream post-processing tools.

Verification:
- Automated: correction-input raw/RTCM3 extraction tests.
- Manual: replay correction stream through the downstream pipeline.

