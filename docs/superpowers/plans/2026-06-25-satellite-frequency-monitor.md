# Satellite Frequency Monitor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a recording-only satellite-frequency monitor that compares external rover observations, NTRIP base correction observations and selected-engine signal usage without weakening byte-exact recording reliability.

**Architecture:** Satellite monitoring is an advisory pipeline attached after receiver and correction bytes are already persisted. Receiver/profile capabilities are explicit, source availability is reported honestly, and unsupported rover/base/solution data is displayed as unavailable instead of being inferred. Shared protocol and monitor models live in reusable modules, the foreground service owns live aggregation, and Compose only renders throttled snapshots.

**Tech Stack:** Kotlin, Android foreground service, Jetpack Compose, existing Gradle modules `core:quality`, `core:correction`, `core:rtklib`, `receiver:ublox-m8`, `receiver:unicore-n4`, `app`.

---

## Source Design

- Approved design: `docs/superpowers/specs/2026-06-23-satellite-frequency-monitor-design.md`
- External receiver scope: USB GNSS receivers only; Android internal GNSS is out of scope.
- Reliability rule: parser, monitor, UI and RTKLIB usage-state failures must not stop recording, NTRIP, receiver command routing, correction writing, RTKLIB workers or session writers.
- Capture rule: `receiver-rx.raw`, `tx-to-receiver.raw` and `correction-input.raw` remain authoritative byte streams. Satellite monitor parsing consumes bytes only after the matching append call has returned.
- Profile rule: satellite monitoring is enabled only by explicit receiver command/profile capability metadata. No hidden command injection and no silent fallback between `In-device RTK` and `RTKLIB`.
- Compact-card display rule: the main dashboard card stays with the active main engine and does not expose an engine selector. It groups first by constellation and then by frequency. Each frequency cell has rover (`R`) and base (`B`) boxed bars with right-aligned `used/visible` counts; saturated boxes are the used prefix inside the lower-saturation visible total.
- Freshness-label rule: the compact card labels source freshness dots as `R`, `B` and `S`, with an information affordance explaining rover, base and selected-solution usage semantics.
- Theme rule: the compact satellite card follows the light app colour scheme by default. Dashboard layout settings provide a binary switch for the approved dark satellite-card palette without changing the whole app theme.
- Detailed-view grouping rule: default to frequency as the primary grouping, with an optional detailed-view slider switch for constellation as the primary grouping and frequency as the secondary grouping. The compact dashboard remains grouped by constellation first, with frequency bands nested inside each constellation.

## File Structure

### Create

- `core/quality/src/main/kotlin/org/rtkcollector/core/quality/SatelliteMonitorModels.kt`
  Receiver-agnostic monitor model, normalized constellation/frequency keys, source freshness and summary rows.
- `core/quality/src/test/kotlin/org/rtkcollector/core/quality/SatelliteMonitorModelsTest.kt`
  Unit tests for frequency grouping, summary counts, source status and stale-source behavior.
- `core/correction/src/main/kotlin/org/rtkcollector/core/correction/RtcmBitReader.kt`
  Shared RTCM bit reader extracted from `Rtcm3ReferenceStationParser`.
- `core/correction/src/main/kotlin/org/rtkcollector/core/correction/Rtcm3MsmParser.kt`
  Advisory RTCM3 MSM decoder for base-side satellite/signal presence and C/N0 where carried.
- `core/correction/src/main/kotlin/org/rtkcollector/core/correction/RtcmSignalMapping.kt`
  Message-family and signal-ID mapping to normalized `SatelliteSignalKey` frequency bands.
- `core/correction/src/test/kotlin/org/rtkcollector/core/correction/Rtcm3MsmParserTest.kt`
  Synthetic-frame tests for MSM masks, frequency mapping and malformed-frame rejection.
- `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxRawxSatelliteMonitorParser.kt`
  UBX `RXM-RAWX` rover observation mapping for M8T/M8P satellite-monitor profiles.
- `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxRawxSatelliteMonitorParserTest.kt`
  Tests for UBX raw observation frequency mapping and C/N0 extraction.
- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980SatelliteMonitorParser.kt`
  UM980/N4 observation/status mapping for monitor profiles, limited to documented messages enabled by explicit profiles.
- `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980SatelliteMonitorParserTest.kt`
  Tests with byte fixtures for supported UM980 satellite monitor messages.
- `app/src/main/kotlin/org/rtkcollector/app/recording/SatelliteMonitorController.kt`
  Bounded, in-memory advisory aggregator owned by the recording service.
- `app/src/test/kotlin/org/rtkcollector/app/recording/SatelliteMonitorControllerTest.kt`
  Tests for bounded queues, freshness transitions, engine selection and no-throw parser failure paths.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/SatelliteMonitorDashboardModels.kt`
  Compact-card model, source freshness labels and boxed-bar segment semantics.
- `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/SatelliteMonitorDashboardModelsTest.kt`
  Tests for compact-card grouping and used/visible boxed-bar semantics.
- `app/src/main/kotlin/org/rtkcollector/app/ui/satellite/SatelliteMonitorScreen.kt`
  Advanced grouped monitor screen bound to the dashboard-selected engine.
- `app/src/main/kotlin/org/rtkcollector/app/ui/satellite/SatelliteMonitorUiModels.kt`
  Compose display rows, bar visibility state and formatting helpers for the advanced screen.
- `app/src/test/kotlin/org/rtkcollector/app/ui/satellite/SatelliteMonitorUiModelsTest.kt`
  Pure Kotlin tests for grouping mode, bar toggle behavior and stale/unavailable labels.

### Modify

- `docs/specification/functional-requirements.md`
  Add formal requirements for advisory satellite monitoring, explicit profiles, source availability and external-USB-only scope.
- `docs/specification/ui-requirements.md`
  Add compact dashboard and advanced screen requirements for the monitor.
- `docs/specification/android-runtime.md`
  Add foreground-service and raw-capture isolation requirements for the monitor pipeline.
- `docs/specification/receiver-behaviour.md`
  Add receiver-family capability requirements for UM980/N4, u-blox M8P, u-blox M8T and generic NMEA/RTCM.
- `docs/specification/verification-matrix.md`
  Map new requirements to tests and manual hardware/caster checks.
- `docs/user-workflows.md`
  Document that RtkCollector currently targets external USB GNSS receivers and that the monitor does not consume Android internal GNSS.
- `docs/receiver-driver-api.md`
  Document monitor-capability reporting and the boundary between receiver parsing and app aggregation.
- `docs/ntrip-and-corrections.md`
  Document advisory RTCM MSM parsing after `correction-input.raw` append.
- `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
  Add explicit satellite monitor capability metadata to command profiles.
- `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
  Add built-in monitor profiles and default engine metadata.
- `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStoreMigrations.kt`
  Sync same-id built-ins while preserving user copies.
- `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt`
  Resolve active monitor configuration from the selected command profile.
- `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt`
  Cover monitor default engine and unsupported-profile state.
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
  Add satellite monitor snapshot fields and clear them on stop/reset.
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
  Feed advisory monitor controller after receiver/correction bytes are persisted; broadcast throttled monitor extras.
- `app/src/test/kotlin/org/rtkcollector/app/recording/RecordingServiceStateTest.kt`
  Cover monitor-state reset and dashboard broadcast defaults.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
  Add dashboard satellite monitor state.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
  Decode service extras into satellite monitor dashboard state.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
  Place compact main-engine `Satellites` card near fix/correction status.
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModels.kt`
  Add satellite-card light/dark theme preference.
- `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
  Add dashboard layout setting for the satellite-card dark palette.
- `core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibOutputModels.kt`
  Add optional per-signal usage contract without requiring RTKLIB to expose it immediately.
- `core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibOutputModelsTest.kt`
  Cover default-empty signal usage and snapshot compatibility.
- `receiver/ublox-m8/build.gradle.kts`
  Depend on `:core:quality`.
- `receiver/unicore-n4/build.gradle.kts`
  Depend on `:core:quality`.
- `core/correction/build.gradle.kts`
  Depend on `:core:quality`.

## Reliability Gates For Every Implementation Task

- Parser work must execute after raw receiver or correction append calls have completed.
- Parser exceptions must be converted to monitor-source unavailability, stale state or parser-warning counters.
- Monitor state must be in memory only while recording; no high-rate per-satellite history files are introduced.
- Monitor UI updates must use existing throttled service broadcasts or bounded state snapshots.
- Engine switching changes only monitor display selection. It must not start RTKLIB, stop RTKLIB, send receiver commands, reconnect USB, reconnect NTRIP or mutate workflow/profile state.
- Unsupported data is represented with explicit status values. Do not treat missing rover/base/solution data as zero satellites.
- Validation commands for this Termux workspace are `git diff --check` and `sh gradlew :app:compileDebugKotlin`; module JVM tests should be run when they do not trigger Android resource packaging.

---

### Task 1: Formal Requirements And Verification Matrix

**Files:**
- Modify: `docs/specification/functional-requirements.md`
- Modify: `docs/specification/ui-requirements.md`
- Modify: `docs/specification/android-runtime.md`
- Modify: `docs/specification/receiver-behaviour.md`
- Modify: `docs/specification/verification-matrix.md`
- Modify: `docs/user-workflows.md`
- Modify: `docs/receiver-driver-api.md`
- Modify: `docs/ntrip-and-corrections.md`

- [ ] **Step 1: Add functional requirements**

Add these requirement entries under the closest existing functional requirements section:

```markdown
### Satellite Frequency Monitoring

- **FR-SATMON-001:** RtkCollector shall provide satellite-frequency monitoring only for active recording sessions using external USB GNSS receivers.
- **FR-SATMON-002:** Satellite-frequency monitoring shall be advisory. Monitor parser, aggregation, RTKLIB usage-state and UI failures shall not stop, alter or delay byte-exact receiver recording, app-to-receiver TX capture, correction input capture, NTRIP routing or session writers.
- **FR-SATMON-003:** Satellite-frequency monitoring shall separate rover-observed, base-present and used-in-selected-solution states. Missing source data shall be represented as unsupported, unavailable or stale rather than inferred from another source.
- **FR-SATMON-004:** Satellite-frequency monitoring shall be enabled only by explicit receiver profile capability metadata. The app shall not mutate receiver command profiles or inject hidden monitor commands when a profile lacks this capability.
- **FR-SATMON-005:** The monitor shall support the explicit display engines `In-device RTK` and `RTKLIB`. Selecting an unavailable engine shall display an unavailable state and shall not silently switch to the other engine.
```

- [ ] **Step 2: Add UI requirements**

Add these entries under dashboard or monitoring UI requirements:

```markdown
### Satellite Monitor UI

- **UI-SATMON-001:** During recording, the dashboard shall expose a compact `Satellites` card that stays with the active main solution engine and does not expose an engine selector.
- **UI-SATMON-002:** The compact `Satellites` card shall group first by constellation and then by frequency. Each frequency cell shall show rover (`R`) and base (`B`) rows with right-aligned `used/visible` counts.
- **UI-SATMON-003:** The advanced satellite monitor screen shall default to normalized frequency band as the primary grouping and show only bands present in the current rover or base monitor snapshot.
- **UI-SATMON-004:** Signal-strength bars shall be optional. When shown, rover and base bars for the same satellite signal shall appear side by side.
- **UI-SATMON-005:** The UI shall label unsupported, unavailable and stale rover, base and solution-usage data distinctly.
- **UI-SATMON-006:** The advanced satellite monitor screen shall include a detailed-view grouping slider switch with `Frequency` and `Constellation` choices. `Frequency` shall be the default. `Constellation` shall group by constellation first and frequency band second.
- **UI-SATMON-007:** The compact `Satellites` card shall render lower-saturation visible boxes and higher-saturation used boxes, with used boxes drawn as a saturated prefix inside the visible total.
- **UI-SATMON-008:** Dashboard layout settings shall provide a binary switch for the approved dark satellite-card palette while leaving the rest of the application theme unchanged.
```

