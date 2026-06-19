# User Workflows

RtkCollector V1 is a receiver recorder and correction router. It is not a GIS
app: there are no maps, shapefiles, feature forms or survey-cartography project
tools.

## Experimental V1 Android UI

The Android UI lets the user choose a workflow and receiver profile. The derived
workflow details and expected recording artifacts are shown before validation.
The Home screen uses a Compose dashboard that shows the effective session
configuration and live telemetry in compact cards: Position, Fix, NTRIP and
Files. Detailed profile editing is reached from Menu.
The dashboard configuration tiles are intentionally lean selectors: Workflow
selects the active workflow, Settings selects the active settings set,
Mountpoint selects or overrides the active NTRIP mountpoint, Receiver selects a
receiver command profile and Storage selects a storage location profile. Full
profile creation and editing belongs in Menu.
It also provides the experimental real-recording controls:

- USB device refresh and Android USB permission request;
- command profiles for reusable init and shutdown scripts;
- USB/baud profiles for profile baud, post-profile serial baud and optional
  remembered USB identity;
- NTRIP caster and mountpoint profiles, including typed mountpoint entry and
  stored Keystore-backed passwords;
- NTRIP caster-upload profiles for base workflows that publish receiver-created
  RTCM to an external caster;
- recording policies for derived NMEA/JSONL exports, NTRIP correction input,
  optional remote-base raw observations where a source supports them, and an
  optional Android mock-location publisher (described below);
- selectable PPP-to-GGA quality mapping for generated NMEA, defaulting to `2`
  with `5` and `9` available for compatibility with downstream consumers;
- storage profiles, with app-private storage as the default and Android SAF
  folder selection for user-visible session folders;
- manual fixed-base coordinates or pasted/imported `base-position.json`;
- foreground-service start/stop;
- live receiver RX, receiver TX, correction input and NTRIP state counters.

The Position card is also an action surface. Tapping the displayed coordinate
opens copy choices for `geo:lat,lon`, `lat,lon`, `lat` and `lon`. In base
workflows, compact `Base` and `Avg` controls may appear next to the coordinate:
`Base` switches the next-session setup toward fixed-base operation using the
current coordinate and ellipsoidal height; the user must still choose or create
a matching `MODE BASE` command profile before starting. `Avg` starts a live
coordinate and ellipsoidal-height average. Averaging is valid only while the
interpreted fix type remains unchanged and the receiver continues reporting
ellipsoidal height. If the fix changes or required height disappears, averaging
stops and the app reports the reason. This live average is field guidance, not a
replacement for PPP/static RTK or an accepted `base-position.json`.

The Files card shows the active session location and recorded byte counts. The
Sessions menu lists recordings in the configured app-private storage with latest
sessions first and separates the current session, completed recordings and
archived recordings. Active recordings cannot be shared, archived, restored or
deleted from this menu.

Android mock-location output is a recording-scoped option. When enabled, the
foreground recording service publishes the current best fresh RtkCollector
solution to Android's mock GPS provider. The app must be selected as the mock
location app in Android Developer options. Mock-provider failure degrades only
mock output; raw receiver recording continues. Android `Location.altitude` is
published as ellipsoidal height. Satellite counts are attached as best-effort
location extras when available, but Android's public mock-location API does not
allow an ordinary app to inject full GNSS satellite status or satellite sky
positions for other apps. The Fix card shows compact mock-provider monitoring
when mock output is enabled, including publish state, effective publish rate,
the millisecond interval between the last two successful mock updates and the
age of the selected solution. V1 publishes mock locations at a fixed-rate
default of `1 Hz`. This monitor reports RtkCollector's publishing cadence;
downstream apps may still apply their own display smoothing or throttling.

Completed recordings can be selected individually, by group or all together.
Sharing the full recording creates one temporary ZIP per selected session,
sends those ZIPs through the Android share sheet and leaves the original session
folders untouched. Users can also share the derived NMEA solution export
directly when `receiver-solution.nmea` exists for the selected session. Direct
NMEA sharing creates temporary `.nmea` files in app cache with names matching
the session ZIP naming pattern, sends those files through the Android share
sheet and does not modify the session folder. The same session list also offers
an explicit `Re-export NMEA` action for filesystem-backed stopped sessions; it
regenerates `receiver-solution.nmea` from `receiver-rx.raw` using the currently
selected PPP-to-GGA mapping and leaves raw/correction/TX artifacts unchanged.
Old temporary share ZIPs and NMEA files are cleaned up on later share attempts,
and newly shared temporary files are scheduled for delayed best-effort cleanup
because Android does not expose a reliable signal that the receiving app has
finished reading them. Archiving is a different operation: it creates a permanent
maximum-compression ZIP in session storage, verifies that the ZIP contains
session artifacts, then removes the original folder. Archived recordings are
marked in the list. Restore extracts an archive back to a session folder,
verifies the restored artifacts and removes the archive ZIP only after a
successful restore. Delete requires confirmation and can remove selected
completed folders or archive ZIPs.

