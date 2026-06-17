# RtkCollector — New Contributor Map

This is the orientation doc for a new contributor (human or agent). It points
to the right module, file or domain doc for a given concern, and names the
architectural rules a contributor must not violate. For the authoritative
specification of any particular subsystem, follow the links into the rest of
`docs/`.

## The one big idea

> The raw receiver byte stream is sacred. Everything else is optional.

Bytes coming off the GNSS receiver land in `receiver-rx.raw` byte-exact, with
no timestamps, no markers and no interpretation. Parsers, NTRIP, quality
monitoring, UI and a future RTKLIB engine are advisory consumers. If any of
them throws, the recorder keeps writing. If you ever find yourself routing
parser output into the raw write path, or gating raw writes on parser success,
you are breaking the architecture.

Everything in the layout below exists to enforce that invariant.

## Five rules that override personal preference

You will hit these in code review. Memorise them before you touch anything:

1. No app data in `receiver-rx.raw` — no timestamps, no markers, no metadata.
   Commands you sent go in `tx-to-receiver.raw`; events go in `events.jsonl`.
2. Capture path does not depend on Activity, Compose, parsers or NTRIP. The
   `RecordingForegroundService` owns the lifecycle. UI/Activity only sends
   start/stop intents and observes state.
3. Validate `WorkflowSpec` before any side effect. No USB open, no NTRIP TCP,
   no recorder file open until `WorkflowValidator` says yes.
4. `session.json` never contains secrets. Caster passwords/tokens live in
   `NtripSecretStore`; `session.json` gets only secret references or redacted
   metadata. `allowSecretsInSessionJson` must stay false.
5. Not a GIS app. No maps, shapefiles, feature collection, RTKLIB realtime in
   V1, or cartographic project management. Do not add the dependency, do not
   add the screen.

Source of these rules: [`AGENTS.md`](../AGENTS.md) § Non-Negotiable
Architecture Rules and [`docs/architecture.md`](architecture.md) § Capture
Rules.

## Module map (Gradle multi-module)

```text
rtkcollector/
├── app/                              Android app — Compose UI + foreground service
├── core/
│   ├── transport/                    SerialTransport abstraction (USB/BT/TCP/file)
│   ├── capture/                      CaptureRuntime + RawRecorder + AdvisoryFanout
│   ├── correction/                   NtripClient + Rtcm3Extractor
│   ├── session/                      Session metadata models + writers + filenames
│   ├── workflow/                     WorkflowSpec, WorkflowValidator, command plan
│   └── quality/                      Quality event models (boundary only)
└── receiver/
    ├── api/                          GnssReceiverDriver contract + data classes
    ├── unicore-n4/                   UM980/N4 driver (primary, only one app wires in)
    ├── ublox-m8/                     M8P / M8T (skeleton)
    └── generic-nmea-rtcm/            Generic NMEA + RTCM (skeleton)
```

Dependency direction: `app` → `core:*` and `receiver:*`. Receiver modules
depend on `receiver:api`. `core:*` modules do not depend on each other except
through small contracts (for example, `capture` depends on `transport`). No
`core:*` module depends on Android. That keeps them pure-Kotlin/JUnit-testable
on the JVM.

Current `app` wiring (`app/build.gradle.kts`): pulls in `core:capture`,
`core:correction`, `core:session`, `core:transport`, `core:workflow` and
`receiver:unicore-n4`. `core:quality` and the other receiver drivers are not
wired yet — they exist as boundaries and skeletons.

## Where to look for X

