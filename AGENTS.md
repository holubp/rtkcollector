# Agent Guide

This repository welcomes agentic coding, but agents must preserve the GNSS
recording architecture. Read this file before making changes, then consult the
linked docs for the detailed specification.

## Project Identity

RtkCollector is a GPL-3.0-or-later Android GNSS receiver companion for robust
byte-exact receiver recording, NTRIP correction intake, correction routing,
receiver control and temporary-base preparation workflows.

Primary receiver targets:

- Unicore UM980 / Unicore N4 family.
- u-blox M8P.
- u-blox M8T.
- Generic NMEA + RTCM receivers.

Hard non-goals:

- no maps;
- no shapefiles;
- no GIS editing;
- no field-feature collection;
- no cartographic survey project management.

## Non-Negotiable Architecture Rules

- The raw receiver capture path is authoritative and byte-exact.
- Parser, UI, NTRIP, quality-monitor and future RTKLIB failures must not stop
  or mutate raw recording while transport and storage still function.
- Do not put timestamps, markers or app metadata into `receiver-rx.raw`.
- Commands and correction bytes sent from the app to the receiver belong in
  `tx-to-receiver.raw`, not in the receiver RX stream.
- Correction bytes received from NTRIP or another correction source belong in a
  correction sidecar such as `correction-input.raw`.
- `session.json` must not contain passwords, tokens or raw NTRIP credentials.
  Store only redacted metadata or secret references.
- Android recording must be foreground-service owned. Activity and UI code may
  control and observe recording, but must not own the capture lifecycle.
- Workflow changes must use validated `WorkflowSpec` models rather than
  unrelated ad-hoc UI modes.
- In-phone RTKLIB real-time solution is version 2 work. Do not make it required
  for V1 recording, NTRIP-to-receiver, temporary-base or fixed-base workflows.
- RTKLIB-EX integration must be lazy and route-planned. Do not load native
  RTKLIB libraries or start RTKLIB workers unless the validated workflow
  explicitly enables RTKLIB. Direct supported routes are preferred, such as
  u-blox RAWX/SFRBX to `input_ubx`, UM980 OBSVMB to `input_unicore`, and RTCM3
  corrections to `input_rtcm3`; UM980 OBSVMCMPB requires a named converter or
  explicit decoder support before use.

## Specification-First Workflow

Before implementing non-trivial behaviour:

1. Inspect the current repository state and branch.
2. Update the relevant specification under `docs/`.
3. Keep docs, workflow models, session metadata and tests consistent.
4. Add focused tests before or alongside reusable Kotlin logic.
5. Keep commits small and reviewable.
6. Avoid broad rewrites unless the specification explicitly requires them.

When a user or reviewer manually corrects the perceived status of a plan,
validate that correction against repository evidence before accepting it. Check
source files, tests, docs and known manual-test gaps, then update the shared
plan tracker if the correction is accurate.

Important docs:

- `docs/workflows.md` for workflow concepts and validation rules.
- `docs/user-workflows.md` for V1 user-facing workflows.
- `docs/architecture.md` for service-first capture architecture.
- `docs/session-format.md` for session artifacts and metadata.
- `docs/receiver-driver-api.md` for receiver-driver boundaries.
- `docs/ntrip-and-corrections.md` for correction routing.
- `docs/android-background-operation.md` for Android robustness rules.
- `docs/superpowers/plan-status.md` for the current cross-agent status of
  larger Superpowers plans.

Formal specifications and user-facing documentation have different audiences
and must stay distinct. Formal specifications are for AI agents and contributors:
they should be precise, stable, unambiguous and suitable for consistency checks
and verification matrices. User-facing documentation is for operators: it should
be friendly, task-oriented and readable by both new users and advanced power
users. Superpowers brainstorming/spec/plan files are source material and
traceability history; they are not a substitute for the canonical formal
specification.

When changing behaviour, identify affected formal requirement IDs from
`docs/specification/` before implementation. If the behaviour is not covered,
add or update formal requirements first or in the same branch. Keep
`docs/specification/verification-matrix.md` aligned with tests, manual checks
and known gaps.

## Plan Status Tracking

`docs/superpowers/plan-status.md` is the GitHub-tracked source of truth for
large-plan status. Keep it current when implementing, reviewing, validating or
reclassifying work. It must stay plain Markdown so different AI agents and
human contributors can update it through normal pull requests.

Status updates must be evidence-based:

- Mark `Done` only when functionality is present in code/docs and no specific
  open work remains beyond normal regression testing.
- Mark `Implemented, not field-tested` when code and tests exist but real
  hardware, Android vendor behaviour, SAF provider behaviour or caster/provider
  validation is still missing.
- Mark `In progress` when current code or uncommitted work exists but behaviour
  is not settled.
- Mark `Open` when the work is planned or discussed but not implemented end to
  end.

Do not let plan status drift from reality. If implementation changes a plan's
state, update the tracker in the same branch or explain why it was left
unchanged.

## Receiver And Protocol Rules

- Do not add risky receiver command sequences without source documentation,
  tests where practical and clear user-visible warnings.
