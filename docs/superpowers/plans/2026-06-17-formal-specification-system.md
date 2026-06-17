# Formal Specification System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a durable, GitHub-tracked formal specification system that turns Superpowers brainstorming/spec/plan history into precise requirements, traceability and verification criteria while keeping user-facing documentation separate and friendly.

**Architecture:** Add a canonical `docs/specification/` tree for normative requirements and verification matrices. Keep `docs/superpowers/specs/` and `docs/superpowers/plans/` as design-history and execution-history inputs, not as the formal contract. Update contributor/agent guidance so every future feature maps requirements, implementation and verification back to the formal spec and keeps `docs/superpowers/plan-status.md` current.

**Tech Stack:** Markdown documentation, repository-local scripts or tests for link/ID validation, existing Gradle/JVM test infrastructure only if a lightweight verifier is included.

---

## Scope

This plan creates the specification governance system. It does not rewrite every
existing requirement in one pass. The first implementation should define the
structure, templates, traceability rules and initial high-priority requirements
for the app's non-negotiable architecture.

Formal specifications are for AI agents and contributors. They must be precise,
stable and testable. User-facing documentation remains separate and should be
clear for new users and power users.

## File Structure

Create:

- `docs/specification/index.md`
  - Entry point, reading order, status definitions and normative/non-normative distinction.
- `docs/specification/terminology.md`
  - Stable vocabulary: receiver RX, app TX to receiver, correction input, workflow, temporary base, fixed base, PPP, RTK, raw observations.
- `docs/specification/functional-requirements.md`
  - Requirement IDs grouped by domain.
- `docs/specification/workflows.md`
  - Normative workflow requirements extracted from `docs/workflows.md` and user-facing workflow docs.
- `docs/specification/receiver-behaviour.md`
  - UM980, u-blox and generic receiver behaviour requirements.
- `docs/specification/session-artifacts.md`
  - Normative session artifact and metadata requirements.
- `docs/specification/android-runtime.md`
  - Foreground service, background recording, USB permission, notification and crash-recovery requirements.
- `docs/specification/security-privacy.md`
  - Secrets, settings backup, NTRIP auth, Android intents/imports, Play-readiness requirements.
- `docs/specification/ui-requirements.md`
  - Dashboard, menu, profile, keyboard, portrait/landscape and accessibility requirements.
- `docs/specification/verification-matrix.md`
  - Requirement-to-test/manual-check/sample mapping.
- `docs/specification/traceability.md`
  - Mapping from Superpowers plans/specs and existing docs to formal spec sections.
- `docs/specification/templates/requirement-template.md`
  - Reusable requirement format.
- `docs/specification/templates/verification-row-template.md`
  - Reusable verification row format.

Modify:

- `AGENTS.md`
  - Add the canonical spec workflow and requirement-ID expectations.
- `docs/contributor-onboarding.md`
  - Add reading order and contribution workflow for formal specs vs user docs.
- `docs/superpowers/plan-status.md`
  - Track this formal specification system plan.
- `README.md`
  - Add a short pointer to user docs and formal specs without overwhelming app users.

Optional follow-up, only after the Markdown structure is stable:

- `tools/check_spec_requirements.py`
  - Validate requirement ID uniqueness, matrix references and dead links.
- `tools/test_check_spec_requirements.py`
  - Tests for the spec checker.

---

## Requirement ID Scheme

Use stable, grep-friendly IDs:

- `ARCH-*` for raw-capture architecture and failure isolation.
- `WF-*` for workflow semantics.
- `SESSION-*` for session artifacts and metadata.
- `RX-*` for receiver-specific behaviour.
- `NTRIP-*` for correction intake and routing.
- `ANDROID-*` for foreground service, USB and background execution.
- `UI-*` for dashboard and settings UI.
- `SEC-*` for secrets, import/export and privacy.
- `TEST-*` for verification-process requirements.

Example:

```markdown
### ARCH-RAW-001: Receiver RX Raw Stream Is Authoritative

Status: Normative

The app MUST record receiver-originated bytes into `receiver-rx.raw` without
inserting timestamps, markers, parsed fields or app metadata.

Rationale:
The raw receiver stream is the source of truth for replay, diagnostics and
post-processing.

Verification:
- Automated: session writer byte-preservation test.
- Manual: replay a known byte stream and compare output bytes.
- Review: parser, UI, NTRIP and RTKLIB paths must not write into
  `receiver-rx.raw`.
```

---

## Task 1: Add Formal Specification Entry Point And Templates

**Files:**
- Create: `docs/specification/index.md`
- Create: `docs/specification/templates/requirement-template.md`
- Create: `docs/specification/templates/verification-row-template.md`

- [ ] **Step 1: Create `docs/specification/index.md`**

Use this content:

```markdown
# RtkCollector Formal Specification

This tree is the canonical formal specification for RtkCollector. It is written
for AI agents and contributors who need precise, testable requirements.

User-facing documentation remains outside this tree and should be friendly,
task-oriented and practical. Superpowers brainstorming, design and plan files
remain design history and traceability inputs.

## Normative Status

- `Normative`: required behaviour.
- `Experimental`: implemented or planned behaviour that is not yet a stable
  contract.
- `Future`: documented direction, not required for current implementation.
- `Deprecated`: retained only to explain old behaviour.

## Reading Order

1. `terminology.md`
2. `functional-requirements.md`
3. `workflows.md`
4. `receiver-behaviour.md`
5. `session-artifacts.md`
6. `android-runtime.md`
7. `security-privacy.md`
8. `ui-requirements.md`
9. `verification-matrix.md`
10. `traceability.md`

## How To Change The App

Every non-trivial feature or fix should identify affected requirement IDs before
implementation. If no requirement exists, update this specification first or in
the same branch. Update `verification-matrix.md` and
`docs/superpowers/plan-status.md` when implementation status changes.
```

- [ ] **Step 2: Create `docs/specification/templates/requirement-template.md`**

Use this content:

````markdown
# Requirement Template

```markdown
### <ID>: <Short Requirement Name>

Status: Normative | Experimental | Future | Deprecated

The app MUST/SHOULD/MAY <precise requirement>.

Rationale:
<Why this requirement exists.>

Applies to:
- <Workflow, receiver, UI area, session artifact or runtime component.>

Verification:
- Automated: <test name or planned test>.
- Manual: <field or device check>.
- Review: <code/design review criterion>.

Traceability:
- Source: <Superpowers spec/plan/doc/user decision>.
```
````

- [ ] **Step 3: Create `docs/specification/templates/verification-row-template.md`**

Use this content:

````markdown
# Verification Matrix Row Template

```markdown
| Requirement ID | Verification type | Evidence | Status | Notes |
| --- | --- | --- | --- | --- |
| `<ID>` | Automated / Manual / Review / Sample replay | `<test/file/device/session>` | Passing / Not run / Missing / Field-tested | `<short note>` |
```
````

- [ ] **Step 4: Commit**

```bash
git add docs/specification/index.md docs/specification/templates/requirement-template.md docs/specification/templates/verification-row-template.md
git commit -m "Add formal specification entry point"
```

---

## Task 2: Define Terminology And Core Non-Negotiable Requirements

**Files:**
- Create: `docs/specification/terminology.md`
- Create: `docs/specification/functional-requirements.md`

- [ ] **Step 1: Create `docs/specification/terminology.md`**

Use this content:

```markdown
# Terminology

## Receiver RX

Bytes emitted by the GNSS receiver and read by RtkCollector. These bytes are
recorded byte-exactly in `receiver-rx.raw`.

## App TX To Receiver

Bytes transmitted by RtkCollector toward the receiver serial input. This
includes init commands, shutdown commands and correction bytes sent to the
receiver. These bytes are recorded separately from receiver RX.

## Correction Input

Bytes received by RtkCollector from NTRIP or another correction source before
they are forwarded to a receiver or advisory solution engine.

## Temporary Base

A stationary receiver session used to determine a coordinate from RTK against
another base, PPP, static/post-processing or fallback averaging.

## Fixed Base

A receiver configured with an already accepted coordinate to produce base
corrections. Fixed-base operation is separate from temporary-base coordinate
determination.

## Device Internal Solution

The receiver's own navigation solution, such as UM980 BESTNAV/ADRNAV/PPPNAV or
u-blox NAV-PVT-derived status.

## In-Phone Solution

A solution computed by the Android device, such as future RTKLIB real-time
processing. V1 does not require in-phone RTKLIB.
```

- [ ] **Step 2: Create `docs/specification/functional-requirements.md`**

Use this initial content:

```markdown
# Functional Requirements

## Capture Architecture

### ARCH-RAW-001: Receiver RX Raw Stream Is Authoritative

Status: Normative

The app MUST record receiver-originated bytes into `receiver-rx.raw` without
inserting timestamps, markers, parsed fields or app metadata.

Rationale:
The raw receiver stream is the source of truth for replay, diagnostics and
post-processing.

Verification:
- Automated: session writer byte-preservation test.
- Manual: replay a known byte stream and compare output bytes.
- Review: parser, UI, NTRIP and RTKLIB paths must not write into
  `receiver-rx.raw`.

### ARCH-RAW-002: Advisory Failures Must Not Stop Raw Capture

Status: Normative

Parser, UI, NTRIP, quality-monitor and future RTKLIB failures MUST NOT stop or
mutate raw recording while transport and storage still function.

Verification:
- Automated: parser-failure isolation test.
- Manual: run recording with malformed mixed stream and confirm raw file grows.
- Review: capture loop does not wait on advisory consumers.

### ARCH-TX-001: App TX Is Recorded Separately

Status: Normative

Commands and correction bytes transmitted from RtkCollector to the receiver MUST
NOT be written into `receiver-rx.raw`. They MUST be recorded in
`tx-to-receiver.raw` or an explicitly documented TX artifact.

Verification:
- Automated: session artifact writer tests.
- Review: all serial write paths pass through TX recording or documented
  exceptions.

### ARCH-CORR-001: Correction Input Is Recorded Separately

Status: Normative

NTRIP or other correction-source bytes received by the app SHOULD be recorded in
`correction-input.raw` before or while they are forwarded to the receiver.

Verification:
- Automated: NTRIP runtime/session writer tests.
- Manual: rover + NTRIP session contains usable RTCM3 correction stream.

## Product Boundaries

### PRODUCT-NONGOAL-001: No GIS Application Scope

Status: Normative

The app MUST NOT introduce maps, shapefiles, GIS editing, field-feature
collection or cartographic survey project-management dependencies.

Verification:
- Review: dependency and UI review for every feature branch.
```

- [ ] **Step 3: Commit**

```bash
git add docs/specification/terminology.md docs/specification/functional-requirements.md
git commit -m "Define formal terminology and core requirements"
```

---

## Task 3: Add Workflow And Receiver Formal Specs

**Files:**
- Create: `docs/specification/workflows.md`
- Create: `docs/specification/receiver-behaviour.md`

- [ ] **Step 1: Create `docs/specification/workflows.md`**

Use this initial content:

