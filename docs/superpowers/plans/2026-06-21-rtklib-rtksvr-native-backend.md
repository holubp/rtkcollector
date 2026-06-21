# RTKLIB Server Native Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current simplified RTKLIB JNI positioning loop with an RTKLIB-EX `rtksvr_t`-based backend, using in-memory streams fed by RtkCollector so RTKLIB handles rover/base observation synchronization while raw recording remains authoritative.

**Architecture:** RtkCollector continues to own USB, NTRIP, foreground-service lifecycle, raw session writers and bounded advisory queues. The RTKLIB worker remains lazy and starts only for workflows whose validated `RtklibConfig` enables RTKLIB. The native backend starts RTKLIB's real-time server with `STR_MEMBUF` rover and base/correction streams, writes already-recorded rover/correction bytes into those streams from the advisory worker, drains RTKLIB solution outputs from memory buffers, and reports snapshots without ever touching `receiver-rx.raw`, `correction-input.raw` or the UI thread.

**Tech Stack:** Kotlin/JVM unit tests, Android foreground service, JNI/C++, pinned RTKLIB-EX C sources, RTKLIB `rtksvr_t`, `STR_MEMBUF`, Gradle Kotlin compilation, Python source-structure tests for native build glue.

---

## Evidence And Design Decisions

The field session `samples/debug/session-2026-06-21T17-08-13.445776Z-d83546aa-beb3-4ff1-9cc6-c21142b9dcdc-1782062170308.zip` showed:

- `receiver-rx.raw`: healthy u-blox stream with RAWX/SFRBX/NAV-PVT.
- `correction-input.rtcm3`: valid RTCM3 MSM and base metadata frames.
- `rtklib-solution.pos`: every solution row was RTKLIB quality `4` with `ns=3`.
- `rtklib-status.jsonl`: empty.

Root cause in current native code:

- `app/src/main/cpp/rtklib_bridge.cpp` stores only one `latest_base_obs` vector.
- `store_base_observations()` overwrites the vector on each decoded RTCM observation message.
- `solve_if_possible()` calls `rtkpos()` directly from the rover decoder path using the current single base-observation snapshot.
- RTCM MSM streams arrive as multiple constellation/message frames, so this does not reproduce RTKLIB's real-time server synchronization.

RTKGPS+ reference:

- `RtkServer.java` calls `_rtksvrstart(...)`.
- `jni/rtkserver.c` passes streams, formats, commands, NMEA request policy, `prcopt_t` and `solopt_t` into `rtksvrstart(...)`.
- RTKLIB `rtksvr.c` decodes rover/base streams, stores observation buffers through `update_obs()`, combines rover and base observations in the server loop, and then calls `rtkpos()`.

Chosen design:

- Use RTKLIB `rtksvr_t` in the RtkCollector native backend.
- Do not let RTKLIB open USB, NTRIP or Android storage directly.
- Use `STR_MEMBUF` for RTKLIB rover input, base/correction input and solution outputs.
- Keep RtkCollector's existing bounded `RtklibWorker` queues as the first isolation layer.
- Treat RTKLIB memory-buffer overflow as advisory RTKLIB lag/failure, never as a raw-recording failure.
- Set RTKLIB frequency count explicitly. u-blox M8T/M8P are single-frequency receivers and must not be forced through `nf=2` by RTKLIB defaults.

## File Structure

- Modify `docs/specification/workflows.md`
  - Clarify that RTKLIB live processing must use RTKLIB server-style stream synchronization or equivalent tested semantics.
- Modify `docs/specification/receiver-behaviour.md`
  - Clarify u-blox RTKLIB direct route, single-frequency profile handling and validation.
- Modify `docs/specification/verification-matrix.md`
  - Add verification rows for RTKLIB server backend, frequency-count configuration and sample replay/field validation.
- Modify `docs/superpowers/plan-status.md`
  - Mark in-phone RTKLIB as still `In progress` and note this server-backend plan as the active fix for DGPS-only M8T output.
- Create `tools/test_rtklib_native_server_backend.py`
  - Source-structure regression test proving the native bridge uses `rtksvr_t`, includes `rtksvr.c`, uses memory-buffer streams, and no longer has the simplified `latest_base_obs` direct-`rtkpos()` path.
- Modify `tools/test_rtklib_native_build_files.py`
  - Require `rtksvr.c` in the native build file.
- Modify `core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibConfig.kt`
  - Add RTKLIB runtime parameters needed by the native server backend.
- Modify `core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibNativeBridge.kt`
  - Pass the new runtime parameters to JNI.
- Modify `core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibConfigTest.kt`
  - Add validation tests for frequency count and server timing/buffer configuration.
- Modify `core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibWorkerTest.kt`
  - Update fake native API signatures and add tests that RTKLIB remains lazy and advisory.
- Modify `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
  - Add `frequencyCount`, `serverCycleMillis`, `serverBufferBytes`, `solutionBufferBytes` fields to `RtklibProfile`.
- Modify `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
  - Set default u-blox RTKLIB profile frequency count to `1`.
- Modify `app/src/test/kotlin/org/rtkcollector/app/profile/RecordingPolicyProfileTest.kt`
  - Add JSON round-trip tests for the new RTKLIB profile fields.
- Modify `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt`
  - Prove active config passes frequency/server fields into `RtklibConfig`.
- Modify `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt`
  - Resolve RTKLIB runtime parameters from the selected profile.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  - Expose compact RTKLIB profile editing fields for frequency count and server cycle/buffer values.
- Modify `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
  - Include runtime parameters in the service-created `RtklibConfig`.
  - Keep worker creation gated by `EXTRA_RTKLIB_ENABLED`.
  - Append `rtklib-status.jsonl` snapshots at bounded cadence when RTKLIB is enabled.
- Modify `app/src/main/cpp/CMakeLists.txt`
  - Include RTKLIB `rtksvr.c`.
- Replace internals of `app/src/main/cpp/rtklib_bridge.cpp`
  - Keep JNI function names stable where practical.
  - Replace direct raw/RTCM decoder state and direct `rtkpos()` call with `rtksvr_t` and RTKLIB memory streams.

## Task 1: Specification And Status Alignment

**Files:**
- Modify: `docs/specification/workflows.md`
- Modify: `docs/specification/receiver-behaviour.md`
- Modify: `docs/specification/verification-matrix.md`
- Modify: `docs/superpowers/plan-status.md`

- [ ] **Step 1: Update RTKLIB workflow requirement**

In `docs/specification/workflows.md`, extend `WF-RTKLIB-001` after the route expectations with:

```markdown
Live RTKLIB real-time processing MUST preserve RTKLIB's rover/base observation
synchronisation semantics. The preferred implementation is RTKLIB's `rtksvr_t`
server using app-fed in-memory streams. A custom native loop MAY be used only
if automated replay tests prove it combines rover observations, base RTCM
observations, ephemeris, base position and correction age equivalently to
RTKLIB's server loop. A native loop MUST NOT solve each rover epoch against
only the most recently decoded single RTCM observation message.

