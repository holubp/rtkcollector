# Workflows

`docs/workflows.md` is the authoritative specification for RtkCollector session
workflows. Workflows are validated session plans, not unrelated hard-coded UI
modes.

RtkCollector is not a GIS app. Workflow design must not introduce maps,
shapefiles, GIS editing, field-feature collection or cartographic survey project
management.

## Conceptual Model

A workflow composes these dimensions:

- receiver role;
- correction source;
- correction target;
- solution engines;
- base context;
- recorded artifacts;
- receiver capabilities;
- workflow safety and validation rules.

The capture path remains authoritative. Parsers, NTRIP, RTKLIB, UI and quality
monitoring are advisory consumers or producers around the capture path. Parser
failure must never imply recording failure.

## Workflow Table

| Workflow | Receiver role | Corrections | Correction target | Base required | Device solution | RTKLIB solution | Raw observations |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Base calibration, raw only | `BASE_CALIBRATION` | none | none | no | optional | post-processing later | required if supported |
| Base calibration with CORS/NTRIP | `BASE_CALIBRATION` | `NTRIP` | `RECEIVER` and/or `RTKLIB` | yes, NTRIP/CORS station | yes/optional | optional | required if supported |
| Fixed base streaming | `FIXED_BASE` | none | downstream rover/caster later | yes, fixed coordinate | base status only | no | optional |
| Plain rover recording | `ROVER` | none | none | no | yes | no | required if supported |
| Rover + NTRIP to receiver | `ROVER` | `NTRIP` | `RECEIVER` | yes, NTRIP/CORS/mountpoint | yes | no | required if supported |
| Rover + RTKLIB real-time | `ROVER` | `NTRIP`/local base/file | `RTKLIB`, optionally `RECEIVER` | yes | optional/recorded | yes | required |

## Main Workflows

### Base Calibration Recording

Purpose: record a static receiver session that can later be processed into a
precise base coordinate.

Subtypes:

- raw static recording only;
- static recording while feeding CORS/EUREF/NTRIP corrections to the receiver;
- static recording while RTKLIB real-time solution is used as an advisory live
  check;
- static recording for long-term average or survey-in fallback.

Required properties:

- `receiverRole = BASE_CALIBRATION`;
- raw observations should be requested where the receiver supports them;
- device internal solution can be recorded if available;
- NTRIP corrections are optional;
- RTKLIB real-time is optional and requires base context;
- output must be eligible for later `base-position.json` generation.

### Fixed-Base Operation

Purpose: use an already known or calibrated coordinate to configure a receiver
as a fixed base and optionally stream RTCM corrections.

Required properties:

- `receiverRole = FIXED_BASE`;
- base position is required;
- receiver profile must support fixed-base mode;
- RTCM output may be recorded or extracted;
- NTRIP server upload and caster upload are later implementation tasks.

Fixed-base operation must not start directly from a base-calibration session.
A base-position candidate must first be accepted as an explicit fixed-base
coordinate or imported `base-position.json`.

### Plain Rover Recording

Purpose: record rover data without external corrections.

Required properties:

- `receiverRole = ROVER`;
- `correctionSource = none`;
- `correctionTargets = empty`;
- `solutionEngines` includes `DEVICE_INTERNAL` where supported;
- raw observations should be requested where supported;
- expected device fix is standalone/single, SBAS or DGNSS if supported.

### Rover Recording With NTRIP Fed Into Receiver

Purpose: use Android as NTRIP client and feed RTCM corrections into the
receiver so the receiver itself can generate RTK float/fix.

Required properties:

- `receiverRole = ROVER`;
- `correctionSource = NTRIP`;
- `correctionTargets` contains `RECEIVER`;
- device internal solution is enabled;
- base context represents the NTRIP/CORS/mountpoint source;
- all correction bytes sent to the receiver must later be recordable in a TX or
  correction sidecar.

### Rover Recording With RTKLIB Real-Time Solution

Purpose: compute an independent real-time RTKLIB solution while still recording
receiver data and optionally receiver internal solution.

Required properties:

- `receiverRole = ROVER`;
- `solutionEngines` contains `RTKLIB_REALTIME`;
- base context is required;
- correction source or local base observation source is required;
- `correctionTargets` contains `RTKLIB` and may also contain `RECEIVER`;
- receiver must provide raw observations in an RTKLIB-compatible form, or an
  explicit converter must be configured;
- device internal solution may be recorded and displayed separately.

RTKLIB must not be assumed to consume every receiver-native binary stream.
The workflow model distinguishes RTCM observation streams, receiver-native raw
decoded by a supported parser, file/replay/offline conversion and unsupported
raw observation formats.

## Base Position Strategies

Base calibration and fixed-base operation are separate concepts.

Base calibration records static data and may generate one or more later
base-position candidates. Fixed-base operation requires an accepted known
coordinate before receiver configuration starts.

Candidate-generation methods:

- static RTK against known CORS/EUREF/local base;
- PPP/static processing;
- receiver survey-in;
- long-term averaging;
- manual/known point;
- external `base-position.json`.

