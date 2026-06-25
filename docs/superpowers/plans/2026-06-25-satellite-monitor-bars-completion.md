# Satellite Monitor Bars Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the satellite monitor bars end to end: live compact and detailed grouped bars must be backed by real rover, base and selected-solution satellite/signal telemetry from explicit built-in monitor profiles, with no fake per-frequency data derived from aggregate status counts.

**Architecture:** Keep byte-exact recording authoritative. Add advisory parsers and a bounded satellite-monitor aggregation layer after raw receiver and correction bytes are already persisted. Existing Compose card/detail views consume throttled service snapshots only. Built-in profiles explicitly declare which telemetry they enable, and the selected main engine controls the compact card.

**Tech Stack:** Kotlin/JVM modules (`core:quality`, `core:correction`, receiver modules), Android foreground service and Jetpack Compose, RTKLIB JNI/C++ bridge, focused unit tests, local replay checks from `samples/debug/` as manual evidence.

---

## Scope

This plan completes the boxed bar functionality requested for the monitoring UI:

- Main screen compact card:
  - primary grouping is constellation, frequency rows inside each constellation;
  - R/B rows use boxed horizontal bars;
  - low saturation means visible, high saturation means used;
  - right-aligned label is `used/visible`;
  - source dots use `R`, `B`, `S` labels and the info affordance already present;
  - the card always follows the main selected engine.
- Detailed monitor screen:
  - default primary grouping is frequency;
  - switchable primary grouping is constellation with frequency secondary;
  - the page can show both in-device and RTKLIB data when both are available;
  - optional signal strength bars are controlled from the detail screen menu.
- Telemetry sources:
  - rover visible signals from receiver raw observation telemetry;
  - base visible signals from incoming RTCM MSM correction messages;
  - used signals from the selected positioning engine: UM980 in-device telemetry when supported, RTKLIB usage telemetry when RTKLIB is the engine.
- Built-in profiles:
  - UM980/N4 profiles must explicitly enable the required binary telemetry;
  - u-blox M8T profiles must explicitly enable RAWX/SFRBX/NAV-SAT telemetry for RTKLIB mode;
  - M8P/F9P may use the same raw-monitor path as M8T only as untested compatibility, not as a tested precise-positioning claim.

Out of scope:

- Maps, GIS, shapefiles or field collection.
- Making RTKLIB real-time processing mandatory for V1 recording.
- Treating aggregate `BESTNAV`, `STADOP`, `RTKSTATUS`, `RTCMSTATUS` or `NAV-SAT` counts as per-frequency bars.
- Injecting satellite status into Android mock locations.

## Reliability Invariants

Every implementation task must preserve these properties:

- `receiver-rx.raw` remains byte-exact and is appended before advisory parsing.
- NTRIP/correction bytes remain separate from receiver RX.
- Parser, monitor, RTKLIB and UI failures never stop an otherwise healthy recording.
- Satellite monitor parsing runs on bounded/advisory paths and must drop stale monitor state rather than block recording.
- Built-in monitor profiles are explicit. No automatic hidden command enablement based on a receiver guess.
- Unsupported telemetry must be reported as unavailable or partial, never displayed as valid bars.

## Current Repository State

Already present:

- Compact card and detailed screen UI models:
  - `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/SatelliteMonitorDashboardModels.kt`
  - `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
  - `app/src/main/kotlin/org/rtkcollector/app/ui/satellite/SatelliteMonitorScreen.kt`
- Dashboard mapper support for a compact groups wire format:
  - `constellation|band|roverUsed|roverVisible|baseUsed|baseVisible`
  - implemented in `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt`
- UM980 binary framing and several navigation/status parsers:
  - `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980StreamParser.kt`
  - `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParser.kt`
- RTCM3 frame extraction:
  - `core/correction/src/main/kotlin/org/rtkcollector/core/correction/Rtcm3Extractor.kt`
- RTKLIB aggregate solution snapshots:
  - `core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibOutputModels.kt`
  - `app/src/main/cpp/rtklib_bridge.cpp`

Implemented in this branch:

- A satellite-monitor domain model and aggregator in `core:quality`.
- Rover raw observation parsing for UM980 `OBSVMB`/`OBSVMCMPB`.
- Rover raw observation parsing for u-blox `RXM-RAWX`, plus per-satellite `NAV-SAT` use data.
- RTCM MSM parser for base visible signals and C/N0 when payload layout is supported.
- UM980 selected-solution usage parser for `BESTSATB`.
- RTKLIB per-satellite/per-frequency usage export through the native bridge contract.
- Service wiring to feed parsed batches into the aggregator and broadcast populated groups.
- Built-in profile capability metadata and monitor-ready built-in command profiles.
- Tests proving the bars are backed by parser/controller telemetry fixtures.

Remaining validation gaps:

- Real hardware/replay confirmation for UM980 `OBSVMB`/`OBSVMCMPB` and `BESTSATB` monitor bars.
- Fresh M8T/M8P replay or hardware confirmation for RAWX/NAV-SAT and RTKLIB bars.
- Host Android NDK build and device exercise of the native RTKLIB `.so` satellite-usage export.

## File Structure

The original execution checklist below is retained as planning traceability.
Current branch status is summarized in the implemented/validation-gap section
above and in `docs/superpowers/plan-status.md`.

Create:

- `core/quality/src/main/kotlin/org/rtkcollector/core/quality/SatelliteMonitorModels.kt`
- `core/quality/src/main/kotlin/org/rtkcollector/core/quality/SatelliteMonitorAggregator.kt`
- `core/quality/src/test/kotlin/org/rtkcollector/core/quality/SatelliteMonitorAggregatorTest.kt`
- `core/correction/src/main/kotlin/org/rtkcollector/core/correction/RtcmBitReader.kt`
- `core/correction/src/main/kotlin/org/rtkcollector/core/correction/RtcmSignalMapping.kt`
- `core/correction/src/main/kotlin/org/rtkcollector/core/correction/Rtcm3MsmParser.kt`
- `core/correction/src/test/kotlin/org/rtkcollector/core/correction/Rtcm3MsmParserTest.kt`
- `core/correction/src/test/kotlin/org/rtkcollector/core/correction/Rtcm3MsmTestFrameBuilder.kt`
- `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxRawxParser.kt`
- `receiver/ublox-m8/src/test/kotlin/org/rtkcollector/receiver/ublox/UbloxRawxParserTest.kt`
- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980ObservationParser.kt`
- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980BestSatParser.kt`
- `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980ObservationParserTest.kt`
- `receiver/unicore-n4/src/test/kotlin/org/rtkcollector/receiver/unicore/Um980BestSatParserTest.kt`
- `app/src/main/kotlin/org/rtkcollector/app/recording/SatelliteMonitorController.kt`
- `app/src/test/kotlin/org/rtkcollector/app/recording/SatelliteMonitorControllerTest.kt`
- `core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibSatelliteUsage.kt`

Modify:

- `core/correction/src/main/kotlin/org/rtkcollector/core/correction/Rtcm3ReferenceStationParser.kt`
- `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxNavSatParser.kt`
- `receiver/ublox-m8/src/main/kotlin/org/rtkcollector/receiver/ublox/UbloxStreamParser.kt` only if needed for routing `RXM-RAWX` frames
- `receiver/unicore-n4/src/main/kotlin/org/rtkcollector/receiver/unicore/Um980BinaryParser.kt`
- `core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibOutputModels.kt`
- `core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibNativeBridge.kt`
- `app/src/main/cpp/rtklib_bridge.cpp`
- `app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt`
- `app/src/main/kotlin/org/rtkcollector/app/recording/SatelliteMonitorServiceStatus.kt`
- `app/src/main/kotlin/org/rtkcollector/app/profiles/CommandProfile.kt`
- the built-in command profile source currently defining UM980 and u-blox profiles
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/SatelliteMonitorDashboardModels.kt`
- `app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt`
- `app/src/main/kotlin/org/rtkcollector/app/ui/satellite/SatelliteMonitorScreen.kt`
- `docs/specification/`
- `docs/specification/verification-matrix.md`
- `docs/user-workflows.md`
- `docs/receivers.md` or the current user-facing receiver documentation file
- `docs/superpowers/plan-status.md`