RTKLIB processing MUST remain lazy and advisory. If RTKLIB is disabled by the
validated workflow/profile, no RTKLIB native library may be loaded, no RTKLIB
worker may be started and no RTKLIB memory buffers may be allocated.
```

- [ ] **Step 2: Update u-blox RTKLIB receiver requirement**

In `docs/specification/receiver-behaviour.md`, extend `RX-UBLOX-RTKLIB-001` with:

```markdown
u-blox M8T and M8P profiles MUST be treated as single-frequency RTKLIB inputs
unless a future receiver-specific profile declares otherwise. RTKLIB runtime
configuration MUST pass this frequency count explicitly instead of relying on
RTKLIB's default dual-frequency processing option.
```

- [ ] **Step 3: Update verification matrix**

In `docs/specification/verification-matrix.md`, update the RTKLIB rows so they include:

```markdown
RTKLIB server-backend source-structure tests; RTKLIB profile frequency-count
tests; M8T field replay using `samples/debug/session-2026-06-21T17-08-13...zip`;
Android Studio/CI native `.so` build.
```

Keep status as `In progress` until the native `.so` is built on a supported host and M8T hardware/replay validation shows RTKLIB solutions use more than the previous `ns=3` DGPS-only output.

- [ ] **Step 4: Update Superpowers plan status**

In `docs/superpowers/plan-status.md`, update the `In-phone RTKLIB real-time solution` note by appending:

```markdown
Current M8T field evidence showed the first JNI bridge solved against a
single latest RTCM observation snapshot and produced DGPS-only `ns=3`
solutions. The active follow-up is `2026-06-21-rtklib-rtksvr-native-backend.md`,
which replaces that bridge with RTKLIB `rtksvr_t` memory-buffer stream
processing.
```

- [ ] **Step 5: Check documentation diff**

Run:

```bash
git diff -- docs/specification/workflows.md docs/specification/receiver-behaviour.md docs/specification/verification-matrix.md docs/superpowers/plan-status.md
```

Expected: only RTKLIB-specific wording changes, no changes to V1 raw recording rules.

- [ ] **Step 6: Commit checkpoint**

Run:

```bash
git add docs/specification/workflows.md docs/specification/receiver-behaviour.md docs/specification/verification-matrix.md docs/superpowers/plan-status.md
git commit -m "docs: specify rtklib server backend requirements"
```

## Task 2: Native Backend Source-Structure Regression Tests

**Files:**
- Create: `tools/test_rtklib_native_server_backend.py`
- Modify: `tools/test_rtklib_native_build_files.py`

- [ ] **Step 1: Write failing architecture regression test**

Create `tools/test_rtklib_native_server_backend.py` with:

```python
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "app/src/main/cpp/rtklib_bridge.cpp"
CMAKE = ROOT / "app/src/main/cpp/CMakeLists.txt"


def test_native_bridge_uses_rtklib_server_memory_streams():
    bridge = BRIDGE.read_text(encoding="utf-8")
    assert "rtksvr_t" in bridge
    assert "rtksvrinit(" in bridge
    assert "rtksvrstart(" in bridge
    assert "rtksvrstop(" in bridge
    assert "rtksvrfree(" in bridge
    assert "STR_MEMBUF" in bridge
    assert "strwrite(" in bridge
    assert "strread(" in bridge


def test_native_bridge_does_not_use_single_latest_base_observation_snapshot():
    bridge = BRIDGE.read_text(encoding="utf-8")
    assert "latest_base_obs" not in bridge
    assert "store_base_observations" not in bridge
    assert "solve_if_possible" not in bridge
    assert "rtkpos(&engine->rtk" not in bridge


def test_native_build_includes_rtklib_server_source():
    cmake = CMAKE.read_text(encoding="utf-8")
    assert '${RTKLIB_SRC}/rtksvr.c' in cmake
```

- [ ] **Step 2: Extend existing native build file test**

In `tools/test_rtklib_native_build_files.py`, add:

```python
    assert "${RTKLIB_SRC}/rtksvr.c" in cmake
```

inside the existing CMake source assertions.

- [ ] **Step 3: Run tests and verify they fail before implementation**

Run:

```bash
python3 tools/test_rtklib_native_server_backend.py
python3 tools/test_rtklib_native_build_files.py
```

Expected before implementation:

- `tools/test_rtklib_native_server_backend.py` fails because `rtklib_bridge.cpp` still contains `latest_base_obs` and does not use `rtksvr_t`.
- `tools/test_rtklib_native_build_files.py` fails if `rtksvr.c` is not yet in `CMakeLists.txt`.

If direct `python3` execution does not run test functions because no pytest harness is present, run:

```bash
python3 -m pytest tools/test_rtklib_native_server_backend.py tools/test_rtklib_native_build_files.py
```

Expected: the same failing assertions.

- [ ] **Step 4: Commit failing tests only**

Run:

```bash
git add tools/test_rtklib_native_server_backend.py tools/test_rtklib_native_build_files.py
git commit -m "test: cover rtklib native server backend architecture"
```

## Task 3: RTKLIB Profile And Config Runtime Parameters

**Files:**
- Modify: `core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibConfig.kt`
- Modify: `core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibConfigTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/profile/RecordingPolicyProfileTest.kt`

- [ ] **Step 1: Write failing RTKLIB config validation tests**

In `core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibConfigTest.kt`, add:

```kotlin
@Test
fun `frequency count must be supported by RTKLIB live processing`() {
    val config = validConfig().copy(frequencyCount = 0)

    val result = config.validate()

    assertTrue(result.errors.any { it.contains("frequencyCount must be 1, 2 or 3") })
}

