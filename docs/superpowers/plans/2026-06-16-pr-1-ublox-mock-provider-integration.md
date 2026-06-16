# PR 1 u-blox M8T Mock Provider Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve, review, validate and merge GitHub PR #1, `feat/ublox-m8t-mock-provider`, into current `main` without regressing UM980 recording, SAF session management, raw capture invariants or Android background recording.

**Architecture:** Use a dedicated integration worktree/branch based on current `origin/main`, merge the PR branch into it, resolve conflicts there, then review by subsystem. Keep raw capture and storage changes from current `main` authoritative while integrating the PR's u-blox M8T, best-solution and mock-location features.

**Tech Stack:** Git/GitHub CLI, Gradle Kotlin/Android modules, Superpowers review workflow, Android foreground service, `core:solution`, `receiver:ublox-m8`, `receiver:unicore-n4`, app recording/profile/UI modules.

---

## Current PR State

- GitHub PR: `#1 Add u-blox M8T support and Android mock-location publishing`
- Head branch: `feat/ublox-m8t-mock-provider`
- Base branch: `main`
- Current GitHub merge state: `CONFLICTING / DIRTY`
- GitHub status checks: none reported
- Size: 46 changed files, approximately 4,899 additions
- High-risk files touched by the PR:
  - `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
  - `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  - `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
  - `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
  - `settings.gradle.kts`
  - `receiver/ublox-m8/**`
  - `receiver/unicore-n4/**`
  - new `core:solution` module

## Non-Negotiable Integration Rules

- Do not merge PR #1 directly from GitHub while it is conflicting.
- Resolve conflicts in an isolated integration worktree/branch.
- Preserve current `main` behaviour for SAF session browsing/actions and NMEA regeneration.
- Preserve raw capture invariants:
  - `receiver-rx.raw` remains byte-exact;
  - app TX/corrections stay out of receiver RX;
  - parser, mock-location and best-solution failures do not stop recording when transport/storage still work.
- Mock location must be disabled by default and must fail visibly but non-fatally if Android developer settings do not permit it.
- u-blox M8T command/profile work must not send UM980-specific baud-switch commands to u-blox receivers.
- Do not delete or overwrite local untracked folders such as `.codex-tmp/`, `.superpowers/` or `samples/`.

## Checkpoint Model

Every task below is a resumable checkpoint. At the end of each task:

1. Run the task's validation command.
2. Commit the checkpoint on the integration branch.
3. Record unresolved risks in the commit body.
4. Do not push or merge until Task 10.

If interrupted, resume by checking:

```sh
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector status --short
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector log --oneline --decorate -8
```

---

### Task 1: Create Integration Worktree

**Files:**
- No source files modified.
- Worktree path: `../rtkcollector-pr1-integration`
- Branch: `codex/integrate-pr-1-ublox-mock-provider`

- [ ] **Step 1: Verify current main is clean except local artifacts**

Run:

```sh
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector status --short
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector branch --show-current
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector rev-parse HEAD
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector rev-parse origin/main
```

Expected:
- branch is `main`;
- `HEAD` equals `origin/main`;
- only allowed untracked local artifacts are present.

- [ ] **Step 2: Fetch PR branch**

Run:

```sh
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector fetch origin main feat/ublox-m8t-mock-provider
```

Expected: fetch succeeds.

- [ ] **Step 3: Create isolated worktree**

Run:

```sh
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector worktree add ../rtkcollector-pr1-integration -b codex/integrate-pr-1-ublox-mock-provider origin/main
```

Expected: worktree is created on `codex/integrate-pr-1-ublox-mock-provider`.

- [ ] **Step 4: Confirm worktree state**

Run:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration status --short
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration branch --show-current
```

Expected:
- status is clean;
- branch is `codex/integrate-pr-1-ublox-mock-provider`.

- [ ] **Step 5: Commit checkpoint only if any local plan bookkeeping was changed**

No integration commit is expected in this task.

---

### Task 2: Merge PR Branch And Resolve Conflicts

**Files likely modified by conflict resolution:**
- `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
- `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
- `app/src/main/kotlin/org/rtkcollector/app/profile/SettingsSetModels.kt`
- `docs/user-workflows.md`
- `settings.gradle.kts`

- [ ] **Step 1: Merge PR branch into integration branch**

Run:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration merge --no-ff origin/feat/ublox-m8t-mock-provider
```

