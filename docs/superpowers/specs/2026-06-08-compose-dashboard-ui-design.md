# Compose Dashboard UI Design

## Purpose

Replace the current long Android form with a polished Compose and Material 3
user interface that is usable in real field sessions. The UI must make the
effective workflow configuration visible before start, keep live recording
telemetry visible while running, and keep critical controls reachable on
portrait and landscape screens.

This design does not add maps, shapefiles, GIS editing, feature collection,
RTKLIB real-time processing, Android-side PPP/static solving or NTRIP caster
upload.

## Current Problems

The existing native `LinearLayout` UI is one long vertical page. It mixes
workflow choice, receiver setup, profile editing, NTRIP settings, storage,
recording policy, start/stop and monitoring. This creates several concrete
failures:

- labels and fields are visually separated by unrelated buttons;
- buttons appear between labels and their fields;
- profile selection immediately writes values into fields without a clear
  profile-editing context;
- fetched NTRIP mountpoints can overwrite the mountpoint text field when the
  spinner refreshes to its first item;
- live recording status is textual and hard to scan;
- `Start`, `Stop` and `Menu` can scroll away on small screens;
- file locations and sharing are not first-class session actions.

## UI Architecture

Use Compose and Material 3 for the Android app UI. Keep existing capture,
session, NTRIP and receiver logic service-owned and Kotlin-module based. Compose
is an observer and command surface; it must not own the recording lifecycle.

Top-level screens:

- **Home dashboard**: first screen, compact recording instrument panel.
- **Settings hub**: reachable from the top app bar menu.
- **Profile editors**: dedicated screens for profile types.
- **Sessions**: current and historical session browser with file/share actions.

The first implementation should add the Compose shell and replace the current
long form with state-driven screens. The foreground recording service remains
the owner of USB capture, NTRIP feeding, wake lock and session writers.

## Home Dashboard

The Home screen uses the exact dashboard-card structure validated in the design
mockup:

- fixed top app bar;
- fixed bottom action bar;
- status strip;
- cards: `Position`, `Fix`, `NTRIP`, `Files`.

The dashboard is not a configuration form. Before recording it shows the
configuration that will be used if the user taps `Start`. During recording it
shows the immutable running configuration plus live observables. After
recording it shows final session state and file actions.

### Persistent Controls

The top app bar is always visible and contains:

- app name;
- recording state indicator;
- compact workflow/mode indicator where space permits;
- `Menu`.

The bottom action bar is always visible and contains:

- before recording: `Start`;
- while recording: `Stop`, `NTRIP`, optional `Mark`;
- after recording: `New`, `Share ZIP`, optional `Open session`.

Only the middle dashboard content scrolls. Critical actions must never be
inside the scroll area.

### Responsive Layout

The UI must work on portrait phones, landscape phones, portrait tablets,
landscape tablets and unusual resolutions.

Layout rules:

- small portrait: one-column cards in the scrollable middle area;
- medium width: two columns;
- wide tablet: up to three columns;
- status strip wraps compactly or collapses lower-priority chips;
- top and bottom bars keep fixed heights;
- buttons use short labels and fixed minimum heights;
- text must not overlap or wrap incoherently inside controls.

On the first visible viewport of a small screen, the user should see at least:

- workflow;
- mountpoint if NTRIP is enabled;
- receiver;
- storage target/state;
- fix type;
- position when available;
- NTRIP status when enabled;
- primary `Start` or `Stop` action.

## Dashboard Content

### Status Strip

The status strip contains:

- `Workflow`;
- `Mountpoint`;
- `Receiver`;
- `Storage`.

It is a compact summary of the effective session configuration, not an editor.

### Position Card

Required fields:

- latitude/longitude;
- ellipsoidal height;
- altitude, meaning orthometric height above geoid/MSL when the receiver or
  parser provides it;
- UTC date/time if available from the selected solution source.

### Fix Card

Required fields:

- interpreted fix type, not only a raw numeric code;
- UTC date/time where not already shown in the Position card;
- satellites in view;
- satellites in use;
- PDOP;
- HDOP;
- VDOP;
- latitude error;
- longitude error;
- horizontal accuracy estimate;
- vertical accuracy estimate;
- age of differential;
- baseline length;
- PPP status where available;
- reserved RTKLIB status, shown as `Not configured` in V1.

Receiver internal solution, receiver PPP solution and future RTKLIB solution
are separate solution engines. Do not merge them into one ambiguous fix.

### NTRIP Card

Required fields:

