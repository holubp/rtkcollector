# Base RTCM NTRIP Caster Upload Design

## Purpose

Add a future base workflow capability for uploading RTCM corrections from
RtkCollector to an NTRIP caster such as rtk2go or a private caster. This is a
separate follow-up to the recording-safe telemetry isolation cleanup.

The feature must let temporary-base and fixed-base workflows stream correction
data downstream while preserving byte-exact recording and service robustness.

## Dependency

This feature depends on
[Recording-Safe Telemetry Isolation](2026-06-17-recording-safe-telemetry-isolation-design.md).

The caster-upload worker must be isolated from:

- USB read and raw recording;
- advisory parsing;
- dashboard rendering;
- Android mock-location publishing;
- averaging metrics;
- future RTKLIB-EX processing.

Upload failure must never stop raw receiver recording while transport and
storage still function.

## Goals

- Configure NTRIP caster upload profiles from the menu.
- Enable or disable caster upload per base workflow.
- Support fixed-base upload from accepted coordinates.
- Support temporary-base upload only when an explicit workflow policy allows it.
- Record upload status and bytes without contaminating `receiver-rx.raw`.
- Keep caster reconnect behaviour independent of receiver capture.
- Make rtk2go/private-caster setup practical but not hard-coded.

## Non-Goals

- Do not make Android a general-purpose NTRIP caster/server in V1.
- Do not upload from rover workflows.
- Do not upload before base-coordinate safety checks pass.
- Do not implement Android PPP/static solving as part of upload.
- Do not add maps, GIS, shapefile or feature-collection functionality.

## Workflow Semantics

### Fixed Base

Fixed-base upload requires an accepted base coordinate. The coordinate may come
from:

- manual known-coordinate entry in the Android UI;
- imported `base-position.json`;
- accepted temporary-base result transferred by the app.

The receiver must be configured as a fixed base where supported. RTCM output
from the receiver can then be uploaded to the configured caster.

`base-position.json` is the canonical serialization/import/export format for an
accepted coordinate. It must not be treated as the only way for a user to reach
fixed-base upload.

### Base Coordinate Acquisition

The implementation plan must add base-coordinate acquisition UI because manual
entry does not exist yet.

Required acquisition paths:

- manual entry screen or dialog for latitude, longitude, ellipsoidal height,
  frame/datum, antenna height and antenna reference point;
- validation before accepting the coordinate;
- "use current/averaged temporary-base coordinate as fixed base" action that
  transfers the accepted candidate into the fixed-base workflow;
- import existing `base-position.json` for externally processed coordinates;
- persist accepted coordinates in the same internal model that can be exported
  as `base-position.json`.

Manual entry should be explicit and cautious: a mistyped base coordinate can
make downstream rover positions consistently wrong while still appearing
precise.

### Temporary Base

Temporary-base upload is more sensitive. It is allowed only when the workflow
explicitly permits it and the UI communicates the coordinate source state.

Supported states:

- upload disabled while the temporary base is still determining its coordinate;
- upload enabled after user accepts a current RTK/PPP/manual coordinate;
- upload enabled for a documented temporary-base strategy where downstream
  rovers accept the base-coordinate quality.

Long-term averaging is a fallback coordinate strategy and must be labelled as
lower-grade than static RTK, PPP/static or a known control point.

### Rover

Rover workflows must not expose caster-upload start controls. Rover workflows
may still use an NTRIP client to receive corrections and feed the receiver.

## Configuration Model

Add profiles separate from NTRIP client profiles.

### Caster Upload Profile

Fields:

- profile id;
- display name;
- host;
- port;
- mountpoint;
- username;
- password secret reference;
- protocol policy, for example NTRIP v2 preferred with compatibility fallback;
- optional user-agent override;
- reconnect policy;
- enabled-by-default flag for selected base workflows.

Plaintext passwords must not be written to session metadata. Settings export
may include plaintext passwords only when the existing explicit export option
for secrets is selected by the user.

### Base Stream Profile

Fields:

- receiver profile id or family;
- expected RTCM source, such as receiver RX extraction or dedicated receiver
  output stream;
- expected RTCM message set;
- minimum message health policy;
- whether to record upload stream sidecar;
- whether to allow upload from temporary-base state.

The first implementation should prefer RTCM extracted from the receiver stream
already being recorded. A future dedicated second-port base output can be added
without changing the session semantics.

## Menu And UI

Settings menu:

- add "NTRIP caster upload profiles" under the NTRIP/base group;
- list profiles with active/default markers;
- support add, edit, copy, rename and delete;
- allow password entry with show/hide control;
- allow optional sourcetable/check action if the caster supports it;
- validate required host, port and mountpoint before saving.

Workflow/settings set:

- fixed-base settings can select a caster upload profile or disabled;
- temporary-base settings can select disabled, ask at start, or a specific
  upload profile when workflow policy allows it;
- rover settings must show upload as disabled/not applicable.

Main dashboard:

- base workflows show caster-upload status when configured;
- status values include Disabled, Connecting, Streaming, Reconnect wait,
  Auth error, Network error and Stopped;
- show upload URL/mountpoint, bytes uploaded, upload rate and last error;
- provide a compact action to disable upload during recording;
- do not expose upload controls in rover workflows.
- fixed-base setup must provide a way to enter or select the accepted base
  coordinate before upload can start.