Do not commit:

- `samples/debug/*.zip`
- generated replay output
- `.codex-tmp/`
- `.superpowers/`

## Data Contracts

Implement the shared monitor model first so receiver parsers, RTCM parsers, RTKLIB and UI cannot invent incompatible formats.

```kotlin
package org.rtkcollector.core.quality

enum class SatelliteMonitorEngine {
    IN_DEVICE_RTK,
    RTKLIB
}

enum class SatelliteMonitorSource {
    ROVER,
    BASE,
    SOLUTION
}

enum class SatelliteConstellation {
    GPS,
    GLONASS,
    GALILEO,
    BEIDOU,
    QZSS,
    SBAS,
    UNKNOWN
}

data class SatelliteSignalKey(
    val constellation: SatelliteConstellation,
    val svid: Int,
    val band: String,
    val signalCode: String? = null
)

data class SatelliteSignalObservation(
    val key: SatelliteSignalKey,
    val source: SatelliteMonitorSource,
    val observedAtEpochMillis: Long,
    val cn0DbHz: Double? = null,
    val used: Boolean = false
)

data class SatelliteMonitorInputBatch(
    val engine: SatelliteMonitorEngine,
    val source: SatelliteMonitorSource,
    val receivedAtEpochMillis: Long,
    val observations: List<SatelliteSignalObservation>
)

data class SatelliteFrequencySummary(
    val constellation: SatelliteConstellation,
    val band: String,
    val roverVisible: Int,
    val roverUsed: Int,
    val baseVisible: Int,
    val baseUsed: Int,
    val roverAverageCn0DbHz: Double?,
    val baseAverageCn0DbHz: Double?
)

data class SatelliteMonitorSnapshot(
    val engine: SatelliteMonitorEngine,
    val generatedAtEpochMillis: Long,
    val sourceAgesMillis: Map<SatelliteMonitorSource, Long?>,
    val summaries: List<SatelliteFrequencySummary>,
    val message: String?
)
```

Aggregation rules:

- Visible count is distinct `constellation + svid + band + source`.
- Rover used count is the intersection of rover visible keys and selected-solution used keys.
- Base used count is the intersection of base visible keys and selected-solution used keys.
- If the selected solution only reports satellite-level usage without frequency, apply usage to observed frequencies for that satellite and keep a `PARTIAL_USAGE_FREQUENCY_INFERRED` diagnostic flag in the snapshot message. The compact card may show bars, but the detailed page must disclose the partial usage source.
- If no selected-solution usage exists, used count is zero and source `S` is unavailable/stale. Do not mirror visible into used.
- Expire rover/base/solution inputs independently.
- The aggregator must never throw on malformed, empty or duplicate batches.

## Implementation Tasks

### Task 1: Baseline And Spec Alignment

- [ ] Inspect current dirty state:

  ```sh
  git status --short
  git diff -- app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt app/src/main/kotlin/org/rtkcollector/app/recording/SatelliteMonitorServiceStatus.kt app/src/test/kotlin/org/rtkcollector/app/recording/SatelliteMonitorServiceStatusTest.kt
  ```

- [ ] Preserve any existing narrow telemetry-status fix as an independent change. It is acceptable for this plan to build on it, but do not hide it inside large parser commits.
- [ ] Update formal requirements under `docs/specification/` before code changes. Add or update requirements covering:
  - recording-only satellite monitor;
  - explicit monitor profiles;
  - compact card grouped by constellation then frequency;
  - detailed view switch between frequency-primary and constellation-primary;
  - R/B boxed bars with used/visible semantics;
  - no aggregate-status-to-frequency fabrication;
  - graceful unavailable/partial source states.
- [ ] Update `docs/specification/verification-matrix.md` with planned unit tests and manual hardware checks.
- [ ] Update `docs/superpowers/plan-status.md` to show this work as `In progress`.

Commit:

```sh
git add docs/specification docs/superpowers/plan-status.md docs/superpowers/plans/2026-06-25-satellite-monitor-bars-completion.md
git commit -m "Document satellite monitor bar completion plan"
```

