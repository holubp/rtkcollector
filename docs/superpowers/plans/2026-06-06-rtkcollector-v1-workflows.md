# RtkCollector V1 Workflows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update the workflow model and docs so version 1 implements receiver-side rover, NTRIP-to-receiver, temporary-base preparation, fixed-base, and replay workflows while leaving in-phone RTKLIB real-time for version 2.

**Architecture:** Keep workflows as validated pure Kotlin `WorkflowSpec` values. The raw capture path remains authoritative; base-position generation is represented as candidate policy attached to temporary-base preparation sessions, not as a separate UI mode.

**Tech Stack:** Kotlin/JVM module `:core:workflow`, JUnit 5 tests, Markdown docs.

---

### Task 1: Define V1 Temporary-Base Recording Requirements

**Files:**
- Modify: `core/workflow/src/test/kotlin/org/rtkcollector/core/workflow/WorkflowValidatorTest.kt`
- Modify: `core/workflow/src/main/kotlin/org/rtkcollector/core/workflow/WorkflowModel.kt`
- Modify: `core/workflow/src/main/kotlin/org/rtkcollector/core/workflow/WorkflowExamples.kt`
- Modify: `core/workflow/src/main/kotlin/org/rtkcollector/core/workflow/WorkflowValidator.kt`

- [x] Add failing tests that a base-preparation workflow records device solution, requests raw observations at 1 Hz when supported, requests receiver PPP when supported, and carries base-position candidate methods.
- [x] Run `sh ./gradlew :core:workflow:test` and verify the new tests fail for missing model/validator support.
- [x] Add the minimal model fields and validator rules.
- [x] Run `sh ./gradlew :core:workflow:test` and verify the workflow tests pass.

### Task 2: Move RTKLIB Real-Time Out Of V1 User Workflows

**Files:**
- Modify: `core/workflow/src/test/kotlin/org/rtkcollector/core/workflow/WorkflowValidatorTest.kt`
- Modify: `core/workflow/src/main/kotlin/org/rtkcollector/core/workflow/WorkflowExamples.kt`
- Modify: `docs/workflows.md`

- [x] Add tests proving V1 example set excludes RTKLIB real-time while legacy/future validation can still reject unsafe RTKLIB specs.
- [x] Update workflow examples to expose a V1 list separate from future RTKLIB helper examples.
- [x] Run workflow tests.

### Task 3: Update User Documentation

**Files:**
- Create: `docs/user-workflows.md`
- Modify: `docs/workflows.md`
- Modify: `docs/base-calibration-workflow.md`
- Modify: `docs/session-format.md`
- Modify: `docs/product-requirements.md`
- Modify: `docs/architecture.md`
- Modify: `README.md`

- [x] Document V1 workflows as user scenarios: plain rover, rover with NTRIP to receiver, temporary-base preparation, fixed-base from accepted coordinate, and replay/test.
- [x] Document that base-position candidates are generated from a temporary-base recording, ranked from static RTK/PPP/static through receiver PPP to long averaging fallback.
- [x] Document that in-phone RTKLIB real-time is V2.
- [x] Document risks: raw conversion compatibility, PPP convergence, antenna metadata, frame/epoch, wrong-base precision, car-roof stability and multipath.

### Task 4: Final Review And Validation

**Files:**
- All changed files.

- [x] Ask reviewer agent to review updated workflow model/docs.
- [x] Apply only must-fix review findings.
- [x] Rerun reviewer if fixes are applied.
- [x] Run `sh ./gradlew clean`, `sh ./gradlew test`, and `sh ./gradlew assembleDebug`.
- [x] Commit with review-and-commit and push the branch.
