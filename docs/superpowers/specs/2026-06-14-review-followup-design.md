# Review Follow-up: UM980 Best Solution + Mock-Location Robustness Design

## Goal

Close the post-merge review findings on the u-blox M8T + mock-location feature
so that the Best Solution card and Android mock-location output work correctly
on both UM980 and u-blox sessions, and the new code paths are robust under
realistic failure conditions.

Two critical reviewer findings remain after the in-line fixes already shipped
in commit `e34a972` (baud-switch crash and NAV-PVT carrSoln):

- The `solutionCandidates` map and the `BestSolutionSelector` only receive
  u-blox candidates. On a UM980 session — the project's primary receiver —
  the Best card always shows `n/a` and the mock provider publishes nothing.
- The mock-location code path is functionally broken on a fresh device
  because Android's `setTestProviderLocation` requires `addTestProvider` plus
  `setTestProviderEnabled` to run first, and nothing in the service registers
  the test provider.

Around those, several `Important` and carry-over items need addressing so the
feature is not a liability under field conditions.

## Non-goals

- No change to the workflow model, capture path, recording artifacts, or
  receiver-driver API contracts.
- No change to existing UM980 telemetry parsers, NMEA parsers, NTRIP client,
  or session writers.
- No third-party fused-location integration (Google Play Services
  `LocationRequest` and Mapbox/etc. consumers continue to interact with the
  test provider through the standard Android `LocationManager` API).
- No new receiver family. M8P internal RTK support is intentionally untouched
  in this plan beyond NAV-PVT `carrSoln`.

## Architecture

The current branch wires advisory parsing → `solutionCandidates` map →
selector → state copy → broadcast, all triggered per advisory byte chunk, only
on the u-blox path. This design replaces that with a two-clock model.

### Per-chunk clock (existing, byte-driven)

Each advisory consumer writes a `SolutionCandidate` keyed by a stable source
identifier into the `solutionCandidates` map. The consumers are isolated by
the existing `runCatching` in `AdvisoryFanout.accept` so parser failures never
block raw recording.

### 1 Hz tick clock (new, scheduled)

A single-threaded scheduled executor runs the selector, broadcasts state, and
publishes to the Android mock provider at 1 Hz. This is the natural cadence
both because GNSS receiver position outputs typically refresh at 1–5 Hz and
because Android mock-location consumers register `LocationListener`s with
`minTimeMs = 1000`, capping observation at 1 Hz on their side regardless of
how often we publish.

```text
Per-chunk clock                                  1 Hz tick clock
─────────────────────────────                    ─────────────────────────
ublox advisory consumer                          evict candidates older
  UbloxStreamParser                                than 5 s
    ubx records                                  best = BestSolutionSelector
      → UbloxNavPvtParser                          .select(remaining, now)
      → solutionCandidates[                      synchronized(stateLock):
          "UBX-NAV-PVT"]                           state = state.copy(
    nmea records                                       bestSolution* …,
      → NmeaGgaParser                                  ubloxFrequency …)
      → solutionCandidates[                      if mockEnabled:
          "NMEA-GGA"]                              if best == null:
                                                     mockState = STALE
um980-ascii advisory consumer                      else if best.updatedAt
  Um980AsciiSolutionParser                                 == lastPublishedAt:
    → solutionCandidates[                            no-op
        "UM980-BESTNAV"]                           else:
  NmeaGgaParser                                      result = sink.publish(best)
    → solutionCandidates[                            if PUBLISHED:
        "NMEA-GGA"]                                    lastPublishedAt =
                                                         best.updatedAt
um980-binary advisory consumer                       if FAILED and previous
  Um980BinaryParser BESTNAV                              != FAILED:
    → solutionCandidates[                              set lastError once
        "UM980-BESTNAV"]                         broadcastState()
  Um980BinaryParser PPPNAV
    → solutionCandidates["UM980-PPP"]
```