### Task 2: Add Core Satellite Monitor Aggregation

- [ ] Add `SatelliteMonitorModels.kt` with the contracts from this plan.
- [ ] Add `SatelliteMonitorAggregator.kt`.
- [ ] Write failing tests first in `SatelliteMonitorAggregatorTest.kt`:
  - rover visible GPS G12 L1/L5 and base visible GPS G12 L1/L5 plus solution used GPS G12 L1 produces `1/1` for both rover and base on L1 and `0/1` on L5;
  - duplicate observations for the same key count once;
  - stale base data expires while rover remains visible;
  - no solution data produces `used=0` and `S` unavailable;
  - partial satellite-level usage marks all observed bands for that satellite as used and emits a partial diagnostic.
- [ ] Implement the aggregator to make the tests pass.

Expected test command:

```sh
sh gradlew :core:quality:test
```

Commit:

```sh
git add core/quality
git commit -m "Add satellite monitor aggregation model"
```

### Task 3: Parse Base Signals From RTCM MSM Corrections

- [ ] Extract the private bit reader logic from `Rtcm3ReferenceStationParser.kt` into `RtcmBitReader.kt` without changing existing reference-station tests.
- [ ] Add `RtcmSignalMapping.kt` with conservative signal-ID to band mappings for RTCM MSM:
  - GPS: L1, L2, L5;
  - GLONASS: G1, G2;
  - Galileo: E1, E5a, E5b, E6;
  - BeiDou: B1, B2, B3;
  - QZSS: L1, L2, L5, L6;
  - SBAS: L1, L5.
- [ ] Add `Rtcm3MsmParser.kt` that supports MSM message families:
  - 1071-1077 GPS;
  - 1081-1087 GLONASS;
  - 1091-1097 Galileo;
  - 1121-1127 BeiDou.
- [ ] The parser must decode the header masks and signal cells into base `SatelliteSignalObservation` keys. It must parse C/N0 for MSM variants that carry it and leave `cn0DbHz=null` for variants that do not.
- [ ] Write failing tests with `Rtcm3MsmTestFrameBuilder.kt`:
  - one GPS MSM frame with satellites 12 and 24, signals L1 and L5;
  - one Galileo frame with E1 and E5a;
  - one malformed frame returns empty parse result and a diagnostic, not an exception;
  - existing `Rtcm3ReferenceStationParserTest` remains green.

Expected commands:

```sh
sh gradlew :core:correction:test
sh gradlew :core:quality:test
```

Commit:

```sh
git add core/correction core/quality
git commit -m "Parse RTCM MSM satellite monitor signals"
```

### Task 4: Parse u-blox Rover Signals

- [ ] Add `UbloxRawxParser.kt` for UBX `RXM-RAWX` (`class=0x02`, `id=0x15`).
- [ ] Map `gnssId`, `svId`, and `sigId` to `SatelliteConstellation`, `svid`, `band`, and `signalCode`.
- [ ] Treat each RAWX measurement block as rover-visible if the measurement block is present and the tracking status reports a valid pseudorange or carrier phase.
- [ ] Extend `UbloxNavSatParser.kt` so it can return per-satellite used flags in addition to existing aggregate counts. Keep the existing aggregate API working.
- [ ] Write failing tests:
  - RAWX GPS L1/L2 measurement blocks map to rover visible rows;
  - RAWX Galileo E1/E5a maps to distinct bands;
  - NAV-SAT used flags create satellite-level solution usage for u-blox in-device engines when such an engine is selected;
  - M8T default RTKLIB mode does not report in-device used bars from NAV-SAT alone.

Expected command:

```sh
sh gradlew :receiver:ublox-m8:test
```

Commit:

```sh
git add receiver/ublox-m8
git commit -m "Parse u-blox satellite monitor telemetry"
```

### Task 5: Parse UM980 Rover Signals And In-Device Usage

