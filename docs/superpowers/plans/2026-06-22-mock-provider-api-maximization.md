# Mock Provider API Maximization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish every Android mock-location field that RtkCollector can populate from the selected best solution, while documenting public API limits for satellite status.

**Architecture:** Keep mock publishing advisory and recording-scoped. The raw USB capture path, session writers, NTRIP routing, RTKLIB workers and dashboard monitoring must not depend on Android mock-location success. Extend the existing `BestSolutionSnapshot -> MockLocationUpdate -> AndroidMockLocationSink` path only, guarded by the existing mock-enabled runtime tick.

**Tech Stack:** Kotlin, Android `Location` / `LocationManager.setTestProviderLocation`, Compose dashboard state, existing JUnit tests, formal specs under `docs/specification/`.

---

## File Structure

- Modify `docs/specification/android-runtime.md`
  - Add a normative requirement for using all supported public `Location` fields available from the selected solution.
- Modify `docs/specification/verification-matrix.md`
  - Add the new requirement row.
- Modify `docs/user-workflows.md`
  - Clarify which mock-provider fields are published and which satellite fields remain impossible through the public API.
- Modify `docs/superpowers/plan-status.md`
  - Add or update the mock-provider capability row if the tracker already has a suitable section.
- Modify `app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt`
  - Extend `MockLocationUpdate`.
  - Map vertical accuracy and MSL altitude from `BestSolutionSnapshot`.
  - Add compatibility satellite-count extras.
  - Apply Android API-gated `Location` setters.
- Modify `app/src/test/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisherTest.kt`
  - Add mapper tests for vertical accuracy and MSL altitude.
  - Add extras tests for satellite aliases.

Do not modify receiver capture, session writer, NTRIP, RTKLIB or parser code for this plan.

## Requirements And Limits

- `Location.altitude` remains ellipsoidal height from `BestSolutionSnapshot.ellipsoidalHeightM`.
- `Location.accuracy` remains horizontal accuracy from `BestSolutionSnapshot.horizontalAccuracyM`.
- On Android API 26+, call `Location.setVerticalAccuracyMeters(...)` when `verticalAccuracyM` is available.
- On Android API 34+, call `Location.setMslAltitudeMeters(...)` and `Location.setMslAltitudeAccuracyMeters(...)` only if the app has MSL altitude and an appropriate accuracy estimate. In the current model, MSL altitude is available but MSL altitude accuracy is not; do not invent it.
- Keep satellite counts as best-effort `Location.extras`. Add compatibility aliases:
  - `satellites`
  - `satellitesUsed`
  - `satellitesInUse`
  - `satellitesInView`
  - `satellitesVisible`
- Do not add receiver-specific per-satellite extras in this implementation. The current selected-solution model exposes aggregate counts only. Pulling per-satellite telemetry directly into the mock publisher would couple receiver-specific high-rate monitoring to the mock path and risks avoidable overhead.
- A future RtkCollector-specific rich-satellite extras extension may be added only after a bounded, advisory internal satellite snapshot model exists. That future model must remain off the raw capture path and must be documented as non-standard metadata that ordinary third-party apps may ignore.
- Do not promise or attempt full synthetic `GnssStatus` injection. Ordinary apps publishing through `LocationManager.setTestProviderLocation` provide `Location` updates, not full per-satellite sky-position/status streams.
- Do not add speed, bearing, speed accuracy or bearing accuracy until `BestSolutionSnapshot` carries velocity/bearing from receiver telemetry or RTKLIB output.

---

### Task 1: Add Formal Mock-Provider Capability Requirement

**Files:**
- Modify: `docs/specification/android-runtime.md`
- Modify: `docs/specification/verification-matrix.md`

- [ ] **Step 1: Add requirement text**

Add this section after `ANDROID-MOCK-001`:

```markdown
### ANDROID-MOCK-004: Mock Location Uses Maximum Safe Public Location Fields

Status: Normative

When Android mock-location publishing is enabled, RtkCollector MUST populate
every public `Location` field that is supported on the running Android version
and available from the selected best solution. At minimum this includes
latitude, longitude, ellipsoidal altitude, timestamp, elapsed realtime,
horizontal accuracy, API-gated vertical accuracy and API-gated MSL altitude.
RtkCollector MUST NOT invent unavailable speed, bearing or accuracy values.
Satellite counts MAY be attached as best-effort extras under common aliases, but
RtkCollector MUST NOT claim that these extras are equivalent to Android
`GnssStatus`.
Failure to apply an optional mock-location field MUST NOT stop receiver
recording.

Verification:
- Automated: mock-location mapper tests.
- Review: Android sink uses API guards for version-specific setters.
- Manual: compare mock altitude, vertical accuracy and MSL altitude behaviour
  on a device where consumer apps expose these fields.
```

- [ ] **Step 2: Add verification matrix row**

Add this row near the other mock-location rows:

```markdown
| `ANDROID-MOCK-004` | Automated + manual | Mock-location mapper tests; Android sink review; device mock-provider smoke test | Needs review | Publishes all supported public `Location` fields available from the selected best solution. |
```