Filesystem-backed app-private storage and SAF document-tree storage should offer
the same session-management workflow where Android's document provider supports
the required operations: browse sessions, share temporary ZIPs, share generated
NMEA, re-export generated NMEA, archive, restore and delete completed
recordings. SAF operations are stream-based and provider-dependent; failures
must be reported without mutating `receiver-rx.raw`. Start/Stop and Menu are
intended to stay outside scrollable content so they remain reachable in portrait
and landscape layouts.

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

Built-in init/shutdown command profiles open in view-only mode. Copy a built-in
profile before editing it. Multiline command fields use a native Android text
editor so hardware-keyboard arrows and modifier combinations navigate inside the
field; Tab and Shift+Tab move between fields and restore each field's cursor
position where possible.

While a real session is active, the foreground service owns capture. The
Activity only sends start/stop requests and observes service state.
During recording, only NTRIP mountpoint/caster updates are intended to apply
live. Workflow, receiver-command and storage profile switches are next-session
configuration changes and should be made after stopping the active recording.

Settings-set export must keep passwords redacted by default. Plaintext NTRIP
password export is allowed only through an explicit user option because exported
files are ordinary user-visible data.

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

If a receiver is connected but Android USB permission is missing, pressing Start
requests permission and does not create a recording session. Approve the Android
permission dialog, then press Start again. If Android reports permission granted
but the receiver cannot be opened, reconnect the receiver, close other serial
apps and retry USB access.

The built-in UM980 command profile `UM980 multi-Hz binary RTK+PPP` is the
recommended default. Built-in profiles are app-distributed, read-only in the UI
and synced on app update; copy one before editing it. This profile sends
binary receiver-solution, raw-observation, DOP, ephemeris and ionosphere/time
logs while avoiding high-rate NMEA chatter:

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

The built-in profile `UM980 multi-Hz ASCII RTK+PPP` is available for lower-risk
ASCII/PPP-oriented testing:

```text
CONFIG PPP ENABLE E6-HAS
CONFIG PPP DATUM WGS84
CONFIG PPP TIMEOUT 120
CONFIG PPP CONVERGE 15 30

MODE ROVER SURVEY
CONFIG RTK TIMEOUT 120
CONFIG RTK RELIABILITY 3 1

GNGGA 0.05
GNRMC 0.05
GNGST 0.05
GNGSV 1
GNGSA 1
GPGLL 1
GPGNS 1
GPGRS 30
PPPNAVA 1
ADRNAVA 1
RTKSTATUSA 1
RTCMSTATUSA ONCHANGED

TROPINFOA ONCHANGED
GPSIONB ONCHANGED
```

Additional built-ins include `UM980 1 Hz ASCII RTK+PPP` and `UM980 base config`.
They are also read-only and copyable. Fixed-base start from the Position card
replaces the base template's survey-style `MODE BASE TIME ...` line with an
explicit `MODE BASE <lat> <lon> <ellipsoidal-height>` command from the accepted
current or averaged coordinate. The height is ellipsoidal height, matching the
UM980 fixed-base command model and the local UM980 RTKLIB pipeline; orthometric
altitude/MSL height must not be substituted. User changes to copied command
scripts should remain conservative and source-backed.

The normal recording start path sends runtime UM980 commands only. It does not
write receiver non-volatile memory. Persistent writes are explicit warned
maintenance actions:

- Menu > Init/shutdown scripts > Edit > Write init config persistently to device:
  sends the visible Init script, then `SAVECONFIG`, and requires the receiver's
  `response: OK` acknowledgement.
- Menu > USB device and baud > Edit > Write target baud persistently to device:
  sends `CONFIG COM1 <target baud>`, switches the host to the target baud when
  needed, verifies receiver communication, sends `CONFIG COM2 <target baud>`,
  `CONFIG COM3 <target baud>` and `SAVECONFIG`, and requires the receiver's
  `response: OK` acknowledgement for `SAVECONFIG`.

If recording is active, persistent writes use the foreground recording service's
existing receiver connection and are recorded in `tx-to-receiver.raw`. If
recording is not active, RtkCollector opens the selected USB receiver at the
USB/baud profile's initial receiver baud, verifies communication with the
receiver, and only then sends persistent commands. Normal recording startup and
shutdown never append `SAVECONFIG` automatically.