- [ ] Add `Um980ObservationParser.kt`.
- [ ] Implement `OBSVMB` binary observation parsing from the Unicore manual:
  - decode the message header through existing `Um980BinaryParser`;
  - decode each tracked satellite and per-frequency observation record;
  - emit rover-visible `SatelliteSignalObservation` values with C/N0 where documented;
  - reject invalid CRC or truncated records as empty parse results with diagnostics.
- [ ] Implement `OBSVMCMPB` compressed observation parsing from the Unicore manual:
  - decode compressed satellite and signal masks;
  - map signal indexes through the manual signal index table;
  - emit the same rover-visible model as `OBSVMB`;
  - preserve C/N0 when available in the compressed record.
- [x] Add `Um980BestSatParser.kt` for `BESTSATB`:
  - decode satellites and signal masks used in the selected in-device solution;
  - emit solution usage observations;
  - when `BESTSATB` reports only satellite-level usage, emit satellite-level usage and let the aggregator apply the partial-usage diagnostic.
- [x] Write failing tests from local manual examples or captured binary frames:
  - OBSVMB GPS L1/L5 and BeiDou B1/B2 records produce rover visible keys;
  - OBSVMCMPB produces the same keys for equivalent compressed data;
  - BESTSATB marks only documented used satellites/signals;
  - malformed/truncated binary frames never throw.
- [x] Add parser hooks in `Um980BinaryParser.kt` without regressing existing BESTNAV/RTKSTATUS/STADOP parsing.

Expected command:

```sh
sh gradlew :receiver:unicore-n4:test
```

Commit:

```sh
git add receiver/unicore-n4
git commit -m "Parse UM980 satellite monitor telemetry"
```

### Task 6: Export RTKLIB Per-Satellite Usage

- [x] Add `RtklibSatelliteUsage.kt` in `core:rtklib`:

  ```kotlin
  data class RtklibSatelliteUsage(
      val constellation: String,
      val svid: Int,
      val frequencyIndex: Int,
      val band: String,
      val used: Boolean,
      val cn0DbHz: Double?
  )
  ```

- [x] Extend `RtklibSolutionSnapshot` with `satelliteUsages: List<RtklibSatelliteUsage> = emptyList()`.
- [x] Update `RtklibNativeBridge.kt` and `rtklib_bridge.cpp` to export bounded per-satellite/per-frequency usage from the active RTKLIB state:
  - read under the same synchronization boundary used for solution snapshots;
  - use `ssat[].vsat[f]` or the bundled RTKLIB equivalent for used-per-frequency status;
  - map RTKLIB system and PRN to the shared constellation/svid model;
  - map RTKLIB frequency index to L1/L2/L5-style band labels;
  - bound exported rows to the RTKLIB maximum satellite and frequency counts;
  - validate all JNI strings and arrays before use.
- [x] Add tests or a native-test harness that feeds a synthetic RTKLIB snapshot and verifies:
  - used and visible frequency rows are distinct;
  - empty RTKLIB state returns an empty list;
  - JNI export handles zero satellites without null dereference.

Expected commands:

```sh
sh gradlew :core:rtklib:test
sh gradlew :app:compileDebugKotlin
```

Commit:

```sh
git add core/rtklib app/src/main/cpp app/src/main/kotlin/org/rtkcollector/app
git commit -m "Export RTKLIB satellite usage for monitor bars"
```

### Task 7: Add The App Satellite Monitor Controller

- [x] Add `SatelliteMonitorController.kt` in the recording package.
- [x] Responsibilities:
  - receive rover, base and solution batches from service callbacks;
  - keep separate state for `IN_DEVICE_RTK` and `RTKLIB`;
  - compute compact active-engine snapshots;
  - compute detailed all-engine snapshots;
  - expose source freshness as `R`, `B`, `S`;
  - serialize compact groups into the existing dashboard wire format;
  - serialize detailed data into an explicit detail wire format that includes C/N0 and partial diagnostics.
- [x] Add tests:
  - active engine in-device uses UM980 BESTSAT usage and ignores RTKLIB usage for compact card;
  - active engine RTKLIB uses RTKLIB usage and still keeps in-device data available for detail;
  - base RTCM MSM visible data updates only source `B`;
  - stale solution source changes source `S` to stale/unavailable without deleting rover/base visibility immediately.

