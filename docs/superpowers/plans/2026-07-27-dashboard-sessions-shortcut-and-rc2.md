# Dashboard Sessions Shortcut And 1.0-RC2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an eighth Home `Sessions` button beside `Storage`, preserve origin-aware session-browser navigation, and publish the verified source as RtkCollector 1.0-RC2.

**Architecture:** Extend the existing `DashboardSetupItem` model so compact and rail layouts consume one ordered set of eight Home actions. Reuse the existing `SessionsScreen` and add a small saved entry-point model in `MainActivity` so visible and system Back return to the caller. Keep release identity coordinated across Gradle, NTRIP protocol defaults and current-release documentation.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose Material 3, JUnit 5/Kotlin test, Gradle/AGP 9.2, GitHub CLI.

## Global Constraints

- The compact Home setup grid order MUST be `Device`, `Settings`, `Workflow`, `Mountpoint`, `Profiles`, `Upload`, `Storage`, `Sessions`.
- In the two-column setup, the final row MUST be `Storage | Sessions`.
- `Sessions` is a navigation action, not configuration state. It MUST remain enabled and MUST NOT contribute a warning or automatic-expansion condition.
- The existing fold preference and automatic expansion for invalid active configuration MUST remain unchanged.
- The Home shortcut MUST refresh and open the existing session browser; it MUST NOT duplicate or bypass active-session, archive, operation-lease or sharing protections.
- Back from Sessions MUST return to Home when opened from Home and Settings when opened from Settings, for both the screen Back action and Android system Back.
- The existing Settings > Sessions > Recent sessions and sharing route MUST remain.
- Android `versionName` and project version MUST be `1.0-RC2`; Android `versionCode` MUST be `2`.
- The default NTRIP client/source user agent MUST be `NTRIP RtkCollector/1.0-RC2`.
- Tag `v1.0-RC2` MUST identify the exact verified release commit.
- RC2 MUST be a GitHub prerelease and MUST remain source-only until a binary built from that exact RC2 commit is supplied. The existing `1.0-RC1` debug APK MUST NOT be renamed or attached.
- Raw receiver capture, correction routing, foreground-service ownership and session artifact behavior MUST NOT change.
- Existing unrelated dirty and untracked specification-assurance work MUST remain uncommitted and unmodified except for narrowly required, partially staged requirement text.
- Before push, run `sh scripts/pre_push_check.sh` against a clean checkout containing exactly the release commits.

---

### Task 1: Canonical Requirement And Traceability Update

**Files:**
- Modify: `docs/specification/ui-requirements.md`
- Modify: `docs/specification/verification-matrix.md`

**Interfaces:**
- Consumes: approved design in `docs/superpowers/specs/2026-07-27-dashboard-sessions-shortcut-and-rc2-design.md`
- Produces: normative `UI-SETUP-003` and `UI-SETUP-008` wording for Tasks 2 and 3

- [ ] **Step 1: Update `UI-SETUP-003`**

Change the dashboard requirement to state:

```text
The dashboard setup strip MUST expose Device, Settings, Workflow, Mountpoint,
Profiles, Upload, Storage and Sessions actions, in that order. The first seven
are active-configuration selectors. Sessions is a navigation shortcut to the
existing Recent sessions and sharing screen and MUST NOT participate in setup
validation.
```

Retain all existing Mountpoint and Upload behavior. Add verification that the
compact two-column layout ends with `Storage | Sessions` and that session
protections remain authoritative.

- [ ] **Step 2: Update `UI-SETUP-008`**

Replace references to "seven dashboard setup selectors" with wording that
distinguishes "seven setup selectors and the Sessions shortcut" and requires
all eight actions to fold together. Keep automatic expansion driven only by the
existing configuration errors.

- [ ] **Step 3: Update matrix notes without claiming fresh evidence**

Update the prior-context notes for `UI-SETUP-003` and `UI-SETUP-008` to describe
the eight-action implementation target. Do not change evidence receipts from
`not-run` and do not fabricate approvals.

- [ ] **Step 4: Validate and partially stage only these requirement hunks**

Run:

```bash
git diff --check -- docs/specification/ui-requirements.md docs/specification/verification-matrix.md
python3 tools/check_spec_requirements.py
```

Expected: no whitespace error and the structural requirement checker passes.
Because both files contain pre-existing unrelated worktree changes, stage only
the Task 1 hunks and verify the cached diff does not contain other assurance
migration changes.

