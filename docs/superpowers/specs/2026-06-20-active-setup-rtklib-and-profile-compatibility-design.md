# Active Setup, RTKLIB, And Profile Compatibility Design

## Purpose

RtkCollector now has enough independent profile types that the relationship
between them must be made explicit. The user must be able to prepare reusable
field setups, make quick field-time changes, and still understand exactly what
will be activated when recording starts.

This design specifies:

- active settings-set visibility and temporary override semantics;
- per-option settings-set policy;
- receiver-family compatibility for command/init profiles and RTKLIB routes;
- RTKLIB and in-device solution coexistence;
- mock-provider source selection;
- profile actions, grouping and ordering;
- consistent profile naming;
- menu text-field and dashboard layout consistency.

## Current Problems

The current model has useful primitives but exposes them inconsistently:

- `RecordingSettingsSet` already carries references to command, USB/baud,
  NTRIP, RTKLIB, recording-output and storage profiles.
- `RtklibProfile` exists and is passed into `ActiveRecordingConfig`, but no
  visible menu lets the user choose or edit RTKLIB profiles.
- Real exported settings in
  `samples/rtkcollector-settings-1781935653886.json` show mismatches such as a
  settings set with `receiverProfileId = "um980-n4"` referencing a u-blox M8T
  command profile.
- Main-screen quick selectors change effective runtime behaviour, but the UI
  does not clearly show whether those changes are temporary overrides or saved
  settings-set changes.
- Profile list actions do not consistently separate activation from editing.
- Profile names use inconsistent raw/status shorthand, which does not clearly
  distinguish solution-output rate from raw-observation rate.

## Goals

- Keep the main dashboard simple.
- Make the active settings set visible and selectable at the top of the main
  menu/settings screen.
- Treat main-screen quick changes as remembered temporary overrides, not
  automatic profile/settings-set edits.
- Make settings-set defaults, locks and required user selections explicit per
  major option.
- Let every profile remain editable somewhere, while allowing activation only
  when compatible with the current active setup.
- Keep device solution and RTKLIB solution separate in session outputs and
  dashboard monitoring.
- Add automatic best-solution arbitration with manual override for mock output
  and solution use.
- Keep RTKLIB advisory and isolated from raw capture.
- Make text-field editing consistent for hardware keyboards.
- Adapt dashboard columns by usable `dp`, text scale and actual content fit.

## Non-Goals

- Do not implement maps, GIS, shapefiles or feature collection.
- Do not merge receiver and RTKLIB solution files.
- Do not make RTKLIB required for ordinary recording, NTRIP-to-receiver or
  temporary-base workflows.
- Do not auto-mutate imported settings to hide incompatibilities.
- Do not restrict expert baud-rate selection beyond hard validity checks.

## Concepts

### Settings Set

A settings set is a reusable template for a field situation. It stores defaults
for workflow, receiver family, init/command profile, USB/baud profile, NTRIP
profiles, RTKLIB profile, solution/mock policy, recording outputs, storage,
base coordinate and caster upload.

### Active Setup

The active setup is:

```text
selected settings set
  + remembered temporary overrides
  + per-run transient choices for "ask every time" fields
```

The active setup is what Start validates and records into `session.json`.

### Remembered Temporary Overrides

Main-screen quick selectors create remembered temporary overrides. They survive
app restart until changed, reset, saved into the settings set, or saved as a new
settings set.

They must not silently edit the saved settings set.

### Per-Option Policy

Each major settings-set option has a compact policy:

| Policy | Meaning | Quick selector behaviour |
| --- | --- | --- |
| `DEFAULT_OVERRIDABLE` | Settings set provides a default. | User may override; override is remembered. |
| `LOCKED` | Settings set provides a value that defines the setup. | User cannot quick-change; edit settings set to change. |
| `CHOOSE_ONCE_REMEMBER` | Settings set intentionally leaves value empty. | User must choose; choice is remembered until changed or reset. |
| `ASK_EVERY_TIME` | Settings set intentionally leaves value empty for each run. | User must choose for each recording run; value is cleared after recording stops or start fails. |

`ASK_EVERY_TIME` values remain visible during the run that uses them. They clear
after the run ends so the next Start requires a fresh selection.

Policies apply to:

- workflow;
- receiver/init command profile;
- USB/baud profile;
- NTRIP caster profile;
- NTRIP mountpoint profile;
- NTRIP caster-upload profile;
- RTKLIB profile;
- solution/mock policy;
- recording-output profile;
- storage profile;
- base coordinate profile.

## Menu Structure

