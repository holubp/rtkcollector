# Base Workflow, Upload Control And Profile Cleanup Design

## Purpose

Fix the fixed-base workflow so RtkCollector never silently changes the base
position sent to a receiver, make NTRIP source upload an explicit main-screen
choice, clean up confusing built-in settings, and make editable text fields
behave consistently across settings and profile screens.

This design is scoped to workflow correctness and user-visible setup clarity.
Satellite-monitor interpretation for GLONASS L3, Galileo L6 and BeiDou L5/L7
is deliberately deferred until paired base/rover recordings are available.

## Evidence

A fixed-base recording from
`samples/debug/session-2026-06-28T11-37-23.795Z-739e3826-3903-4a3e-8dbe-768f8789f70a-1782648286570.zip`
showed that the app did not send the manually edited profile command:

```text
MODE BASE 49.463759313 15.451254479 707.8
```

Instead, `tx-to-receiver.raw` showed:

```text
MODE BASE 49.4639217447 15.4515414490 711.9392
```

`session.json` and `base-position.json` showed that fixed-base start selected
the saved coordinate named `Temporary base average`, then replaced the profile's
`MODE BASE` line before sending mode commands. The receiver and dashboard then
correctly reflected the substituted coordinate.

The same recording and the Unicore N4 command manual also show that UM980
`BESTNAV` height is height above mean sea level and carries geoid undulation
separately. UM980 `MODE BASE` documents its third geodetic parameter as
`Altitude, in meters`. Therefore the app must not blindly write an ellipsoidal
height into UM980 `MODE BASE`; it must track the height semantics explicitly.

## Goals

- Remove silent fixed-base `MODE BASE` replacement.
- Provide a clear temporary-base to fixed-base workflow.
- Store and display both MSL altitude and ellipsoidal height when both are
  known.
- Use MSL altitude for UM980 `MODE BASE`; keep ellipsoidal height for
  dashboard, mock-location and location-quality semantics.
- Add a compact main-screen `Upload` selector with an explicit `Off` state.
- Ensure default rover settings do not accidentally enable NTRIP source upload.
- Simplify built-in settings/profile labels around practical scenarios.
- Make all settings/profile/base-coordinate text fields use consistent editing
  behaviour.
- Preserve byte-exact recording and existing receiver/NTRIP functionality.

## Non-Goals

- Do not implement GLONASS L3, Galileo L6 or BeiDou L5/L7 investigation in this
  pass.
- Do not make temporary/converging bases publish public corrections.
- Do not delete user-created settings sets or command profiles.
- Do not redesign the whole dashboard or replace the settings hub.
- Do not add maps, GIS, shapefile or field-feature collection features.

## Fixed-Base Workflow

Fixed-base start must be explicit about the coordinate and command profile used.
The start path must not silently replace a user-edited `MODE BASE` line because
a saved base coordinate is selected.

When transitioning from temporary base to fixed base, the UI offers two actions:

1. Create a new fixed-base profile.
2. Overwrite the selected editable profile.

Both actions must show:

- selected source coordinate name;
- coordinate method and frame/datum where available;
- latitude and longitude;
- MSL altitude used for UM980 `MODE BASE`;
- ellipsoidal height used for dashboard/location semantics;
- the exact generated `MODE BASE ...` line.

### Create New Profile

The user selects a source/template command profile. The app copies that profile
into an editable profile, writes or replaces exactly one explicit `MODE BASE`
line, and saves the new profile only after the user confirms the preview.

### Overwrite Selected Profile

Overwrite is allowed only for editable profiles. Protected built-ins remain
read-only and must require copying first.

If the selected profile already contains a `MODE BASE` line, the app previews
the before/after replacement. If no `MODE BASE` line exists, the app previews an
inserted line near the start of the mode/runtime script, after `UNLOG` and baud
setup commands when such commands are present.

## Height Semantics

The accepted-base coordinate model needs explicit height semantics:

- `mslAltitudeM`: height above geoid/mean sea level, used for UM980
  `MODE BASE`.
- `ellipsoidalHeightM`: height above the reference ellipsoid, used for
  dashboard, mock-location and generic geodetic display.
- `geoidSeparationM`: optional separation used to derive one height from the
  other when source telemetry provides it.

Existing `heightM` import/export fields must remain backward compatible. When a
legacy record has only `heightM`, the app must preserve the value but must not
pretend it knows both MSL and ellipsoidal height. Fixed-base profile generation
for UM980 is allowed only when MSL altitude is available directly or can be
derived from known ellipsoidal height plus geoid separation.

Manual coordinate entry should ask for MSL altitude and ellipsoidal height
separately where practical. If that is too dense for the compact flow, it must
at least label which height will be used for UM980 `MODE BASE` and show the
derived counterpart when available.

## Main-Screen Upload Control

