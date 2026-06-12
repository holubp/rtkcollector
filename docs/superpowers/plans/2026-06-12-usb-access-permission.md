# USB Access Permission Robustness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Start request USB permission automatically when missing, verify real USB open access before session creation, and improve Android USB attach/default-app coverage for expected serial GNSS bridges.

**Architecture:** Keep Activity/UI responsible for Android permission prompts and user messages. Keep `RecordingForegroundService` responsible for the authoritative access verification before opening session writers. Add small pure Kotlin USB access decision helpers so older-Android permission states can be tested without service instrumentation.

**Tech Stack:** Kotlin, Android USB Host API, Jetpack Compose app shell, foreground service, existing Gradle/JUnit tests.

---

## Reference Spec

Implement `docs/superpowers/specs/2026-06-12-usb-access-permission-design.md`.

## File Map

- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModels.kt`
  - Add pure USB permission/start decision types and user-facing messages.
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModelsTest.kt`
  - Test permission-missing, permission-reported-granted, no-device and open-failed messages.
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  - Reuse the decision helper in Start and USB access actions.
  - Auto-request permission from Start when permission is missing.
  - Show the exact "press Start again" message.
  - Show permission grant/deny messages from the permission broadcast.
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
  - Keep USB open before session writer creation.
  - Emit explicit stale-permission/device-busy open-failure text.
- Modify: `app/src/main/res/xml/usb_device_filter.xml`
  - Keep the observed UM980 FTDI filter and add common serial bridge filters used by GNSS receiver modules.
- Modify: `docs/android-background-operation.md`
  - Document the USB permission/open verification boundary.
- Modify: `docs/user-workflows.md`
  - Document the two-step Start flow when permission is missing.

## Task 1: Add Pure USB Access Decision Model

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModels.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModelsTest.kt`

- [ ] **Step 1: Add failing tests**

Append these tests to `UsbSelectionModelsTest.kt`:

```kotlin
@Test
fun `start with no connected receiver reports no device`() {
    val result = UsbStartAccessDecision.evaluate(
        deviceConnected = false,
        permissionReportedGranted = false,
    )

    assertEquals(UsbStartAccessAction.NO_DEVICE, result.action)
    assertEquals("Selected USB receiver is not connected.", result.message)
}

@Test
fun `start with missing permission requests permission and tells user to press start again`() {
    val result = UsbStartAccessDecision.evaluate(
        deviceConnected = true,
        permissionReportedGranted = false,
    )

    assertEquals(UsbStartAccessAction.REQUEST_PERMISSION, result.action)
    assertEquals(
        "USB permission requested. Approve the Android permission dialog, then press Start again.",
        result.message,
    )
}

@Test
fun `start with reported permission verifies access`() {
    val result = UsbStartAccessDecision.evaluate(
        deviceConnected = true,
        permissionReportedGranted = true,
    )

    assertEquals(UsbStartAccessAction.VERIFY_AND_START, result.action)
    assertEquals("USB permission reported granted; access will be verified on Start.", result.message)
}

@Test
fun `open failure message names stale permission and busy receiver cases`() {
    assertEquals(
        "Android reports USB permission, but the receiver could not be opened. Reconnect the receiver, close other serial apps, then retry USB access.",
        UsbStartAccessDecision.openFailureMessage(),
    )
}
```

- [ ] **Step 2: Run the failing tests**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.usb.UsbSelectionModelsTest
```

Expected in a normal Android SDK environment: tests fail because `UsbStartAccessDecision` and `UsbStartAccessAction` do not exist.

Expected in the current Termux environment: command may fail earlier at `:app:processDebugResources` with the known `aapt2` native binary error.

- [ ] **Step 3: Add the model**

Append this code to `UsbSelectionModels.kt`:

```kotlin
enum class UsbStartAccessAction {
    NO_DEVICE,
    REQUEST_PERMISSION,
    VERIFY_AND_START,
}

data class UsbStartAccessResult(
    val action: UsbStartAccessAction,
    val message: String,
)

object UsbStartAccessDecision {
    fun evaluate(
        deviceConnected: Boolean,
        permissionReportedGranted: Boolean,
    ): UsbStartAccessResult =
        when {
            !deviceConnected -> UsbStartAccessResult(
                action = UsbStartAccessAction.NO_DEVICE,
                message = "Selected USB receiver is not connected.",
            )
            !permissionReportedGranted -> UsbStartAccessResult(
                action = UsbStartAccessAction.REQUEST_PERMISSION,
                message = "USB permission requested. Approve the Android permission dialog, then press Start again.",
            )
            else -> UsbStartAccessResult(
                action = UsbStartAccessAction.VERIFY_AND_START,
                message = "USB permission reported granted; access will be verified on Start.",
            )
        }

    fun permissionDeniedMessage(): String =
        "USB permission was denied. Approve USB access before starting recording."

    fun permissionGrantedMessage(): String =
        "USB permission granted. Press Start again to begin recording."

    fun openFailureMessage(): String =
        "Android reports USB permission, but the receiver could not be opened. Reconnect the receiver, close other serial apps, then retry USB access."
}
```

- [ ] **Step 4: Verify model tests**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.usb.UsbSelectionModelsTest
```

Expected in a normal Android SDK environment: `UsbSelectionModelsTest` passes.

Expected in the current Termux environment: the Android resource-processing `aapt2` blocker may prevent execution.

- [ ] **Step 5: Commit checkpoint**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModels.kt app/src/test/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModelsTest.kt
git commit -m "Add USB access start decision model"
```

## Task 2: Auto-Request USB Permission From Start

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModelsTest.kt`

- [ ] **Step 1: Add imports**

In `MainActivity.kt`, extend the existing USB UI imports:

```kotlin
import org.rtkcollector.app.ui.usb.UsbStartAccessAction
import org.rtkcollector.app.ui.usb.UsbStartAccessDecision
```

- [ ] **Step 2: Add a reusable permission request helper**

Near `requestUsbPermissionForProfile(...)`, add:

```kotlin
private fun requestUsbPermissionForDevice(context: Context, device: UsbDevice) {
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }
    val permissionIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
        flags,
    )
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    usbManager.requestPermission(device, permissionIntent)
}
```

- [ ] **Step 3: Reuse the helper in the USB access button path**

In `requestUsbPermissionForProfile(...)`, replace the inline `PendingIntent` creation and `usbManager.requestPermission(...)` block with:

```kotlin
requestUsbPermissionForDevice(context, device)
Toast.makeText(
    context,
    "USB permission requested. Approve the Android permission dialog, then press Start again.",
    Toast.LENGTH_LONG,
).show()
```

Keep the existing early return when `usbManager.hasPermission(device)` is true.

- [ ] **Step 4: Use the decision helper in Start**

In `buildDashboardStartIntent(...)`, replace the current permission check:

```kotlin
if (!usbManager.hasPermission(usbDevice)) {
    Toast.makeText(context, "Cannot start: USB permission is not granted.", Toast.LENGTH_LONG).show()
    return null
}
```

with:

```kotlin
val usbAccess = UsbStartAccessDecision.evaluate(
    deviceConnected = true,
    permissionReportedGranted = usbManager.hasPermission(usbDevice),
)
when (usbAccess.action) {
    UsbStartAccessAction.NO_DEVICE -> {
        Toast.makeText(context, usbAccess.message, Toast.LENGTH_LONG).show()
        return null
    }
    UsbStartAccessAction.REQUEST_PERMISSION -> {
        requestUsbPermissionForDevice(context, usbDevice)
        Toast.makeText(context, usbAccess.message, Toast.LENGTH_LONG).show()
        return null
    }
    UsbStartAccessAction.VERIFY_AND_START -> Unit
}
```

Leave the existing no-device branch above this block unchanged. The no-device
branch still gives the more specific "selected" versus "any" receiver message.

- [ ] **Step 5: Verify Kotlin compilation**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: build succeeds.

- [ ] **Step 6: Attempt targeted app tests**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.usb.UsbSelectionModelsTest
```

