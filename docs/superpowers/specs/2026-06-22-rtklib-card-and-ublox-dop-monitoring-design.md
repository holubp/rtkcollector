# RTKLIB Card And u-blox DOP Monitoring Design

## Purpose

Improve live monitoring for two related field-test gaps:

- when RTKLIB real-time processing finds a solution, the dashboard should show
  more than only RTKLIB fix type;
- u-blox M8T built-in monitoring profiles should provide enough in-device
  solution telemetry for UTC and DOP display on the main Position/Fix cards.

The change must preserve RtkCollector's recording architecture. Raw receiver
capture remains authoritative, and RTKLIB, u-blox parsing and dashboard display
remain advisory. Missing optional telemetry must display as `n/a`; it must not
block recording or trigger receiver reconfiguration outside the selected command
profile.

## Scope

In scope:

- extend the existing RTKLIB dashboard card with RTKLIB solution position,
  ellipsoidal height and horizontal/vertical accuracy;
- keep RTKLIB values inside the RTKLIB card only;
- enable u-blox `UBX-NAV-DOP` in the built-in u-blox profiles that are intended
  for live monitoring;
- parse u-blox `UBX-NAV-DOP` and map it to the existing main Fix-card
  `PDOP` and `HDOP / VDOP` fields;
- verify or repair u-blox `UBX-NAV-PVT` UTC propagation to the Position card;
- keep u-blox horizontal/vertical accuracy sourced from `UBX-NAV-PVT hAcc/vAcc`;
- update built-in u-blox profiles and any exported settings fixture consistently
  where profile configuration changes are required;
- add focused mapper/parser/profile tests.

Out of scope:

- showing RTKLIB values in the main Position or Fix cards;
- showing RTKLIB orthometric/MSL altitude;
- inventing u-blox latitude or longitude error estimates from horizontal
  accuracy;
- enabling NMEA only to obtain UTC when `UBX-NAV-PVT` already provides valid
  UTC fields;
- changing raw capture, NTRIP routing, RTKLIB input routing or storage
  behaviour.

## RTKLIB Dashboard Card

The existing RTKLIB card remains the only place where RTKLIB real-time solution
details are shown in this pass. This avoids confusing receiver-internal
monitoring with RTKLIB advisory processing.

When RTKLIB is disabled, the card remains hidden as today. When RTKLIB is
enabled but has no solution yet, the card keeps showing state, fix, age, route,
snapshot, queues, dropped bytes, decoded counts and output counts.

Once `RtklibSolutionSnapshot` contains a latest solution, the RTKLIB card should
also show:

- `Lat/Lon` from `latDeg` and `lonDeg`;
- `Ell h` from `ellipsoidalHeightM`;
- `Acc H/V` from `horizontalAccuracyM` and `verticalAccuracyM`.

The card must not show altitude. RTKLIB currently exposes ellipsoidal height in
the app model, not a distinct orthometric/MSL altitude. If any RTKLIB field is
missing, the corresponding display value is `n/a`.

The formatting should match existing dashboard conventions:

- latitude/longitude use fixed decimal formatting and may wrap onto two lines
  if space is tight;
- height and accuracy use existing metric display helpers or equivalent
  compact metre/cm/mm formatting;
- layout remains compact enough for phone portrait cards.

## u-blox DOP Monitoring

The `u-blox M8T raw 5 Hz RTKLIB-EX` profile currently enables raw observation,
timing, position and satellite-status messages, but not `UBX-NAV-DOP`. To fill
`PDOP` and `HDOP / VDOP`, the built-in u-blox live-monitoring profiles should
enable NAV-DOP on USB:

```text
!UBX CFG-MSG 1 4 0 0 0 1 0 0
```

At minimum, the command belongs in:

- `u-blox M8T raw 5 Hz RTKLIB-EX`;
- `u-blox M8T raw + status/mock`.

Do not change `u-blox M8T raw 1 Hz safe` in this pass. It remains the
conservative long-recording profile unless later field testing shows NAV-DOP is
needed there too.

Profile changes must update the app-distributed built-in definitions in source,
not only local/user settings. If an importable settings JSON needs to be edited
for testing or transfer between devices, start from the local untracked sample:

```text
samples/rtkcollector-settings-1781935653886-ublox-rtklib-updated-no-passwords.json
```

Do not reconstruct that JSON from scratch and do not introduce NTRIP passwords.
The `samples/` copy remains local debugging/configuration evidence and must not
be committed unless the repository policy is explicitly changed.

The parser should add a small `UBX-NAV-DOP` decoder in the u-blox receiver
module. It should validate the UBX frame, require class `0x01`, id `0x04`, and
decode the DOP fields as u-blox centi-DOP values:

- `gDOP`;
- `pDOP`;
- `tDOP`;
- `vDOP`;
- `hDOP`;
- `nDOP`;
- `eDOP`.

The service should record NAV-DOP frequency and update:

- `PDOP` from `pDOP`;
- `HDOP / VDOP` from `hDOP` and `vDOP`.

The existing u-blox frequency line should include `NAV-DOP` so users can see
whether the receiver is actually emitting it.

## u-blox UTC And Accuracy

`UBX-NAV-PVT` already carries UTC date/time fields and validity flags. It should
remain the primary source for the Position-card UTC value for u-blox profiles.
The implementation should verify that valid NAV-PVT UTC reaches the dashboard.

If NAV-PVT has a usable position but UTC validity flags are not set, the
Position-card UTC remains `n/a`. The app must not substitute Android wall-clock
time or receiver arrival time.

u-blox `NAV-PVT hAcc/vAcc` remain the source for main Fix-card horizontal and
vertical accuracy. These are not the same as separate latitude and longitude
errors, so the main Position-card `Lat error` and `Lon error` fields remain
`n/a` for u-blox until a receiver-native per-axis source is added.

## Data Flow

### RTKLIB

1. Native RTKLIB produces a `RtklibSolutionSnapshot`.
2. `RtklibWorker.snapshot()` exposes the latest solution.
3. `RecordingForegroundService.broadcastState()` copies the RTKLIB solution
   fields into explicit RTKLIB extras.
4. `DashboardServiceMapper` maps the extras into `RtklibCardState`.
5. `HomeDashboard.RtklibCard` displays RTKLIB solution metrics inside the
   RTKLIB card only.

### u-blox

1. Built-in u-blox command profiles enable NAV-DOP where live monitoring needs
   DOP.
2. Optional importable settings JSON is updated from the named no-passwords
   local sample when a user-transfer configuration is needed.
3. The advisory u-blox stream parser emits UBX frames from recorded receiver
   bytes.
4. The u-blox parser decodes NAV-DOP and NAV-PVT.
5. `RecordingForegroundService` updates dashboard state from parsed telemetry.
6. The main Position/Fix cards display UTC, PDOP and HDOP/VDOP when available.

## Error Handling

- Missing RTKLIB solution fields display as `n/a`.
- Invalid or unsupported NAV-DOP frames are ignored as parser failures; raw
  recording continues.
- NAV-PVT UTC is used only when its validity flags indicate valid date and time.
- If NAV-DOP is not emitted by a copied/edited user profile, DOP fields remain
  `n/a`.
- No parser exception may stop recording, NTRIP, RTKLIB or session writers.

## Testing

Add focused tests for:

- `RtklibCardState` mapping of solution lat/lon, ellipsoidal height and H/V
  accuracy extras;
- RTKLIB card rendering/model expectations for missing values as `n/a`;
- `UBX-NAV-DOP` parser validation and centi-DOP scaling;
- u-blox built-in profile strings containing the NAV-DOP enable command where
  expected;
- if a settings JSON export is updated locally, diff it against
  `samples/rtkcollector-settings-1781935653886-ublox-rtklib-updated-no-passwords.json`
  and verify no plaintext passwords or unrelated NTRIP changes were introduced;
- service/dashboard mapping from parsed NAV-DOP to `PDOP` and `HDOP / VDOP`;
- NAV-PVT UTC propagation to dashboard state using a valid UTC sample;
- frequency-line formatting after adding `NAV-DOP`.

Termux validation should use focused JVM/Python/source tests and
`:app:compileDebugKotlin`. Hardware validation remains required on M8T to
confirm NAV-DOP emission and live dashboard values.