- [ ] **Step 3: Add Android runtime requirements**

Add these entries near foreground-service and capture-isolation requirements:

```markdown
### Satellite Monitor Runtime Isolation

- **AND-SATMON-001:** The foreground recording service shall own satellite-monitor aggregation while recording is active.
- **AND-SATMON-002:** Receiver and correction bytes shall be appended to their session artifacts before being offered to satellite-monitor parsers.
- **AND-SATMON-003:** Satellite-monitor parser work shall use bounded queues or bounded per-source state and shall not run on the byte-append critical path.
- **AND-SATMON-004:** Routine satellite-monitor dashboard updates shall be throttled with other advisory UI state. Recording lifecycle, stop, fatal storage and USB recovery state changes remain immediate.
```

- [ ] **Step 4: Add receiver behavior requirements**

Add these entries near receiver-family behavior requirements:

```markdown
### Satellite Monitor Receiver Profiles

- **RX-SATMON-001:** UM980/N4 satellite monitor profiles shall declare the exact observation and solution-status messages used by the monitor and shall default to `In-device RTK`.
- **RX-SATMON-002:** u-blox M8P satellite monitor profiles shall declare the exact UBX messages used by the monitor and shall default to `In-device RTK` only when receiver-side RTK is configured.
- **RX-SATMON-003:** u-blox M8T satellite monitor profiles shall declare UBX raw observation output and shall default to `RTKLIB`.
- **RX-SATMON-004:** Generic NMEA/RTCM profiles shall not claim full satellite-frequency monitoring unless they provide real per-frequency rover observation evidence. Generic NMEA GSV alone shall not satisfy this requirement.
```

- [ ] **Step 5: Update verification matrix**

Add rows matching the repository's current matrix format. Use these mappings:

```markdown
| FR-SATMON-001 | Unit + manual | `SatelliteMonitorControllerTest.recordingOnlyState`; external USB hardware smoke test | Manual hardware validation required |
| FR-SATMON-002 | Unit + code review | `SatelliteMonitorControllerTest.parserFailureDoesNotThrow`; `RecordingForegroundService` append-before-offer review | Manual long-recording validation required |
| FR-SATMON-003 | Unit | `SatelliteMonitorModelsTest.sourceAvailabilityIsIndependent` | None |
| FR-SATMON-004 | Unit | `ActiveRecordingConfigTest.satelliteMonitorRequiresExplicitProfileCapability` | None |
| FR-SATMON-005 | Unit | `SatelliteMonitorControllerTest.unavailableSelectedEngineDoesNotFallback` | None |
| UI-SATMON-001 | Unit + manual | `SatelliteMonitorDashboardModelsTest.dashboardStateIsUnavailableUntilExplicitMonitorDataIsSupplied` | Compose visual/manual check required |
| UI-SATMON-002 | Unit + manual | `SatelliteMonitorDashboardModelsTest.previewDashboardDataGroupsConstellationFirstAndFrequencySecond` | Compose visual/manual check required |
| UI-SATMON-003 | Unit | `SatelliteMonitorUiModelsTest.omitsBandsAbsentFromRoverAndBase` | None |
| UI-SATMON-004 | Unit + manual | `SatelliteMonitorUiModelsTest.signalStrengthBarsRespectToggle` | Compose visual/manual check required |
| UI-SATMON-005 | Unit | `SatelliteMonitorUiModelsTest.sourceLabelsAreDistinct` | None |
| UI-SATMON-006 | Unit + manual | `SatelliteMonitorUiModelsTest.constellationGroupingNestsFrequencyBands` | Compose visual/manual check required |
| UI-SATMON-007 | Unit + manual | `SatelliteMonitorDashboardModelsTest.boxedBarSegmentsShowUsedAsSaturatedPrefixInsideVisibleTotal` | Compose visual/manual check required |
| UI-SATMON-008 | Unit + manual | `DashboardLayoutModelsTest.storedSatelliteCardThemeIdsParseDefensively` | Compose visual/manual check required |
| AND-SATMON-001 | Unit + code review | `RecordingServiceStateTest.satelliteMonitorResetsOnStop` | None |
| AND-SATMON-002 | Code review + manual | `RecordingForegroundService` append-before-offer review | Manual recording artifact validation required |
| AND-SATMON-003 | Unit | `SatelliteMonitorControllerTest.dropsOldUpdatesWhenBounded` | None |
| AND-SATMON-004 | Unit + manual | `SatelliteMonitorDashboardMapperTest.throttledExtrasDecode` | Manual UI cadence validation required |
| RX-SATMON-001 | Unit + manual | `Um980SatelliteMonitorParserTest` | UM980/N4 hardware validation required |
| RX-SATMON-002 | Unit + manual | `UbloxRawxSatelliteMonitorParserTest` | M8P hardware validation required |
| RX-SATMON-003 | Unit + manual | `ActiveRecordingConfigTest.m8tMonitorDefaultsToRtklib` | M8T + RTKLIB validation required |
| RX-SATMON-004 | Unit | `ActiveRecordingConfigTest.genericProfileDoesNotClaimFullMonitor` | None |
```

- [ ] **Step 6: Update user and architecture documentation**

Add concise operator-facing text to `docs/user-workflows.md`:

```markdown
RtkCollector is currently built for external USB GNSS receivers. The satellite-frequency monitor described here uses data from the connected receiver, NTRIP corrections and, when selected, RTKLIB. It does not read Android internal GNSS satellite status.
```

Add contributor-facing text to `docs/receiver-driver-api.md`:

```markdown
Receiver drivers that support satellite-frequency monitoring must expose that capability through profile metadata and normalized monitor models. Drivers must not send additional receiver commands from parser code. Any command needed for monitor telemetry belongs in an explicit, visible, read-only built-in command profile that users can inspect and copy.
```

Add correction routing text to `docs/ntrip-and-corrections.md`:

```markdown
RTCM MSM parsing for satellite monitoring is advisory and happens after correction bytes are appended to `correction-input.raw`. Malformed or unsupported RTCM frames may affect monitor availability, but must not interrupt correction capture or forwarding to the receiver.
```

- [ ] **Step 7: Review documentation requirement IDs**

Run:

```bash
rg -n "SATMON|Satellite Monitor|satellite-frequency|external USB GNSS" docs/specification docs/user-workflows.md docs/receiver-driver-api.md docs/ntrip-and-corrections.md
```

Expected: every new `FR-SATMON`, `UI-SATMON`, `AND-SATMON` and `RX-SATMON` ID appears in the relevant specification file and in `docs/specification/verification-matrix.md`.

- [ ] **Step 8: Commit documentation requirements**

```bash
git add docs/specification/functional-requirements.md docs/specification/ui-requirements.md docs/specification/android-runtime.md docs/specification/receiver-behaviour.md docs/specification/verification-matrix.md docs/user-workflows.md docs/receiver-driver-api.md docs/ntrip-and-corrections.md
git commit -m "docs: specify satellite frequency monitor"
```

---

### Task 2: Shared Satellite Monitor Model

**Files:**
- Create: `core/quality/src/main/kotlin/org/rtkcollector/core/quality/SatelliteMonitorModels.kt`
- Create: `core/quality/src/test/kotlin/org/rtkcollector/core/quality/SatelliteMonitorModelsTest.kt`

- [ ] **Step 1: Write model tests first**

Create `core/quality/src/test/kotlin/org/rtkcollector/core/quality/SatelliteMonitorModelsTest.kt`:

```kotlin
package org.rtkcollector.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SatelliteMonitorModelsTest {
    @Test
    fun sourceAvailabilityIsIndependent() {
        val key = SatelliteSignalKey(
            constellation = SatelliteConstellation.GPS,
            satelliteId = 5,
            band = SatelliteFrequencyBand.L1,
            signalCode = "C/A",
        )
        val snapshot = SatelliteMonitorSnapshot(
            profileSupported = true,
            selectedEngine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            defaultEngine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            roverStatus = SatelliteSourceStatus.FRESH,
            baseStatus = SatelliteSourceStatus.UNAVAILABLE,
            solutionStatus = SatelliteSourceStatus.UNSUPPORTED,
            roverSignals = mapOf(key to RoverSignalState(cn0DbHz = 42.5, usedByReceiver = false)),
            baseSignals = emptyMap(),
            solutionSignals = emptyMap(),
            updatedAtMillis = 1_000L,
        )

        val summary = snapshot.frequencySummaries.single()

        assertEquals(SatelliteFrequencyBand.L1, summary.band)
        assertEquals(1, summary.roverObserved)
        assertEquals(0, summary.basePresent)
        assertEquals(0, summary.matchedRoverBase)
        assertEquals(null, summary.usedInSolution)
        assertEquals(SatelliteSourceStatus.UNAVAILABLE, snapshot.baseStatus)
        assertEquals(SatelliteSourceStatus.UNSUPPORTED, snapshot.solutionStatus)
    }

    @Test
    fun omitsBandsAbsentFromRoverAndBase() {
        val usedOnlyKey = SatelliteSignalKey(SatelliteConstellation.GALILEO, 11, SatelliteFrequencyBand.L5, "E5a")
        val snapshot = SatelliteMonitorSnapshot(
            profileSupported = true,
            selectedEngine = SatelliteMonitorEngine.RTKLIB,
            defaultEngine = SatelliteMonitorEngine.RTKLIB,
            roverStatus = SatelliteSourceStatus.FRESH,
            baseStatus = SatelliteSourceStatus.FRESH,
            solutionStatus = SatelliteSourceStatus.FRESH,
            roverSignals = emptyMap(),
            baseSignals = emptyMap(),
            solutionSignals = mapOf(usedOnlyKey to SolutionSignalState(used = true, engine = SatelliteMonitorEngine.RTKLIB)),
            updatedAtMillis = 1_000L,
        )

        assertTrue(snapshot.frequencySummaries.isEmpty())
    }

    @Test
    fun selectedEngineAvailabilityIsExplicit() {
        val snapshot = SatelliteMonitorSnapshot(
            profileSupported = true,
            selectedEngine = SatelliteMonitorEngine.RTKLIB,
            defaultEngine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            availableEngines = setOf(SatelliteMonitorEngine.IN_DEVICE_RTK),
            roverStatus = SatelliteSourceStatus.FRESH,
            baseStatus = SatelliteSourceStatus.FRESH,
            solutionStatus = SatelliteSourceStatus.UNAVAILABLE,
            updatedAtMillis = 1_000L,
        )

        assertFalse(snapshot.selectedEngineAvailable)
    }
}
```

- [ ] **Step 2: Run model tests and verify they fail**

Run:

```bash
sh gradlew :core:quality:test --tests org.rtkcollector.core.quality.SatelliteMonitorModelsTest
```

Expected: compilation fails because `SatelliteMonitorSnapshot` and related model types do not exist.

- [ ] **Step 3: Add the monitor model**

Create `core/quality/src/main/kotlin/org/rtkcollector/core/quality/SatelliteMonitorModels.kt`:

```kotlin
package org.rtkcollector.core.quality

enum class SatelliteMonitorEngine {
    IN_DEVICE_RTK,
    RTKLIB,
}

enum class SatelliteConstellation {
    GPS,
    GLONASS,
    GALILEO,
    BEIDOU,
    QZSS,
    SBAS,
    UNKNOWN,
}

enum class SatelliteFrequencyBand {
    L1,
    L2,
    L5,
    L6,
    UNKNOWN,
}

enum class SatelliteSourceStatus {
    UNSUPPORTED,
    UNAVAILABLE,
    FRESH,
    STALE,
}

data class SatelliteSignalKey(
    val constellation: SatelliteConstellation,
    val satelliteId: Int,
    val band: SatelliteFrequencyBand,
    val signalCode: String,
) {
    init {
        require(satelliteId > 0) { "satelliteId must be positive" }
        require(signalCode.isNotBlank()) { "signalCode must not be blank" }
    }
}

data class RoverSignalState(
    val cn0DbHz: Double? = null,
    val usedByReceiver: Boolean? = null,
    val observedAtMillis: Long = 0L,
)

data class BaseSignalState(
    val cn0DbHz: Double? = null,
    val stationId: Int? = null,
    val observedAtMillis: Long = 0L,
)

data class SolutionSignalState(
    val used: Boolean,
    val engine: SatelliteMonitorEngine,
    val observedAtMillis: Long = 0L,
)

data class SatelliteFrequencySummary(
    val band: SatelliteFrequencyBand,
    val roverObserved: Int,
    val basePresent: Int,
    val matchedRoverBase: Int,
    val usedInSolution: Int?,
)

data class SatelliteMonitorSnapshot(
    val profileSupported: Boolean,
    val selectedEngine: SatelliteMonitorEngine,
    val defaultEngine: SatelliteMonitorEngine,
    val availableEngines: Set<SatelliteMonitorEngine> = setOf(defaultEngine),
    val roverStatus: SatelliteSourceStatus = SatelliteSourceStatus.UNSUPPORTED,
    val baseStatus: SatelliteSourceStatus = SatelliteSourceStatus.UNSUPPORTED,
    val solutionStatus: SatelliteSourceStatus = SatelliteSourceStatus.UNSUPPORTED,
    val roverSignals: Map<SatelliteSignalKey, RoverSignalState> = emptyMap(),
    val baseSignals: Map<SatelliteSignalKey, BaseSignalState> = emptyMap(),
    val solutionSignals: Map<SatelliteSignalKey, SolutionSignalState> = emptyMap(),
    val updatedAtMillis: Long = 0L,
) {
    val selectedEngineAvailable: Boolean
        get() = selectedEngine in availableEngines

    val frequencySummaries: List<SatelliteFrequencySummary>
        get() {
            val keysByBand = (roverSignals.keys + baseSignals.keys)
                .groupBy { it.band }
                .toSortedMap(compareBy { it.ordinal })
            return keysByBand.map { (band, keys) ->
                val keySet = keys.toSet()
                val roverKeys = keySet.intersect(roverSignals.keys)
                val baseKeys = keySet.intersect(baseSignals.keys)
                val solutionCount = if (solutionStatus == SatelliteSourceStatus.FRESH || solutionStatus == SatelliteSourceStatus.STALE) {
                    keySet.count { solutionSignals[it]?.used == true }
                } else {
                    null
                }
                SatelliteFrequencySummary(
                    band = band,
                    roverObserved = roverKeys.size,
                    basePresent = baseKeys.size,
                    matchedRoverBase = roverKeys.intersect(baseKeys).size,
                    usedInSolution = solutionCount,
                )
            }
        }

    companion object {
        fun unsupported(selectedEngine: SatelliteMonitorEngine = SatelliteMonitorEngine.IN_DEVICE_RTK): SatelliteMonitorSnapshot =
            SatelliteMonitorSnapshot(
                profileSupported = false,
                selectedEngine = selectedEngine,
                defaultEngine = selectedEngine,
                availableEngines = emptySet(),
            )
    }
}
```

- [ ] **Step 4: Run model tests and verify they pass**

Run:

```bash
sh gradlew :core:quality:test --tests org.rtkcollector.core.quality.SatelliteMonitorModelsTest
```

Expected: tests pass.

- [ ] **Step 5: Commit shared model**

```bash
git add core/quality/src/main/kotlin/org/rtkcollector/core/quality/SatelliteMonitorModels.kt core/quality/src/test/kotlin/org/rtkcollector/core/quality/SatelliteMonitorModelsTest.kt
git commit -m "feat: add satellite monitor model"
```

---

### Task 3: Explicit Profile Capability And Active Config

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStoreMigrations.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt`

- [ ] **Step 1: Write active config tests first**

Add tests to `ActiveRecordingConfigTest`:

```kotlin
@Test
fun satelliteMonitorRequiresExplicitProfileCapability() {
    val config = activeRecordingConfigFor(commandProfile = genericNmeaCommandProfile())

    assertFalse(config.satelliteMonitor.profileSupported)
    assertEquals(SatelliteMonitorEngine.IN_DEVICE_RTK, config.satelliteMonitor.selectedEngine)
}

@Test
fun m8tMonitorDefaultsToRtklib() {
    val config = activeRecordingConfigFor(commandProfile = ubloxM8tRawSatelliteMonitorCommandProfile())

    assertTrue(config.satelliteMonitor.profileSupported)
    assertEquals(SatelliteMonitorEngine.RTKLIB, config.satelliteMonitor.selectedEngine)
    assertTrue(SatelliteMonitorEngine.RTKLIB in config.satelliteMonitor.availableEngines)
}

@Test
fun um980MonitorDefaultsToInDeviceRtk() {
    val config = activeRecordingConfigFor(commandProfile = um980RoverSatelliteMonitorCommandProfile())

    assertTrue(config.satelliteMonitor.profileSupported)
    assertEquals(SatelliteMonitorEngine.IN_DEVICE_RTK, config.satelliteMonitor.selectedEngine)
    assertTrue(SatelliteMonitorEngine.IN_DEVICE_RTK in config.satelliteMonitor.availableEngines)
}
```

Use existing test helpers in `ActiveRecordingConfigTest`; if helper names differ, create local private helpers that construct `CommandProfile` values with the new `satelliteMonitor` property from Step 3.

- [ ] **Step 2: Run active config tests and verify they fail**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.profile.ActiveRecordingConfigTest
```

Expected in this Termux workspace: the test task may fail before execution if Android resource tooling is unavailable. If it fails with an `aapt2` executable-format or resource-processing error, record that exact toolchain blocker and continue with `sh gradlew :app:compileDebugKotlin` after implementation. If it reaches Kotlin compilation, it fails because satellite monitor profile types do not exist.

- [ ] **Step 3: Add profile capability model**

In `ProfileModels.kt`, add:

```kotlin
import org.rtkcollector.core.quality.SatelliteMonitorEngine

data class SatelliteMonitorProfile(
    val supported: Boolean = false,
    val defaultEngine: SatelliteMonitorEngine = SatelliteMonitorEngine.IN_DEVICE_RTK,
    val availableEngines: Set<SatelliteMonitorEngine> = emptySet(),
    val roverObservationSource: SatelliteMonitorObservationSource = SatelliteMonitorObservationSource.NONE,
    val solutionUsageSource: SatelliteMonitorSolutionSource = SatelliteMonitorSolutionSource.NONE,
)

enum class SatelliteMonitorObservationSource {
    NONE,
    UBX_RXM_RAWX,
    UM980_OBSERVATION,
}

enum class SatelliteMonitorSolutionSource {
    NONE,
    RECEIVER_STATUS,
    RTKLIB,
}
```

Add this property to `CommandProfile` with a default that preserves existing profile JSON compatibility:

```kotlin
val satelliteMonitor: SatelliteMonitorProfile = SatelliteMonitorProfile()
```

- [ ] **Step 4: Add active recording monitor config**

In `ActiveRecordingConfig.kt`, add:

```kotlin
import org.rtkcollector.core.quality.SatelliteMonitorEngine

data class ActiveSatelliteMonitorConfig(
    val profileSupported: Boolean,
    val selectedEngine: SatelliteMonitorEngine,
    val defaultEngine: SatelliteMonitorEngine,
    val availableEngines: Set<SatelliteMonitorEngine>,
    val roverObservationSource: SatelliteMonitorObservationSource,
    val solutionUsageSource: SatelliteMonitorSolutionSource,
)
```

Add `val satelliteMonitor: ActiveSatelliteMonitorConfig` to `ActiveRecordingConfig`.

Resolve it from the selected command profile:

```kotlin
private fun CommandProfile.toActiveSatelliteMonitorConfig(): ActiveSatelliteMonitorConfig {
    val monitor = satelliteMonitor
    if (!monitor.supported) {
        return ActiveSatelliteMonitorConfig(
            profileSupported = false,
            selectedEngine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            defaultEngine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            availableEngines = emptySet(),
            roverObservationSource = SatelliteMonitorObservationSource.NONE,
            solutionUsageSource = SatelliteMonitorSolutionSource.NONE,
        )
    }
    require(monitor.defaultEngine in monitor.availableEngines) {
        "Satellite monitor default engine must be one of the available engines."
    }
    return ActiveSatelliteMonitorConfig(
        profileSupported = true,
        selectedEngine = monitor.defaultEngine,
        defaultEngine = monitor.defaultEngine,
        availableEngines = monitor.availableEngines,
        roverObservationSource = monitor.roverObservationSource,
        solutionUsageSource = monitor.solutionUsageSource,
    )
}
```

- [ ] **Step 5: Add explicit built-in profiles**

In `ProfileStores.kt`, add read-only built-in command profiles for:

```kotlin
private val Um980RoverSatelliteMonitorProfile = existingUm980RoverProfile.copy(
    id = "um980-rover-ntrip-satellite-monitor",
    name = "UM980 rover + NTRIP + satellite monitor",
    satelliteMonitor = SatelliteMonitorProfile(
        supported = true,
        defaultEngine = SatelliteMonitorEngine.IN_DEVICE_RTK,
        availableEngines = setOf(SatelliteMonitorEngine.IN_DEVICE_RTK),
        roverObservationSource = SatelliteMonitorObservationSource.UM980_OBSERVATION,
        solutionUsageSource = SatelliteMonitorSolutionSource.RECEIVER_STATUS,
    ),
)

private val UbloxM8pSatelliteMonitorProfile = existingUbloxM8pProfile.copy(
    id = "ublox-m8p-rover-ntrip-satellite-monitor",
    name = "u-blox M8P rover + NTRIP + satellite monitor",
    satelliteMonitor = SatelliteMonitorProfile(
        supported = true,
        defaultEngine = SatelliteMonitorEngine.IN_DEVICE_RTK,
        availableEngines = setOf(SatelliteMonitorEngine.IN_DEVICE_RTK),
        roverObservationSource = SatelliteMonitorObservationSource.UBX_RXM_RAWX,
        solutionUsageSource = SatelliteMonitorSolutionSource.RECEIVER_STATUS,
    ),
)

private val UbloxM8tSatelliteMonitorProfile = existingUbloxM8tRawProfile.copy(
    id = "ublox-m8t-rover-ntrip-satellite-monitor",
    name = "u-blox M8T rover + NTRIP + satellite monitor",
    satelliteMonitor = SatelliteMonitorProfile(
        supported = true,
        defaultEngine = SatelliteMonitorEngine.RTKLIB,
        availableEngines = setOf(SatelliteMonitorEngine.RTKLIB),
        roverObservationSource = SatelliteMonitorObservationSource.UBX_RXM_RAWX,
        solutionUsageSource = SatelliteMonitorSolutionSource.RTKLIB,
    ),
)
```