@Test
fun `server cycle and buffer sizes must be positive`() {
    val config = validConfig().copy(
        serverCycleMillis = 0,
        serverBufferBytes = 0,
        solutionBufferBytes = 0,
    )

    val result = config.validate()

    assertTrue(result.errors.any { it.contains("serverCycleMillis must be positive") })
    assertTrue(result.errors.any { it.contains("serverBufferBytes must be positive") })
    assertTrue(result.errors.any { it.contains("solutionBufferBytes must be positive") })
}
```

- [ ] **Step 2: Write failing profile JSON round-trip test**

In `app/src/test/kotlin/org/rtkcollector/app/profile/RecordingPolicyProfileTest.kt`, add:

```kotlin
@Test
fun `rtklib profile persists server runtime parameters`() {
    val profile = RtklibProfile(
        id = "rtklib-m8t",
        name = "RTKLIB M8T",
        enabled = true,
        frequencyCount = 1,
        serverCycleMillis = 50,
        serverBufferBytes = 65536,
        solutionBufferBytes = 65536,
    )

    val decoded = RtklibProfile.fromJson(profile.toJson())

    assertEquals(1, decoded.frequencyCount)
    assertEquals(50, decoded.serverCycleMillis)
    assertEquals(65536, decoded.serverBufferBytes)
    assertEquals(65536, decoded.solutionBufferBytes)
}
```

- [ ] **Step 3: Run tests and verify failure**

Run:

```bash
sh gradlew :core:rtklib:test --tests org.rtkcollector.core.rtklib.RtklibConfigTest
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.RecordingPolicyProfileTest
```

Expected before implementation:

- `RtklibConfigTest` fails to compile because fields do not exist.
- `RecordingPolicyProfileTest` fails to compile because `RtklibProfile` fields do not exist.

If `:app:testDebugUnitTest` reaches the known Termux Android resource/native-tool blocker, record that and continue after proving the compile failure with `:app:compileDebugKotlin` later.

- [ ] **Step 4: Add fields to `RtklibConfig`**

In `core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibConfig.kt`, add constructor fields:

```kotlin
val frequencyCount: Int = DEFAULT_FREQUENCY_COUNT,
val serverCycleMillis: Int = DEFAULT_SERVER_CYCLE_MILLIS,
val serverBufferBytes: Int = DEFAULT_SERVER_BUFFER_BYTES,
val solutionBufferBytes: Int = DEFAULT_SOLUTION_BUFFER_BYTES,
```

Add validation:

```kotlin
if (frequencyCount !in 1..3) errors += "frequencyCount must be 1, 2 or 3"
if (serverCycleMillis <= 0) errors += "serverCycleMillis must be positive"
if (serverBufferBytes <= 0) errors += "serverBufferBytes must be positive"
if (solutionBufferBytes <= 0) errors += "solutionBufferBytes must be positive"
```

Add companion defaults:

```kotlin
const val DEFAULT_FREQUENCY_COUNT: Int = 1
const val DEFAULT_SERVER_CYCLE_MILLIS: Int = 50
const val DEFAULT_SERVER_BUFFER_BYTES: Int = 65_536
const val DEFAULT_SOLUTION_BUFFER_BYTES: Int = 65_536
```

- [ ] **Step 5: Add fields to `RtklibProfile`**

In `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`, add constructor fields:

```kotlin
val frequencyCount: Int = DEFAULT_FREQUENCY_COUNT,
val serverCycleMillis: Int = DEFAULT_SERVER_CYCLE_MILLIS,
val serverBufferBytes: Int = DEFAULT_SERVER_BUFFER_BYTES,
val solutionBufferBytes: Int = DEFAULT_SOLUTION_BUFFER_BYTES,
```

Add validation:

```kotlin
require(frequencyCount in 1..3) { "RTKLIB frequency count must be 1, 2 or 3." }
require(serverCycleMillis > 0) { "RTKLIB server cycle must be positive." }
require(serverBufferBytes > 0) { "RTKLIB server buffer size must be positive." }
require(solutionBufferBytes > 0) { "RTKLIB solution buffer size must be positive." }
```

Add JSON fields to `toJson()`:

```kotlin
.put("frequencyCount", frequencyCount)
.put("serverCycleMillis", serverCycleMillis)
.put("serverBufferBytes", serverBufferBytes)
.put("solutionBufferBytes", solutionBufferBytes)
```

Add JSON fields to `fromJson()`:

```kotlin
frequencyCount = json.optInt("frequencyCount", DEFAULT_FREQUENCY_COUNT),
serverCycleMillis = json.optInt("serverCycleMillis", DEFAULT_SERVER_CYCLE_MILLIS),
serverBufferBytes = json.optInt("serverBufferBytes", DEFAULT_SERVER_BUFFER_BYTES),
solutionBufferBytes = json.optInt("solutionBufferBytes", DEFAULT_SOLUTION_BUFFER_BYTES),
```

Add companion defaults:

```kotlin
const val DEFAULT_FREQUENCY_COUNT = 1
const val DEFAULT_SERVER_CYCLE_MILLIS = 50
const val DEFAULT_SERVER_BUFFER_BYTES = 65_536
const val DEFAULT_SOLUTION_BUFFER_BYTES = 65_536
```

- [ ] **Step 6: Set protected default RTKLIB profile values**

In `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`, set the rover kinematic RTK profile to:

```kotlin
frequencyCount = 1,
serverCycleMillis = 50,
serverBufferBytes = 65_536,
solutionBufferBytes = 65_536,
```

Use `frequencyCount = 1` because the immediate validated target is u-blox M8T/M8P. UM980-specific RTKLIB profiles can later copy the profile and set `frequencyCount` to `2` or `3` once direct UM980 OBSVMB live validation is field-proven.

- [ ] **Step 7: Run RTKLIB/profile tests**

Run:

```bash
sh gradlew :core:rtklib:test --tests org.rtkcollector.core.rtklib.RtklibConfigTest
sh gradlew :app:compileDebugKotlin
```

Expected:

- `:core:rtklib:test` passes.
- `:app:compileDebugKotlin` passes in the supported local Termux check environment.

- [ ] **Step 8: Commit checkpoint**

Run:

```bash
git add core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibConfig.kt \
  core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibConfigTest.kt \
  app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt \
  app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt \
  app/src/test/kotlin/org/rtkcollector/app/profile/RecordingPolicyProfileTest.kt
git commit -m "feat: add rtklib runtime server profile parameters"
```

## Task 4: Propagate RTKLIB Runtime Parameters To Active Config And UI

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`

- [ ] **Step 1: Write failing active config test**

In `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt`, add:

```kotlin
@Test
fun `active config carries rtklib server runtime parameters`() {
    val config = ActiveRecordingConfig.resolve(
        settingsSet = RecordingSettingsSet.builtInRoverNtrip().copy(
            workflowId = "rover-rtklib",
            receiverProfileId = "ublox-m8t",
            rtklibProfileRef = ProfileReference("rtklib-rover-kinematic", "RTKLIB rover kinematic RTK"),
        ),
        commandProfile = CommandProfile(
            id = "ublox-m8t-raw-5hz-rtklib-ex",
            name = "u-blox M8T 5 Hz NAV-PVT 5 Hz RAWX RTKLIB",
            receiverFamily = "ublox-m8t",
            runtimeScript = "!UBX CFG-RATE 200 1 1",
        ),
        usbBaudProfile = UsbBaudProfile("baud", "Baud", profileBaud = 230400, serialBaud = 230400),
        ntripCasterProfile = NtripCasterProfile("caster", "Caster", host = "caster.example.org"),
        ntripMountpointProfile = NtripMountpointProfile("mount", "Mount", casterProfileId = "caster", mountpoint = "BASE0"),
        recordingPolicyProfile = RecordingPolicyProfile("record", "Record"),
        storageProfile = StorageProfile("storage", "Storage"),
        rtklibProfile = RtklibProfile(
            id = "rtklib-rover-kinematic",
            name = "RTKLIB rover kinematic RTK",
            enabled = true,
            frequencyCount = 1,
            serverCycleMillis = 50,
            serverBufferBytes = 65536,
            solutionBufferBytes = 65536,
        ),
        workflowName = "Rover + RTKLIB",
        workflowUsesNtrip = true,
        passwordLookup = { null },
    )

    assertEquals(1, config.rtklib.frequencyCount)
    assertEquals(50, config.rtklib.serverCycleMillis)
    assertEquals(65536, config.rtklib.serverBufferBytes)
    assertEquals(65536, config.rtklib.solutionBufferBytes)
}
```

If local helper names differ, use the existing test fixture methods in the same file and preserve the four assertions exactly.

- [ ] **Step 2: Run test and verify failure**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected before implementation: compile fails because `ActiveRecordingConfig.Rtklib` does not expose the new fields.

- [ ] **Step 3: Extend active config model**

In `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt`, extend the RTKLIB nested model with:

```kotlin
val frequencyCount: Int,
val serverCycleMillis: Int,
val serverBufferBytes: Int,
val solutionBufferBytes: Int,
```

When resolving from `RtklibProfile`, assign:

```kotlin
frequencyCount = rtklibProfile?.frequencyCount ?: RtklibProfile.DEFAULT_FREQUENCY_COUNT,
serverCycleMillis = rtklibProfile?.serverCycleMillis ?: RtklibProfile.DEFAULT_SERVER_CYCLE_MILLIS,
serverBufferBytes = rtklibProfile?.serverBufferBytes ?: RtklibProfile.DEFAULT_SERVER_BUFFER_BYTES,
solutionBufferBytes = rtklibProfile?.solutionBufferBytes ?: RtklibProfile.DEFAULT_SOLUTION_BUFFER_BYTES,
```

- [ ] **Step 4: Add intent extras from dashboard start path**

In `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`, in the RTKLIB extras added by `buildDashboardStartIntent(...)`, add:

```kotlin
putExtra(RecordingForegroundService.EXTRA_RTKLIB_FREQUENCY_COUNT, activeConfig.rtklib.frequencyCount)
putExtra(RecordingForegroundService.EXTRA_RTKLIB_SERVER_CYCLE_MILLIS, activeConfig.rtklib.serverCycleMillis)
putExtra(RecordingForegroundService.EXTRA_RTKLIB_SERVER_BUFFER_BYTES, activeConfig.rtklib.serverBufferBytes)
putExtra(RecordingForegroundService.EXTRA_RTKLIB_SOLUTION_BUFFER_BYTES, activeConfig.rtklib.solutionBufferBytes)
```

- [ ] **Step 5: Add service constants**

In `RecordingForegroundService.Companion`, add:

```kotlin
const val EXTRA_RTKLIB_FREQUENCY_COUNT = "rtklibFrequencyCount"
const val EXTRA_RTKLIB_SERVER_CYCLE_MILLIS = "rtklibServerCycleMillis"
const val EXTRA_RTKLIB_SERVER_BUFFER_BYTES = "rtklibServerBufferBytes"
const val EXTRA_RTKLIB_SOLUTION_BUFFER_BYTES = "rtklibSolutionBufferBytes"
```

- [ ] **Step 6: Pass runtime fields into service-created `RtklibConfig`**

In `RecordingForegroundService.startRtklibWorkerIfEnabled(...)`, add to `RtklibConfig(...)`:

```kotlin
frequencyCount = intent.getIntExtra(
    EXTRA_RTKLIB_FREQUENCY_COUNT,
    RtklibConfig.DEFAULT_FREQUENCY_COUNT,
),
serverCycleMillis = intent.getIntExtra(
    EXTRA_RTKLIB_SERVER_CYCLE_MILLIS,
    RtklibConfig.DEFAULT_SERVER_CYCLE_MILLIS,
),
serverBufferBytes = intent.getIntExtra(
    EXTRA_RTKLIB_SERVER_BUFFER_BYTES,
    RtklibConfig.DEFAULT_SERVER_BUFFER_BYTES,
),
solutionBufferBytes = intent.getIntExtra(
    EXTRA_RTKLIB_SOLUTION_BUFFER_BYTES,
    RtklibConfig.DEFAULT_SOLUTION_BUFFER_BYTES,
),
```

- [ ] **Step 7: Expose compact RTKLIB profile editing fields**

In `MainActivity.kt`, within the RTKLIB profile editor branch, add editable fields:

- `Frequency count` as a selectable option with values `1`, `2`, `3`.
- `Server cycle [ms]` as numeric text field.
- `Server buffer [bytes]` as numeric text field.
- `Solution buffer [bytes]` as numeric text field.

On save, parse using:

```kotlin
frequencyCount = edits["frequencyCount"]?.toIntOrNull() ?: profile.frequencyCount,
serverCycleMillis = edits["serverCycleMillis"]?.toIntOrNull() ?: profile.serverCycleMillis,
serverBufferBytes = edits["serverBufferBytes"]?.toIntOrNull() ?: profile.serverBufferBytes,
solutionBufferBytes = edits["solutionBufferBytes"]?.toIntOrNull() ?: profile.solutionBufferBytes,
```

Keep built-in profile behaviour unchanged: built-ins are viewable read-only, users copy before editing.

- [ ] **Step 8: Run compile and focused tests**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: Kotlin compilation passes.

- [ ] **Step 9: Commit checkpoint**

Run:

```bash
git add app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt \
  app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt \
  app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
git commit -m "feat: propagate rtklib server runtime parameters"
```

## Task 5: Kotlin Native API Contract For RTKLIB Server Backend

**Files:**
- Modify: `core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibNativeBridge.kt`
- Modify: `core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibWorkerTest.kt`

