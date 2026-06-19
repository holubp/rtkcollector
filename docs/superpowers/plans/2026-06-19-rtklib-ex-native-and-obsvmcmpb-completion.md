# RTKLIB-EX Native Solver And OBSVMCMPB Completion Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` or
> `superpowers:executing-plans` to implement this plan task-by-task. Use
> `superpowers:test-driven-development` for every reusable parser, converter,
> native bridge wrapper and replay validator. Do not skip phases. If a phase
> proves technically wrong, update this plan first with evidence.

**Goal:** Finish the RTKLIB-EX real-time feature completely: compile a pinned
RTKLIB-EX source snapshot into the Android app as an in-process native library,
feed live rover/correction bytes programmatically, emit RTKLIB NMEA and `.pos`
outputs, and support UM980 `OBSVMCMPB` through an explicit tested converter or
decoder patch.

**Starting repository state before this completion pass:** App-side RTKLIB
routing, settings, session artifacts, worker isolation, dashboard card,
pinned-checkout automation and UM980 sample-readiness checks existed.
`RtklibNativeBridge` was still a safe stub. UM980 `OBSVMCMPB` was deliberately
rejected by start validation until a converter or RTKLIB-EX decoder patch
existed.

**2026-06-19 checkpoint:** The safe stub has been replaced by an optional JNI
backend and Android app-module CMake build glue for `librtkcollector_rtklib.so`.
The bridge feeds declared rover/correction streams to RTKLIB-EX APIs and emits
NMEA/POS/status strings back to Kotlin writers. `OBSVMCMPB` is currently handled
by the named `rtkcollector-obsvmcmp-shim`, which builds a generated copy of the
pinned RTKLIB-EX Unicore decoder with message id 138 routed through the OBSVMB
decoder path. This is implementation-complete enough for host native build and
receiver replay, but not yet field-proven: the local `samples/session-*`
captures checked so far contain monitoring/correction frames and no actual
OBSVMB/OBSVMCMPB observation frames.

The native build is intentionally hosted from `:app` because Android packaging
owns native shared libraries. `core:rtklib` remains the pure Kotlin API/worker
boundary. On this Termux/aarch64 checkout, CMake is enabled only when a valid
Android NDK with `source.properties` is present; otherwise local validation is
limited to Python checks, JVM tests and Kotlin compilation.

**Non-negotiable architecture:** RTKLIB-EX is advisory. It must never own USB
capture, NTRIP, receiver TX, session writers, Android foreground-service
lifecycle or storage. RTKLIB consumes bounded byte queues only after
`receiver-rx.raw` and `correction-input.raw` have already been appended. If
RTKLIB lags, crashes, fails validation or is disabled, raw recording and
NTRIP-to-receiver must continue.

---

## Locked Decisions

- Use RTKLIB-EX as an in-process native library, not command-line tools.
- Use forward-only live processing only.
- Direct input routes:
  - u-blox RAWX/SFRBX -> RTKLIB `input_ubx`;
  - UM980 `OBSVMB` -> RTKLIB `input_unicore`;
  - NTRIP correction input -> RTKLIB `input_rtcm3`.
- UM980 `OBSVMCMPB` must be supported only through one of:
  - a named Kotlin/native converter to RTKLIB-compatible observation records; or
  - a documented RTKLIB-EX decoder patch that accepts `OBSVMCMPB` directly.
- For this plan, prefer a converter layer unless RTKLIB-EX already contains
  stable `OBSVMCMPB` support in the pinned snapshot.
- RTKLIB output artifacts remain separate:
  - `rtklib-solution.nmea`;
  - `rtklib-solution.pos`;
  - optional `rtklib-status.jsonl`.
- User-facing receiver solution exports and RTKLIB solution exports must not be
  mixed.
- Native code must not write app session files directly. It returns emitted
  NMEA/POS/status to Kotlin, and Kotlin session writers persist them.
- `SAVECONFIG`, receiver control and NTRIP caster upload are out of scope except
  insofar as they provide input streams already recorded by the app.

---

## Phase 0: Reconfirm Baseline And Inputs

