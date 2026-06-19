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

### SESSION-RTKLIB-001: RTKLIB Outputs Are Separate Advisory Artifacts

Status: Future

When RTKLIB-EX real-time processing is enabled, RTKLIB-derived user-facing
solution output MUST be stored separately from receiver-derived solution output.
The required RTKLIB artifacts are `rtklib-solution.nmea` and
`rtklib-solution.pos`. Optional RTKLIB engine diagnostics MAY be stored in
`rtklib-status.jsonl`. These artifacts MUST NOT replace or modify
`receiver-rx.raw`, `correction-input.raw`, `receiver-solution.nmea` or
`receiver-solution.jsonl`.

Verification:
- Automated: workflow artifact validation and RTKLIB output writer tests.
- Manual: RTKLIB-enabled session contains RTKLIB NMEA/POS artifacts while
  receiver raw and receiver solution artifacts remain separate.
