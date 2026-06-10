# App-Wide UI Tidy Design

## Purpose

RtkCollector needs a cleaner Android UI foundation that feels like a precise
field instrument: compact, orderly, self-documented and careful about small
details. The current app already has the core recording and dashboard concepts,
but the visual structure still feels like a prototype: dashboard cards are not
compact enough, menus are flat, labels and controls are not consistently
grouped, and profile editors do not yet share one polished interaction model.

This clean-slate design replaces the earlier UI tidy draft. The approved mockup
is stored locally under the Superpowers brainstorming artifacts for this design
session, but the requirements below are the durable source of truth.

This work is UI-only. It must not change receiver recording, NTRIP protocol
logic, UM980 protocol handling, session writing, workflow validation, RTKLIB
integration, maps, shapefiles, GIS editing or feature collection.

## Design Principles

- Keep the app useful as a GNSS field instrument, not a decorative dashboard.
- Preserve the current telemetry inventory; visual tidying must not hide key
  GNSS fields.
- Use a clear scan order: setup context first, live observables second,
  recording controls always reachable.
- Use larger typography for primary values and smaller typography for secondary
  values.
- Use subtle lines, dashed separators and compact sections to show grouping.
- Avoid random whitespace. Space should separate logical groups.
- Prefer lists, dropdowns, toggles and checkboxes over typo-prone text fields.
- Keep help local, concise and dismissible.

## Top-Level Navigation

The app keeps a simple top-level structure:

- Home dashboard;
- Settings;
- Profile lists and editors;
- Session browser/sharing.

Back navigation must feel native:

- Back from an editor returns to the profile list;
- Back from a profile list returns to Settings;
- Back from Settings returns to Home;
- Back must not close the app while there is still an app screen to return to.

The same behaviour applies to on-screen Back, Android Back, hardware keyboard
Back and Escape where available.

## Home Dashboard

### Default Layout

The default Home screen is a compact field dashboard.

It has:

- frozen top bar;
- compact setup strip;
- vertically scrollable middle dashboard;
- frozen bottom recording/action bar.

The top bar contains:

- app name;
- short context subtitle;
- recording state;
- Menu action.

The setup strip contains five compact tiles:

- Settings;
- Workflow;
- Mountpoint;
- Receiver;
- Storage.

Each tile shows a small uppercase label and a compact current value. Missing
required values use a pale red background with dark red text. Selected values
use normal neutral styling. Tiles are clickable selectors, not full editors.
Detailed configuration stays in Settings.

The middle dashboard scrolls vertically if the display is too small. Normal use
must not require horizontal scrolling.

The bottom bar contains:

- primary Start or Stop recording action;
- USB/access action where relevant;
- Mark action where relevant.

Start/Stop must never be inside the scrollable content.

### Dashboard Cards

The default dashboard has four primary cards:

- Position;
- Fix;
- Corrections;
- Recording.

Each card has:

- compact title row;
- optional `(i)` help icon;
- one large primary value;
- smaller secondary metric rows;
- dashed separators between logical groups;
- stable label/value rows.

Live updates must not cause card sizes to jump. The implementation should use
stable row counts, max lines, ellipsis and fixed label/value structure.

### Position Card

Primary value:

- latitude and longitude.

Secondary values:

- UTC;
- ellipsoidal height;
- altitude;
- latitude error;
- longitude error.

### Fix Card

Primary value:

- interpreted fix type.

Secondary values:

- `Sats used/view`;
- PDOP;
- HDOP;
- VDOP;
- horizontal accuracy;
- vertical accuracy;
- differential age;
- baseline length;
- PPP status;
- RTKLIB status placeholder.

`Sats used/view` means receiver-solution satellites used followed by visible
satellites in view. This wording must appear in the UI so the order is not
ambiguous.

### Corrections Card

Primary value:

- NTRIP/correction state, for example `Streaming`, `Configured`, `Off`,
  `Auth error` or `Reconnect wait`.

Secondary values:

- caster host and port;
- mountpoint;
- reference station ID;
- base position or base distance where known;
- inbound NTRIP rate;
- rate/bytes sent to receiver;
- total correction bytes for the session.

Secrets must never be shown on the dashboard.

### Recording Card

Primary value:

- current session state or session location summary.

Secondary values:

- `receiver-rx.raw` byte count;
- `tx-to-receiver.raw` byte count;
- `correction-input.raw` byte count;
- NMEA export byte count;
- ZIP/share state.

ZIP should be shown as unavailable before a recording exists and as available
only when a shareable archive can be generated or has been generated.

## Alternate Dashboard Layout

The app also provides a selectable wide/rail layout.

The rail layout:

- uses the same dashboard state as the default layout;
- preserves all fields from the default dashboard;
- places workflow, mountpoint, receiver and storage context in a left rail;
- places telemetry cards beside the rail;
- is intended for landscape screens, tablets and users who prefer persistent
  setup context.

The rail layout must not fork business logic or hide fields. It is an
arrangement preference only.

## Settings Hub

The Settings screen is grouped, not a flat list of buttons.

Groups:

### Session Setup

- Settings sets;
- Workflow selection;
- Recording outputs;
- Storage location profiles.

### Receiver And USB