| You want to… | Start here |
|---|---|
| Add a new receiver | `receiver/api/GnssReceiverDriver.kt`, copy `receiver/ublox-m8/` as scaffold |
| Add a new V1 workflow | `core/workflow/WorkflowModel.kt`, then `WorkflowExamples.kt`, then `WorkflowValidator.kt` |
| Change what files a session writes | `core/session/SessionArtifacts.kt` (filenames) + `SessionWriters.kt` + `docs/session-format.md` |
| Add an NTRIP behaviour | `core/correction/NtripClient.kt` + `NtripRuntimeController.kt`; reconnect rules in `docs/ntrip-and-corrections.md` |
| Parse a new RTCM message type | `core/correction/Rtcm3Extractor.kt` |
| Parse UM980 ASCII/binary | `receiver/unicore-n4/Um980StreamParser.kt`, `Um980BinaryParser.kt`, `Um980LiveParsers.kt` |
| Add a UM980 receiver command | `receiver/unicore-n4/Um980CommandBuilder.kt` + `Um980CommandModels.kt`. Cite Unicore docs in the PR. |
| Change USB enumeration / permission | `app/usb/AndroidUsbSerialTransport.kt`, `app/ui/usb/UsbSelectionModels.kt` |
| Add a Compose screen | `app/ui/<area>/...`. Keep Menu and Start/Stop outside scrollable content. |
| Add a setting or profile | `app/profile/ProfileModels.kt`, `ProfileStores.kt`, with a migration in `ProfileStoreMigrations.kt` |
| Move logic that the foreground service does | `app/recording/RecordingForegroundService.kt` — but prefer to extract pure logic into a sibling testable class (see `RecordingStartPreflight`, `NtripUpdatePolicy`, `Um980BaudTransition`) |
| Touch secrets/credentials | `app/secrets/NtripSecretStore.kt` only. Never serialise into `session.json`. |
| Add a session-management feature | `app/sessions/FilesystemSessionBrowser.kt`, `SessionArchiveManager.kt` |

## The capture path, end to end

The piece every contributor must hold in their head:

```text
SerialTransport.readAvailable(maxBytes)            (core:transport)
   ↓
CaptureRuntime.readOnce()                          (core:capture)
   ↓
recorder.appendReceiverBytes(bytes)                ─→ receiver-rx.raw  ← AUTHORITATIVE
   ↓
advisoryFanout?.accept(bytes)                      ─→ wrapped in runCatching
   ↓ (AsyncAdvisoryFanout: bounded queue, drops bytes if full,
   ↓  emits one "advisory-queue-dropped" event, raw write continues)
   ↓
[parsers, quality monitor, dashboard state]
```

`CaptureRuntime.kt` is short — read it. Every advisory call is wrapped in
`runCatching` and emits an event, not an exception. That is the failure
isolation rule expressed in code. `AsyncAdvisoryFanout` is the bounded-queue
variant used by the foreground service so a slow parser cannot backpressure
raw writes.

For TX: `CaptureRuntime.sendToReceiver()` writes to `tx-to-receiver.raw`
before the transport write. `injectCorrectionBytes()` adds a duplicate write
to `correction-input.raw`, with `correction-input.rtcm3` as a same-byte
compatibility copy where available. That is deliberate — corrections received
and corrections transmitted are accounted separately.

## The workflow model

`core/workflow/WorkflowModel.kt` is the system vocabulary. A `WorkflowSpec`
composes:

- `receiverRole` — `ROVER` / `BASE_CALIBRATION` / `FIXED_BASE` / `REPLAY_TEST`
- `correctionSource` — sealed class: `None`, `Ntrip`, `LocalBaseStream`,
  `FileReplay`, `ExternalSerialOrTcp`
- `correctionTargets` — set of `RECEIVER` / `RTKLIB` (RTKLIB is V2 only)
- `solutionEngines` — set of `DEVICE_INTERNAL` / `RECEIVER_PPP` /
  `RTKLIB_REALTIME` / `POSTPROCESSING_PIPELINE`
- `observationRequirement` — `NONE` / `RAW_IF_SUPPORTED` / `RAW_REQUIRED` /
  `RTKLIB_COMPATIBLE_REQUIRED`
- `baseContext` — sealed class: `KnownStation`, `ManualCoordinate`,
  `NtripMountpoint`, etc.
- `recording`, `qualityMonitoring`, `safety` — capability/policy structs
- `receiverCapabilities` — what the receiver actually supports; gates
  validation

Five V1 workflows are validated: plain rover, rover+NTRIP-to-receiver,
temporary-base prep, temporary-base prep with NTRIP, fixed-base, replay/test.
RTKLIB realtime is deferred to V2 — primitives are kept around for validation,
but no V1 user path exposes it.

`WorkflowValidator` runs before any side effect. Errors block start; warnings
require user acknowledgement.

## Reading order for someone new

In this order, you will have a working mental model in roughly 90 minutes:

1. [`README.md`](../README.md), [`AGENTS.md`](../AGENTS.md),
   [`docs/architecture.md`](architecture.md) — the law.
2. [`docs/workflows.md`](workflows.md) — the vocabulary.
3. [`docs/session-format.md`](session-format.md) — the on-disk artifacts.
4. `core/workflow/WorkflowModel.kt` + `WorkflowValidator.kt` — the validated
   spec.
