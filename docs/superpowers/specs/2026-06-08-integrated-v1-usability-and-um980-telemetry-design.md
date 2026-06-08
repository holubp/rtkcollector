# Integrated V1 Usability And UM980 Telemetry Design

## Purpose

This V1 pass turns the current partial Compose dashboard into the normal
RtkCollector recording flow. It also fixes the UM980 telemetry model so the
dashboard can show the fields needed during rover + NTRIP field use without
depending on the legacy Activity or on NMEA GGA as the primary solution source.

RtkCollector remains a byte-exact GNSS receiver recorder and correction router.
It remains explicitly outside GIS scope: no maps, no shapefiles, no GIS editing
and no field-feature collection.

## Scope

In scope:

- Compose-owned profile selection, profile editing, validation and recording
  start/stop.
- Dense tiled monitoring dashboard for portrait and landscape screens.
- Full profile CRUD for V1 profile groups.
- Live NTRIP mountpoint switching during recording.
- UM980 binary rover profile with BESTNAVB, STADOPB, compressed raw
  observations, ephemeris, ionosphere and UTC messages.
- Normalized telemetry merger for BESTNAVB, STADOPB and supported fallback
  solution/status messages.
- Session file actions, including on-demand ZIP sharing after recording stops.

Out of scope for this pass:

- RTKLIB real-time solution.
- Android PPP/static solver.
- Maps, shapefiles, GIS editing or feature collection.
- NTRIP caster/server operation.
- Live profile mutation during recording, except active NTRIP mountpoint
  switching.

## Architecture

Compose becomes the only normal user-facing entry point for configuration,
profile selection, recording start/stop, monitoring and session/file actions.
The legacy Activity may remain temporarily as internal reference code during
implementation, but Start must not launch it in the final V1 flow.

The foreground service remains the owner of recording, wake lock, USB runtime,
NTRIP runtime and session writers. Compose builds a validated start request,
sends explicit service commands and observes service state.

The raw receiver capture path remains authoritative:

- `receiver-rx.raw` is never modified by parser, UI, NTRIP or ZIP/share logic.
- Parser failures do not imply recording failure.
- NTRIP failure does not imply recording failure.
- ZIP/share failures do not modify original session files.
- Profile-editor validation errors block start before receiver commands,
  NTRIP connection or recording begin.

## Dashboard

The Home screen is an information-dense tiled monitor. It must adapt column
count and compact row layout for portrait phones, landscape phones and tablets.
Only dashboard content scrolls. Start/Stop and Menu remain fixed outside the
scrollable content.

Tiles:

- Position: large lat/lon, MSL altitude, ellipsoidal height, UTC, latitude
  sigma and longitude sigma.
- Fix: large interpreted fix type, tracked/used satellites, PDOP/HDOP/VDOP,
  horizontal/vertical accuracy, differential age and PPP status.
- NTRIP: large connection status, caster URL, clickable mountpoint, transferred
  bytes, station ID/name, base lat/lon, baseline distance and input/output
  rates.
- Files: session folder, receiver RX bytes, TX-to-receiver bytes, correction
  input bytes, generated NMEA bytes and artifact/session action status.
- Profiles: one compact tile with rows for workflow, command script profile,
  baud profile, NTRIP caster profile, recording/output profile and storage
  location profile.

No separate "Change mountpoint" button is needed. Tapping the NTRIP mountpoint
field opens the contextual live mountpoint picker.

Clickable fields:

- NTRIP mountpoint: live editable during recording.
- Baud: selectable before recording only.
- Files/session folder: opens session/file actions.
- Profile summary rows: open the relevant profile editor before recording and
  are read-only during recording.

Units are auto-scaled with explicit units:

- accuracy/distance below 0.01 m as mm;
- accuracy/distance below 1 m as cm;
- other local distances as m;
- baseline below 1000 m as m, otherwise km;
- bytes and rates as B/kB/MB/GB and B/s/kB/s/MB/s;
- differential age in seconds unless large enough to need minutes.

## Profiles

V1 profile management is full CRUD through Compose screens:

- command script profiles;
- USB/baud profiles;
- NTRIP caster profiles;
- NTRIP mountpoint profiles;
- recording/output profiles;
- storage location profiles.

