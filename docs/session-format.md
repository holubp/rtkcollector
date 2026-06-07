# Session Format

## Folder Layout

```text
session/
  session.json
  receiver-rx.raw
  tx-to-receiver.raw
  correction-input.raw
  events.jsonl
  quality-live.jsonl
  device-solution.jsonl optional
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
- `antenna`
- `sessionUuid`
- `linkedBaseSessionUuid`

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
`correction-input.raw`, `events.jsonl` and `quality-live.jsonl` as separate
append-only artifacts. Receiver RX is the authoritative raw capture. Receiver
TX includes init commands, shutdown commands and RTCM correction bytes that were
sent to the receiver. Correction input records the NTRIP byte stream before
receiver injection.

## `base-position.json`

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
