# Google Play Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare RtkCollector for a defensible Google Play test or production listing by closing security, privacy, permissions, foreground-service, export, licence and documentation consistency gaps found in the pre-publication review.

**Architecture:** Keep the raw GNSS capture architecture unchanged. This plan adds publication-facing documentation, tightens Android permissions and import surfaces, adds explicit runtime permission/security models, cleans sensitive backup files after sharing, and makes session/privacy documentation match V1 behaviour. All changes must preserve byte-exact `receiver-rx.raw`, separate TX/correction artifacts, service-owned recording, and no map/GIS/shapefile dependencies.

**Tech Stack:** Android/Kotlin, Jetpack Compose, AndroidX Core/Activity, Android foreground services, Android Keystore, Java sockets, optional future TLS sockets, JUnit 5, existing Gradle modules.

---

## 2026-07-08 Pre-Execution Review

This plan remains the execution source for Google Play readiness, but it predates
later NTRIP source-upload work, release-native-library Gradle checks and the
formal specification tree. Execute it with the complementary design in
`docs/superpowers/specs/2026-07-08-google-play-readiness-execution-design.md`
and the formal requirements in `docs/specification/publication-readiness.md`.

Current-state corrections before assigning tasks:

- `PRIVACY.md` and `docs/play-publication.md` are still missing and remain
  required outputs.
- `ACCESS_MOCK_LOCATION` is already debug-only; do not add it to the main
  manifest.
- The main manifest still lacks `POST_NOTIFICATIONS` for the target SDK 36
  recording notification flow.
- Release Gradle tasks already validate that Google Play release bundles do not
  silently omit required RTKLIB native libraries; verify and preserve those
  checks rather than duplicating them.
- NTRIP source upload v1/v2 is now a separate implemented path. Do not regress
  classic v1 `SOURCE <password> /mount` or v2 chunked `POST` source upload while
  working on NTRIP correction-download transport disclosure or TLS options.
- NTRIP TLS support is useful but not a strict internal/closed testing blocker
  if Play Data safety explicitly does not claim universal encryption in transit.
- Use current split profile/settings files when implementing model changes; do
  not assume the old file layout in this historical plan is exact.
- Full release AAB validation must run on Windows Android Studio, CI or another
  host with working Android SDK/NDK native tools. Termux can still run
  `git diff --check`, `sh gradlew :app:compileDebugKotlin` and Android Studio
  task-selection dry runs.

## Scope And Source References

This plan addresses the review findings:

- No Play-ready privacy policy or Data safety source.
- Missing Android 13+ notification permission handling for target SDK 36.
- Plaintext settings backups can remain in cache after sharing.
- NTRIP correction download and source upload can use plaintext TCP with Basic
  Auth; Play disclosures must be truthful and must not claim universal
  encryption in transit unless TLS support is actually shipped and selected.
- `docs/session-format.md` describes planned fields as required V1 fields.
- `docs/third-party-licenses.md` does not list current runtime dependencies.
- JSON import manifest surface is broader than needed.
- Battery optimisation warning is documented but not clearly implemented.
- Foreground service type/rationale needs Play-facing justification.

Official references to use while implementing:

- Google Play Data safety form: <https://support.google.com/googleplay/android-developer/answer/10787469>
- Google Play User Data policy: <https://support.google.com/googleplay/android-developer/answer/10144311>
- Android notification runtime permission: <https://developer.android.com/develop/ui/compose/notifications/notification-permission>
- Android foreground service types: <https://developer.android.com/develop/background-work/services/fgs/service-types>

## File Structure

Create:

- `PRIVACY.md` - human-readable app privacy policy source for Play listing and README link.
- `docs/play-publication.md` - Play Console checklist and Data safety/FGS/permission declaration source.
- `app/src/main/kotlin/org/rtkcollector/app/permissions/RecordingPermissionModel.kt` - pure Kotlin policy model for notification permission and battery warning state.
- `app/src/test/kotlin/org/rtkcollector/app/permissions/RecordingPermissionModelTest.kt` - unit tests for the permission policy.
- `app/src/main/kotlin/org/rtkcollector/app/share/SettingsBackupSharePolicy.kt` - pure Kotlin filename and cleanup policy for settings backups.
- `app/src/test/kotlin/org/rtkcollector/app/share/SettingsBackupSharePolicyTest.kt` - unit tests for backup filename and cleanup policy.

Modify:

- `README.md` - link privacy/publishing docs and correct current transport wording.
- `SECURITY.md` - link privacy doc and document plaintext backup / NTRIP cleartext risk.
- `docs/android-background-operation.md` - align foreground service, notification permission and battery warning behaviour.
- `docs/ntrip-and-corrections.md` - document cleartext NTRIP transport, GGA/Data safety implications and the fact that TLS is not currently universal.
- `docs/session-format.md` - split current V1 session fields from planned fields.
- `docs/third-party-licenses.md` - list current runtime and test dependencies with licence families.
- `docs/user-workflows.md` - document settings import/export security and cleanup behaviour.
- `app/src/main/AndroidManifest.xml` - add notification/connected-device foreground service declarations and narrow JSON import surface.
- `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt` - request notification permission before recording, show battery warning, and schedule settings-backup cleanup.
- `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripClient.kt` - review correction-download transport behaviour and keep request syntax unchanged unless a separate TLS feature is explicitly chosen.
- `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripCasterUploadClient.kt` - review source-upload transport behaviour and preserve v1/v2 source-upload protocol syntax.
- Existing tests:
  - `app/src/test/kotlin/org/rtkcollector/app/ui/AndroidManifestJsonImportTest.kt`
  - `app/src/test/kotlin/org/rtkcollector/app/profile/SettingsBackupModelsTest.kt`
  - `app/src/test/kotlin/org/rtkcollector/app/profile/SettingsImportModelsTest.kt`
  - `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripClientTest.kt`
  - `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripCasterUploadClientTest.kt`
  - `core/session/src/test/kotlin/org/rtkcollector/core/session/SessionWritersTest.kt`

Do not modify:

- Raw capture, UM980 command sequencing, USB serial read/write loops, parser fanout, session writer byte paths, map/GIS dependencies.

---

### Task 0: Current-State Validation And Task Pruning

