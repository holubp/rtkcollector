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

Verification:
- Review: no V1 feature requires RTKLIB to record, feed NTRIP or prepare a base.

