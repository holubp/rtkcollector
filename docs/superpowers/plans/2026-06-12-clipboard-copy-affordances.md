# Clipboard Copy Affordances Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two small clipboard affordances: tapping the dashboard error strip copies the full error text, and tapping a recorded-session row outside selection mode copies that session path.

**Architecture:** Keep the behavior testable by adding pure helper functions for the strings/actions, then wire Android clipboard calls through thin Compose callbacks. Do not alter recording, session discovery, archive/share, or selection semantics.

**Tech Stack:** Kotlin, Jetpack Compose, Android `ClipboardManager` through Compose `LocalClipboardManager`, JUnit tests under `app/src/test`.

---

## File Structure

- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
  - Add a pure `DashboardState.errorClipboardText()` helper that returns the full copied text or `null`.
- Modify `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`
  - Add regression tests for full error-copy text and no-copy when there is no error.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
  - Add clipboard/Toast wiring and make the red error strip clickable only when there is text to copy.
- Modify `app/src/main/kotlin/org/rtkcollector/app/sessions/SessionBrowserModels.kt`
  - Add pure `sessionPathCopyText(entry, selectionMode)` helper.
- Modify `app/src/test/kotlin/org/rtkcollector/app/ui/sessions/SessionBrowserModelsTest.kt`
  - Add tests that session rows copy paths outside selection mode and do not copy paths in selection mode.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionsScreen.kt`
  - Add an `onCopyPath` callback to `SessionsScreen`, pass selection mode into `SessionRow`, and make rows clickable for path-copy only when selection mode is off.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  - Pass an `onCopyPath` callback to `SessionsScreen` that copies `entry.location` to clipboard and shows a concise Toast.

---

### Task 1: Copy Full Dashboard Error Text

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`

- [ ] **Step 1: Write the failing tests**

Append these tests to `DashboardStateTest`:

```kotlin
@Test
fun `error clipboard text includes category and full message`() {
    val state = DashboardState.planned(
        workflow = "Rover + NTRIP",
        mountpoint = "TUBO00CZE0",
        receiver = "UM980",
        storage = "App-private",
        lastError = "No static method writeString(Ljava/nio/file/Path;Ljava/lang/CharSequence;)",
        errorCategory = "SERVICE_LIFECYCLE",
    )

    assertEquals(
        "SERVICE_LIFECYCLE: No static method writeString(Ljava/nio/file/Path;Ljava/lang/CharSequence;)",
        state.errorClipboardText(),
    )
}

@Test
fun `error clipboard text is null without an error`() {
    val state = DashboardState.planned(
        workflow = "Plain rover",
        mountpoint = "n/a",
        receiver = "UM980",
        storage = "App-private",
    )

    assertEquals(null, state.errorClipboardText())
}
```