The main dashboard remains simple. It does not show or edit full settings-set
structure.

The main menu/settings screen starts with an **Active settings set** section.
This section is not a deep profile editor. It is the operational control for
what will be used at Start.

It shows:

- active settings set name;
- state: `Clean`, `Modified`, `Invalid`, or `Missing selections`;
- workflow;
- receiver family;
- init/command profile;
- USB/baud profile;
- NTRIP caster and mountpoint;
- RTKLIB profile;
- solution/mock policy;
- recording-output profile;
- storage profile;
- base coordinate and caster-upload state when relevant.

Actions:

- activate another settings set;
- reset overrides;
- save overrides into active settings set;
- save current active setup as a new settings set;
- open full settings-set editor.

Detailed profile editing remains under profile-management sections.

## Profile Actions

Activation and editing are different operations.

- `Activate` uses an item in the active setup. It is compatibility-checked.
- `Edit` modifies a saved user profile definition. It does not activate it.
- `View` inspects a built-in read-only profile.
- `Rename` changes a user profile name without opening the full editor.
- `Copy` duplicates a profile as an editable user profile.
- `Delete` deletes user profiles only, with confirmation.

Built-in profiles show `View` and `Copy`, not disabled `Edit` buttons.

Incompatible profiles can still be viewed, edited, renamed, copied and deleted
where allowed. They cannot be activated.

## Profile Grouping And Ordering

Every profile list supports groups:

- each profile has a group label and order within group;
- each group has order in the profile type;
- users may move profiles up/down within group;
- users may move profiles to another group;
- users may move groups up/down;
- users may create, rename and delete empty user groups;
- built-in/default groups cannot be deleted, but may be collapsed.

Activation dialogs preserve grouping but clearly mark or filter incompatible
profiles.

Default command-profile groups:

- `UM980 / N4`;
- `u-blox M8T`;
- `u-blox M8P`;
- `Generic NMEA/RTCM`;
- `User experiments`.

Default RTKLIB groups:

- `Disabled`;
- `Rover`;
- `Temporary base`;
- `Advanced / experimental`.

## Receiver Compatibility

Receiver family is the primary compatibility anchor for command/init profiles
and RTKLIB route selection.

Rules:

- all profiles are editable;
- command/init profiles are activatable only when their `receiverFamily`
  matches the active receiver family;
- a user may edit a command profile and change receiver family; after that the
  profile belongs to the new family for activation purposes;
- RTKLIB profiles are activatable only when their route requirements can be
  satisfied by the active receiver family, workflow and command profile output;
- imported mismatches are allowed into storage but shown invalid until fixed;
- Start uses the same validation rules as the red/invalid UI state.

USB/baud profiles remain independent. The UI shows guidance:

- `Recommended`;
- `Known working`;
- `Untested`;
- `Unusual`.

Hard-invalid baud values remain rejected by model validation.

## RTKLIB And In-Device Solutions

Device solution and RTKLIB solution are independent solution engines.

Session outputs stay separate:

- `receiver-solution.nmea`;
- `receiver-solution.jsonl`;
- `rtklib-solution.nmea`;
- `rtklib-solution.pos`;
- `rtklib-status.jsonl`.

The dashboard shows:

- device solution tile;
- RTKLIB tile when RTKLIB is enabled;
- best/mock source status.

RTKLIB must not replace, mutate or suppress receiver solution outputs.

RTKLIB route validation is receiver-dependent:

- u-blox RAWX/SFRBX routes directly to RTKLIB-EX `input_ubx`;
- UM980 OBSVMB routes directly to RTKLIB-EX `input_unicore`;
- UM980 OBSVMCMPB requires the named converter/shim or explicit RTKLIB-EX
  decoder support;
- NTRIP correction RTCM3 routes to RTKLIB-EX `input_rtcm3`;
- NMEA, BESTNAV, ADRNAV, PPPNAV and NAV-PVT are monitoring/solution outputs,
  not RTKLIB raw-observation input.

## Best Solution And Mock Provider Policy

Automatic mode ranks fresh solution candidates and selects the best available
source. Manual override can force a source.

Policy values:

- `AUTO_BEST`;
- `DEVICE_INTERNAL_ONLY`;
- `RTKLIB_ONLY`;
- `OFF`.

Default policy:

- UM980: `AUTO_BEST` with device `BESTNAV/BESTNAVB` preferred unless the user
  explicitly selects RTKLIB-only;
- u-blox M8T with RTKLIB enabled: `AUTO_BEST` may select RTKLIB when it has a
  better fresh solution;
