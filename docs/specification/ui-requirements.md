# UI Requirements

## Dashboard

### UI-DASH-001: Recording Controls Remain Reachable

Status: Normative

Start/Stop, Menu and core runtime actions MUST remain reachable in portrait and
landscape layouts, including devices with navigation bars and tablets with
hardware keyboards.

Verification:
- Manual: phone portrait, tablet landscape and navigation-bar layout checks.
- Review: controls are not buried inside vertically scrollable telemetry
  content.

### UI-DASH-003: Portrait Monitoring Cards Avoid Content Clipping

Status: Normative

When live-monitoring cards cannot fit their content cleanly in two columns, the
portrait dashboard MUST use a single-column monitoring layout while preserving
the fixed top selectors and bottom runtime controls.

Verification:
- Manual: phone portrait and small-screen portrait dashboard checks.
- Review: dashboard layout selection depends on available width/orientation,
  not on recorded value length.

### UI-DASH-002: Dashboard Values Must Not Jump Layout

Status: Normative

Live telemetry updates MUST NOT cause dashboard cards to resize or jump in a way
that disrupts operation.

Verification:
- Manual: live UM980 session with high-rate position updates.
- Review: fixed/minimum card dimensions and compact formatting.

### UI-DASH-004: Mock GPS Control Is A First-Class Dashboard Chip

Status: Normative

The dashboard MUST expose Android mock-location output as a compact control
near the recording state chip. The control MUST show `Off` or the selected
publish frequency and MUST allow the user to select only supported fixed rates
without opening a full profile editor. Event-marker placeholders MUST NOT be
shown as active controls until event marking is implemented.

Verification:
- Automated: dashboard state/action tests and profile/config rate tests.
- Manual: top chip can switch between Off, `1 Hz`, `2 Hz`, `5 Hz` and `10 Hz`
  before recording and during recording.

### UI-DASH-005: Dashboard Labels And Units Are Consistent

Status: Normative

Dashboard fields with the same semantics MUST use the same labels and unit
formatting across cards. Horizontal/vertical accuracy estimates SHOULD be
presented as `Acc H/V`; latitude/longitude component estimates SHOULD be
presented as `Err lat` and `Err lon`; satellites MUST state that values are
`used / in view`. The dashboard MUST offer dynamic distance units
(`m`/`cm`/`mm`) and static metre display for distance-like accuracy/error
fields.

Verification:
- Automated: dashboard formatter and layout preference tests.
- Manual: live dashboard review in portrait and landscape.

### UI-SATMON-001: Compact Satellite Card Uses Main Engine

Status: Normative

During recording, the dashboard SHOULD expose a compact `Satellites` card for
satellite-frequency monitoring when monitor state is available or explicitly
unavailable. The card MUST stay with the active main solution engine and MUST
NOT expose an engine switch on the main dashboard.

Verification:
- Automated: satellite monitor dashboard model tests.
- Manual: dashboard visual review in portrait and landscape.

### UI-SATMON-002: Compact Satellite Card Groups Constellation Then Frequency

Status: Normative

The compact `Satellites` card MUST group first by constellation and then by
frequency band. Each frequency cell MUST show rover (`R`) and base (`B`) rows
with right-aligned `used/visible` counts.

Verification:
- Automated: satellite monitor dashboard model tests.
- Manual: dashboard visual review with supported monitor profiles.

### UI-SATMON-003: Boxed Bars Encode Visible And Used Counts

Status: Normative

The compact `Satellites` card MUST render boxed horizontal bars for rover and
base rows. Lower-saturation boxes represent visible satellites. Higher
saturation boxes represent satellites used by the active main engine. Used
boxes MUST be drawn as a saturated prefix inside the visible total.

Verification:
- Automated: `SatelliteMonitorDashboardModelsTest` boxed segment tests.
- Manual: dashboard visual review.

### UI-SATMON-004: Source Freshness Labels Are Learnable

Status: Normative

The compact `Satellites` card MUST label source freshness dots as `R`, `B` and
`S`, meaning rover observations, base correction observations and selected
solution usage. The card MUST include an information affordance explaining
these labels and the `used/visible` boxed-bar semantics.

Verification:
- Manual: dashboard help review.

### UI-SATMON-005: Satellite Card Has Light Default And Optional Dark Style