- [ ] **Step 2: Run the targeted test and verify it fails**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardStateTest
```

Expected: compile failure or test failure because `DashboardState.errorClipboardText()` does not exist.

- [ ] **Step 3: Add the pure helper**

Add this function near `DashboardState` in `DashboardModels.kt`:

```kotlin
fun DashboardState.errorClipboardText(): String? {
    val message = lastError?.takeIf { it.isNotBlank() } ?: return null
    return "$errorCategory: $message"
}
```

- [ ] **Step 4: Wire the error strip to clipboard**

In `HomeDashboard.kt`, add imports:

```kotlin
import android.widget.Toast
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
```

Inside `HomeDashboard(...)`, before layout content that renders `ErrorStrip`, create:

```kotlin
val clipboardManager = LocalClipboardManager.current
val context = LocalContext.current
val copyErrorToClipboard = {
    state.errorClipboardText()?.let { text ->
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(context, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
```

Update every `ErrorStrip(state)` call in `HomeDashboard.kt` to:

```kotlin
ErrorStrip(
    state = state,
    onCopy = copyErrorToClipboard,
)
```

Replace the existing private function:

```kotlin
@Composable
private fun ErrorStrip(state: DashboardState) {
    val message = state.lastError?.takeIf { it.isNotBlank() } ?: return
    Surface(
        color = TidyColors.MissingBackground,
        contentColor = TidyColors.MissingText,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, TidyColors.MissingText),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "${state.errorCategory}: $message",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

with:

```kotlin
@Composable
private fun ErrorStrip(
    state: DashboardState,
    onCopy: () -> Unit,
) {
    val text = state.errorClipboardText() ?: return
    Surface(
        color = TidyColors.MissingBackground,
        contentColor = TidyColors.MissingText,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, TidyColors.MissingText),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

- [ ] **Step 5: Run the targeted test and verify it passes**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardStateTest
```

Expected: PASS.

- [ ] **Step 6: Commit Task 1**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt
git commit -m "Copy dashboard errors to clipboard"
```

---

### Task 2: Copy Session Path On Row Tap Outside Selection Mode

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/sessions/SessionBrowserModels.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/sessions/SessionBrowserModelsTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionsScreen.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Write the failing tests**

Append these tests to `SessionBrowserModelsTest`:

```kotlin
@Test
fun `session path copy text returns location outside selection mode`() {
    val entry = entry("session-1", SessionEntryKind.RECORDING, 10)
        .copy(location = "/storage/emulated/0/Android/data/org.rtkcollector.app/files/sessions/session-1")

    assertEquals(
        "/storage/emulated/0/Android/data/org.rtkcollector.app/files/sessions/session-1",
        sessionPathCopyText(entry, selectionMode = false),
    )
}

@Test
fun `session path copy text is disabled in selection mode`() {
    val entry = entry("session-1", SessionEntryKind.RECORDING, 10)
        .copy(location = "/storage/emulated/0/Android/data/org.rtkcollector.app/files/sessions/session-1")

    assertEquals(null, sessionPathCopyText(entry, selectionMode = true))
}
```

- [ ] **Step 2: Run the targeted test and verify it fails**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.sessions.SessionBrowserModelsTest
```

Expected: compile failure or test failure because `sessionPathCopyText(...)` does not exist.

- [ ] **Step 3: Add the pure helper**

Append this helper to `app/src/main/kotlin/org/rtkcollector/app/sessions/SessionBrowserModels.kt`:

```kotlin
import org.rtkcollector.app.sessions.SessionBrowserEntry

fun sessionPathCopyText(
    entry: SessionBrowserEntry,
    selectionMode: Boolean,
): String? {
    if (selectionMode) return null
    return entry.location.takeIf { it.isNotBlank() }
}
```

If the file already has declarations before imports, move the new import to the existing import block and keep the function below the data classes.

- [ ] **Step 4: Wire row tap behavior in `SessionsScreen`**

In `SessionsScreen.kt`, add an import:

```kotlin
import androidx.compose.foundation.clickable
```

Extend `SessionsScreen` parameters:

```kotlin
    onDeleteSelected: () -> Unit,
    onCopyPath: (SessionBrowserEntry) -> Unit,
    onBack: () -> Unit,
```

Before rendering groups, compute selection mode:

```kotlin
        val selectionMode = state.selectedIds.isNotEmpty()
```

Update the `SessionRow(...)` call:

```kotlin
                    SessionRow(
                        entry = entry,
                        selected = entry.id in state.selectedIds,
                        selectionMode = selectionMode,
                        onToggle = { onToggle(entry.id) },
                        onCopyPath = { onCopyPath(entry) },
                    )
```

Update `SessionRow` signature:

```kotlin
private fun SessionRow(
    entry: SessionBrowserEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onToggle: () -> Unit,
    onCopyPath: () -> Unit,
)
```

Replace:

```kotlin
    Card(modifier = Modifier.fillMaxWidth()) {
```

with:

```kotlin
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !selectionMode, onClick = onCopyPath),
    ) {
```

Do not change checkbox behavior. In selection mode, the checkbox remains the explicit selection control and row taps do not copy paths.

- [ ] **Step 5: Wire Android clipboard in `MainActivity`**

In `MainActivity.kt`, add imports if missing:

```kotlin
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
```

Inside `RtkCollectorApp(...)`, near other `remember`/context setup, create:

```kotlin
    val clipboardManager = LocalClipboardManager.current
```

In the `SessionsScreen(...)` call, add:

```kotlin
                    onCopyPath = { entry ->
                        clipboardManager.setText(AnnotatedString(entry.location))
                        Toast.makeText(context, "Session path copied", Toast.LENGTH_SHORT).show()
                    },
```

- [ ] **Step 6: Run the targeted test and verify it passes**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.sessions.SessionBrowserModelsTest
```

Expected: PASS.

- [ ] **Step 7: Run focused app UI/model tests**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardStateTest --tests org.rtkcollector.app.sessions.SessionBrowserModelsTest
```

Expected: PASS.

- [ ] **Step 8: Commit Task 2**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/sessions/SessionBrowserModels.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionsScreen.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/sessions/SessionBrowserModelsTest.kt
git commit -m "Copy session paths from session browser"
```

---

## Final Verification

- [ ] Run source whitespace check:

```bash
git diff --check
```

Expected: no output and exit code 0.

- [ ] Run the focused unit tests:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardStateTest --tests org.rtkcollector.app.sessions.SessionBrowserModelsTest
```

Expected: PASS.

- [ ] Run broader feasible test/build command:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: PASS in environments with compatible Android SDK tooling. If Termux fails earlier in Android resource processing due to native `aapt2`, report that exact environment blocker and keep the focused JVM test result as the source-level validation.

- [ ] Review changed files:

```bash
git status --short
git diff --stat
```

Expected: only the planned source/test files changed; local `.codex-tmp/`, `.superpowers/`, and `samples/` remain uncommitted.

---

## Self-Review

Spec coverage:

- Tapping the red error strip copies the full error: Task 1.
- Tapping a recorded session outside selection mode copies its path: Task 2.
- Selection mode remains for bulk archive/share/delete: Task 2 keeps checkbox behavior and disables row copy while selection is active.

Placeholder scan:

- No placeholder markers or unspecified implementation placeholders are used in the task steps.

Type consistency:

- `DashboardState.errorClipboardText()` is defined and used by `HomeDashboard.ErrorStrip`.
- `sessionPathCopyText(entry, selectionMode)` is defined in the UI session model package and tested independently.
- `SessionsScreen` receives `onCopyPath: (SessionBrowserEntry) -> Unit`, while `SessionRow` receives a no-argument `onCopyPath` closure bound to the current entry.
