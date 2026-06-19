# RTKLIB-EX Real-Time MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` or
> `superpowers:executing-plans` to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for checkpointing. Do not skip ahead, and do not
> improvise a different architecture without updating this plan first.

**Goal:** Add a first in-phone RTKLIB-EX real-time solution path for RtkCollector
as an optional, forward-only, advisory native-library engine. The first practical
target is u-blox M8T/M8P RAWX/SFRBX with NTRIP RTCM3 corrections. UM980 is
supported through direct OBSVMB only for this first RTKLIB implementation;
OBSVMCMPB remains converter/decoder-update work. Temporary-base live positioning
uses the same NTRIP RTCM3 correction input path.

**Non-negotiable architecture:** RTKLIB-EX must never own USB capture, NTRIP
networking, receiver TX, session writers, Android foreground-service lifecycle
or storage. RTKLIB-EX consumes advisory data only after receiver RX and
correction input have already been recorded. If RTKLIB-EX lags, fails or is not
enabled, receiver recording, NTRIP-to-receiver and dashboard monitoring must
continue.

**Tech stack:** Kotlin, Android foreground service, JNI/C or C++ facade,
RTKLIB-EX pinned snapshot, Gradle/CMake native build, `core:workflow`,
`core:solution`, `core:session`, app recording service, JUnit tests, replay
fixtures, `git diff --check`, `sh gradlew :core:workflow:test`,
`sh gradlew :app:compileDebugKotlin`, host/CI `assembleDebug`.

---

## Locked Decisions

- RTKLIB-EX is used as an in-process native library, not via command-line tools.
- Live RTKLIB processing is forward-only. No forward/backward or combined mode.
- First MVP target is u-blox M8T with UBX RAWX/SFRBX and NTRIP RTCM3.
- u-blox M8P/F9P-style UBX RAWX/SFRBX should use the same direct UBX route
  where available.
- UM980 uses direct OBSVMB for RTKLIB live processing in this phase.
- UM980 OBSVMCMPB must not be accepted as direct RTKLIB input unless RTKLIB-EX
  direct support is later declared. It needs a named converter or decoder patch.
- Base/correction input is NTRIP RTCM3 for this phase.
- Temporary-base live positioning also uses NTRIP RTCM3 correction input.
- Persisted user-facing RTKLIB outputs are NMEA and `.pos`.
- RTKLIB status may be logged separately for diagnostics if lightweight, but
  `.nmea` and `.pos` are the required deliverables.
- Dashboard shows a separate RTKLIB square/card only when RTKLIB is enabled.
- Mock provider does not auto-judge whether RTKLIB is better than receiver
  solution. User control remains explicit.
- RTKLIB native library and worker are lazy: do not load/start the native path
  unless RTKLIB processing is requested.
- Licence/NOTICE updates for RTKLIB-EX are part of the implementation.

---

## Current Foundation

The following routing foundation is expected before starting native work:

- `RtklibRoverInputFormat`
- `RtklibInputRouteKind`
- `RtklibRoverInputCapability`
- `RtklibEngineCapabilities`
- `RtklibInputRoutePlan`
- `RtklibSolutionDirection.FORWARD_ONLY`
- `RtklibInputRouter`
- workflow validator checks based on explicit RTKLIB routes

Status: implemented as routing/API foundation. Native RTKLIB-EX code is still
not bundled.

Additional checkpoint: app-side RTKLIB profile/settings and session-artifact
plumbing is implemented. `RtklibProfile` supports disabled, rover kinematic and
temporary-base static presets; settings sets can reference a RTKLIB profile;
settings export/import preserves RTKLIB profiles; active recording config
declares RTKLIB artifacts only when the profile is enabled; app/session writers
have separate append paths for `rtklib-solution.nmea`, `rtklib-solution.pos`
and `rtklib-status.jsonl`. Native RTKLIB-EX source/JNI, service fanout and live
dashboard card remain open.

If these are not present in a future branch, implement them first using the
already approved model:

- direct u-blox RAWX/SFRBX -> `input_ubx`;
- direct Unicore OBSVMB -> `input_unicore`;
- direct RTCM3 corrections/base observations -> `input_rtcm3`;
- UM980 OBSVMCMPB -> converter-required unless direct support is explicitly
  added.

Checkpoint validation:

```sh
git diff --check
sh gradlew :core:workflow:test
sh gradlew :app:compileDebugKotlin
python3 tools/check_spec_requirements.py docs/specification
```

---

## File Structure

Create or update these files. Names may be adjusted only if the existing module
layout requires it; update this plan when doing so.

```text
third_party/rtklib-ex/README.md
third_party/rtklib-ex/upstream/...
third_party/rtklib-ex/patches/...
third_party/rtklib-ex/android/CMakeLists.txt

core/rtklib/build.gradle.kts
core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibConfig.kt
core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibEngine.kt
core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibInputModels.kt
core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibOutputModels.kt
core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibNativeBridge.kt
core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibWorker.kt
core/rtklib/src/main/kotlin/org/rtkcollector/core/rtklib/RtklibOutputWriters.kt
core/rtklib/src/main/cpp/rtklib_bridge.c
core/rtklib/src/main/cpp/rtklib_bridge.h
core/rtklib/src/main/cpp/CMakeLists.txt

core/session/src/main/kotlin/org/rtkcollector/core/session/SessionArtifacts.kt
core/workflow/src/main/kotlin/org/rtkcollector/core/workflow/WorkflowModel.kt
core/workflow/src/main/kotlin/org/rtkcollector/core/workflow/RtklibInputRouter.kt
core/workflow/src/main/kotlin/org/rtkcollector/core/workflow/WorkflowValidator.kt
core/solution/src/main/kotlin/org/rtkcollector/core/solution/SolutionModels.kt

app/src/main/kotlin/org/rtkcollector/app/profile/ProfileModels.kt
app/src/main/kotlin/org/rtkcollector/app/profile/ProfileStores.kt
app/src/main/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfig.kt
app/src/main/kotlin/org/rtkcollector/app/recording/RecordingForegroundService.kt
app/src/main/kotlin/org/rtkcollector/app/recording/RecordingServiceState.kt
app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardModels.kt
app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/DashboardServiceMapper.kt
app/src/main/kotlin/org/rtkcollector/app/ui/dashboard/HomeDashboard.kt

NOTICE
docs/receiver-profiles.md
docs/session-format.md
docs/user-workflows.md
docs/workflows.md
docs/specification/workflows.md
docs/specification/receiver-behaviour.md
docs/specification/session-artifacts.md
docs/specification/verification-matrix.md
docs/superpowers/plan-status.md
```

Add tests:

```text
core/workflow/src/test/kotlin/org/rtkcollector/core/workflow/RtklibInputRouterTest.kt
core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibConfigTest.kt
core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibWorkerTest.kt
core/rtklib/src/test/kotlin/org/rtkcollector/core/rtklib/RtklibOutputWritersTest.kt
app/src/test/kotlin/org/rtkcollector/app/profile/RtklibProfileTest.kt
app/src/test/kotlin/org/rtkcollector/app/profile/ActiveRecordingConfigRtklibTest.kt
```

Native integration tests may require a normal Android build host. Do not spend
Termux time retrying native packaging when the known `aapt2`/ABI limitation is
hit; record the limitation and run Kotlin-level checks.

---

## Phase 1: Formal Specs And Session Artifacts

- [ ] Update `docs/specification/workflows.md`:
  - RTKLIB-EX is a native library, not CLI.
  - live mode is forward-only only.
  - RTKLIB is advisory and separate from receiver internal solution and PPP.
  - RTKLIB path is lazy and inactive unless enabled.
  - input route plan is mandatory before start.
- [ ] Update `docs/specification/receiver-behaviour.md`:
  - u-blox RAWX/SFRBX is first-class direct RTKLIB input.
  - UM980 OBSVMB is first direct route.
  - UM980 OBSVMCMPB is converter-required for now.