The per-chunk path is unchanged in its existing UM980 work; it adds writes to
`solutionCandidates`. The tick path replaces the existing
`updateBestSolution(nowMillis)` calls that ran inside `parseUbloxAdvisory`.

## Components

### `receiver:unicore-n4` — new file `Um980SolutionAdapter.kt`

Pure Kotlin, JUnit 5 tested. Module gains an `implementation(project(":core:solution"))`
dependency mirroring the `receiver:ublox-m8` setup.

- `Um980AsciiSolution.toBestnavCandidate(nowMillis: Long): SolutionCandidate?` —
  returns a `SolutionEngine.DEVICE_INTERNAL` candidate from BESTNAV output.
  `FixClass` derived from `positionType`:
  - `NARROW_INT`, `WIDE_INT`, `L1_INT`, `INS_RTKFIXED` → `RTK_FIXED`
  - `NARROW_FLOAT`, `IONOFREE_FLOAT`, `L1_FLOAT`, `INS_RTKFLOAT` → `RTK_FLOAT`
  - `PSRDIFF`, `SBAS`, `INS_PSRDIFF` → `DGPS`
  - `SINGLE`, `INS_PSRSP` → `SINGLE`
  - `PPP` → `PPP_CONVERGED`
  - `PPP_CONVERGING` → `PPP_CONVERGING`
  - `NONE`, blank → returns null (no candidate)

- `Um980Telemetry.toPppCandidate(nowMillis: Long): SolutionCandidate?` —
  returns a `SolutionEngine.RECEIVER_PPP` candidate from PPPNAV output.
  `FixClass` mapping: `PPP` → `PPP_CONVERGED`; `PPP_CONVERGING` → `PPP_CONVERGING`;
  null otherwise.

- `NmeaGgaFix.toCandidate(receiverFamily: String, nowMillis: Long): SolutionCandidate?` —
  returns a `SolutionEngine.GENERIC_NMEA` candidate. `FixClass` from GGA fix
  quality field: 1 → `SINGLE`, 2 → `DGPS`, 4 → `RTK_FIXED`, 5 → `RTK_FLOAT`,
  6 → `PPP_CONVERGED` (dead-reckoning is treated as PPP-class for ranking
  purposes here, conservatively), 0/null → null.

All adapters return `null` rather than a `FixClass.NONE` candidate so the
selector's contract stays clean.

### `RecordingForegroundService` changes

- `private val solutionCandidates = ConcurrentHashMap<String, SolutionCandidate>()`
  (was a plain `MutableMap`). Writes happen from advisory-consumer threads;
  reads happen from the tick thread.

- `@Volatile private var activeReceiverFamily: String = ""` so the long-lived
  `AdvisoryConsumer` lambda sees writes from `startRecording`.

- `private var bestSolutionTicker: ScheduledFuture<*>? = null`
  `private val tickerExecutor =
      Executors.newSingleThreadScheduledExecutor { runnable ->
          Thread(runnable, "rtkcollector-best").apply { isDaemon = true }
      }`
  Scheduled in `startRecording` at `1 s` fixed rate, cancelled and `shutdown()`-ed
  in `stopRecording`.

- `private var lastMockPublishedAt: Long? = null`
- `private var previousMockResult: MockLocationPublishResult? = null`

- Replace the in-line `updateBestSolution(System.currentTimeMillis())` call in
  `parseUbloxAdvisory` with just the `solutionCandidates["UBX-NAV-PVT"] = …`
  write and tracker `record(...)` calls. The tick owns the selector.

- New `private fun runBestSolutionTick(now: Long)` runs the body described in
  the architecture section. Behaviour is extracted into a pure `Tick` helper
  (see Testing) so the executor wrapper is a one-liner.

- Route u-blox NMEA into the existing `NmeaGgaParser`. The parser lives in
  `receiver/unicore-n4` but is receiver-neutral; either move it to a shared
  spot or import it from the u-blox consumer. Simpler: import it. It already
  has no UM980-specific assumptions.

