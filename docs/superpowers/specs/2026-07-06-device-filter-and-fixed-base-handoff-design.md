# Device Filter And Fixed-Base Handoff Design

Date: 2026-07-06

## Purpose

RtkCollector now has enough settings sets and init/shutdown command profiles
that users need a clear way to focus profile lists on the receiver they are
using. The app also needs a less confusing temporary-base to fixed-base handoff:
after accepting a coordinate, the selected settings set and init/shutdown
profile must visibly become a fixed-base setup.

This design keeps advanced behaviour available through `Any`, while making the
default paths more guided and less error-prone.

## Device Filter

Add a persistent app-level `Device` preference with these values:

- `Any` as the default. This preserves current unfiltered behaviour for power
  users.
- `UM980`.
- `u-blox M8T`.

The main screen uses the compact label `Device`. Selector headers and settings
screens use `Device filter` when the filtering effect needs to be explicit.

Filtering applies to:

- Menu > Settings sets.
- Menu > Init/shutdown profiles.
- Home quick Settings selector.
- Home quick Receiver/init selector.
- Device console init-profile dropdown.
- Temporary-base to fixed-base init/shutdown profile selector.
- Temporary-base to fixed-base fixed-base settings-set selector.

Filtering does not apply to USB baud profiles, NTRIP
caster/mountpoint/upload profiles, recording outputs, storage, dashboard
layout, solution/mock policy or RTKLIB profiles. Those lists are either not
receiver-family lists, or their compatibility is already checked elsewhere.

Settings sets are matched by `receiverProfileId`. UM980 includes UM980,
Unicore and N4 identifiers. u-blox M8T includes M8T receiver identifiers.

Init/shutdown profiles are matched by `CommandProfile.receiverFamily`. UM980
includes UM980, Unicore and N4 receiver families. u-blox M8T includes M8T
receiver families.

The current active profile must not disappear silently when it is outside the
current filter. Affected selectors keep the active row visible with an
`Outside filter` subtitle. If a filtered list has no matches, show a clear
empty state such as:

`No UM980 settings sets. Switch Device to Any or create/copy a matching profile.`

## Home Screen Controls

Add a sixth compact setup control labelled `Device` to the main screen. This
balances the compact setup controls in portrait and landscape layouts.

The compact setup controls should be:

- `Settings`.
- `Workflow`.
- `Device`.
- `Receiver`.
- `Upload`.
- `Storage`.

`Device` opens the persistent device-filter picker. The Home quick Settings and
Receiver/init selectors respect that same filter and show `Device filter:
<value>` in their headers.

The upload control must be warning-coloured only when upload is relevant:

- Fixed base: warn if upload is expected but no upload profile is selected.
- Temporary base: warn only if the active command profile emits RTCM/base
  corrections or the workflow is explicitly configured for upload.
- Rover/plain workflows: do not warn for missing upload.
- If the selected command profile cannot emit RTCM/base-output messages, the
  upload control may be muted or disabled with a short explanation such as
  `No RTCM output`.

Design must be compact and effectively use limited screen space in landscape
and particularly (more difficult) in portrait mode, without making the
monitoring cards disappear somewhere deep below.

## Settings Set Modified State And Re-apply

Use the existing local-override state to show when the active settings set has
been modified from its stored definition.

Compact display:

- Clean/applied settings set: normal name, preferably with the applied state
  styled in green where colour is available.
- Modified settings set: append `+`, for example `Temporary base +`.
- The `+` is the semantic marker. Colour is supplementary only.
- Modified state may use blue styling for the `+` or the whole settings-set
  label where space allows.

Full rows or subtitles may show `Modified from settings set`.

Add a `Re-apply` action wherever the active settings set is shown in detail,
especially Menu > Active setup and Menu > Settings sets. Re-apply resets local
overrides and profile references back to the stored selected settings set. It
must not switch to another settings set. It is disabled when there are no local
modifications. If re-apply discards unsaved local edits, ask for confirmation.

## Naming

Rename user-facing `Init/shutdown scripts` to `Init/shutdown profiles`.

The underlying profile model can remain `CommandProfile`; this is a user-facing
terminology improvement.

Derived fixed-base init/shutdown profiles include date/time in their names,
for example:

`UM980 fixed base 2026-06-05T1355`

## Temporary Base To Fixed Base Handoff

When the user taps `Base` in a temporary-base workflow, the app must make both
the accepted coordinate source and fixed-base target explicit.

### Coordinate Source

Ask which coordinate to accept:

- `Use average`, if an averaged coordinate exists.
- `Use current`, if a current instant coordinate exists.
- `Cancel`.

If only one candidate exists, the prompt still names it clearly, for example
`Use current position`. The user must not have to infer whether an average or
instant coordinate was used.

### Init/shutdown Profile

Offer only suitable `MODE BASE` init/shutdown profiles:

- derive a new init/shutdown profile from a selected `MODE BASE` profile; or
- overwrite a selected editable `MODE BASE` profile.

Only the `MODE BASE` line changes. RTCM output lines and all other commands
remain unchanged.

### Fixed-base Settings Set

Offer suitable fixed-base settings sets for the selected device:

- select an existing fixed-base settings set and apply the chosen init/shutdown
  profile if that settings set can safely select or override it;
- derive a new fixed-base settings set from a selected fixed-base settings set;
- if the selected settings set is immutable because it is built-in, protected
  or its policy does not allow the init/shutdown profile to be changed in
  place, show:

`Immutable settings set: derive a new set`

Default selection should prefer:

1. the last fixed-base settings set used for the current device;
2. a matching built-in or user fixed-base base-output settings set;
3. a matching fixed-base settings-set template chosen by the user.

The default is highlighted, but the user can change it before confirming.

### Final Confirmation

Before applying the transition, show one compact confirmation:

`Switching to fixed-base settings set "<settings set>" with init/shutdown profile "<profile>".`

Buttons:

- `OK`.
- `Cancel`.

On `OK`:

- stop the temporary-base recording;
- select the fixed-base settings set, or the derived fixed-base settings set;
- select the chosen or derived init/shutdown profile;
- switch workflow to Fixed base;
- keep recording stopped so the user can choose/check Upload and then press
  Start.

On `Cancel`, the app must not leave the setup half-transitioned. Preferably,
the handoff is transactional: derived profiles, derived settings sets and
overwrites are prepared in memory and are not persisted until `OK`. If an
implementation step has already persisted a derived profile, derived settings
set or overwrite before the final confirmation, `Cancel` opens a cleanup
confirmation that lists what would be reverted or removed:

`Discard prepared fixed-base changes?`

The cleanup confirmation should offer:

- remove newly derived init/shutdown profiles;
- remove newly derived settings sets;
- revert edits to existing init/shutdown profiles;
- revert edits to existing settings sets.

The default action should discard/revert those prepared changes. A secondary
action may keep the prepared profiles/settings for later manual use, but it
must still leave the current recording, selected settings set, selected
workflow and selected init/shutdown profile unchanged.

## Error Handling

If no suitable fixed-base settings set exists for the selected device, the flow
offers derivation from a fixed-base template if one exists. If no template
exists, stop with:

`No fixed-base settings set for UM980. Create or copy a fixed-base settings set first.`

If no suitable `MODE BASE` init/shutdown profile exists, stop with:

`No MODE BASE init/shutdown profile for UM980. Switch Device to Any or copy a fixed-base profile.`

Changing the `Device` filter must never mutate the selected settings set,
command profile or recording workflow by itself.

## Tests

Add focused tests for:

- device-filter classification for UM980, u-blox M8T and Any;
- settings-set filtering by `receiverProfileId`;
- init/shutdown profile filtering by `CommandProfile.receiverFamily`;
- active out-of-filter rows remaining visible with an `Outside filter` marker;
- settings-set modified marker and Re-apply clearing local overrides;
- upload warning state being workflow-aware;
- Base action offering average/current coordinate sources explicitly;
- temporary-base to fixed-base selector offering only `MODE BASE` profiles;
- immutable fixed-base settings sets routing to derivation with
  `Immutable settings set: derive a new set`;
- final fixed-base transition selecting the fixed-base workflow/settings/profile
  without starting recording.
- final fixed-base transition `Cancel` discarding or explicitly preserving
  prepared derived profiles/settings sets and reverting prepared overwrites
  without changing the active recording setup.
