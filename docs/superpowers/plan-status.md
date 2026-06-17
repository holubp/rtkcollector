# Superpowers Plan Status

Last reviewed: 2026-06-17

This tracker summarises implementation status for the larger Superpowers plans.
It is intentionally separate from the historical step-by-step plan files so
agents can update status without rewriting old execution notes.

This file is the GitHub-tracked cross-agent status source. When a user,
reviewer or another agent suggests a status correction, validate the correction
against repository evidence before changing this file.

Status meanings:

- `Done`: functionality is present in code/docs and no specific open work is
  known beyond normal regression testing.
- `Implemented, not field-tested`: first implementation is present, but real
  hardware/provider or broader field validation is still needed.
- `Open`: planned or discussed, but not yet implemented end to end.
- `In progress`: current work exists, but the implementation is incomplete or
  not yet settled.

| Area | Related plan(s) | Status | Notes |
| --- | --- | --- | --- |
| Branding and logos | `2026-06-11-logo-branding-assets.md` | Done | Source logos, generator, Android launcher assets and rectangular badge assets are in place. |
| NTRIP/correction robustness | `2026-06-07-profiled-recording-fixes.md`, `2026-06-12-field-hardening-settings-telemetry.md` | Done | NTRIP v2-style client behaviour, mountpoint handling, correction recording and reconnect/auth policy are implemented. Keep field-regression testing with real casters. |
| USB access and older Android handling | `2026-06-12-usb-access-permission.md`, `2026-06-12-field-hardening-settings-telemetry.md` | Done | Start/USB access paths and stale-permission diagnostics are implemented. Huawei/vendor Android remains a recommended manual smoke test. |
| Persistent receiver writes | `2026-06-12-persistent-receiver-writes.md` | Done | Explicit warned `SAVECONFIG` flows and target-baud persistence support are implemented. Hardware verification remains important for UM980 changes. |
| Device console | `2026-06-14-device-console.md` | Done | Idle-only device console source and tests are present, including connect/disconnect, command input and output-buffer behaviour. |
| Session export, NMEA export, archive/restore and error expiry | `2026-06-11-session-browser-archive.md`, `2026-06-14-session-nmea-export-error-expiry-and-dashboard-overflow.md` | Done | ZIP share, archive/restore/delete, direct NMEA export/re-export and dashboard error expiry are implemented. Continue regression testing with real recorded sessions. |
| u-blox M8T support and Android mock provider | `2026-06-13-ublox-m8t-and-mock-provider.md`, `2026-06-16-pr-1-ublox-mock-provider-integration.md` | Implemented, not field-tested | UBX framing/parser/profile code, solution arbitration and mock-location adapter exist with tests. Needs M8T hardware validation and broader M8P/F9P follow-up before marking done. |
| SAF storage feature equivalence | `2026-06-08-field-test-hardening.md`, `2026-06-11-session-browser-archive.md`, `2026-06-14-session-nmea-export-error-expiry-and-dashboard-overflow.md` | Implemented, not field-tested | SAF browsing/actions, archive/restore/delete and NMEA re-export support are present. Needs provider-level validation across common Android document providers. |
| PPP status and UM980 PPPNAV handling | `2026-06-11-um980-rtk-monitoring-and-editor-input.md` | In progress | Current open work is to make PPP state distinguish no PPP messages, PPP not started/no solution, converging and converged. Recent debug sessions are under `samples/debug/`. |
| Temporary-base to fixed-base workflow | `2026-06-16-base-workflow-coordinate-actions.md` | In progress | Coordinate copying/averaging semantics are specified, but workflow transition, accepted-coordinate persistence and fixed-base coordinate visibility still need completion/review. |
| Built-in UM980 command profiles | `2026-06-08-integrated-v1-usability-and-um980-telemetry.md`, `2026-06-11-um980-rtk-monitoring-and-editor-input.md` | Open | Need read-only built-in profiles for UM980 multi-Hz binary RTK+PPP, ASCII RTK+PPP variants and base/fixed-base use, with copy-to-edit behaviour and migration from existing user profiles. |
| In-phone RTKLIB real-time solution | `2026-06-06-rtkcollector-v1-workflows.md` | Open | V1 deliberately leaves RTKLIB real-time disabled. A dedicated plan is still needed before implementation, including RTKLIB+ architecture review and strict separation from receiver-internal solutions. |
| Google Play readiness | `2026-06-14-google-play-readiness.md` | Open | Plan exists for privacy, permissions, security, foreground-service and publication documentation cleanup. It still needs a fresh implementation/status review before marking done. |
| Formal specification system | `2026-06-17-formal-specification-system.md` | Open | Create canonical `docs/specification/` requirements, traceability and verification matrix separate from user-facing documentation and Superpowers history. |