- Add new `UbloxMessageKind` records on GGA when the u-blox consumer sees an
  NMEA `$..GGA` line, so the frequency row's last slot stops being a permanent
  `-`.

- UM980 advisory consumers (`um980-ascii` and `um980-binary` in
  `buildAdvisoryFanout`) write to `solutionCandidates` using the new adapters
  on each parsed solution event.

- `compileReceiverCommands` catches `Throwable` excluding `Error` subclasses
  (`catch (t: Exception)`). Caller passes a `phase: CompilePhase` enum
  (`START` / `STOP`). `START` failure sets `RecordingErrorSeverity.FATAL` and
  re-throws so the existing start-path aborts before any TX bytes. `STOP`
  failure sets `DEGRADED` and does NOT re-throw so the existing stop-path
  completes.

### `MockLocationPublisher` changes

- `MockLocationPublisher` gains `var lastFailure: Throwable? = null` — set
  inside the `runCatching { … }.fold(onFailure = …)` branch so the caller can
  inspect it for logging.

- `AndroidMockLocationSink` signature unchanged.

- New `MockLocationPublishResult.NOT_PERMITTED` to distinguish the
  "RtkCollector is not the selected mock app" state from `DISABLED`.

- Drop the `altitudeM = current.mslAltitudeM ?: current.ellipsoidalHeightM`
  fallback. Use `mslAltitudeM` only. When null, the resulting `Location` has
  `hasAltitude() == false`, which is the honest signal.

- `MockLocationUpdate` removes the `provider: String` field — it was dead data
  (the sink uses its own `providerName` constructor arg). Tests are updated.

### `RecordingForegroundService.configureMockLocation` changes

```kotlin
private fun configureMockLocation(enabled: Boolean) {
    if (!enabled) {
        teardownMockLocation()
        return
    }
    val manager = getSystemService(LocationManager::class.java)
    if (manager == null) {
        state = state.copy(mockLocationState = "NOT_PERMITTED")
        return
    }
    val ok = runCatching {
        manager.addTestProvider(
            LocationManager.GPS_PROVIDER,
            false /* requiresNetwork */,
            false /* requiresSatellite */,
            false /* requiresCell */,
            false /* hasMonetaryCost */,
            true  /* supportsAltitude */,
            true  /* supportsSpeed */,
            true  /* supportsBearing */,
            ProviderProperties.POWER_USAGE_LOW,
            ProviderProperties.ACCURACY_FINE,
        )
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
    }
    if (ok.isFailure) {
        mockLocationPublisher = null
        state = state.copy(
            mockLocationState = "NOT_PERMITTED",
            lastError = "RtkCollector is not the selected mock location app. " +
                "Enable in Developer Options.",
            errorCategory = RecordingErrorCategory.PARSER_EXPORT,
            errorSeverity = RecordingErrorSeverity.DEGRADED,
        )
        return
    }
    mockLocationPublisher = MockLocationPublisher(
        AndroidMockLocationSink(manager, LocationManager.GPS_PROVIDER),
    )
}

private fun teardownMockLocation() {
    val publisher = mockLocationPublisher ?: return
    mockLocationPublisher = null
    runCatching {
        val manager = getSystemService(LocationManager::class.java) ?: return
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
        manager.removeTestProvider(LocationManager.GPS_PROVIDER)
    }
}
```

`teardownMockLocation()` is called from `stopRecording` and at the start of
`configureMockLocation(enabled = false)`.

### `UbloxScriptCompiler` changes

- `parseInteger` accepts the range `Int.MIN_VALUE..0xFFFF_FFFFL`. The per-field
  packers (`u8`, `u16`, `u32`, `i32`) keep their own range checks so the
  produced bytes remain protocol-correct.

- Misleading "missing command name" message becomes "missing command payload"
  when `parts.size in 2..2` (i.e. command name present, payload missing).

