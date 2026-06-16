# Session NMEA Export, Error Expiry And Dashboard Overflow Design

## Context

RtkCollector already records an optional `receiver-solution.nmea` sidecar derived
from the authoritative receiver RX stream. The recorded-session browser can share
temporary ZIP files, archive/restore sessions and delete selected recordings.
The main dashboard shows service errors, but some recovered errors remain visible
long after they are no longer true. On narrow portrait devices such as Huawei
P30 Pro, the Fix card can clip its lower rows, including the line below
`RTKLIB Not configured`.

This work is small UI/export hardening. It must not alter raw capture,
receiver-command sequencing, NTRIP injection, UM980 parsing, session writer byte
paths or any map/GIS behaviour.

## Goals

1. Allow direct sharing of recorded NMEA files without creating or sharing a full
   session ZIP.
2. Make the session-browser `Select all` action become `Unselect all` when all
   selectable entries are already selected.
3. Clear stale dashboard error messages after at most 15 seconds when the service
   no longer reports an active error.
4. Prevent dashboard cards, especially the Fix card, from clipping metric rows on
   narrow portrait screens; scrolling is acceptable and preferred to trimmed
   content.

## Non-Goals

- Do not regenerate NMEA from raw receiver streams during sharing.
- Do not change the recording policy model.
- Do not change the raw `receiver-rx.raw` file or session writer authority.
- Do not introduce permanent NMEA export files outside the session folder.
- Do not refactor the foreground service error model broadly.
- Do not add map, GIS, shapefile or field-collection dependencies.

## Direct NMEA Sharing

The session browser gains a `Share NMEA` action next to `Share ZIP`.

The action operates on selected session entries. A session is eligible when:

- it is filesystem-backed;
- it is not the active recording session;
- it is a stopped current session or normal recording session;
- the session directory contains `receiver-solution.nmea`.

The app must not synthesize or reparse NMEA at share time. It copies the existing
`receiver-solution.nmea` sidecar into app cache and shares the temporary copy
through Android's share sheet. This mirrors temporary ZIP sharing: the share
artifact is a cache file, not a permanent recording artifact.

The share filename must follow the ZIP naming style but use `.nmea`, for
example:

```text
session-2026-06-11T10-55-35.270010Z-0f63e64e-07c8-45d9-afbb-5b448cbba183-1781176588408.nmea
```

If one selected session has NMEA, use `ACTION_SEND` with MIME type
`text/plain`. If multiple selected sessions have NMEA, use
`ACTION_SEND_MULTIPLE` with MIME type `text/plain`.

If some selected sessions do not have `receiver-solution.nmea`, they are skipped.
The UI reports this in progress or toast text, for example:

```text
Shared NMEA for 2 session(s); 1 selected session had no NMEA export.
```

If no selected session has a shareable NMEA file, the action is disabled or shows:

```text
No recorded NMEA file is available for the selected session(s).
```

Temporary `.nmea` share files should be deleted on a best-effort basis after
sharing, using the same delayed cleanup style as temporary ZIP shares.

## Explicit NMEA Re-Export Addendum

A later explicit `Re-export NMEA` action is separate from direct sharing. It may
regenerate the derived `receiver-solution.nmea` sidecar for filesystem-backed
stopped sessions from the authoritative `receiver-rx.raw` stream. This is used
for fixes to generated NMEA semantics such as PPP-to-GGA quality mapping. The
operation must not mutate `receiver-rx.raw`, receiver TX, correction input or
session metadata. PPP mapping is configurable in the recording-output profile:
`2` is the default, `5` and `9` are selectable, and `4` is reserved until a
future parser can distinguish PPP-AR/PPP-RTK integer-fixed output.

## Selection Toggle

The session-browser selection model should expose whether every selectable entry
is selected. Selectable entries are the same entries currently eligible for
selection: filesystem-backed and not active.

Button text:

- `Select all` when at least one selectable entry is not selected;
- `Unselect all` when all selectable entries are selected;
- disabled or no-op when there are no selectable entries.

Pressing the button:

- selects all selectable entries when not all are selected;
- clears selection when all selectable entries are already selected.

This logic belongs in the session-browser model so it can be unit tested without
Compose.

## Dashboard Error Expiry

The dashboard should not keep stale recovered errors on screen indefinitely.
Error display is a UI concern; the foreground service can continue broadcasting
its existing state fields.

The UI should track an error fingerprint:

```text
category + severity + message
```

Visibility rules:

1. If `lastError` is blank or absent, hide the error immediately.
2. If `errorCategory == NONE` and `errorSeverity == NONE`, hide the error
   immediately even if a stale message is present.
3. If the fingerprint changes, record the current time as the first-seen time
   for that displayed error.
4. If the error is fatal, keep it visible until the service clears or replaces
   it.