- UM980/N4 commands should be grounded in the Unicore documentation and known
  local pipeline evidence where applicable.
- Treat `TX to receiver` as app transmit toward the receiver serial input. Do
  not describe this as "receiver TX", which means the opposite hardware line.
- NTRIP stream and sourcetable requests must use an NTRIP-client-style
  `User-Agent` such as `NTRIP RtkCollector/1.0-RC1`; some strict casters reject a
  generic app-style user agent with `403` even when credentials are valid.
- Keep UM980, M8P, M8T and generic NMEA/RTCM capabilities distinct. M8T is a
  raw/timing/post-processing receiver, not an internal RTK float/fix rover like
  M8P.
- Long-term averaging is a fallback base-position strategy, not equivalent to
  PPP/static RTK or a known control point.
- Temporary-base recording and fixed-base operation are separate phases. A
  temporary base is a stationary receiver used to determine a coordinate from
  RTK against another base, PPP, static/post-processing or fallback averaging.
  A fixed base uses an already accepted coordinate to configure base output.
- Rover workflows must not use command profiles that put the receiver in
  `MODE BASE`. Temporary-base workflows may use rover-mode commands when the
  goal is coordinate determination from another base, PPP or raw/static
  post-processing. Fixed-base workflows must not use command profiles that put
  the receiver in `MODE ROVER`.
- Receiver command validation and compilation must follow the selected command
  profile receiver family. Do not infer UM980 command syntax from a stale or
  unset receiver selector when a u-blox command profile is selected.
- Dashboard coordinate actions are intentionally compact: tapping coordinates
  copies them, `Base` moves toward fixed-base use of the current coordinate,
  and `Avg` starts a live average that must stop if the fix type changes.
  Live coordinate averaging must be scoped to one active recording session and
  must not seed or continue from stale dashboard coordinates from a previous
  recording.

## Android Implementation Rules

- Foreground service owns recording, wake lock and session writers.
- UI code should be thin: collect user intent, display state and send explicit
  start/stop commands.
- Compose UI work must keep Menu and Start/Stop outside scrollable content so
  the recording controls remain visible across phone/tablet portrait and
  landscape layouts.
- Profile editors must keep built-in app profiles viewable but read-only; users
  copy built-ins before editing. Do not hide built-ins behind disabled Edit
  buttons that prevent inspection.
- Multiline init/shutdown command editing must preserve hardware-keyboard text
  editing semantics. Arrow keys and arrow-key modifier combinations stay inside
  the active text field; Tab and Shift+Tab are the cross-field navigation keys.
- Android SAF storage locations must be selected with the system folder picker
  and persisted read/write URI permission. Do not make users hand-enter SAF tree
  URIs.
- Keep raw capture independent from Compose, Activity lifecycle and advisory
  parsers.
- Empty USB reads during an active recording are not proof of healthy capture.
  If receiver-originated bytes stop beyond the configured stall threshold, the
  foreground service must persist a session event, report degraded USB capture
  and attempt receiver reconnect/re-init while preserving existing artifacts.
  Do not clear USB RX-stall state from unrelated NTRIP or serial TX activity;
  clear it only when receiver bytes resume or reconnect succeeds.
- Do not broadcast full dashboard state from high-rate capture loops on every
  receiver read. Throttle routine UI state broadcasts; keep lifecycle, error,
  start, stop and reconnect state changes immediate.
- Do not read, migrate or normalise profile stores from high-rate service
  broadcast handlers. Cache planned dashboard configuration and refresh it only
  after explicit settings/profile changes.
- Screen monitoring, Android mock-location publishing, coordinate averaging,
  frequency measurement, future RTKLIB-EX processing and future caster upload
  must not run on the raw capture path. They consume advisory snapshots or
  bounded queues after `receiver-rx.raw` has already been appended.
- JNI, NDK and other native-library boundaries must be treated as unsafe input
  boundaries. Validate Java strings, byte arrays, handles and allocation results
  before dereferencing; use scoped/RAII ownership for JNI resources; and follow
  downstream C API nullability contracts exactly. Passing `NULL` is allowed only
  where the target API documents `NULL` as valid. If the target API documents an
  empty string or another sentinel for "disabled", pass that sentinel instead.
- For receivers with a documented best-navigation output such as UM980
  `BESTNAV/BESTNAVB`, the primary monitoring solution must be a transparent
  pass-through of that receiver solution. Mock-provider resampling is separate
  and must run only when mock output is enabled.
- Android mock `Location.altitude` must use ellipsoidal height. The public
  mock-location API does not allow ordinary apps to inject complete GNSS
  satellite status or satellite sky positions; expose those limits clearly.
- Record receiver RX, app TX to receiver, correction input, events, quality and
  metadata as separate artifacts.
- If an established NTRIP stream ends or expected correction bytes stop
  arriving, treat it as a degraded reconnect condition unless authentication or
  authorisation failed. NTRIP reconnect and receiver TX failures must remain
  separate from byte-exact receiver RX recording.
- Validate workflow and command safety before sending receiver commands,
  connecting NTRIP or starting real recording.