## Device Console

The Device console is an idle-only manual receiver console. It is available
from Menu > Device console and is disabled while recording is active.

Use it for short diagnostics or maintenance commands:

1. Stop recording.
2. Open Menu > Device console.
3. Select the USB/baud profile.
4. Tap Connect.
5. Select the line ending, normally CRLF.
6. Type an ASCII receiver command and tap Send.
7. Optionally select an init/shutdown script and tap Send init.
8. Tap Disconnect or leave the screen to close the USB connection.

Console output is temporary. It is not saved into session folders and does not
modify receiver recording artifacts. Non-printable bytes are shown as compact
hex markers so mixed binary/text receiver streams do not break the display.

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
receiver's in-device solution where available. Derived NMEA/JSONL solution
sidecars are produced from the receiver RX stream according to the selected
recording policy. Raw observations are requested where the receiver supports
them.

## Rover With NTRIP Fed To Receiver

Use this when Android should act as the NTRIP client and feed RTCM corrections
to a capable rover receiver.

User flow:

1. Select the rover receiver profile.
2. Select or edit an NTRIP caster profile and mountpoint profile. The mountpoint
   can be typed directly or selected from a cached/fetched caster sourcetable.
3. Store credentials as secret references; passwords are never written to
   `session.json`.
4. Start recording.
5. The app records receiver RX, selected correction input, TX bytes sent to the
   receiver, selected derived solution sidecars, quality events and NTRIP state.

The receiver's internal RTK float/fix solution is separate from any future
Android-side solution engine.

### UM980 In-Device RTK Monitoring

For UM980/N4 V1 rover use, RtkCollector runs the NTRIP client externally and
feeds raw RTCM3 bytes to the receiver over COM1. The receiver computes its own
RTK solution. The dashboard separates:

- main receiver fix from BESTNAV/GGA;
- in-device PPP status from PPPNAV;
- in-device RTK status from ADRNAV/RTKSTATUS/RTCMSTATUS;
- future RTKLIB status, which remains disabled in V1.

The app does not use UM980 internal NTRIP-client commands in V1. RTCM bytes from
NTRIP are fed unchanged to the receiver input and are recorded separately from
the authoritative receiver RX stream.

The NTRIP caster editor includes a refresh action for the selected caster. It
connects to the caster sourcetable, replaces the cached mountpoint list on that
caster profile, and leaves the selected mountpoint profile unchanged until the
user selects or edits one.

The NTRIP mountpoint editor includes the same refresh action for the currently
selected caster, so a stale list can be updated without leaving the mountpoint
configuration screen. If the refreshed or selected caster list does not contain
the current mountpoint text, the editor preserves the text, marks the field as
invalid, and blocks saving until a compatible caster or mountpoint is selected.

Mountpoint profile lists show suspect saved profiles with an orange warning and
the text `Suspect invalid mountpoint`. The main-screen mountpoint selector shows
the same profile-level suspect state with a compact warning marker.

PPP status is shown only from explicit PPP receiver logs such as `PPPNAVB` or
`PPPNAVA`: no PPP log means `n/a`; PPP logs with insufficient observations mean
`PPP not started`; `PPP_CONVERGING` means `PPP converging`; and a converged PPP
position type means `PPP converged`. PPP convergence is not used to replace the
main receiver fix unless the receiver's main solution itself reports PPP.

### Live NTRIP Changes During Recording

For NTRIP-enabled workflows, the UI can request a live caster or mountpoint
update without stopping raw receiver recording. The service cancels the old
NTRIP client, records a redacted event, starts the new NTRIP client and
continues writing `receiver-rx.raw`. `correction-input.raw` and
the same-byte compatibility file `correction-input.rtcm3` remain append-only
across the switch. `tx-to-receiver.raw` remains the separate app-to-receiver
transmit log.

The user can also disable NTRIP during recording. This stops correction feeding
but leaves raw receiver capture active.

## Storage Profiles

The default storage profile writes sessions under the app-private external
files directory. This is the safest first test path because `session.json` uses
the platform file API and can be replaced atomically.

To write into a visible user folder, choose a storage profile and tap
`Select Android folder`. Android shows the system folder picker and the app
persists read/write permission for that tree. The stored URI field is
display-only routing data, not a manually typed path. Recording start is blocked
if the selected SAF profile has no tree URI or if Android no longer reports a
persisted write permission. The app does not silently fall back to app-private
storage after the user selected SAF.