**Files:**
- Inspect: `app/src/main/AndroidManifest.xml`
- Inspect: `app/src/debug/AndroidManifest.xml`
- Inspect: `app/build.gradle.kts`
- Inspect: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripCasterUploadClient.kt`
- Inspect: `docs/specification/publication-readiness.md`
- Inspect: `docs/specification/verification-matrix.md`

- [ ] **Step 1: Verify publication documents are still missing before creating them**

Run:

```bash
test ! -f PRIVACY.md
test ! -f docs/play-publication.md
```

Expected before Task 1: both commands exit successfully. If either file already
exists, inspect it and update Task 1 rather than overwriting it blindly.

- [ ] **Step 2: Verify mock-location permission stays debug-only**

Run:

```bash
rg -n "ACCESS_MOCK_LOCATION" app/src/main app/src/debug
```

Expected:

```text
app/src/debug/AndroidManifest.xml:2:    <uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />
```

No `ACCESS_MOCK_LOCATION` hit may appear under `app/src/main`.

- [ ] **Step 3: Verify notification permission is still missing from the main manifest**

Run:

```bash
rg -n "POST_NOTIFICATIONS" app/src/main/AndroidManifest.xml
```

Expected before Task 3: no output and exit status 1. Task 3 must add the release
manifest declaration and runtime handling.

- [ ] **Step 4: Verify release-native-library checks already exist**

Run:

```bash
rg -n "validateGooglePlayReleaseBuildInputs|validateGooglePlayReleaseBundle|librtkcollector_rtklib.so|abiFilters" app/build.gradle.kts
```

Expected: hits for release input validation, release bundle validation,
`librtkcollector_rtklib.so`, and `armeabi-v7a` plus `arm64-v8a` ABI filters.
Do not duplicate this Gradle logic; preserve it and exercise it in Task 9 on a
release-capable host.

- [ ] **Step 5: Verify NTRIP source upload remains separate from rover correction download**

Run:

```bash
rg -n "NtripCasterUploadRequest|NtripSourceUploadRequest|SOURCE |POST /|GET /" core/correction/src/main/kotlin/org/rtkcollector/core/correction
```

Expected: source upload request construction lives in
`NtripCasterUploadClient.kt`; rover correction download request construction
uses `NtripClient.kt`. Do not merge those roles while executing this plan.

- [ ] **Step 6: Record the current gate status**

Update only the notes for existing `PLAY-*` rows in
`docs/specification/verification-matrix.md` if Task 0 evidence differs from the
expected state above. Do not mark any `PLAY-*` row `Done` until the relevant
code/docs and manual release-host validation are complete.

---

### Task 1: Publication Privacy And Data Safety Documentation

**Files:**
- Create: `PRIVACY.md`
- Create: `docs/play-publication.md`
- Modify: `README.md`
- Modify: `SECURITY.md`
- Modify: `docs/user-workflows.md`
- Modify: `docs/ntrip-and-corrections.md`

- [ ] **Step 1: Create `PRIVACY.md`**

Create `PRIVACY.md` with this content:

```markdown
# RtkCollector Privacy Policy

Effective date: 2026-06-14

RtkCollector is an Android GNSS receiver companion for byte-exact receiver
recording, NTRIP correction intake, receiver control and temporary-base
preparation. The app is designed to operate locally on the user's Android
device. It does not include advertising, analytics SDKs, crash-reporting SDKs,
maps, shapefiles, GIS editing or field-feature collection.

## Data Processed By The App

RtkCollector can process and store:

- precise GNSS positions received from an attached receiver;
- receiver raw byte streams and derived solution logs;
- receiver identifiers, USB VID/PID, product names and serial/baud settings;
- NTRIP caster host, port, mountpoint, username and secret references;
- NTRIP passwords stored locally through Android Keystore-backed storage;
- user-created receiver command scripts, storage profiles and settings sets;
- session files, ZIP archives and settings backup JSON files selected by the user.

## Local Storage

Recording sessions are stored in the configured app-private folder or Android
Storage Access Framework location. Session metadata must not contain NTRIP
passwords or tokens. Receiver RX, app TX to receiver, correction input, events,
quality logs and derived solution files are stored as separate artifacts.

NTRIP passwords are stored locally and encrypted with Android Keystore-backed
AES-GCM where Android provides the required Keystore services. Android backup is
disabled for the app.

## Network Transfers

When the user enables NTRIP, RtkCollector connects to the configured caster and
mountpoint. The app sends NTRIP credentials to that caster when credentials are
configured. If GGA upload is enabled, the app may send the receiver-derived
rover position to the selected caster for NTRIP/VRS operation.

Many NTRIP casters use ordinary TCP. In that mode credentials, source-upload
authentication and optional GGA data are not protected by TLS. Do not treat
NTRIP data as encrypted in transit unless a future release explicitly implements
and uses TLS transport for the selected caster endpoint.

RtkCollector does not operate an app backend service for sessions, credentials
or telemetry.

## Sharing And Export

Users may explicitly share session ZIPs, session files or settings backup JSON
files through Android's share sheet. Settings backup export can optionally
include plaintext NTRIP passwords. This option is off by default and should be
used only when transferring settings to a trusted device.

Temporary share files are written to app cache and should be removed by the app
after sharing on a best-effort basis. Recipients selected in Android's share
sheet are outside RtkCollector's control.

## Deletion And Retention

Users control retention by deleting sessions, archives and settings inside the
app or through Android storage tools. Removing the app removes app-private
storage on normal Android installations. Files stored in user-selected external
locations remain under the user's control.

## Permissions

RtkCollector uses:

- USB host access to communicate with GNSS receivers;
- Internet access for NTRIP caster connections;
- foreground service and wake lock access to keep recording active while the
  screen is off;
- notification permission on Android versions that require it for foreground
  service notifications.

The app does not request Android location permission for receiver-derived GNSS
positions from an external USB receiver.

## Contact

Report privacy or security concerns through the repository security process in
`SECURITY.md`.
```

- [ ] **Step 2: Create `docs/play-publication.md`**

Create `docs/play-publication.md` with this content:

```markdown
# Google Play Publication Checklist

This checklist is the source document for Play Console publication declarations.
It must be reviewed before every Play release.

## Current Intended Release Status

RtkCollector is experimental GNSS field-recording software. Store listing text
must say that hardware support and receiver commands are experimental and that
users must validate recordings before operational use.

## Play Data Safety Draft

Declare the following data handling based on current V1 behaviour:

- Precise location: processed locally from external receiver streams; stored in
  user-controlled session files; optionally transmitted to the selected NTRIP
  caster if GGA upload is enabled.