- [ ] Confirm branch and clean/known-dirty state.
- [ ] Confirm latest pushed commit contains:
  - app-side RTKLIB worker wiring;
  - `RtklibNativeBridge` stub;
  - `tools/update_rtklib_ex.py`;
  - `tools/um980_rtklib_sample_check.py`;
  - UM980 OBSVMB RTKLIB built-in profile;
  - OBSVMCMPB rejection in `RtklibStartValidator`.
- [ ] Run current reliable local checks:

```sh
git diff --check
python3 -m pytest tools/test_*.py
python3 tools/check_spec_requirements.py docs/specification
sh gradlew :core:workflow:test :core:rtklib:test :core:session:test :app:compileDebugKotlin
```

- [ ] Run UM980 sample preflight:

```sh
python3 tools/um980_rtklib_sample_check.py samples/session-*
```

- [ ] Record in this plan whether local samples contain `OBSVMB`,
  `OBSVMCMPB`, NTRIP correction bytes and enough ephemeris for replay.
- [ ] Do not commit `samples/` or `samples/debug/`.

Checkpoint:

```sh
git status --short
```

---

## Phase 1: RTKLIB-EX Source Snapshot And Licence Gate

- [ ] Use `tools/update_rtklib_ex.py` to checkout/update the pinned RTKLIB-EX
  snapshot.
- [ ] Keep `third_party/rtklib-ex/upstream/` ignored unless a deliberate source
  vendoring decision is made.
- [ ] Add `third_party/rtklib-ex/README.md` documenting:
  - upstream repository;
  - pinned commit;
  - update command;
  - local checkout directory;
  - generated/native build artifacts to avoid committing.
- [ ] Add `third_party/rtklib-ex/patches/README.md` documenting patch policy:
  - no silent upstream drift;
  - every patch must have purpose, affected files and tests;
  - no unrelated RTKLIB behaviour changes.
- [ ] Identify RTKLIB-EX licence obligations from upstream files.
- [ ] Update `NOTICE` and `docs/third-party-licenses.md` for the intended
  native inclusion before merging a build that bundles RTKLIB-EX.
- [ ] Add a CI/check script that fails if a native RTKLIB build is enabled but
  licence/NOTICE entries are missing.

Validation:

```sh
python3 tools/update_rtklib_ex.py --ref <pinned-full-commit>
python3 -m pytest tools/test_update_rtklib_ex.py
rg "RTKLIB|RTKLIB-EX" NOTICE docs/third-party-licenses.md third_party/rtklib-ex
```

---

## Phase 2: Native Build Skeleton

- [ ] Add Gradle/NDK/CMake wiring for `core:rtklib` without touching app
  startup.
- [ ] Add `core/rtklib/src/main/cpp/CMakeLists.txt`.
- [ ] Add wrapper source files:
  - `rtklib_bridge.h`;
  - `rtklib_bridge.c` or `rtklib_bridge.cpp`;
  - `rtklib_jni.cpp`.
- [ ] Compile only the RTKLIB-EX library/source files required for live stream
  decoding, positioning and solution output.
- [ ] Do not compile RTKLIB command-line applications.
- [ ] Keep wrapper logging minimal and Android-scoped.
- [ ] Ensure native library name matches
  `RtklibNativeBridge.LIBRARY_NAME = "rtkcollector_rtklib"`.
- [ ] Add a native smoke symbol such as `nativeVersion()` before adding solver
  state.
- [ ] Add JVM/Android instrumentation-compatible tests for the Kotlin side
  where local environment permits.

Validation on host/CI with working Android NDK:

```sh
./gradlew :core:rtklib:assemble
./gradlew :app:assembleDebug
```

Termux fallback validation:

```sh
sh gradlew :core:rtklib:compileKotlin :app:compileDebugKotlin
```

Checkpoint requirement:

- [ ] If native build cannot be run in Termux, document the exact blocker and
  run the host/CI command before marking this phase done.

---

## Phase 3: Native Bridge Lifecycle