- [ ] Update `docs/specification/session-artifacts.md`:
  - add RTKLIB NMEA artifact name, recommended `rtklib-solution.nmea`;
  - add RTKLIB POS artifact name, recommended `rtklib-solution.pos`;
  - if status logging is added, name it `rtklib-status.jsonl`;
  - state these are derived/advisory artifacts and not replacements for
    `receiver-rx.raw` or `correction-input.raw`.
- [ ] Update `docs/session-format.md` and `docs/user-workflows.md`.
- [ ] Update `docs/superpowers/plan-status.md`:
  - set In-phone RTKLIB real-time solution to `In progress` when implementation
    begins.

Checkpoint:

```text
Document RTKLIB-EX real-time MVP requirements
```

Validation:

```sh
git diff --check
python3 tools/check_spec_requirements.py docs/specification
```

---

## Phase 2: Pin RTKLIB-EX Snapshot And Native Build Shell

- [x] Add `third_party/rtklib-ex/README.md` with:
  - upstream URL;
  - exact commit hash requirement;
  - licence;
  - local patch policy;
  - refresh procedure;
  - statement that automatic tracking of upstream HEAD is forbidden.
- [x] Add `tools/update_rtklib_ex.py` to create/update a local pinned checkout
  and write `snapshot.json`.
- [ ] Add the pinned RTKLIB-EX snapshot under `third_party/rtklib-ex/upstream`.
  Use a clean copy or submodule only if repository policy allows it. Current
  checkpoint keeps the local checkout ignored until the exact snapshot is
  selected.
- [x] Add `third_party/rtklib-ex/patches/README.md` even if no patches exist.
- [x] Add native-build shell placeholder for RTKLIB-EX sources needed by the
  bridge. Full source list remains open until the pinned snapshot is selected.
- [ ] Prefer compiling RTKLIB-EX as a static native library linked into
  `rtkcollector_rtklib`.
- [ ] Define compile flags explicitly:
  - constellation flags for GPS/GLO/GAL/BDS/QZSS as needed;
  - frequency count suitable for M8T/M8P and UM980;
  - no Android logging inside RTKLIB core unless wrapper-scoped;
  - avoid LAPACK dependency for first MVP unless proven necessary.
- [ ] Do not include RTKLIB command-line apps.
- [ ] Do not include RTKGPS+ map, Proj/GDAL or embedded caster code.
- [ ] Ensure normal Android Studio/CI build can package all supported ABIs.

Checkpoint:

```text
Add pinned RTKLIB-EX native build shell
```

Validation:

```sh
git diff --check
sh gradlew :app:compileDebugKotlin
```

On a normal Android build host:

```sh
./gradlew assembleDebug
```

Expected Termux limitation: APK/native packaging may need Windows Android
Studio, CI or an x86-64 Linux host with runnable Android SDK native tools.

---

## Phase 3: Kotlin RTKLIB API Surface

- [x] Create `:core:rtklib` and include it in `settings.gradle.kts`.
- [x] Add `RtklibPreset` enum:
  - `ROVER_KINEMATIC_RTK`
  - `TEMPORARY_BASE_STATIC_RTK`
- [x] Add `RtklibConfig`:
  - route plan from `RtklibInputRouter`;
  - preset;
  - receiver profile id;
  - base context summary;
  - output NMEA enabled;
  - output POS enabled;
  - max rover queue bytes;
  - max correction queue bytes;
  - stale/backlog thresholds.
- [x] Add `RtklibInputModels`:
  - `RtklibInputStreamKind.ROVER`
  - `RtklibInputStreamKind.CORRECTION`
  - byte chunk with monotonic timestamp, session offset if available, and
    declared input route.
- [x] Add `RtklibOutputModels`:
  - engine state;
  - last solution;
  - fix class;
  - output age;
  - correction age;
  - backlog metrics;
  - decoded rover epochs;
  - decoded correction messages;
  - last warning/error.
- [x] Add `RtklibEngine` interface:

```kotlin
interface RtklibEngine : AutoCloseable {
    fun start(config: RtklibConfig): RtklibStartResult
    fun offerRoverBytes(bytes: ByteArray, timestampMillis: Long): RtklibOfferResult
    fun offerCorrectionBytes(bytes: ByteArray, timestampMillis: Long): RtklibOfferResult
    fun snapshot(): RtklibEngineSnapshot
    fun stop()
}
```