- generic NMEA: device/generic solution only unless RTKLIB route is valid.

If a strict manual source is stale or unavailable, mock output reports stale/no
publish instead of silently changing source. `AUTO_BEST` may fall back according
to ranking rules.

The mock-provider dashboard status must show:

- policy;
- actual published source;
- last update interval;
- stale/not-permitted/failure state.

## Naming Convention

Command/init profile names use this grammar:

```text
<device> <solution-output> <raw-output> <features>
```

Examples:

- `UM980 20 Hz BESTNAVB 4 Hz OBSVMCMPB RTK+PPP`;
- `UM980 20 Hz BESTNAVB 4 Hz OBSVMB RTKLIB RTK+PPP`;
- `UM980 1 Hz NMEA no raw RTK+PPP`;
- `UM980 fixed base 1 Hz RTCM+OBSVMCMPB`;
- `u-blox M8T 1 Hz NAV-PVT 1 Hz RAWX`;
- `u-blox M8T 5 Hz NAV-PVT 5 Hz RAWX RTKLIB`;
- `u-blox M8T 1 Hz NAV-PVT 1 Hz RAWX mock-ready`.

Raw/status shorthand is avoided because it does not distinguish position
solution output from raw observation output.

Profile details should show message-specific output:

- `NAV-PVT: 1 Hz`;
- `RXM-RAWX: 1 Hz`;
- `RXM-SFRBX: enabled, burst/event`;
- `TIM-TM2: enabled/disabled`;
- `NAV-SAT: enabled/disabled`;
- UM980 equivalents such as `BESTNAVB`, `OBSVMB`, `OBSVMCMPB`, `PPPNAVB`,
  `ADRNAVB`, `RTKSTATUSB`, `RTCMSTATUSB`.

## Text Field Consistency

All menu text fields use one shared field implementation:

- thin border;
- small label over the top-left border;
- no filled purple background;
- hardware-keyboard arrow keys and modifier-arrow keys stay inside the field;
- Tab and Shift+Tab move between fields;
- cursor/selection position is restored when returning to a field;
- multiline and single-line fields follow the same visual rules.

This applies to all profile editors, not only init/shutdown scripts.

## Adaptive Dashboard Layout

Dashboard layout uses:

- available width and height in `dp`;
- orientation;
- system insets;
- font scale;
- actual text truncation and card overflow measurements.

Initial heuristics may try two columns on large portrait screens, but measured
content fit is authoritative. If critical tile content truncates or overflows,
the monitoring area falls back to single column or a roomier layout.

Start/Stop, Menu and core controls remain fixed and visible.

Layout measurement is UI-only. It must not affect recording, parser, RTKLIB or
mock-provider paths.

## Start Validation

Start validates the effective active setup:

1. Apply settings-set defaults and policies.
2. Apply remembered temporary overrides only where policy allows.
3. Apply per-run transient choices for `ASK_EVERY_TIME` options.
4. Validate missing required fields.
5. Validate receiver-family compatibility.
6. Validate command profile/workflow safety, including rover/base mode sanity.
7. Validate RTKLIB route if RTKLIB is enabled.
8. Validate NTRIP and caster-upload requirements when selected workflow uses
   them.
9. Validate storage availability.

If validation fails, Start is blocked with a concrete user-facing reason.

## Documentation Updates Required

Implementation must update:

- `docs/specification/ui-requirements.md`;
- `docs/specification/workflows.md`;
- `docs/specification/receiver-behaviour.md`;
- `docs/specification/session-artifacts.md`;
- `docs/specification/verification-matrix.md`;
- `docs/user-workflows.md`;
- `docs/contributor-onboarding.md`;
- `AGENTS.md` only if new durable guardrails are learned during implementation.

## Test Strategy

Automated tests should cover:

- settings-set policy resolution;
- remembered override application and reset;
- `ASK_EVERY_TIME` clearing after stop or failed start;
- profile compatibility filtering and start blocking;
- RTKLIB profile selection and route validation;
- automatic and manual solution-source selection;
- mock-provider source policy;
- profile action labels and grouping/order models;
- naming/migration of built-in profiles;
- dashboard layout fallback model where it can be tested without full emulator
  rendering.

Manual tests should cover:

- menu Active settings set selector;
- profile activation vs edit/rename/copy/delete;
- hardware-keyboard text-field behaviour;
- phone portrait and tablet portrait/landscape dashboard fit;
- UM980 and u-blox M8T profile compatibility;
- RTKLIB-enabled session on a host/device where native RTKLIB-EX can be built.
