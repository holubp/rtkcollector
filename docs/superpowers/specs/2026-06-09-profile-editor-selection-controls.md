# Profile Editor Selection Controls

## Purpose

The profile UI must prevent typo-prone configuration. Whenever a value is a
reference to another profile, a known workflow mode, a boolean, a known baud
rate or a connected USB device, the UI must present a constrained selector
rather than a free-text field.

This is a usability and safety improvement only. It must not change raw capture,
receiver RX recording, NTRIP correction routing or foreground-service ownership.

## Dashboard Selectors

The dashboard tiles for Workflow, Mountpoint, Receiver and Storage open compact
overlay selectors over the dashboard. They must not navigate to full-screen
configuration menus.

Each selector lists only selectable options. The active option is shown with a
light green background and a dark green `Active` label. The active marker is not
a clickable no-op button.

Dashboard selectors:

- Workflow: select the active settings set or workflow mode configuration.
- Mountpoint: select an NTRIP mountpoint from the list retrieved for the active
  caster connection.
- Receiver: select the receiver command profile.
- Storage: select the storage location profile.

During active recording, only NTRIP mountpoint changes may apply live. Workflow,
receiver-command and storage changes remain blocked until recording stops.

## Profile Editor Controls

The generic profile editor should support typed fields:

- text;
- multiline text;
- password with Show/Hide;
- checkbox;
- dropdown selector;
- read-only list.

Profile references must use dropdown/list selectors populated from existing
profile stores. Workflow mode must use a fixed selector. Boolean values must use
checkboxes.

Initial workflow modes:

- Plain rover;
- Rover with NTRIP;
- Temporary base recording;
- Fixed base.

## NTRIP Profiles

NTRIP caster profiles keep editable connection fields and password storage, but
known mountpoints must be displayed as a read-only list. Users should refresh or
retrieve mountpoints from the caster connection rather than edit the cached list
manually.

NTRIP mountpoint profiles select their caster profile from the existing NTRIP
caster profiles. Mountpoints should be selectable from the retrieved caster list
for that caster. Direct text entry may remain only if no fetched list exists or
for advanced fallback use.

## Command Profiles

Command profile editing is simplified:

- remove the current separate `Init script` field;
- show the current runtime script as `Init script`;
- keep `Shutdown script` as a labelled multiline field;
- leave empty shutdown scripts visibly empty.

The stored model may keep legacy fields for compatibility, but the UI should no
longer expose a confusing separate pre-runtime init script.

## USB And Baud Profiles

Profile baud and host serial baud are selected from this fixed list:

`4800, 9600, 14400, 19200, 38400, 57600, 115200, 128000, 230400, 256000, 460800, 921600`

USB VID/PID/name/product should be represented as one USB device selector:

- connected Android USB devices appear first;
- remembered devices may appear below;
- remembered devices that are not currently connected should have an `X` remove
  action;
- the visible label should include VID:PID plus product/name where available.

Separate editable USB VID, PID, device name and product name fields should not
be shown to ordinary users.

## Testing

Add focused tests for:

- workflow mode options;
- profile-reference selector option generation;
- boolean fields represented as checkboxes;
- command profile editor field mapping;
- USB device selector label generation;
- active-row styling state model if represented outside Compose.

Android UI execution may still require host-side Android Studio or CI because
Termux resource linking for Android 36 is locally limited.
