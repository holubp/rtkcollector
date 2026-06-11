# UM980 RTK Monitoring And Editor Input Design

Date: 2026-06-11

## Purpose

This spec tightens RtkCollector V1 behaviour for UM980 rover operation with
external NTRIP corrections. The app remains the NTRIP client and correction
router. The UM980 receives raw RTCM3 bytes on its serial input and computes any
in-device RTK/PPP solution.

The design addresses three observed gaps:

- receiver RTK status is not shown separately from PPP or future RTKLIB;
- PPP convergence can be inferred from generic BESTNAV output even when no
  explicit PPP status log is present;
- multiline profile editors can mishandle hardware-keyboard arrow navigation.

It also defines how persistent receiver configuration writes are exposed without
placing `SAVECONFIG` in normal recording startup scripts.

## Scope

In scope:

- UM980/N4 binary and ASCII monitoring profile updates for COM1;
- parser/data-model support for RTK diagnostic logs;
- dashboard display model changes for in-device RTK status;
- PPP status display rules;
- command-profile editor action for explicit persistent writes;
- local keyboard behaviour fixes for profile text fields.

Out of scope:

- Android-side RTKLIB real-time solution;
- Android-side PPP/static solver;
- UM980 internal NTRIP-client configuration;
- NTRIP caster/server upload;
- maps, shapefiles, GIS editing or feature collection.

## Receiver And Port Assumptions

For V1 UM980 USB operation, `COM1` is the default monitored and corrected port.
RtkCollector writes NTRIP RTCM bytes unchanged to the same USB serial stream used
for receiver commands and receiver output.

The app must not wrap RTCM bytes in any Unicore binary protocol. RTCM remains a
raw correction stream delivered to the receiver serial input.

The app must not use UM980 internal NTRIP-client commands. The UM980/N4 command
manual evidence available to this project indicates the documented
`CONFIG NCOM20 ... NTRIP-client` command is for another receiver family, not the
normal UM980 V1 path.

## Default UM980 Rover Monitoring Profile

The COM1 binary rover profile should request a receiver-internal RTK-capable
monitoring set:

```text
UNLOG COM1
MODE ROVER SURVEY
CONFIG MMP ENABLE
CONFIG RTK TIMEOUT 120
CONFIG RTK RELIABILITY 3 1
CONFIG PPP ENABLE E6-HAS
CONFIG PPP DATUM WGS84
CONFIG PPP TIMEOUT 120
CONFIG PPP CONVERGE 15 30

VERSIONB
BESTNAVB COM1 0.05
ADRNAVB COM1 1
PPPNAVB COM1 1
RTKSTATUSB COM1 1
RTCMSTATUSB COM1 ONCHANGED
OBSVMCMPB COM1 0.25
STADOPB COM1 1
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

`SAVECONFIG` must not be part of this normal runtime script.

The ASCII profile may keep `BESTNAVA`, `PPPNAVA`, `ADRNAVA`, `GGA`, `GST`, `GSA`
and `GSV` style logs for field debugging, but the binary profile is the main V1
target for high-rate recording.

## Binary Logs To Parse

The UM980 mixed-stream parser already treats binary frames as byte records with
sync `AA 44 B5`, 24-byte header, payload length and CRC. New RTK monitoring
parsers must stay inside this frame parser and must not feed arbitrary binary
bytes to line-oriented parsers.

Required binary logs:

| Log | Message ID | Purpose |
| --- | ---: | --- |
| `BESTNAVB` | 2118 | final receiver navigation solution and app position |
| `ADRNAVB` | 142 | carrier-phase RTK solution view |
| `PPPNAVB` | 1026 | explicit receiver PPP status |
| `RTKSTATUSB` | 509 | RTK engine diagnostic state |
| `RTCMSTATUSB` | 2125 | receiver-decoded RTCM message status |
| `STADOPB` | 954 | DOP and tracked-satellite diagnostics |

`ADRNAVB` uses the same common navigation payload offsets already used for
`BESTNAVB`/`PPPNAVB`:

- solution status;
- position type;
- latitude, longitude and height;
- latitude, longitude and height standard deviations;
- base station ID;
- differential age;
- solution age;
- satellites tracked and used;
- velocity solution status/type when present.

`RTKSTATUSB` must expose at least:

- correction-source bitmaps where decoded;
- position type;
- calculate status;
- ionosphere detected flag;
- ADR number.

`RTCMSTATUSB` must expose at least:

- RTCM message ID;
- message count;
- base ID;
- satellite count;
- observable counts L1 through L6.

## RTK State Model

Add a receiver-internal RTK status distinct from main fix, PPP and RTKLIB.

User-facing states:

- `No RTCM`
- `RTCM decoded`
- `RTK float`
- `RTK fixed`
- `RTK stale`
- `Base obs insufficient`
- `Rover obs insufficient`
- `n/a`

Inputs, in priority order:

1. `ADRNAV[A/B]` position type and solution status for carrier-phase RTK view.
2. `BESTNAV[A/B]` position type and solution status as fallback.
3. `RTKSTATUS[A/B]` calculate status.
4. Recent `RTCMSTATUS[A/B]` presence.
5. Differential age from `ADRNAV`/`BESTNAV`.
6. GGA quality only as a fallback for ASCII/NMEA-only monitoring.

Classification rules:

- `NARROW_INT` or `INS_RTKFIXED` with computed solution means `RTK fixed`.
- `NARROW_FLOAT`, `IONOFREE_FLOAT`, `L1_FLOAT` or `INS_RTKFLOAT` with computed
  solution means `RTK float`.
- calculate status `0` means `No RTCM`.
- calculate status `1` means `Base obs insufficient`.
- calculate status `2`, or differential age above the configured stale
  threshold, means `RTK stale`.
- calculate status `4` means `Rover obs insufficient`.
- calculate status `5` with recent RTCM but no float/fixed position type means
  `RTCM decoded`.
- recent `RTCMSTATUS` without float/fixed position means `RTCM decoded`.
- absence of RTK/RTCM diagnostics means `n/a`, not an error.

The dashboard should show:

- main fix type as the major value;
- `PPP` status from explicit PPP logs only;
- `RTK` in-device status from the state model above;
- `RTKLIB` as `Not configured` in V1.

## PPP Status Semantics

The PPP row must only be updated from explicit PPP reporting logs:

- `PPPNAVB`;
- `PPPNAVA`;
- later documented PPP-specific logs if added.

`BESTNAV[A/B]` may still report a final receiver solution position type of
`PPP_CONVERGING` or `PPP`, but that should not by itself populate the PPP status
row. Without explicit PPP status logs, PPP status remains `n/a`.

Main fix display remains separate:

- RTK fixed/float from BESTNAV/ADRNAV/GGA should display as fix state;
- converged PPP may display as a main fix when BESTNAV reports `PPP` and it is
  not superseded by RTK;
- PPP convergence from BESTNAV can be displayed as receiver solution state only
  when there is no better GGA/RTK-derived main fix, but it must not imply PPP
  status telemetry was received.

## Persistent Receiver Configuration Action

Normal recording start must not persist receiver settings.

The command-profile editor may expose a separate button:

`Write init config persistently to device`

This action:

1. Requires an active selected USB receiver and baud profile.
2. Sends the current init script.
3. Sends `SAVECONFIG`.
4. Reports success or failure as a profile-editor event.

Before sending anything, the app must show a modal warning. The warning must
state that this writes to receiver non-volatile memory and can affect other
apps, tools and future receiver sessions until the receiver is manually
reconfigured.

The modal choices are:

- `Write persistently`;
- `Cancel`.

No normal workflow, recording start or shutdown action may silently append
`SAVECONFIG`.

## Keyboard Editing Behaviour

Profile editor text fields, especially multiline init and shutdown scripts, must
behave like normal text editors for hardware keyboards:

- arrow keys move the caret inside the current field;
- `Ctrl+Left` and `Ctrl+Right` move by words where Compose/platform support
  allows;
- `Ctrl+A`, `Ctrl+C`, `Ctrl+V` and `Ctrl+X` keep standard text-editing meaning;
- `Tab` moves to the next field;
- `Shift+Tab` moves to the previous field;
- arrow keys must not move focus between fields while a text field is focused.

The fix should be local to reusable profile-editor text-field components. It
must not require a broad Compose navigation rewrite.

## Data Flow And Safety

All new RTK and PPP parser outputs are advisory. Parser failure must not stop or
mutate byte-exact raw recording.

Recording artifacts remain separate:

- receiver RX stays in `receiver-rx.raw`;
- app commands and NTRIP correction bytes sent to receiver stay in
  `tx-to-receiver.raw`;
- correction input bytes stay in `correction-input.raw`;
- parsed RTK/PPP/RTCM status goes to JSONL event or quality sidecars.

## Tests

Required focused tests:

- `ADRNAVB` binary frame parses with the same common nav fields as `BESTNAVB`;
- `RTKSTATUSB` calculate status maps to the expected user-facing RTK state;
- `RTCMSTATUSB` message/base/satellite fields parse correctly;
- PPP row is not populated from BESTNAV-only `PPP_CONVERGING`;
- PPP row is populated from explicit `PPPNAVB`/`PPPNAVA`;
- dashboard fix card includes separate PPP, RTK and RTKLIB rows;
- default UM980 binary script includes `ADRNAVB`, `RTKSTATUSB`,
  `RTCMSTATUSB COM1 ONCHANGED` and does not include `SAVECONFIG`;
- editor text-field model or Compose test coverage verifies arrow keys do not
  trigger field-to-field focus movement where testable.

## Acceptance Criteria

- In UM980 binary rover + NTRIP mode, the dashboard can show receiver-internal
  RTK state independently of PPP and RTKLIB.
- If only BESTNAV reports `PPP_CONVERGING`, the PPP row remains `n/a`.
- If PPPNAV reports `PPP_CONVERGING`, the PPP row shows it.
- If RTCM is decoded but RTK is not available, the UI can distinguish transport
  success from RTK-engine insufficiency.
- Normal recording start never persists receiver configuration.
- Persistent receiver config write is available only through an explicit editor
  action with a warning.
- Hardware keyboard arrow navigation works inside profile script text fields.