- Do not add map, GIS, shapefile or feature-collection dependencies.

## Superpowers Plugin Usage

Use the Superpowers plugin as the default agentic workflow for non-trivial work.
In this Codex environment, Superpowers skills are installed and load natively;
do not add generated local plugin state to the repository.

Use these skills deliberately:

- `superpowers:brainstorming` before creative feature design or behaviour
  changes.
- `superpowers:writing-plans` for multi-step implementation plans.
- `superpowers:test-driven-development` for new reusable logic, parsers,
  validators and protocol helpers.
- `superpowers:systematic-debugging` before changing code to fix an observed
  failure.
- `superpowers:subagent-driven-development` when executing a written plan with
  independent tasks.
- `superpowers:dispatching-parallel-agents` for independent research, review or
  implementation work that can safely run in parallel.
- `superpowers:requesting-code-review` before merging or after major feature
  work.
- `superpowers:receiving-code-review` before applying reviewer feedback.
- `superpowers:verification-before-completion` before claiming work is done.
- `review-and-commit` when the user asks for `$review-and-commit`, commit,
  push, merge or an equivalent final quality gate.

When using subagents, give each agent precise scope and constraints. For this
repo, useful reviewer roles include:

- GNSS/RTK systems architect.
- Android robust background-capture engineer.
- Kotlin/domain-model maintainer.
- Receiver-protocol reviewer for UM980, u-blox or generic NMEA/RTCM work.

Do not use subagents as a substitute for final local verification. The
coordinating agent remains responsible for inspecting the diff, running relevant
tests and reporting exact results.

## Validation Expectations

Prefer the strongest feasible verification for the touched code:

- Pure Kotlin model/parser/session/correction changes: run targeted module
  tests and broader `./gradlew test` when the environment supports it.
- Android app changes: run `:app:compileDebugKotlin`; run `assembleDebug` on a
  host where Android SDK native tools can execute.
- Android Gradle/AGP task changes must preserve Android Studio compatibility
  aliases used by IDE import/build actions. Keep `:app:unitTestClasses` and
  `:app:androidTestClasses` available unless verified obsolete, and check them
  with `sh gradlew :app:unitTestClasses :app:androidTestClasses --dry-run`.
  In Termux, a real Android-test/resource compile may still fail on the known
  non-runnable Maven `aapt2`; use dry-run for task-selection validation there
  and run full Android test/resource packaging on Windows Android Studio, CI or
  another host with working Android SDK native tools.
- Hardware-facing UM980 changes: document manual hardware smoke tests and the
  exact receiver/USB/baud assumptions.
- UM980 live parsers must be byte-level for mixed NMEA, UM980 ASCII and UM980
  binary streams. Do not feed arbitrary binary bytes into line-oriented parsers.
- UM980 satellite-monitor messages do not share one universal constellation or
  satellite-ID layout. `OBSVMB`/`OBSVMCMPB` use channel tracking status, while
  `BESTSATB` uses the manual's Satellite System enum and a split 32-bit
  Satellite ID field; keep message-specific tests for both documented and
  observed firmware layouts.
- RTCM MSM decoders must parse the complete MSM header before satellite and
  signal masks, including external clock indicator, divergence-free smoothing
  indicator and smoothing interval fields. When changing MSM parsing, verify
  signal IDs against an independent decoder such as RTKLIB `convbin` or a
  matching archived RINEX observation header.
- Derived `receiver-solution.nmea` exports must preserve sub-second UTC from
  structured/binary receiver telemetry and must not copy binary/noise fragments
  that merely look like dollar-prefixed lines.
- JNI/native changes must include source or native tests for pointer
  nullability, resource release and downstream C API sentinel contracts. For
  RTKLIB and similar C libraries, review the upstream function comments before
  deciding whether a pointer may be null.
- User-provided debug recordings should be expected under `samples/debug/`.
  Treat `samples/` as local evidence only; do not commit those captures.
- App-distributed init/shutdown command profiles are built-ins: keep them
  read-only in the UI, sync same-id/same-name copies to the current app default
  on update, and require users to copy before editing.
- UM980 profile-to-runtime baud changes must keep receiver baud-switch commands
  separate from post-switch mode/log commands: send init, send the baud switch,
  reconfigure the host serial bridge without purging RX/TX, drain through the
  recorded RX path, then send mode/log commands.
- On Android shared-storage workspaces, avoid running multiple Gradle compiles
  in parallel. Kotlin incremental cache files can collide and produce false
  unresolved-reference errors; rerun verification sequentially after stopping
  daemons.

Known local caveat: Android SDK Java tools work in Termux, but Google native
tools such as `aapt2` may be x86-64 Linux binaries and may not run on this
aarch64 Android/noexec environment. Do not misreport that as a source failure.

## Repository Hygiene

- Do not commit local agent artifacts such as `.superpowers/` unless the user
  explicitly asks for them.
- Do not commit IDE junk, Gradle caches, local SDK files or generated temporary
  captures.
- Never revert user changes unless the user explicitly asks.
- If a worktree is dirty, separate your changes from pre-existing changes in
  status and final reporting.
