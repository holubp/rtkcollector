# RtkCollector Experimental E2E V1 Design

## Scope

Experimental E2E V1 turns the current workflow dry-run app into a small real recording vertical slice:

- Android owns USB permission, serial RX/TX, recording, and stop control.
- UM980/N4 is the first real receiver path.
- NTRIP client can receive RTCM3 bytes and enqueue them to receiver TX.
- All receiver RX bytes are written byte-exactly to `receiver-rx.raw`.
- All app-sent receiver bytes, including init, shutdown, and injected RTCM corrections, are written to `tx-to-receiver.raw`.
- NTRIP input bytes are written separately to `correction-input.raw`.
- The UI remains operational and map-free: select workflow/profile, edit init/shutdown scripts, start, observe counters/state, stop.

V1 does not implement RTKLIB real-time, Android PPP/static solving, NTRIP caster/server, GIS/maps/shapefiles, or full receiver-native decoding.

## Evidence From UM980 Pipeline

The adjacent `../um980-rtklib-pipeline` project provides the hardware-proven behavior that V1 should preserve:

- Rootless Termux capture used Android USB permission plus an authorised USB file descriptor, then claimed the FTDI-style bulk endpoints.
- Tested adapter was FTDI-style `VID 0x0403 / PID 0x6015`; incoming USB packets included a two-byte FTDI status header per packet that must not be written into receiver raw capture.
- FTDI baud configuration required fractional divisors; 230400, 460800, and 921600 worked in tested capture runs, while 1843200 and 3686400 did not take effect through `CONFIG COM1`.
- Sending a receiver profile can require a `profileBaud` first, then changing host serial baud to `serialBaud` after `CONFIG COM1 <baud>`.
- After sending a profile, the old pipeline drained/discarded transitional receiver output before opening the authoritative capture file.
- Runtime profiles were line-oriented ASCII commands, disabled unless reviewed, and rejected persistent/risky commands such as `SAVECONFIG`, `RESET`, `FRESET`, `SAVE`, `FLASH`, `NVM`, and shell metacharacters.
- Reviewed UM980 rover capture patterns use `UNLOG COM1`, `MODE ROVER`, `CONFIG MMP ENABLE`, `BESTNAVB`, `OBSVMCMPB`, binary ephemeris messages, and optional ION/UTC diagnostics.

The Unicore N4 command manual confirms the command families used here: ASCII command input, `CONFIG COMx`, `MODE ROVER`, fixed `MODE BASE`, RTCM auto-recognition on rover input, periodic NMEA/output commands, `UNLOG`, `SAVECONFIG`, and RTCM base-output command families.

## Architecture

V1 adds these runtime components:

1. `core:transport`
   - Pure contracts for serial transports.
   - Android implementation in `app` for USB Host API, initially focused on FTDI-style USB serial bridges.

2. `core:capture`
   - Capture orchestrator that reads serial RX on a background coroutine/thread and writes bytes before advisory parsing.
   - TX queue that records bytes before sending them to the receiver.
   - Capture observes NTRIP state but never blocks on NTRIP or parsers.

3. `core:session`
   - Session directory creation and append-only writers for `session.json`, `receiver-rx.raw`, `tx-to-receiver.raw`, `correction-input.raw`, `events.jsonl`, and `quality-live.jsonl`.
   - Session export metadata redacts secrets and records secret references only.

4. `core:correction`
   - Minimal NTRIP v1 client: TCP socket, GET request, Basic Auth from runtime password only, optional GGA upload, RTCM byte callback, reconnect loop, and state snapshots.
   - No NTRIP caster/server.

5. `receiver:unicore-n4`
   - Runtime profile validation and UM980 profile templates.
   - No persistent commands by default.

6. `app`
   - Minimal foreground service owns real recording.
   - Activity configures workflow/profile/NTRIP/session parameters and observes service state.
   - UI can send init/profile commands, run recording, show counters, and stop with optional shutdown commands.

## Profile Model

Each session combines:

- user init sequence;
- selected workflow mode sequence;
- optional user shutdown sequence.

The startup command stream is `initSequence + modeSequence`. Shutdown is separate and sent only during stop while transport is available.

For UM980 V1, built-in mode templates are runtime-only examples. They may include `CONFIG COM1 <baud>` and `UNLOG COM1`, but they must not include persistent saves or resets. User-edited commands are allowed only after validation and UI display.

## UM980 V1 Defaults

Default experimental rover/base-preparation profile:

```text
UNLOG COM1
MODE ROVER
CONFIG MMP ENABLE
VERSIONB
BESTNAVB COM1 0.2
OBSVMCMPB COM1 1
GPSEPHB COM1 300
GLOEPHB COM1 300
GALEPHB COM1 300
BDSEPHB COM1 300
BD3EPHB COM1 300
QZSSEPHB COM1 300
GPSIONB ONCHANGED
BDSIONB ONCHANGED
BD3IONB ONCHANGED
GALIONB ONCHANGED
GPSUTCB ONCHANGED
BDSUTCB ONCHANGED
BD3UTCB ONCHANGED
GALUTCB ONCHANGED
```

The raw observation period is `1`, meaning at least 1 Hz. Users may raise OBSVMCMPB/BESTNAV rates after choosing an appropriate baud and accepting bandwidth warnings.

Default fixed-base template requires an accepted base position and should remain explicitly user-confirmed before use.

## Safety Rules

- Raw RX capture is authoritative and must not include injected timestamps, app markers, or FTDI status bytes.
- TX bytes are recorded before send.
- NTRIP input bytes are recorded before receiver injection.
- Parser errors update quality/events only.
- NTRIP reconnect cannot stop RX capture.
- UI lifecycle cannot own recording.
- No secrets in `session.json`; only redacted metadata and secret reference names.
- V1 warns that Android cannot guarantee survival after force-stop, USB power loss, phone shutdown, or vendor task killing.

## Test Strategy

- Pure JVM tests for profile validation, command rendering, NTRIP request creation/redaction, session metadata redaction, and capture orchestration using fake transports.
- Android compile for USB/service/UI integration.
- Manual hardware test protocol for UM980/FTDI: passive capture, profile capture at 230400, optional 460800/921600 capture, NTRIP injection, stop/shutdown.
