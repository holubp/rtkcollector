# Profiled Recording Fixes Design

## Purpose

This design covers the next V1 field-fix batch after real-device testing of the
experimental UM980 recording flow. The goal is to make recording repeatable and
robust enough for Windows-built APK field testing:

- NTRIP authentication must work with modern casters that expect NTRIP v2 style
  requests, while retaining controlled compatibility with older caster
  behaviour.
- Init/shutdown scripts, USB/baud choices, NTRIP caster settings, mountpoint
  settings, storage location and artifact selection must become reusable
  profiles instead of ad-hoc form state.
- Recording must continue in the foreground service while the Activity is
  minimised, destroyed/recreated or the screen is off.
- Session artifacts must remain recoverable after an app crash or unclean stop.
- The reported stop-time `InterruptedException` from NTRIP reconnect delay must
  be handled as intentional cancellation, not as an app error.

This batch remains UM980-focused for live recording. It does not add maps,
shapefiles, GIS editing, RTKLIB real-time, PPP/static solving on Android, or an
NTRIP caster/server.

## UI Direction

RtkCollector gains a first-class profile manager. The recording screen consumes
selected profile references rather than requiring the user to retype the full
configuration every time.

Profile manager sections:

- Command Profiles
- USB / Baud Profiles
- NTRIP Caster Profiles
- NTRIP Mountpoint Profiles
- Recording Policies
- Storage Locations
- Workflow Defaults

The recording screen remains the operational surface:

- choose workflow/scenario;
- choose receiver family/profile;
- choose command profile;
- choose USB/baud profile;
- choose NTRIP mountpoint profile only when the workflow uses NTRIP;
- choose storage location profile;
- choose recording policy;
- start and stop recording;
- observe live counters and states.

During recording, the UI shows at least receiver RX bytes, TX-to-receiver bytes,
NTRIP correction bytes, NTRIP state/status, parsed fix state, PPP status where
available, and artifact availability. The Activity is only a controller and
observer. It must not own recording state.

## Profile Model

### Command Profile

A command profile stores user-managed receiver scripts:

- stable ID;
- display name;
- receiver family, initially UM980/N4;
- init script;
- shutdown script;
- optional default workflow IDs;
- created/updated timestamps.

Command profiles are validated before start using the UM980 runtime command
validator. Shutdown scripts are usually empty but remain explicit.

Generated workflow mode/log commands remain separate from the user command
profile. A UM980 baud switch remains a separate generated command phase, not a
hidden part of post-switch mode/log commands.

### USB / Baud Profile

A USB/baud profile stores connection setup:

- stable ID;
- display name;
- profile baud;
- runtime serial baud;
- optional remembered USB device identity, such as VID, PID, product name,
  device name and serial number when Android exposes it;
- created/updated timestamps.

The remembered USB identity is a convenience filter. Recording start must still
validate that the selected Android USB device exists and permission is granted.

### NTRIP Caster Profile

An NTRIP caster profile stores shared caster/server settings:

- stable ID;
- display name;
- host;
- port;
- protocol policy: NTRIP v2 preferred with compatibility fallback;
- username;
- Keystore-backed password secret reference;
- last sourcetable fetch metadata and cache timestamp;
- created/updated timestamps.

The caster editor includes an explicit temporary show-password control. Showing
the password is a UI action only; plaintext passwords must never be written to
session metadata, logs, docs, test fixtures, profile exports, events or memory.

### NTRIP Mountpoint Profile

An NTRIP mountpoint profile references a caster profile and adds:

- stable ID;
- display name;
- caster profile ID;
- mountpoint, either typed directly or selected from fetched sourcetable;
- GGA upload policy;
- expected correction format, initially RTCM3;
- optional station/base metadata;
- optional approximate base position;
- whether remote-base raw observations are practically available from this
  source;
- created/updated timestamps.

Typed mountpoints remain valid even if sourcetable fetch fails. Sourcetable
fetch is on demand and may use a short-lived local cache for field convenience.

### Recording Policy

A recording policy specifies requested artifacts. It is workflow-constrained:
impossible choices are disabled or rejected before start.