```markdown
# Workflow Requirements

## WF-ROVER-001: Plain Rover Recording

Status: Normative

Plain rover recording MUST use receiver role `ROVER`, no correction source and
no correction targets. It SHOULD request raw observations where the selected
receiver supports them.

Verification:
- Automated: workflow validator tests.
- Manual: start plain rover without NTRIP and confirm no NTRIP connection opens.

## WF-ROVER-NTRIP-001: Rover With NTRIP To Receiver

Status: Normative

Rover + NTRIP MUST run Android as the NTRIP client, receive RTCM correction
bytes, record correction input, transmit correction bytes unchanged toward the
receiver and keep receiver-internal solution separate from future in-phone
solutions.

Verification:
- Automated: NTRIP client/request/routing tests.
- Manual: EUREF/CORS mountpoint session reaches receiver RTK float/fix.

## WF-TEMPBASE-001: Temporary Base Is Coordinate Determination

Status: Normative

Temporary-base recording is a stationary coordinate-determination workflow. It
MAY determine coordinates from RTK against another base, PPP, static
post-processing or fallback averaging. It MUST NOT be treated as equivalent to
fixed-base operation until a coordinate is accepted.

Verification:
- Automated: workflow validator and base-coordinate state tests.
- Manual: temporary-base session can accept an averaged/current coordinate and
transition toward fixed-base use.

## WF-FIXEDBASE-001: Fixed Base Requires Accepted Coordinate

Status: Normative

Fixed-base operation MUST require an accepted base coordinate from manual entry,
imported base-position file or accepted temporary-base result. It MUST NOT start
from an unaccepted temporary-base session.

Verification:
- Automated: workflow validator test.
- Manual: fixed-base UI shows the exact coordinate before start.

## WF-RTKLIB-001: In-Phone RTKLIB Is Future Work

Status: Future

In-phone RTKLIB real-time solution is not required for V1. When implemented, it
MUST remain a separate solution engine from receiver-internal solution and MUST
NOT become part of the raw capture path.

Verification:
- Review: no V1 feature requires RTKLIB to record, feed NTRIP or prepare a base.
```

- [ ] **Step 2: Create `docs/specification/receiver-behaviour.md`**

Use this initial content:

```markdown
# Receiver Behaviour Requirements

## RX-UM980-001: UM980 Mixed Stream Parser Is Advisory

Status: Normative

UM980 parsing MUST tolerate mixed NMEA, UM980 ASCII, UM980 binary and RTCM-like
bytes without feeding arbitrary binary data into line-only parsers.

Verification:
- Automated: UM980 mixed-stream parser tests.
- Manual: binary UM980 session updates dashboard while `receiver-rx.raw`
  remains byte-exact.

## RX-UM980-RTK-001: UM980 RTK Status Sources

Status: Normative

UM980 in-device RTK monitoring SHOULD use BESTNAV/ADRNAV, RTKSTATUS and
RTCMSTATUS when available. RTK state MUST remain separate from PPP status and
future RTKLIB status.

Verification:
- Automated: BESTNAV/ADRNAV/RTKSTATUS/RTCMSTATUS parser tests.
- Manual: rover + NTRIP session shows RTK float/fix and decoded RTCM state.

## RX-UM980-PPP-001: UM980 PPP Status Requires Explicit PPP Telemetry

Status: Normative

The dashboard MUST NOT infer PPP convergence from generic position updates.
PPP status MUST come from explicit PPP telemetry such as PPPNAV when available,
or show that PPP telemetry is unavailable.

Verification:
- Automated: PPPNAV parser and dashboard mapping tests.
- Manual: PPP-enabled UM980 recording distinguishes no PPP telemetry, no PPP
  solution, converging and converged states.

## RX-UBLOX-M8T-001: M8T Is Raw/Timing/Post-Processing Receiver

Status: Normative

u-blox M8T support MUST treat M8T as a raw/timing/post-processing receiver, not
as an internal RTK float/fix rover like M8P.

Verification:
- Automated: receiver capability tests.
- Manual: M8T profile records UBX RAWX/SFRBX/TIM where configured.
```

- [ ] **Step 3: Commit**

```bash
git add docs/specification/workflows.md docs/specification/receiver-behaviour.md
git commit -m "Add formal workflow and receiver requirements"
```

---

## Task 4: Add Session, Android Runtime, Security And UI Specs

