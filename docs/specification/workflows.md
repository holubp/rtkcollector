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

### WF-ROVER-RTKLIB-001: Rover With NTRIP To RTKLIB

Status: Experimental

Rover + RTKLIB MUST run Android as the NTRIP client when the selected RTKLIB
profile requires live RTCM3 corrections. The correction target MAY be RTKLIB
only. In that case the app MUST record the correction input and feed the RTKLIB
worker, but MUST NOT treat the workflow as NTRIP-to-receiver or transmit RTCM
bytes to a receiver that is only providing raw rover observations.

Verification:
- Automated: workflow classification and active recording configuration tests.
- Manual: u-blox M8T RAWX/SFRBX recording with an EUREF/CORS mountpoint starts
  RTKLIB without sending RTCM corrections to receiver TX.

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

### WF-TEMPBASE-002: Temporary Base Averaging Survives NTRIP Source Changes

Status: Normative

Temporary-base coordinate averaging MUST NOT stop solely because the upstream
NTRIP caster or mountpoint changes. Averaging remains tied to the stationary
local receiver, selected solution and required fix class; if those conditions
remain valid, changing the correction source is an advisory event only.

Verification:
- Automated: coordinate averaging controller tests.
- Manual: temporary-base session can continue averaging after switching between
  two NTRIP mountpoints.

### WF-TEMPBASE-HEIGHT-001: Temporary Base Candidate Preserves Height Semantics

Status: Normative

Temporary-base coordinate actions that prepare fixed-base operation MUST carry
the available height semantics explicitly. Ellipsoidal height MUST remain
available for dashboard and mock-location use when reported by the receiver.
UM980/N4 fixed-base command generation MUST use accepted MSL altitude and MUST
NOT silently substitute ellipsoidal height for the `MODE BASE` altitude field.

Verification:
- Automated: dashboard base-coordinate candidate tests.
- Review: UM980 `MODE BASE` generation uses MSL altitude only.

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

### WF-FIXEDBASE-003: Fixed Base Profile Must Match Accepted UM980 Coordinate

Status: Normative

Fixed-base command-profile materialisation and fixed-base start preflight MUST
only use UM980/Unicore command profiles. The selected command profile MUST
contain a visible `MODE BASE <lat> <lon> <msl-altitude>` line whose latitude,
longitude and MSL altitude match the selected accepted base coordinate within
tight command-format tolerances. Survey-style `MODE BASE TIME ...` does NOT
satisfy this requirement.

Verification:
- Automated: fixed-base command validator tests.
- Review: fixed-base start preflight rejects non-UM980 profiles, `MODE BASE
  TIME`, and accepted-coordinate drift from the visible command profile.

### WF-FIXEDBASE-004: Fixed Base Command Materialisation Is Explicit

Status: Normative

Fixed-base coordinate acceptance MUST NOT silently replace or inject `MODE BASE`
commands at recording start. The user MUST first choose whether to use the
current coordinate or an available averaged coordinate, then choose a fixed-base
settings set whose effective command profile already contains `MODE BASE`.
The chosen settings set is the target or template for fixed-base operation; the
temporary-base settings set used to determine the coordinate MUST NOT be assumed
as the fixed-base output profile. If the chosen settings set or its command
profile is immutable, the app MUST derive a new timestamped settings set and,
where needed, a new command profile. If both are editable, the app MAY update
the selected settings set in place. In all cases, only the `MODE BASE` line is
replaced with `MODE BASE <lat> <lon> <msl-altitude>`; all other command lines,
including RTCM output lines, MUST remain unchanged. The final confirmation MUST
describe whether the app will derive a new set or update in place. Cancel before
final confirmation MUST leave settings sets and command profiles unchanged.

Verification:
- Automated: fixed-base profile selection, handoff planner, materialiser and
  command validator tests.
- Review: dashboard Base action opens coordinate choice, fixed-base settings
  set choice and final confirmation; start preflight uses the selected command
  profile as-is.

### WF-PROFILE-FILTER-001: Device Filter Scopes Profile Selection

Status: Normative