### `UbloxMessageFrequencyTracker` changes

- `WINDOW_MILLIS = 1_000L` (was 1_500L). The display string now reads as a
  literal per-second count.

- `display()` uses `UbloxMessageKind.entries.joinToString("/") { it.label }`
  for the header so `UbloxMessageKind.label` is no longer dead.

- Existing test's expected math updated: with `record(RAWX, 1000); record(RAWX, 2000);
  record(NAV_PVT, 1500); record(NAV_PVT, 2500); display(3000)` and `WINDOW = 1000`:
  RAWX at 2000 (`3000 - 2000 = 1000`, not `< 1000`) → excluded; NAV_PVT at 2500
  (`3000 - 2500 = 500 < 1000`) → included. Result: `"-/-/-/1/- Hz"`. Test
  updated; an extra case proves the boundary semantics explicitly.

### Thread-safety contract documentation

- `UbloxStreamParser`: KDoc on the class — `Not thread-safe; the parser
  maintains an internal buffer of pending bytes between calls. Confine to a
  single advisory-consumer thread or wrap calls in external synchronization.`

- `UbloxMessageFrequencyTracker`: same KDoc. `record()` and `display()` may
  run on the same thread under the current advisory-fanout model.

- `Um980MessageFrequencyTracker`: pre-existing tracker gets the same KDoc for
  consistency.

### Charset

- `compileReceiverCommands` non-UBX branch: `"$it\r\n".toByteArray(Charsets.US_ASCII)`
  instead of `encodeToByteArray()`. UM980 command strings are pure ASCII so
  the encoded bytes are identical today; the explicit charset guarantees the
  contract against future non-ASCII contributions.

## Data flow

### Per-chunk write path (unchanged structure, new writes)

```
SerialTransport bytes
  ↓ CaptureRuntime.readOnce
  ↓ recorder.appendReceiverBytes        ← AUTHORITATIVE
  ↓ AdvisoryFanout.accept (runCatching per consumer)
        ├─ ublox-mixed-stream  (if activeReceiverFamily.startsWith("ublox"))
        │     UbloxStreamParser.accept
        │       ubx → UbloxNavPvtParser → solutionCandidates["UBX-NAV-PVT"]
        │       ubx → ubloxFrequencyTracker.record(NAV_PVT/RAWX/SFRBX/TM2)
        │       nmea → NmeaGgaParser → solutionCandidates["NMEA-GGA"]
        │       nmea → ubloxFrequencyTracker.record(GGA)
        ├─ um980-ascii
        │     Um980AsciiSolutionParser → solutionCandidates["UM980-BESTNAV"]
        │     NmeaGgaParser → solutionCandidates["NMEA-GGA"]
        │     um980FrequencyTracker.record(…)
        └─ um980-binary
              Um980BinaryParser BESTNAV → solutionCandidates["UM980-BESTNAV"]
              Um980BinaryParser PPPNAV → solutionCandidates["UM980-PPP"]
              um980FrequencyTracker.record(…)
```

Same source label is overwritten by later parsers (last writer wins). The map
has at most four entries during a single session.

### 1 Hz tick (read path, replaces the in-line call)

```kotlin
val now = System.currentTimeMillis()
solutionCandidates.entries.removeIf {
    now - it.value.updatedAtMillis > BestSolutionSelector.DEFAULT_MAX_AGE_MILLIS
}
val best = BestSolutionSelector.select(solutionCandidates.values, now)