**Files:**
- Create: `docs/specification/session-artifacts.md`
- Create: `docs/specification/android-runtime.md`
- Create: `docs/specification/security-privacy.md`
- Create: `docs/specification/ui-requirements.md`

- [ ] **Step 1: Create `docs/specification/session-artifacts.md`**

Use this initial content:

```markdown
# Session Artifact Requirements

## SESSION-FILES-001: Required Artifact Separation

Status: Normative

Sessions MUST distinguish receiver RX, app TX to receiver, correction input,
events, quality logs, generated solution exports and metadata as separate
artifacts.

Verification:
- Automated: session writer tests.
- Manual: completed session contains expected files for selected recording
  outputs.

## SESSION-NMEA-001: Generated NMEA Preserves Sub-Second UTC

Status: Normative

Generated `receiver-solution.nmea` MUST preserve sub-second UTC where source
telemetry provides it. It MUST NOT copy binary/noise fragments that merely look
like dollar-prefixed NMEA lines.

Verification:
- Automated: UM980 NMEA exporter/re-exporter tests.
- Manual: compare generated NMEA against known high-rate recording.
```

- [ ] **Step 2: Create `docs/specification/android-runtime.md`**

Use this initial content:

```markdown
# Android Runtime Requirements

## ANDROID-SERVICE-001: Foreground Service Owns Recording

Status: Normative

The foreground service MUST own recording, wake lock, transport access and
session writers. Activity and Compose UI MAY control and observe recording but
MUST NOT own the capture lifecycle.

Verification:
- Review: recording loop and writers live in service/runtime code.
- Manual: recording continues with screen off and app backgrounded.

## ANDROID-USB-001: USB Permission And Open Access Are Separate Gates

Status: Normative

The app MUST treat Android USB permission and actual device-open success as
separate checks. If Android reports permission but opening fails, the user must
receive a clear actionable error.

Verification:
- Automated: USB permission decision model tests.
- Manual: Huawei/vendor Android USB smoke test.
```

- [ ] **Step 3: Create `docs/specification/security-privacy.md`**

Use this initial content:

```markdown
# Security And Privacy Requirements

## SEC-SECRETS-001: Session Metadata Excludes Secrets

Status: Normative

`session.json` MUST NOT contain plaintext NTRIP passwords, tokens or raw
credentials. It may contain redacted metadata or secret references.

Verification:
- Automated: session metadata and settings-export tests.
- Review: all session metadata writers redact credentials.

## SEC-IMPORT-001: Imported Settings Are Validated

Status: Normative

Imported JSON settings MUST be structurally validated before use. Plaintext
password import MUST be explicit and user-visible.

Verification:
- Automated: settings import validation tests.
- Manual: Android "open JSON with RtkCollector" import flow.
```

- [ ] **Step 4: Create `docs/specification/ui-requirements.md`**

Use this initial content:

```markdown
# UI Requirements

## UI-DASH-001: Recording Controls Remain Reachable

Status: Normative

Start/Stop, Menu and core runtime actions MUST remain reachable in portrait and
landscape layouts, including devices with navigation bars and tablets with
hardware keyboards.

Verification:
- Manual: phone portrait, tablet landscape and navigation-bar layout checks.
- Review: controls are not buried inside vertically scrollable telemetry
  content.

## UI-DASH-002: Dashboard Values Must Not Jump Layout

Status: Normative

Live telemetry updates MUST NOT cause dashboard cards to resize or jump in a way
that disrupts operation.

Verification:
- Manual: live UM980 session with high-rate position updates.
- Review: fixed/minimum card dimensions and compact formatting.

## UI-DOCS-001: User Documentation Is Separate From Formal Specs

Status: Normative

User-facing docs MUST explain workflows in task-oriented language for new users
and power users. Formal specs MUST remain separate and may use precise
requirements language.

Verification:
- Review: user docs do not become requirement matrices; formal specs do not
  become tutorials.
```