- [x] Make queue offer non-blocking from the caller perspective.
- [x] If queue is full, drop advisory RTKLIB input and increment drop counters.
  Do not block receiver recording.
- [x] Add tests for config validation, route acceptance, queue sizing and drop
  accounting with a fake native backend.

Checkpoint:

```text
Add RTKLIB Kotlin engine API
```

Validation:

```sh
git diff --check
sh gradlew :core:rtklib:test
sh gradlew :app:compileDebugKotlin
```

---

## Phase 4: Native Bridge Facade

- [ ] Add `RtklibNativeBridge` as the only Kotlin class that loads the native
  library.
- [ ] Do not call `System.loadLibrary(...)` from app startup or unrelated code.
  Load only when RTKLIB engine is constructed for an enabled workflow.
- [ ] Add JNI functions:
  - create engine handle;
  - start with preset/config;
  - feed rover bytes;
  - feed correction bytes;
  - poll NMEA lines or bytes;
  - poll POS lines or bytes;
  - poll structured status;
  - stop;
  - destroy.
- [ ] Native bridge must consume the already-planned route:
  - UBX -> RTKLIB `input_ubx`;
  - Unicore OBSVMB -> RTKLIB `input_unicore`;
  - RTCM3 -> RTKLIB `input_rtcm3`;
  - converter route -> not implemented in this MVP except explicit error.
- [ ] Native bridge must reject unsupported route kinds with structured error,
  not crash.
- [ ] Native bridge must not open files, serial devices, sockets or NTRIP
  streams.
- [ ] Native bridge must not write app session artifacts directly.
- [ ] Native code may maintain RTKLIB state, but all file output is performed by
  Kotlin session writers from emitted NMEA/POS bytes.
- [ ] Native code must not assume BESTNAV, ADRNAV, PPPNAV, NAV-PVT or NMEA are
  rover raw observations.

Checkpoint:

```text
Add RTKLIB native bridge facade
```

Validation:

```sh
git diff --check
sh gradlew :core:rtklib:compileKotlin
sh gradlew :app:compileDebugKotlin
```

On host/CI:

```sh
./gradlew assembleDebug
```

---

## Phase 5: Worker Isolation And Recording-Service Fanout

- [ ] Add `RtklibWorker` using one dedicated worker thread.
- [ ] Worker has bounded rover and correction queues.
- [ ] Worker is started only when active workflow/config enables RTKLIB.
- [ ] Worker receives bytes only after:
  - receiver RX bytes have been appended to `receiver-rx.raw`;
  - correction bytes have been appended to `correction-input.raw` /
    `correction-input.rtcm3`.
- [ ] Worker must never hold locks used by USB reads, serial writes, NTRIP
  client, session writers or dashboard broadcast.
- [ ] Worker state updates are throttled and exposed as snapshots.
- [ ] Stop ordering:
  - stop accepting new RTKLIB input;
  - drain or bounded-flush output lines;
  - close NMEA and POS writers syntactically if needed;
  - stop native engine;
  - release worker thread.
- [ ] If RTKLIB throws a managed exception, mark RTKLIB failed and keep recording.
- [ ] Native crash risk cannot be fully caught in-process. Mitigation for this
  MVP is lazy loading: if RTKLIB is disabled, native path is never activated.

Checkpoint:

```text
Isolate RTKLIB worker from recording path
```

Validation:

```sh
git diff --check
sh gradlew :core:rtklib:test
sh gradlew :app:compileDebugKotlin
```

Manual review:

- Confirm no RTKLIB call exists in the raw USB read loop before raw append.
- Confirm non-RTKLIB workflows do not instantiate `RtklibNativeBridge`.

---

## Phase 6: Session Outputs

- [x] Add session artifacts:
  - `rtklib-solution.nmea`
  - `rtklib-solution.pos`
  - optional `rtklib-status.jsonl`
- [x] Add `RtklibOutputWriters`.
- [ ] NMEA writer:
  - writes only RTKLIB-generated NMEA;
  - preserves sub-second time if RTKLIB emits it;
  - does not mix receiver NMEA with RTKLIB NMEA.
