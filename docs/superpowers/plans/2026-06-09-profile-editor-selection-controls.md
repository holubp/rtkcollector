# Profile Editor Selection Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace typo-prone profile text fields with constrained selectors and compact dashboard overlays.

**Architecture:** Keep existing profile storage. Extend the generic editor field model with enough typed metadata for dropdowns, checkboxes and read-only lists. Keep capture/service behaviour unchanged.

**Tech Stack:** Kotlin, Android Compose Material3, existing `ProfileStores`, existing Compose dashboard/settings screens.

---

### Task 1: Typed Editor Field Model

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt`

- [ ] Add `readOnly`, `readOnlyList`, and `displayLabel` support to `EditableProfileField`.
- [ ] Render options as dropdowns, booleans as checkboxes, secrets with Show/Hide, and read-only lists as non-editable rows.
- [ ] Add focused model tests for dropdown/check/list field metadata.
- [ ] Run `:app:compileDebugKotlin`.
- [ ] Commit checkpoint.

### Task 2: Profile References And Workflow Modes

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/profile/ProfileStoresTest.kt`

- [ ] Define fixed workflow mode options: `plain-rover`, `rover-ntrip`, `base-calibration`, `fixed-base`.
- [ ] In settings-set editor, render workflow and all profile references as dropdowns populated from current profile stores.
- [ ] Preserve existing save logic by storing selected IDs.
- [ ] Run `:app:compileDebugKotlin`.
- [ ] Commit checkpoint.

### Task 3: Dashboard Overlay Selectors

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`

- [ ] Replace full-screen dashboard selector routes with compact overlay selector state.
- [ ] Mark active row with light green background and dark green `Active` label.
- [ ] Keep Workflow, Receiver and Storage blocked during recording; keep Mountpoint live-update capable.
- [ ] Run `:app:compileDebugKotlin`.
- [ ] Commit checkpoint.

### Task 4: Command And Recording Editors

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt`

- [ ] Remove command editor exposure of legacy pre-runtime `initScript`.
- [ ] Show `runtimeScript` as `Init script`.
- [ ] Keep `Shutdown script` as a normal multiline field, empty if empty.
- [ ] Render recording-output booleans as checkboxes.
- [ ] Ensure active config uses runtime script as init/mode command source consistently with existing tests.
- [ ] Run `:app:compileDebugKotlin`.
- [ ] Commit checkpoint.

### Task 5: NTRIP And USB Editor Cleanup

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModels.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModelsTest.kt`

- [ ] Show NTRIP caster mountpoints as read-only list, not editable multiline text.
- [ ] Let NTRIP mountpoint profile select caster profile from a dropdown.
- [ ] Let mountpoint select from the selected caster's known mountpoint list when available.
- [ ] Replace separate editable USB VID/PID/name/product fields with a single USB device dropdown label.
- [ ] Run `:app:compileDebugKotlin`.
- [ ] Commit checkpoint.

### Task 6: Final Verification And Push

**Files:**
- All touched files.

- [ ] Run `git diff --check`.
- [ ] Run `:app:compileDebugKotlin`.
- [ ] Run pure module tests: `:core:correction:test :core:session:test :core:capture:test :core:workflow:test :receiver:unicore-n4:test`.
- [ ] Attempt app unit tests if local SDK permits; otherwise report Android 36 resource-link blocker.
- [ ] Commit any remaining docs/tests.
- [ ] Push `main`.