- [ ] **Step 3: Validate docs formatting**

Run:

```bash
git diff --check docs/specification/android-runtime.md docs/specification/verification-matrix.md
```

Expected: no output.

---

### Task 2: Add Failing Tests For Mock Update Field Mapping

**Files:**
- Modify: `app/src/test/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisherTest.kt`

- [ ] **Step 1: Add assertions to the existing publish test**

Change `publishes fresh snapshot when enabled` to assert the richer update:

```kotlin
val update = sink.locations.single()
assertEquals(50.0, update.latDeg)
assertEquals(14.0, update.lonDeg)
assertEquals(300.0, update.altitudeM)
assertEquals(250.0, update.mslAltitudeM)
assertEquals(1.2f, update.horizontalAccuracyM)
assertEquals(2.4f, update.verticalAccuracyM)
assertEquals(1_000L, update.timeMillis)
```

- [ ] **Step 2: Add null handling test**

Add:

```kotlin
@Test
fun `omits optional vertical and msl fields when unavailable`() {
    val sink = FakeMockLocationSink()
    val publisher = MockLocationPublisher(sink)
    val partial = snapshot().copy(mslAltitudeM = null, verticalAccuracyM = null)

    publisher.publish(partial, enabled = true)

    val update = sink.locations.single()
    assertNull(update.mslAltitudeM)
    assertNull(update.verticalAccuracyM)
}
```

- [ ] **Step 3: Run test to verify RED**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.mocklocation.MockLocationPublisherTest
```

Expected in a normal Android host: FAIL because `MockLocationUpdate` has no `mslAltitudeM` or `verticalAccuracyM` fields. In the current Termux environment this may stop earlier at the known x86 `aapt2` resource-tool failure; if so, record the exact blocker and continue with `:app:compileDebugKotlin` after implementation.

---

### Task 3: Extend `MockLocationUpdate` And Publisher Mapping

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt`

- [ ] **Step 1: Extend the data class**

Change `MockLocationUpdate` to:

```kotlin
data class MockLocationUpdate(
    val latDeg: Double,
    val lonDeg: Double,
    val altitudeM: Double?,
    val mslAltitudeM: Double?,
    val horizontalAccuracyM: Float?,
    val verticalAccuracyM: Float?,
    val timeMillis: Long,
    val satellitesUsed: Int?,
    val satellitesInView: Int?,
)
```

- [ ] **Step 2: Map the new fields**

In `MockLocationPublisher.publish`, change the update construction to include:

```kotlin
mslAltitudeM = current.mslAltitudeM,
verticalAccuracyM = current.verticalAccuracyM?.toFloat(),
```

Keep:

```kotlin
altitudeM = current.ellipsoidalHeightM
```

- [ ] **Step 3: Run compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: PASS.

---

### Task 4: Add Failing Tests For Satellite Extra Aliases

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisherTest.kt`

- [ ] **Step 1: Make extras helper testable**

Rename `private fun mockLocationExtras(update: MockLocationUpdate): Bundle?` to:

```kotlin
internal fun mockLocationExtras(update: MockLocationUpdate): Bundle?
```

- [ ] **Step 2: Add failing alias test**

Add:

```kotlin
@Test
fun `mock location extras include common satellite count aliases`() {
    val extras = mockLocationExtras(
        MockLocationUpdate(
            latDeg = 50.0,
            lonDeg = 14.0,
            altitudeM = 300.0,
            mslAltitudeM = 250.0,
            horizontalAccuracyM = 1.2f,
            verticalAccuracyM = 2.4f,
            timeMillis = 1_000L,
            satellitesUsed = 12,
            satellitesInView = 18,
        ),
    )

    assertNotNull(extras)
    assertEquals(12, extras!!.getInt("satellites"))
    assertEquals(12, extras.getInt("satellitesUsed"))
    assertEquals(12, extras.getInt("satellitesInUse"))
    assertEquals(18, extras.getInt("satellitesInView"))
    assertEquals(18, extras.getInt("satellitesVisible"))
}
```

- [ ] **Step 3: Run test to verify RED**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.mocklocation.MockLocationPublisherTest
```

Expected on a normal Android host: FAIL because `satellitesInUse` and `satellitesVisible` are not populated. In Termux, the known `aapt2` blocker may prevent execution.

---

### Task 5: Populate Satellite Alias Extras

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt`

- [ ] **Step 1: Update extras helper**

Change `mockLocationExtras` to:

```kotlin
internal fun mockLocationExtras(update: MockLocationUpdate): Bundle? {
    val extras = Bundle()
    update.satellitesUsed?.let { used ->
        extras.putInt("satellites", used)
        extras.putInt("satellitesUsed", used)
        extras.putInt("satellitesInUse", used)
    }
    update.satellitesInView?.let { inView ->
        extras.putInt("satellitesInView", inView)
        extras.putInt("satellitesVisible", inView)
    }
    return if (extras.isEmpty) null else extras
}
```

- [ ] **Step 2: Run compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: PASS.

---

### Task 6: Apply Android API-Gated Optional Location Fields

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt`

- [ ] **Step 1: Import `Build`**