- [ ] POS writer:
  - writes RTKLIB `.pos` output exactly as emitted or as formatted by the
    RTKLIB bridge;
  - includes header if RTKLIB provides one;
  - flushes periodically and closes cleanly on stop.
- [x] Add session metadata fields:
  - RTKLIB enabled;
  - RTKLIB preset;
  - output artifact names;
- [ ] RTKLIB-EX snapshot id;
- [ ] RTKLIB route plan;
- [ ] validation result at start.
- [x] Never store NTRIP secrets in RTKLIB metadata.

Checkpoint:

```text
Record RTKLIB NMEA and POS outputs
```

Validation:

```sh
git diff --check
sh gradlew :core:rtklib:test
sh gradlew :app:compileDebugKotlin
```

---

## Phase 7: Profiles, Settings And Workflow Activation

- [x] Add minimal RTKLIB profile/settings model:
  - disabled;
  - rover kinematic RTK;
  - temporary-base static RTK.
- [x] Add settings-set fields for:
  - selected RTKLIB preset;
  - enabled/disabled;
  - output NMEA/POS toggles;
  - queue/backlog defaults if exposed.
- [ ] Do not add full RTKLIB option editor.
- [ ] Workflow validation must reject RTKLIB start when:
  - route plan unsupported;
  - correction source is not NTRIP RTCM3 for this MVP;
  - UM980 profile is OBSVMCMPB without converter;
  - required base/mountpoint context missing;
  - output artifacts cannot be opened.
- [ ] Add or update built-in command profiles:
  - u-blox M8T raw RTKLIB profile already emits RAWX/SFRBX;
  - UM980 RTKLIB OBSVMB profile emits OBSVMB plus monitoring logs;
  - keep existing OBSVMCMPB profile for recording efficiency, but do not use it
    for RTKLIB live MVP.
- [x] Ensure non-RTKLIB workflows still do not load native RTKLIB.

Checkpoint:

```text
Add RTKLIB profiles and workflow activation
```

Validation:

```sh
git diff --check
sh gradlew :app:compileDebugKotlin
```

Targeted tests:

- active config with RTKLIB disabled;
- u-blox + RTKLIB enabled;
- UM980 OBSVMB + RTKLIB enabled;
- UM980 OBSVMCMPB + RTKLIB enabled rejected without converter;
- NTRIP missing rejected;
- non-RTKLIB start path does not instantiate native bridge.

---

## Phase 8: Dashboard RTKLIB Card

- [ ] Add separate RTKLIB card/square shown only when RTKLIB is enabled.
- [ ] Show compact fields:
  - RTKLIB state;
  - fix class;
  - age;
  - correction age;
  - backlog/dropped chunks;
  - output rate if available;
  - last warning/error.
- [ ] Keep receiver solution card unchanged.
- [ ] Do not merge receiver and RTKLIB fix states.
- [ ] Do not automatically switch mock provider source based on perceived
  quality.
- [ ] If mock provider source selection is added, it must be explicit and
  user-controlled.
- [ ] Ensure dashboard update throttling remains smooth and does not increase
  capture-path work.

Checkpoint:

```text
Add RTKLIB dashboard card
```

Validation:

```sh
git diff --check
sh gradlew :app:compileDebugKotlin
```

Manual smoke:

- RTKLIB disabled: no RTKLIB card, no native load.
- RTKLIB enabled: RTKLIB card visible, receiver card still visible.

---

## Phase 9: Replay And Reference-Recording Validation

- [x] Add UM980 session-readiness checker for local ZIP/directory captures
  without USB hardware.
- [ ] Add full file/replay support that can feed RTKLIB-EX once the native
  backend exists.
- [ ] Use local debug recordings under `samples/debug/` only as local evidence;
  do not commit captures.
- [ ] When the user provides canonical M8T and UM980 recordings, either:
  - derive small non-sensitive test fixtures committed under `testdata/`, or
  - document manual replay commands if captures cannot be committed.
