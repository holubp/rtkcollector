# Receiver Behaviour Requirements

## UM980 / Unicore N4

### RX-UM980-001: UM980 Mixed Stream Parser Is Advisory

Status: Normative

UM980 parsing MUST tolerate mixed NMEA, UM980 ASCII, UM980 binary and RTCM-like
bytes without feeding arbitrary binary data into line-only parsers.

Verification:
- Automated: UM980 mixed-stream parser tests.
- Manual: binary UM980 session updates dashboard while `receiver-rx.raw`
  remains byte-exact.

### RX-UM980-RTK-001: UM980 RTK Status Sources

Status: Normative

UM980 in-device RTK monitoring SHOULD use BESTNAV/ADRNAV, RTKSTATUS and
RTCMSTATUS when available. RTK state MUST remain separate from PPP status and
future RTKLIB status.

Verification:
- Automated: BESTNAV/ADRNAV/RTKSTATUS/RTCMSTATUS parser tests.
- Manual: rover + NTRIP session shows RTK float/fix and decoded RTCM state.

### RX-UM980-PPP-001: UM980 PPP Status Requires Explicit PPP Telemetry

Status: Normative

The dashboard MUST NOT infer PPP convergence from generic position updates.
PPP status MUST come from explicit PPP telemetry such as PPPNAV when available,
or show that PPP telemetry is unavailable.

Verification:
- Automated: PPPNAV parser and dashboard mapping tests.
- Manual: PPP-enabled UM980 recording distinguishes no PPP telemetry, no PPP
  solution, converging and converged states.

### RX-UM980-PROFILES-001: Built-In UM980 Profiles Are Protected Defaults

Status: Normative

App-distributed UM980 command profiles MUST be protected built-ins. Users MAY
copy built-ins to create editable variants, but app updates SHOULD resynchronise
same-id or known legacy same-name built-ins to current defaults.

Verification:
- Automated: profile store migration and protected-profile tests.
- Review: built-in profiles are not directly editable in profile screens.

UM980 built-in command profiles that enable BESTNAV/NMEA monitoring telemetry
MUST declare their satellite telemetry capability explicitly. Binary UM980
profiles SHOULD declare UM980 binary telemetry; ASCII/NMEA-oriented UM980
profiles SHOULD declare UM980 NMEA telemetry.

Verification:
- Automated: built-in command-profile default tests.

### RX-UM980-RTKLIB-001: UM980 RTKLIB Input Routing Is Message-Specific

Status: Implemented, not field-tested

UM980 RTKLIB routing MUST distinguish direct RTKLIB-EX compatible observation
messages from compact or app-specific observation messages. OBSVMB MAY be
treated as a direct RTKLIB-EX route when the selected receiver profile emits it
and the RTKLIB-EX capability model declares `input_unicore` support. OBSVMCMPB
MUST require a named converter or explicit RTKLIB-EX decoder support before it
is accepted as RTKLIB input. The current Android native build declares the
named `rtkcollector-obsvmcmp-shim`, which patches the pinned RTKLIB-EX Unicore
decoder to accept message id 138 through the OBSVMB decoder path. Advisory
satellite-monitor parsing for `OBSVMB`, `OBSVMCMPB` and `BESTSATB` is
implemented, but solution validation remains field/replay-sensitive until a
debug capture with actual `OBSVMCMPB` observation frames is available.

Verification:
- Automated: RTKLIB input route tests for OBSVMB direct and named OBSVMCMPB
  shim cases; sample-readiness tooling reports whether observation frames are
  actually present.
- Review: UM980 command profiles selected for RTKLIB live processing emit the
  format declared by the workflow route plan.

## u-blox

### RX-UBLOX-M8T-001: M8T Is Raw/Timing/Post-Processing Receiver

Status: Normative

u-blox M8T support MUST treat M8T as a raw/timing/post-processing receiver, not
as an internal RTK float/fix rover like M8P.
App-distributed M8T command profiles MUST be protected built-ins and MUST
declare u-blox NAV-SAT satellite telemetry when their command set enables the
status messages needed by monitoring.

Verification:
- Automated: receiver capability tests.
- Manual: M8T profile records UBX RAWX/SFRBX/TIM where configured.

### RX-UBLOX-RTKLIB-001: u-blox Raw Input Is First-Class RTKLIB Input

Status: Implemented, not field-tested

u-blox profiles that emit UBX RXM-RAWX and RXM-SFRBX MUST be eligible for a
direct RTKLIB-EX route through the UBX decoder. The route model MUST preserve
u-blox-specific raw data instead of forcing it through a generic intermediate
observation model unless a future validation shows a direct route is unsafe.
u-blox M8T and M8P profiles MUST be treated as single-frequency RTKLIB inputs
unless a future receiver-specific profile declares otherwise. RTKLIB runtime
configuration MUST pass this frequency count explicitly instead of relying on
RTKLIB's default dual-frequency processing option.
The advisory monitor parses `RXM-RAWX` rover visibility and `NAV-SAT`
selected-satellite usage when the active built-in profile declares u-blox
telemetry support. Fresh M8T/M8P replay or hardware validation remains open.

Verification:
- Automated: RTKLIB input route tests for u-blox RAWX/SFRBX direct routing.
- Manual: M8T/M8P replay sessions produce RTKLIB observation epochs without
  affecting receiver raw recording.
