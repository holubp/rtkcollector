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

### UI-BASE-001: Base Coordinate Actions Are Compact And Explicit

Status: Normative

Dashboard base-coordinate actions MUST keep coordinate copy, averaging and
fixed-base acceptance compact while making the selected coordinate source and
height semantics explicit.

Verification:
- Automated: dashboard state tests for base candidate generation.
- Manual: temporary-base dashboard review in portrait and landscape.

## Profile Editors

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