- USB device and baud;
- Command scripts;
- Receiver family/profile.

### Corrections

- NTRIP casters;
- NTRIP mountpoints.

### Sessions

- Recent sessions;
- Sharing/export actions.

Each group uses a subtle surface, a small uppercase group label and row
separators. Rows may use simple icons to improve scanning, but icons must not
replace text labels.

The Settings screen uses a frozen top bar with Back, title and Help where
appropriate. The grouped menu body scrolls underneath.

## Profile Lists

All profile lists share one visual and interaction model.

Rows show:

- profile name;
- compact profile summary;
- active state where relevant;
- built-in/editable status where relevant;
- grouped actions.

Active rows use:

- pale green background;
- dark green `Active` label.

Active state is not a disabled button and not a no-op action.

Common actions:

- Add;
- Use/select;
- Edit;
- Copy;
- Rename;
- Delete.

Actions are grouped in a stable order. Delete should be visually subdued unless
the current screen is explicitly confirming deletion.

Rename is a lightweight dialog, not a full editor screen. It contains:

- editable name field;
- Save;
- Discard;
- Cancel.

## Profile Editors

Profile editors use a frozen top action bar and a scrollable form body.

Top action bar:

- Back;
- screen title;
- Save action;
- Discard action;
- Delete action where allowed.

Save should use green positive styling. Discard should use red negative styling.
Delete should use grey/destructive styling and require confirmation where data
loss is possible.

Form rules:

- labels are visually attached to fields;
- empty fields remain empty;
- labels carry meaning, not placeholder text;
- long command scripts use monospaced multiline fields;
- receiver family is selected from a list;
- baud rates are selected from the supported baud list;
- profile references are selected from lists;
- booleans use checkboxes or switches;
- mountpoints are selected from retrieved lists or typed only in fields meant
  for direct mountpoint entry.

Command script editor specifics:

- `Runtime script` is named `Init script`;
- `Shutdown script` appears as a label above the shutdown script field;
- an empty shutdown script field is visually empty;
- the receiver family field is not free text.

## Help Overlays

Small `(i)` icons may appear on dashboard cards and important form labels.

Help overlays:

- open as small local dialogs;
- explain one field or card in practical language;
- are dismissible by outside tap, X, Back or Escape;
- do not navigate away from the current screen.

Example help text:

- `Sats used/view`: used is the number of satellites in the receiver solution;
  view is the aggregate visible-satellite count reported by telemetry such as
  GSV or STADOP.
- `Ellipsoidal height`: height above the reference ellipsoid, distinct from
  orthometric altitude.
- `TX to receiver`: bytes transmitted by the app toward the receiver serial
  input.
- `NTRIP URL`: caster host, port and mountpoint without password or token.

## Unsaved Changes

Any editor with unsaved changes intercepts Back.

The unsaved-change dialog offers:

- Save;
- Discard;
- Cancel.

Save persists changes and leaves. Discard leaves without saving. Cancel closes
the dialog and keeps the user on the editor.

## Hardware Keyboard Behaviour

The app should preserve normal Android text editing behaviour:

- arrow keys move inside text fields;
- Ctrl+arrow moves by words where supported;
- Ctrl+A selects all;
- Ctrl+C copies;
- Ctrl+V pastes;
- Ctrl+X cuts;
- Tab moves to the next field;
- Shift+Tab moves to the previous field.

The implementation should avoid custom key handling that breaks standard text
field behaviour.

## Responsive Behaviour

The UI must support portrait phones, landscape phones, tablets and unusual
aspect ratios.

Rules:

- Use one dashboard column on narrow screens.
- Use two dashboard columns on medium screens.
- Use rail or multi-column layout on wide screens when selected.
- Top and bottom controls stay visible.
- Middle content scrolls vertically.
- Text uses hierarchy rather than uniformly shrinking everything.
- No normal screen should require horizontal scrolling.
- Values may ellipsize where needed, but labels and units must remain clear.

## Visual Acceptance Criteria

The implementation is acceptable only if it matches the intent of the approved
clean-slate mockup:

- main dashboard looks compact and aligned;
- major telemetry values are visually dominant;
- secondary telemetry remains visible and readable;
- setup tiles form a coherent strip, not scattered chips;
- cards have consistent widths, row spacing and separators;
- live telemetry updates do not visibly resize the layout;
- settings are grouped by session setup, receiver/USB, corrections and
  sessions;
- profile list rows have consistent actions and active labels;
- editors keep Save/Discard/Delete visible while form content scrolls;
- empty fields do not show misleading placeholder text;
- help overlays are local and dismissible.

## Testing And Review

Implementation should include:

- Compose previews or preview-like states for the default dashboard;
- a wide/rail layout preview or test state;
- focused tests for any new layout-preference model;
- focused tests for any new unsaved-change state model;
- manual visual review on a narrow portrait viewport;
- manual visual review on a wide/landscape viewport;
- verification that existing dashboard telemetry fields are still represented.

## Out Of Scope

This UI pass does not implement:

- new receiver communication features;
- new NTRIP behaviour;
- new UM980 parsing fields;
- RTKLIB integration;
- PPP/static solving on Android;
- maps;
- shapefiles;
- GIS editing;
- feature collection.
