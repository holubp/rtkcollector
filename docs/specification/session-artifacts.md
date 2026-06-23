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

`session.json` SHOULD record workflow, receiver, correction-source, artifact,
solution-policy and validation context without embedding secret values. When a
solution policy profile is active, metadata SHOULD include the selected profile
identifier plus the screen and mock-location source policies.

Verification:
- Automated: session metadata writer tests.
- Review: metadata fields align with `docs/session-format.md`.

## Generated Exports

### SESSION-NMEA-001: Generated NMEA Preserves Sub-Second UTC

Status: Normative

Generated `receiver-solution.nmea` MUST preserve sub-second UTC where source
telemetry provides it. It MUST NOT copy binary/noise fragments that merely look
like dollar-prefixed NMEA lines. Regeneration of NMEA for completed sessions
MUST update only `receiver-solution.nmea` from the receiver/in-device solution.
It MUST NOT overwrite RTKLIB real-time or postprocessed NMEA artifacts.

Verification:
- Automated: UM980 NMEA exporter/re-exporter tests; receiver-family NMEA
  regeneration tests; source-aware NMEA sharing tests.
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
`receiver-solution.jsonl`. Completed-session RTKLIB postprocessing MAY create
additional artifacts such as `rtklib-postprocessed-forward.nmea` and
`rtklib-postprocessed-combined.nmea`. When completed-session postprocessing
converts RTCM3 correction input to base-station RINEX before calling RTKLIB
`postpos()`, it MUST use the converted base RINEX header as the reference
position source. If native real-time RTKLIB text output is unavailable and the
app synthesizes a minimal GGA sentence from a solution snapshot, it MUST NOT
write ellipsoidal height into the NMEA GGA mean-sea-level altitude field or
claim a zero geoid separation. These artifacts MUST be shared only when they
already exist and MUST NOT be implied by the receiver NMEA regeneration action.

Verification:
- Automated: workflow artifact validation and RTKLIB output writer tests.
- Manual: RTKLIB-enabled session contains RTKLIB NMEA/POS artifacts while
  receiver raw and receiver solution artifacts remain separate.