5. If the error is non-fatal, degraded, informational or has inconsistent
   `NONE` category/severity, show it for no more than 15 seconds.
6. If the service continues to report an active non-fatal error with the same
   fingerprint, it may remain visible only while the age is within the 15-second
   window. A new fingerprint starts a new window.

Examples:

- `NONE: Software caused connection abort` disappears immediately if category
  and severity are both `NONE`, or after 15 seconds if it arrives with a
  non-fatal severity.
- `NTRIP: NTRIP connection failed` disappears after 15 seconds unless a new
  active NTRIP error fingerprint arrives.
- `USB: USB serial device could not be opened` with fatal severity remains
  visible until the service clears it or a new error replaces it.

The error strip remains tappable for clipboard copy while visible.

## Dashboard Overflow Fix

Dashboard cards must not clip content on narrow portrait displays. The specific
reported failure is the Fix card on Huawei P30 Pro portrait, where the row below
`RTKLIB Not configured` is only half visible.

Implementation should make cards content-safe:

- Remove fixed card heights where they cause clipping, or replace them with
  minimum heights.
- Let card content determine height inside the already scrollable dashboard.
- Keep the top menu and bottom Start/Stop/action bar outside the scrollable
  dashboard content.
- Preserve the compact visual style; do not add large blank space just to avoid
  clipping.
- Confirm both compact portrait and rail landscape layouts still work.

It is acceptable for the middle dashboard content to scroll vertically on small
screens. It is not acceptable for metric rows to be hidden, half visible or
unreachable.

## Components

### Session Share Model

Add a focused model/helper near existing session share code:

- derive temporary NMEA filename from session title/id or ZIP basename;
- identify `receiver-solution.nmea` under a session directory;
- return shareable NMEA plans and skipped-session counts.

The helper must never read or parse receiver raw data.

### Session Browser Model

Extend `SessionBrowserState` with:

- `allSelectableSelected: Boolean`;
- `selectAllButtonLabel: String`;
- `toggleSelectAll(): SessionBrowserState`.

### Dashboard Error Visibility Model

Add a pure Kotlin model for visibility decisions, for example:

```kotlin
data class DashboardErrorSnapshot(
    val category: String,
    val severity: String,
    val message: String?,
)
```

and a policy function that determines whether the snapshot should be displayed
at a given age. Compose can use `remember` state to track the first-seen time for
the current fingerprint.

### Dashboard Layout

Update the dashboard card implementation so content is not clipped. The likely
smallest change is to replace fixed `height(...)` with `heightIn(min = ...)` or
remove fixed heights for metric-heavy cards. The scroll container already exists
around compact dashboard content and should remain the overflow mechanism.

## Testing

Add or extend unit tests for:

- NMEA share plan includes only sessions containing `receiver-solution.nmea`.
- NMEA share filename uses `.nmea` and mirrors the current session ZIP naming
  style.
- missing NMEA sidecars are counted as skipped.
- `Select all` label becomes `Unselect all` when all selectable entries are
  selected.
- `toggleSelectAll()` clears selection when all selectable entries are selected.
- stale non-fatal dashboard error is hidden after 15 seconds.
- `NONE`/`NONE` with a stale message is hidden immediately.
- fatal dashboard error remains visible beyond 15 seconds.
- dashboard card sizing model, if extracted, uses minimum/growing content height
  rather than fixed clipping heights.

Manual checks:

1. Record a session with NMEA export enabled.
2. Open recorded sessions, select it and share NMEA directly.
3. Confirm the shared file name ends in `.nmea`.
4. Select all sessions and confirm the button changes to `Unselect all`.
5. Trigger or simulate a transient NTRIP/USB error and confirm it clears from
   the dashboard within 15 seconds after recovery.
6. Test Huawei P30 Pro portrait or equivalent narrow portrait preview/device and
   confirm the Fix card rows are fully visible via scrolling.

## Documentation

Update:

- `docs/user-workflows.md` with direct NMEA sharing behaviour.
- `docs/session-format.md` to state that `receiver-solution.nmea` is the source
  for direct NMEA share and is not regenerated during sharing.

## Acceptance Criteria

- Direct NMEA sharing works for one or multiple selected filesystem-backed
  stopped sessions with `receiver-solution.nmea`.
- Direct NMEA sharing skips selected sessions without NMEA and reports that
  clearly.
- Temporary NMEA share files are cleaned up best-effort after sharing.
- `Select all` changes to `Unselect all` when all selectable entries are
  selected and clears selection when pressed in that state.
- Stale non-fatal dashboard errors disappear after no more than 15 seconds.
- Healthy `NONE` error state with stale message is not shown.
- Fatal errors remain visible until cleared/replaced.
- Dashboard Fix card and other metric groups do not clip rows on narrow portrait
  screens; scrolling exposes all content.
- Raw capture, receiver TX, correction input and session writer behaviour remain
  unchanged.