- Files and docs: user-created session files, session archives and settings
  backups can be shared through Android share targets selected by the user.
- Device or other IDs: USB VID/PID, receiver identification and profile IDs are
  stored in session metadata/settings for app functionality.
- Personal info/User IDs: NTRIP username may be stored locally and transmitted
  to the selected NTRIP caster for authentication.
- App info and performance: local recording status and error messages are shown
  in the app and session sidecars; no analytics SDK is present.

Security practice declarations:

- Encryption in transit: do not claim universal encryption while cleartext NTRIP
  TCP is supported. Claim encrypted transit only for TLS caster connections once
  implemented and selected.
- Data deletion: users can delete local sessions and archives in the app.
- No advertising or analytics SDKs are included.

## Privacy Policy

The Play listing must link to the published version of `PRIVACY.md`.

## Permission Rationale

- `INTERNET`: connects to user-configured NTRIP casters.
- `FOREGROUND_SERVICE` and foreground service type permissions: keeps user
  started recording and correction routing active in the background.
- `WAKE_LOCK`: keeps capture running while the screen is off.
- `POST_NOTIFICATIONS`: displays the foreground recording notification on
  Android versions that require runtime notification permission.
- USB host feature: communicates with attached GNSS receivers after Android USB
  permission is granted.

## Foreground Service Declaration

The foreground service is user initiated by pressing Start. It records receiver
data and may route NTRIP corrections to an attached receiver while the app is in
the background. The notification must show recording state, receiver RX bytes and
NTRIP correction bytes.

## Release Validation

Before upload:

1. Run `git diff --check`.
2. Run `sh gradlew :app:compileDebugKotlin` in Termux or `./gradlew :app:compileDebugKotlin` on a desktop host.
3. Run `./gradlew test` on a host where Android Gradle plugin native tools run.
4. Build a signed release AAB on Windows Android Studio or CI.
5. Install the release build on at least one Android 13+ device and verify notification permission flow.
6. Verify a plain rover recording, rover + NTRIP recording, session ZIP share and settings backup import/export.
7. Confirm `docs/third-party-licenses.md`, `PRIVACY.md`, `SECURITY.md` and Play Data safety answers match the shipped build.
```

- [ ] **Step 3: Update README and SECURITY links**

Add this paragraph to `README.md` after the licence/development status block:

```markdown
## Privacy And Publication Status

Google Play publication requires the privacy policy and Play disclosure checklist
to match the shipped APK/AAB. See [PRIVACY.md](PRIVACY.md) and
[docs/play-publication.md](docs/play-publication.md). RtkCollector can store
precise receiver-derived locations and NTRIP settings locally, and can transmit
NTRIP credentials and optional GGA positions to user-selected casters.
```

Add this paragraph to `SECURITY.md` after the NTRIP credential section:

```markdown
Privacy and publication disclosures are tracked in [PRIVACY.md](PRIVACY.md) and
[docs/play-publication.md](docs/play-publication.md). Security-sensitive changes
to NTRIP credentials, settings backup import/export, FileProvider sharing,
foreground services or session metadata must update those documents in the same
change.
```

- [ ] **Step 4: Update NTRIP and user workflow docs**

In `docs/ntrip-and-corrections.md`, add a "Transport security" section:

```markdown
## Transport Security

NTRIP Basic authentication over ordinary TCP exposes credentials to the caster
path in cleartext. RtkCollector must label cleartext NTRIP behaviour clearly and
must not claim encrypted transit in Play Data safety disclosures while cleartext
NTRIP correction download or source upload is supported. GGA upload, when
enabled, sends receiver-derived position to the configured caster and must be
disclosed as optional location transmission.
```

In `docs/user-workflows.md`, add a "Settings backup import/export" section:

```markdown
## Settings Backup Import And Export

Settings backup export writes profiles, selected workflow/settings references
and optional NTRIP passwords to a JSON file selected through Android sharing.
Plaintext password export is off by default and requires explicit user selection.
Temporary settings backup files are cache artifacts and are deleted on a
best-effort basis after sharing. Imported JSON must be previewed and validated
before it replaces existing profiles or writes NTRIP passwords to local secret
storage.
```

- [ ] **Step 5: Verify documentation coverage**

Run:

```bash
rg -n "PRIVACY.md|play-publication|Data safety|plaintext NTRIP|Transport Security" README.md SECURITY.md docs
```

Expected: hits in `README.md`, `SECURITY.md`, `docs/play-publication.md`, `docs/ntrip-and-corrections.md`, and `docs/user-workflows.md`.

- [ ] **Step 6: Commit**

```bash
git add PRIVACY.md docs/play-publication.md README.md SECURITY.md docs/ntrip-and-corrections.md docs/user-workflows.md
git commit -m "docs: add Play privacy and publication checklist"
```

---

### Task 2: Third-Party Licence Documentation

**Files:**
- Modify: `docs/third-party-licenses.md`
- Inspect: `app/build.gradle.kts`
- Inspect: root/module `build.gradle.kts` files

- [ ] **Step 1: Replace bootstrap-only licence text**

Replace `docs/third-party-licenses.md` with:

```markdown
# Third-Party Licences

RtkCollector is GPL-3.0-or-later. Before each release, verify this list against
the Gradle dependency graph used for the released AAB/APK.

## Runtime Dependencies

| Dependency | Purpose | Licence family |
| --- | --- | --- |
| Kotlin standard library | Kotlin runtime support | Apache-2.0 |
| Android Gradle Plugin runtime outputs | Android build/runtime support | Apache-2.0 |
| AndroidX Core KTX | Android compatibility helpers | Apache-2.0 |
| AndroidX Activity Compose | Compose activity integration | Apache-2.0 |
| Jetpack Compose UI | Compose UI runtime | Apache-2.0 |
| Jetpack Compose Material 3 | Material components | Apache-2.0 |
| Jetpack Compose UI tooling preview | UI previews and preview metadata | Apache-2.0 |

## Test And Build Dependencies

| Dependency | Purpose | Licence family |
| --- | --- | --- |
| Gradle | Build tool | Apache-2.0 |
| Kotlin Gradle plugin | Kotlin compilation | Apache-2.0 |
| Android Gradle Plugin | Android build integration | Apache-2.0 |
| JUnit Jupiter API/Engine | Unit tests | Eclipse Public License 2.0 |
| Compose UI tooling | Debug-only UI inspection | Apache-2.0 |