- [ ] **Step 1: Write failing native API propagation test**

In `RtklibWorkerTest.kt`, add a fake native API field recorder:

```kotlin
@Test
fun `native bridge passes rtklib server runtime parameters`() {
    val api = FakeNativeApi()
    val bridge = RtklibNativeBridge(
        loadLibrary = {},
        nativeApi = api,
    )
    val backend = bridge.create()

    backend.start(
        validConfig().copy(
            frequencyCount = 1,
            serverCycleMillis = 50,
            serverBufferBytes = 65536,
            solutionBufferBytes = 65536,
        ),
    )

    assertEquals(1, api.frequencyCount)
    assertEquals(50, api.serverCycleMillis)
    assertEquals(65536, api.serverBufferBytes)
    assertEquals(65536, api.solutionBufferBytes)
}
```

Modify `FakeNativeApi` in the same file to store the four values after the production signature is changed.

- [ ] **Step 2: Run test and verify failure**

Run:

```bash
sh gradlew :core:rtklib:test --tests org.rtkcollector.core.rtklib.RtklibWorkerTest
```

Expected before implementation: compile fails because `NativeApi.start(...)` does not accept the new fields.

- [ ] **Step 3: Extend `NativeApi.start`**

In `RtklibNativeBridge.kt`, change `NativeApi.start(...)` to:

```kotlin
fun start(
    handle: Long,
    preset: String,
    roverFormat: String,
    correctionFormat: String,
    outputNmea: Boolean,
    outputPos: Boolean,
    frequencyCount: Int,
    serverCycleMillis: Int,
    serverBufferBytes: Int,
    solutionBufferBytes: Int,
): String?
```

Pass the new fields from `NativeBackend.start(config)`:

```kotlin
frequencyCount = config.frequencyCount,
serverCycleMillis = config.serverCycleMillis,
serverBufferBytes = config.serverBufferBytes,
solutionBufferBytes = config.solutionBufferBytes,
```

- [ ] **Step 4: Extend external JNI declaration**

Change `nativeRtklibStart(...)` signature to include:

```kotlin
frequencyCount: Int,
serverCycleMillis: Int,
serverBufferBytes: Int,
solutionBufferBytes: Int,
```

- [ ] **Step 5: Update fake native API**

Update `FakeNativeApi.start(...)` in `RtklibWorkerTest.kt` to accept the new parameters and store:

```kotlin
var frequencyCount: Int = -1
var serverCycleMillis: Int = -1
var serverBufferBytes: Int = -1
var solutionBufferBytes: Int = -1
```

inside the fake class.

- [ ] **Step 6: Run RTKLIB core tests**

Run:

```bash
sh gradlew :core:rtklib:test
```

Expected: tests pass.

- [ ] **Step 7: Commit checkpoint**

Run:

```bash
git add core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibNativeBridge.kt \
  core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibWorkerTest.kt
git commit -m "feat: pass rtklib server runtime options to native bridge"
```

## Task 6: Native Build Includes RTKLIB Server

**Files:**
- Modify: `app/src/main/cpp/CMakeLists.txt`

- [ ] **Step 1: Run native build-file test and verify failure**

Run:

```bash
python3 -m pytest tools/test_rtklib_native_build_files.py tools/test_rtklib_native_server_backend.py
```

Expected before implementation: assertion failure for missing `rtksvr.c`.

- [ ] **Step 2: Add `rtksvr.c` to native library sources**

In `app/src/main/cpp/CMakeLists.txt`, add:

```cmake
"${RTKLIB_SRC}/rtksvr.c"
```

next to:

```cmake
"${RTKLIB_SRC}/rtkpos.c"
```

- [ ] **Step 3: Run native build-file tests**

Run:

```bash
python3 -m pytest tools/test_rtklib_native_build_files.py
```

Expected: build-file test passes.

- [ ] **Step 4: Commit checkpoint**

Run:

```bash
git add app/src/main/cpp/CMakeLists.txt
git commit -m "build: include rtklib server source"
```

## Task 7: Replace Simplified Native RTKLIB Loop With `rtksvr_t`

**Files:**
- Modify: `app/src/main/cpp/rtklib_bridge.cpp`

- [ ] **Step 1: Confirm failing server-backend architecture test**

Run:

```bash
python3 -m pytest tools/test_rtklib_native_server_backend.py
```

Expected before implementation: failure because `latest_base_obs` and direct `rtkpos()` path still exist.

- [ ] **Step 2: Replace engine state**

In `app/src/main/cpp/rtklib_bridge.cpp`, replace the existing `RtklibEngineHandle` fields:

```cpp
raw_t rover_raw{};
rtcm_t correction_rtcm{};
nav_t nav{};
rtk_t rtk{};
prcopt_t prcopt{};
solopt_t nmea_opt{};
solopt_t pos_opt{};
std::vector<obsd_t> latest_base_obs;
```

with:

```cpp
rtksvr_t server{};
stream_t monitor{};
prcopt_t prcopt{};
solopt_t solopt[2]{};
bool server_initialized = false;
bool server_started = false;
bool output_nmea = true;
bool output_pos = true;
uint64_t decoded_rover_epochs = 0;
uint64_t decoded_correction_messages = 0;
NativeResult latest;
```

Keep `std::mutex mutex` and `started`-style state, but rename booleans clearly so `rtksvrstop()` and `rtksvrfree()` run exactly once.

- [ ] **Step 3: Remove direct decoder helpers**

Delete these functions completely:

```cpp
init_nav
copy_ion_utc
copy_nav_update
maybe_update_base_position
store_base_observations
solve_if_possible
handle_rover_ret
handle_correction_ret
feed_bytes
```

Do not leave a direct `rtkpos(&engine->rtk, ...)` call in the file.

- [ ] **Step 4: Add RTKLIB stream format mapping helper**

Add:

```cpp
static int rover_format_for(const char *format) {
    if (std::strcmp(format, "UBX_RXM_RAWX_SFRBX") == 0) return STRFMT_UBX;
    if (std::strcmp(format, "UNICORE_OBSVMB") == 0) return STRFMT_UNICORE;
    if (std::strcmp(format, "UNICORE_OBSVMCMPB") == 0) return STRFMT_UNICORE;
    return STRFMT_UBX;
}
```

Keep correction format fixed to `STRFMT_RTCM3` for the current route.

- [ ] **Step 5: Update processing options for explicit frequency count**

Change `configure_options(...)` signature to:

```cpp
static void configure_options(RtklibEngineHandle *engine, const char *preset, int frequency_count)
```

Set:

```cpp
engine->prcopt = prcopt_default;
engine->prcopt.soltype = SOLTYPE_FORWARD;
engine->prcopt.mode = std::strcmp(preset, "TEMPORARY_BASE_STATIC_RTK") == 0 ? PMODE_STATIC : PMODE_KINEMA;
engine->prcopt.navsys = SYS_GPS | SYS_GLO | SYS_GAL | SYS_CMP | SYS_QZS;
engine->prcopt.nf = std::max(1, std::min(3, frequency_count));
engine->prcopt.refpos = POSOPT_RTCM;
engine->prcopt.rovpos = POSOPT_POS_LLH;
```

Set solution options:

```cpp
engine->solopt[0] = solopt_default;
engine->solopt[0].posf = SOLF_NMEA;
engine->solopt[0].times = TIMES_GPST;
engine->solopt[0].timeu = 3;
engine->solopt[0].outhead = 0;

engine->solopt[1] = solopt_default;
engine->solopt[1].posf = SOLF_LLH;
engine->solopt[1].times = TIMES_GPST;
engine->solopt[1].timeu = 3;
engine->solopt[1].outhead = 0;
```

- [ ] **Step 6: Implement server start with memory-buffer streams**

In JNI `nativeRtklibStart(...)`, after configuring options, initialise and start RTKLIB server:

```cpp
if (!rtksvrinit(&engine->server)) {
    return env->NewStringUTF("RTKLIB server initialization failed");
}
engine->server_initialized = true;

int strs[8] = {
    STR_MEMBUF,
    STR_MEMBUF,
    STR_NONE,
    output_nmea ? STR_MEMBUF : STR_NONE,
    output_pos ? STR_MEMBUF : STR_NONE,
    STR_NONE,
    STR_NONE,
    STR_NONE,
};

std::string rover_path = std::to_string(std::max(server_buffer_bytes, 4096));
std::string base_path = std::to_string(std::max(server_buffer_bytes, 4096));
std::string nmea_path = std::to_string(std::max(solution_buffer_bytes, 4096));
std::string pos_path = std::to_string(std::max(solution_buffer_bytes, 4096));

const char *paths[8] = {
    rover_path.c_str(),
    base_path.c_str(),
    "",
    nmea_path.c_str(),
    pos_path.c_str(),
    "",
    "",
    "",
};

int formats[3] = {
    rover_format_for(rover_format),
    STRFMT_RTCM3,
    STRFMT_RTCM3,
};

const char *cmds[3] = {nullptr, nullptr, nullptr};
const char *periodic_cmds[3] = {nullptr, nullptr, nullptr};
const char *rcvopts[3] = {"", "", ""};
double nmeapos[3] = {0.0, 0.0, 0.0};
char errmsg[2048] = {0};

if (!rtksvrstart(
        &engine->server,
        std::max(server_cycle_millis, 1),
        std::max(server_buffer_bytes, 4096),
        strs,
        paths,
        formats,
        0,
        cmds,
        periodic_cmds,
        rcvopts,
        0,
        0,
        nmeapos,
        &engine->prcopt,
        engine->solopt,
        nullptr,
        errmsg)) {
    std::string error = errmsg[0] ? errmsg : "RTKLIB server start failed";
    if (engine->server_initialized) {
        rtksvrfree(&engine->server);
        engine->server_initialized = false;
    }
    return env->NewStringUTF(error.c_str());
}
engine->server_started = true;
engine->latest.state = "RUNNING";
```

- [ ] **Step 7: Implement memory-stream feed**

Replace native feed logic with:

```cpp
static NativeResult feed_server_bytes(RtklibEngineHandle *engine, int stream_kind, const uint8_t *bytes, int length) {
    NativeResult result = engine->latest;
    result.nmea.clear();
    result.pos.clear();
    result.warning.clear();
    result.error.clear();

    if (!engine->server_started) {
        result.state = "FAILED";
        result.error = "RTKLIB server is not started";
        return result;
    }

    int stream_index = stream_kind == STREAM_ROVER ? 0 : 1;
    int written = strwrite(engine->server.stream + stream_index, const_cast<uint8_t *>(bytes), length);
    if (written < length) {
        result.warning = "RTKLIB memory stream accepted fewer bytes than offered";
    }

    drain_solution_streams(engine, &result);
    update_result_from_server(engine, &result);
    result.state = result.error.empty() ? "RUNNING" : "FAILED";
    engine->latest = result;
    return result;
}
```

Use `STREAM_CORRECTION` as RTKLIB base stream index `1`, because app-side NTRIP RTCM3 is the base/correction observation stream for rover RTK.

- [ ] **Step 8: Implement solution stream draining**

Add:

```cpp
static void drain_stream_to_string(stream_t *stream, std::string *target) {
    uint8_t buff[4096] = {0};
    while (true) {
        int n = strread(stream, buff, sizeof(buff));
        if (n <= 0) break;
        target->append(reinterpret_cast<const char *>(buff), n);
    }
    if (!target->empty() && target->back() != '\n') {
        target->push_back('\n');
    }
}

static void drain_solution_streams(RtklibEngineHandle *engine, NativeResult *result) {
    if (engine->output_nmea) drain_stream_to_string(engine->server.stream + 3, &result->nmea);
    if (engine->output_pos) drain_stream_to_string(engine->server.stream + 4, &result->pos);
}
```

- [ ] **Step 9: Implement server snapshot extraction**

Add:

```cpp
static void update_result_from_server(RtklibEngineHandle *engine, NativeResult *result) {
    rtksvrlock(&engine->server);
    sol_t sol = engine->server.rtk.sol;
    uint64_t rover_messages = engine->server.nmsg[0][0];
    uint64_t base_messages = engine->server.nmsg[1][0] + engine->server.nmsg[1][1] +
        engine->server.nmsg[1][4] + engine->server.nmsg[1][5] + engine->server.nmsg[1][6] +
        engine->server.nmsg[1][7];
    rtksvrunlock(&engine->server);

    engine->decoded_rover_epochs = rover_messages;
    engine->decoded_correction_messages = base_messages;

    if (sol.stat <= SOLQ_NONE) return;

    double pos[3] = {0.0, 0.0, 0.0};
    ecef2pos(sol.rr, pos);
    result->fix_class = fix_class_for(sol.stat);
    result->timestamp_millis = long_to_string(solution_time_millis(&sol));
    result->lat_deg = double_to_string(pos[0] * R2D);
    result->lon_deg = double_to_string(pos[1] * R2D);
    result->height_m = double_to_string(pos[2]);
    result->satellites_used = long_to_string(sol.ns);
    if (sol.qr[0] > 0.0 && sol.qr[1] > 0.0) {
        result->h_acc_m = double_to_string(std::sqrt(sol.qr[0] + sol.qr[1]));
    }
    if (sol.qr[2] > 0.0) {
        result->v_acc_m = double_to_string(std::sqrt(sol.qr[2]));
    }
}
```