Expected command:

```sh
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.recording.SatelliteMonitorControllerTest
```

Commit:

```sh
git add app/src/main/kotlin/org/rtkcollector/app/recording app/src/test/kotlin/org/rtkcollector/app/recording
git commit -m "Add recording satellite monitor controller"
```

### Task 8: Wire Parsers Into RecordingForegroundService

- [ ] Instantiate `SatelliteMonitorController` when a recording starts and dispose it when the recording stops.
- [ ] Feed rover batches only after receiver bytes have already been appended to `receiver-rx.raw`.
- [ ] Feed correction/base batches only after correction bytes have already been appended to `correction-input.raw`.
- [ ] Feed in-device solution usage from:
  - UM980 `BESTSATB`;
  - u-blox per-satellite `NAV-SAT` only for receivers and workflows where in-device usage is valid.
- [ ] Feed RTKLIB solution usage from `RtklibSolutionSnapshot.satelliteUsage` only when RTKLIB is enabled by the validated workflow.
- [ ] Replace current hard-coded broadcast values:
  - `EXTRA_STATE_SATELLITE_MONITOR_SOURCES = "R:UNAVAILABLE;B:UNAVAILABLE;S:UNAVAILABLE"`
  - `EXTRA_STATE_SATELLITE_MONITOR_GROUPS = ""`
  with controller outputs.
- [ ] Keep lifecycle/error broadcasts immediate and routine monitor broadcasts throttled with the existing dashboard throttling approach.
- [ ] Add service tests or focused JVM tests for service mapper/controller integration.

Expected commands:

```sh
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.recording.SatelliteMonitorControllerTest
sh gradlew :app:compileDebugKotlin
```

Commit:

```sh
git add app/src/main/kotlin/org/rtkcollector/app/recording app/src/test/kotlin/org/rtkcollector/app/recording
git commit -m "Wire live satellite monitor bars into recording service"
```

### Task 9: Complete The UI Bars And Detail Controls

- [ ] Keep the compact card light theme by default and preserve the implemented dark-mode card switch.
- [ ] Ensure the main compact card:
  - groups by constellation first;
  - uses frequency rows inside each constellation;
  - renders boxed bars for rover and base;
  - shows `used/visible` right aligned for each R/B row;
  - shows `R`, `B`, `S` source labels next to the status dots;
  - keeps the info affordance in the card top right.
- [ ] Extend the detailed screen data model to include:
  - source-specific C/N0 averages;
  - per-satellite optional rows for signal strength;
  - partial usage diagnostics.
- [ ] Add a detail-screen menu item or slider setting for signal strength bars.
- [ ] Keep the detailed primary grouping switch:
  - default `Frequency`;
  - optional `Constellation`.
- [ ] Add Compose/UI tests where feasible:
  - compact card displays `R 2/3` and `B 1/2` values from a fake model;
  - grouping switch changes order but not counts;
  - unavailable source dot does not display as fresh.

Expected commands:

```sh
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.ui.dashboard.DashboardServiceMapperTest
sh gradlew :app:compileDebugKotlin
```

Manual visual check:

```sh
sh gradlew :app:compileDebugKotlin
```

Then inspect the running UI at the currently available local preview/dev endpoint when one is active, or run the Android app on device/emulator and capture screenshots of:

- main compact card, light mode;
- main compact card, dark card mode;
- detailed screen frequency-primary;
- detailed screen constellation-primary;
- detailed screen with signal strength bars enabled.

Commit:

```sh
git add app/src/main/kotlin/org/rtkcollector/app/ui app/src/test/kotlin/org/rtkcollector/app/ui
git commit -m "Complete satellite monitor bar UI"
```

### Task 10: Add Explicit Built-In Monitor Profiles

- [ ] Extend command profile metadata with explicit monitor capability fields:

  ```kotlin
  enum class SatelliteMonitorProfileCapability {
      UM980_OBSVMB,
      UM980_OBSVMCMPB,
      UM980_BESTSATB,
      UBLOX_RAWX,
      UBLOX_NAV_SAT,
      RTCM_MSM_BASE,
      RTKLIB_USAGE
  }
  ```