Replace `existingUm980RoverProfile`, `existingUbloxM8pProfile` and `existingUbloxM8tRawProfile` with the actual existing built-in constants in `ProfileStores.kt`; do not duplicate command scripts by hand when a `.copy(...)` from an existing built-in keeps the profile safer.

- [ ] **Step 6: Preserve built-in sync behavior**

In `ProfileStoreMigrations.kt`, ensure same-id built-ins receive updated satellite monitor metadata while user-created copies remain user-editable:

```kotlin
private fun CommandProfile.sameEditableContentAsBuiltIn(builtIn: CommandProfile): Boolean =
    id == builtIn.id && isProtected
```

Use the existing built-in migration pattern in the file. The important outcome is:

```kotlin
if (stored.sameEditableContentAsBuiltIn(builtIn)) {
    builtIn
} else {
    stored
}
```

- [ ] **Step 7: Run config verification**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: Kotlin compilation passes. If Android resource/unit-test tooling is blocked in this Termux environment, report that separately and do not weaken the source-level verification result.

- [ ] **Step 8: Commit profile capability work**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStoreMigrations.kt app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigTest.kt
git commit -m "feat: add explicit satellite monitor profiles"
```

---

### Task 4: RTCM3 MSM Base Observation Parser

**Files:**
- Create: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/RtcmBitReader.kt`
- Create: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/Rtcm3MsmParser.kt`
- Create: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/RtcmSignalMapping.kt`
- Create: `core/correction/src/test/kotlin/org/rtkcollector/core/correction/Rtcm3MsmParserTest.kt`
- Modify: `core/correction/src/main/kotlin/org/rtkcollector/core/correction/Rtcm3ReferenceStationParser.kt`
- Modify: `core/correction/build.gradle.kts`

- [ ] **Step 1: Add module dependency**

In `core/correction/build.gradle.kts`, add:

```kotlin
dependencies {
    implementation(project(":core:quality"))
}
```

Keep existing dependencies in place.

- [ ] **Step 2: Extract RTCM bit reader**

Move the private `RtcmBitReader` from `Rtcm3ReferenceStationParser.kt` into `RtcmBitReader.kt`:

```kotlin
package org.rtkcollector.core.correction

internal class RtcmBitReader(
    private val bytes: ByteArray,
    private var bitOffset: Int,
    private val bitLength: Int,
) {
    val remaining: Int
        get() = bitLength - (bitOffset - 24)

    fun skip(width: Int) {
        require(width >= 0) { "width must be non-negative" }
        require(remaining >= width) { "RTCM payload is shorter than expected." }
        bitOffset += width
    }

    fun unsigned(width: Int): Long {
        require(width in 1..63) { "width must be 1..63" }
        require(remaining >= width) { "RTCM payload is shorter than expected." }
        var value = 0L
        repeat(width) {
            value = (value shl 1) or bitAt(bitOffset).toLong()
            bitOffset += 1
        }
        return value
    }

    fun signed(width: Int): Long {
        val value = unsigned(width)
        val signBit = 1L shl (width - 1)
        return if ((value and signBit) == 0L) value else value - (1L shl width)
    }

    private fun bitAt(index: Int): Int {
        val byte = bytes[index / 8].toInt() and 0xff
        return (byte ushr (7 - (index % 8))) and 1
    }
}
```

Remove the private class from `Rtcm3ReferenceStationParser.kt`.

- [ ] **Step 3: Write MSM parser tests**

Create `Rtcm3MsmParserTest.kt` with synthetic frames built by helper functions:

```kotlin
package org.rtkcollector.core.correction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteFrequencyBand

class Rtcm3MsmParserTest {
    @Test
    fun parsesGpsMsmSignalMasksIntoFrequencyBands() {
        val frame = RtcmMsmTestFrameBuilder(messageType = 1074, stationId = 42)
            .satellite(3)
            .signal(2)
            .cell(satellite = 3, signal = 2, cn0DbHz = null)
            .build()

        val result = Rtcm3MsmParser.parse(frame)!!

        assertEquals(42, result.stationId)
        assertEquals(1, result.signals.size)
        val signal = result.signals.single()
        assertEquals(SatelliteConstellation.GPS, signal.key.constellation)
        assertEquals(3, signal.key.satelliteId)
        assertEquals(SatelliteFrequencyBand.L1, signal.key.band)
    }

    @Test
    fun parsesGpsMsm7Cn0WhenPresent() {
        val frame = RtcmMsmTestFrameBuilder(messageType = 1077, stationId = 9)
            .satellite(22)
            .signal(15)
            .cell(satellite = 22, signal = 15, cn0DbHz = 47.0)
            .build()

        val result = Rtcm3MsmParser.parse(frame)!!

        assertEquals(47.0, result.signals.single().base.cn0DbHz!!, 0.5)
    }

    @Test
    fun returnsNullForUnsupportedOrMalformedFrame() {
        assertNull(Rtcm3MsmParser.parse(Rtcm3Frame(byteArrayOf(0xd3.toByte(), 0x00, 0x00), crcValid = false)))
    }
}
```

Add a private `RtcmMsmTestFrameBuilder` in the same test file that writes:

```kotlin
private class RtcmMsmTestFrameBuilder(private val messageType: Int, private val stationId: Int) {
    private val satellites = mutableListOf<Int>()
    private val signals = mutableListOf<Int>()
    private val cells = mutableListOf<Cell>()

    fun satellite(prn: Int) = apply { satellites += prn }
    fun signal(signalId: Int) = apply { signals += signalId }
    fun cell(satellite: Int, signal: Int, cn0DbHz: Double?) = apply { cells += Cell(satellite, signal, cn0DbHz) }

    fun build(): Rtcm3Frame {
        val payload = RtcmBitWriter()
            .unsigned(messageType.toLong(), 12)
            .unsigned(stationId.toLong(), 12)
            .unsigned(0, 30)
            .unsigned(0, 1)
            .unsigned(0, 3)
            .unsigned(0, 7)
            .unsigned(0, 2)
            .mask64(satellites)
            .mask32(signals)
            .cellMask(satellites, signals, cells)
            .minimalCellPayload(messageType, cells)
            .toPayload()
        return Rtcm3Frame.fromPayload(payload, crcValid = true)
    }

    private data class Cell(val satellite: Int, val signal: Int, val cn0DbHz: Double?)
}
```

If `Rtcm3Frame` does not have `fromPayload`, add a private test helper that wraps payload bytes using the existing `Rtcm3Frame` constructor shape in this module.

- [ ] **Step 4: Run MSM tests and verify they fail**

Run:

```bash
sh gradlew :core:correction:test --tests org.rtkcollector.core.correction.Rtcm3MsmParserTest
```

Expected: compilation fails because `Rtcm3MsmParser` and mapping helpers do not exist.

- [ ] **Step 5: Add signal mapping**

Create `RtcmSignalMapping.kt`:

```kotlin
package org.rtkcollector.core.correction

import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteFrequencyBand

internal data class RtcmMsmFamily(
    val constellation: SatelliteConstellation,
    val firstMessageType: Int,
    val lastMessageType: Int,
)

internal object RtcmSignalMapping {
    private val families = listOf(
        RtcmMsmFamily(SatelliteConstellation.GPS, 1071, 1077),
        RtcmMsmFamily(SatelliteConstellation.GLONASS, 1081, 1087),
        RtcmMsmFamily(SatelliteConstellation.GALILEO, 1091, 1097),
        RtcmMsmFamily(SatelliteConstellation.QZSS, 1111, 1117),
        RtcmMsmFamily(SatelliteConstellation.BEIDOU, 1121, 1127),
        RtcmMsmFamily(SatelliteConstellation.SBAS, 1101, 1107),
    )

    fun constellationFor(messageType: Int): SatelliteConstellation? =
        families.firstOrNull { messageType in it.firstMessageType..it.lastMessageType }?.constellation

    fun bandFor(constellation: SatelliteConstellation, signalId: Int): SatelliteFrequencyBand =
        when (constellation) {
            SatelliteConstellation.GPS -> when (signalId) {
                2, 3, 4, 30 -> SatelliteFrequencyBand.L1
                8, 9, 10, 15, 16 -> SatelliteFrequencyBand.L2
                22, 23, 24 -> SatelliteFrequencyBand.L5
                else -> SatelliteFrequencyBand.UNKNOWN
            }
            SatelliteConstellation.GLONASS -> when (signalId) {
                2, 3 -> SatelliteFrequencyBand.L1
                8, 9 -> SatelliteFrequencyBand.L2
                else -> SatelliteFrequencyBand.UNKNOWN
            }
            SatelliteConstellation.GALILEO -> when (signalId) {
                2, 3, 4 -> SatelliteFrequencyBand.L1
                8, 9, 10 -> SatelliteFrequencyBand.L5
                14, 15, 16 -> SatelliteFrequencyBand.L6
                else -> SatelliteFrequencyBand.UNKNOWN
            }
            SatelliteConstellation.BEIDOU -> when (signalId) {
                2, 3, 4 -> SatelliteFrequencyBand.L1
                8, 9, 10 -> SatelliteFrequencyBand.L2
                14, 15, 16 -> SatelliteFrequencyBand.L5
                else -> SatelliteFrequencyBand.UNKNOWN
            }
            SatelliteConstellation.QZSS,
            SatelliteConstellation.SBAS,
            SatelliteConstellation.UNKNOWN -> SatelliteFrequencyBand.UNKNOWN
        }

    fun signalCode(signalId: Int): String = "MSM$signalId"
}
```

- [ ] **Step 6: Add MSM parser**

Create `Rtcm3MsmParser.kt`:

```kotlin
package org.rtkcollector.core.correction

import org.rtkcollector.core.quality.BaseSignalState
import org.rtkcollector.core.quality.SatelliteSignalKey

data class Rtcm3MsmObservation(
    val messageType: Int,
    val stationId: Int,
    val signals: List<Rtcm3MsmSignal>,
)

data class Rtcm3MsmSignal(
    val key: SatelliteSignalKey,
    val base: BaseSignalState,
)

object Rtcm3MsmParser {
    fun parse(frame: Rtcm3Frame): Rtcm3MsmObservation? =
        runCatching {
            if (frame.crcValid == false) return null
            val bits = RtcmBitReader(frame.bytes, bitOffset = 24, bitLength = frame.payloadLength * 8)
            val messageType = bits.unsigned(12).toInt()
            val constellation = RtcmSignalMapping.constellationFor(messageType) ?: return null
            val msmKind = messageType % 10
            val stationId = bits.unsigned(12).toInt()
            bits.skip(30)
            bits.skip(1)
            bits.skip(3)
            bits.skip(7)
            bits.skip(2)
            val satellites = readMask(bits, 64)
            val signals = readMask(bits, 32)
            val activeCells = satellites.flatMap { satellite ->
                signals.mapNotNull { signal ->
                    if (bits.unsigned(1) == 1L) satellite to signal else null
                }
            }
            skipMsmSatelliteData(bits, msmKind, satellites.size)
            skipMsmSignalData(bits, msmKind, signals.size)
            activeCells.map { (satellite, signal) ->
                val cn0 = readCellCn0(bits, msmKind)
                Rtcm3MsmSignal(
                    key = SatelliteSignalKey(
                        constellation = constellation,
                        satelliteId = satellite,
                        band = RtcmSignalMapping.bandFor(constellation, signal),
                        signalCode = RtcmSignalMapping.signalCode(signal),
                    ),
                    base = BaseSignalState(
                        cn0DbHz = cn0,
                        stationId = stationId,
                    ),
                )
            }.let { parsedSignals ->
                Rtcm3MsmObservation(
                    messageType = messageType,
                    stationId = stationId,
                    signals = parsedSignals,
                )
            }
        }.getOrNull()

    private fun readMask(bits: RtcmBitReader, width: Int): List<Int> {
        val result = mutableListOf<Int>()
        for (index in 1..width) {
            if (bits.unsigned(1) == 1L) result += index
        }
        return result
    }

    private fun skipMsmSatelliteData(bits: RtcmBitReader, msmKind: Int, satelliteCount: Int) {
        val width = when (msmKind) {
            1, 2, 3 -> 10
            4, 5, 6, 7 -> 18
            else -> 0
        }
        repeat(satelliteCount) { bits.skip(width) }
    }

    private fun skipMsmSignalData(bits: RtcmBitReader, msmKind: Int, signalCount: Int) {
        val width = when (msmKind) {
            1, 2, 3 -> 0
            4, 6 -> 15
            5, 7 -> 20
            else -> 0
        }
        repeat(signalCount) { bits.skip(width) }
    }

    private fun readCellCn0(bits: RtcmBitReader, msmKind: Int): Double? {
        return when (msmKind) {
            4, 5 -> {
                bits.skip(15)
                bits.unsigned(6) * 1.0
            }
            6, 7 -> {
                bits.skip(20)
                bits.unsigned(10) * 0.0625
            }
            else -> null
        }
    }
}
```