Add `Upload` to the compact dashboard setup controls alongside `Workflow`,
`Mountpoint`, `Receiver` and `Storage`.

The tile value is:

- `Off` when no NTRIP source upload is selected/enabled;
- the upload profile name when upload is active;
- warning-styled when upload is selected for a workflow where it is unsafe or
  irrelevant.

Tapping `Upload` opens a picker with:

- `Off`;
- available NTRIP caster upload profiles;
- a shortcut to edit or copy upload profiles.

The existing `Caster upload` monitoring card remains shown anytime upload is
configured and enabled. It continues to show status, mountpoint, uploaded
volume, bitrate, RTCM total Hz, per-message Hz, safety status, dropped frames,
stop reason and connection/auth errors.

## Upload Safety

NTRIP source upload is a fixed-base publishing function. It must not be enabled
while a temporary base is still temporary, converging, averaging or internally
RTK-derived.

Temporary-base workflows may collect data and produce an accepted coordinate,
but publishing starts only after the user accepts the coordinate and switches to
explicit fixed-base operation.

RTK2go safety rules remain forced for RTK2go hosts. Other upload profiles keep
the safety rules switch user-settable; when disabled, the app warns but does
not force stopping solely because the host is not RTK2go.

Upload failure must remain advisory. It must not stop raw receiver recording
while USB transport and local storage continue to function.

## Built-In Settings And Profiles

Default and built-in settings should describe real user scenarios rather than
accumulated experiments.

UM980 built-ins should focus on:

- rover with NTRIP;
- rover plain or PPP-oriented recording;
- temporary base without upload;
- fixed base without upload;
- fixed base with explicit upload;
- RTKLIB-specific scenario only where required.

NTRIP source upload is Off by default unless the selected built-in explicitly
names a base-upload scenario.

u-blox built-ins should be reduced to a small user-facing set:

- raw rover recording;
- raw rover plus RTKLIB/NTRIP where applicable;
- status/mock profile only where it provides distinct value.

Labels and documentation must not imply tested precise-positioning support for
M8P or F9P. UM980 and M8T remain the primary supported devices; M8P/F9P may
share generic or raw modes but precise-positioning workflows are not tested by
the author.

Existing user-created profiles and settings sets are preserved. Migrations may
update same-id built-ins and repair unsafe built-in defaults, but must not
delete user copies.

## Text Editing Consistency

All user-editable single-line text fields in settings, profile and
base-coordinate flows must use the same editor behaviour:

- hardware arrow keys move inside the active field;
- Tab and Shift+Tab move between fields;
- cursor movement and selection remain predictable;
- password fields keep masking without losing navigation behaviour;
- read-only fields remain visually distinct and non-editable.

This applies to NTRIP host/user/mountpoint/password fields, upload profile
fields, base coordinate fields, settings-set names, USB profile fields, storage
labels and remaining one-line editable profile dialogs.

Device console input is outside this cleanup unless it is later found to share
the same broken settings/profile editing behaviour.

## Deferred Satellite-Monitor Work

The following questions are deferred:

- why GLONASS L3 appears differently between rover and base;
- why Galileo L6 is visible but not used;
- why BeiDou L5/L7 are visible but not used;
- whether differences come from RTCM signal availability, receiver tracking,
  RTK engine selection or monitor aggregation.

This later work requires paired base and rover recordings. It should compare
receiver telemetry, RTCM MSM signal masks and solution usage before changing
monitor display logic.

## Testing And Verification

Focused tests should cover:

- fixed-base start no longer mutates command scripts silently;
- generated fixed-base profile previews contain the expected `MODE BASE` line;
- protected built-ins cannot be overwritten;
- UM980 fixed-base generation rejects missing MSL altitude when it cannot be
  derived safely;
- legacy `heightM` import remains backward compatible;
- dashboard setup state shows Upload `Off` or selected profile correctly;
- rover/default settings do not enable source upload;
- text-field editor model preserves arrow-key and Tab semantics where it is
  reusable/testable.

Manual checks should cover:

- temporary-base to fixed-base create-new-profile flow;
- temporary-base to fixed-base overwrite-editable-profile flow;
- main-screen upload Off/Profile selection;
- start preflight warning when upload is selected for the wrong workflow;
- UM980 field smoke test confirming `tx-to-receiver.raw` contains the previewed
  `MODE BASE` command exactly.

## Reliability Constraints

No change in this plan may reduce recording reliability:

- raw receiver capture remains byte-exact and foreground-service owned;
- parser, dashboard, upload and profile-generation failures must not mutate
  `receiver-rx.raw`;
- NTRIP source upload continues to stream only RTCM3 frames extracted from the
  receiver stream;
- upload and parser errors are surfaced as state/events without stopping local
  recording unless the user explicitly stops recording.
