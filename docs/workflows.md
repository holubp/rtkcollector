# Workflows

`docs/workflows.md` is the authoritative specification for RtkCollector session
workflows. Workflows are validated session plans, not unrelated hard-coded UI
modes.

RtkCollector is not a GIS app. Workflow design must not introduce maps,
shapefiles, GIS editing, field-feature collection or cartographic survey project
management.

## Version 1 Scope

Version 1 covers receiver-side recording and correction routing:

- plain rover recording;
- rover recording with NTRIP corrections fed to the receiver;
- temporary-base preparation recording;
- fixed-base operation from an accepted base position;
- replay/test sessions.

In-phone RTKLIB real-time solution is version 2. The domain model keeps RTKLIB
validation primitives so future or imported specs can be checked, but V1 user
workflows must not expose RTKLIB as a selectable live solution engine.

## Conceptual Model

A workflow composes these dimensions:

- receiver role;
- correction source;
- correction target;
- solution engines;
- base context;
- base-position candidate generation;
- recorded artifacts;
- receiver capabilities;
- workflow safety and validation rules.

The capture path remains authoritative. Parsers, NTRIP, RTKLIB, UI and quality
monitoring are advisory consumers or producers around the capture path. Parser
failure must never imply recording failure.

## V1 Workflow Table

| Workflow | Receiver role | Corrections | Correction target | Base required at start | Device solution | Receiver PPP | Raw observations |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Plain rover recording | `ROVER` | none | none | no | yes, if available | no | requested if supported |
| Rover + NTRIP to receiver | `ROVER` | `NTRIP` | `RECEIVER` | yes, NTRIP/CORS mountpoint context | yes | no | requested if supported |
| Temporary-base preparation, raw only | `BASE_CALIBRATION` | none | none | no | yes | requested if supported | required at >= 1 Hz if supported |
| Temporary-base preparation with CORS/NTRIP | `BASE_CALIBRATION` | `NTRIP` | `RECEIVER` | yes, NTRIP/CORS mountpoint context | yes | requested if supported | required at >= 1 Hz if supported |
| Fixed base from accepted coordinate | `FIXED_BASE` | none | downstream rover/caster later | yes, accepted fixed coordinate | base status only | no | optional |
| Replay/test | `REPLAY_TEST` | file replay | none | no | no | no | replayed from file |

## Main V1 Workflows

### Plain Rover Recording

Purpose: record rover data without external corrections.

Required properties:

- `receiverRole = ROVER`;
- `correctionSource = none`;
- `correctionTargets = empty`;
- `solutionEngines` includes `DEVICE_INTERNAL` where supported;
- raw observations are requested where supported;
- expected device fix is standalone/single, SBAS or DGNSS if supported.

### Rover Recording With NTRIP Fed Into Receiver

Purpose: use Android as NTRIP client and feed RTCM corrections into the
receiver so the receiver itself can generate RTK float/fix.

Required properties:

- `receiverRole = ROVER`;
- `correctionSource = NTRIP`;
- `correctionTargets` contains `RECEIVER`;
- device internal solution is enabled;
- base context represents the exact NTRIP/CORS caster and mountpoint;
- all correction bytes received and all bytes sent to the receiver are
  recordable in correction/TX sidecars;
- raw observations are requested where supported, so later validation is not
  limited to the receiver's live RTK result.

### Temporary-Base Preparation Recording

Purpose: place a temporary base close to the rover, preferably with open sky
visibility, and record enough evidence to derive an accepted base coordinate
later.

Temporary-base preparation is not a standalone final operating mode. It is the
coordinate-generation part of preparing a later fixed-base workflow.

Required properties:

- `receiverRole = BASE_CALIBRATION`;
- the raw receiver byte stream is recorded unchanged;
- raw observations are requested at `>= 1 Hz` when the receiver supports raw
  observations;
- the receiver's normal in-device solution is always recorded;
- receiver PPP solution is requested and recorded separately where the receiver
  profile supports it;
- optional NTRIP/CORS corrections may be fed to the receiver;
- output is eligible for later `base-position.json` candidate generation.

Base-position candidates are generated from the recording, not by switching to a
separate app mode. Candidate methods are ranked as:

1. static RTK against a known CORS/EUREF/local base;
2. PPP/static processing;
3. receiver in-device PPP solution;
4. long averaging of non-PPP in-device solution as fallback;
5. receiver survey-in where available and explicitly understood;
6. manual/known point or external `base-position.json`.

Long averaging is lower-grade fallback evidence. It must not be treated as
equivalent to static RTK or PPP/static processing.

### Fixed-Base Operation

Purpose: use an already accepted coordinate to configure a receiver as a fixed
base and optionally stream RTCM corrections later.

Required properties:

- `receiverRole = FIXED_BASE`;
- base position is required;
- receiver profile must support fixed-base mode;
- the accepted base position must be a manual coordinate, imported
  `base-position.json`, known station, or recorded-base-session candidate that
  has already been accepted;
- RTCM output may be recorded or extracted;
- NTRIP server upload and caster upload are later implementation tasks.

Fixed-base operation must not start directly from a temporary-base preparation
session. A base-position candidate must first be accepted as an explicit
fixed-base coordinate or imported `base-position.json`.

### Replay/Test

Purpose: replay a recorded receiver stream or test file as deterministic input.

Required properties:

- `receiverRole = REPLAY_TEST`;
- file replay is the correction/input source;
- no Android foreground-service or wake-lock requirement is implied for pure
  offline replay;
- replay must not rewrite the captured raw stream.

## V2 Workflow: RTKLIB Real-Time