When implementing, align skip widths with the RTCM MSM fields actually used by the test fixtures and parser. If a field width needs correction, update the parser and test builder together so tests prove the chosen bit layout.

- [ ] **Step 7: Run correction tests**

Run:

```bash
sh gradlew :core:correction:test --tests org.rtkcollector.core.correction.Rtcm3MsmParserTest --tests org.rtkcollector.core.correction.Rtcm3ReferenceStationParserTest
```

Expected: tests pass and existing reference station parsing remains unchanged.

- [ ] **Step 8: Commit RTCM MSM parser**

```bash
git add core/correction/build.gradle.kts core/correction/src/main/kotlin/org/rtkcollector/core/correction/RtcmBitReader.kt core/correction/src/main/kotlin/org/rtkcollector/core/correction/Rtcm3ReferenceStationParser.kt core/correction/src/main/kotlin/org/rtkcollector/core/correction/Rtcm3MsmParser.kt core/correction/src/main/kotlin/org/rtkcollector/core/correction/RtcmSignalMapping.kt core/correction/src/test/kotlin/org/rtkcollector/core/correction/Rtcm3MsmParserTest.kt
git commit -m "feat: parse rtcm msm satellite signals"
```

---

### Task 5: u-blox Rover Observation Parser

**Files:**
- Modify: `receiver/ublox-m8/build.gradle.kts`
- Create: `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxRawxSatelliteMonitorParser.kt`
- Create: `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxRawxSatelliteMonitorParserTest.kt`

- [ ] **Step 1: Add module dependency**

In `receiver/ublox-m8/build.gradle.kts`, add:

```kotlin
dependencies {
    implementation(project(":core:quality"))
}
```

Keep existing dependencies.

- [ ] **Step 2: Write parser tests first**

Create `UbloxRawxSatelliteMonitorParserTest.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteFrequencyBand

class UbloxRawxSatelliteMonitorParserTest {
    @Test
    fun mapsGpsRawxObservationToL1() {
        val payload = UbloxRawxTestPayloadBuilder()
            .measurement(gnssId = 0, svId = 7, sigId = 0, cn0 = 45)
            .build()

        val signals = UbloxRawxSatelliteMonitorParser.parsePayload(payload, observedAtMillis = 123L)

        val entry = signals.entries.single()
        assertEquals(SatelliteConstellation.GPS, entry.key.constellation)
        assertEquals(7, entry.key.satelliteId)
        assertEquals(SatelliteFrequencyBand.L1, entry.key.band)
        assertEquals(45.0, entry.value.cn0DbHz!!, 0.1)
    }

    @Test
    fun mapsGalileoRawxObservationToL5WhenSignalIdIndicatesE5a() {
        val payload = UbloxRawxTestPayloadBuilder()
            .measurement(gnssId = 2, svId = 12, sigId = 5, cn0 = 39)
            .build()

        val signals = UbloxRawxSatelliteMonitorParser.parsePayload(payload, observedAtMillis = 123L)

        assertEquals(SatelliteFrequencyBand.L5, signals.keys.single().band)
    }

    @Test
    fun returnsNullForTooShortPayload() {
        assertNull(UbloxRawxSatelliteMonitorParser.parsePayload(byteArrayOf(1, 2, 3), observedAtMillis = 1L))
    }
}
```

Add a private `UbloxRawxTestPayloadBuilder` in the same test file. Use the UBX-RXM-RAWX payload layout: header with `rcvTow`, `week`, `leapS`, `numMeas`, `recStat`, `version`, reserved bytes, followed by 32-byte measurement records. Fill only `gnssId`, `svId`, `sigId` and `cno` with non-zero values; leave measurement doubles as zero.

- [ ] **Step 3: Run parser tests and verify they fail**

Run:

```bash
sh gradlew :receiver:ublox-m8:test --tests org.rtkcollector.receiver.ublox.UbloxRawxSatelliteMonitorParserTest
```

Expected: compilation fails because `UbloxRawxSatelliteMonitorParser` does not exist.

- [ ] **Step 4: Add UBX RAWX parser**

Create `UbloxRawxSatelliteMonitorParser.kt`:

```kotlin
package org.rtkcollector.receiver.ublox

import org.rtkcollector.core.quality.RoverSignalState
import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteFrequencyBand
import org.rtkcollector.core.quality.SatelliteSignalKey

object UbloxRawxSatelliteMonitorParser {
    private const val HeaderLength = 16
    private const val MeasurementLength = 32

    fun parsePayload(payload: ByteArray, observedAtMillis: Long): Map<SatelliteSignalKey, RoverSignalState>? =
        runCatching {
            if (payload.size < HeaderLength) return null
            val numMeas = payload[11].toInt() and 0xff
            val requiredLength = HeaderLength + numMeas * MeasurementLength
            if (payload.size < requiredLength) return null
            buildMap {
                repeat(numMeas) { index ->
                    val offset = HeaderLength + index * MeasurementLength
                    val gnssId = payload[offset + 20].toInt() and 0xff
                    val svId = payload[offset + 21].toInt() and 0xff
                    val sigId = payload[offset + 22].toInt() and 0xff
                    val cn0 = payload[offset + 25].toInt() and 0xff
                    val constellation = constellationFor(gnssId)
                    val band = bandFor(gnssId, sigId)
                    if (constellation != SatelliteConstellation.UNKNOWN && svId > 0) {
                        put(
                            SatelliteSignalKey(
                                constellation = constellation,
                                satelliteId = svId,
                                band = band,
                                signalCode = "UBX-$sigId",
                            ),
                            RoverSignalState(
                                cn0DbHz = cn0.toDouble(),
                                observedAtMillis = observedAtMillis,
                            ),
                        )
                    }
                }
            }
        }.getOrNull()

    private fun constellationFor(gnssId: Int): SatelliteConstellation =
        when (gnssId) {
            0 -> SatelliteConstellation.GPS
            1 -> SatelliteConstellation.SBAS
            2 -> SatelliteConstellation.GALILEO
            3 -> SatelliteConstellation.BEIDOU
            5 -> SatelliteConstellation.QZSS
            6 -> SatelliteConstellation.GLONASS
            else -> SatelliteConstellation.UNKNOWN
        }

    private fun bandFor(gnssId: Int, sigId: Int): SatelliteFrequencyBand =
        when (gnssId) {
            0 -> if (sigId in setOf(3, 4)) SatelliteFrequencyBand.L2 else SatelliteFrequencyBand.L1
            2 -> if (sigId in setOf(5, 6, 7)) SatelliteFrequencyBand.L5 else SatelliteFrequencyBand.L1
            3 -> if (sigId in setOf(2, 3)) SatelliteFrequencyBand.L2 else SatelliteFrequencyBand.L1
            6 -> if (sigId in setOf(2, 3)) SatelliteFrequencyBand.L2 else SatelliteFrequencyBand.L1
            else -> SatelliteFrequencyBand.UNKNOWN
        }
}
```

The signal-ID mapping above is the first tested mapping. During implementation, verify it against the u-blox M8 Interface Description before field use and update the tests with fixture names that cite the document section in comments.

- [ ] **Step 5: Integrate parser with existing UBX stream parser**

Locate the existing callback point in `UbloxStreamParser.kt` where UBX message class/id frames are emitted. Add a helper method or event branch for class `0x02`, id `0x15` (`RXM-RAWX`) that passes only the payload to `UbloxRawxSatelliteMonitorParser.parsePayload(...)`.

Use this shape:

```kotlin
if (messageClass == 0x02 && messageId == 0x15) {
    val monitorSignals = UbloxRawxSatelliteMonitorParser.parsePayload(payload, observedAtMillis = nowMillis)
    if (monitorSignals != null) {
        listener.onRoverSatelliteSignals(monitorSignals)
    }
}
```

If the existing parser does not use listeners, expose a small return object from the parser step that carries optional rover monitor signals next to existing telemetry.

- [ ] **Step 6: Run u-blox tests**

Run:

```bash
sh gradlew :receiver:ublox-m8:test --tests org.rtkcollector.receiver.ublox.UbloxRawxSatelliteMonitorParserTest
```

Expected: tests pass.

- [ ] **Step 7: Commit u-blox parser**

```bash
git add receiver/ublox-m8/build.gradle.kts receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxRawxSatelliteMonitorParser.kt receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxRawxSatelliteMonitorParserTest.kt
git commit -m "feat: map ublox rawx satellite signals"
```

---

### Task 6: UM980 Monitor Parser And Explicit Limits

**Files:**
- Modify: `receiver/unicore-n4/build.gradle.kts`
- Create: `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980SatelliteMonitorParser.kt`
- Create: `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980SatelliteMonitorParserTest.kt`
- Modify: `docs/receiver-driver-api.md`

- [ ] **Step 1: Add module dependency**

In `receiver/unicore-n4/build.gradle.kts`, add:

```kotlin
dependencies {
    implementation(project(":core:quality"))
}
```

Keep existing dependencies.

- [ ] **Step 2: Write UM980 parser tests with documented fixture scope**

Create `Um980SatelliteMonitorParserTest.kt`:

```kotlin
package org.rtkcollector.receiver.unicore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteFrequencyBand

class Um980SatelliteMonitorParserTest {
    @Test
    fun returnsEmptyForUnknownBinaryMessage() {
        val signals = Um980SatelliteMonitorParser.parseObservationMessage(
            messageId = 0,
            payload = byteArrayOf(1, 2, 3),
            observedAtMillis = 1L,
        )

        assertTrue(signals.isEmpty())
    }

    @Test
    fun mapsDocumentedObservationFixtureToSignalKeys() {
        val fixture = Um980ObservationFixture.singleGpsL1Signal()

        val signals = Um980SatelliteMonitorParser.parseObservationMessage(
            messageId = fixture.messageId,
            payload = fixture.payload,
            observedAtMillis = 100L,
        )

        val entry = signals.entries.single()
        assertEquals(SatelliteConstellation.GPS, entry.key.constellation)
        assertEquals(SatelliteFrequencyBand.L1, entry.key.band)
        assertEquals(41.0, entry.value.cn0DbHz!!, 0.5)
    }
}
```

Add `Um980ObservationFixture.singleGpsL1Signal()` only from a known local sample or documented UM980 observation payload. If no documented payload is available in the workspace, keep only `returnsEmptyForUnknownBinaryMessage`, document the manual gap in `verification-matrix.md`, and do not claim UM980 per-signal support beyond aggregate availability.