synchronized(stateLock) {
    state = state.copy(
        bestSolutionSource = best?.sourceId ?: "n/a",
        bestSolutionFix = best?.fixClass?.name ?: "n/a",
        bestSolutionAgeMs = best?.ageMillis,
        latDeg = best?.latDeg ?: state.latDeg,
        lonDeg = best?.lonDeg ?: state.lonDeg,
        latLon = best?.let { latLonDisplay(it.latDeg, it.lonDeg) } ?: state.latLon,
        ellipsoidalHeight = best?.ellipsoidalHeightM?.let(::metersDisplay)
            ?: state.ellipsoidalHeight,
        altitude = best?.mslAltitudeM?.let(::metersDisplay) ?: state.altitude,
        horizontalAccuracy = best?.horizontalAccuracyM?.let(::metersDisplay)
            ?: state.horizontalAccuracy,
        verticalAccuracy = best?.verticalAccuracyM?.let(::metersDisplay)
            ?: state.verticalAccuracy,
        satellitesUsed = best?.satellitesUsed ?: state.satellitesUsed,
        ubloxFrequency = ubloxFrequencyTracker.display(now),
    )
}

if (mockLocationPublisher != null) {
    val result = when {
        best == null -> MockLocationPublishResult.STALE
        best.updatedAtMillis == lastMockPublishedAt -> previousMockResult
            ?: MockLocationPublishResult.PUBLISHED
        else -> mockLocationPublisher.publish(best, enabled = true)
            .also { r ->
                if (r == MockLocationPublishResult.PUBLISHED) {
                    lastMockPublishedAt = best.updatedAtMillis
                }
            }
    }
    val changed = result != previousMockResult
    previousMockResult = result
    synchronized(stateLock) {
        state = state.copy(mockLocationState = result.name)
        if (result == MockLocationPublishResult.FAILED && changed) {
            state = state.copy(
                lastError = "Android mock-location update failed. " +
                    "Check Developer Options mock-location app setting.",
                errorCategory = RecordingErrorCategory.PARSER_EXPORT,
                errorSeverity = RecordingErrorSeverity.DEGRADED,
            )
        }
    }
}
broadcastState()
```

`mockLocationPublisher.publish` keeps its `Result<…>.fold` shape; the
`Throwable` is captured to `publisher.lastFailure` for downstream logging.

### Start path additions

```
onStartCommand
  read extras (existing) including EXTRA_ENABLE_MOCK_LOCATION
  configureMockLocation(enabled) — registers test provider if requested
  validate workflow (existing)
  compileReceiverCommands(START, family, initScript)
    on Exception → FATAL, state broadcast, recording aborts before TX bytes
  open USB, open writers (existing)
  start advisory fanout (existing)
  bestSolutionTicker = tickerExecutor.scheduleAtFixedRate(
      ::runBestSolutionTick, 1, 1, TimeUnit.SECONDS)
```

### Stop path additions

```
stopRecording
  bestSolutionTicker?.cancel(false)
  tickerExecutor doesn't shut down per stop because the service is destroyed
    next; if the service stays alive, the same executor handles the next
    session. Service onDestroy calls tickerExecutor.shutdown().
  compileReceiverCommands(STOP, family, shutdownScript)
    on Exception → DEGRADED, does NOT abort the stop path
  teardownMockLocation()
  solutionCandidates.clear()
  lastMockPublishedAt = null
  previousMockResult = null
  reset state fields (existing block, unchanged)
