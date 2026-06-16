# Base Workflow Coordinate Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Document the permanent/temporary base workflow model and add compact dashboard actions for copying coordinates, accepting current coordinates as a manual base, and guarded coordinate averaging.

**Architecture:** Keep raw recording unchanged. Implement coordinate actions as Compose dashboard callbacks backed by small pure Kotlin model helpers so clipboard formatting and averaging behavior are testable without Android hardware. Treat fixed-base serving as distinct from temporary-base coordinate determination.

**Tech Stack:** Kotlin, Jetpack Compose, Android ClipboardManager, existing profile/settings stores, JUnit/Kotlin tests, Markdown docs.

---

### Task 1: Document Base Workflow Semantics

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/user-workflows.md`
- Modify: `docs/workflows.md`

- [x] Add the two base scenarios:
  - permanent base with manually set precise coordinate;
  - temporary base whose coordinate is determined from external RTK, PPP/static, receiver PPP, or fallback long averaging, then used as fixed base.
- [x] Document that rover workflow plus `MODE BASE` is invalid.
- [x] Document that base/raw/static collection may use rover-mode receiver commands while deriving a coordinate.
- [x] Document compact dashboard icons:
  - copy coordinates;
  - use current coordinate as manual base;
  - start averaging;
  - stop averaging.

### Task 2: Add Coordinate Copy Model

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`

- [x] Add `CoordinateCopyOption` with labels and `format(lat, lon)`.
- [x] Add `PositionCardState.coordinatePairOrNull()` parsing the existing `latLon` display string.
- [x] Test:
  - `geo:lat,lon`;
  - `lat,lon`;
  - `lat`;
  - `lon`;
  - missing coordinates return null.

### Task 3: Add Averaging Model

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`

- [x] Add `CoordinateAveragingState` helper logic.
- [x] `start(fixType, lat, lon)` begins averaging only with non-empty fix and coordinates.
- [x] `add(fixType, lat, lon)` accumulates samples only while fix type is unchanged.
- [x] If fix type changes, return stopped state with message.
- [x] Expose compact status text.

### Task 4: Wire Dashboard Coordinate Actions

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [x] Add callbacks to `HomeDashboard`:
  - `onUseCurrentCoordinateAsManualBase`;
  - `onStartAveraging`;
  - `onStopAveraging`.
- [x] Make the position value clickable and show a small popup with copy options.
- [x] Add compact icon buttons below the coordinate in base workflows:
  - manual base icon;
  - average start/stop icon.
- [x] Keep controls inside the existing Position card and avoid increasing card height unless tests/preview show clipping.

### Task 5: Apply Manual Base Selection

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [x] On “use current coordinate as manual base”, copy current `lat/lon` into a local manual-base selection state.
- [x] Switch selected workflow to `fixed-base`.
- [x] Require explicit user selection of a matching fixed/manual base command profile instead of auto-selecting one.
- [x] Do not write persistent receiver config.
- [x] Do not alter already-recorded session artifacts.

### Task 6: Verify

**Files:**
- Test: affected pure Kotlin tests.

- [x] Run `git diff --check`.
- [x] Run `sh gradlew :app:compileDebugKotlin`.
- [x] Run `sh gradlew :core:solution:test :receiver:unicore-n4:test`.
- [x] If Android app unit tests are blocked by local Termux `aapt2`/`R.jar`, report the exact failure and leave them for Windows/CI.
