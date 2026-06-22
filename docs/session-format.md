# Session Format

## Folder Layout

```text
session/
  session.json
  receiver-rx.raw
  tx-to-receiver.raw
  correction-input.raw
  correction-input.rtcm3
  base-caster-upload.rtcm3 optional, fixed-base/base upload stream
  events.jsonl
  quality-live.jsonl
  receiver-solution.nmea optional, derived from receiver RX
  receiver-solution.jsonl optional, derived from receiver RX
  receiver-ppp-solution.jsonl optional
  rtklib-solution.nmea optional, V2 RTKLIB-EX output
  rtklib-solution.pos optional, V2 RTKLIB-EX output
  rtklib-postprocessed-forward.nmea optional, completed-session RTKLIB forward output
  rtklib-postprocessed-forward.pos optional, completed-session RTKLIB forward output
  rtklib-postprocessed-combined.nmea optional, completed-session RTKLIB forward/backward output
  rtklib-postprocessed-combined.pos optional, completed-session RTKLIB forward/backward output
  rtklib-status.jsonl optional, V2 RTKLIB-EX diagnostics
  init-script.txt
  receiver-profile.json
  base-position.json optional
  rtcm-extracted.rtcm3 optional
```

## `session.json`

Required fields:

- `appVersion`
- `androidDeviceModel`
- `androidVersion`
- `receiverDriverId`
- `receiverIdentification`
- `workflowSpecVersion`
- `workflowId`
- `workflowName`
- `receiverRole`
- `correctionSource`
- `correctionTargets`
- `solutionEngines`
- `baseContext`
- `basePositionCandidateGeneration`
- `recordingExpectations`
- `qualityMonitoringExpectations`
- `workflowValidationAtStart`
- `usbVid`
- `usbPid`
- `baudRate`
- `serialParameters`
- `mode`: `rover`, `fixed-base`, `temporary-base-preparation` or
  `replay-test`
- `startedAt`
- `stoppedAt`
- `ntrip`
- `commandProfileId`
- `usbBaudProfileId`
- `ntripCasterProfileId`
- `ntripMountpointProfileId`
- `recordingPolicyId`
- `storageProfileId`
- `storageKind`
- `antenna`
- `sessionUuid`
- `linkedBaseSessionUuid`
- `baseCasterUploadEnabled`
- `baseCasterUploadHost`
- `baseCasterUploadPort`
- `baseCasterUploadMountpoint`
- `baseCasterUploadUsernamePresent`
- `baseCasterUploadSecretRef`
- `baseCasterUploadFinalStatus`

Workflow fields mirror the validated `WorkflowSpec` described in
[Workflows](workflows.md). `correctionSource` metadata must exclude secrets.
NTRIP passwords, tokens and raw credentials must never appear in `session.json`;
only secret references or redacted metadata are allowed. The raw stream must be
stored only in `receiver-rx.raw`; app timestamps and markers belong in sidecar
files.

`solutionEngines` must distinguish the normal receiver in-device solution from
receiver PPP solution and from future RTKLIB real-time solution. Temporary-base
preparation sessions should record raw observations at `>= 1 Hz` where
supported, record normal device solution and record receiver PPP solution where
supported.

When RTKLIB-EX real-time processing is enabled in V2, session metadata must
record whether RTKLIB was enabled, the selected RTKLIB profile id, preset,
RTKLIB-EX snapshot id once native code is bundled, forward-only route plan,
receiver input route, correction input route and enabled output artifacts.
RTKLIB solution artifacts are derived/advisory outputs and must stay separate
from receiver-derived `receiver-solution.nmea` and `receiver-solution.jsonl`.
The real-time user-facing RTKLIB outputs are `rtklib-solution.nmea` and
`rtklib-solution.pos`; optional diagnostics belong in `rtklib-status.jsonl`.
Completed-session postprocessing may add `rtklib-postprocessed-forward.nmea`,
`rtklib-postprocessed-forward.pos`, `rtklib-postprocessed-combined.nmea` and
`rtklib-postprocessed-combined.pos`. These files are shareable only after an
explicit postprocessing action has generated them. For filesystem-backed
sessions, that action calls the native RTKLIB-EX library path: `convrnx()` for
receiver/correction conversion and `postpos()` for the selected forward or
forward/backward solution mode.
The current configuration metadata fields are `rtklibProfileId`,
`rtklibEnabled`, `rtklibPreset`, `rtklibOutputNmea` and `rtklibOutputPos`.
These fields must not contain NTRIP passwords, tokens or other secrets.