```

## Error handling

| Trigger | Surfaced as | Recording continues? |
| --- | --- | --- |
| `UbloxScriptCompiler.compile` throws on init script | `RECEIVER_COMMAND`, `FATAL`, aborts before TX | No (by design) |
| `UbloxScriptCompiler.compile` throws on shutdown script | `RECEIVER_COMMAND`, `DEGRADED`, stop completes | Yes — stop continues |
| Parser throws inside per-chunk write site | Existing `runCatching` in `AdvisoryFanout` → event log | Yes |
| `BestSolutionSelector.select` throws (pure, shouldn't) | `runCatching` around tick body; tick retries next second | Yes |
| `addTestProvider` throws `SecurityException` | `mockLocationState = NOT_PERMITTED`, `lastError` set once | Yes |
| `setTestProviderLocation` throws | Publisher returns `FAILED`, captures throwable to `lastFailure`. `lastError` set ONLY on transition (debounced via `previousMockResult`) | Yes |
| Mock publisher null at tick time (user disabled) | `mockLocationState = DISABLED`; no publish | Yes |
| `removeTestProvider` throws on stop | `runCatching`, swallowed | Yes — stop continues |
| `Um980*Parser` throws during candidate write | Existing `runCatching` in advisory consumer | Yes |
| `NmeaGgaParser` throws on malformed GGA | Existing `runCatching` in advisory consumer | Yes |

## Testing

### New pure-Kotlin unit tests

- `receiver/unicore-n4/.../Um980SolutionAdapterTest.kt` — every documented
  BESTNAV `positionType` → `FixClass` mapping; PPP variants; `NONE` returns
  null candidate; `engine` correct for each adapter.

- `receiver/unicore-n4/.../NmeaGgaCandidateTest.kt` — GGA fix-quality matrix
  (0…8) → `FixClass` mapping; quality 0/null → null candidate; engine =
  `GENERIC_NMEA`.

- `receiver/ublox-m8/.../UbloxScriptCompilerTest.kt` — add `CFG-NAV5 1 3 -5 …`
  case proving negative `i32` round-trips. Add CFG-RATE byte-content assertion
  (`1000 1 1` → `E8 03 01 00 01 00`) closing the byte-content coverage gap.

- `receiver/ublox-m8/.../UbloxMessageFrequencyTrackerTest.kt` — update for
  `WINDOW = 1000`. Add a boundary case for the `< WINDOW` predicate and a case
  proving the header is built from `UbloxMessageKind.label`.

- `core/solution/.../BestSolutionSelectorTest.kt` — add cases for future
  timestamp filter, recency tiebreak, custom `maxAgeMillis`, and SBAS vs DGPS
  rank-tie tiebreaker.

### Pure-Kotlin tick helper test (no Android)

Extract the tick body into:

```kotlin
internal data class BestSolutionTickInput(
    val candidates: Collection<SolutionCandidate>,
    val nowMillis: Long,
    val mockEnabled: Boolean,
    val lastMockPublishedAt: Long?,
    val previousMockResult: MockLocationPublishResult?,
)

internal data class BestSolutionTickOutput(
    val stateDelta: BestSolutionStateDelta,
    val publishAction: PublishAction,
    val newLastMockPublishedAt: Long?,
    val newPreviousMockResult: MockLocationPublishResult?,
)

internal sealed class PublishAction {
    data object None : PublishAction()
    data class Publish(val snapshot: BestSolutionSnapshot) : PublishAction()
}