The app MUST provide a persistent profile device filter with at least `Any`,
`UM980` and `u-blox M8T`. The filter MUST apply to settings-set selectors,
init/shutdown profile selectors and device-console init-profile selection.
The selected active profile MAY remain visible outside the filter so users can
understand the current state. `Any` MUST preserve broad power-user behaviour.

Verification:
- Automated: profile device filter and profile-list row tests.
- Review: Home and Menu Device selector changes visible profile choices without
  deleting current selections.

### WF-UPLOAD-UI-001: Upload Selector Is Base-Workflow Scoped

Status: Normative

The main dashboard Upload selector MUST be available for workflows that can act
as a base and publish RTCM. Rover-only workflows MUST NOT show missing upload as
an error state; the Upload tile SHOULD be disabled or marked not needed.

Verification:
- Automated: dashboard status tests.
- Review: plain-rover and rover-with-NTRIP dashboard show upload as not needed,
  while fixed-base and temporary-base workflows allow explicit upload selection.

### WF-BUILTIN-SETTINGS-001: Built-In Rover Settings Require Explicit NTRIP Selection

Status: Normative

Built-in rover and base settings sets MUST NOT enable NTRIP source upload by
default. Every built-in rover settings set MUST have no NTRIP source-upload
profile selected. A settings set MAY reference an NTRIP correction-download
caster where the workflow uses corrections, but the built-in `UM980 rover +
NTRIP` settings set MUST NOT contain a fixed correction mountpoint. Selecting or
loading that built-in MUST NOT silently materialise the last active mountpoint;
the user MUST explicitly select or type a mountpoint before Start.

An explicit selection MAY be represented as a visible local override on the
built-in settings set. Users MAY copy the built-in to create an editable named
settings set with a predefined mountpoint. User-created copies MUST NOT be reset
to built-in defaults during migration.

Verification:
- Automated: built-in settings-set and profile-store migration tests.
- Manual: selecting `UM980 rover + NTRIP` shows Mountpoint unresolved and Upload
  as `Off` until the user explicitly chooses a mountpoint.

## Future In-Phone Solution

### WF-SOLUTION-001: Device And RTKLIB Solutions Stay Separately Selectable

Status: Normative

When both receiver-internal and RTKLIB solution sources are available, the app
MUST keep them distinguishable in session artifacts, dashboard monitoring and
mock-location source selection. Automatic best-solution selection MAY be the
default, but users MUST be able to select device-only, RTKLIB-only or disabled
solution output policies where the relevant engine exists.

Verification:
- Automated: best-solution selector and recording tick-policy tests.
- Review: session metadata records screen and mock-location source policy.

### WF-RTKLIB-001: In-Phone RTKLIB Is Future Work

Status: Future

In-phone RTKLIB real-time solution is not required for V1. When implemented, it
MUST remain a separate solution engine from receiver-internal solution and MUST
NOT become part of the raw capture path.

RTKLIB-EX integration MUST use the library API through an in-process native
adapter, not RTKLIB command-line tools. Live processing MUST be forward-only.
Forward/backward or combined processing belongs to offline/post-processing.
The RTKLIB-EX source used by Android builds MUST be pinned to an exact
reviewed upstream commit; automatic tracking of upstream HEAD, tags or moving
branches is not acceptable for reproducible field builds.

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

Live RTKLIB real-time processing MUST preserve RTKLIB's rover/base observation
synchronisation semantics. The preferred implementation is RTKLIB's `rtksvr_t`
server using app-fed in-memory streams. A custom native loop MAY be used only
if automated replay tests prove it combines rover observations, base RTCM
observations, ephemeris, base position and correction age equivalently to
RTKLIB's server loop. A native loop MUST NOT solve each rover epoch against
only the most recently decoded single RTCM observation message.

RTKLIB processing MUST remain lazy and advisory. If RTKLIB is disabled by the
validated workflow/profile, no RTKLIB native library may be loaded, no RTKLIB
worker may be started and no RTKLIB memory buffers may be allocated.

Verification:
- Review: no V1 feature requires RTKLIB to record, feed NTRIP or prepare a base.
- Automated: RTKLIB input route tests cover direct u-blox, direct Unicore,
  named converter/shim Unicore compact observations and RTCM3 corrections.
