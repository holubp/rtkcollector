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

## Documentation Separation

### UI-DOCS-001: User Documentation Is Separate From Formal Specs

Status: Normative

User-facing docs MUST explain workflows in task-oriented language for new users
and power users. Formal specs MUST remain separate and may use precise
requirements language.

Verification:
- Review: user docs do not become requirement matrices; formal specs do not
  become tutorials.