- [ ] **Step 5: Commit the requirement checkpoint**

```bash
git commit -m "Specify the Home Sessions shortcut"
```

The commit must contain only the two narrowly staged requirement/matrix hunks.

---

### Task 2: Dashboard Action And Session Navigation

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModelsTest.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/ui/SessionBrowserNavigationTest.kt`

**Interfaces:**
- Consumes: `defaultDashboardSetupItems`, `DashboardStatus.setupWarningReason`, `HomeDashboard`, `AppScreen.SESSIONS`, `refreshSessions()`
- Produces: `DashboardSetupItem.SESSIONS`, `SessionBrowserEntryPoint`, `SessionBrowserEntryPoint.returnScreen()`, `HomeDashboard(onSessions)`

- [ ] **Step 1: Write failing dashboard ordering and semantics tests**

Extend the existing dashboard tests with:

```kotlin
assertEquals(
    listOf(
        DashboardSetupItem.DEVICE,
        DashboardSetupItem.SETTINGS,
        DashboardSetupItem.WORKFLOW,
        DashboardSetupItem.MOUNTPOINT,
        DashboardSetupItem.INIT_PROFILES,
        DashboardSetupItem.UPLOAD,
        DashboardSetupItem.STORAGE,
        DashboardSetupItem.SESSIONS,
    ),
    defaultDashboardSetupItems,
)
assertEquals(
    listOf(DashboardSetupItem.STORAGE, DashboardSetupItem.SESSIONS),
    defaultDashboardSetupItems.chunked(2).last(),
)
assertEquals(null, validDashboardStatus().setupWarningReason(DashboardSetupItem.SESSIONS))
assertEquals(true, validDashboardStatus().isSetupItemEnabled(DashboardSetupItem.SESSIONS))
```

Update the label-list expectation to end with `"Sessions"`.

- [ ] **Step 2: Write the failing navigation-origin test**

Create `SessionBrowserNavigationTest.kt`:

```kotlin
package org.rtkcollector.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionBrowserNavigationTest {
    @Test
    fun `session browser returns to its entry screen`() {
        assertEquals(AppScreen.HOME, SessionBrowserEntryPoint.HOME.returnScreen())
        assertEquals(AppScreen.SETTINGS, SessionBrowserEntryPoint.SETTINGS.returnScreen())
    }
}
```

- [ ] **Step 3: Run tests and verify RED**

Run sequentially:

```bash
sh gradlew :app:termuxTestDebugUnitTest \
  --tests org.rtkcollector.app.ui.dashboard.DashboardStateTest \
  --tests org.rtkcollector.app.ui.dashboard.DashboardLayoutModelsTest \
  --tests org.rtkcollector.app.ui.SessionBrowserNavigationTest \
  --no-parallel
```

Expected: compilation/test failure because `SESSIONS`,
`SessionBrowserEntryPoint` and the eighth action do not exist.

- [ ] **Step 4: Add the eighth action to the shared model**

Add:

```kotlin
SESSIONS("Sessions"),
```

after `STORAGE`, append it to `defaultDashboardSetupItems`, make
`isSetupItemEnabled(SESSIONS)` return true, and make
`setupWarningReason(SESSIONS)` return null. Do not add it to any missing-value
or validation branch.

- [ ] **Step 5: Render and route the action**

Add `onSessions: () -> Unit` through `HomeDashboard`, `CompactDashboard`,
`RailDashboard` and `SetupStrip`. Route `DashboardSetupItem.SESSIONS` to that
callback in both compact and rail layouts. Render the action's secondary text
as:

```kotlin
DashboardSetupItem.SESSIONS -> "Recent & share"
```

This is action copy, not configuration state.

- [ ] **Step 6: Add saved entry-point navigation**

Make `AppScreen` internal for the package-level test and add:

```kotlin
internal enum class SessionBrowserEntryPoint {
    HOME,
    SETTINGS,
}

internal fun SessionBrowserEntryPoint.returnScreen(): AppScreen =
    when (this) {
        SessionBrowserEntryPoint.HOME -> AppScreen.HOME
        SessionBrowserEntryPoint.SETTINGS -> AppScreen.SETTINGS
    }
