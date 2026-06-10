# App-Wide UI Tidy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved clean-slate RtkCollector UI tidy design with a compact field dashboard, selectable rail layout, grouped settings hub, consistent profile lists/editors, local help overlays, and native-feeling back/unsaved-change behaviour.

**Architecture:** Keep Compose as a thin observer/command surface over existing profile, recording and dashboard state. Add small UI-state models where behaviour needs tests, and isolate reusable visual components so Home, Settings and Profile screens share one tidy style. Do not touch receiver recording, NTRIP protocol logic, UM980 parsing, session writers or workflow validation.

**Tech Stack:** Android Kotlin, Jetpack Compose Material 3, existing app/profile/dashboard model classes, JUnit tests for pure Kotlin UI state/model logic.

---

## Scope Check

This plan implements one subsystem: the app-wide Compose UI foundation. It includes layout preference plumbing, dashboard visual structure, grouped settings, profile-list/editor polish, help overlays and unsaved-change guards. It explicitly excludes GNSS protocol changes, USB recording changes, NTRIP networking changes and new telemetry parsing.

## File Structure

- Create `app/src/main/kotlin/org/rtkcollector/app/ui/common/TidyUi.kt`
  - Shared compact UI primitives: frozen top bar wrapper, section surfaces, setup tile, metric row, help icon, active label.
- Create `app/src/main/kotlin/org/rtkcollector/app/ui/common/HelpOverlay.kt`
  - Field/card help topics and dismissible help dialog.
- Create `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModels.kt`
  - Dashboard layout preference enum and selector labels.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
  - Replace current FlowRow/card styling with compact default dashboard and selectable rail layout rendering.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
  - Add layout parameter only if needed by pure state; avoid changing telemetry inventory.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`
  - Replace flat buttons with grouped menu sections and icons.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
  - Apply consistent profile row styling, active rows, editor top actions, rename dialog and local help hooks.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  - Store dashboard layout preference in Compose state initially; wire Settings entry for selecting compact/rail layout. Preserve existing selectors and recording controls.
- Create `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModelsTest.kt`
  - Test layout preference labels and parsing/defaults.
- Create `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/UnsavedEditorStateTest.kt`
  - Test unsaved-change decision model if a new pure model is introduced.
- Modify existing tests only where model names or labels change:
  - `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardStateTest.kt`
  - `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt`

## Task 1: Dashboard Layout Preference Model

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModels.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModelsTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package org.rtkcollector.app.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardLayoutModelsTest {
    @Test
    fun `default layout is compact field dashboard`() {
        assertEquals(DashboardLayoutPreference.COMPACT_FIELD, DashboardLayoutPreference.default)
        assertEquals("Compact field dashboard", DashboardLayoutPreference.COMPACT_FIELD.displayName)
    }

    @Test
    fun `stored layout ids parse defensively`() {
        assertEquals(DashboardLayoutPreference.RAIL, DashboardLayoutPreference.fromStorageId("rail"))
        assertEquals(DashboardLayoutPreference.COMPACT_FIELD, DashboardLayoutPreference.fromStorageId("unknown"))
        assertEquals(DashboardLayoutPreference.COMPACT_FIELD, DashboardLayoutPreference.fromStorageId(""))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardLayoutModelsTest
```

Expected: FAIL because `DashboardLayoutPreference` is not defined.

- [ ] **Step 3: Implement the model**

```kotlin
package org.rtkcollector.app.ui.dashboard

enum class DashboardLayoutPreference(
    val storageId: String,
    val displayName: String,
) {
    COMPACT_FIELD("compact_field", "Compact field dashboard"),
    RAIL("rail", "Rail layout");

    companion object {
        val default: DashboardLayoutPreference = COMPACT_FIELD

        fun fromStorageId(value: String?): DashboardLayoutPreference =
            entries.firstOrNull { it.storageId == value } ?: default
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run the same Gradle test command.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModels.kt app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModelsTest.kt
git commit -m "Add dashboard layout preference model"
```

## Task 2: Shared Tidy UI Primitives

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/common/TidyUi.kt`
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/common/HelpOverlay.kt`

- [ ] **Step 1: Create shared primitives**

Create `TidyUi.kt`:

```kotlin
package org.rtkcollector.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

object TidyColors {
    val ActiveBackground = Color(0xFFE8F5E9)
    val ActiveText = Color(0xFF145A18)
    val MissingBackground = Color(0xFFFFDAD6)
    val MissingText = Color(0xFF8C1D18)
    val Divider = Color(0xFFD4DDE6)
}

@Composable
fun TidyTopBar(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (leading != null) {
                leading()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing()
        }
    }
}

@Composable
fun TidyPill(text: String, modifier: Modifier = Modifier, active: Boolean = false, missing: Boolean = false) {
    val background = when {
        missing -> TidyColors.MissingBackground
        active -> TidyColors.ActiveBackground
        else -> MaterialTheme.colorScheme.surface
    }
    val foreground = when {
        missing -> TidyColors.MissingText
        active -> TidyColors.ActiveText
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = background,
        border = BorderStroke(1.dp, if (active) TidyColors.ActiveText else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TidyMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun HelpIcon(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text("i", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
```

