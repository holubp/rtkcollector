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
  rtklib-solution.jsonl optional, V2
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
recording. Direct NMEA sharing uses `receiver-solution.nmea` as its source and
creates a temporary `.nmea` copy for Android send-to workflows; it must not
regenerate, reinterpret or modify the authoritative `receiver-rx.raw` stream.
When derived NMEA is generated from binary or structured receiver telemetry, UTC
fields should preserve sub-second precision available in the source solution,
and binary/noise fragments must not be copied into the NMEA sidecar. The
recording-output profile controls how converged receiver PPP is mapped into the
GGA quality field for generated NMEA. V1 allows `2`, `5` or `9`, defaults to
`2`, and must not emit `4` for PPP unless a future parser can explicitly prove
PPP-AR/PPP-RTK integer-fixed status.

For stopped sessions, the app may re-export `receiver-solution.nmea` from
`receiver-rx.raw` on explicit user request. This operation replaces only the
derived NMEA sidecar and must leave `receiver-rx.raw`, `tx-to-receiver.raw`,
correction input and session metadata unchanged. Filesystem-backed storage and
SAF document-tree storage should both expose this workflow; SAF implementations
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
artifacts, not session archives, and are recreated from `receiver-solution.nmea`
only when the user explicitly requests NMEA sharing.

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