Expected:
- merge reports conflicts;
- no files are resolved automatically without inspection if high-risk files conflict.

- [ ] **Step 2: List conflicts**

Run:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration diff --name-only --diff-filter=U
```

Expected: list every conflicted file. Save this list in the task notes or commit body.

- [ ] **Step 3: Resolve `settings.gradle.kts`**

Resolution rule:
- keep all current modules from `main`;
- add `:core:solution` if it is not already present;
- do not remove `:receiver:ublox-m8`, `:receiver:unicore-n4`, app or core modules.

Validation:

```sh
rg -n "core:solution|receiver:ublox-m8|receiver:unicore-n4|app" ../rtkcollector-pr1-integration/settings.gradle.kts
```

Expected: all listed modules are present exactly once.

- [ ] **Step 4: Resolve profile model/store conflicts**

Resolution rules:
- preserve settings-set, SAF storage, recording-output and NTRIP profile fields already on `main`;
- add PR's `enableMockLocation` recording-policy field;
- preserve JSON import/export compatibility for all fields;
- default `enableMockLocation` to `false`.

Validation:

```sh
rg -n "enableMockLocation|pppNmeaGgaQuality|SAF_TREE|lastActiveNtripMountpoint" ../rtkcollector-pr1-integration/app/src/main/kotlin/org/rtkcollector/app/profile
```

Expected:
- `enableMockLocation` appears in profile model, defaults and active config plumbing;
- existing PPP NMEA and SAF fields remain present.

- [ ] **Step 5: Resolve `MainActivity.kt` conflicts**

Resolution rules:
- preserve current `main` SAF session management actions:
  - `SafSessionBrowser`;
  - `SafSessionActions`;
  - NMEA regeneration progress bar;
  - filesystem/SAF action capability handling.
- add PR's mock-location profile editor field if missing.
- add PR's best-solution/mock-location dashboard display fields without removing existing dashboard tiles.
- keep all Start/Stop, USB permission and NTRIP controls visible in portrait and landscape.

Validation:

```sh
rg -n "SafSessionActions|SafSessionBrowser|enableMockLocation|bestSolution|mockLocation" ../rtkcollector-pr1-integration/app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
```

Expected: all identifiers are present where appropriate.

- [ ] **Step 6: Resolve `RecordingForegroundService.kt` conflicts**

Resolution rules:
- foreground service remains the owner of recording;
- raw receiver RX writer remains independent of best-solution, mock-location and parser failures;
- mock-location publication runs only when enabled by recording policy;
- mock-location errors update state and events but do not stop recording;
- preserve current NTRIP, USB reconnect, notification and SAF writer behaviour from `main`.

Validation:

```sh
rg -n "enableMockLocation|MockLocationPublisher|BestSolutionTickLogic|appendReceiverRx|appendCorrectionInput|broadcast" ../rtkcollector-pr1-integration/app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
```

Expected:
- raw append path still exists;
- best-solution/mock-location code is present but advisory.

- [ ] **Step 7: Resolve docs conflicts**

Resolution rules:
- preserve current SAF/session-management documentation;
- add u-blox M8T and mock-provider workflow documentation;
- keep explicit non-goals: no maps, shapefiles, GIS editing or feature collection.

Validation:

```sh
rg -n "SAF|u-blox|M8T|mock location|maps|shapefiles|GIS" ../rtkcollector-pr1-integration/README.md ../rtkcollector-pr1-integration/docs
```

Expected: relevant docs mention both current SAF parity and PR features.

- [ ] **Step 8: Verify conflicts are fully resolved**

Run:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration diff --name-only --diff-filter=U
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration status --short
```

Expected:
- no unresolved conflict files;
- only intentional modified/added files remain.

- [ ] **Step 9: Compile checkpoint**

Run:

```sh
cd ../rtkcollector-pr1-integration
sh gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit conflict-resolution checkpoint**

Run:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration add .
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration commit -m "Resolve PR 1 integration conflicts"
```