- [ ] Replace the `UnimplementedNativeBackend` with a real backend that calls
  JNI only after RTKLIB is enabled.
- [ ] Add JNI functions:
  - create engine handle;
  - start with preset/config;
  - feed rover bytes;
  - feed correction bytes;
  - poll NMEA/POS output;
  - poll structured status;
  - stop;
  - destroy.
- [ ] Wrap native handles with strict lifecycle ownership.
- [ ] Every JNI call must catch native-side failure and return a structured
  error to Kotlin rather than crashing the service where possible.
- [ ] Native backend must reject unsupported routes before allocating large
  native state.
- [ ] `stop()` must be idempotent.
- [ ] `close()` must release native handles even if `start()` partially fails.
- [ ] No native call may open files, sockets, serial devices or Android storage.

Tests:

- [ ] Kotlin test with fake native loader verifies disabled RTKLIB path does not
  load the library.
- [ ] Kotlin test verifies backend creation/start failure marks RTKLIB failed
  while recording service state can continue.
- [ ] Native/unit smoke test verifies create/start/stop/destroy lifecycle with
  empty inputs.

Validation:

```sh
sh gradlew :core:rtklib:test :app:compileDebugKotlin
./gradlew :app:assembleDebug   # host/CI
```

---

## Phase 4: RTKLIB Input Adapters

- [ ] Implement route dispatch inside the native bridge:
  - UBX rover chunks -> RTKLIB `input_ubx`;
  - Unicore `OBSVMB` rover chunks -> RTKLIB `input_unicore`;
  - RTCM3 correction chunks -> RTKLIB `input_rtcm3`;
  - converted `OBSVMCMPB` epochs -> RTKLIB observation input path chosen in
    Phase 5.
- [ ] Preserve chunk ordering per input stream.
- [ ] Do not assume rover and correction chunks arrive at identical rates.
- [ ] Keep byte offsets from `RtklibInputChunk.sessionOffsetBytes` in
  diagnostic status only; never write offsets into RTKLIB input streams.
- [ ] Add counters for:
  - rover bytes accepted;
  - correction bytes accepted;
  - RTKLIB decoder epochs/messages;
  - decoder errors;
  - stale correction warnings.
- [ ] Surface these counters in `RtklibEngineSnapshot`.

Tests:

- [ ] Native/Kotlin fake backend tests verify UBX, OBSVMB and RTCM3 chunks are
  routed by declared route, not receiver profile string guessing.
- [ ] Regression test verifies `OBSVMCMPB` is still rejected until Phase 5 is
  complete.

---

## Phase 5: UM980 OBSVMCMPB Converter Design

- [ ] Locate and document the UM980 protocol definition for `OBSVMCMPB`.
- [ ] Record exact source references:
  - UM980/N4 PDF section/table names;
  - local `um980-rtklib-pipeline` parser references if used;
  - any RTKLIB-EX Unicore decoder assumptions.
- [ ] Define an internal neutral observation model sufficient for RTKLIB input:
  - epoch time;
  - constellation/system;
  - satellite id;
  - signal/frequency code;
  - pseudorange;
  - carrier phase;
  - Doppler if available;
  - C/N0;
  - lock/loss flags;
  - half-cycle/ambiguity flags if present;
  - receiver clock/time-status fields if present.
- [ ] Decide converter output path:
  - direct native handoff to RTKLIB `obsd_t`/navigation structures; or
  - generated standard RTCM3/RINEX-like observation stream; or
  - RTKLIB-EX decoder patch accepting `OBSVMCMPB` directly.
- [ ] Prefer direct native handoff if it avoids lossy or expensive temporary
  encodings.
- [ ] Document any fields that cannot be represented safely.
- [ ] Add validation rules for incomplete `OBSVMCMPB` epochs.

Exit criteria:

- [ ] Converter design is documented in `docs/receiver-driver-api.md` or a new
  formal spec under `docs/specification/`.
- [ ] Tests can be written before implementation using golden UM980 samples.

---

## Phase 6: OBSVMCMPB Parser And Golden Tests