- [ ] Validate:
  - M8T RAWX/SFRBX + NTRIP RTCM3 -> RTKLIB `.pos` and NMEA output;
  - UM980 OBSVMB + NTRIP RTCM3 -> RTKLIB `.pos` and NMEA output;
  - UM980 OBSVMCMPB rejected without converter;
  - malformed data does not stop recording/replay session.
- [ ] Compare RTKLIB output against known external pipeline where practical.

Checkpoint:

```text
Add RTKLIB replay validation
```

Validation:

```sh
git diff --check
sh gradlew :core:rtklib:test
sh gradlew :app:compileDebugKotlin
```

Manual:

- replay user-provided M8T recording once available;
- replay available UM980 OBSVMB recording;
- confirm output files are created and non-empty.

---

## Phase 10: Licence, NOTICE And Publication Hygiene

- [ ] Identify RTKLIB-EX licence and required notices.
- [ ] Update root `NOTICE`.
- [ ] Update `docs/third-party-licenses.md`.
- [ ] Confirm GPL-3.0-or-later project compatibility.
- [ ] Document native library inclusion in user-facing docs.
- [ ] Ensure no RTKLIB-EX upstream generated binaries or local build artifacts
  are committed.

Checkpoint:

```text
Document RTKLIB-EX licensing
```

Validation:

```sh
git diff --check
rg "RTKLIB|RTKLIB-EX" NOTICE docs/third-party-licenses.md docs
```

---

## Phase 11: End-To-End Validation

Run in Termux:

```sh
git diff --check
sh gradlew :core:workflow:test
sh gradlew :core:rtklib:test
sh gradlew :app:compileDebugKotlin
python3 tools/check_spec_requirements.py docs/specification
```

Run on Windows Android Studio, CI, or another full Android build host:

```sh
./gradlew clean
./gradlew assembleDebug
./gradlew test
```

Manual hardware smoke tests:

- M8T + NTRIP RTCM3:
  - recording starts;
  - raw receiver RX is recorded;
  - correction input is recorded;
  - RTKLIB card appears;
  - RTKLIB NMEA and POS files are written;
  - stopping closes files cleanly.
- UM980 OBSVMB + NTRIP RTCM3:
  - receiver internal solution remains separate;
  - RTKLIB card appears;
  - RTKLIB output files are written.
- Non-RTKLIB rover + NTRIP:
  - no RTKLIB card;
  - no native RTKLIB worker;
  - existing receiver-side RTK behaviour unchanged.
- Temporary-base + NTRIP + RTKLIB:
  - RTKLIB static/temporary-base preset starts;
  - outputs are written;
  - recording remains robust if RTKLIB lags/fails.

Final review checklist:

- [ ] Raw capture path unaffected.
- [ ] RTKLIB not loaded unless enabled.
- [ ] RTKLIB never owns USB, NTRIP, storage or receiver TX.
- [ ] RTKLIB outputs are separate from receiver solution exports.
- [ ] Forward-only mode only.
- [ ] u-blox direct route tested.
- [ ] UM980 OBSVMB direct route tested.
- [ ] UM980 OBSVMCMPB rejected or converter-declared.
- [ ] Licence/NOTICE updated.
- [ ] Plan status updated.

Final commit suggestion:

```text
Add RTKLIB-EX real-time solution MVP
```

---

## Non-Goals For This Plan

- No RTKLIB command-line execution.
- No forward/backward or combined offline processing.
- No RTKLIB PPP implementation.
- No full RTKLIB option editor.
- No map/GIS/shapefile/feature collection.
- No automatic mock-provider source switching based on quality.
- No direct OBSVMCMPB support unless implemented as an explicit converter or
  RTKLIB-EX decoder update with tests.
- No local live base receiver stream beyond NTRIP RTCM3 in this MVP.
- No separate native process isolation in this MVP, although it remains a later
  hardening option if native crash risk becomes unacceptable.

---

## Open Inputs Needed From User

- Canonical M8T recording with RAWX/SFRBX and matching NTRIP RTCM3 correction
  stream.
- Canonical UM980 OBSVMB recording if available.
- Confirmation after first field run whether the minimal RTKLIB presets are
  sufficient before adding more option controls.
