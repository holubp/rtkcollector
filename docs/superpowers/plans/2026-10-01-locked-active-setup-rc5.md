# Locked Active Setup and RC5 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every locked settings-set option consistent between Home and recording start, then release a stable-key-signed RC5 APK.

**Architecture:** Normalize a settings set into one effective active configuration before dashboard projection and recording-intent construction. Apply workflow defaults only when a settings set is activated; use the current selected workflow for Start unless the workflow is locked. Home setup controls derive their fixed/disabled state from the same option policies, with selection callbacks refusing locked changes as a second guard.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit, Gradle, GitHub Actions.

**Spec:** `docs/specification/ui-requirements.md` (`UI-SETUP-002`).

## Global Constraints

- Preserve byte-exact receiver recording, session data and unrelated NTRIP behavior.
- Keep built-in settings sets read-only; copied sets remain editable.
- Never discard a user's stored overrides merely to render a locked value.
- RC5 source tag is immutable after creation; sideload signing uses the existing pinned stable certificate, not a fresh key.

## Review Focus

- A locked workflow with a stale selected workflow must display and start the fixed value.
- A changeable workflow selected after set activation must be the one Start uses.
- A locked mountpoint or caster must not change indirectly when the other is selected.
- A locked upload option must cover both profile selection and the enable/off state.
- A stale local override of any locked option must not affect either display or Start.

---

### Task 1: Canonical Effective Setup

**Files:** `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt`, `WorkflowActivationPolicy.kt`, `app/src/main/kotlin/org/rtkcollector/app/ui/NtripProfileResolution.kt`, focused tests under `app/src/test/kotlin/org/rtkcollector/app/profile/` and `app/src/test/kotlin/org/rtkcollector/app/ui/`.

- [x] Add failing tests for each locked override family, workflow lock/changeability, and paired NTRIP caster/mountpoint resolution.
- [x] Verify the tests fail for the current implementation.
- [x] Implement one policy-aware effective-settings function used by both UI and Start; keep stored overrides unchanged.
- [x] Verify focused tests pass.

### Task 2: Home and Selection Guards

**Files:** `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`, `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`, `HomeDashboard.kt`, focused dashboard/UI tests.

- [x] Add failing tests for fixed-value projection, disabled compact/rail selectors, and stale selection rejection.
- [x] Verify the tests fail for the current implementation.
- [x] Derive Home and Start from the effective settings set and effective workflow; show a lock symbol on grey, non-clickable locked controls. Guard callbacks as well as visual controls.
- [x] Verify focused tests pass and changeable controls still work.

### Task 3: Settings Editor, Specification and Regression Gate

**Files:** settings-set editor in `MainActivity.kt`, editor tests, `docs/specification/verification-matrix.md`, `docs/user-workflows.md`, `docs/superpowers/plan-status.md`.

- [x] Expose lock policy for applicable settings-set options without modifying protected built-ins; preserve imported policies on edit/save.
- [x] Add editor persistence and import/restart regression tests.
- [ ] Run `sh scripts/pre_push_check.sh` with the available Android SDK path and review the complete task diff.
- [ ] Commit and push the reviewed change to `main`; require successful clean-host CI.

### Task 4: Publish RC5

**Files:** `app/build.gradle.kts`, `.github/workflows/release-debug-apk.yml`, `docs/releases/1.0-RC5.md`, status/evidence documentation.

- [x] Bump to `versionCode = 5`, `versionName = "1.0-RC5"`; update release workflow default and release notes.
- [ ] Tag the verified source as `v1.0-RC5` and create a prerelease after clean-host CI passes.
- [ ] Dispatch the pinned stable-key GitHub release workflow; require success.
- [ ] Download the public APK, checksum and signing receipt; independently verify APK checksum, one pinned signer, package/version and source/tooling commits.
- [ ] Record final evidence, commit/push documentation, and keep on-device in-place upgrade testing explicit if not yet performed.