- URL connected to, including host, port and mountpoint, without secrets;
- connection status;
- total bytes transferred in the session;
- reference station ID where known;
- base station latitude/longitude where known;
- inbound NTRIP rate;
- rate sent to the rover receiver;
- base distance/baseline length where both rover and base coordinates are
  available.

The card must display `n/a` for unavailable base metadata instead of moving or
removing fields.

### Files Card

Required fields and actions:

- session folder or SAF-backed session location;
- key recorded files and byte counters, including receiver RX, TX to receiver,
  NTRIP correction input and NMEA solution export where enabled;
- copy folder/location;
- copy selected file path/location;
- copy file list;
- share selected files through Android `send to`;
- create/share ZIP after recording stops.

While recording, copying paths is allowed. ZIP/share during recording is either
disabled or explicitly labelled as a partial snapshot.

For SAF storage, the UI must show the storage profile name and URI-backed
location because a raw filesystem path may not exist.

### Units And Unavailable Values

Use adaptive units:

- distances and accuracies: `mm`, `cm`, `m`, `km`;
- bytes: `B`, `kB`, `MB`, `GB`;
- rates: `B/s`, `kB/s`, `MB/s`.

Unavailable values display as `n/a` in stable positions. Missing telemetry must
not collapse card layout or move critical controls.

## Settings Hub

The Settings hub is opened from `Menu` and contains clear rows for:

- workflow defaults;
- receiver / USB;
- command scripts;
- NTRIP casters;
- NTRIP mountpoints;
- recording outputs;
- storage;
- sessions.

The hub keeps detailed settings away from the Home dashboard while making them
easy to reach.

## Profile Model

Profiles are named reusable objects, not hidden preferences.

Profile types:

- receiver profile;
- command script profile;
- USB/baud profile;
- NTRIP caster profile;
- NTRIP mountpoint profile;
- recording output profile;
- storage profile;
- workflow default profile set.

Every profile editor must support:

- create new;
- select existing;
- visible profile name;
- edit;
- rename;
- copy to new profile;
- save;
- delete when safe;
- set as default for one or more workflows/modes.

Built-in defaults may be protected from destructive edits. Users can copy a
protected default and edit the copy. Deleting a profile used as a workflow
default requires confirmation and reassignment.

### Workflow Defaults

A workflow default profile set maps a mode, such as `Rover + NTRIP`, to:

- receiver profile;
- command profile;
- USB/baud profile;
- NTRIP caster profile;
- NTRIP mountpoint profile;
- recording output profile;
- storage profile.

Home loads the selected workflow default set and displays the effective
configuration.

### Runtime Edit Semantics

During recording, settings remain reachable but every changed field must say
whether it applies now or on the next recording.

- NTRIP host/mountpoint/GGA settings may offer `Apply now` for NTRIP-enabled
  workflows.
- Receiver commands, baud, workflow, receiver profile, storage and recording
  output files apply to the next recording.
- The running dashboard must continue showing the immutable configuration that
  actually started the current recording.

## NTRIP UI Rules

NTRIP settings are split into caster and mountpoint profiles.

Caster profile fields:

- name;
- host;
- port;
- username;
- password with show/hide control;
- protocol policy;
- cached sourcetable mountpoints.

Mountpoint profile fields:

- name;
- referenced caster profile;
- mountpoint text;
- GGA upload policy;
- expected format;
- optional remote-base raw-observation availability flag.

Mountpoint fetch rules:

- fetching a sourcetable updates the cached list only;
- typed or saved mountpoint text is never overwritten by list refresh;
- selecting a listed mountpoint is an explicit user action and updates the
  mountpoint text;
- typed mountpoints remain valid even when the mountpoint list is unavailable.

The UI must show NTRIP protocol/status separately from recording output
profiles so users do not confuse a recording policy name with NTRIP version.

## UM980 Live Telemetry And Binary Parsing

UM980 live telemetry must support mixed receiver streams. The parser path must
not be line-only because binary logs can contain arbitrary `$`, `#` or
Unicore-looking sync bytes.

Required parser architecture:

- byte-level stream classifier for NMEA, Unicore ASCII, Unicore binary, RTCM
  and noise;
- NMEA sentence validation before accepting `$...` as NMEA;
- Unicore ASCII printable record-shape checks and checksum/CRC validation where
  implemented;
- Unicore binary frame detection, length validation and CRC validation before
  decoding;
- parser failures reported as advisory warnings, never as raw recording
  failures.

UM980 binary support must extract dashboard telemetry where documented,
including at least binary `BESTNAVB` receiver solution fields when present:

- GPS week/TOW converted to UTC;
- solution status;
- position type;
- latitude/longitude;
- MSL height;
- undulation;
- derived ellipsoidal height where needed;
- latitude, longitude and height sigma;
- station ID;
- differential age;
- satellites tracked;
- satellites used;
- velocity status/type where useful.

`BESTNAV[A/B]` is a receiver solution product. It may feed dashboard telemetry
and derived NMEA/track exports, but it must not be treated as RTKLIB raw
observation input. RTKLIB-compatible processing requires observations such as
`OBSVMB`/`OBSVMCMPB`, ephemerides/NAV and base observations.

The Android implementation may learn framing and field layouts from the
existing `../um980-rtklib-pipeline` code, especially its public stream parser,
BESTNAV parser and message-statistics rules. Do not guess unsupported UM980
binary fields; unsupported records must be counted and surfaced.

## UM980 Baud Synchronisation

The app must configure the UM980 device and the host serial link to the same
line speed through a single controlled transition. This setting should be
performed once at recording start, not repeatedly in the live capture loop.

Required sequence when changing baud:

1. Open/configure the host bridge at the currently working receiver baud
   (`profileBaud`).
2. Send user init commands and any reviewed baud-switch command to the receiver
   at that current baud.
3. Pause briefly after profile/baud command transmission.
4. Reconfigure the host bridge to the target recording baud (`serialBaud`).
5. Drain transitional receiver output through the normal RX path where possible
   so raw capture remains authoritative.
6. Send mode/log commands only after the host bridge is listening at the target
   baud.
7. Start the steady capture loop.

This mirrors the successful Termux UM980 pipeline rule: send the runtime
profile at the currently working receiver baud and switch the host bridge only
after the profile has been sent. FTDI-style bridges may need exact/fractional
baud handling rather than rounded divisors.

The UI must make `profileBaud` and `serialBaud` visible in the effective
configuration summary and must warn when they differ without an explicit
reviewed baud-switch command.

## Sessions Screen

The Sessions screen lists:

- active session at the top when recording;
- recent sessions;
- older sessions with search/filter where practical.

Session detail shows:

- workflow and receiver;
- NTRIP metadata without secrets;
- start/stop timestamps;
- storage location;
- file list with sizes;
- validation/warnings;
- copy/share actions.

The browser should remain useful beyond the most recent session. It should not
block the dashboard work, but it is in scope for the Compose UI pass.

## Error Handling

UI errors must be explicit and field-oriented:

- invalid profile fields show validation messages near the field;
- protected profile edits explain that the user should copy first;
- live NTRIP update failures show degraded NTRIP status while recording
  continues;
- parser gaps show `n/a` and warnings while raw recording continues;
- share/ZIP errors show actionable messages and do not alter session files;
- SAF permission loss blocks new SAF recording and explains the required folder
  re-selection.

No UI, parser, NTRIP or sharing failure may modify `receiver-rx.raw` or stop raw
capture while transport and storage still function.

## Testing

Required tests and validation:

- Compose state-model unit tests for planned, running and stopped dashboard
  states;
- profile-store tests for create, rename, copy, delete and default assignment;
- NTRIP mountpoint tests proving list refresh does not overwrite typed text;
- UM980 parser tests for mixed streams with NMEA, ASCII, binary and noise;
- binary `BESTNAVB` parser tests using documented message ID and CRC-validated
  frames;
- baud-transition tests proving commands before and after baud switch are
  ordered correctly;
- session browser tests for file list and ZIP/share eligibility states;
- app compile on a full Android host;
- manual portrait and landscape screenshot review on phone/tablet dimensions;
- field smoke test: plain rover, rover + NTRIP to UM980, temporary-base with
  PPP enabled, stop/share session.

In the local Termux environment, Android resource linking may remain limited by
native Android 36 tooling. Use `:app:compileDebugKotlin` locally where full APK
packaging is blocked and run full `assembleDebug` on Windows/Android Studio or
CI.

## Implementation Slices

The implementation should be reviewable:

1. Add Compose/Material dependencies and app shell.
2. Introduce dashboard state models and formatters.
3. Implement the Home dashboard with mocked/available service state.
4. Add Settings hub navigation.
5. Implement profile editor pattern and workflow defaults.
6. Fix NTRIP mountpoint semantics in the new model.
7. Expand service state for telemetry.
8. Implement UM980 mixed-stream and binary `BESTNAVB` parsing.
9. Implement controlled baud-transition model.
10. Implement Sessions browser and file/share/ZIP actions.
11. Add responsive screenshot/manual validation notes.

Each slice should keep raw recording behaviour unchanged unless explicitly
touching service state or parser fanout.