- [ ] Add a pure parser module or package for UM980 compact observations.
- [ ] Parser must be byte-level and safe for mixed binary/NMEA streams.
- [ ] Parser must resynchronise after corrupt frames without blocking raw
  recording.
- [ ] Reuse existing UM980 binary frame parser/CRC where possible.
- [ ] Add golden fixtures extracted from local samples without committing full
  raw sessions:
  - small byte fixture with one or more `OBSVMCMPB` frames;
  - expected decoded epoch summary;
  - expected satellite/signal counts.
- [ ] Do not commit user-sensitive full `samples/` captures.
- [ ] Add tests for:
  - valid frame decode;
  - CRC failure rejection;
  - truncated frame buffering;
  - mixed-stream resync;
  - satellite count and signal code mapping;
  - time/tag preservation.

Validation:

```sh
sh gradlew :receiver:unicore-n4:test :core:rtklib:test
python3 tools/um980_rtklib_sample_check.py samples/session-*
```

---

## Phase 7: OBSVMCMPB To RTKLIB Observation Adapter

- [ ] Implement converter from decoded `OBSVMCMPB` epochs to RTKLIB-native
  observation records or the selected Phase 5 input representation.
- [ ] Preserve epoch time with sub-second precision.
- [ ] Map constellation and signal identifiers explicitly; no best-effort
  guessing without warning counters.
- [ ] Drop only invalid observations, not whole epochs, unless epoch timing is
  invalid.
- [ ] Track dropped observation reasons:
  - unsupported signal;
  - invalid pseudorange/carrier phase;
  - missing time;
  - inconsistent satellite id;
  - RTKLIB handoff failure.
- [ ] Surface converter counters in `RtklibEngineSnapshot` and dashboard
  details.
- [ ] Add start validation that allows `OBSVMCMPB` only when converter support
  is compiled and enabled.
- [ ] Keep `OBSVMB` direct route available and preferred when selected.

Tests:

- [ ] Unit tests for signal/constellation mapping.
- [ ] Unit tests for invalid observation dropping.
- [ ] Replay test with committed minimal fixture produces non-empty RTKLIB
  observation handoff.
- [ ] Start-validation test:
  - without converter -> reject `OBSVMCMPB`;
  - with converter -> accept `OBSVMCMPB` route and record converter id.

---

## Phase 8: RTKLIB Solver Configuration

- [ ] Map `RtklibPreset.ROVER_KINEMATIC_RTK` to RTKLIB options:
  - forward-only;
  - dynamic rover;
  - NTRIP RTCM3 correction/base input;
  - enabled constellations consistent with incoming data.
- [ ] Map `RtklibPreset.TEMPORARY_BASE_STATIC_RTK` to RTKLIB static options:
  - forward-only;
  - static receiver;
  - correction/base input from NTRIP RTCM3;
  - outputs suitable for temporary-base coordinate selection.
- [ ] Explicitly disable unsupported modes:
  - forward/backward;
  - PPP via RTKLIB;
  - command-line app execution.
- [ ] Add minimal configurable options only if required for correctness:
  - elevation mask;
  - navigation systems;
  - output interval;
  - base position source if RTKLIB requires explicit metadata.
- [ ] Keep full RTKLIB option editor out of this completion plan.

Tests:

- [ ] Kotlin/native tests verify each preset produces expected RTKLIB options.
- [ ] Invalid route/preset combinations fail before native processing starts.

---

## Phase 9: Live Output And Status

- [ ] Poll RTKLIB output from native bridge without blocking the RTKLIB worker
  queue.
- [ ] Persist RTKLIB-generated NMEA to `rtklib-solution.nmea`.
- [ ] Persist RTKLIB `.pos` output to `rtklib-solution.pos`.
- [ ] Preserve sub-second UTC if RTKLIB emits it.
- [ ] Do not mix receiver-generated `receiver-solution.nmea` with RTKLIB NMEA.
- [ ] Emit lightweight RTKLIB status snapshots:
  - engine state;
  - fix class;
  - age of solution;
  - queue sizes;
  - dropped bytes;
  - decoded rover epochs;
  - decoded correction messages;
  - converter warnings/errors;
  - last RTKLIB warning/error.