Experimental V1 writes `receiver-rx.raw`, `tx-to-receiver.raw`,
`correction-input.raw`, `correction-input.rtcm3`, `events.jsonl`,
`quality-live.jsonl` and selected derived solution sidecars as separate
append-only artifacts. Receiver RX is the authoritative raw capture. Receiver TX
includes init commands, shutdown commands and RTCM correction bytes that were
sent to the receiver. Correction input records the NTRIP byte stream before
receiver injection; `correction-input.rtcm3` is a same-byte RTCM3-named copy for
downstream tools that expect an RTCM3 extension. The canonical correction input
is `correction-input.raw`; the `.rtcm3` mirror is best-effort where storage
permits. `receiver-solution.nmea` and
solution JSONL files are advisory exports derived from the RX stream and may be
disabled by recording policy; parser/export failure must not stop raw
recording. Direct NMEA sharing offers only non-empty NMEA artifacts that already
exist for the selected session: receiver in-device `receiver-solution.nmea`,
real-time `rtklib-solution.nmea`, and any generated RTKLIB postprocessed NMEA
files. Sharing creates temporary `.nmea` copies for Android send-to workflows;
it must not regenerate, reinterpret or modify the authoritative
`receiver-rx.raw` stream.
When derived NMEA is generated from binary or structured receiver telemetry, UTC
fields should preserve sub-second precision available in the source solution,
and binary/noise fragments must not be copied into the NMEA sidecar. The
recording-output profile controls how converged receiver PPP is mapped into the
GGA quality field for generated NMEA. V1 allows `2`, `5` or `9`, defaults to
`2`, and must not emit `4` for PPP unless a future parser can explicitly prove
PPP-AR/PPP-RTK integer-fixed status.

For stopped sessions, the app may regenerate `receiver-solution.nmea` from
`receiver-rx.raw` on explicit user request. This operation uses only the best
available receiver/in-device solution for the receiver family, replaces only the
receiver-derived NMEA sidecar when at least one sentence is produced, and must
leave `receiver-rx.raw`, `tx-to-receiver.raw`, correction input, RTKLIB
artifacts and session metadata unchanged. Filesystem-backed storage and SAF
document-tree storage should both expose this workflow; SAF implementations
must use stream-based reads and writes because document URIs are not filesystem
paths.

Fixed-base and temporary-base workflows that upload generated RTCM to an
external NTRIP caster write the exact bytes offered to the caster to
`base-caster-upload.rtcm3`. This file is separate from `receiver-rx.raw`,
`tx-to-receiver.raw`, `correction-input.raw` and `rtcm-extracted.rtcm3` so that
downstream tools can distinguish receiver output, app-to-receiver input,
upstream correction intake and base-caster publication. The related
`baseCasterUpload*` session metadata is redacted: it may include host, port,
mountpoint, username-present flag, secret reference and final status, but must
not include the caster upload password or token.

`storageKind` records whether the session used app-private storage or a SAF
tree. For SAF sessions, the UI-displayed session location may be a document URI
rather than a filesystem path. SAF tree URIs are profile/runtime routing data;
they are not credentials, but exports should avoid treating them as portable
paths because they are Android-provider specific.

Runtime NTRIP changes are represented as redacted `events.jsonl` entries, not
as markers in `receiver-rx.raw`. Future structured exports such as GPX must be
finalised with best-effort closing syntax; failure to finalise them is a
sidecar error and does not invalidate raw RX.

## Session Archives

Session archives are storage-management artifacts, not live recording folders.
The Android app may create temporary share ZIPs in app cache; these are intended
only for Android send-to workflows and should be removed on a best-effort basis
through delayed cleanup after sharing and before later share attempts. Sharing
multiple selected sessions creates one temporary ZIP per session rather than one
combined multi-session archive. Direct NMEA shares are also temporary cache
artifacts, not session archives. The legacy temporary name `<session>.nmea`
is preserved when sharing only `receiver-solution.nmea`; RTKLIB and multi-source
shares use explicit source suffixes such as `<session>-rtklib-realtime.nmea`.

A permanent archive is a maximum-compression ZIP stored next to the session
folder in configured filesystem storage. Archiving must verify the ZIP before
removing the original folder. Restoring must extract the ZIP to a session folder,
verify that expected session artifacts are present and remove the ZIP only after
the restore succeeds. Active recording sessions must not be archived, restored
or deleted.

## `base-position.json`

`base-position.json` is the canonical serialization for accepted base
coordinates used by fixed-base workflows. Users do not need to hand-create this
file before using the app: the UI can create accepted coordinates manually,
import them from JSON, or promote temporary-base RTK/PPP/average candidates.
When fixed-base recording starts, the accepted coordinate is written into the
session as `base-position.json`.

Required fields:

- `latitudeDegrees`
- `longitudeDegrees`
- `heightMeters`
- `ecefXMeters`
- `ecefYMeters`
- `ecefZMeters`
- `frame`
- `epoch`
- `method`: `manual-known-point`, `static-RTK`, `PPP-static`,
  `receiver-PPP`, `long-average`, `receiver-survey-in`,
  `external-base-position-json` or `unknown`
- `durationSeconds`
- `horizontalUncertaintyMeters`
- `verticalUncertaintyMeters`
- `antennaHeightMeters`
- `antennaReferencePoint`
- `sourceSessionReference`