5. `core/capture/CaptureRuntime.kt` + `AdvisoryFanout.kt` — the capture
   invariant in code.
6. `receiver/api/GnssReceiverDriver.kt` — driver contract.
7. `app/recording/RecordingForegroundService.kt` — how it all comes together
   on Android.
8. `docs/superpowers/specs/<latest date>-*.md` — what the last few features
   actually did. Skim two or three recent ones.

## Test layout and verification

- Pure Kotlin modules (`core:*`, `receiver:*`): JUnit 5
  (`useJUnitPlatform()`), runs anywhere with JDK 17. `./gradlew test` is fine.
- `app/` JVM tests: also JUnit 5, but uses Android-named classes that compile
  against `compileDebugKotlin`. Use `:app:compileDebugKotlin` to validate
  Kotlin/UI locally; full `assembleDebug` needs SDK native binaries such as
  `aapt2` that do not run on every host (the Termux/aarch64 caveat
  `AGENTS.md` warns about).
- Pattern: pure logic is extracted out of the service into testable Kotlin
  classes (`RecordingStartPreflight`, `NtripUpdatePolicy`,
  `Um980BaudTransition`, `PersistentReceiverWritePolicy`). Follow this
  pattern — anything you put inside `RecordingForegroundService` directly is
  essentially untestable.

## Spec-first workflow

This repo is spec-first. The plumbing for it:

- `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md` — design doc, written
  before code.
- `docs/superpowers/plans/YYYY-MM-DD-<topic>.md` — implementation plan,
  derived from spec.
- Top-level domain docs ([`workflows.md`](workflows.md),
  [`session-format.md`](session-format.md),
  [`architecture.md`](architecture.md),
  [`receiver-driver-api.md`](receiver-driver-api.md),
  [`ntrip-and-corrections.md`](ntrip-and-corrections.md),
  [`android-background-operation.md`](android-background-operation.md)) are
  the durable specifications — update them when behaviour changes.

For non-trivial work, the expected order is: update domain doc → write spec
→ write plan → tests-first implementation → code review → verify.
[`AGENTS.md`](../AGENTS.md) § Superpowers Plugin Usage lists which skill to
use at each step.

## Formal Specification Workflow

The canonical requirements live in `docs/specification/`. Superpowers specs and
plans are design history and implementation history. For non-trivial changes:

1. Find affected requirement IDs.
2. Update formal specs if behaviour changes.
3. Update user-facing docs separately when users need instructions.
4. Update `docs/specification/verification-matrix.md`.
5. Update `docs/superpowers/plan-status.md` if large-plan status changes.

## Quick glossary

- NTRIP — protocol to fetch RTCM corrections over HTTP from a *caster*; a
  *mountpoint* is one stream on a caster.
- RTCM3 — binary correction format. `Rtcm3Extractor` frames messages out of a
  byte stream.
- GGA — NMEA position sentence. NTRIP servers sometimes require the client to
  upload a GGA so the caster can pick a network station.
- Rover — receiver computing its own position using corrections.
- Base / fixed-base — receiver at a known coordinate, generating corrections
  for downstream rovers.
- Temporary-base preparation — recording session whose purpose is to later
  derive an accepted base coordinate. Not itself a final operating mode.
- PPP — Precise Point Positioning. Some receivers can do this on-board; this
  is not the same as the receiver's normal device solution and must be
  modelled separately.
- RX vs TX — repo convention: "receiver TX" means the hardware data line going
  *out of* the receiver. "Receiver RX" is bytes coming *from* the receiver
  into the app. The file `tx-to-receiver.raw` is what the app transmitted
  toward the receiver.

## Things that will trip you up

- "TX to receiver" naming. Do not reverse it.
- NTRIP `User-Agent` must say `NTRIP RtkCollector/...` or strict casters
  return `403`.
- UM980 baud-switch sequence is not a normal command sequence — separate the
  switch from post-switch logs and drain through the recorded RX path. See
  `Um980BaudTransition.kt`.
- M8T is not M8P. M8T is raw/timing/post-processing; it is not an internal RTK
  rover. Keep their capabilities distinct.
- Long-averaging a standalone fix to derive a base coordinate is a fallback,
  not equivalent to static RTK or PPP. The base-position candidate ranking
  enforces this.
- Do not run two Gradle compiles in parallel on shared storage (Android shared
  workspace) — Kotlin incremental caches collide and you will chase phantom
  unresolved references.