```

Persist the entry point with a `Saver<SessionBrowserEntryPoint, String>`.
Create one local `openSessions(entryPoint)` helper that sets the entry point,
calls `refreshSessions()`, then sets `screen = AppScreen.SESSIONS`.

Wire:

```kotlin
HomeDashboard(onSessions = { openSessions(SessionBrowserEntryPoint.HOME) })
SettingsHub(onSessions = { openSessions(SessionBrowserEntryPoint.SETTINGS) })
```

Use `sessionBrowserEntryPoint.returnScreen()` for both `SessionsScreen.onBack`
and the `BackHandler` branch when `screen == AppScreen.SESSIONS`.

- [ ] **Step 7: Update previews**

Add `onSessions = {}` to every `HomeDashboard` preview invocation so preview
sources compile.

- [ ] **Step 8: Run targeted tests and compile**

Run:

```bash
sh gradlew :app:termuxTestDebugUnitTest \
  --tests org.rtkcollector.app.ui.dashboard.DashboardStateTest \
  --tests org.rtkcollector.app.ui.dashboard.DashboardLayoutModelsTest \
  --tests org.rtkcollector.app.ui.SessionBrowserNavigationTest \
  --no-parallel
sh gradlew :app:compileDebugKotlin --no-parallel
```

Expected: all targeted tests and production Kotlin compilation pass.

- [ ] **Step 9: Commit**

```bash
git add \
  app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModelsTest.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/SessionBrowserNavigationTest.kt
git commit -m "Add Home shortcut to recent sessions"
```

---

### Task 3: User Documentation And Plan Status

**Files:**
- Modify: `docs/user-workflows.md`
- Modify: `docs/superpowers/plan-status.md`

**Interfaces:**
- Consumes: the Task 2 Home behavior
- Produces: operator-facing shortcut instructions and accurate implementation status

- [ ] **Step 1: Update Home setup documentation**

Replace "seven Home setup selectors" with:

```text
The Home Active setup area contains seven configuration selectors plus a
Sessions shortcut. All eight buttons fold together. In the two-column layout,
Storage and Sessions share the final row.
```

Explain that `Sessions` opens `Recent sessions and sharing`, and retain the
existing description of remembered folding and automatic expansion.

- [ ] **Step 2: Update screenshot guidance**

Screenshot item 1 must list all eight Home actions. Session-list and sharing
screens remain separate screenshot items.

- [ ] **Step 3: Update session workflow text**

Document that recent sessions are reachable both from the Home `Sessions`
button and from Menu > Sessions > Recent sessions and sharing. Preserve all
active-recording restrictions.

- [ ] **Step 4: Update plan status**

Change the Active setup status note from seven to eight Home actions and record
the direct Sessions shortcut while retaining the existing field-test caveat.

- [ ] **Step 5: Validate and commit**

Run:

```bash
git diff --check -- docs/user-workflows.md docs/superpowers/plan-status.md
```

Then commit:

```bash
git add docs/user-workflows.md docs/superpowers/plan-status.md
git commit -m "Document direct Home session access"
```

---

### Task 4: 1.0-RC2 Version And Release Notes

**Files:**
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripClient.kt`
- Modify: `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripClientTest.kt`
- Modify: `docs/ntrip-and-corrections.md`
- Modify: `docs/play-publication.md`
- Create: `docs/releases/1.0-RC2.md`
- Partially modify/stage: `AGENTS.md`

**Interfaces:**
- Consumes: current RC1 version literals and verified changes since `v1.0-RC1`
- Produces: coherent `1.0-RC2` source identity and GitHub release body

- [ ] **Step 1: Write the failing user-agent expectation**

Change both default-request assertions in `NtripClientTest` to:

```kotlin
assertTrue(rendered.contains("User-Agent: NTRIP RtkCollector/1.0-RC2\r\n"))
```

- [ ] **Step 2: Run the correction test and verify RED**

Run:

```bash
sh gradlew :core:correction:test \
  --tests org.rtkcollector.core.correction.NtripClientTest \
  --no-parallel
```

Expected: the two assertions fail because the production default still says
`1.0-RC1`.

- [ ] **Step 3: Update executable version surfaces**

Set:

```kotlin
// build.gradle.kts
version = "1.0-RC2"

// app/build.gradle.kts
versionCode = 2
versionName = "1.0-RC2"

// NtripClient.kt
const val DEFAULT_NTRIP_USER_AGENT: String = "NTRIP RtkCollector/1.0-RC2"
```

