# Dashboard Sessions Shortcut And 1.0-RC2 Design

Date: 2026-07-27
Status: Approved

## Goal

Make completed recordings and sharing reachable directly from the Home
dashboard, while keeping the compact setup controls balanced and preserving the
existing session-browser safeguards. Publish the resulting source as the
1.0-RC2 release candidate.

## Dashboard Interaction

The foldable `Active setup` section gains an eighth top-level button:
`Sessions`. It appears after `Storage`, so the final row in the compact
two-column layout is:

| Storage | Sessions |
| --- | --- |

The rail layout uses the same ordered set of eight actions. `Sessions` is a
navigation shortcut, not an active-configuration selector, and therefore has
no setup value, warning state or configuration-validation responsibility.
Collapsing `Active setup` hides it together with the seven configuration
buttons. Existing automatic expansion for invalid configuration remains
unchanged.

Pressing `Sessions` refreshes the session browser and opens the existing
`Recent sessions and sharing` screen. No duplicate browser or sharing workflow
is introduced. Existing active-session protections, archive validation,
operation leases and sharing rules remain authoritative.

The session screen returns to the screen from which it was opened:

- Home shortcut -> Home
- Settings > Sessions > Recent sessions and sharing -> Settings

Both the visible Back action and Android system Back use this origin.

## Specification And Documentation

The canonical UI requirements will define all eight Home actions, while
distinguishing the seven configuration selectors from the `Sessions`
navigation shortcut. User documentation and screenshot guidance will list the
new Home shortcut. The existing dedicated Sessions group in Settings remains
available.

## Verification

Automated tests will verify:

- the ordered Home actions are Device, Settings, Workflow, Mountpoint,
  Profiles, Upload, Storage and Sessions;
- the compact two-column grouping places Storage and Sessions together;
- the Sessions action has no configuration warning semantics;
- session navigation returns to Home or Settings according to its origin;
- active setup expansion behavior remains driven only by configuration
  validity.

The Android app Kotlin sources and JVM tests must compile, and the complete
repository pre-push gate must pass before release.

## Release

The project and Android application versions become `1.0-RC2`, with Android
`versionCode` incremented to `2`. Protocol-visible RtkCollector user-agent
defaults and current-release documentation must match `1.0-RC2`.

The release commit will be tagged `v1.0-RC2` and published as a GitHub
prerelease. The only currently available APK is a debug-signed `1.0-RC1`
artifact; it must not be renamed or attached to RC2. RC2 is therefore
source-only until an APK or AAB built from the RC2 release commit is supplied
and independently verified.