Status: Normative

The compact satellite card MUST follow the normal light dashboard colour scheme
by default. Dashboard layout settings MUST provide a binary switch for applying
the approved dark satellite-card colour scheme without changing the rest of the
application theme.

Verification:
- Automated: dashboard layout preference tests.
- Manual: dashboard visual review in light and satellite-card dark modes.

### UI-SATMON-006: Detailed Satellite Monitor Supports Grouping Switch

Status: Normative

Tapping the compact `Satellites` card SHOULD open a detailed `Satellite
monitor` screen. The detailed screen MUST default to frequency-band primary
grouping and MUST provide a slider switch to view constellation-primary
grouping with frequency bands nested underneath. This detailed grouping switch
MUST NOT change the compact dashboard grouping, which remains constellation
first and frequency second.

Verification:
- Automated: satellite monitor dashboard model grouping tests.
- Manual: detailed screen visual review in both grouping modes.

### UI-BASE-001: Base Coordinate Actions Are Compact And Explicit

Status: Normative

Dashboard base-coordinate actions MUST keep coordinate copy, averaging and
fixed-base acceptance compact while making the selected coordinate source and
height semantics explicit.

Verification:
- Automated: dashboard state tests for base candidate generation.
- Manual: temporary-base dashboard review in portrait and landscape.

## Profile Editors

### UI-SETUP-001: Active Settings Set Is Visible In Settings

Status: Normative

The settings hub MUST show the currently active settings set and current
workflow near the top of the menu. Activating a settings set MUST be separate
from editing a settings-set profile.

Verification:
- Automated: active setup resolver and settings-set persistence tests.
- Manual: settings hub review before and during recording.

### UI-SETUP-002: Settings Set Options Have Explicit Application Policy

Status: Normative

Major settings-set options SHOULD declare how they apply to the live active
setup: default and user-changeable, locked, empty and remembered after user
selection, or empty and required every time. User selections made from the main
dashboard are transient active-setup choices and MUST NOT silently rewrite the
settings-set profile.

Verification:
- Automated: active setup resolver tests.
- Manual: activate settings set, override dashboard selectors and restart app.

### UI-PROFILE-002: Profile Activation Is Separate From Editing

Status: Normative

Profile list screens MUST distinguish activation from editing. Profile
references that are constrained by receiver family or workflow compatibility
MUST be selectable from controlled lists instead of free-text identifiers.

Verification:
- Automated: profile compatibility tests where practical.
- Manual: settings/profile screens show Activate/Edit/Rename as distinct
  actions.

### UI-PROFILE-001: Built-In Profiles Are Viewable And Copyable

Status: Normative

Built-in app-distributed profiles MUST be viewable in profile screens, MUST NOT
be directly editable, and MUST offer a copy path for user-modified variants.

Verification:
- Automated: profile protection tests where practical.
- Manual: built-in command-profile screen shows View/read-only behaviour and a
  copy action.

### UI-KEYBOARD-001: Multiline Command Editors Preserve Hardware Keyboard Semantics

Status: Normative

Multiline init/shutdown command editors MUST allow hardware-keyboard arrow keys
and arrow-key modifier combinations to move within the text field. Tab and
Shift+Tab MAY move between fields, but arrow keys MUST NOT be used for
cross-field focus traversal while a multiline command field is active. Field
cursor/selection position SHOULD be restored when returning to a field through
Tab or Shift+Tab.

Verification:
- Manual: Bluetooth/hardware keyboard editing in init/shutdown command screens.
- Review: command editor does not intercept arrow keys for field traversal.

### UI-STORAGE-001: SAF Storage Uses Android Folder Picker

Status: Normative

Selected Android folder storage profiles MUST obtain their tree URI through the
Android system folder picker and MUST persist read/write URI permission before
recording. Users MUST NOT be required to type document-tree URIs manually.

Verification:
- Manual: create/edit storage profile with Selected Android folder.
- Review: profile editor treats the tree URI as display-only routing data.

## Documentation Separation

### UI-DOCS-001: User Documentation Is Separate From Formal Specs

Status: Normative

User-facing docs MUST explain workflows in task-oriented language for new users
and power users. Formal specs MUST remain separate and may use precise
requirements language.

Verification:
- Review: user docs do not become requirement matrices; formal specs do not
  become tutorials.
