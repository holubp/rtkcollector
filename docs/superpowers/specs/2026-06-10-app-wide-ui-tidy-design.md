# App-Wide UI Tidy Design

## Purpose

RtkCollector needs an app-wide UI foundation that is tidy, compact, readable
and self-documented. The dashboard and settings screens must look like one
carefully designed field instrument, not a collection of unrelated prototype
forms.

This design covers:

- the default monitoring dashboard;
- a selectable alternate dashboard layout;
- grouped settings menus;
- profile list and editor screens;
- help overlays;
- unsaved-change handling;
- keyboard-friendly text-field behaviour.

It does not change receiver recording, NTRIP networking, UM980 protocol logic,
session writing, workflow validation, maps, GIS, shapefile support or RTKLIB
integration.

## Visual Direction

The approved primary direction is the **Compact Instrument Panel** mockup.

Characteristics:

- calm light surfaces;
- compact cards;
- thin grey or dashed separators;
- stable value slots;
- strong typographic hierarchy;
- larger text for major live values;
- smaller text for secondary telemetry;
- fixed top and bottom controls;
- vertically scrollable middle content;
- no normal horizontal scrolling.

The UI should feel meticulous and dense, but not cramped. Whitespace must be
intentional: it separates groups and scan paths rather than leaving arbitrary
gaps.

## Dashboard Layouts

### Default: Compact Instrument Panel

The default dashboard is the compact card layout.

Structure:

- frozen top app bar;
- compact status strip;
- scrollable middle dashboard;
- frozen bottom action bar.

The top app bar contains:

- `RtkCollector`;
- recording state;
- compact workflow or receiver hint where space allows;
- `Menu`.

The status strip contains:

- settings set;
- workflow;
- mountpoint;
- receiver;
- storage.

Missing required selections use a pale red background with dark red text.
Selected/active values use the normal compact chip style. The strip must remain
compact and wrap cleanly on narrow screens.

The dashboard cards are:

- Position;
- Fix;
- NTRIP;
- Files;
- Profiles or Session.

Each card has:

- a small uppercase title;
- an optional `(i)` help icon;
- one major value in larger text;
- secondary rows in smaller text;
- grey/dashed separators between logical groups;
- stable row positions for unavailable values.

Unavailable values display as `n/a`; fields must not disappear and cause the
dashboard to jump.

The bottom action bar contains the primary recording action and critical runtime
actions. `Start`/`Stop` must never be inside the scrollable middle content.

### Selectable Alternate: Rail Layout

The alternate dashboard layout is selectable in app settings.

It uses the same dashboard state and the same telemetry fields as the compact
layout. It only rearranges them:

- setup/status controls appear in a persistent rail;
- telemetry cards appear beside the rail;
- the layout is most useful for landscape and tablet displays.

The rail layout must not become a different feature set. Any field shown in the
compact layout must remain available in the rail layout.

### Future: Floating/Minimized Layout

The third mockup direction, a hero status plus small cards, is documented as a
future floating or minimized-window layout. It is not part of this
implementation pass.

## Dashboard Information Inventory

The tidy-up must not remove GNSS information that is already shown or required.

Position card:

- latitude and longitude;
- ellipsoidal height;
- altitude;
- UTC date/time;
- latitude error;
- longitude error.

Fix card:

- interpreted fix type;
- satellites used and satellites in view, labelled as `Sats used/view`;
- PDOP;
- HDOP;
- VDOP;
- horizontal accuracy;
- vertical accuracy;
- differential age;
- baseline length;
- PPP status;
- RTKLIB status placeholder.

NTRIP card:

- URL without secrets;
- connection status;
- total bytes transferred;
- reference station ID;
- base station latitude/longitude;
- inbound NTRIP rate;
- bytes/rate sent to rover;
- base distance where available.

Files card:

- session location or storage profile;
- receiver RX raw byte count;
- app TX-to-receiver byte count;
- correction/NTRIP input byte count;
- NMEA export byte count;
- ZIP/share state.

Profiles/session card:

- settings set;
- command script profile;
- USB/baud profile;
- NTRIP caster profile;
- mountpoint profile where useful;
- recording output profile;
- storage location profile.

## Responsive Behaviour

The dashboard must support phones, tablets, portrait, landscape and unusual
aspect ratios.

Rules:

- Use font hierarchy before dropping information.
- Major values remain readable on small screens.
- Secondary telemetry may be smaller, but must stay legible.
- If the display is too small, the middle dashboard scrolls vertically.
- Top and bottom controls remain frozen.
- Layout reflows between one, two and three columns based on available width.
- Normal operation must not require horizontal scrolling.
- Live value updates must not resize cards enough to make the screen jump.

The implementation should use stable dimensions, row heights, max line counts
and ellipsis where needed.

## Settings Hub

The settings hub is grouped by user mental model, not as one flat button list.

Groups:

### Session Setup

- Settings sets;
- workflow selection/defaults;
- recording outputs;
- storage location profiles.

### Receiver And USB

- USB device and baud profile;
- command scripts;
- receiver family/profile selection.

### Corrections

- NTRIP casters;
- NTRIP mountpoints.

### Sessions

- recent/current sessions;
- file sharing/export actions.

Group boundaries use subtle surfaces, headings and thin/dashed separators. The
screen uses a frozen top area with title and back/menu action where applicable;
the grouped menu body scrolls underneath.

## Profile Lists

Profile lists use one consistent row design.

Each row shows:

- profile name;
- compact summary;
- built-in/editable state where relevant;
- active state where relevant;
- grouped row actions.

Active profiles are marked by:

- pale green row background;
- dark green `Active` label.

Active state must not be presented as a disabled or no-op button.

Common actions:

- Add;
- Use/select;
- Edit;
- Copy;
- Rename;
- Delete.

Actions must be visually grouped and consistently ordered. Destructive actions
use subdued grey or red styling depending on risk.

Rename opens a small dialog with:

- editable profile name;
- Save;
- Discard;
- Cancel.

Renaming should not require opening the full editor.

## Profile Editors

Editor screens use a frozen top action bar.

Top bar:

- Back;
- screen title;
- Save as a green check or clearly labelled action;
- Discard as a red X or clearly labelled action;
- Delete as a grey trash action where allowed.

The editor form scrolls underneath the frozen top bar.

Form rules:

- labels stay visually attached to fields;
- empty text fields remain empty;
- meaning belongs in the label, not placeholder text;
- long scripts use monospaced multiline fields;
- dropdown values are not free-text fields;
- booleans are checkboxes or toggles;
- receiver family is a list, not editable text;
- baud rates are selected from the valid supported list;
- profile references are selected from lists, not typed as IDs.

For command scripts:

- `Runtime script` is renamed to `Init script`;
- the old separate current-init-script wording is removed;
- `Shutdown script` is a label above an empty-capable field;
- an empty shutdown script field must be visually empty.

## Help Overlays

Small `(i)` icons appear beside labels or card titles where user help is useful.

Help behaviour:

- opens a small overlay/dialog;
- explains the local field in practical language;
- can be dismissed by tapping outside, tapping X, or Back;
- does not navigate away from the current screen;
- is concise and field-specific.

Examples:

- `Sats used/view`: used satellites in the receiver solution versus visible
  satellites reported by GSV/STADOP-style telemetry.
- `Ell. height`: height above the ellipsoid, distinct from orthometric altitude.
- `TX to receiver`: app-transmitted bytes sent toward the receiver serial input.
- `NTRIP URL`: host, port and mountpoint without password or token.

## Back And Unsaved Changes

Back navigation must behave like normal Android navigation:

- Back from a specific editor returns to the profile list or settings group;
- Back from settings returns to the dashboard;
- Back should not unexpectedly dismiss the app while there is a previous screen
  in the app navigation stack.

When a screen has unsaved changes, Back opens a dialog:

- Save;
- Discard;
- Cancel.

Save persists changes and leaves the screen. Discard leaves without saving.
Cancel closes the dialog and keeps the user on the editor.

The same logic applies to hardware Back, on-screen Back and keyboard Back/Escape
where available.

## Keyboard Behaviour

Text fields must preserve normal hardware-keyboard editing behaviour:

- arrow keys move within fields;
- Ctrl+arrow moves by words where supported by the platform;
- Ctrl+A selects all;
- Ctrl+C copies;
- Ctrl+V pastes;
- Ctrl+X cuts;
- Tab moves to the next field;
- Shift+Tab moves to the previous field.

The implementation should avoid custom key handlers that break platform text
editing unless a field explicitly requires special behaviour.

## Implementation Quality Bar

The implementation must be checked visually against the approved mockups.

Acceptance criteria:

- dashboard cards align consistently;
- status strip chips align and wrap cleanly;
- row labels and values do not drift apart;
- settings menus are grouped and not a flat button wall;
- editor labels are attached to fields;
- empty fields do not show misleading placeholder values;
- frozen controls remain visible on small and wide screens;
- no card resizing or major layout jumping during live telemetry updates;
- all current dashboard information remains available;
- compact and rail layouts are selectable;
- C is documented but not implemented as a normal layout.

Testing should include:

- Compose previews where practical;
- at least one portrait narrow preview/state;
- at least one landscape/tablet-style preview/state;
- focused model tests for any new UI state selection logic;
- manual emulator or device smoke test when Android tooling is available.

## Out Of Scope

This UI tidy pass does not implement:

- new receiver communication features;
- new NTRIP protocol behaviour;
- new UM980 parsing fields;
- RTKLIB integration;
- PPP/static solving on Android;
- maps;
- shapefiles;
- GIS editing;
- field-feature collection.
