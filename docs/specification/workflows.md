# Workflow Requirements

## Rover Workflows

### WF-ROVER-001: Plain Rover Recording

Status: Normative

Plain rover recording MUST use receiver role `ROVER`, no correction source and
no correction targets. It SHOULD request raw observations where the selected
receiver supports them.

Verification:
- Automated: workflow validator tests.
- Manual: start plain rover without NTRIP and confirm no NTRIP connection opens.

### WF-ROVER-NTRIP-001: Rover With NTRIP To Receiver

Status: Normative

Rover + NTRIP MUST run Android as the NTRIP client, receive RTCM correction
bytes, record correction input, transmit correction bytes unchanged toward the
receiver and keep receiver-internal solution separate from future in-phone
solutions.

Verification:
- Automated: NTRIP client/request/routing tests.
- Manual: EUREF/CORS mountpoint session reaches receiver RTK float/fix.

## Base Workflows

### WF-TEMPBASE-001: Temporary Base Is Coordinate Determination

Status: Normative

Temporary-base recording is a stationary coordinate-determination workflow. It
MAY determine coordinates from RTK against another base, PPP, static
post-processing or fallback averaging. It MUST NOT be treated as equivalent to
fixed-base operation until a coordinate is accepted.

Verification:
- Automated: workflow validator and base-coordinate state tests.
- Manual: temporary-base session can accept an averaged/current coordinate and
  transition toward fixed-base use.

### WF-TEMPBASE-HEIGHT-001: Temporary Base Candidate Uses Ellipsoidal Height

Status: Normative

Temporary-base coordinate actions that prepare fixed-base operation MUST carry
ellipsoidal height. Orthometric altitude or MSL height MUST NOT be substituted
for receiver fixed-base commands.

Verification:
- Automated: dashboard base-coordinate candidate tests.
- Review: UM980 `MODE BASE` generation uses ellipsoidal height only.

### WF-FIXEDBASE-001: Fixed Base Requires Accepted Coordinate

Status: Normative

Fixed-base operation MUST require an accepted base coordinate from manual entry,
imported base-position file or accepted temporary-base result. It MUST NOT start
from an unaccepted temporary-base session.

Verification:
- Automated: workflow validator and start-preflight tests.
- Manual: fixed-base UI shows the exact coordinate before start.

### WF-FIXEDBASE-002: Fixed Base Must Not Run Rover Mode Commands

Status: Normative

Fixed-base operation MUST NOT start with a receiver command profile that later
sets the receiver to rover mode.

Verification:
- Automated: active recording configuration validation tests.
- Review: generated fixed-base command injection cannot be followed by
  `MODE ROVER`.

## Future In-Phone Solution

### WF-RTKLIB-001: In-Phone RTKLIB Is Future Work

Status: Future

In-phone RTKLIB real-time solution is not required for V1. When implemented, it
MUST remain a separate solution engine from receiver-internal solution and MUST
NOT become part of the raw capture path.

RTKLIB-EX integration MUST use the library API through an in-process native
adapter, not RTKLIB command-line tools. Live processing MUST be forward-only.
Forward/backward or combined processing belongs to offline/post-processing.

RTKLIB input routing MUST be explicit and format-aware. Receiver profiles MUST
declare candidate RTKLIB rover-input formats, and the RTKLIB engine capability
model MUST declare which formats can be consumed directly. Direct RTKLIB-EX
routes SHOULD be used before converter routes when the direct route preserves
the receiver data. Converter routes are allowed only when named explicitly.

Initial V2 route expectations:

- u-blox RAWX/SFRBX SHOULD route directly to RTKLIB-EX `input_ubx` when the
  selected profile emits those messages.
- Unicore/UM980 OBSVMB SHOULD route directly to RTKLIB-EX `input_unicore` when
  the selected profile emits OBSVMB.
- Unicore/UM980 OBSVMCMPB MUST NOT be treated as direct RTKLIB-compatible input
  unless the RTKLIB-EX capability model explicitly declares direct support;
  otherwise it requires a named converter or a focused decoder update.
- RTCM3 correction/base-observation streams SHOULD route directly to
  RTKLIB-EX `input_rtcm3`.
- NMEA, BESTNAV, ADRNAV, PPPNAV and NAV-PVT are solution/monitoring data, not
  RTKLIB raw-observation input.

Verification:
- Review: no V1 feature requires RTKLIB to record, feed NTRIP or prepare a base.
- Automated: RTKLIB input route tests cover direct u-blox, direct Unicore,
  converter-required Unicore compact observations and RTCM3 corrections.
