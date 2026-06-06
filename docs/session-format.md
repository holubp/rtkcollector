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

NTRIP metadata must exclude secrets. The raw stream must be stored only in
`receiver-rx.raw`; app timestamps and markers belong in sidecar files.

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
- `method`: `manual`, `long-average`, `static-RTK`, `PPP-static`,
  `receiver-survey-in` or `external`
- `durationSeconds`
- `uncertaintyMeters`
- `antennaHeightMeters`
- `antennaReferencePoint`
- `sourceSessionReference`