- [ ] **Step 3: Run UM980 tests and verify expected failure**

Run:

```bash
sh gradlew :receiver:unicore-n4:test --tests org.rtkcollector.receiver.unicore.Um980SatelliteMonitorParserTest
```

Expected: compilation fails because `Um980SatelliteMonitorParser` does not exist.

- [ ] **Step 4: Add parser scaffold that is explicit about supported messages**

Create `Um980SatelliteMonitorParser.kt`:

```kotlin
package org.rtkcollector.receiver.unicore

import org.rtkcollector.core.quality.RoverSignalState
import org.rtkcollector.core.quality.SatelliteSignalKey

object Um980SatelliteMonitorParser {
    fun parseObservationMessage(
        messageId: Int,
        payload: ByteArray,
        observedAtMillis: Long,
    ): Map<SatelliteSignalKey, RoverSignalState> =
        when (messageId) {
            SupportedObservationMessageId -> parseSupportedObservationPayload(payload, observedAtMillis)
            else -> emptyMap()
        }

    private const val SupportedObservationMessageId = -1

    private fun parseSupportedObservationPayload(
        payload: ByteArray,
        observedAtMillis: Long,
    ): Map<SatelliteSignalKey, RoverSignalState> {
        return emptyMap()
    }
}
```

Replace `SupportedObservationMessageId = -1` and `parseSupportedObservationPayload(...)` only when the implementation has a documented UM980 observation message ID and byte layout. Until then, the profile may advertise monitor capability as rover source unavailable, and the UI must show that state.

- [ ] **Step 5: Document UM980 limitation honestly**

In `docs/receiver-driver-api.md`, add:

```markdown
UM980/N4 satellite-frequency monitoring must use documented observation messages enabled by an explicit command profile. Aggregate BESTNAV, RTKSTATUS, STADOP or RTCMSTATUS satellite counts are not per-frequency rover observation evidence and must not be displayed as full satellite-frequency support.
```

- [ ] **Step 6: Run UM980 module tests**

Run:

```bash
sh gradlew :receiver:unicore-n4:test --tests org.rtkcollector.receiver.unicore.Um980SatelliteMonitorParserTest
```

Expected: tests pass for scaffold behavior. If documented fixtures were added, those fixture tests also pass.

- [ ] **Step 7: Commit UM980 monitor parser boundary**

```bash
git add receiver/unicore-n4/build.gradle.kts receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980SatelliteMonitorParser.kt receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980SatelliteMonitorParserTest.kt docs/receiver-driver-api.md
git commit -m "feat: define um980 satellite monitor boundary"
```

---

### Task 7: RTKLIB Per-Signal Usage Contract

**Files:**
- Modify: `core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibOutputModels.kt`
- Create or modify: `core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibOutputModelsTest.kt`

- [ ] **Step 1: Write RTKLIB model tests first**

Add `RtklibOutputModelsTest.kt`:

```kotlin
package org.rtkcollector.core.rtklib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteFrequencyBand
import org.rtkcollector.core.quality.SatelliteMonitorEngine
import org.rtkcollector.core.quality.SatelliteSignalKey

class RtklibOutputModelsTest {
    @Test
    fun solutionSignalUsageDefaultsToEmpty() {
        val solution = RtklibSolutionSnapshot(
            fixClass = RtklibFixClass.RTK_FLOAT,
            timestampMillis = 100L,
        )

        assertTrue(solution.signalUsage.isEmpty())
    }

    @Test
    fun signalUsageCarriesPerSignalUsedState() {
        val key = SatelliteSignalKey(SatelliteConstellation.GPS, 3, SatelliteFrequencyBand.L1, "L1")
        val usage = RtklibSignalUsage(key = key, used = true)

        assertEquals(SatelliteMonitorEngine.RTKLIB, usage.engine)
        assertEquals(true, usage.used)
    }
}
```

- [ ] **Step 2: Run RTKLIB tests and verify they fail**

Run:

```bash
sh gradlew :core:rtklib:test --tests org.rtkcollector.core.rtklib.RtklibOutputModelsTest
```

Expected: compilation fails because `RtklibSignalUsage` and `signalUsage` do not exist.

- [ ] **Step 3: Add optional per-signal usage model**

Modify `RtklibOutputModels.kt`:

```kotlin
import org.rtkcollector.core.quality.SatelliteMonitorEngine
import org.rtkcollector.core.quality.SatelliteSignalKey

data class RtklibSignalUsage(
    val key: SatelliteSignalKey,
    val used: Boolean,
    val engine: SatelliteMonitorEngine = SatelliteMonitorEngine.RTKLIB,
)
```

Add this property to `RtklibSolutionSnapshot`:

```kotlin
val signalUsage: List<RtklibSignalUsage> = emptyList(),
```

- [ ] **Step 4: Run RTKLIB tests**

Run:

```bash
sh gradlew :core:rtklib:test --tests org.rtkcollector.core.rtklib.RtklibOutputModelsTest
```

Expected: tests pass.

- [ ] **Step 5: Document native extraction boundary**

Add a short comment near `RtklibSignalUsage`:

```kotlin
// Optional bridge contract for native RTKLIB per-satellite/per-signal status.
// Empty means the selected RTKLIB route has not exposed usage detail.
```

Do not change native RTKLIB worker behavior in this task unless the upstream RTKLIB status API and nullability contracts have already been reviewed.

- [ ] **Step 6: Commit RTKLIB model contract**

```bash
git add core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibOutputModels.kt core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibOutputModelsTest.kt
git commit -m "feat: expose rtklib signal usage contract"
```

---

### Task 8: Bounded App Aggregator And Service Integration

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/recording/SatelliteMonitorController.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/recording/SatelliteMonitorControllerTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- Modify: `app/src/test/kotlin/org/rtkcollector/app/recording/RecordingServiceStateTest.kt`

- [ ] **Step 1: Write controller tests first**

Create `SatelliteMonitorControllerTest.kt`:

```kotlin
package org.rtkcollector.app.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rtkcollector.core.quality.BaseSignalState
import org.rtkcollector.core.quality.RoverSignalState
import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteFrequencyBand
import org.rtkcollector.core.quality.SatelliteMonitorEngine
import org.rtkcollector.core.quality.SatelliteSignalKey
import org.rtkcollector.core.quality.SatelliteSourceStatus

class SatelliteMonitorControllerTest {
    private val key = SatelliteSignalKey(SatelliteConstellation.GPS, 7, SatelliteFrequencyBand.L1, "L1")

    @Test
    fun unsupportedProfilePublishesUnsupportedSnapshot() {
        val controller = SatelliteMonitorController(ActiveSatelliteMonitorConfig.unsupported())

        val snapshot = controller.snapshot(nowMillis = 1_000L)

        assertFalse(snapshot.profileSupported)
        assertEquals(SatelliteSourceStatus.UNSUPPORTED, snapshot.roverStatus)
    }

    @Test
    fun combinesRoverAndBaseWithoutRequiringSolutionUsage() {
        val controller = SatelliteMonitorController(ActiveSatelliteMonitorConfig.testSupported(SatelliteMonitorEngine.RTKLIB))

        controller.offerRoverSignals(mapOf(key to RoverSignalState(cn0DbHz = 44.0)), nowMillis = 1_000L)
        controller.offerBaseSignals(mapOf(key to BaseSignalState(cn0DbHz = 41.0)), nowMillis = 1_100L)

        val summary = controller.snapshot(nowMillis = 1_200L).frequencySummaries.single()

        assertEquals(1, summary.roverObserved)
        assertEquals(1, summary.basePresent)
        assertEquals(1, summary.matchedRoverBase)
        assertEquals(null, summary.usedInSolution)
    }

    @Test
    fun unavailableSelectedEngineDoesNotFallback() {
        val controller = SatelliteMonitorController(
            ActiveSatelliteMonitorConfig.testSupported(
                selectedEngine = SatelliteMonitorEngine.RTKLIB,
                availableEngines = setOf(SatelliteMonitorEngine.IN_DEVICE_RTK),
            ),
        )

        val snapshot = controller.snapshot(nowMillis = 100L)

        assertFalse(snapshot.selectedEngineAvailable)
        assertEquals(SatelliteMonitorEngine.RTKLIB, snapshot.selectedEngine)
    }

    @Test
    fun staleBaseDataFadesToStaleStatus() {
        val controller = SatelliteMonitorController(ActiveSatelliteMonitorConfig.testSupported(SatelliteMonitorEngine.IN_DEVICE_RTK))
        controller.offerBaseSignals(mapOf(key to BaseSignalState()), nowMillis = 1_000L)

        val snapshot = controller.snapshot(nowMillis = 31_000L)

        assertEquals(SatelliteSourceStatus.STALE, snapshot.baseStatus)
    }
}
```

Add private test helpers in the test file if `ActiveSatelliteMonitorConfig` does not yet have `unsupported()` or `testSupported(...)`.

- [ ] **Step 2: Run controller tests and verify they fail**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.recording.SatelliteMonitorControllerTest
```

Expected: either Android unit-test tooling fails with the known local resource/toolchain blocker, or Kotlin compilation fails because `SatelliteMonitorController` does not exist.

- [ ] **Step 3: Add controller implementation**

Create `SatelliteMonitorController.kt`:

```kotlin
package org.rtkcollector.app.recording

import org.rtkcollector.app.profile.ActiveSatelliteMonitorConfig
import org.rtkcollector.core.quality.BaseSignalState
import org.rtkcollector.core.quality.RoverSignalState
import org.rtkcollector.core.quality.SatelliteMonitorSnapshot
import org.rtkcollector.core.quality.SatelliteSignalKey
import org.rtkcollector.core.quality.SatelliteSourceStatus
import org.rtkcollector.core.quality.SolutionSignalState

internal class SatelliteMonitorController(
    private val config: ActiveSatelliteMonitorConfig,
    private val staleAfterMillis: Long = 30_000L,
) {
    private var roverSignals: Map<SatelliteSignalKey, RoverSignalState> = emptyMap()
    private var baseSignals: Map<SatelliteSignalKey, BaseSignalState> = emptyMap()
    private var solutionSignals: Map<SatelliteSignalKey, SolutionSignalState> = emptyMap()
    private var roverUpdatedAtMillis: Long? = null
    private var baseUpdatedAtMillis: Long? = null
    private var solutionUpdatedAtMillis: Long? = null

    fun offerRoverSignals(signals: Map<SatelliteSignalKey, RoverSignalState>, nowMillis: Long) {
        roverSignals = signals
        roverUpdatedAtMillis = nowMillis
    }

    fun offerBaseSignals(signals: Map<SatelliteSignalKey, BaseSignalState>, nowMillis: Long) {
        baseSignals = signals
        baseUpdatedAtMillis = nowMillis
    }

    fun offerSolutionSignals(signals: Map<SatelliteSignalKey, SolutionSignalState>, nowMillis: Long) {
        solutionSignals = signals
        solutionUpdatedAtMillis = nowMillis
    }

    fun snapshot(nowMillis: Long): SatelliteMonitorSnapshot {
        if (!config.profileSupported) {
            return SatelliteMonitorSnapshot.unsupported(config.selectedEngine)
        }
        return SatelliteMonitorSnapshot(
            profileSupported = true,
            selectedEngine = config.selectedEngine,
            defaultEngine = config.defaultEngine,
            availableEngines = config.availableEngines,
            roverStatus = sourceStatus(roverUpdatedAtMillis, nowMillis),
            baseStatus = sourceStatus(baseUpdatedAtMillis, nowMillis),
            solutionStatus = sourceStatus(solutionUpdatedAtMillis, nowMillis),
            roverSignals = roverSignals,
            baseSignals = baseSignals,
            solutionSignals = solutionSignals,
            updatedAtMillis = nowMillis,
        )
    }

    private fun sourceStatus(updatedAtMillis: Long?, nowMillis: Long): SatelliteSourceStatus =
        when {
            updatedAtMillis == null -> SatelliteSourceStatus.UNAVAILABLE
            nowMillis - updatedAtMillis > staleAfterMillis -> SatelliteSourceStatus.STALE
            else -> SatelliteSourceStatus.FRESH
        }
}
```

- [ ] **Step 4: Add state fields and reset behavior**

In `RecordingServiceState.kt`, add:

```kotlin
val satelliteMonitorSnapshot: org.rtkcollector.core.quality.SatelliteMonitorSnapshot =
    org.rtkcollector.core.quality.SatelliteMonitorSnapshot.unsupported(),