- [ ] Built-in profile requirements:
  - built-ins remain viewable and read-only;
  - copied user profiles preserve monitor capability metadata;
  - same-id/same-name built-ins sync to current app defaults on update.
- [ ] Add or update UM980/N4 monitor-ready built-ins:
  - rover with NTRIP and in-device RTK;
  - command list includes live-validated `OBSVMCMPB COM1 0.2` for practical bandwidth;
  - command list includes `BESTSATB COM1 1` for solution usage;
  - keep existing `BESTNAVB`, `RTKSTATUSB`, `RTCMSTATUSB`, `STADOPB` outputs;
  - do not switch the receiver into base mode for rover workflows.
- [ ] Add or update u-blox M8T monitor-ready built-ins:
  - default engine is RTKLIB;
  - enable `RXM-RAWX`;
  - enable `RXM-SFRBX` for RTKLIB processing;
  - enable `NAV-SAT` for visibility/status context;
  - mark M8P/F9P same-mode compatibility as untested precise positioning unless hardware validation exists.
- [ ] Add a profile-list UI fix if not already present:
  - list must be scrollable when opened from the main page;
  - built-in profiles that support telemetry must be findable without hidden off-screen rows.
- [ ] Add tests for profile metadata and built-in migration:
  - UM980 monitor profile declares UM980 observation and usage capabilities;
  - M8T profile declares RAWX, NAV-SAT and RTKLIB usage capabilities;
  - unsupported generic NMEA profile does not claim per-frequency rover telemetry.

Expected commands:

```sh
sh gradlew :app:testDebugUnitTest --tests '*Profile*'
sh gradlew :app:compileDebugKotlin
```

Commit:

```sh
git add app/src/main/kotlin/org/rtkcollector/app/profiles app/src/test/kotlin/org/rtkcollector/app
git commit -m "Add explicit satellite monitor built-in profiles"
```

### Task 11: Replay The User Debug Capture Locally

- [ ] Use `samples/debug/session-2026-06-25T12-19-44.526835Z-745bd627-f282-4b30-90d2-71b2b88fb072-1782390091734.zip` as manual evidence only.
- [ ] Extract to a temporary ignored directory under `/tmp` or `.codex-tmp/`.
- [ ] Run parser replay over:
  - `receiver-rx.raw`;
  - `correction-input.raw`;
  - generated quality sidecars.
- [ ] Confirm:
  - the UM980 status no longer says it is waiting for telemetry once BESTNAV/RTKSTATUS-style telemetry is present;
  - rover bars remain unavailable until OBSVM/OBSVMCMP or BESTSAT telemetry required by the selected profile is present;
  - base bars appear when MSM messages are present in corrections;
  - no parser failure changes raw artifacts.
- [ ] Document the manual result in `docs/specification/verification-matrix.md`, not by committing the capture.

Expected commands:

```sh
git status --short
sh gradlew :app:compileDebugKotlin
```

Commit:

```sh
git add docs/specification/verification-matrix.md
git commit -m "Record satellite monitor replay verification"
```

### Task 12: Update User-Facing Documentation

- [ ] Update receiver documentation to state clearly:
  - RtkCollector currently targets external USB receivers;
  - UM980 and M8T are the focused supported devices for the documented monitor path;
  - M8P/F9P may work in M8T-style raw mode, but precise positioning support is not tested unless hardware evidence is added.
- [ ] Update the monitoring documentation:
  - explain `R`, `B`, `S`;
  - explain visible versus used bars;
  - explain why used can be zero while visible is non-zero;
  - explain partial usage diagnostics;
  - explain that the compact card follows the main engine, while details can show both engines.
- [ ] Update setup documentation for:
  - plain rover;
  - rover with NTRIP;
  - temporary base;
  - permanent/fixed base and rtk2go handoff.
- [ ] Add a screenshot checklist for the user:
  - main screen with UM980 in-device monitor bars;
  - main screen with M8T RTKLIB monitor bars;
  - detailed frequency-primary view;
  - detailed constellation-primary view;
  - profile picker showing scrollable built-ins.