SAF sessions are created as new uniquely named folders. Receiver RX, receiver
TX, NTRIP correction input, events, quality logs and derived solution sidecars
are separate files in that folder. Raw receiver bytes remain in
`receiver-rx.raw`; metadata and parser-derived exports are sidecars.

## Temporary Base

Use this when you want to place a temporary base close to the rover, often with
better sky visibility than a distant permanent base. A stationary car roof in
open sky can be useful if the antenna mount is stable and multipath is
acceptable. The temporary base is a normal base-position determination
workflow: it records raw observations and in-device solutions so its coordinate
can be determined by RTK against another base, PPP, static post-processing, or
fallback averaging.

Temporary base is not the same as final fixed-base operation. It records the
data needed to produce one or more base-position candidates later.

Required recording behaviour:

- record the raw receiver byte stream unchanged;
- request raw observations at at least `1 Hz` where the receiver supports raw
  observations;
- always record the receiver's normal in-device solution;
- request and record receiver PPP solution separately where the receiver profile
  supports PPP;
- optionally feed CORS/EUREF/NTRIP corrections to the receiver.

Temporary base can use the same selected NTRIP caster and mountpoint profiles as
rover+NTRIP. This is intended for deriving the temporary base coordinate from a
CORS/EUREF/base correction stream. Plain rover recording remains NTRIP-off.

Base-position candidate preference:

1. static RTK against a known CORS/EUREF/local base;
2. PPP/static processing;
3. receiver in-device PPP solution;
4. long averaging of non-PPP in-device solution as fallback;
5. receiver survey-in where available and explicitly understood;
6. manual known point or external `base-position.json`.

Long averaging is fallback evidence. It is not equivalent to static RTK or PPP.
It should carry duration and uncertainty and should be used only when better
methods are unavailable.

The temporary-base dashboard can help capture an accepted coordinate in the
field. Use `Avg` only while the receiver is stationary and the fix type is
stable. Use `Base` only after deciding the shown or averaged coordinate is good
enough for the intended work; if no `MODE BASE` command profile exists, create
or select one in Menu > Init/shutdown scripts before starting fixed-base
operation.

The app performs a start-time sanity check: a rover workflow must not run a
receiver command profile that sends `MODE BASE`. Temporary-base and other
base-style recordings may still use rover-mode receiver commands when the
purpose is to derive the base coordinate from another base, PPP or later
processing.

## Fixed Base From Accepted Coordinate

Use this only after a base-position candidate has been accepted or an external
known coordinate has been imported.

User flow:

1. Select a receiver profile that supports fixed-base mode.
2. Enter the accepted coordinate manually or paste an imported
   `base-position.json`.
3. Verify frame/datum, epoch, antenna height and antenna reference point.
4. Start fixed-base operation.
5. Optionally select an NTRIP caster-upload profile if downstream publication
   is needed.
6. Record base status and RTCM output/extracted RTCM where supported.

When caster upload is enabled, the selected command profile must emit minimum
RTCM base data. The app rejects upload start if the command script has no
base-position RTCM message or no MSM observation message. During recording,
valid RTCM extracted from receiver RX is written to `base-caster-upload.rtcm3`
and uploaded through a bounded background uploader. Caster outage or
authentication failure degrades only upload; `receiver-rx.raw` recording
continues.

A fixed base must not start directly from a temporary-base recording. The base
coordinate must be accepted first. The V1 UI rejects starts where manual
coordinates and imported `base-position.json` are both supplied.

## Replay/Test

Use replay/test sessions for deterministic parser, quality-monitor and workflow
tests. Replay does not require Android foreground-service or wake-lock semantics
because it is not live hardware recording.

## Settings Backup

RtkCollector can export a user-initiated settings backup for transferring
profiles between phones. The backup contains receiver command profiles, USB/baud
profiles, NTRIP caster and mountpoint profiles, recording outputs, storage
profiles, settings sets and active selections.

NTRIP passwords are excluded by default. The export dialog may include a
separate checkbox to include plaintext passwords. That option is intended only
for trusted phone-to-phone transfer and the resulting file must be handled as a
secret.

## Live Stream Diagnostics

The main dashboard may show compact UM980 receive-frequency diagnostics such as
`Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 20/1/1/1/1/4 Hz`. These
are observed message rates from the incoming receiver stream, not merely the
configured receiver output periods. A `-` means the app has not seen that message
type in the current window.

The UM980 mode shown on the dashboard is receiver-reported if such evidence is
available. Otherwise it is labelled as the mode commanded by the selected init
script.

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