- [ ] **Step 5: Commit**

```bash
git add docs/specification/session-artifacts.md docs/specification/android-runtime.md docs/specification/security-privacy.md docs/specification/ui-requirements.md
git commit -m "Add formal runtime security session and UI specs"
```

---

## Task 5: Add Verification Matrix And Traceability

**Files:**
- Create: `docs/specification/verification-matrix.md`
- Create: `docs/specification/traceability.md`

- [ ] **Step 1: Create `docs/specification/verification-matrix.md`**

Use this initial content:

```markdown
# Verification Matrix

| Requirement ID | Verification type | Evidence | Status | Notes |
| --- | --- | --- | --- | --- |
| `ARCH-RAW-001` | Automated + manual | Session writer tests; replay comparison | Needs review | Confirm current tests cover byte-for-byte replay. |
| `ARCH-RAW-002` | Automated + manual | Parser-failure isolation tests; malformed stream session | Needs review | Confirm parser exceptions cannot stop raw capture. |
| `ARCH-TX-001` | Automated + review | TX artifact tests; serial write path review | Needs review | Include correction injection and command scripts. |
| `ARCH-CORR-001` | Automated + manual | NTRIP runtime tests; rover+NTRIP session | Needs review | Verify `correction-input.raw` is usable RTCM3. |
| `WF-ROVER-001` | Automated + manual | Workflow validator tests; plain rover smoke test | Needs review | Confirm no NTRIP connection opens. |
| `WF-ROVER-NTRIP-001` | Automated + manual | NTRIP tests; EUREF/CORS field session | Needs review | Confirm RTK float/fix with correction recording. |
| `WF-TEMPBASE-001` | Automated + manual | Base averaging/acceptance tests; temporary-base field test | Missing | Open implementation area. |
| `WF-FIXEDBASE-001` | Automated + manual | Workflow validator; fixed-base coordinate display/start test | Missing | Open implementation area. |
| `WF-RTKLIB-001` | Review | V1 code review | Passing | RTKLIB remains future/V2. |
| `RX-UM980-PPP-001` | Automated + manual | PPPNAV parser/dashboard tests; debug sessions | In progress | Open implementation area. |
| `RX-UBLOX-M8T-001` | Automated + hardware | u-blox parser/capability tests; M8T device test | Not field-tested | First implementation exists. |
| `SESSION-NMEA-001` | Automated + sample replay | NMEA exporter/re-exporter tests; high-rate samples | Needs review | Verify sub-second UTC preservation. |
| `ANDROID-USB-001` | Automated + device | USB decision tests; Huawei P30 Pro smoke test | Needs field retest | Vendor Android edge case. |
| `SEC-SECRETS-001` | Automated + review | Session/settings export tests | Needs review | Include plaintext password export option. |
| `UI-DASH-001` | Manual + review | Phone/tablet portrait/landscape screenshots | Needs review | Visual fidelity and nav-bar checks. |
```

- [ ] **Step 2: Create `docs/specification/traceability.md`**

Use this initial content:

```markdown
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
| `docs/superpowers/plans/2026-06-14-google-play-readiness.md` | `security-privacy.md`, `android-runtime.md` | Publication, privacy and permission requirements. |
| `docs/superpowers/plans/2026-06-16-base-workflow-coordinate-actions.md` | `workflows.md`, `ui-requirements.md` | Temporary-base coordinate actions. |
```

- [ ] **Step 3: Commit**

```bash
git add docs/specification/verification-matrix.md docs/specification/traceability.md
git commit -m "Add formal verification and traceability matrices"
```

---