Built-in profiles are protected and can only be copied. User profiles can be
created, renamed, copied, edited and deleted.

The dashboard shows selected profile summaries. Dedicated screens edit profile
details.

Terminology:

- Recording/output profile controls which session artifacts are written.
- Storage location profile controls where the session folder is created.

Before recording, the app validates:

- USB device selected and permission granted;
- workflow and receiver profile compatibility;
- command script safety;
- baud transition plan;
- NTRIP configuration only if the workflow uses NTRIP;
- storage permission and output policy;
- base position when fixed-base mode is selected.

During recording, only the active NTRIP mountpoint can be changed live. Other
profile edits apply to the next session.

## Baud Selection

Baud is a clickable profile/configuration field before recording. It opens a
fixed-value selector, not free text.

V1 selectable values:

- `115200`
- `230400`
- `460800`
- `921600`

Default is `230400`. `921600` is the high-throughput field option when the
selected UM980 output profile approaches the available line budget.

The app must distinguish:

- profile baud: current receiver baud used to send initial commands and any
  baud-switch command;
- recording serial baud: target host/receiver baud after the switch.

Baud changes are forbidden during recording. The transition sequence is:

1. Open host serial bridge at profile baud.
2. Send user init commands.
3. Send generated receiver baud command, for example `CONFIG COM1 921600`.
4. Pause.
5. Reconfigure host serial bridge to target baud.
6. Drain transitional receiver output through the recorded RX path.
7. Send mode/log commands at the target baud.

## UM980 Default Binary Rover Profile

The default UM980 rover + NTRIP profile should be generated from the selected
baud and should prefer binary logs.

Default command body:

```text
CONFIG COM1 <selected-baud>
UNLOG COM1

MODE ROVER
CONFIG MMP ENABLE

VERSIONB

BESTNAVB COM1 0.1
STADOPB COM1 1
OBSVMCMPB COM1 0.25

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

`CONFIG COM1 <selected-baud>` is part of the controlled baud transition plan,
not arbitrary user script text. If the selected target baud is the same as the
profile baud, no host-side baud transition is needed.

`VERSIONB` is recorded for receiver identification and session metadata.

`STADOPB` is included by default because the UM980 manual identifies it as DOP
of BESTNAV, message ID 954, binary syntax `STADOPB 1`. The implementation must
verify whether the receiver accepts `STADOPB COM1 1`; if not, the command
builder should fall back to the documented non-port syntax `STADOPB 1` or expose
a compatibility profile.

No GGA is enabled by default. Compatibility profiles may enable GGA/RMC/GSA/GSV,
but the default dashboard source is binary telemetry.

## UM980 Telemetry Parsing

Telemetry parsing uses a normalized merger. Individual parsers decode specific
wire formats into a common telemetry state. Each field should track source and
age internally so stale values can be shown as stale or `n/a`.

Primary dashboard decoders:

- BESTNAVB: solution status/type, GPS week/TOW-derived UTC, latitude,
  longitude, MSL height, undulation, ellipsoidal height, latitude sigma,
  longitude sigma, height sigma, station ID, differential age, solution age,
  satellites tracked, satellites used, velocity status/type, horizontal speed,
  track, vertical speed and speed sigmas where parsed.
- STADOPB: GDOP, PDOP, TDOP, VDOP, HDOP, NDOP, EDOP, cutoff and tracked PRNs
  where parsed.

Fallback/compatibility decoders:

- BESTNAVA for ASCII BESTNAV profiles.
- NMEA GGA/RMC/GSA/GSV when compatibility profiles enable NMEA output.
- PPP/ADR logs only in workflows that explicitly request PPP or ADR solution
  monitoring.

Processing-support messages:

- OBSVMCMPB is recorded as the compressed raw observation source.
- Binary ephemeris, ionosphere and UTC messages are classified and preserved for
  downstream processing even if all semantic decoders are not completed in the
  first dashboard implementation.

Generated NMEA export should be derived from decoded BESTNAVB for GGA/RMC/VTG
where enabled, following the approach used in `../um980-rtklib-pipeline`.

Dashboard labelling must be precise:

- BESTNAVB provides satellites tracked/used, not necessarily true satellites in
  view.
- True satellites-in-view requires GSV/SATELLITE/SATSINFO-style sources and
  should not be implied by BESTNAVB alone.
- Base lat/lon and baseline are derived from NTRIP/profile metadata plus rover
  position, not from BESTNAVB.

## NTRIP Metadata And Live Mountpoint Switching

NTRIP uses two profile layers:

- Caster profile: host, port, username, secret reference, protocol policy and
  cached sourcetable.
- Mountpoint profile: caster reference, mountpoint name, expected correction
  format, parsed station metadata and optional manual overrides.

The app should parse sourcetable `STR` metadata when available and store it in
the selected mountpoint profile and session metadata:

- station ID/name;
- country/network/operator where available;
- approximate base lat/lon if provided;
- correction format/details;
- carrier/system metadata where useful.

Manual override is allowed when caster metadata is missing or wrong.

During recording, tapping the mountpoint field in the NTRIP tile opens a
mountpoint picker for the active caster profile. Applying a new mountpoint:

- stops the current NTRIP client;
- starts the new mountpoint connection;
- records a redacted event;
- continues raw receiver recording;
- keeps existing session artifacts append-only;
- updates dashboard NTRIP URL, status, station/base metadata and rates.

Authentication and authorization failures remain terminal for that mountpoint
attempt and must not loop aggressively. Raw recording continues.

## Files, Sessions And ZIP Sharing

The Files tile shows session folder/location and artifact counters. Tapping it
opens session actions.

While recording:

- copy session location is allowed;
- ZIP/share is disabled;
- active raw files are not shared directly unless a future explicit partial
  snapshot mode exists.

After recording:

- copy folder/file paths;
- share selected files;
- create/share ZIP on demand only.

ZIP presets:

- Full session: every session artifact.
- Processing bundle: raw receiver stream, TX/corrections where relevant,
  metadata, base-position file, receiver profile, init script and generated
  NMEA/JSON solution exports where enabled.
- Diagnostics bundle: metadata, events, quality logs, NTRIP state,
  command/profile IDs, validation result and bounded raw samples/tails if
  implemented safely.

ZIP creation:

- starts only after the user requests sharing;
- shows progress;
- can be cancelled;
- writes a separate temporary/export archive;
- never modifies original session files;
- runs only after recording stops in V1.

## Testing And Acceptance

Focused JVM tests:

- profile CRUD and protected built-ins;
- Compose dashboard state models and clickable-field routing;
- baud selector allowed values and baud-transition plan;
- UM980 binary stream classification;
- BESTNAVB full field extraction matching the UM980 manual and
  `../um980-rtklib-pipeline`;
- STADOPB DOP extraction;
- NMEA generation from BESTNAVB;
- NTRIP sourcetable metadata parsing and mountpoint non-overwrite behavior;
- ZIP preset selection, progress model and disabled-while-recording rules.

Android/manual acceptance:

- portrait and landscape phone/tablet layouts keep Start/Stop/Menu visible;
- Compose can start recording without launching the old Activity;
- rover + NTRIP to UM980 reaches streaming and feeds RTCM to receiver;
- dashboard shows BESTNAVB position/fix fields and STADOPB DOP fields;
- NTRIP live mountpoint change works without stopping recording;
- session files remain append-only and recoverable after stop;
- ZIP creation after stop shows progress and Android share sheet;
- no maps/GIS/shapefile dependencies.

Build validation:

- JVM module tests locally;
- `:app:compileDebugKotlin` locally;
- full `assembleDebug` and Android app tests on Windows/Android Studio or CI
  because Termux Android 36 resource linking is known to be limited.

## Risks

- The default binary profile increases receiver output compared with a minimal
  BESTNAVB-only profile. The baud/profile selector and line-rate estimate must
  make high-load configurations visible before start.
- Some UM980 binary commands may have firmware-specific syntax differences for
  port-qualified versus non-port-qualified forms. Command builders should use
  verified syntax and keep compatibility profiles explicit.
- STADOPB and other low-rate diagnostics must remain advisory. If they fail to
  parse or are absent, raw recording continues and dashboard fields show `n/a`.
- ZIP creation can be slow for long raw sessions. It must be cancellable and
  must not hold locks that interfere with stopped-session cleanup or future
  recordings.
- NTRIP sourcetable metadata may be incomplete or wrong. Manual overrides and
  provenance in session metadata are required.