Add:

```kotlin
import android.os.Build
```

- [ ] **Step 2: Apply vertical accuracy on API 26+**

In `AndroidMockLocationSink.publish`, after horizontal accuracy:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    update.verticalAccuracyM?.let { verticalAccuracyMeters = it }
}
```

- [ ] **Step 3: Apply MSL altitude on API 34+**

In the same block, add:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    update.mslAltitudeM?.let { mslAltitudeMeters = it }
}
```

Do not call `mslAltitudeAccuracyMeters` until a real MSL-altitude accuracy value exists in the selected solution model.

- [ ] **Step 4: Keep ellipsoidal altitude unchanged**

Confirm this line still uses ellipsoidal altitude:

```kotlin
update.altitudeM?.let { altitude = it }
```

- [ ] **Step 5: Run compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: PASS.

---

### Task 7: Update User Documentation

**Files:**
- Modify: `docs/user-workflows.md`

- [ ] **Step 1: Update mock-location section**

Replace the current field description with wording equivalent to:

```markdown
When enabled, RtkCollector publishes latitude, longitude, ellipsoidal height,
horizontal accuracy, UTC-derived fix time and elapsed realtime to Android's mock
GPS provider. On Android versions whose public `Location` API supports them,
RtkCollector also publishes vertical accuracy and MSL altitude when these are
available from the selected best solution. Satellite counts are attached as
best-effort extras under common aliases, but Android's public mock-location API
does not allow an ordinary app to inject full GNSS satellite status or satellite
sky positions for other apps.
```

- [ ] **Step 2: Validate docs formatting**

Run:

```bash
git diff --check docs/user-workflows.md
```

Expected: no output.

---

### Task 8: Update Plan Status

**Files:**
- Modify: `docs/superpowers/plan-status.md`

- [ ] **Step 1: Find the appropriate status section**

Run:

```bash
rg -n "mock|Mock|Android mock|location" docs/superpowers/plan-status.md
```

Expected: identify the existing mock-provider or Android runtime tracking row/section. If no suitable section exists, add a short row in the closest Android runtime/dashboard section.

- [ ] **Step 2: Record implementation status**

Add or update a concise entry:

```markdown
- Mock provider maximum public `Location` fields: Implemented, not field-tested.
  Evidence: `MockLocationPublisherTest`, `:app:compileDebugKotlin`, formal
  requirement `ANDROID-MOCK-004`. Field validation still needed on Android API
  26+ for vertical accuracy and API 34+ for MSL altitude visibility in consumer
  apps.
```

Do not mark as `Done` until a real Android smoke test confirms optional fields on a device or emulator that supports them.

- [ ] **Step 3: Validate docs formatting**

Run:

```bash
git diff --check docs/superpowers/plan-status.md
```

Expected: no output.

---

### Task 9: Final Verification And Commit

**Files:**
- All files modified in Tasks 1-8.

- [ ] **Step 1: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 2: Run app Kotlin compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 3: Run mock-location tests where the host supports Android app unit tests**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.mocklocation.MockLocationPublisherTest
```

Expected on Windows/Android Studio or CI with runnable Android build tools: PASS.

Expected on this Termux/aarch64 environment unless tooling has changed: may fail at `:app:processDebugResources` because Gradle resolves an x86 Linux `aapt2`; record the blocker and do not retry repeatedly.

- [ ] **Step 4: Review architectural isolation**

Confirm by diff review:

- No changes to `receiver-rx.raw` writing.
- No changes to USB capture loops.
- No changes to NTRIP connection/reconnect logic.
- No changes to RTKLIB worker activation.
- Mock publishing still returns `DISABLED` before touching the snapshot when mock output is disabled.

- [ ] **Step 5: Commit**

Run:

```bash
git add docs/specification/android-runtime.md docs/specification/verification-matrix.md docs/user-workflows.md app/src/main/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisher.kt app/src/test/kotlin/org/rtkcollector/app/mocklocation/MockLocationPublisherTest.kt
git add docs/superpowers/plan-status.md
git commit -m "feat: maximize mock location fields"
```

Commit body:

```text
Publish vertical accuracy and MSL altitude through Android mock-location APIs
when supported by the running Android version and available from the selected
best solution. Keep ellipsoidal height as Location.altitude and keep satellite
status as documented best-effort extras only.

The change remains isolated to the mock-provider path and does not touch raw
receiver recording, NTRIP routing, RTKLIB activation or session writer paths.
```

---

## Self-Review Checklist

- Spec coverage: Tasks 1 and 7 cover formal and user documentation; Tasks 2-6 cover code and tests; Task 8 covers verification.
- Placeholder scan: no `TBD`, `TODO`, or unspecified "add tests" steps.
- Type consistency: `MockLocationUpdate.mslAltitudeM` and `verticalAccuracyM` are introduced before later tests and Android sink use them.
- Performance: all work remains behind existing mock-enabled publishing; disabled mock output remains a fast return.
- Android API limits: full `GnssStatus` and satellite sky-position injection remain explicitly out of scope because the public mock-location API does not provide that capability to ordinary apps.