```

Ensure stop/reset paths copy it back to `SatelliteMonitorSnapshot.unsupported()`.

- [ ] **Step 5: Integrate after byte persistence**

In `RecordingForegroundService.kt`, find the receiver-byte path that appends `receiver-rx.raw`. Immediately after the append succeeds and after existing raw fanout setup, offer bytes to the receiver-specific monitor parser through `runCatching`:

```kotlin
recorder.appendReceiverRxBytes(bytes)
satelliteMonitorController?.let { controller ->
    runCatching {
        roverSatelliteSignalDecoder?.decode(bytes, nowMillis = clock.millis())
            ?.takeIf { it.isNotEmpty() }
            ?.let { controller.offerRoverSignals(it, clock.millis()) }
    }.onFailure {
        serviceState = serviceState.copy(
            satelliteMonitorSnapshot = controller.snapshot(clock.millis()),
        )
    }
}
```

In `processNtripCorrectionBytes(...)`, keep the existing order:

```kotlin
recorder.appendCorrectionInputBytes(bytes)
rtklibWorker?.offerCorrectionBytes(bytes)
val frames = rtcmExtractor.accept(bytes)
```

After frames are extracted, offer MSM observations:

```kotlin
val baseSignals = frames
    .mapNotNull { Rtcm3MsmParser.parse(it) }
    .flatMap { it.signals }
    .associate { it.key to it.base.copy(observedAtMillis = nowMillis) }
if (baseSignals.isNotEmpty()) {
    satelliteMonitorController?.offerBaseSignals(baseSignals, nowMillis)
}
```

Wrap only the monitor parse/offer block with `runCatching`; do not include `appendCorrectionInputBytes`, receiver TX or RTKLIB queue calls inside that monitor failure boundary.

- [ ] **Step 6: Add RTKLIB solution usage feed**

Where `rtklibWorker?.snapshot()` is read for broadcast state, map optional signal usage:

```kotlin
rtklibSnapshot.latestSolution?.signalUsage
    ?.map { it.key to SolutionSignalState(used = it.used, engine = SatelliteMonitorEngine.RTKLIB) }
    ?.toMap()
    ?.takeIf { it.isNotEmpty() }
    ?.let { satelliteMonitorController?.offerSolutionSignals(it, nowMillis) }
```

If the list is empty, leave solution source unavailable rather than inventing not-used states.

- [ ] **Step 7: Run service compile verification**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: Kotlin compilation passes.

- [ ] **Step 8: Commit service integration**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/recording/SatelliteMonitorController.kt app/src/test/kotlin/org/rtkcollector/app/recording/SatelliteMonitorControllerTest.kt app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt app/src/test/kotlin/org/rtkcollector/app/recording/RecordingServiceStateTest.kt
git commit -m "feat: aggregate satellite monitor state"
```

---

### Task 9: Dashboard Card And Advanced Screen

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/SatelliteMonitorDashboardModels.kt`
- Create: `app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/SatelliteMonitorDashboardModelsTest.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/common/HelpOverlay.kt`

Current UI slice: implement the compact main-card visual exactly as approved. The
full live parser, service mapper and separate advanced screen remain later
work. The compact card is intentionally inline in `HomeDashboard.kt` to follow
existing dashboard structure and avoid introducing a second dashboard component
boundary before live data mapping exists.

- [ ] **Step 1: Write UI model tests first**

Create `SatelliteMonitorUiModelsTest.kt`:

```kotlin
package org.rtkcollector.app.ui.satellite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rtkcollector.core.quality.BaseSignalState
import org.rtkcollector.core.quality.RoverSignalState
import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteFrequencyBand
import org.rtkcollector.core.quality.SatelliteMonitorEngine
import org.rtkcollector.core.quality.SatelliteMonitorSnapshot
import org.rtkcollector.core.quality.SatelliteSignalKey
import org.rtkcollector.core.quality.SatelliteSourceStatus

class SatelliteMonitorUiModelsTest {
    private val key = SatelliteSignalKey(SatelliteConstellation.GPS, 3, SatelliteFrequencyBand.L1, "L1")
    private val galileoKey = SatelliteSignalKey(SatelliteConstellation.GALILEO, 12, SatelliteFrequencyBand.L5, "E5a")

    @Test
    fun defaultsToFrequencyPrimaryGrouping() {
        val snapshot = SatelliteMonitorSnapshot(
            profileSupported = true,
            selectedEngine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            defaultEngine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            roverStatus = SatelliteSourceStatus.FRESH,
            baseStatus = SatelliteSourceStatus.FRESH,
            roverSignals = mapOf(
                key to RoverSignalState(cn0DbHz = 44.0),
                galileoKey to RoverSignalState(cn0DbHz = 38.0),
            ),
            baseSignals = mapOf(key to BaseSignalState(cn0DbHz = 40.0)),
        )

        val state = snapshot.toSatelliteMonitorUiState(showSignalBars = true)

        assertEquals(SatelliteMonitorGroupingMode.FREQUENCY, state.groupingMode)
        assertEquals(listOf("L1", "L5"), state.groups.map { it.primaryLabel })
        assertEquals("G03", state.groups.first().sections.single().rows.single().satelliteLabel)
    }

    @Test
    fun constellationGroupingNestsFrequencyBands() {
        val snapshot = SatelliteMonitorSnapshot(
            profileSupported = true,
            selectedEngine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            defaultEngine = SatelliteMonitorEngine.IN_DEVICE_RTK,
            roverStatus = SatelliteSourceStatus.FRESH,
            baseStatus = SatelliteSourceStatus.FRESH,
            roverSignals = mapOf(
                key to RoverSignalState(cn0DbHz = 44.0),
                galileoKey to RoverSignalState(cn0DbHz = 38.0),
            ),
            baseSignals = mapOf(key to BaseSignalState(cn0DbHz = 40.0)),
        )

        val state = snapshot.toSatelliteMonitorUiState(
            showSignalBars = true,
            groupingMode = SatelliteMonitorGroupingMode.CONSTELLATION,
        )

        assertEquals(SatelliteMonitorGroupingMode.CONSTELLATION, state.groupingMode)
        assertEquals(listOf("GPS", "Galileo"), state.groups.map { it.primaryLabel })
        assertEquals("L1", state.groups.first().sections.single().secondaryLabel)
        assertEquals("L5", state.groups.last().sections.single().secondaryLabel)
    }

    @Test
    fun signalStrengthBarsRespectToggle() {
        val state = SatelliteMonitorSnapshot.unsupported().toSatelliteMonitorUiState(showSignalBars = false)

        assertFalse(state.showSignalBars)
    }

    @Test
    fun unsupportedProfileShowsRequirementMessage() {
        val state = SatelliteMonitorSnapshot.unsupported().toSatelliteMonitorUiState(showSignalBars = true)

        assertTrue(state.message!!.contains("requires a satellite-monitoring receiver profile"))
    }
}
```

- [ ] **Step 2: Run UI model tests and verify they fail**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.satellite.SatelliteMonitorUiModelsTest
```

Expected: either the known local Android unit-test tooling blocker appears, or Kotlin compilation fails because UI model types do not exist.

- [ ] **Step 3: Add compact dashboard model state**

In `SatelliteMonitorDashboardModels.kt`, add:

```kotlin
data class SatelliteMonitorDashboardState(
    val engineLabel: String,
    val sources: SatelliteMonitorSourceStatuses,
    val constellations: List<SatelliteMonitorConstellationGroup>,
    val message: String?,
)
```

Add `val satelliteMonitor: SatelliteMonitorDashboardState =
SatelliteMonitorDashboardState.unavailable()` to `DashboardState`, `planned(...)`,
`running(...)` and `withPlannedConfiguration(...)`.

- [ ] **Step 4: Add UI model mapping**

Create `SatelliteMonitorUiModels.kt`:

```kotlin
package org.rtkcollector.app.ui.satellite

import org.rtkcollector.core.quality.SatelliteConstellation
import org.rtkcollector.core.quality.SatelliteFrequencyBand
import org.rtkcollector.core.quality.SatelliteMonitorSnapshot
import org.rtkcollector.core.quality.SatelliteSignalKey

enum class SatelliteMonitorGroupingMode {
    FREQUENCY,
    CONSTELLATION,
}

data class SatelliteMonitorUiState(
    val showSignalBars: Boolean,
    val groupingMode: SatelliteMonitorGroupingMode,
    val message: String?,
    val groups: List<SatellitePrimaryGroupUi>,
)

data class SatellitePrimaryGroupUi(
    val primaryLabel: String,
    val sections: List<SatelliteSecondaryGroupUi>,
)

data class SatelliteSecondaryGroupUi(
    val secondaryLabel: String,
    val rows: List<SatelliteSignalRowUi>,
)

data class SatelliteSignalRowUi(
    val satelliteLabel: String,
    val signalCode: String,
    val roverCn0DbHz: Double?,
    val baseCn0DbHz: Double?,
    val used: Boolean?,
)

fun SatelliteMonitorSnapshot.toSatelliteMonitorUiState(
    showSignalBars: Boolean,
    groupingMode: SatelliteMonitorGroupingMode = SatelliteMonitorGroupingMode.FREQUENCY,
): SatelliteMonitorUiState {
    if (!profileSupported) {
        return SatelliteMonitorUiState(
            showSignalBars = showSignalBars,
            groupingMode = groupingMode,
            message = "Satellite monitor requires a satellite-monitoring receiver profile",
            groups = emptyList(),
        )
    }
    val keys = (roverSignals.keys + baseSignals.keys).toSet()
    val groups = when (groupingMode) {
        SatelliteMonitorGroupingMode.FREQUENCY -> keys
            .groupBy { it.band }
            .toSortedMap(compareBy { it.ordinal })
            .map { (band, bandKeys) ->
                SatellitePrimaryGroupUi(
                    primaryLabel = band.label,
                    sections = listOf(
                        SatelliteSecondaryGroupUi(
                            secondaryLabel = "Signals",
                            rows = bandKeys.sortedSignalKeys().map(::toRow),
                        ),
                    ),
                )
            }
        SatelliteMonitorGroupingMode.CONSTELLATION -> keys
            .groupBy { it.constellation }
            .toSortedMap(compareBy { it.ordinal })
            .map { (constellation, constellationKeys) ->
                SatellitePrimaryGroupUi(
                    primaryLabel = constellation.label,
                    sections = constellationKeys
                        .groupBy { it.band }
                        .toSortedMap(compareBy { it.ordinal })
                        .map { (band, bandKeys) ->
                            SatelliteSecondaryGroupUi(
                                secondaryLabel = band.label,
                                rows = bandKeys.sortedSignalKeys().map(::toRow),
                            )
                        },
                )
            }
    }
    return SatelliteMonitorUiState(
        showSignalBars = showSignalBars,
        groupingMode = groupingMode,
        message = null,
        groups = groups,
    )
}

private fun Iterable<SatelliteSignalKey>.sortedSignalKeys(): List<SatelliteSignalKey> =
    sortedWith(compareBy<SatelliteSignalKey> { it.constellation.ordinal }.thenBy { it.satelliteId }.thenBy { it.signalCode })

private fun SatelliteMonitorSnapshot.toRow(key: SatelliteSignalKey): SatelliteSignalRowUi =
    SatelliteSignalRowUi(
        satelliteLabel = "${key.constellation.name.first()}%02d".format(key.satelliteId),
        signalCode = key.signalCode,
        roverCn0DbHz = roverSignals[key]?.cn0DbHz,
        baseCn0DbHz = baseSignals[key]?.cn0DbHz,
        used = solutionSignals[key]?.used,
    )

private val SatelliteConstellation.label: String
    get() = when (this) {
        SatelliteConstellation.GPS -> "GPS"
        SatelliteConstellation.GLONASS -> "GLONASS"
        SatelliteConstellation.GALILEO -> "Galileo"
        SatelliteConstellation.BEIDOU -> "BeiDou"
        SatelliteConstellation.QZSS -> "QZSS"
        SatelliteConstellation.SBAS -> "SBAS"
        SatelliteConstellation.UNKNOWN -> "Unknown"
    }

private val SatelliteFrequencyBand.label: String
    get() = name
```

