# Superpowers Plan Status

Last reviewed: 2026-06-20

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
| u-blox M8T support and Android mock provider | `2026-06-13-ublox-m8t-and-mock-provider.md`, `2026-06-16-pr-1-ublox-mock-provider-integration.md` | Implemented, not field-tested | UBX framing/parser/profile code, solution arbitration and mock-location adapter exist with tests. Mock altitude uses ellipsoidal height; satellite status remains limited by Android's public mock-location API. Needs M8T hardware validation and broader M8P/F9P follow-up before marking done. |
| SAF storage feature equivalence | `2026-06-08-field-test-hardening.md`, `2026-06-11-session-browser-archive.md`, `2026-06-14-session-nmea-export-error-expiry-and-dashboard-overflow.md` | Implemented, not field-tested | SAF browsing/actions, archive/restore/delete and NMEA re-export support are present. Storage profiles now use Android folder picker with persisted write permission. Needs provider-level validation across common Android document providers. |
| PPP status and UM980 PPPNAV handling | `2026-06-11-um980-rtk-monitoring-and-editor-input.md` | Implemented, not field-tested | PPPNAV parsing and dashboard mapping distinguish unavailable telemetry, not started/no convergence, converging and converged states. Continue field regression with live PPP sessions. |
| Temporary-base to fixed-base workflow | `2026-06-16-base-workflow-coordinate-actions.md`, `2026-06-18-base-rtcm-ntrip-caster-upload.md` | Implemented, not field-tested | Coordinate copying/averaging, accepted-coordinate persistence, manual/imported coordinates and fixed-base transition exist with ellipsoidal-height enforcement. Needs real base workflow field validation. |
| Base RTCM NTRIP caster upload | `2026-06-17-base-rtcm-ntrip-caster-upload-design.md`, `2026-06-18-base-rtcm-ntrip-caster-upload.md` | Implemented, not field-tested | Upload profiles, start validation, minimum RTCM command sanity checks, bounded upload controller, `base-caster-upload.rtcm3`, redacted metadata and dashboard status are implemented. Needs live caster/base hardware validation. |
| Built-in UM980 command profiles | `2026-06-08-integrated-v1-usability-and-um980-telemetry.md`, `2026-06-11-um980-rtk-monitoring-and-editor-input.md` | Implemented, not field-tested | Protected built-in profiles exist for UM980 multi-Hz binary RTK+PPP, ASCII RTK+PPP variants and base use, with migration from legacy user profiles. Hardware validation remains required. |
| In-phone RTKLIB real-time solution | `2026-06-06-rtkcollector-v1-workflows.md`, `2026-06-19-rtklib-ex-realtime-mvp.md`, `2026-06-19-rtklib-ex-native-and-obsvmcmpb-completion.md` | In progress | RTKLIB-EX routing/API foundation, bounded worker, app-side profiles/settings references, UM980 OBSVMB RTKLIB command profile, separate session artifacts, route/snapshot metadata, local pinned-checkout automation, native JNI bridge/build glue, NMEA/POS native output plumbing, named OBSVMCMPB decoder shim, UM980 sample-readiness tooling and dashboard RTKLIB card are implemented. Local Termux has no valid runnable NDK for proving the native `.so`; Android Studio/CI native build and real replay/field validation remain required. Current local UM980 samples contain monitoring/correction frames but no actual OBSVMB/OBSVMCMPB observation frames, so OBSVMCMPB solution validation remains open. |
| Active setup, RTKLIB/profile compatibility and solution policy | `2026-06-20-active-setup-rtklib-and-profile-compatibility.md` | In progress | Active setup policy/resolver, settings-set JSON persistence, profile compatibility checks, solution policy profiles, RTKLIB/solution-policy menu entries, split screen/mock source selection and session metadata fields are implemented. Remaining work: profile grouping/reordering UI, compact per-option policy selector polish and broader field review. |
| Google Play readiness | `2026-06-14-google-play-readiness.md` | Open | Plan exists for privacy, permissions, security, foreground-service and publication documentation cleanup. It still needs a fresh implementation/status review before marking done. |
| Formal specification system | `2026-06-17-formal-specification-system.md` | Done | Canonical `docs/specification/` requirements, templates, traceability, verification matrix and checker are present and separated from user-facing documentation and Superpowers history. |