internal object BestSolutionTickLogic {
    fun compute(input: BestSolutionTickInput): BestSolutionTickOutput { … }
}
```

`BestSolutionTickLogicTest` covers: empty candidates, single fresh candidate,
multiple candidates with selector ranking, stale-only candidates, mock
disabled, mock enabled with no fresh candidate (`STALE` + no publish), mock
enabled with same timestamp as `lastMockPublishedAt` (no publish), mock
enabled with new timestamp (Publish action), FAILED transition (lastError
set), repeated FAILED (lastError NOT set).

This is the most important new test — it covers all branches of the tick
without needing Android.

### Service-side smoke test (no Android required for the helper test)

`RecordingForegroundService` itself becomes a thin shell around
`BestSolutionTickLogic` and `configureMockLocation`. Both of those are tested
in isolation. No service-instrumentation test is added in this plan.

### Manual on-device verification (Android-SDK host required)

- u-blox M8T session: dashboard Best card shows e.g. `RTK_FLOAT from
  UBX-NAV-PVT`; ublox frequency row shows updating counts; mock GPS visible in
  third-party "Show GPS location" app at 1 Hz.
- UM980 session: Best card shows `RTK_FIXED from UM980-BESTNAV` when receiver
  reports `NARROW_INT`; mock-location identical to dashboard lat/lon.
- Both: disconnect antenna for >5 s, Best card switches to `n/a`, mock state
  to `STALE`, no mock publishes during the gap.
- Both: enable mock-location toggle without selecting RtkCollector in
  Developer Options → mock state shows `NOT_PERMITTED` and a clear lastError.
- Shutdown-time script error: lifecycle ends as `STOPPED` with a `DEGRADED`
  error log entry, not `FAILED`.

## Finding coverage map

Every reviewer finding accounted for:

| Finding | Where addressed |
| --- | --- |
| Critical #1 UM980 candidates | `Um980SolutionAdapter`; per-chunk writes in UM980 consumers |
| Critical #2 baud-switch crash | Already fixed in `e34a972` |
| Critical #3 NAV-PVT carrSoln | Already fixed in `e34a972` |
| Important #4 frequency window/Hz | `WINDOW_MILLIS = 1_000L`; header uses `label` |
| Important #5 GGA slot dead | u-blox NMEA wired; tracker records GGA |
| Important #6 u-blox NMEA discarded | Routed into `NmeaGgaParser`; produces `NMEA-GGA` candidates |
| Important #7 shutdown FATAL | `compileReceiverCommands` distinguishes `START`/`STOP` |
| Important #8 narrow catch | Catch broadened to `Exception` |
| Important #9 negative integers | `parseInteger` accepts `Int.MIN_VALUE..0xFFFF_FFFFL` |
| Important #10 mock spam | 1 Hz tick + `lastMockPublishedAt` |
| Carry-over thread-safety | `@Volatile` on `activeReceiverFamily`; `ConcurrentHashMap` for `solutionCandidates`; KDocs on parsers/trackers |
| Carry-over unbounded map | TTL eviction at tick |
| Carry-over `addTestProvider` gap | `configureMockLocation` registers; `teardownMockLocation` removes |
| Carry-over MockLocationPublisher swallows throwable | `lastFailure` field on publisher |
| Carry-over altitudeM substitution | Strict MSL only |
| Carry-over FAILED spam | Debounced via `previousMockResult` |
| Carry-over charset regression | `Charsets.US_ASCII` in non-UBX branch |
| Carry-over `UbloxMessageKind.label` unused | Used in header |
| Carry-over `toSolutionCandidate()` returns NONE | Adapter returns null on `FixClass.NONE` |
| Carry-over `BestSolutionSnapshot.isFresh` hardcoded | Not fixed in this plan; logged as future work below |
| Carry-over `UbloxScriptCompilerTest` byte content | Byte-content assertion added |

## Known limitations / future work

- `BestSolutionSnapshot.isFresh` still hardcodes `DEFAULT_MAX_AGE_MILLIS`. A
  custom `maxAgeMillis` passed to `BestSolutionSelector.select` still produces
  a snapshot whose `isFresh` lies. Not addressed in this plan because the
  service uses the default everywhere. Logged for future work.

- `M8P` internal RTK support is unchanged. NAV-PVT `carrSoln` parsing already
  produces `RTK_FLOAT`/`RTK_FIXED` from u-blox NAV-PVT, but `M8P`-specific
  command profiles, base-mode commands, and RTCM3 input wiring are out of
  scope.

- Other `UbloxM8Driver.build{Rover,Base,FixedBase}Commands` continue to return
  `emptyList()`. The plan does not wire them; M8T runs via `buildInitCommands`
  only.

- Test coverage for `MockLocationPublisher.publish` STALE and FAILED branches
  is added via the new `BestSolutionTickLogicTest`. The publisher's own
  per-branch tests are not expanded in this plan because the logic is now
  trivially driven by the tick.

- `SolutionEngine.GENERIC_NMEA` is treated equally with `DEVICE_INTERNAL` for
  ranking purposes (both flow through the `FixClass.rank` ordering). A more
  nuanced ranking that prefers receiver-native over NMEA-derived at equal
  `FixClass` is possible later if needed.