- [ ] **Step 10: Implement stop and destroy correctly**

In native stop:

```cpp
if (engine->server_started) {
    const char *cmds[3] = {nullptr, nullptr, nullptr};
    rtksvrstop(&engine->server, cmds);
    engine->server_started = false;
}
```

In native destroy or `release_rtklib_state(...)`:

```cpp
if (engine->server_started) {
    const char *cmds[3] = {nullptr, nullptr, nullptr};
    rtksvrstop(&engine->server, cmds);
    engine->server_started = false;
}
if (engine->server_initialized) {
    rtksvrfree(&engine->server);
    engine->server_initialized = false;
}
```

- [ ] **Step 11: Update JNI signature**

Change `Java_org_rtkcollector_core_rtklib_RtklibNativeBridgeKt_nativeRtklibStart(...)` signature to receive:

```cpp
jint frequency_count,
jint server_cycle_millis,
jint server_buffer_bytes,
jint solution_buffer_bytes
```

Pass those values into `configure_options(...)` and `rtksvrstart(...)`.

- [ ] **Step 12: Run architecture tests**

Run:

```bash
python3 -m pytest tools/test_rtklib_native_server_backend.py tools/test_rtklib_native_build_files.py
```

Expected: tests pass.

- [ ] **Step 13: Run Kotlin compile**

Run:

```bash
sh gradlew :core:rtklib:test :app:compileDebugKotlin
```

Expected:

- `:core:rtklib:test` passes.
- `:app:compileDebugKotlin` passes.

- [ ] **Step 14: Native build verification on capable host**

On Windows Android Studio, CI, or another host with a working Android NDK, run:

```bash
./gradlew :app:externalNativeBuildDebug
./gradlew assembleDebug
```

Expected:

- `rtkcollector_rtklib` native library builds.
- APK packaging succeeds.

In Termux/aarch64, do not burn time retrying this if it reaches the known native Android SDK/NDK tool limitation.

- [ ] **Step 15: Commit checkpoint**

Run:

```bash
git add app/src/main/cpp/rtklib_bridge.cpp app/src/main/cpp/CMakeLists.txt tools/test_rtklib_native_server_backend.py tools/test_rtklib_native_build_files.py
git commit -m "feat: use rtklib server backend for live processing"
```

## Task 8: RTKLIB Status Output And Diagnostics

**Files:**
- Modify: `core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibWorker.kt`
- Modify: `core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibModels.kt` or the current file containing `RtklibEngineSnapshot`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt`

- [ ] **Step 1: Find current RTKLIB snapshot model**

Run:

```bash
rg -n "data class RtklibEngineSnapshot|RtklibSolutionSnapshot|rtklib-status" core app/src/main/kotlin app/src/test/kotlin
```

Expected: one model definition and service/dashboard mapping references.

- [ ] **Step 2: Add snapshot fields**

Extend `RtklibEngineSnapshot` with:

```kotlin
val serverCpuTimeMillis: Int? = null,
val serverRoverObservationMessages: Long = 0L,
val serverBaseObservationMessages: Long = 0L,
val serverMissingObservationCount: Long = 0L,
```

If native does not yet expose all fields in Task 7, populate with `null` or `0L`. Do not block RTKLIB output on diagnostics.

- [ ] **Step 3: Append bounded status JSONL only when RTKLIB is enabled**

In `RecordingForegroundService`, add a private timestamp:

```kotlin
private var lastRtklibStatusWriteMillis: Long = 0L
```

In the periodic state/update path that already snapshots RTKLIB state, write at most once per second:

```kotlin
private fun maybeAppendRtklibStatus(snapshot: RtklibEngineSnapshot, nowMillis: Long) {
    if (nowMillis - lastRtklibStatusWriteMillis < 1_000L) return
    lastRtklibStatusWriteMillis = nowMillis
    sessionWriters?.appendRtklibStatusJsonl(
        """{"type":"rtklib-status","state":"${snapshot.state}","fix":${snapshot.latestSolution?.fixClass?.name.jsonStringOrNull()},"decodedRoverEpochs":${snapshot.decodedRoverEpochs},"decodedCorrectionMessages":${snapshot.decodedCorrectionMessages},"droppedRoverBytes":${snapshot.droppedRoverBytes},"droppedCorrectionBytes":${snapshot.droppedCorrectionBytes},"warning":${snapshot.lastWarning.jsonStringOrNull()},"error":${snapshot.lastError.jsonStringOrNull()}}""",
    )
}
```

Use the repository's existing JSON escaping helper names rather than duplicating helpers.

- [ ] **Step 4: Add test for non-empty RTKLIB status when worker snapshot exists**

In the closest service/dashboard mapper test file, add a focused test that a snapshot with decoded rover/correction counts maps to dashboard RTKLIB status and does not require receiver-internal solution data.

- [ ] **Step 5: Run compile/tests**

Run:

```bash
sh gradlew :core:rtklib:test :app:compileDebugKotlin
```

Expected: passes.

- [ ] **Step 6: Commit checkpoint**

Run:

```bash
git add core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib \
  app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt \
  app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapperTest.kt
git commit -m "feat: record bounded rtklib status diagnostics"
```

## Task 9: Replay/Field Validation Tooling For The M8T Session

**Files:**
- Create: `tools/rtklib_solution_summary.py`
- Create: `tools/test_rtklib_solution_summary.py`

- [ ] **Step 1: Write solution summary script**

Create `tools/rtklib_solution_summary.py`:

```python
#!/usr/bin/env python3
import argparse
import collections
import zipfile


def summarize_pos_lines(lines):
    quality = collections.Counter()
    satellites = collections.Counter()
    for line in lines:
        line = line.strip()
        if not line or line.startswith("%"):
            continue
        parts = line.split()
        if len(parts) < 7:
            continue
        quality[parts[5]] += 1
        satellites[parts[6]] += 1
    return quality, satellites


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("session_zip")
    args = parser.parse_args()
    with zipfile.ZipFile(args.session_zip) as archive:
        text = archive.read("rtklib-solution.pos").decode("ascii", errors="replace")
    quality, satellites = summarize_pos_lines(text.splitlines())
    print("quality", " ".join(f"{key}:{quality[key]}" for key in sorted(quality)))
    print("satellites", " ".join(f"{key}:{satellites[key]}" for key in sorted(satellites, key=lambda value: int(value) if value.isdigit() else value)))


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Write script unit test**