Expected in a normal Android SDK environment: tests pass.

Expected in the current Termux environment: command may be blocked by `aapt2`.

- [ ] **Step 7: Commit checkpoint**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/main/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModels.kt app/src/test/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModelsTest.kt
git commit -m "Request USB permission from Start"
```

## Task 3: Report Permission Broadcast Results Clearly

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Update the broadcast receiver branch**

In the `DisposableEffect(context)` broadcast receiver in `MainActivity.kt`, replace the current grouped branch:

```kotlin
UsbManager.ACTION_USB_DEVICE_ATTACHED,
UsbManager.ACTION_USB_DEVICE_DETACHED,
ACTION_USB_PERMISSION -> {
    profileRevision++
}
```

with:

```kotlin
UsbManager.ACTION_USB_DEVICE_ATTACHED,
UsbManager.ACTION_USB_DEVICE_DETACHED -> {
    profileRevision++
}
ACTION_USB_PERMISSION -> {
    profileRevision++
    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
    val message = if (granted) {
        UsbStartAccessDecision.permissionGrantedMessage()
    } else {
        UsbStartAccessDecision.permissionDeniedMessage()
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
```

- [ ] **Step 2: Verify Kotlin compilation**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: build succeeds.

- [ ] **Step 3: Commit checkpoint**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt
git commit -m "Show USB permission result guidance"
```

## Task 4: Use Explicit Open-Failure Guidance In The Service

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModels.kt`

- [ ] **Step 1: Import the USB decision helper**

In `RecordingForegroundService.kt`, add:

```kotlin
import org.rtkcollector.app.ui.usb.UsbStartAccessDecision
```

- [ ] **Step 2: Replace the generic open failure text**

In `startRecording(...)`, find the `runCatching { usbTransport.open() }.onFailure { ... }` block. Replace the current `error(openFailure.message)` line with:

```kotlin
error(UsbStartAccessDecision.openFailureMessage())
```

Keep `runCatching { usbTransport.close() }` before the error.

- [ ] **Step 3: Verify service still opens before session writers**

Inspect `RecordingForegroundService.kt` and confirm the order remains:

```kotlin
runCatching { usbTransport.open() }
transport = usbTransport
val openedSession = openSessionWriters(intent)
```

If `openSessionWriters(intent)` appears before `usbTransport.open()`, stop and fix the ordering before continuing.

- [ ] **Step 4: Verify Kotlin compilation**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: build succeeds.

- [ ] **Step 5: Commit checkpoint**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt app/src/main/kotlin/org/rtkcollector/app/ui/usb/UsbSelectionModels.kt
git commit -m "Clarify stale USB permission open failures"
```

## Task 5: Broaden USB Attach Filters For Common Serial GNSS Bridges

**Files:**
- Modify: `app/src/main/res/xml/usb_device_filter.xml`

- [ ] **Step 1: Replace USB filter contents**

Replace `usb_device_filter.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- FTDI bridge observed with the UM980 USB module: 0403:6015. -->
    <usb-device
        vendor-id="1027"
        product-id="24597" />

    <!-- Common FTDI USB serial bridge IDs used by GNSS receiver carrier boards. -->
    <usb-device
        vendor-id="1027"
        product-id="24577" />
    <usb-device
        vendor-id="1027"
        product-id="24592" />
    <usb-device
        vendor-id="1027"
        product-id="24593" />
    <usb-device
        vendor-id="1027"
        product-id="24596" />

    <!-- Common Silicon Labs CP210x USB serial bridge. -->
    <usb-device
        vendor-id="4292"
        product-id="60000" />

    <!-- Common WCH CH340/CH341 USB serial bridge. -->
    <usb-device
        vendor-id="6790"
        product-id="29987" />

    <!-- Common Prolific PL2303 USB serial bridge. -->
    <usb-device
        vendor-id="1659"
        product-id="8963" />
</resources>
```

These values are decimal Android resource values for:

- `0403:6001`
- `0403:6010`
- `0403:6011`
- `0403:6014`
- `0403:6015`
- `10C4:EA60`
- `1A86:7523`
- `067B:2303`

- [ ] **Step 2: Verify XML resource processing where available**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: Kotlin compilation succeeds. In this Termux environment this command
has already been the reliable source-level check.

On Windows Android Studio or CI, run:

```bash
gradlew.bat :app:assembleDebug
```

Expected: APK assembly succeeds and XML resource parsing passes.

- [ ] **Step 3: Commit checkpoint**

```bash
git add app/src/main/res/xml/usb_device_filter.xml
git commit -m "Expand USB attach filters for serial GNSS bridges"
```

## Task 6: Documentation Updates

**Files:**
- Modify: `docs/android-background-operation.md`
- Modify: `docs/user-workflows.md`

- [ ] **Step 1: Update Android background operation docs**

Add this paragraph to the USB/recording robustness area of
`docs/android-background-operation.md`:

```markdown
USB permission and access are separate gates. The UI may request Android USB
permission, but the foreground service must verify actual USB open/claim access
before session writers are opened. If Start requests permission, the user must
approve the Android dialog and press Start again; V1 does not auto-start
recording from the permission callback.
```

- [ ] **Step 2: Update user workflow docs**

Add this paragraph to the recording startup section of `docs/user-workflows.md`:

```markdown
If a receiver is connected but Android USB permission is missing, pressing Start
requests permission and does not create a recording session. Approve the Android
permission dialog, then press Start again. If Android reports permission granted
but the receiver cannot be opened, reconnect the receiver, close other serial
apps and retry USB access.
```

- [ ] **Step 3: Verify docs diff**

Run:

```bash
git diff --check docs/android-background-operation.md docs/user-workflows.md
```

Expected: no output.

- [ ] **Step 4: Commit checkpoint**

```bash
git add docs/android-background-operation.md docs/user-workflows.md
git commit -m "Document USB permission startup flow"
```

## Task 7: Final Verification And Review

**Files:**
- No new files.

- [ ] **Step 1: Run source compilation**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: build succeeds.

- [ ] **Step 2: Run pure receiver tests unaffected by Android resources**

Run:

```bash
sh gradlew :receiver:unicore-n4:test --tests org.rtkcollector.receiver.unicore.Um980MessageFrequencyTrackerTest --tests org.rtkcollector.receiver.unicore.Um980ModeParserTest
```

Expected: tests pass.

- [ ] **Step 3: Attempt targeted app tests**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.usb.UsbSelectionModelsTest --tests org.rtkcollector.app.recording.RecordingStartPreflightTest
```

Expected in a normal Android SDK environment: tests pass.

Expected in the current Termux environment: command may fail at
`:app:processDebugResources` with the known cached Linux `aapt2` binary error.
If that happens, record the exact failure and do not treat it as a source
failure.

- [ ] **Step 4: Run diff hygiene**

Run:

```bash
git diff --check HEAD
```

Expected: no output.

- [ ] **Step 5: Request code review**

Use `superpowers:requesting-code-review` with this scope:

```text
Review the USB access permission implementation against
docs/superpowers/specs/2026-06-12-usb-access-permission-design.md and
docs/superpowers/plans/2026-06-12-usb-access-permission.md.

Focus on:
- Start automatically requests USB permission when missing.
- User is told to approve permission and press Start again.
- hasPermission true is not treated as final access proof.
- USB open remains before session writer creation.
- stale permission/device-busy open failure is reported clearly.
- USB attach filters are reasonable and not a raw-capture risk.
```

- [ ] **Step 6: Apply reviewer must-fix feedback**

If the reviewer reports Critical or Important issues, fix them before push.
For each fix, run:

```bash
sh gradlew :app:compileDebugKotlin
git diff --check HEAD
```

Expected: compilation succeeds and diff check has no output.

- [ ] **Step 7: Push after clean review**

Run:

```bash
git status --short --branch
git push origin main
```

Expected: `main` is pushed. Local-only untracked `.codex-tmp/`, `.superpowers/`
and `samples/` remain uncommitted.