- [ ] Update dashboard RTKLIB card without increasing raw-capture load.
- [ ] Keep mock-provider source selection explicit; do not auto-switch to
  RTKLIB solution based on quality.

Tests:

- [ ] Fake/native backend emits NMEA/POS and Kotlin writers persist lines.
- [ ] Dashboard mapper shows RTKLIB status only when enabled.
- [ ] RTKLIB error does not remove receiver solution from dashboard.

---

## Phase 10: Replay Harness With Real UM980 Samples

- [ ] Add a replay tool/test harness that can feed:
  - `receiver-rx.raw`;
  - `correction-input.raw`;
  - session metadata and init script.
- [ ] Ensure replay preserves byte order and relative stream timing where
  practical.
- [ ] Add tests for current local UM980 samples:
  - OBSVMCMPB-only capture is accepted only after converter is enabled;
  - correction input is detected when present;
  - solver emits at least one status update;
  - solver emits NMEA or POS when enough data is available.
- [ ] Add a small committed fixture derived from UM980 samples, not the full
  private recording.
- [ ] Compare output against `um980-rtklib-pipeline` or another known-good
  external pipeline for at least:
  - epoch count;
  - approximate coordinate;
  - fix status progression when available;
  - no gross time shifts.
- [ ] Clearly document when a sample is insufficient because it lacks
  corrections, ephemeris, observation span or compatible data.

Validation:

```sh
python3 tools/um980_rtklib_sample_check.py samples/session-*
sh gradlew :core:rtklib:test :receiver:unicore-n4:test
```

Host/CI:

```sh
./gradlew :app:assembleDebug
```

---

## Phase 11: u-blox Replay And Hardware Readiness

- [ ] Add u-blox M8T/M8P RAWX/SFRBX replay fixtures once user-provided data is
  available.
- [ ] Confirm RTKLIB direct UBX route works with:
  - RAWX;
  - SFRBX;
  - NTRIP RTCM3 corrections.
- [ ] Validate M8T is treated as raw/post-processing/timing receiver, not an
  in-device RTK receiver.
- [ ] Validate M8P internal RTK solution remains separate from RTKLIB solution.
- [ ] Add missing u-blox tests only after fixture availability; do not fake
  successful field readiness.

Status rule:

- [ ] Keep u-blox RTKLIB field status `Implemented, not field-tested` until a
  real device recording validates replay and live operation.

---

## Phase 12: Android Service And Failure Hardening

- [ ] Verify RTKLIB disabled path never constructs `RtklibNativeBridge`.
- [ ] Verify RTKLIB enabled but native load failure:
  - records receiver RX;
  - records correction input;
  - feeds NTRIP to receiver if configured;
  - marks RTKLIB failed;
  - keeps foreground service alive.
- [ ] Verify RTKLIB queue overflow drops only advisory RTKLIB input.
- [ ] Verify native crash risk is bounded as far as possible:
  - validate routes before native start;
  - keep native input sizes bounded;
  - avoid native file/socket/device operations;
  - avoid calling native code from raw read loop.
- [ ] Add ANR/performance smoke checks:
  - dashboard remains responsive;
  - recording notification remains updated;
  - raw RX byte count is continuous;
  - queue drop counters increase instead of blocking if RTKLIB lags.
- [ ] Ensure all native warnings/errors are copyable from UI via existing error
  UX where applicable.

Manual validation:

- [ ] UM980 live rover + NTRIP + RTKLIB enabled, recording for at least 15 min.
- [ ] USB disconnect/reconnect while RTKLIB enabled.
- [ ] App background/minimise while RTKLIB enabled.
- [ ] RTKLIB disabled regression recording.

---

## Phase 13: Documentation And Formal Specs

- [ ] Update formal requirements under `docs/specification/`:
  - RTKLIB native integration;
  - RTKLIB advisory isolation;
  - OBSVMCMPB converter;
  - RTKLIB output artifacts;
  - replay validation.