- [ ] **Step 5: Add detailed grouping mode control to the screen**

In the advanced-screen state holder in `MainActivity.kt`, keep this UI-only state outside recording/profile configuration:

```kotlin
var satelliteMonitorGroupingMode by rememberSaveable {
    mutableStateOf(SatelliteMonitorGroupingMode.FREQUENCY)
}
```

Pass it into `toSatelliteMonitorUiState(...)`:

```kotlin
val satelliteMonitorUiState = dashboardState.satelliteMonitor.snapshot.toSatelliteMonitorUiState(
    showSignalBars = showSatelliteSignalBars,
    groupingMode = satelliteMonitorGroupingMode,
)
```

This grouping mode changes only detailed-view presentation. It must not send receiver commands, change the monitor engine, reconnect NTRIP or mutate recording state.

- [ ] **Step 6: Add service mapper tests**

Create `SatelliteMonitorDashboardMapperTest.kt` with the repository's existing intent-extra test style. Cover:

```kotlin
@Test
fun compactSummaryByBand() {
    val intent = recordingStateIntentWithSatelliteMonitor(
        profileSupported = true,
        selectedEngine = "RTKLIB",
        summaries = "L1:3:2:2:1;L5:1:1:1:",
    )

    val state = DashboardServiceMapper.map(intent)

    assertEquals(2, state.satelliteMonitor.summaries.size)
    assertEquals(SatelliteMonitorEngine.RTKLIB, state.satelliteMonitor.selectedEngine)
}
```

Use the actual `DashboardServiceMapper` API name in the file.

- [ ] **Step 7: Add compact card composable**

Add `SatelliteMonitorCard` inside `HomeDashboard.kt` with the approved compact
visual:

- render only while recording;
- show the active main engine as display-only text;
- do not expose a main-card engine selector;
- keep constellation as the primary grouping and frequency as the secondary grouping;
- for each frequency, show rover (`R`) and base (`B`) rows;
- draw boxed bars where muted boxes represent visible satellites and saturated
  boxes are the used prefix inside the visible total;
- right-align `used/visible` counts;
- show `R`, `B` and `S` freshness dots with explicit tiny labels;
- show a top-right `(i)` help affordance that opens the satellite-monitor help topic.

- [ ] **Step 8: Add advanced screen**

Create `SatelliteMonitorScreen.kt`:

```kotlin
package org.rtkcollector.app.ui.satellite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun SatelliteMonitorScreen(
    state: SatelliteMonitorUiState,
    onGroupingModeChanged: (SatelliteMonitorGroupingMode) -> Unit,
    onShowSignalBarsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row {
            SatelliteMonitorGroupingMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.groupingMode == mode,
                    onClick = { onGroupingModeChanged(mode) },
                    label = { Text(if (mode == SatelliteMonitorGroupingMode.FREQUENCY) "Frequency" else "Constellation") },
                )
            }
        }
        Row {
            Text("Signal strength")
            Switch(checked = state.showSignalBars, onCheckedChange = onShowSignalBarsChanged)
        }
        state.message?.let { Text(it) }
        state.groups.forEach { group ->
            Text(group.primaryLabel)
            group.sections.forEach { section ->
                if (state.groupingMode == SatelliteMonitorGroupingMode.CONSTELLATION) {
                    Text(section.secondaryLabel)
                }
                section.rows.forEach { row ->
                    Text("${row.satelliteLabel} ${row.signalCode} R ${row.roverCn0DbHz ?: "-"} B ${row.baseCn0DbHz ?: "-"}")
                }
            }
        }
    }
}
```

During implementation, replace plain `Text` rows with the repo's existing compact dashboard typography and make the grouping controls a slider-style two-choice segmented switch. Do not use nested cards.

- [ ] **Step 8: Wire card and screen route**

In `HomeDashboard.kt`, place `SatelliteMonitorCard` near the existing fix and corrections cards. Pass callbacks from `MainActivity.kt`.

In `MainActivity.kt`, add:

```kotlin
enum class AppScreen {
    HOME,
    SATELLITE_MONITOR,
    ...
}
```

Add an explicit action to the recording service:

```kotlin
private fun selectSatelliteMonitorEngine(engine: SatelliteMonitorEngine) {
    val intent = Intent(this, RecordingForegroundService::class.java)
        .setAction(RecordingForegroundService.ACTION_SET_SATELLITE_MONITOR_ENGINE)
        .putExtra(RecordingForegroundService.EXTRA_SATELLITE_MONITOR_ENGINE, engine.name)
    startService(intent)
}
```

The service action only updates monitor display selection. It does not change receiver commands, workflow, NTRIP or RTKLIB lifecycle.

- [ ] **Step 9: Run UI compile verification**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: Kotlin compilation passes.

- [ ] **Step 10: Commit UI work**

```bash
git add app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/SatelliteMonitorDashboardModels.kt app/src/test/kotlin/org/rtkcollector/app/ui/dashboard/SatelliteMonitorDashboardModelsTest.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardLayoutModels.kt app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt app/src/main/kotlin/org/rtkcollector/app/ui/common/HelpOverlay.kt
git commit -m "feat: add satellite monitor ui"
```

---

### Task 10: Final Verification And Reliability Review

**Files:**
- Review only unless fixes are required by verification.

- [ ] **Step 1: Check for accidental sample staging**

Run:

```bash
git status --short
```

Expected: no files under `samples/` or `samples/debug/` are staged or modified for commit.

- [ ] **Step 2: Check whitespace and patch hygiene**

Run:

```bash
git diff --check
```

Expected: no whitespace errors.

- [ ] **Step 3: Run focused JVM module tests**

Run:

```bash
sh gradlew :core:quality:test :core:correction:test :core:rtklib:test :receiver:ublox-m8:test :receiver:unicore-n4:test
```

Expected: tests pass. If a module has no tests for a new parser path, add the missing test before continuing.

- [ ] **Step 4: Run Android source compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin
```

Expected: Kotlin compilation passes.

- [ ] **Step 5: Run app unit tests only when local tooling reaches tests**

Run:

```bash
sh gradlew :app:testDebugUnitTest
```

Expected on a compatible host: tests pass. In this Termux/aarch64 workspace, if Gradle fails before test execution because Android SDK resource tooling resolves a non-runnable `aapt2`, record the exact failure and rely on `:app:compileDebugKotlin` plus pure JVM module tests for local verification.

- [ ] **Step 6: Review append-before-parse ordering**

Inspect `RecordingForegroundService.kt` and confirm:

```text
receiver bytes: append receiver-rx.raw -> advisory receiver parser
correction bytes: append correction-input.raw -> RTKLIB/correction routing -> advisory RTCM MSM parser
main card: display active main engine only; no dashboard engine selector
```

If any monitor parser runs before the relevant append call, move it after the append and rerun `sh gradlew :app:compileDebugKotlin`.

- [ ] **Step 7: Review unsupported-state language**

Run:

```bash
rg -n "Android internal|requires a satellite-monitoring|unsupported|unavailable|stale|In-device RTK|RTKLIB" app/src/main/kotlin docs
```

Expected: user-facing text states external USB scope and distinguishes unsupported, unavailable and stale states.

- [ ] **Step 8: Review hidden command mutation risk**

Run:

```bash
rg -n "satelliteMonitor|ACTION_SET_SATELLITE_MONITOR_ENGINE|send.*command|initCommands|runtimeCommands" app/src/main/kotlin receiver
```

Expected: satellite monitor capability is read from explicit profiles, and engine selection does not send receiver commands or rewrite profile commands.

- [ ] **Step 9: Update plan status if this work is implemented**

If implementation is complete in the branch, add an evidence-based row or update an existing row in `docs/superpowers/plan-status.md` using the repo taxonomy:

```markdown
| Satellite frequency monitor | Implemented, not field-tested | Code, docs and JVM/Kotlin verification are present. UM980/N4, M8P, M8T+RTKLIB and live MSM caster validation remain hardware/manual checks. |
```

Use `Done` only after the manual hardware/caster checks in the verification matrix no longer list specific open work.

- [ ] **Step 10: Final commit**

If `docs/superpowers/plan-status.md` was changed:

```bash
git add docs/superpowers/plan-status.md
git commit -m "docs: update satellite monitor plan status"
```

---

## Self-Review

### Spec Coverage

- Recording-only and external USB scope: Task 1 requirements and Task 9 UI text.
- Advisory pipeline and capture reliability: Task 1 Android requirements, Task 8 service integration and Task 10 append-before-parse review.
- Per-frequency rover/base/used counts: Task 2 shared summaries, Task 4 RTCM MSM parser, Task 5 u-blox RAWX parser, Task 7 RTKLIB usage contract and Task 9 UI.
- Bands grouped and only current rover/base bands displayed: Task 2 `frequencySummaries` and Task 9 UI model tests.
- Optional signal bars: Task 9 UI model and screen toggle.
- Explicit in-device RTK / RTKLIB selector with profile-driven default: Task 3 config and Task 9 UI/service action.
- No silent fallback: Task 2 selected-engine availability and Task 8 controller tests.
- Explicit supported-device profiles: Task 3 profile capability model and built-ins.
- UM980 uncertainty: Task 6 keeps UM980 per-signal support behind documented messages and marks unsupported data honestly.
- RTCM MSM parsing: Task 4.
- RTKLIB per-satellite/per-signal usage contract: Task 7.
- Documentation and verification matrix: Task 1 and Task 10.

### Placeholder Scan

This plan avoids unresolved placeholder instructions. Where receiver documentation is required before field support, the plan gives an explicit scaffold and verification-matrix outcome instead of asking the implementer to guess.

### Type Consistency

Shared model names are introduced in Task 2 and reused consistently: `SatelliteMonitorSnapshot`, `SatelliteSignalKey`, `RoverSignalState`, `BaseSignalState`, `SolutionSignalState`, `SatelliteFrequencySummary`, `SatelliteMonitorEngine`, `SatelliteSourceStatus`. Profile config introduced in Task 3 is consumed by the controller in Task 8 and UI in Task 9. RTKLIB per-signal usage introduced in Task 7 maps to `SolutionSignalState` in Task 8.