Create `HelpOverlay.kt`:

```kotlin
package org.rtkcollector.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

enum class HelpTopic(val title: String, val body: String) {
    SATS_USED_VIEW(
        "Sats used/view",
        "Used is the number of satellites in the receiver solution. View is the aggregate visible-satellite count reported by telemetry such as GSV or STADOP.",
    ),
    ELLIPSOIDAL_HEIGHT(
        "Ellipsoidal height",
        "Height above the reference ellipsoid, distinct from orthometric altitude.",
    ),
    TX_TO_RECEIVER(
        "TX to receiver",
        "Bytes transmitted by the app toward the receiver serial input.",
    ),
    NTRIP_URL(
        "NTRIP URL",
        "Caster host, port and mountpoint without password or token.",
    );
}

@Composable
fun HelpOverlay(topic: HelpTopic?, onDismiss: () -> Unit) {
    if (topic == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(topic.title) },
        text = { Text(topic.body) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
```

- [ ] **Step 2: Compile to verify imports and Compose API usage**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/common/TidyUi.kt app/src/main/kotlin/org/rtkcollector/app/ui/common/HelpOverlay.kt
git commit -m "Add shared tidy Compose primitives"
```

## Task 3: Compact Dashboard Visual Rewrite

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`

- [ ] **Step 1: Keep current public API and add layout parameter**

Update `HomeDashboard` signature to accept the new layout preference with a default:

```kotlin
fun HomeDashboard(
    state: DashboardState,
    layoutPreference: DashboardLayoutPreference = DashboardLayoutPreference.default,
    onPrimaryAction: () -> Unit,
    onMenu: () -> Unit,
    onNtrip: () -> Unit,
    onUsbPermission: () -> Unit,
    onWorkflow: () -> Unit,
    onSettingsSet: () -> Unit,
    onReceiver: () -> Unit,
    onStorage: () -> Unit,
    onMark: () -> Unit,
)
```

- [ ] **Step 2: Replace dashboard card structure**

Implement two render paths:

```kotlin
when (layoutPreference) {
    DashboardLayoutPreference.COMPACT_FIELD -> CompactFieldDashboard(...)
    DashboardLayoutPreference.RAIL -> RailDashboard(...)
}
```

Both render paths must call the same card content functions:

```kotlin
PositionCard(state.position)
FixCard(state.fix)
CorrectionsCard(state.ntrip)
RecordingCard(state.files)
```

Use the current card field inventory exactly:

- Position: `latLon`, `utcTime`, `ellipsoidalHeight`, `altitude`, `latError`, `lonError`.
- Fix: `fixType`, `satellites`, `pdop`, `hdopVdop`, `horizontalAccuracy`, `verticalAccuracy`, `differentialAge`, `baseline`, `pppStatus`, `rtklibStatus`.
- Corrections: `status`, `url`, `transferred`, `stationId`, `baseLatLon`, `rates`.
- Recording: `sessionLocation`, `receiverRxBytes`, `txToReceiverBytes`, `ntripBytes`, `nmeaBytes`, `zipShareLabel`.

- [ ] **Step 3: Add setup tiles**

Replace the current `AssistChip` status strip with compact setup tiles labelled:

```kotlin
Settings
Workflow
Mountpoint
Receiver
Storage
```

Keep the current missing-value logic:

```kotlin
private fun String.isMissingDashboardValue(): Boolean
```

Render missing tiles with pale red background and dark red text.

- [ ] **Step 4: Add help topics to card titles**

Wire local state in `HomeDashboard`:

```kotlin
var helpTopic by remember { mutableStateOf<HelpTopic?>(null) }
HelpOverlay(topic = helpTopic, onDismiss = { helpTopic = null })
```

Add `(i)` buttons for:

- Fix card: `HelpTopic.SATS_USED_VIEW`.
- Position card: `HelpTopic.ELLIPSOIDAL_HEIGHT`.
- Corrections card: `HelpTopic.NTRIP_URL`.
- Recording card: `HelpTopic.TX_TO_RECEIVER`.

- [ ] **Step 5: Compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt
git commit -m "Polish compact dashboard layout"
```

## Task 4: Rail Dashboard Layout And Selection

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`

- [ ] **Step 1: Store dashboard layout in Compose state**

In `RtkCollectorApp`, add:

```kotlin
var dashboardLayout by remember { mutableStateOf(DashboardLayoutPreference.default) }
```