- [ ] Update `docs/specification/verification-matrix.md`.
- [ ] Update user docs:
  - enabling RTKLIB;
  - selecting u-blox RAWX/SFRBX profile;
  - selecting UM980 OBSVMB direct profile;
  - selecting UM980 OBSVMCMPB converter profile after converter exists;
  - interpreting RTKLIB dashboard card.
- [ ] Update `AGENTS.md` if the final architecture changes agent rules.
- [ ] Update `docs/superpowers/plan-status.md`:
  - keep `In progress` until native build and replay work pass;
  - mark `Implemented, not field-tested` only after CI/host build and replay;
  - mark `Done` only after real hardware validation for at least UM980 and one
    u-blox path.

---

## Phase 14: Final Quality Gate

- [ ] Run source hygiene:

```sh
git diff --check
```

- [ ] Run Python tooling tests:

```sh
python3 -m py_compile tools/update_rtklib_ex.py tools/um980_rtklib_sample_check.py tools/check_spec_requirements.py
python3 -m pytest tools/test_*.py
```

- [ ] Run formal spec check:

```sh
python3 tools/check_spec_requirements.py docs/specification
```

- [ ] Run JVM/Kotlin tests:

```sh
sh gradlew :core:workflow:test :core:rtklib:test :core:session:test :receiver:unicore-n4:test :receiver:ublox-m8:test
```

- [ ] Run app Kotlin compile:

```sh
sh gradlew :app:compileDebugKotlin
```

- [ ] Run host/CI Android native build:

```sh
./gradlew clean assembleDebug
./gradlew test
```

- [ ] If Termux cannot execute native Android packaging, state that explicitly
  and cite host/CI build results instead.
- [ ] Request code review with roles:
  - GNSS/RTK systems architect;
  - Android robust background-capture engineer;
  - Kotlin/native/JNI maintainer;
  - receiver-protocol reviewer for UM980/u-blox.
- [ ] Address must-fix review findings.
- [ ] Commit with a message that lists:
  - RTKLIB-EX commit;
  - native build status;
  - OBSVMCMPB converter status;
  - replay fixtures used;
  - validation commands and results.
- [ ] Push.

---

## Definition Of Complete

This plan is complete only when all of the following are true:

- [ ] RTKLIB-EX native library is compiled into the Android app on a supported
  Android build host or CI.
- [ ] `RtklibNativeBridge` uses real JNI/native backend functions.
- [ ] RTKLIB starts only when explicitly enabled.
- [ ] RTKLIB receives rover and correction data only after authoritative raw and
  correction sidecar appends.
- [ ] RTKLIB emits real `rtklib-solution.nmea` and/or `rtklib-solution.pos`.
- [ ] UM980 `OBSVMB` direct route is live-tested or replay-tested.
- [ ] UM980 `OBSVMCMPB` is either:
  - converted with tested converter; or
  - accepted by a documented RTKLIB-EX decoder patch with tests.
- [ ] Current UM980 sample sessions can be replayed to the maximum extent their
  contents allow, with clear pass/insufficient-data results.
- [ ] u-blox route is implemented and ready for M8T/M8P sample validation.
- [ ] RTKLIB failures do not stop receiver recording, NTRIP-to-receiver or
  dashboard monitoring.
- [ ] Formal specs, user docs, NOTICE/licensing and plan status are aligned.

---

## Known Risks

- Native RTKLIB crashes can still kill the app process if wrapper boundaries
  are wrong. Mitigate with strict route validation, bounded buffers and minimal
  native entry points.
- `OBSVMCMPB` field mapping errors can create plausible but wrong RTK
  solutions. Mitigate with golden fixtures, cross-checks against
  `um980-rtklib-pipeline`, and conservative rejection of unsupported signals.
- Android/Termux cannot prove final native APK packaging when local native SDK
  tools are unavailable. Treat CI/Windows/Linux Android Studio build as
  required evidence.
- RTKLIB-EX upstream behaviour may change. Keep a pinned commit and patch notes;
  do not track upstream HEAD automatically.
- Full replay equivalence depends on available corrections, ephemeris and
  observation span in the sample session.