Long-term averaging is a fallback or lower-grade method, not the preferred
source of truth when PPP/static RTK is available.

## Lifecycle

1. User or automation selects receiver profile and workflow intent.
2. App builds a `WorkflowSpec` with receiver capabilities, correction source,
   base context, recording expectations and safety settings.
3. `WorkflowValidator` validates the spec before receiver commands, NTRIP
   connection or recording start.
4. UI presents errors and warnings. Errors block start; warnings require
   explicit user visibility.
5. Foreground capture service executes the validated workflow.
6. Raw receiver capture starts before advisory parsers and solution engines can
   affect UI state.
7. Session writers record workflow metadata, validation result and expected
   artifacts in sidecar/session metadata.

## Validation Rules

Required validation errors:

- Plain rover recording must not have correction targets.
- Plain rover recording must not have NTRIP correction source.
- Any correction target requires a non-empty correction source, except future
  fixed-base downstream output when modelled separately.
- `CorrectionTarget.RECEIVER` requires receiver RTCM input support when source
  is NTRIP or local base corrections.
- Recording workflows must keep the raw receiver RX stream enabled and list
  `receiver-rx.raw` as an expected artifact.
- Recording workflows must list `events.jsonl` as an expected artifact so
  lifecycle, validation and advisory failures are recoverable from sidecar
  metadata.
- Receiver TX, correction input and quality-event recording flags require their
  corresponding sidecar artifacts.
- Android recording workflows must require foreground-service execution and a
  wake lock while recording.
- NTRIP correction workflows require base context that represents the same
  NTRIP/CORS caster and mountpoint.
- `SolutionEngine.RTKLIB_REALTIME` requires `CorrectionTarget.RTKLIB`.
- `SolutionEngine.RTKLIB_REALTIME` requires base context.
- `SolutionEngine.RTKLIB_REALTIME` requires correction source or local/recorded
  base observation source.
- `SolutionEngine.RTKLIB_REALTIME` requires RTCM observation corrections, or an
  explicit converter for receiver-native correction/base observation formats.
- `SolutionEngine.RTKLIB_REALTIME` requires RTKLIB-compatible raw observations
  or an explicit converter.
- `FIXED_BASE` requires base position, base-position file or manual coordinate.
- `FIXED_BASE` requires fixed-base receiver capability.
- `RAW_REQUIRED` requires receiver raw-observation support.
- `RTKLIB_COMPATIBLE_REQUIRED` requires compatible raw observations or explicit
  converter.
- Receiver-internal RTK workflows require internal RTK receiver capability.
- NTRIP source must have host, port and mountpoint.
- NTRIP credentials must be secret references, not plaintext export values.
- `allowSecretsInSessionJson` must not be true.
- Fixed-base streaming must not start directly from a base-calibration session
  unless a base-position candidate has been accepted.

Required validation warnings:

- Base calibration should request raw observations when the receiver supports
  them.
- Long-term averaging should warn that it is fallback/lower-grade unless
  accompanied by uncertainty and duration metadata.
- Rover + NTRIP to receiver without raw observations should warn that
  post-processing validation will be limited.
- RTKLIB real-time plus receiver-internal solution should warn that they are
  separate solution engines and must be displayed/logged independently.
- NTRIP mountpoint without approximate station/base metadata should warn that
  reproducibility is weaker.
- Base position without frame/datum should warn.
- Base position without antenna metadata should warn.
- Receiver profile with custom init disabled should warn if custom init commands
  are requested.

## Examples

The Kotlin workflow module provides fixtures for:

- `baseCalibrationRawOnly()`;
- `baseCalibrationWithNtripToReceiver()`;
- `fixedBaseFromBasePosition()`;
- `plainRoverRecording()`;
- `roverWithNtripToReceiver()`;
- `roverWithRtklibRealtime()`;
- `roverWithNtripToReceiverAndRtklibRealtime()`.

Receiver capability fixtures cover generic NMEA+RTCM, UM980/N4, u-blox M8P-0,
u-blox M8P-2 and u-blox M8T.

## Relationship To `session.json`

`session.json` records what was started, not receiver bytes. It must include:

- workflow spec version;
- workflow id/name;
- receiver role;
- correction source metadata without secrets;
- correction targets;
- solution engines;
- base context reference;
- recording artifact expectations;
- quality monitoring expectations;
- validation result at session start.

`session.json` must never contain NTRIP passwords or tokens. It may contain only
secret references or redacted metadata.

## Relationship To Receiver Capabilities

Receiver capabilities constrain which workflows can start. A receiver profile
may support recording but not fixed-base operation, raw observations, internal
RTK or RTKLIB-compatible raw output. The workflow validator uses those
capabilities before commands or network connections are attempted.

## Relationship To Later Android UI

The Android UI should ask for workflow intent and required inputs, then start a
validated `WorkflowSpec`. UI labels may be user-friendly, but they must map back
to validated workflow dimensions rather than creating ad-hoc modes.