Create `tools/test_rtklib_solution_summary.py`:

```python
from tools.rtklib_solution_summary import summarize_pos_lines


def test_summarize_pos_lines_counts_quality_and_satellites():
    quality, satellites = summarize_pos_lines(
        [
            "% header",
            "2026/06/21 17:09:48.200 49.0 16.0 342.0 4 3 1.0",
            "2026/06/21 17:09:48.400 49.0 16.0 342.0 2 12 1.0",
            "2026/06/21 17:09:48.600 49.0 16.0 342.0 1 15 1.0",
        ],
    )

    assert quality["4"] == 1
    assert quality["2"] == 1
    assert quality["1"] == 1
    assert satellites["3"] == 1
    assert satellites["12"] == 1
    assert satellites["15"] == 1
```

- [ ] **Step 3: Run script test**

Run:

```bash
python3 -m pytest tools/test_rtklib_solution_summary.py
```

Expected: passes.

- [ ] **Step 4: Use script on current field sample as baseline**

Run:

```bash
python3 tools/rtklib_solution_summary.py samples/debug/session-2026-06-21T17-08-13.445776Z-d83546aa-beb3-4ff1-9cc6-c21142b9dcdc-1782062170308.zip
```

Expected before a new APK field test:

```text
quality 4:1274
satellites 3:1274
```

After deploying a build with the server backend and recording the same rover + RTKLIB workflow, expected improvement:

- `satellites` is not only `3`.
- RTKLIB quality includes `2` float or `1` fixed when field conditions and single-frequency M8T limitations allow.
- If quality remains `4`, `rtklib-status.jsonl` and solution summary show enough decoded rover/base counts to distinguish RTKLIB configuration limits from missing observations.

- [ ] **Step 5: Commit checkpoint**

Run:

```bash
git add tools/rtklib_solution_summary.py tools/test_rtklib_solution_summary.py
git commit -m "test: add rtklib solution summary tooling"
```

## Task 10: End-To-End Verification And Review

**Files:**
- Review all modified files.
- Do not add anything under `samples/`.

- [ ] **Step 1: Confirm working tree excludes debug samples**

Run:

```bash
git status --short
```

Expected: no `samples/` files staged or modified by this implementation.

- [ ] **Step 2: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no whitespace errors.

- [ ] **Step 3: Run source tests that are valid in Termux**

Run:

```bash
python3 -m pytest tools/test_rtklib_native_build_files.py tools/test_rtklib_native_server_backend.py tools/test_rtklib_solution_summary.py
sh gradlew :core:rtklib:test :app:compileDebugKotlin
```

Expected:

- Python tests pass.
- `:core:rtklib:test` passes.
- `:app:compileDebugKotlin` passes.

- [ ] **Step 4: Run native/APK validation on capable host**

On Windows Android Studio, CI, or another host with working Android SDK/NDK native tools:

```bash
./gradlew :app:externalNativeBuildDebug
./gradlew assembleDebug
```

Expected: native RTKLIB library and debug APK build successfully.

- [ ] **Step 5: Field smoke test**

On Android with u-blox M8T:

1. Select `Rover + RTKLIB`.
2. Select u-blox M8T RAWX/SFRBX RTKLIB command profile.
3. Select valid NTRIP caster and mountpoint.
4. Start recording.
5. Confirm `receiver-rx.raw`, `correction-input.rtcm3`, `rtklib-solution.pos`, `rtklib-solution.nmea`, and `rtklib-status.jsonl` grow.
6. Stop recording.
7. Export/share ZIP.
8. Run `tools/rtklib_solution_summary.py` on the ZIP.

Expected:

- RTKLIB output is no longer stuck at `quality 4` with `satellites 3` only.
- If RTK float/fix is not reached, status and solution outputs still show realistic rover/base observation counts.
- Stopping RTKLIB or RTKLIB failure does not stop raw recording while transport/storage remain available.

- [ ] **Step 6: Review for non-negotiable architecture**

Inspect:

```bash
rg -n "receiver-rx.raw|correction-input.raw|tx-to-receiver.raw|RtklibWorker|System.loadLibrary|EXTRA_RTKLIB_ENABLED|latest_base_obs|rtkpos\\(&engine" app core docs tools
```

Expected:

- No RTKLIB code writes into `receiver-rx.raw`.
- RTKLIB is created only behind `EXTRA_RTKLIB_ENABLED`.
- `System.loadLibrary` remains lazy inside `RtklibNativeBridge.create()`.
- `latest_base_obs` and direct `rtkpos(&engine...)` are gone from the JNI bridge.

- [ ] **Step 7: Request code review**

Use `superpowers:requesting-code-review` with reviewer scopes:

- Android robust background-capture engineer: verify RTKLIB remains advisory and cannot block raw recording.
- GNSS/RTK systems architect: verify `rtksvr_t` memory-buffer use matches RTKLIB real-time semantics.
- Kotlin/domain-model maintainer: verify RTKLIB profile/config fields are clear and testable.

- [ ] **Step 8: Final commit if review fixes were needed**

If review changes are applied after earlier checkpoint commits, run:

```bash
git add app core docs tools
git commit -m "fix: address rtklib server backend review"
```

- [ ] **Step 9: Push after final validation**

Run:

```bash
git push
```

Expected: branch pushed with no `samples/` captures included.

## Risk Notes For Implementers

- `STR_MEMBUF` input streams opened by `rtksvrstart()` are opened read/write for non-file input streams in RTKLIB-EX, so JNI can feed them with `strwrite()`.
- RTKLIB's memory buffer reports overflow through stream state/message; partial writes must become RTKLIB advisory warnings and must not block raw recording.
- `rtksvr_t` starts its own native thread. The app-side `RtklibWorker` thread still remains useful as the boundary that copies bytes out of the recording path and handles bounded backpressure.
- Do not send receiver startup/shutdown commands through RTKLIB. RtkCollector already owns receiver command execution and records app TX in `tx-to-receiver.raw`.
- Do not let RTKLIB open NTRIP. RtkCollector already owns NTRIP authentication, reconnect policy, byte recording and optional receiver injection.
- Do not enable RTKLIB for plain rover, rover+NTRIP-to-receiver, temporary-base or fixed-base workflows unless the selected workflow/profile explicitly enables RTKLIB.
- M8T/M8P are single-frequency; RTK fixed may be harder than with F9P/UM980. The immediate bug criterion is eliminating the artificial `ns=3` DGPS-only behaviour caused by the bridge architecture.