Commit body must mention:
- conflict files;
- SAF/session-management preservation;
- mock-location default-off rule;
- validation command result.

---

### Task 3: Validate Pure Kotlin Receiver And Solution Modules

**Files under review:**
- `core/solution/**`
- `receiver/ublox-m8/**`
- `receiver/unicore-n4/**`

- [ ] **Step 1: Run core solution tests**

Run:

```sh
cd ../rtkcollector-pr1-integration
sh gradlew :core:solution:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run u-blox tests**

Run:

```sh
cd ../rtkcollector-pr1-integration
sh gradlew :receiver:ublox-m8:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run UM980 tests**

Run:

```sh
cd ../rtkcollector-pr1-integration
sh gradlew :receiver:unicore-n4:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Inspect test coverage names**

Run:

```sh
find ../rtkcollector-pr1-integration/core/solution/src/test ../rtkcollector-pr1-integration/receiver/ublox-m8/src/test ../rtkcollector-pr1-integration/receiver/unicore-n4/src/test -type f | sort
```

Expected coverage includes:
- best-solution selector;
- UBX frame compiler/parser;
- u-blox NAV-PVT RTK state mapping;
- u-blox frequency tracking;
- UM980 solution adapter;
- NMEA GGA candidate adapter.

- [ ] **Step 5: Commit validation-only fixes if required**

If tests required fixes, commit:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration add core/solution receiver/ublox-m8 receiver/unicore-n4
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration commit -m "Stabilize receiver solution integration tests"
```

Expected: commit only if code/tests changed.

---

### Task 4: Review GNSS/RTK Correctness

**Files under review:**
- `core/solution/src/main/kotlin/org/rtkcollector/core/solution/**`
- `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/**`
- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980SolutionAdapter.kt`
- `app/src/main/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogic.kt`

- [ ] **Step 1: Use `superpowers:requesting-code-review`**

Reviewer role:

```text
Review the integrated PR #1 branch as a GNSS/RTK systems architect.
Focus on best-solution selection, u-blox M8T capability modelling, NAV-PVT fix mapping, UM980 solution adapter compatibility, stale solution handling and mock-provider source quality.
Do not implement new features. Report must-fix, should-fix and acceptable limitations.
```

- [ ] **Step 2: Verify u-blox M8T is not modelled as internal RTK like M8P**

Run:

```sh
rg -n "M8T|supportsInternalRtk|RTK_FIXED|RTK_FLOAT|RAWX|SFRBX" ../rtkcollector-pr1-integration/receiver/ublox-m8 ../rtkcollector-pr1-integration/docs
```

Expected:
- M8T raw/timing/post-processing identity remains distinct;
- NAV-PVT RTK carrier solution is parsed when present but does not turn M8T into an M8P-style internal RTK receiver profile unless explicitly documented.

- [ ] **Step 3: Verify stale solution policy**

Run:

```sh
rg -n "maxAge|isFresh|STALE|NOT_PERMITTED|NO_SOLUTION|BestSolutionTickLogic" ../rtkcollector-pr1-integration/core/solution ../rtkcollector-pr1-integration/app/src/main/kotlin
```

Expected:
- stale solution does not continue publishing mock GPS;
- stale state reaches dashboard/service state.

- [ ] **Step 4: Apply only must-fix GNSS feedback**

Use `superpowers:receiving-code-review` before editing. For every code fix:
- write or update a test first;
- run the relevant targeted Gradle test.

- [ ] **Step 5: Commit GNSS review fixes**

Run:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration status --short
```

If changes exist:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration add core/solution receiver/ublox-m8 receiver/unicore-n4 app/src/main/kotlin/org/rtkcollector/app/recording/BestSolutionTickLogic.kt app/src/test
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration commit -m "Address GNSS solution integration review"
```

---

### Task 5: Review Android Recording Robustness

**Files under review:**
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- `app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt`
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/**`

- [ ] **Step 1: Use `superpowers:requesting-code-review`**

Reviewer role:

```text
Review the integrated PR #1 branch as an Android robust background-capture engineer.
Focus on raw capture authority, parser/solution/mock-location isolation, foreground-service ownership, wake/background safety, mock-location permission failure, stale-state clearing and no regression to SAF/session-management actions.
Do not implement new features. Report must-fix, should-fix and acceptable limitations.
```

- [ ] **Step 2: Inspect service raw path invariants**

Run:

```sh
rg -n "appendReceiverRx|appendTxToReceiver|appendCorrectionInput|MockLocationPublisher|BestSolutionTickLogic|try|catch|RecordingErrorCategory" ../rtkcollector-pr1-integration/app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
```

Expected:
- raw RX append remains in the transport read path;
- mock-location exceptions are caught and reported without stopping recording;
- start-phase receiver command failures remain fatal when appropriate;
- stop-phase failures remain degraded/non-fatal.

- [ ] **Step 3: Verify mock-location permission handling**

Run:

```sh
rg -n "ACCESS_MOCK_LOCATION|addTestProvider|setTestProviderEnabled|removeTestProvider|SecurityException|NOT_PERMITTED" ../rtkcollector-pr1-integration/app/src/main
```

Expected:
- app declares needed mock-location permission;
- SecurityException maps to user-visible non-fatal state.

- [ ] **Step 4: Verify no maps/GIS dependencies entered**

Run:

```sh
rg -n "maps|mapbox|google.maps|shapefile|GIS|feature collection" ../rtkcollector-pr1-integration/app/build.gradle.kts ../rtkcollector-pr1-integration/settings.gradle.kts ../rtkcollector-pr1-integration/app/src/main
```

Expected:
- no map, shapefile or GIS dependency was added.

- [ ] **Step 5: Apply only must-fix Android robustness feedback**

Use `superpowers:receiving-code-review` before editing. Add tests where possible:
- `BestSolutionTickLogicTest` for stale/publish gating;
- `MockLocationPublisherTest` for permission/failure mapping;
- app compile for service wiring.

- [ ] **Step 6: Commit Android review fixes**