## Task 6: Update Agent And Contributor Guidance

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/contributor-onboarding.md`
- Modify: `README.md`

- [ ] **Step 1: Update `AGENTS.md`**

Add under the existing formal-specification paragraph:

```markdown
When changing behaviour, identify affected formal requirement IDs from
`docs/specification/` before implementation. If the behaviour is not covered,
add or update formal requirements first or in the same branch. Keep
`docs/specification/verification-matrix.md` aligned with tests, manual checks
and known gaps.
```

- [ ] **Step 2: Update `docs/contributor-onboarding.md`**

Add a short section after the existing spec-first workflow section:

```markdown
## Formal Specification Workflow

The canonical requirements live in `docs/specification/`. Superpowers specs and
plans are design history and implementation history. For non-trivial changes:

1. Find affected requirement IDs.
2. Update formal specs if behaviour changes.
3. Update user-facing docs separately when users need instructions.
4. Update `docs/specification/verification-matrix.md`.
5. Update `docs/superpowers/plan-status.md` if large-plan status changes.
```

- [ ] **Step 3: Update `README.md`**

Add a concise contributor pointer near the development status section:

```markdown
Formal contributor-facing requirements live in `docs/specification/`. User
workflow documentation lives in `docs/user-workflows.md`. Superpowers specs and
plans are retained as design and implementation history under
`docs/superpowers/`.
```

- [ ] **Step 4: Commit**

```bash
git add AGENTS.md docs/contributor-onboarding.md README.md
git commit -m "Document formal specification workflow"
```

---

## Task 7: Update Plan Status

**Files:**
- Modify: `docs/superpowers/plan-status.md`

- [ ] **Step 1: Add this row to the status table**

```markdown
| Formal specification system | `2026-06-17-formal-specification-system.md` | Open | Create canonical `docs/specification/` requirements, traceability and verification matrix separate from user-facing documentation and Superpowers history. |
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/plan-status.md
git commit -m "Track formal specification system plan"
```

---

## Task 8: Optional Lightweight Spec Checker

Do this only after Tasks 1-7 are complete and reviewed. Skip this task if the
team wants to keep the first pass documentation-only.

**Files:**
- Create: `tools/check_spec_requirements.py`
- Create: `tools/test_check_spec_requirements.py`

- [ ] **Step 1: Write tests for duplicate IDs and missing matrix references**

Create `tools/test_check_spec_requirements.py`:

```python
from pathlib import Path

from check_spec_requirements import find_requirement_ids, find_matrix_ids, validate_spec_tree


def test_find_requirement_ids_detects_heading_ids(tmp_path):
    spec = tmp_path / "spec.md"
    spec.write_text("### ARCH-RAW-001: Raw stream\n\n### WF-ROVER-001: Rover\n", encoding="utf-8")

    assert find_requirement_ids([spec]) == {"ARCH-RAW-001": [spec], "WF-ROVER-001": [spec]}


def test_validate_spec_tree_rejects_duplicate_requirement_ids(tmp_path):
    first = tmp_path / "a.md"
    second = tmp_path / "b.md"
    matrix = tmp_path / "verification-matrix.md"
    first.write_text("### ARCH-RAW-001: Raw stream\n", encoding="utf-8")
    second.write_text("### ARCH-RAW-001: Duplicate\n", encoding="utf-8")
    matrix.write_text("| Requirement ID |\n| --- |\n| `ARCH-RAW-001` |\n", encoding="utf-8")

    errors = validate_spec_tree(tmp_path)

    assert any("Duplicate requirement ID ARCH-RAW-001" in error for error in errors)


def test_validate_spec_tree_rejects_matrix_id_without_requirement(tmp_path):
    spec = tmp_path / "spec.md"
    matrix = tmp_path / "verification-matrix.md"
    spec.write_text("### ARCH-RAW-001: Raw stream\n", encoding="utf-8")
    matrix.write_text("| Requirement ID |\n| --- |\n| `WF-MISSING-001` |\n", encoding="utf-8")

    errors = validate_spec_tree(tmp_path)

    assert any("Matrix references missing requirement ID WF-MISSING-001" in error for error in errors)
```

- [ ] **Step 2: Implement checker**

Create `tools/check_spec_requirements.py`:

```python
#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path