- [ ] **Step 4: Update current documentation**

Update the live user-agent examples in `docs/ntrip-and-corrections.md` and
`AGENTS.md` to RC2. Update `docs/play-publication.md` to identify `1.0-RC2` as
the current source candidate. Do not rewrite historical
`docs/releases/1.0-RC1.md`.

- [ ] **Step 5: Create RC2 release notes**

Create `docs/releases/1.0-RC2.md` dated 2026-07-27. Include:

- the Home Sessions shortcut;
- the NTRIP reconnect-delay stop-warning fix;
- the existing byte-exact recording and session-safety guarantees;
- the same receiver-support and cleartext-NTRIP limitations;
- explicit source-only status and a statement that no RC1 binary is an RC2
  artifact;
- remaining Google Play signed-AAB/manual-validation gates.

- [ ] **Step 6: Run tests and version scan**

Run:

```bash
sh gradlew :core:correction:test \
  --tests org.rtkcollector.core.correction.NtripClientTest \
  --no-parallel
rg -n "1\\.0-RC1|v1\\.0-RC1" \
  build.gradle.kts app/build.gradle.kts \
  core/correction/src docs/ntrip-and-corrections.md \
  docs/play-publication.md AGENTS.md
```

Expected: the test passes and the active-version scan returns no RC1 hit.
Historical RC1 release notes and digest-bound assurance history remain
unchanged.

- [ ] **Step 7: Stage only release changes and commit**

Because `AGENTS.md` contains pre-existing unrelated edits, stage only its
single user-agent-example hunk. Verify:

```bash
git diff --cached --check
git diff --cached --name-status
```

Then commit:

```bash
git commit -m "Bump RtkCollector to 1.0-RC2"
```

---

### Task 5: Integrated Verification, Review, Push And Release

**Files:**
- Verify all Task 1-4 files
- No source edits unless review identifies a defect

**Interfaces:**
- Consumes: all prior task commits
- Produces: verified `main`, annotated `v1.0-RC2`, GitHub prerelease

- [ ] **Step 1: Inspect scope**

Run:

```bash
git log --oneline v1.0-RC1..HEAD
git diff --check v1.0-RC1..HEAD
git status --short
```

Confirm implementation commits contain only intended files and all unrelated
local assurance work remains outside those commits.

- [ ] **Step 2: Run targeted tests sequentially**

Run:

```bash
sh gradlew :core:correction:test --no-parallel
sh gradlew :app:termuxTestDebugUnitTest --no-parallel
sh gradlew :app:compileDebugKotlin --no-parallel
```

Expected: all commands pass.

- [ ] **Step 3: Run the clean-checkout pre-push gate**

Create a temporary clean clone at the release commit, provision the same local
Android/RTKLIB prerequisites used by the repository gate, and run:

```bash
sh scripts/pre_push_check.sh
```

Expected: the complete gate passes, including app JVM test-source compilation,
non-Robolectric tests and Android Studio compatibility task dry-runs.

- [ ] **Step 4: Run independent final review**

Review against:

- the approved design;
- `UI-SETUP-003` and `UI-SETUP-008`;
- origin-aware visible/system Back behavior;
- raw/session safety non-regression;
- exact RC2 version consistency;
- absence of stale or mislabeled binary assets.

Fix and re-review any Critical or Important finding before proceeding.

- [ ] **Step 5: Push `main`**

Run:

```bash
git push origin main
```

Verify `HEAD` and `origin/main` resolve to the same commit.

- [ ] **Step 6: Create and push annotated tag**

Run:

```bash
git tag -a v1.0-RC2 -m "RtkCollector 1.0-RC2"
git push origin v1.0-RC2
```

Verify the tag peels to the same release commit as `origin/main`.

- [ ] **Step 7: Publish source-only GitHub prerelease**

Run:

```bash
gh release create v1.0-RC2 \
  --repo holubp/rtkcollector \
  --title "RtkCollector 1.0-RC2" \
  --notes-file docs/releases/1.0-RC2.md \
  --prerelease \
  --verify-tag
```

Do not pass an APK/AAB path.

- [ ] **Step 8: Verify remote release**

Run:

```bash
gh release view v1.0-RC2 \
  --repo holubp/rtkcollector \
  --json tagName,name,isPrerelease,targetCommitish,assets,url
```

Expected: tag/name are RC2, `isPrerelease` is true, and `assets` is empty.

