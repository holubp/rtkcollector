# Traceability

This file maps Superpowers design history and existing docs to the formal
specification. Superpowers files remain useful rationale, but formal
requirements live under `docs/specification/`.

| Source | Formal spec destination | Notes |
| --- | --- | --- |
| `docs/workflows.md` | `workflows.md` | Workflow concepts, lifecycle and validation. |
| `docs/user-workflows.md` | `workflows.md`, `ui-requirements.md` | User workflow semantics and UI obligations. |
| `docs/session-format.md` | `session-artifacts.md`, `security-privacy.md` | Session artifact separation and secret redaction. |
| `docs/ntrip-and-corrections.md` | `functional-requirements.md`, `security-privacy.md` | Correction intake/routing, auth and secrets. |
| `docs/android-background-operation.md` | `android-runtime.md` | Foreground-service and background recording requirements. |
| `docs/superpowers/specs/2026-06-08-integrated-v1-usability-and-um980-telemetry-design.md` | `receiver-behaviour.md`, `ui-requirements.md` | Dashboard, profiles and UM980 telemetry. |
| `docs/superpowers/plans/2026-06-11-um980-rtk-monitoring-and-editor-input.md` | `receiver-behaviour.md`, `verification-matrix.md` | RTK/PPP separation and UM980 monitoring. |
| `docs/superpowers/plans/2026-06-13-ublox-m8t-and-mock-provider.md` | `receiver-behaviour.md`, `verification-matrix.md` | M8T and mock-provider requirements. |
| `docs/superpowers/plans/2026-06-14-google-play-readiness.md` | `security-privacy.md`, `android-runtime.md`, `publication-readiness.md` | Publication, privacy and permission requirements. |
| `docs/superpowers/specs/2026-07-08-google-play-readiness-execution-design.md` | `publication-readiness.md`, `verification-matrix.md` | Current-state review and execution gates for the Google Play readiness plan. |
| `docs/superpowers/plans/2026-06-16-base-workflow-coordinate-actions.md` | `workflows.md`, `ui-requirements.md` | Temporary-base coordinate actions. |
| `docs/superpowers/plans/2026-06-17-formal-specification-system.md` | all formal specification files | Defines the formal spec tree and tracking rules. |