Core artifacts:

- device receiver RX stream: required and authoritative;
- TX-to-receiver stream: recorded whenever the app sends commands or
  corrections;
- NTRIP correction input stream: available only for NTRIP workflows;
- derived device solution exports: sidecars derived from device RX;
- optional remote-base raw observations: only when enabled by the user and
  practically available from the selected source;
- quality, events and session metadata.

For base workflows, saving base data equals saving device data because the
device is the base. For rover workflows, remote-base data is additional and must
not be conflated with the rover device RX stream.

Derived device solution exports are advisory sidecars. V1 must implement
functional NMEA export. Parsed JSONL remains available for live status and
downstream inspection. GPX may be produced when enough time/position fields are
available. RTKLIB `.pos` export is explicitly out of V1 scope.

### Storage Profile

A storage profile specifies where sessions are written:

- app-private external storage default;
- or a user-selected Android Storage Access Framework folder URI;
- display name;
- created/updated timestamps.

If a selected SAF folder is unavailable or persisted permission is missing,
recording start is blocked with a clear error. The app may offer explicit
fallback to app-private external storage, but must not silently switch storage
after the user selected a location.

### Workflow Defaults

Workflow defaults map a workflow/scenario to preferred profiles:

- command profile ID;
- USB/baud profile ID;
- NTRIP mountpoint profile ID where applicable;
- recording policy ID;
- storage profile ID.

Defaults are conveniences. The user can override them before start.

## NTRIP Behaviour

The NTRIP client must be v2 preferred:

- render `GET /mountpoint HTTP/1.1`;
- include `Host`;
- include `User-Agent`;
- include `Ntrip-Version: Ntrip/2.0`;
- include Basic authentication when credentials are configured;
- use `Connection: close`;
- support optional GGA upload after the header where configured.

Compatibility fallback is controlled:

- if a v2 request fails in a way known to indicate caster incompatibility, the
  client may retry using the compatibility request style;
- authentication failures such as `401 Unauthorized` or `403 Forbidden` are
  reported clearly and should not be hidden by blind fallback loops;
- the negotiated/attempted protocol and final status are recorded in redacted
  session metadata/events.

The client must classify common responses:

- `ICY 200` and HTTP `200` start streaming;
- `401 Unauthorized` and `403 Forbidden` are authentication/authorization
  failures;
- `SOURCETABLE` is not an RTCM stream;
- timeout/closed stream/reconnect wait are separate states.

NTRIP must only start when the workflow and recording policy use NTRIP. Plain
rover, temporary base without NTRIP, fixed-base status, replay and other
non-NTRIP workflows must not create a caster connection even when NTRIP profiles
exist.

## Background Recording Requirements

The foreground service owns recording. Once start validation succeeds, the
service must continue while the Activity is minimised, destroyed/recreated, or
the screen is off.

The service owns:

- USB transport;
- capture loop;
- raw RX recorder;
- TX recorder;
- NTRIP connection/reconnect loop;
- NTRIP-to-receiver correction injection;
- parser/advisory fanout;
- sidecar writers;
- stop/shutdown handling;
- wake lock and foreground notification.

The Activity snapshots selected profiles and recording policy into the start
request. The service must not read live Activity widgets or depend on Activity
lifecycle.

A partial wake lock is held only while recording. The foreground notification
remains visible while recording. Android cannot guarantee survival after user
force-stop, USB power loss, phone shutdown or aggressive vendor task killing;
the app should warn users about these limits and battery optimisation risks.

## Session Durability

Session files must remain recoverable after unexpected app closure.

Rules:

- raw byte streams are append-only and never rewritten;
- sidecar JSONL/NMEA streams are append-only;
- writers flush regularly and on stop;
- cleanup must not delete a session directory after recording has started;
- incomplete sessions remain visible/diagnosable on next app start;
- `session.json` updates use atomic temp-file-then-rename where feasible, so a
  crash cannot leave a half-written JSON document;
- app restart/stop cleanup never truncates existing artifacts.

Expected V1 artifacts include, as selected by policy:

- `receiver-rx.raw`;
- `tx-to-receiver.raw`;
- `correction-input.raw`;
- `receiver-solution.nmea`;
- `receiver-solution.jsonl`;
- optional `receiver-solution.gpx`;
- optional remote-base raw observation artifact when supported;
- `events.jsonl`;
- `quality-live.jsonl`;
- `session.json`.

## Stop And Cancellation

Stop must be clean even during NTRIP reconnect delay or a blocked NTRIP read:

- stop requests call `NtripClient.cancel()`;
- the active socket is closed;
- reconnect sleep/delay is interruptible;
- `InterruptedException` during stop is treated as intentional cancellation;
- NTRIP state becomes `STOPPED` or `CANCELLED`, not a user-visible failure;
- shutdown commands are attempted from the selected command profile;
- `stoppedAt` and selected profile/policy IDs are persisted;
- writers are flushed and closed even if NTRIP cancellation, shutdown commands
  or advisory parsers fail.

The reported exception stack from `Thread.sleep()` inside
`NtripClient.runWithReconnect()` is a regression case. Tests must reproduce stop
during reconnect wait and assert that no uncaught exception/error state is
emitted.

## Implementation Phases

The design is implemented in phases but can be executed autonomously.

### Phase 1: Critical Field Fixes

- NTRIP v2-preferred request rendering and response classification.
- Controlled compatibility fallback.
- Stop-time `InterruptedException` handled as cancellation.
- Ensure service starts NTRIP only for NTRIP-enabled workflows/policies.
- Functional NMEA solution export sidecar derived from device RX.
- Crash-resilient session writer changes for append streams and atomic
  `session.json`.

### Phase 2: Profile Persistence

- Local models and stores for command profiles, USB/baud profiles, NTRIP caster
  profiles, NTRIP mountpoint profiles, recording policies, storage profiles and
  workflow defaults.
- Copy/duplicate support for profiles.
- Keystore-backed password references for NTRIP caster profiles.
- Validation for missing profiles, missing secrets, unsupported recording
  sources and unavailable storage.

### Phase 3: Profile Manager UI

- Profile manager sections/screens.
- Mountpoint direct entry and sourcetable selection.
- Show-password control for caster profile editor.
- Recording screen selects profile references and recording policy.
- Expert/manual command editing remains available through command profiles.

### Phase 4: Storage And Session Metadata

- SAF folder selection and persisted URI permission.
- Write sessions to selected storage profile.
- Record selected profile IDs, recording policy, NTRIP protocol/status and
  artifact selections in session metadata without secrets.

### Phase 5: Documentation And Field Test Protocol

- Update user workflows, NTRIP docs, session format, background operation and
  agent guidance.
- Add manual field tests for plain rover, rover + NTRIP v2 to UM980, stop during
  reconnect delay, background/screen-off recording, SAF storage, and profile
  copy/select flows.

## Testing Requirements

Automated tests:

- NTRIP v2 request rendering and Basic auth header.
- NTRIP redaction and no plaintext password in metadata.
- HTTP `401`/`403` classification.
- `SOURCETABLE` classification.
- compatibility fallback policy.
- cancellation during reconnect delay.
- no NTRIP start for non-NTRIP workflows.
- command profile validation and copy.
- USB/baud profile validation and copy.
- caster and mountpoint profile validation/copy.
- recording policy compatibility by workflow.
- NMEA export writes functional sidecar records from RX bytes.
- atomic `session.json` update behaviour where testable.
- session writer does not truncate append artifacts.

Manual field tests:

- plain rover without NTRIP;
- rover + external CORS/EUREF NTRIP to UM980;
- minimise app while recording with NTRIP injection and confirm RX/TX/correction
  files continue growing;
- screen off while recording and confirm service continues;
- stop during reconnect wait and confirm no exception dialog/logged app error;
- write to SAF-selected folder;
- copy/select command, USB/baud, caster and mountpoint profiles.

Local Termux validation should run pure JVM tests and `:app:compileDebugKotlin`.
APK assembly may remain blocked locally by x86-64 Android build tools on
aarch64 Termux; a Windows Android Studio build remains the expected APK
validation path.