Pass it to `HomeDashboard(layoutPreference = dashboardLayout, ...)`.

- [ ] **Step 2: Add a simple layout selector action**

In Settings, add a `Dashboard layout` row in the Session Setup group. For this first pass, it can toggle between compact and rail through an overlay/dialog or simple Settings row action. The row must display:

```text
Dashboard layout: Compact field dashboard
```

or:

```text
Dashboard layout: Rail layout
```

- [ ] **Step 3: Implement rail dashboard**

In `HomeDashboard.kt`, implement `RailDashboard` with:

- left rail containing Workflow, Mountpoint, Receiver, Storage setup tiles;
- right side containing the same card functions as compact mode;
- same bottom action behaviour as compact mode.

- [ ] **Step 4: Compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt
git commit -m "Add selectable rail dashboard layout"
```

## Task 5: Grouped Settings Hub

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`

- [ ] **Step 1: Replace flat list with grouped sections**

Implement these groups in order:

```text
Session setup
  Settings sets
  Workflow selection
  Recording outputs
  Storage location profiles

Receiver and USB
  USB device and baud
  Command scripts
  Receiver family/profile

Corrections
  NTRIP casters
  NTRIP mountpoints

Sessions
  Recent sessions and sharing
```

Keep existing callbacks. If there is no callback for `Workflow selection` or `Receiver family/profile`, route to the closest existing profile screen and leave the row label precise enough to avoid user confusion.

- [ ] **Step 2: Add simple icons**

Use short text icons in the first implementation, matching the mockup spirit without adding a new icon dependency:

```text
◎ Settings sets
⇄ Workflow selection
● Recording outputs
▣ Storage location profiles
USB USB device and baud
⌁ Command scripts
RX Receiver family/profile
N NTRIP casters
M NTRIP mountpoints
↗ Recent sessions and sharing
```

- [ ] **Step 3: Compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt
git commit -m "Group settings hub by workflow area"
```

## Task 6: Profile Lists And Rename Dialog Polish

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Modify if needed: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt`

- [ ] **Step 1: Preserve existing list state and callbacks**

Do not change the `ProfileListScreen` public callback contract:

```kotlin
onSelect: (String) -> Unit
onEdit: (String) -> Unit
onCopy: (String) -> Unit
onRename: (String) -> Unit
onDelete: (String) -> Unit
onAdd: () -> Unit
onBack: () -> Unit
```

- [ ] **Step 2: Restyle active rows**

Render selected rows with:

- pale green row background;
- dark green `Active` label;
- no disabled active button.

Only show `Use` for rows that are not selected.

- [ ] **Step 3: Add rename dialog UI shell**

If rename is currently handled outside this composable, keep callback behaviour. If rename is local, add:

```kotlin
var renameTarget by remember { mutableStateOf<ProfileListRow?>(null) }
```

Dialog buttons:

```text
Save
Discard
Cancel
```

The plan does not require changing persistence if `onRename` currently delegates to an existing rename flow.

- [ ] **Step 4: Compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt app/src/test/kotlin/org/rtkcollector/app/ui/profiles/ProfileListModelsTest.kt
git commit -m "Polish profile list interactions"
```

## Task 7: Profile Editor Frozen Actions And Field Discipline

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`

- [ ] **Step 1: Locate `ProfileEditorScreen`**

Read the existing editor implementation and identify:

- title rendering;
- save/back/delete actions;
- text fields;
- dropdown fields;
- command script fields.

- [ ] **Step 2: Add frozen top action pattern**

Use a `Scaffold` top bar or equivalent fixed top row with:

```text
Back | Edit <profile type> | Save | Discard | Delete
```

Keep form fields in the scrollable content area only.

- [ ] **Step 3: Enforce field rules**

Within existing editor field rendering:

- label is always above or tightly attached to value;
- `Shutdown script` is a label, not placeholder text;
- empty shutdown script field is empty;
- receiver family uses existing options/dropdown where available;
- boolean fields remain checkboxes or switches.

- [ ] **Step 4: Compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt
git commit -m "Add tidy profile editor action layout"
```

## Task 8: Unsaved-Changes Guard Model

**Files:**
- Create if needed: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/UnsavedEditorState.kt`
- Create if needed: `app/src/test/kotlin/org/rtkcollector/app/ui/profiles/UnsavedEditorStateTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.rtkcollector.app.ui.profiles

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnsavedEditorStateTest {
    @Test
    fun `unchanged editor can leave immediately`() {
        val state = UnsavedEditorState(savedFingerprint = "a", currentFingerprint = "a")
        assertFalse(state.hasUnsavedChanges)
        assertTrue(state.canLeaveWithoutPrompt)
    }

    @Test
    fun `changed editor requires prompt`() {
        val state = UnsavedEditorState(savedFingerprint = "a", currentFingerprint = "b")
        assertTrue(state.hasUnsavedChanges)
        assertFalse(state.canLeaveWithoutPrompt)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :app:testDebugUnitTest --tests org.rtkcollector.app.ui.profiles.UnsavedEditorStateTest
```