RTKLIB real-time solution is explicitly deferred to version 2. When enabled in
future work, it must remain a separate solution engine from the receiver's own
internal solution and from receiver PPP.

Future RTKLIB requirements remain:

- `SolutionEngine.RTKLIB_REALTIME` requires `CorrectionTarget.RTKLIB`;
- explicit base context is required;
- correction source or local/recorded base observation source is required;
- RTCM observation corrections or an explicit converter are required;
- receiver raw observations must be RTKLIB-compatible, or a converter must be
  configured.

RTKLIB must not be assumed to consume every receiver-native binary stream.

## Lifecycle

1. User or automation selects receiver profile and V1 workflow intent.
2. App builds a `WorkflowSpec` with receiver capabilities, correction source,
   base context, candidate generation policy, recording expectations and safety
   settings.
3. `WorkflowValidator` validates the spec before receiver commands, NTRIP
   connection or recording start.
4. UI presents errors and warnings. Errors block start; warnings require
   explicit user visibility.
5. Foreground capture service executes validated real recording workflows.
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
- Recording workflows must list `events.jsonl` as an expected artifact.
- Receiver TX, correction input, PPP solution and quality-event recording flags
  require their corresponding sidecar artifacts.
- Android real recording workflows must require foreground-service execution
  and a wake lock while recording.
- Temporary-base preparation must record the receiver's normal in-device
  solution.
- Temporary-base preparation with a raw-capable receiver must request raw
  observations at `>= 1 Hz`.
- Temporary-base preparation must request receiver PPP solution recording when
  the receiver profile supports receiver PPP.
- Temporary-base preparation must declare base-position candidate generation
  methods.
- Base-position candidate generation methods must not contain duplicates.
- NTRIP correction workflows require base context that represents the same
  NTRIP/CORS caster and mountpoint.
- `FIXED_BASE` requires accepted base position, base-position file or manual
  coordinate.
- `FIXED_BASE` requires fixed-base receiver capability.
- `RAW_REQUIRED` requires receiver raw-observation support.
- Receiver-internal RTK workflows require internal RTK receiver capability.
- NTRIP source must have host, port and mountpoint.
- NTRIP credentials must be secret references, not plaintext export values.
- `allowSecretsInSessionJson` must not be true.
- Fixed-base streaming must not start directly from a temporary-base preparation
  session unless a base-position candidate has been accepted.

Required validation warnings:

- Temporary-base preparation without raw observations cannot support static RTK
  or RTCM3 observation conversion.
- Long-average base-position candidates should rank after static RTK,
  PPP/static and receiver PPP candidates.
- Long-average accepted base positions should carry uncertainty and duration
  metadata.
- Rover + NTRIP to receiver without raw observations should warn that
  post-processing validation will be limited.
- NTRIP mountpoint without approximate station/base metadata should warn that
  reproducibility is weaker.
- Base position without frame/datum should warn.
- Base position without antenna metadata should warn.
- Receiver profile with custom init disabled should warn if custom init commands
  are requested.

## Risks For Temporary Bases

- Receiver-native raw observations are not automatically RTCM3; conversion may
  require a supported receiver decoder.
- `1 Hz` raw observations are the minimum for V1 base preparation, not a quality
  guarantee.
- Receiver PPP may require subscription services, regional correction sources or
  long convergence; early PPP coordinates must not be silently accepted.
- Long averaging of standalone non-PPP fixes can be biased by metres.
- A temporary base must remain physically fixed. A car roof is usable only when
  the vehicle and antenna mount are stable.
- Antenna height, antenna reference point, phase-centre assumptions, frame/datum
  and epoch metadata directly affect the resulting base coordinate.
- A wrong base coordinate can produce precise-looking but wrong rover RTK
  positions.
- Car roofs can provide good open-sky visibility but may also introduce
  multipath depending on antenna, ground plane, roof rails and nearby objects.

## Examples

The Kotlin workflow module provides V1 examples for:

- `plainRoverRecording()`;
- `roverWithNtripToReceiver()`;
- `temporaryBasePreparation()`;
- `temporaryBasePreparationWithNtripToReceiver()`;
- `baseCalibrationRawOnly()` as a compatibility alias for temporary-base raw
  preparation;
- `baseCalibrationWithNtripToReceiver()` as a compatibility alias for
  temporary-base preparation with CORS/NTRIP corrections;
- `fixedBaseFromBasePosition()`;
- `replayTest()`;
- `version1UserWorkflows()`.

Future RTKLIB helper examples remain available for validation development but
are not V1 user workflows.

Receiver capability fixtures cover generic NMEA+RTCM, UM980/N4, u-blox M8P-0,
u-blox M8P-2 and u-blox M8T.

## Relationship To `session.json`

`session.json` records what was started, not receiver bytes. It must include:

- workflow spec version;
- workflow id/name;
- receiver role;
- correction source metadata without secrets;
- correction targets;
- solution engines, including receiver PPP separately from normal device
  solution;
- base context reference;
- base-position candidate generation policy;
- recording artifact expectations;
- quality monitoring expectations;
- validation result at session start.

`session.json` must never contain NTRIP passwords or tokens. It may contain only
secret references or redacted metadata.

## Relationship To Receiver Capabilities

Receiver capabilities constrain which workflows can start. A receiver profile
may support recording but not fixed-base operation, raw observations, internal
RTK, receiver PPP solution or RTKLIB-compatible raw output. The workflow
validator uses those capabilities before commands or network connections are
attempted.

## Relationship To Later Android UI

The Android UI should ask for workflow intent and required inputs, then start a
validated `WorkflowSpec`. UI labels may be user-friendly, but they must map back
to validated workflow dimensions rather than creating ad-hoc modes.
