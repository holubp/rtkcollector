# Session Format

## Folder Layout

```text
session/
  session.json
  receiver-rx.raw
  tx-to-receiver.raw
  events.jsonl
  quality-live.jsonl
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
- `recordingExpectations`
- `qualityMonitoringExpectations`
- `workflowValidationAtStart`
- `usbVid`
- `usbPid`
- `baudRate`
- `serialParameters`
- `mode`: `rover`, `base`, `base-calibration` or `replay-test`
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
  `long-average`, `receiver-survey-in`, `external-base-position-json` or
  `unknown`
- `durationSeconds`
- `horizontalUncertaintyMeters`
- `verticalUncertaintyMeters`
- `antennaHeightMeters`
- `antennaReferencePoint`
- `sourceSessionReference`
