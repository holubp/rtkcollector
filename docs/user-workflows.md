# User Workflows

RtkCollector V1 is a receiver recorder and correction router. It is not a GIS
app: there are no maps, shapefiles, feature forms or survey-cartography project
tools.

## Experimental V1 Android UI

The Android UI lets the user choose a workflow and receiver profile. The derived
workflow details and expected recording artifacts are shown before validation.
It also provides the experimental real-recording controls:

- USB device refresh and Android USB permission request;
- profile baud and post-profile serial baud;
- editable init, workflow-mode and shutdown command sequences;
- optional NTRIP host, port, mountpoint, username, password and GGA upload line;
- manual fixed-base coordinates or pasted/imported `base-position.json`;
- foreground-service start/stop;
- live receiver RX, receiver TX, correction input and NTRIP state counters.

This UI intentionally does not implement RTKLIB, show maps, load shapefiles,
provide GIS editing or collect field features.

Receiver startup commands are represented as:

1. init sequence;
2. exactly one mode-specific sequence: rover, base-calibration or fixed-base.

Replay/test sessions use an empty receiver command plan. Shutdown commands are
optional and usually empty. They are modelled as a separate post-stop phase so
later receiver TX bytes can be logged without modifying the authoritative
receiver RX stream.

Runtime correction bytes are not command scripts. When implemented, they must
share the receiver TX path and be recorded separately from receiver RX.

While a real session is active, the foreground service owns capture. The
Activity only sends start/stop requests and observes service state.

## Experimental UM980 Recording

Use this only as an early field test path.

Recommended first test flow:

1. Connect the UM980/N4 through the known USB serial bridge.
2. Refresh USB devices and request Android USB permission.
3. Leave profile baud and serial baud at `230400` for the first capture.
4. Review the init and mode commands. The default profile is runtime-only and
   does not include `SAVECONFIG`.
5. Start recording without NTRIP and confirm `receiver-rx.raw` grows.
6. Stop recording and check the session folder path shown by the UI.
7. Repeat with NTRIP only after passive capture is stable.

The generated UM980 mode sequence requests `BESTNAVB` receiver solution,
`GPGGA` and `OBSVMCMPB` raw observations at `1 Hz`. Temporary-base workflows
also enable receiver PPP where supported and request `PPPNAVB` status every
10 seconds. Fixed-base RTCM output uses documented RTCM message command
families such as `RTCM1006`, `RTCM1074`, `RTCM1084`, `RTCM1094` and
`RTCM1124`. User changes to command scripts are validated against a
conservative deny-list before start.

If a profile changes receiver baud, RtkCollector opens the USB serial bridge at
the profile baud, sends user init commands, sends the receiver baud-switch
command, reconfigures the host serial bridge to the post-profile baud, records
transitional receiver output through the normal RX path, and only then sends
UM980 mode/log commands. Mode commands are intentionally separate from the baud
switch so post-switch commands are not transmitted at the old baud.

## Plain Rover Recording

Use this when you want a byte-exact receiver log without external corrections.

User flow:

1. Connect the receiver.
2. Select the receiver profile.
3. Start plain rover recording.
4. Leave the phone screen on or off; the foreground recording service owns the
   session.
5. Stop recording and export the session folder.

The session records the raw receiver stream, events, quality logs and the
receiver's in-device solution where available. Raw observations are requested
where the receiver supports them.

## Rover With NTRIP Fed To Receiver

Use this when Android should act as the NTRIP client and feed RTCM corrections
to a capable rover receiver.

User flow:

1. Select the rover receiver profile.
2. Configure NTRIP caster host, port and mountpoint.
3. Store credentials as secret references; passwords are never written to
   `session.json`.
4. Start recording.
5. The app records receiver RX, correction input, TX bytes sent to the receiver,
   receiver solution, quality events and NTRIP state.

The receiver's internal RTK float/fix solution is separate from any future
Android-side solution engine.

## Temporary-Base Preparation

Use this when you want to place a temporary base close to the rover, often with
better sky visibility than a distant permanent base. A stationary car roof in
open sky can be useful if the antenna mount is stable and multipath is
acceptable.

Temporary-base preparation is not a final fixed-base mode. It records the data
needed to produce one or more base-position candidates later.

Required recording behaviour:

- record the raw receiver byte stream unchanged;
- request raw observations at at least `1 Hz` where the receiver supports raw
  observations;
- always record the receiver's normal in-device solution;
- request and record receiver PPP solution separately where the receiver profile
  supports PPP;
- optionally feed CORS/EUREF/NTRIP corrections to the receiver.

Base-position candidate preference:

1. static RTK against a known CORS/EUREF/local base;
2. PPP/static processing;
3. receiver in-device PPP solution;
4. long averaging of non-PPP in-device solution as fallback;
5. receiver survey-in where available and explicitly understood;
6. manual known point or external `base-position.json`.

Long averaging is fallback evidence. It is not equivalent to static RTK or PPP.

## Fixed Base From Accepted Coordinate

Use this only after a base-position candidate has been accepted or an external
known coordinate has been imported.

User flow:

1. Select a receiver profile that supports fixed-base mode.
2. Enter the accepted coordinate manually or paste an imported
   `base-position.json`.
3. Verify frame/datum, epoch, antenna height and antenna reference point.
4. Start fixed-base operation.
5. Record base status and RTCM output/extracted RTCM where supported.

A fixed base must not start directly from a temporary-base recording. The base
coordinate must be accepted first. The V1 UI rejects starts where manual
coordinates and imported `base-position.json` are both supplied.

## Replay/Test

Use replay/test sessions for deterministic parser, quality-monitor and workflow
tests. Replay does not require Android foreground-service or wake-lock semantics
because it is not live hardware recording.

## V2: In-Phone RTKLIB Real-Time

In-phone RTKLIB real-time solution is planned for version 2. V1 keeps the raw
recording and receiver-side workflows clean so RTKLIB can later be added as an
advisory solution engine without blocking capture.

## Key Risks

- Receiver-native raw observations may not convert to RTCM3 without a supported
  receiver-specific decoder.
- `1 Hz` raw observations are a minimum, not a guarantee of precise static
  positioning.
- Receiver PPP may need correction services, subscriptions or convergence time.
- Early or unconverged PPP positions can be misleading.
- Averaging non-PPP standalone fixes can be biased by metres.
- Moving the base closer to the rover improves baseline geometry but does not
  remove the need for a correct absolute base coordinate.
- A wrong base coordinate can produce precise-looking but wrong rover positions.
- Antenna height, antenna reference point, phase-centre assumptions, frame/datum
  and epoch metadata are part of the measurement, not optional notes.
- A car roof can give good open-sky visibility, but roof shape, rails, nearby
  objects and the antenna ground plane can introduce multipath.