Expected: FAIL because `UnsavedEditorState` is not defined.

- [ ] **Step 3: Implement model**

```kotlin
package org.rtkcollector.app.ui.profiles

data class UnsavedEditorState(
    val savedFingerprint: String,
    val currentFingerprint: String,
) {
    val hasUnsavedChanges: Boolean
        get() = savedFingerprint != currentFingerprint

    val canLeaveWithoutPrompt: Boolean
        get() = !hasUnsavedChanges
}
```

- [ ] **Step 4: Wire prompt in editor screen**

When Back is pressed in an editor:

- if no unsaved changes, call existing `onBack`;
- if unsaved changes, show dialog with `Save`, `Discard`, `Cancel`;
- Save calls the existing save action then leaves;
- Discard leaves without saving;
- Cancel dismisses dialog.

- [ ] **Step 5: Run tests and compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :app:testDebugUnitTest --tests org.rtkcollector.app.ui.profiles.UnsavedEditorStateTest
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: both commands pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/profiles/UnsavedEditorState.kt app/src/test/kotlin/org/rtkcollector/app/ui/profiles/UnsavedEditorStateTest.kt app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt
git commit -m "Guard profile editors against unsaved navigation"
```

## Task 9: Visual Preview States

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Modify if useful: `app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt`
- Modify if useful: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`

- [ ] **Step 1: Add or update Compose previews**

Add preview states for:

- compact dashboard recording state;
- compact dashboard ready/missing selection state;
- rail dashboard wide state;
- grouped settings hub.

Use realistic GNSS content from the spec:

```text
50.0931721, 14.4210168
RTK Float
17 / 24
TUBO00CZE0
receiver-rx.raw 18.4 MB
```

- [ ] **Step 2: Compile previews**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt app/src/main/kotlin/org/rtkcollector/app/ui/settings/SettingsHub.kt app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt
git commit -m "Add tidy UI preview states"
```

## Task 10: Final Verification And Review

**Files:**
- Review all modified UI files.
- Do not modify protocol or recording files unless a compile error proves an API adjustment is required.

- [ ] **Step 1: Run targeted tests**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardLayoutModelsTest --tests org.rtkcollector.app.ui.profiles.UnsavedEditorStateTest --tests org.rtkcollector.app.ui.dashboard.DashboardStateTest --tests org.rtkcollector.app.ui.profiles.ProfileListModelsTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run app Kotlin compile**

Run:

```bash
ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK ANDROID_SDK_ROOT=/storage/3830-3863/Termux/AndroidSDK sh gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Inspect diff against visual acceptance criteria**

Check:

- dashboard setup strip is tile-like, not scattered chips;
- cards use consistent label/value rows;
- all Position/Fix/Corrections/Recording fields remain represented;
- Start/Stop remains in frozen bottom bar;
- Settings hub is grouped;
- profile active state is green row plus `Active` label;
- editor actions remain visible at top;
- empty shutdown field is empty;
- help overlays are local dialogs.

- [ ] **Step 4: Commit any final documentation adjustment**

If the implementation reveals a deliberate deviation from the spec, update:

```text
docs/superpowers/specs/2026-06-10-app-wide-ui-tidy-design.md
```

Then commit:

```bash
git add docs/superpowers/specs/2026-06-10-app-wide-ui-tidy-design.md
git commit -m "Clarify UI tidy implementation notes"
```

- [ ] **Step 5: Push only after final quality gate**

Use the repository’s usual final command when requested:

```bash
git status --short
git push origin main
```

Do not commit `.superpowers/` visual companion artifacts.

## Plan Self-Review

Spec coverage:

- Dashboard compact layout: Tasks 2, 3, 9, 10.
- Rail layout: Tasks 1, 4, 9, 10.
- Grouped settings: Task 5.
- Profile lists/editors: Tasks 6, 7, 8.
- Help overlays: Tasks 2, 3.
- Keyboard behaviour: Task 7 preserves native text fields; Task 10 checks no custom key handling was added.
- Responsive/frozen controls: Tasks 3, 4, 9, 10.
- Out-of-scope protection: Task 10 forbids protocol/recording changes unless required for compile.

Placeholder scan:

- No task uses `TBD`, `TODO`, `similar to`, or open-ended “add appropriate” wording.
- Every test task includes concrete test code and commands.

Type consistency:

- `DashboardLayoutPreference`, `HelpTopic`, `HelpOverlay`, `TidyTopBar`, `TidyPill`, `TidyMetricRow`, `HelpIcon`, and `UnsavedEditorState` are defined before later tasks reference them.