REQUIREMENT_HEADING = re.compile(r"^###\s+([A-Z][A-Z0-9]+(?:-[A-Z0-9]+)+-\d{3}):\s+", re.MULTILINE)
MATRIX_ID = re.compile(r"`([A-Z][A-Z0-9]+(?:-[A-Z0-9]+)+-\d{3})`")


def markdown_files(root: Path) -> list[Path]:
    return sorted(path for path in root.rglob("*.md") if path.is_file())


def find_requirement_ids(files: list[Path]) -> dict[str, list[Path]]:
    found: dict[str, list[Path]] = defaultdict(list)
    for path in files:
        if path.name == "verification-matrix.md":
            continue
        text = path.read_text(encoding="utf-8")
        for match in REQUIREMENT_HEADING.finditer(text):
            found[match.group(1)].append(path)
    return dict(found)


def find_matrix_ids(matrix: Path) -> set[str]:
    if not matrix.exists():
        return set()
    return set(MATRIX_ID.findall(matrix.read_text(encoding="utf-8")))


def validate_spec_tree(root: Path) -> list[str]:
    files = markdown_files(root)
    requirements = find_requirement_ids(files)
    matrix_ids = find_matrix_ids(root / "verification-matrix.md")
    errors: list[str] = []

    for requirement_id, paths in sorted(requirements.items()):
        if len(paths) > 1:
            locations = ", ".join(str(path) for path in paths)
            errors.append(f"Duplicate requirement ID {requirement_id}: {locations}")

    for matrix_id in sorted(matrix_ids):
        if matrix_id not in requirements:
            errors.append(f"Matrix references missing requirement ID {matrix_id}")

    for requirement_id in sorted(requirements):
        if requirement_id not in matrix_ids:
            errors.append(f"Requirement ID {requirement_id} missing from verification matrix")

    return errors


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("docs/specification")
    errors = validate_spec_tree(root)
    for error in errors:
        print(error)
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 3: Run tests**

```bash
python3 -m pytest tools/test_check_spec_requirements.py
python3 tools/check_spec_requirements.py docs/specification
```

Expected: tests pass and checker exits 0 after the matrix covers all initial
IDs.

- [ ] **Step 4: Commit**

```bash
git add tools/check_spec_requirements.py tools/test_check_spec_requirements.py
git commit -m "Add formal specification consistency checker"
```

---

## Validation

Run after documentation tasks:

```bash
git diff --check
rg -n "TBD|TODO|fill in|implement later" docs/specification
```

Expected:

- `git diff --check` reports no whitespace errors.
- The `rg` command should not find unresolved placeholders in formal spec files.
  If it finds the word "Future" as normative status text, that is acceptable.

Run after optional checker task:

```bash
python3 -m pytest tools/test_check_spec_requirements.py
python3 tools/check_spec_requirements.py docs/specification
```

Expected: all commands pass.

Do not run Android packaging tasks for this documentation-only plan.

---

## Self-Review

- Spec coverage: This plan creates a canonical formal spec tree, user-doc
  separation, traceability, verification matrix, agent guidance and plan-status
  tracking.
- Scope check: This is documentation/governance infrastructure. It intentionally
  does not complete all app requirements or implement RTKLIB/PPP/base features.
- Placeholder scan: The planned initial files avoid unresolved implementation
  markers.
- Type/name consistency: Requirement IDs use the declared prefixes and are
  referenced by the verification matrix.

---

## Execution Handoff

Plan complete and saved to
`docs/superpowers/plans/2026-06-17-formal-specification-system.md`.

Recommended execution:

1. **Subagent-Driven**: one agent creates the formal spec tree, one reviews
   traceability against existing docs/Superpowers plans, one reviews
   contributor guidance.
2. **Inline Execution**: execute Tasks 1-7 sequentially, then decide whether
   Task 8 is worth doing in the same pass.