If changes exist:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration add app/src/main app/src/test
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration commit -m "Address Android recording robustness review"
```

---

### Task 6: Review Kotlin/API Maintainability

**Files under review:**
- `core/solution/**`
- `receiver/ublox-m8/**`
- profile/config models in `app/src/main/kotlin/org/rtkcollector/app/profile/**`

- [ ] **Step 1: Use `superpowers:requesting-code-review`**

Reviewer role:

```text
Review the integrated PR #1 branch as a Kotlin/domain-model maintainer.
Focus on explicit APIs, module dependency cleanliness, testability, stable names for session/settings export, no production-fixture mixing and no large unnecessary abstractions.
Do not implement new features. Report must-fix, should-fix and acceptable limitations.
```

- [ ] **Step 2: Verify module dependencies**

Run:

```sh
sed -n '1,160p' ../rtkcollector-pr1-integration/core/solution/build.gradle.kts
sed -n '1,160p' ../rtkcollector-pr1-integration/receiver/ublox-m8/build.gradle.kts
sed -n '1,160p' ../rtkcollector-pr1-integration/receiver/unicore-n4/build.gradle.kts
sed -n '1,180p' ../rtkcollector-pr1-integration/app/build.gradle.kts
```

Expected:
- `core:solution` has no Android dependency;
- receiver modules do not depend on app;
- app depends on core/receiver modules, not vice versa.

- [ ] **Step 3: Verify setting export/import stability**

Run:

```sh
rg -n "enableMockLocation|toJson|fromJson|RecordingPolicyProfile|RecordingOutputOverride|SettingsBackupFile" ../rtkcollector-pr1-integration/app/src/main/kotlin/org/rtkcollector/app/profile ../rtkcollector-pr1-integration/app/src/test
```

Expected:
- `enableMockLocation` survives profile/settings serialization;
- default false is covered.

- [ ] **Step 4: Apply only must-fix maintainability feedback**

Use `superpowers:receiving-code-review` before editing. Keep changes small and covered by tests.

- [ ] **Step 5: Commit maintainability review fixes**

If changes exist:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration add core/solution receiver app/src/main app/src/test
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration commit -m "Address API maintainability review"
```

---

### Task 7: Run Integration Validation Matrix

**Files:** No source changes expected unless validation exposes defects.

- [ ] **Step 1: Run pure Kotlin tests**

Run:

```sh
cd ../rtkcollector-pr1-integration
sh gradlew :core:solution:test :receiver:ublox-m8:test :receiver:unicore-n4:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run app Kotlin compile**

Run:

```sh
cd ../rtkcollector-pr1-integration
sh gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Try app unit-test compile only if local Android tooling supports it**

Run:

```sh
cd ../rtkcollector-pr1-integration
sh gradlew :app:compileDebugUnitTestKotlin
```

Expected in Termux may be failure at `:app:processDebugResources` due native x86-64 `aapt2`.

If it fails with:

```text
aapt2: Syntax error: ")" unexpected
```

Record as known Termux tooling limitation, not source failure.

- [ ] **Step 4: Run host/Windows validation before final merge**

On Windows or CI, run:

```powershell
.\gradlew.bat clean
.\gradlew.bat assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat test
```

Expected: all pass. If not available before local merge, do not claim APK-level validation.

- [ ] **Step 5: Commit validation fixes if required**

If any code changes were required:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration add .
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration commit -m "Fix integration validation failures"
```

---

### Task 8: Manual Hardware/Android Acceptance

**Files:** No source changes expected unless manual testing exposes defects.

- [ ] **Step 1: UM980 smoke test**

Manual test:
- select existing UM980 rover + NTRIP workflow;
- start recording;
- verify raw RX grows;
- verify NTRIP streaming and TX-to-receiver continue;
- verify dashboard still shows UM980 position/fix/RTK status;
- stop recording;
- verify session artifacts exist.

Expected:
- no regression from current `main`.

- [ ] **Step 2: M8T smoke test**

Manual test:
- select u-blox M8T command/profile;
- start recording;
- verify no UM980 `CONFIG COM1` command is sent to u-blox;
- verify UBX RXM-RAWX/SFRBX/NAV-PVT or configured M8T messages are recorded;
- stop recording;
- verify raw RX is present.

Expected:
- M8T starts and records without UM980 command failures.

- [ ] **Step 3: Mock location disabled-by-default test**

Manual test:
- keep `enableMockLocation = false`;
- start recording;
- verify no mock-provider publication is attempted;
- verify recording behaves normally.

Expected:
- no mock-location state error when disabled.

- [ ] **Step 4: Mock location not permitted test**

Manual test:
- enable mock location in RtkCollector profile;
- do not select RtkCollector as mock-location app in Android Developer Options;
- start recording.

Expected:
- recording continues;
- dashboard/state reports `NOT_PERMITTED` or equivalent clear error;
- no raw recording failure.

- [ ] **Step 5: Mock location permitted test**

Manual test:
- select RtkCollector as Android mock-location app;
- enable mock location;
- start recording with UM980 or M8T;
- verify mock GPS updates in an external GPS display app.

Expected:
- mock GPS updates around 1 Hz from best fresh in-device solution.

- [ ] **Step 6: Stale/disconnect test**

Manual test:
- while recording with mock enabled, disconnect receiver or antenna long enough to exceed stale window.

Expected:
- dashboard best solution becomes `n/a` or stale;
- mock GPS stops publishing old coordinates or reports stale;
- recording service remains alive if transport/storage state allows.

- [ ] **Step 7: SAF regression smoke test**

Manual test:
- record to SAF storage;
- stop recording;
- open Sessions;
- verify SAF session is listed;
- share ZIP;
- re-export NMEA with progress bar;
- share NMEA;
- do not mutate `receiver-rx.raw`.

Expected:
- SAF parity from current `main` still works after PR integration.

- [ ] **Step 8: Commit manual-test fixes if required**

If code changes were required:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration add .
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration commit -m "Fix manual acceptance issues"
```

---

### Task 9: Prepare Final Integration Result

**Files:** Git metadata only unless docs require final updates.

- [ ] **Step 1: Review final diff against current main**

Run:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration diff --stat origin/main..HEAD
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration diff --name-only origin/main..HEAD
```

Expected:
- diff contains PR #1 features plus conflict/review fixes;
- no local artifacts such as `.codex-tmp/`, `.superpowers/`, `samples/`, SDK files or build outputs.

- [ ] **Step 2: Final status check**

Run:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration status --short
```

Expected: clean.

- [ ] **Step 3: Push integration branch**

Run:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration push -u origin codex/integrate-pr-1-ublox-mock-provider
```

Expected: push succeeds.

- [ ] **Step 4: Update PR #1 or create integration PR**

Preferred if repository permissions allow updating PR branch:

```sh
gh pr comment 1 --repo holubp/rtkcollector --body "Integration branch prepared: codex/integrate-pr-1-ublox-mock-provider. It resolves conflicts against current main and preserves SAF session-management behaviour. Validation results are in the branch commits."
```

If direct PR branch update is desired instead, only force-with-lease after explicit user approval:

```sh
git -C ../rtkcollector-pr1-integration -c safe.directory=/storage/emulated/0/GitHub/rtkcollector-pr1-integration push --force-with-lease origin codex/integrate-pr-1-ublox-mock-provider:feat/ublox-m8t-mock-provider
```

Expected: no force push without explicit approval.

---

### Task 10: Merge

**Files:** Git metadata only.

- [ ] **Step 1: Confirm merge criteria**

Do not merge until all applicable items are true:

- conflict resolution committed;
- `:core:solution:test` passes;
- `:receiver:ublox-m8:test` passes;
- `:receiver:unicore-n4:test` passes;
- `:app:compileDebugKotlin` passes;
- Windows/CI `assembleDebug` passes, or the lack of APK-level validation is explicitly accepted by the user;
- must-fix review findings are closed;
- mock location remains opt-in;
- raw recording invariants are preserved.

- [ ] **Step 2: Choose merge mechanism**

Recommended:

```text
Squash merge PR #1 after updating it with the integration branch.
```

Reason:
- PR has many incremental commits;
- `main` benefits from one coherent feature commit;
- conflict-resolution/review history remains available in the integration branch.

- [ ] **Step 3: Merge with GitHub CLI only after approval**

If PR #1 has been updated and is green:

```sh
gh pr merge 1 --repo holubp/rtkcollector --squash --delete-branch
```

Expected:
- PR #1 merged into `main`;
- feature branch deleted by GitHub if allowed.

- [ ] **Step 4: Refresh local main**

Run:

```sh
git -C /storage/emulated/0/GitHub/rtkcollector -c safe.directory=/storage/emulated/0/GitHub/rtkcollector pull --ff-only origin main
git -C /storage/emulated/0/GitHub/rtkcollector -c safe.directory=/storage/emulated/0/GitHub/rtkcollector log --oneline --decorate -5
```

Expected:
- local `main` includes the squash merge.

- [ ] **Step 5: Cleanup integration worktree after successful merge**

Run:

```sh
git -C /storage/emulated/0/GitHub/rtkcollector -c safe.directory=/storage/emulated/0/GitHub/rtkcollector worktree remove ../rtkcollector-pr1-integration
git -C /storage/emulated/0/GitHub/rtkcollector -c safe.directory=/storage/emulated/0/GitHub/rtkcollector worktree prune
```

Expected:
- integration worktree removed only after merge success.

---

## Final Report Template

Use this exact structure after execution:

```text
Integrated PR #1.

Merge result:
- PR:
- Merge commit / squash commit:
- Branch cleaned:

Validation:
- :core:solution:test:
- :receiver:ublox-m8:test:
- :receiver:unicore-n4:test:
- :app:compileDebugKotlin:
- assembleDebug / app tests:

Manual checks:
- UM980:
- M8T:
- Mock location disabled:
- Mock location not permitted:
- Mock location permitted:
- SAF session management:

Remaining limitations:
- ...
```

## Plan Self-Review

- Spec coverage: covers conflict resolution, subsystem review, validation, manual acceptance, push and merge.
- Placeholder scan: no `TBD`, `TODO` or open-ended implementation steps remain.
- Type consistency: branch names, paths, task names and commands are consistent across tasks.
- Scope check: this plan is intentionally integration-only; it does not implement new u-blox or mock-provider features beyond PR conflict/review fixes.