## Release Check

Run the Gradle dependency report on the release host and update this file if
dependencies change:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

No map, GIS, shapefile, advertising, analytics or crash-reporting SDK dependency
is intentionally included in V1.
```

- [ ] **Step 2: Verify dependency names**

Run:

```bash
rg -n "implementation|debugImplementation|testImplementation|testRuntimeOnly" app/build.gradle.kts build.gradle.kts core receiver
```

Expected: every runtime/test dependency visible in Gradle files is represented in `docs/third-party-licenses.md`.

- [ ] **Step 3: Commit**

```bash
git add docs/third-party-licenses.md
git commit -m "docs: update third-party licence inventory"
```

---

### Task 3: Notification Permission And Foreground Service Declarations

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/permissions/RecordingPermissionModel.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/permissions/RecordingPermissionModelTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `docs/android-background-operation.md`
- Modify: `docs/play-publication.md`

- [ ] **Step 1: Write failing tests for runtime permission policy**

Create `app/src/test/kotlin/org/rtkcollector/app/permissions/RecordingPermissionModelTest.kt`:

```kotlin
package org.rtkcollector.app.permissions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordingPermissionModelTest {
    @Test
    fun androidTiramisuAndNewerRequiresNotificationPermission() {
        assertEquals(
            listOf("android.permission.POST_NOTIFICATIONS"),
            runtimePermissionsRequiredBeforeRecording(sdkInt = 33),
        )
        assertEquals(
            listOf("android.permission.POST_NOTIFICATIONS"),
            runtimePermissionsRequiredBeforeRecording(sdkInt = 36),
        )
    }

    @Test
    fun androidBeforeTiramisuRequiresNoRuntimeNotificationPermission() {
        assertEquals(emptyList<String>(), runtimePermissionsRequiredBeforeRecording(sdkInt = 32))
    }

    @Test
    fun batteryWarningIsShownWhenOptimisationMayApply() {
        val warning = batteryOptimisationWarning(isIgnoringBatteryOptimisations = false)

        assertTrue(warning.show)
        assertEquals(
            "Battery optimisation may interrupt long GNSS recordings on this device.",
            warning.message,
        )
    }

    @Test
    fun batteryWarningIsHiddenWhenOptimisationIsAlreadyIgnored() {
        assertFalse(batteryOptimisationWarning(isIgnoringBatteryOptimisations = true).show)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.permissions.RecordingPermissionModelTest'
```

Expected on desktop/CI: FAIL because `RecordingPermissionModel.kt` does not exist. In Termux, if Android resource processing fails due local `aapt2`, record that environment blocker and continue with `sh gradlew :app:compileDebugKotlin` after implementation.

- [ ] **Step 3: Add the pure permission model**

Create `app/src/main/kotlin/org/rtkcollector/app/permissions/RecordingPermissionModel.kt`:

```kotlin
package org.rtkcollector.app.permissions

data class BatteryOptimisationWarning(
    val show: Boolean,
    val message: String,
)

fun runtimePermissionsRequiredBeforeRecording(sdkInt: Int): List<String> =
    if (sdkInt >= 33) {
        listOf("android.permission.POST_NOTIFICATIONS")
    } else {
        emptyList()
    }

fun batteryOptimisationWarning(isIgnoringBatteryOptimisations: Boolean): BatteryOptimisationWarning =
    if (isIgnoringBatteryOptimisations) {
        BatteryOptimisationWarning(show = false, message = "")
    } else {
        BatteryOptimisationWarning(
            show = true,
            message = "Battery optimisation may interrupt long GNSS recordings on this device.",
        )
    }
```

- [ ] **Step 4: Update manifest permissions and service type**

Modify `app/src/main/AndroidManifest.xml` permissions:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

Modify the recording service declaration:

```xml
<service
    android:name=".recording.RecordingForegroundService"
    android:exported="false"
    android:foregroundServiceType="connectedDevice|dataSync" />
```

- [ ] **Step 5: Request notification permission before recording**

In `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`, add imports:

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.rtkcollector.app.permissions.runtimePermissionsRequiredBeforeRecording
```

Inside `RtkCollectorApp`, before the Start button callback is built, add:

```kotlin
var pendingStartAfterNotificationPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
) { granted ->
    val pending = pendingStartAfterNotificationPermission
    pendingStartAfterNotificationPermission = null
    if (granted) {
        pending?.invoke()
    } else {
        Toast.makeText(
            context,
            "Recording needs notification permission so Android can show the foreground recording notification.",
            Toast.LENGTH_LONG,
        ).show()
    }
}
```

Wrap the existing start-recording action with:

```kotlin
fun startAfterRuntimePermissionCheck(action: () -> Unit) {
    val missingNotificationPermission =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    if (missingNotificationPermission) {
        pendingStartAfterNotificationPermission = action
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        action()
    }
}
```

Change the Start button handler from:

```kotlin
onStartRecording()
```

to:

```kotlin
startAfterRuntimePermissionCheck { onStartRecording() }
```

- [ ] **Step 6: Show battery optimisation warning without requesting exemption**

In `MainActivity.kt`, add imports:

```kotlin
import android.os.PowerManager
import org.rtkcollector.app.permissions.batteryOptimisationWarning
```

Near dashboard warning rendering, compute:

```kotlin
val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
val batteryWarning = batteryOptimisationWarning(
    isIgnoringBatteryOptimisations = powerManager.isIgnoringBatteryOptimizations(context.packageName),
)
```

Display the warning only when not recording and `batteryWarning.show`:

```kotlin
if (!dashboardState.isRecording && batteryWarning.show) {
    DashboardWarningBanner(
        text = batteryWarning.message,
    )
}
```

Use the existing warning/banner component if one exists; if not, add a compact `Text` row using `MaterialTheme.colorScheme.tertiaryContainer` and `onTertiaryContainer`.

- [ ] **Step 7: Update docs**

In `docs/android-background-operation.md`, replace the battery optimisation bullet with:

```markdown
- Users receive an in-app warning when Android battery optimisation may affect
  long recordings. V1 warns but does not request battery-optimisation exemption
  automatically; users remain in control of vendor-specific battery settings.
```

In `docs/play-publication.md`, ensure the permission rationale lists `POST_NOTIFICATIONS` and `FOREGROUND_SERVICE_CONNECTED_DEVICE`.

- [ ] **Step 8: Verify**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.permissions.RecordingPermissionModelTest'
sh gradlew :app:compileDebugKotlin
```

Expected: targeted test passes on desktop/CI; Kotlin compile passes in Termux.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/kotlin/org/rtkcollector/app/permissions/RecordingPermissionModel.kt app/src/test/kotlin/org/rtkcollector/app/permissions/RecordingPermissionModelTest.kt app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt docs/android-background-operation.md docs/play-publication.md
git commit -m "fix: add recording permission and foreground service readiness"
```

---

### Task 4: Safe Settings Backup Sharing And Cache Cleanup

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/share/SettingsBackupSharePolicy.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/share/SettingsBackupSharePolicyTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/res/xml/file_paths.xml`
- Modify: `docs/user-workflows.md`
- Modify: `PRIVACY.md`

- [ ] **Step 1: Write failing tests for backup share policy**

Create `app/src/test/kotlin/org/rtkcollector/app/share/SettingsBackupSharePolicyTest.kt`:

```kotlin
package org.rtkcollector.app.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsBackupSharePolicyTest {
    @Test
    fun plaintextBackupFilenameIsClearlyMarked() {
        val name = settingsBackupFileName(epochMillis = 1234L, includesPlaintextPasswords = true)

        assertEquals("rtkcollector-settings-plaintext-passwords-1234.json", name)
    }

    @Test
    fun redactedBackupFilenameDoesNotMentionPasswords() {
        val name = settingsBackupFileName(epochMillis = 1234L, includesPlaintextPasswords = false)

        assertEquals("rtkcollector-settings-1234.json", name)
    }

    @Test
    fun cleanupOnlyTargetsSettingsBackupJsonFiles() {
        assertTrue(isSettingsBackupCacheFile("rtkcollector-settings-1234.json"))
        assertTrue(isSettingsBackupCacheFile("rtkcollector-settings-plaintext-passwords-1234.json"))
        assertFalse(isSettingsBackupCacheFile("session.zip"))
        assertFalse(isSettingsBackupCacheFile("../rtkcollector-settings-1234.json"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.share.SettingsBackupSharePolicyTest'
```

Expected on desktop/CI: FAIL because `SettingsBackupSharePolicy.kt` does not exist.

- [ ] **Step 3: Add backup share policy**

Create `app/src/main/kotlin/org/rtkcollector/app/share/SettingsBackupSharePolicy.kt`:

```kotlin
package org.rtkcollector.app.share

private val safeSettingsBackupRegex =
    Regex("""rtkcollector-settings(?:-plaintext-passwords)?-\d+\.json""")

fun settingsBackupFileName(epochMillis: Long, includesPlaintextPasswords: Boolean): String {
    require(epochMillis >= 0) { "Settings backup timestamp must not be negative." }
    val marker = if (includesPlaintextPasswords) "-plaintext-passwords" else ""
    return "rtkcollector-settings$marker-$epochMillis.json"
}

fun isSettingsBackupCacheFile(name: String): Boolean =
    safeSettingsBackupRegex.matches(name)
```

- [ ] **Step 4: Use marked filenames and cleanup in `MainActivity.kt`**

Add import:

```kotlin
import android.os.Handler
import android.os.Looper
import org.rtkcollector.app.share.isSettingsBackupCacheFile
import org.rtkcollector.app.share.settingsBackupFileName
```

Replace the file creation block in `shareSettingsBackup`:

```kotlin
val directory = context.cacheDir.resolve("settings-backups").also { it.mkdirs() }
val file = directory.resolve(settingsBackupFileName(System.currentTimeMillis(), includePlaintextPasswords))
file.writeText(backup.toJson().toString(2), Charsets.UTF_8)
```

After `context.startActivity(...)`, schedule cleanup:

```kotlin
scheduleSettingsBackupCacheCleanup(directory, file)
```

Add this function near `shareSettingsBackup`:

```kotlin
private fun scheduleSettingsBackupCacheCleanup(directory: File, justShared: File) {
    Handler(Looper.getMainLooper()).postDelayed(
        {
            directory.listFiles()
                ?.filter { it.isFile && isSettingsBackupCacheFile(it.name) }
                ?.forEach { file ->
                    if (file == justShared || file.lastModified() < System.currentTimeMillis() - SETTINGS_BACKUP_CACHE_TTL_MILLIS) {
                        runCatching { file.delete() }
                    }
                }
        },
        SETTINGS_BACKUP_SHARE_CLEANUP_DELAY_MILLIS,
    )
}

private const val SETTINGS_BACKUP_SHARE_CLEANUP_DELAY_MILLIS = 5 * 60 * 1000L
private const val SETTINGS_BACKUP_CACHE_TTL_MILLIS = 30 * 60 * 1000L
```

Ensure `java.io.File` is imported.

- [ ] **Step 5: Confirm FileProvider path remains scoped**

Verify `app/src/main/res/xml/file_paths.xml` contains:

```xml
<cache-path
    name="settings_backups"
    path="settings-backups/" />
```

Do not broaden it to the whole cache directory.

- [ ] **Step 6: Update docs**

In `PRIVACY.md` and `docs/user-workflows.md`, state:

```markdown
Settings backup share files are temporary cache artifacts. RtkCollector marks
plaintext-password backups in the filename and schedules best-effort cache
cleanup after launching Android's share sheet.
```

- [ ] **Step 7: Verify**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.share.SettingsBackupSharePolicyTest'
sh gradlew :app:compileDebugKotlin
```

Expected: tests pass on desktop/CI; Kotlin compile passes in Termux.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/share/SettingsBackupSharePolicy.kt app/src/test/kotlin/org/rtkcollector/app/share/SettingsBackupSharePolicyTest.kt app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/main/res/xml/file_paths.xml PRIVACY.md docs/user-workflows.md
git commit -m "fix: clean temporary settings backup shares"
```

---

### Task 5: NTRIP Transport Disclosure And Source-Upload Regression Guard

**Files:**
- Modify: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripClient.kt`
- Modify: `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripClientTest.kt`
- Modify: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/NtripCasterUploadClient.kt`
- Modify: `core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripCasterUploadClientTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt`
- Modify: `docs/ntrip-and-corrections.md`
- Modify: `docs/play-publication.md`

- [ ] **Step 1: Audit current NTRIP transport and source-upload syntax**

Run:

```bash
rg -n "Socket\\(|SSLSocket|NtripTransportSecurity|SOURCE |POST /|GET /|Authorization|Transfer-Encoding" core/correction/src/main/kotlin core/correction/src/test/kotlin docs/ntrip-and-corrections.md
```

Expected:

- correction download uses NTRIP client `GET` requests over ordinary Java
  sockets unless a later task explicitly adds TLS;
- classic source upload uses `SOURCE <password> /<mountpoint>` without `GET`,
  `POST`, `Authorization` or an `HTTP/1.x` suffix;
- NTRIP v2 source upload uses HTTP `POST` with chunked transfer;
- no source-upload test has been weakened.

- [ ] **Step 2: Keep source-upload regression tests in scope**

If the current `NtripCasterUploadClientTest` does not already assert these
conditions, add or preserve tests equivalent to:

```kotlin
@Test
fun v1SourceUploadRequestDoesNotContainHttpOrAuthorizationSyntax() {
    val request = NtripCasterUploadRequest(
        host = "caster.example",
        port = 2101,
        mountpoint = "UM980BASE",
        credentials = NtripCredentials("base01", "pass123"),
        protocolVersion = NtripProtocolVersion.NTRIP_V1,
    )

    val rendered = request.render()

    assertTrue(rendered.startsWith("SOURCE pass123 /UM980BASE\r\n"))
    assertFalse(rendered.contains("GET "))
    assertFalse(rendered.contains("POST "))
    assertFalse(rendered.contains("HTTP/"))
    assertFalse(rendered.contains("Authorization:", ignoreCase = true))
}

@Test
fun v2SourceUploadRequestUsesChunkedPostAndBasicAuth() {
    val request = NtripCasterUploadRequest(
        host = "caster.example",
        port = 2101,
        mountpoint = "UM980BASE",
        credentials = NtripCredentials("base01", "pass123"),
        protocolVersion = NtripProtocolVersion.NTRIP_V2,
    )

    val rendered = request.render()

    assertTrue(rendered.startsWith("POST /UM980BASE HTTP/1.1\r\n"))
    assertTrue(rendered.contains("Ntrip-Version: Ntrip/2.0\r\n"))
    assertTrue(rendered.contains("Transfer-Encoding: chunked\r\n"))
    assertTrue(rendered.contains("Authorization: Basic "))
}
```

- [ ] **Step 3: Run source-upload and correction-client tests before edits**

Run:

```bash
./gradlew :core:correction:test --tests 'org.rtkcollector.core.correction.NtripClientTest'
./gradlew :core:correction:test --tests 'org.rtkcollector.core.correction.NtripCasterUploadClientTest'
```

Expected on desktop/CI: both pass before any disclosure edits. In Termux, if
Gradle unit tests are not reliable, inspect the tests and continue with
`sh gradlew :app:compileDebugKotlin` during final verification.

- [ ] **Step 4: Add cleartext transport disclosure to user-facing docs**

In `docs/ntrip-and-corrections.md`, document:

```markdown
NTRIP correction download and NTRIP source upload may use ordinary cleartext TCP
depending on the selected caster endpoint. In cleartext mode, NTRIP credentials,
optional GGA upload and source-upload authentication are not protected by TLS.

RtkCollector must not claim universal encryption in transit for Google Play
while cleartext NTRIP profiles are supported. A future TLS transport option must
be implemented and tested before the Play Data safety answer can distinguish TLS
profiles as encrypted in transit.
```

In `docs/play-publication.md`, update "Encryption in transit" with the same rule.

- [ ] **Step 5: Add a compact profile-editor warning if absent**

If the NTRIP correction or source-upload profile editor does not already warn
about cleartext transport, add a small non-blocking warning near host/credential
fields:

```kotlin
Text(
    "NTRIP may use cleartext TCP; credentials and GGA/source data are not universally encrypted.",
    color = MaterialTheme.colorScheme.error,
    style = MaterialTheme.typography.bodySmall,
)
```

This warning is advisory. It must not delete saved usernames, passwords,
mountpoints or source-upload protocol settings. Do not add a fake TLS selector
unless TLS socket support is implemented in the same task.

- [ ] **Step 6: Preserve Play Data safety wording**

In `docs/play-publication.md`, ensure the Data safety draft says:

```markdown
Encryption in transit: do not claim universal encryption while cleartext NTRIP
TCP is supported. If a later release adds TLS profiles, claim encrypted transit
only for data sent through selected TLS-capable profiles.
```

- [ ] **Step 7: Verify**

Run:

```bash
./gradlew :core:correction:test --tests 'org.rtkcollector.core.correction.NtripClientTest'
./gradlew :core:correction:test --tests 'org.rtkcollector.core.correction.NtripCasterUploadClientTest'
sh gradlew :app:compileDebugKotlin
```

Expected: correction tests pass on desktop/CI; Kotlin compile passes in Termux.

- [ ] **Step 8: Commit**

```bash
git add core/correction/src/test/kotlin/org/rtkcollector/core/correction/NtripCasterUploadClientTest.kt app/src/main/kotlin/org/rtkcollector/app/ui/profiles/ProfileScreens.kt docs/ntrip-and-corrections.md docs/play-publication.md
git commit -m "docs: disclose NTRIP cleartext transport"
```

TLS support is a separate feature. If a later release should support TLS NTRIP
profiles, create a fresh plan that adds a real transport-security model,
settings persistence, socket connector support and UI selection. Do not use a
TLS label in the UI until the runtime actually opens TLS sockets.

---

### Task 6: Session Format Documentation Consistency

**Files:**
- Modify: `docs/session-format.md`
- Modify: `core/session/src/test/kotlin/org/rtkcollector/core/session/SessionWritersTest.kt`

- [ ] **Step 1: Add a test that protects V1 redaction and current fields**

Add to `SessionWritersTest.kt`:

```kotlin
@Test
fun exportedSessionMetadataContainsCurrentV1FieldsAndNoPassword() {
    val json = exportSessionMetadata(
        SessionMetadata(
            appVersion = "0.1.0",
            androidDeviceModel = "test-device",
            androidVersion = "36",
            receiverDriverId = "unicore-n4",
            usbVid = 1027,
            usbPid = 24597,
            baudRate = 230400,
            mode = SessionMode.ROVER,
            startedAt = "2026-06-14T00:00:00Z",
            stoppedAt = null,
            sessionUuid = "session-1",
            linkedBaseSessionUuid = null,
            workflowId = "rover-ntrip",
            workflowName = "Rover with NTRIP",
            receiverRole = "ROVER",
            um980ProfileId = "um980-binary",
            commandProfileId = "cmd-1",
            usbBaudProfileId = "usb-1",
            ntripCasterProfileId = "caster-1",
            ntripMountpointProfileId = "mount-1",
            recordingPolicyId = "recording-1",
            storageProfileId = "storage-1",
            storageKind = "APP_PRIVATE",
            coordinateSource = "receiver",
            validationSummary = "valid",
            expectedArtifacts = listOf("receiver-rx.raw", "correction-input.raw"),
            ntrip = NtripSessionMetadata(
                casterHost = "caster.example",
                casterPort = 2101,
                mountpoint = "MOUNT",
                usernamePresent = true,
                ggaUploadEnabled = false,
                secretRef = "ntrip:caster.example:caster:user",
                protocol = "NTRIP_V2_PREFERRED_WITH_COMPATIBILITY",
                finalStatus = null,
            ),
        ),
    )

    assertTrue(json.contains("\"workflowId\":\"rover-ntrip\""))
    assertTrue(json.contains("\"expectedArtifacts\":[\"receiver-rx.raw\",\"correction-input.raw\"]"))
    assertTrue(json.contains("\"secretRef\":\"ntrip:caster.example:caster:user\""))
    assertFalse(json.contains("password", ignoreCase = true))
}
```

Adjust constructor fields to match the current `SessionMetadata` and `NtripSessionMetadata` declarations exactly when implementing.

- [ ] **Step 2: Run test**

Run:

```bash
./gradlew :core:session:test --tests 'org.rtkcollector.core.session.SessionWritersTest'
```

Expected: PASS after constructor fields are aligned to current code.

- [ ] **Step 3: Rewrite `docs/session-format.md` V1 section**

Replace the "Required fields" list with:

```markdown
## `session.json`

### Current Experimental V1 Fields

Current V1 writes:

- `appVersion`
- `androidDeviceModel`
- `androidVersion`
- `receiverDriverId`
- `usbVid`
- `usbPid`
- `baudRate`
- `mode`
- `startedAt`
- `stoppedAt`
- `sessionUuid`
- `linkedBaseSessionUuid`
- `workflowId`
- `workflowName`
- `receiverRole`
- `um980ProfileId`
- `commandProfileId`
- `usbBaudProfileId`
- `ntripCasterProfileId`
- `ntripMountpointProfileId`
- `recordingPolicyId`
- `storageProfileId`
- `storageKind`
- `coordinateSource`
- `validationSummary`
- `expectedArtifacts`
- `ntrip`, when NTRIP was configured

The `ntrip` object stores redacted metadata only: caster host, caster port,
mountpoint, username presence, GGA upload status, secret reference, protocol and
final status. It must not contain passwords, tokens or raw credentials.

### Planned Workflow Metadata

The workflow model also defines fields that should be added when the session
metadata writer is upgraded:

- `receiverIdentification`
- `workflowSpecVersion`
- `correctionSource`
- `correctionTargets`
- `solutionEngines`
- `baseContext`
- `basePositionCandidateGeneration`
- `recordingExpectations`
- `qualityMonitoringExpectations`
- `workflowValidationAtStart`
- `serialParameters`
- `antenna`

Until those fields are emitted by the app, downstream tools must treat them as
planned metadata and must not require them for V1 session parsing.
```

- [ ] **Step 4: Verify docs and test**

Run:

```bash
./gradlew :core:session:test --tests 'org.rtkcollector.core.session.SessionWritersTest'
rg -n "Current Experimental V1 Fields|Planned Workflow Metadata|must not contain passwords" docs/session-format.md
```

Expected: session tests pass and docs contain the split sections.

- [ ] **Step 5: Commit**

```bash
git add docs/session-format.md core/session/src/test/kotlin/org/rtkcollector/core/session/SessionWritersTest.kt
git commit -m "docs: align session format with V1 metadata"
```

---

### Task 7: Harden JSON Import Intent Surface

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/AndroidManifestJsonImportTest.kt`
- Modify: `docs/user-workflows.md`

- [ ] **Step 1: Write failing manifest tests**

In `AndroidManifestJsonImportTest.kt`, add assertions:

```kotlin
@Test
fun settingsImportViewIntentDoesNotUseBrowsableOrFileScheme() {
    val manifest = java.io.File("src/main/AndroidManifest.xml").readText()

    val importFilter = manifest.substringAfter("<action android:name=\"android.intent.action.VIEW\" />")
        .substringBefore("</intent-filter>")

    assertFalse(importFilter.contains("android.intent.category.BROWSABLE"))
    assertFalse(importFilter.contains("android:scheme=\"file\""))
    assertTrue(importFilter.contains("android:scheme=\"content\""))
    assertTrue(importFilter.contains("android:mimeType=\"application/json\""))
    assertTrue(importFilter.contains("android:mimeType=\"text/json\""))
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.AndroidManifestJsonImportTest'
```

Expected on desktop/CI: FAIL because the current manifest contains `BROWSABLE` and `file`.

- [ ] **Step 3: Narrow manifest import filter**

In `AndroidManifest.xml`, replace the `VIEW` import filter with:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />

    <category android:name="android.intent.category.DEFAULT" />

    <data android:scheme="content" />
    <data android:mimeType="application/json" />
    <data android:mimeType="text/json" />
</intent-filter>
```

Keep the `SEND` filter for Android share sheet import:

```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />

    <category android:name="android.intent.category.DEFAULT" />

    <data android:mimeType="application/json" />
    <data android:mimeType="text/json" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

- [ ] **Step 4: Update import docs**

In `docs/user-workflows.md`, update import documentation:

```markdown
Settings import is available through Android content/share intents. The app
accepts JSON content, validates structure and references, shows a preview, and
requires explicit confirmation before replacing profiles or importing plaintext
passwords. The app does not automatically import files opened from external
apps. Import is blocked while recording is active.
```

- [ ] **Step 5: Verify**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.AndroidManifestJsonImportTest'
sh gradlew :app:compileDebugKotlin
```

Expected: test passes on desktop/CI; Kotlin compile passes in Termux.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/test/kotlin/org/rtkcollector/app/ui/AndroidManifestJsonImportTest.kt docs/user-workflows.md
git commit -m "fix: narrow settings import intent surface"
```

---

### Task 8: README Capability Wording And Play Release Checklist

**Files:**
- Modify: `README.md`
- Modify: `docs/play-publication.md`
- Modify: `docs/architecture.md`

- [ ] **Step 1: Correct current transport wording**

In `README.md`, replace wording equivalent to "USB, Bluetooth, TCP or file replay transport receives bytes" with:

```markdown
Current Android V1 focuses on USB receiver capture plus NTRIP client correction
intake/routing. The architecture keeps Bluetooth, TCP and file replay as
receiver-agnostic transport boundaries, but those transports are not publication
claims for the current Android app unless explicitly implemented and tested.
```

- [ ] **Step 2: Add release checklist link**

In `README.md`, add:

```markdown
Before any Google Play upload, use [docs/play-publication.md](docs/play-publication.md)
as the release checklist for signing, privacy, Data safety, foreground service
declarations, licence inventory and manual field validation.
```

- [ ] **Step 3: Add architecture note**

In `docs/architecture.md`, add:

```markdown
Publication documentation must describe the shipped implementation, not only the
long-term transport architecture. If Bluetooth, TCP or replay transports are
documented as architectural seams but not implemented in the Android app, user
and Play-facing documents must label them as future or non-current capabilities.
```

- [ ] **Step 4: Verify**

Run:

```bash
rg -n "Current Android V1 focuses on USB|Google Play upload|Publication documentation" README.md docs/architecture.md
```

Expected: all three phrases are found.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/play-publication.md docs/architecture.md
git commit -m "docs: clarify current Android release capabilities"
```

---

### Task 9: End-To-End Publication Verification

**Files:**
- Inspect all modified files.
- No new files unless a verification note is added to `docs/play-publication.md`.

- [ ] **Step 1: Check for accidental GIS/map dependencies**

Run:

```bash
rg -n "maps|mapbox|osmdroid|shapefile|shp|gis|geojson" app build.gradle.kts app/build.gradle.kts core receiver docs README.md
```

Expected: hits only in non-goal documentation or explicit "no maps/GIS/shapefile" statements. No Gradle dependency should contain map/GIS/shapefile libraries.

- [ ] **Step 2: Check for secret leakage in session docs and code**

Run:

```bash
rg -n "password|token|secret|Authorization" core/session app/src/main/kotlin docs/session-format.md docs/ntrip-and-corrections.md PRIVACY.md
```

Expected:

- `Authorization` appears only in NTRIP request code/tests.
- Session metadata code writes `secretRef`, `usernamePresent` or redacted metadata, not plaintext password.
- Docs explicitly state session metadata must not contain passwords/tokens.

- [ ] **Step 3: Run focused JVM tests**

Run on desktop/CI when possible:

```bash
./gradlew :core:correction:test
./gradlew :core:session:test
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.permissions.RecordingPermissionModelTest'
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.share.SettingsBackupSharePolicyTest'
./gradlew :app:testDebugUnitTest --tests 'org.rtkcollector.app.ui.AndroidManifestJsonImportTest'
```

Expected: all pass.

- [ ] **Step 4: Run Termux-safe local compile**

Run in this Termux/aarch64 checkout:

```bash
git diff --check
sh gradlew :app:compileDebugKotlin
sh gradlew :app:unitTestClasses :app:androidTestClasses --dry-run
```

Expected: all three commands pass. Do not keep retrying `assembleDebug` in
Termux if the known x86-64 `aapt2` resource-processing failure appears.

- [ ] **Step 5: Run release-host validation**

Run on Windows Android Studio, x86-64 Linux CI, or another host with runnable Android SDK native tools:

```bash
./gradlew clean
./gradlew test
./gradlew assembleDebug
./gradlew bundleRelease
./gradlew validateGooglePlayReleaseBundle
```

Expected: all pass after signing configuration is supplied for `bundleRelease`.
The release bundle must contain both `armeabi-v7a` and `arm64-v8a`
`librtkcollector_rtklib.so` entries.

- [ ] **Step 6: Manual device validation**

Use a signed debug or internal-test build:

```text
1. Fresh-install on Android 13+.
2. Start plain rover recording and confirm notification permission prompt appears before foreground recording.
3. Deny notification permission and confirm recording does not silently start without a foreground notification.
4. Grant notification permission and confirm recording notification shows active recording.
5. Start rover + NTRIP with a cleartext profile and confirm the UI warns about cleartext credentials.
6. Export settings without plaintext passwords and confirm filename is rtkcollector-settings-<timestamp>.json.
7. Export settings with plaintext passwords and confirm filename is rtkcollector-settings-plaintext-passwords-<timestamp>.json.
8. Wait at least six minutes and confirm temporary settings backup cache files are removed if Android permits cleanup.
9. Import a settings backup from Android share sheet and confirm preview/validation appears before import.
10. Share a session ZIP and confirm receiver RX, TX-to-receiver and correction input files remain separate.
```

- [ ] **Step 7: Final review**

Run:

```bash
git status --short
git log --oneline -8
```

Expected: only intended changes are present; commits are small and reviewable.

- [ ] **Step 8: Final commit or no-op**

If Task 9 only produced documentation evidence, commit it:

```bash
git add docs/play-publication.md
git commit -m "docs: record Play readiness validation"
```

If no files changed, do not create an empty commit.

---

## Self-Review

Spec coverage:

- Current-state validation and stale-task pruning: Task 0.
- Privacy/Data safety source: Task 1.
- Notification permission and foreground-service readiness: Task 3.
- Plaintext settings backup cleanup: Task 4.
- NTRIP cleartext disclosure, source-upload regression guard and optional TLS deferral: Task 5.
- Session metadata/doc consistency: Task 6.
- Third-party licences: Task 2.
- Import surface hardening: Task 7.
- README/release capability wording: Task 8.
- Final verification: Task 9.

Placeholder scan:

- This plan intentionally avoids placeholder instructions, "similar to" shortcuts, and unspecified edge-case instructions.
- Where implementation must adapt to exact current constructors, the step says to align with the current declarations and gives the expected behavioural assertion.

Type consistency:

- `runtimePermissionsRequiredBeforeRecording`, `batteryOptimisationWarning`, `settingsBackupFileName`, and `isSettingsBackupCacheFile` are introduced before later tasks reference them.
- `NtripTransportSecurity` is intentionally not introduced by this plan unless a separate TLS feature is explicitly approved and implemented.
- Manifest changes use Android permission names exactly as required by Android APIs.
- Documentation names match repository paths.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-14-google-play-readiness.md`. Two execution options:

**1. Subagent-Driven (recommended)** - Dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
