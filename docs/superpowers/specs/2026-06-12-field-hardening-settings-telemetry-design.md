# Field Hardening, Settings Transfer and UM980 Diagnostics Design

## Purpose

This design collects the field issues and small usability gaps reported after
the latest pushed build. The goal is to make RtkCollector fail loudly and
diagnostically when recording cannot start, keep dashboard controls usable on
phones with Android navigation bars, preserve the last useful NTRIP choice, and
add compact diagnostics that make UM980 stream health visible in the field.

This is not a redesign of receiver recording. The byte-exact receiver RX stream
remains authoritative. Parser, UI, NTRIP and diagnostic failures must not mutate
or stop raw capture while transport and storage still function.

## Scope

Implement these behaviours:

- comprehensive pre-start and start-time error checking for USB, workflow,
  storage, NTRIP requirements and serial open failures;
- dashboard safe-area handling so bottom controls are never covered by Android
  navigation buttons;
- portrait-safe latitude/longitude display that wraps to separate lines when
  needed;
- persistence of the last active NTRIP mountpoint profile across app restarts;
- settings backup export/import for transfer between phones, with an explicit
  option to include plaintext NTRIP passwords;
- compact UM980 receive-frequency diagnostics for BESTNAV, GGA, PPPNAV, ADRNAV,
  RTKSTATUS and OBSVM;
- UM980 mode display using receiver-reported mode if available, otherwise the
  last commanded mode parsed from the active command script.

Do not implement:

- RTKLIB real-time solution;
- new receiver command sequences beyond parsing the existing selected command
  profile and recording/displaying diagnostics;
- maps, GIS, shapefiles or field-feature collection;
- any storage of passwords in session metadata.

## Start And USB Error Handling

The current Huawei P30 Pro symptom is that USB permission is reported as already
granted, but pressing Start creates an empty session and then no visible error.
The app must distinguish these stages:

1. selected workflow exists and is startable;
2. selected USB/baud profile exists;
3. a matching connected USB device exists;
4. Android USB permission is granted;
5. a supported serial driver can be selected;
6. the device can be opened and its interface/endpoints claimed;
7. initial and target baud values are valid;
8. configured storage is writable;
9. NTRIP mountpoint is required only for workflows that actually use NTRIP;
10. a real session is created only after pre-recording checks have passed far
    enough that a recording attempt is meaningful.

If failure happens before receiver capture can begin, the service must not leave
an empty successful-looking session. It must publish a failed state containing a
category, severity and human-readable message, and the dashboard/notification
must show the message.

If NTRIP fails after recording starts, the recording continues. NTRIP errors are
degraded recording state, not raw recording failure.

## Dashboard Safe-Area And Stable Layout

The bottom action bar must apply Android navigation-bar safe-area padding so
Start/Stop, USB access, NTRIP and Mark remain clickable on devices with 3-button
navigation in portrait mode. The bar stays fixed outside scrollable content.

Position display must never truncate latitude or longitude. In portrait or
narrow tiles, display latitude and longitude as separate labelled lines. In
landscape or wide tiles, a single-line representation may be used only if both
values fit cleanly. The position tile must reserve stable dimensions so live
updates do not make the dashboard jump.

## Last Active NTRIP Mountpoint

Whenever the user selects a NTRIP mountpoint profile, the app stores the
selected profile id as the last active mountpoint.

On app startup:

- restore the saved mountpoint only if the profile still exists;
- display `Mountpoint: n/a` with the missing red state if the saved id is
  missing, blank or invalid;
- never restore raw placeholder text such as `a`;
- keep the remembered mountpoint when the current workflow does not use NTRIP,
  but do not connect to NTRIP unless the workflow requires it;
- allow an activated settings set to override the remembered mountpoint when it
  explicitly selects one;
- preserve the current/remembered mountpoint when a settings set says to leave
  that setting intact.

## Settings Backup Import And Export

Settings transfer between phones is a separate user-initiated backup operation,
not part of session export.

Export must include:

- command profiles;
- USB/baud profiles;
- NTRIP caster profiles;
- NTRIP mountpoint profiles;
- recording policy profiles;
- storage profiles;
- settings sets;
- selected settings set id;
- selected workflow id;
- selected/last active NTRIP mountpoint id;
- format version and export timestamp.

Passwords are excluded by default. The export UI may offer an explicit checkbox:
`Include plaintext NTRIP passwords`. When enabled, the UI must show a warning
that the exported file contains plaintext credentials. Plaintext passwords may
appear only in this explicit settings backup format. They must never appear in
`session.json`, event logs, quality logs, ordinary session ZIP sharing or
diagnostic text.

Import must validate the backup format, merge or replace profile collections as
defined by the import action, restore selected ids only if the referenced
profiles are present, and write imported passwords into `NtripSecretStore`.

## UM980 Receive-Frequency Diagnostics

The app must track actual received message rates over a sliding window. It must
not display configured rates as if they were observed.

Track these sources:

- `BESTNAV` from BESTNAVA/BESTNAVB;
- `GGA` from NMEA GGA;
- `PPPNAV` from PPPNAVA/PPPNAVB;
- `ADRNAV` from ADRNAVA/ADRNAVB;
- `RTKSTATUS` from RTKSTATUSA/RTKSTATUSB;
- `OBSVM` from OBSVMCMPB.

Display compactly, for example:

`Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM 20/1/1/1/1/4 Hz`

If a source has not been observed in the current window, show `-` for that
source. This row belongs in a low-priority area of the dashboard tile content so
it can scroll away on small screens without hiding Start/Stop controls.

## UM980 Mode Display

The dashboard should show the UM980 operating mode where possible, for example
`ROVER SURVEY` or `ROVER AUTOMOTIVE`.

Preferred source order:

1. receiver-reported mode from a supported parsed UM980 response/log if
   available;
2. last commanded mode parsed from the active init/runtime command script.

If the value comes from the command script rather than receiver feedback, label
it as configured/commanded rather than reported. Do not invent unsupported UM980
query commands.

## Validation And Tests

Add focused tests for reusable logic:

- start preflight validation returns structured category/severity/messages;
- NTRIP requirement is enforced only for NTRIP workflows;
- invalid/missing USB profiles and devices produce specific errors;
- last mountpoint persistence restores only valid profile ids;
- settings backup export excludes passwords by default and includes them only
  when explicitly requested;
- imported passwords are routed to `NtripSecretStore` rather than session
  metadata;
- message-rate tracker reports observed Hz and `-` for absent sources;
- command-script mode parser extracts `MODE ROVER SURVEY`, `MODE ROVER
  AUTOMOTIVE` and plain `MODE ROVER`;
- dashboard position formatting wraps latitude/longitude for narrow layouts.

Manual validation on hardware must include:

- Huawei P30 Pro start attempt with UM980 connected: failure must identify the
  exact USB/open/storage stage if recording cannot start;
- Xiaomi 14T Pro and Huawei P30 Pro portrait mode: bottom controls must remain
  clickable above Android navigation buttons;
- UM980 binary profile recording: frequency row must show observed rates for
  the message types present in the stream.

## Documentation Updates

Update user-facing docs to describe:

- settings backup export/import and the plaintext password warning;
- meaning of receive-frequency diagnostics;
- distinction between receiver-reported and command-script UM980 mode;
- start failure behaviour and the rule that NTRIP failure does not stop raw
  recording.