## Runtime Architecture

The caster-upload worker is a service-owned optional consumer:

```text
receiver RX raw recording
  -> advisory RTCM extractor
  -> bounded upload queue
  -> caster upload worker
  -> upload status snapshot
```

Rules:

- `receiver-rx.raw` is written before RTCM extraction.
- RTCM upload queue is bounded.
- Queue overflow marks upload degraded; raw recording continues.
- Caster reconnect does not block receiver capture.
- Upload auth errors stop retrying until configuration changes.
- Network errors enter reconnect wait.
- Upload status is recorded in event/quality sidecars, not in `receiver-rx.raw`.

If the receiver outputs RTCM interleaved with solution logs, the app extracts
valid RTCM frames for upload. Invalid or partial frames are not uploaded.

## Receiver Output Sanity Check

Caster upload must validate that the selected receiver/base configuration is
expected to produce enough RTCM for useful downstream rover corrections. This is
separate from validating the caster host, credentials and mountpoint.

The sanity check should run before upload starts and should be visible in the
profile/workflow UI before recording where practical.

Minimum checks:

- selected workflow is fixed base or explicitly allowed temporary base;
- receiver family/profile supports base RTCM output or extractable RTCM in the
  recorded receiver stream;
- command profile contains an RTCM-output configuration or a known built-in
  base-output profile;
- selected coordinate source is accepted when fixed-base upload is requested;
- expected RTCM message set includes base reference position and observation
  messages sufficient for the configured receiver/constellations.

For UM980, the first practical validation should recognise configured base RTCM
output commands in the selected init/runtime profile. It should warn or block if
the profile only enables solution logs such as `BESTNAVB` without RTCM base
messages. Exact UM980 RTCM command recognition must be grounded in the local
Unicore documentation and existing tested command profiles.

Validation outcomes:

- pass: upload may start;
- warning: upload may start only if the user explicitly accepts reduced
  usefulness, for example missing optional constellations;
- error: upload cannot start, for example no RTCM output is configured.

Runtime should keep checking stream health. If upload starts but no valid RTCM
frames are observed within a bounded time, the caster worker enters a degraded
state and raw recording continues.

## Session Artifacts

The session should record:

- `receiver-rx.raw` as authoritative raw receiver stream;
- `rtcm-extracted.rtcm3` when RTCM extraction is enabled;
- a future `caster-upload.raw` or `caster-upload.rtcm3` if upload sidecar is
  enabled;
- `events.jsonl` upload state transitions and redacted configuration changes;
- `quality-live.jsonl` upload throughput and RTCM message health;
- `session.json` upload configuration metadata without secrets.

`session.json` must include:

- caster upload enabled/disabled;
- upload profile id/name;
- host, port and mountpoint;
- username redacted or recorded as non-secret identifier only;
- password secret reference or redacted presence flag;
- upload start/stop timestamps;
- upload validation result.

## Safety Rules

- Fixed-base upload requires accepted base coordinates.
- Temporary-base upload requires explicit user acceptance or workflow policy.
- Upload must not start from an unaccepted base-calibration session.
- Upload must not start when the selected receiver configuration cannot produce
  the minimum required RTCM stream.
- Upload from rover workflows is invalid.
- Upload must not use plaintext secrets in session metadata.
- Upload worker failure is degraded unless it causes storage failure while
  writing required artifacts.
- Caster authentication failure must not keep retrying aggressively.

## Testing Strategy

Required tests for future implementation:

- fixed-base upload profile validation requires host, port and mountpoint;
- manual base-coordinate entry validates required coordinate, frame/datum and
  antenna fields before accepting;
- accepted temporary-base coordinate can be transferred into fixed-base setup;
- imported `base-position.json` and manually accepted coordinates produce the
  same internal accepted-coordinate model;
- plaintext passwords are excluded from `session.json`;
- rover workflow rejects caster upload;
- fixed-base workflow rejects upload without accepted base coordinates;
- temporary-base workflow rejects upload unless explicitly allowed;
- selected base receiver configuration without RTCM output is rejected;
- selected base receiver configuration missing optional RTCM messages warns
  without deleting user configuration;
- upload queue overflow does not block raw recording;
- auth error stops retrying and reports terminal upload state;
- network error enters reconnect wait and recording continues;
- valid RTCM frames are uploaded and optionally written to upload sidecar;
- invalid RTCM frames are not uploaded.

Manual field validation:

- fixed-base session streams to private caster or rtk2go;
- rover can receive corrections from that caster using another app or receiver;
- disconnect network during upload and verify reconnect without recording loss;
- stop recording and verify uploader stops, writers close and metadata finalises.

## Implementation Monitoring

This feature should be implemented after the telemetry isolation cleanup. Its
implementation plan must explicitly track:

- which receiver families support base RTCM output;
- whether RTCM comes from receiver RX extraction or another transport;
- upload queue capacity and overflow policy;
- exact worker lifecycle in foreground service;
- manual coordinate entry UI and temporary-base-to-fixed-base transfer flow;
- menu and settings-set integration;
- session artifact additions;
- field-test checklist for rtk2go/private caster.