Expected command:

```sh
git diff --check
```

Commit:

```sh
git add docs
git commit -m "Document satellite monitor bars and profiles"
```

### Task 13: Final Verification And Review

- [ ] Run formatting/whitespace check:

  ```sh
  git diff --check
  ```

- [ ] Run targeted tests:

  ```sh
  sh gradlew :core:quality:test
  sh gradlew :core:correction:test
  sh gradlew :receiver:ublox-m8:test
  sh gradlew :receiver:unicore-n4:test
  sh gradlew :core:rtklib:test
  sh gradlew :app:testDebugUnitTest
  ```

- [ ] Run Android compile in this Termux environment:

  ```sh
  sh gradlew :app:compileDebugKotlin
  ```

- [ ] If `assembleDebug` is required for release confidence, run it on Windows/Android Studio or another host where Android SDK native tools match the host CPU. Do not treat Termux `aapt2` host-binary failures as source failures.
- [ ] Request code review using `superpowers:requesting-code-review` with these reviewer scopes:
  - GNSS/RTK systems architect;
  - Android robust background-capture engineer;
  - Kotlin/domain-model maintainer;
  - receiver-protocol reviewer for UM980/u-blox/RTCM.
- [ ] Apply review feedback through `superpowers:receiving-code-review`.
- [ ] Run final verification again.
- [ ] Update `docs/superpowers/plan-status.md`:
  - `Done` only for code paths verified with tests and replay;
  - `Implemented, not field-tested` for hardware paths lacking real UM980/M8T/M8P/F9P field evidence.
- [ ] Commit final review/status changes.

Final push:

```sh
git status --short
git push
```

## Manual Hardware Acceptance Matrix

UM980/N4 rover with NTRIP, in-device RTK:

- Selected built-in profile visibly declares UM980 satellite monitor telemetry.
- Recording starts and raw RX is written.
- `BESTSATB`, `OBSVMCMPB`, base RTCM MSM and solution usage are observed.
- Compact card shows constellation groups with frequency rows.
- Rover/base bars update while recording.
- Stopping NTRIP makes `B` stale/degraded without stopping raw receiver recording.

u-blox M8T rover with NTRIP and RTKLIB:

- Selected built-in profile visibly declares RAWX/SFRBX/NAV-SAT telemetry and RTKLIB monitor mode.
- Compact card follows RTKLIB as the main engine.
- Detailed page can show rover RAWX and RTKLIB usage when available.
- If RTKLIB is not enabled, the app does not claim full used-per-frequency support.

Generic NMEA/RTCM receiver:

- Profile does not claim per-frequency rover telemetry.
- Monitor card explains that a monitor profile is unavailable for the selected receiver.
- Recording, NTRIP routing and raw artifacts continue normally.

M8P/F9P compatibility mode:

- Same raw telemetry path can be selected only where the profile explicitly says it is untested for precise positioning in this app.
- Documentation does not claim tested precise positioning without real hardware evidence.

## Failure Handling Requirements

- Malformed UM980, UBX or RTCM frames are counted as monitor diagnostics only.
- A monitor parser exception must be caught at the parser boundary and converted into stale/unavailable monitor state.
- A monitor source becoming stale must not clear unrelated sources.
- NTRIP reconnect, receiver TX failures and USB RX stalls remain separate service states.
- Monitor state must not be written into `receiver-rx.raw`.

## Done Criteria

- Built-in UM980 and M8T monitor profiles are visible, read-only, scrollable and explicit.
- Main compact card renders real grouped bars from service data, not preview constants.
- Detailed monitor screen switches between frequency-primary and constellation-primary grouping.
- Optional signal strength bars are available on the detailed screen when C/N0 exists.
- UM980 rover observations, UM980 solution usage, u-blox RAWX/NAV-SAT, RTCM MSM base observations and RTKLIB usage all have focused tests.
- The user debug capture has been replayed as manual evidence without committing it.
- `git diff --check` and `sh gradlew :app:compileDebugKotlin` pass locally.
- Documentation explains USB-only focus, UM980/M8T support focus, M8P/F9P caveat and screenshot needs.
