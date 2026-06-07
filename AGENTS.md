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

## Specification-First Workflow

Before implementing non-trivial behaviour:

1. Inspect the current repository state and branch.
2. Update the relevant specification under `docs/`.
3. Keep docs, workflow models, session metadata and tests consistent.
4. Add focused tests before or alongside reusable Kotlin logic.
5. Keep commits small and reviewable.
6. Avoid broad rewrites unless the specification explicitly requires them.

Important docs:

- `docs/workflows.md` for workflow concepts and validation rules.
- `docs/user-workflows.md` for V1 user-facing workflows.
- `docs/architecture.md` for service-first capture architecture.
- `docs/session-format.md` for session artifacts and metadata.
- `docs/receiver-driver-api.md` for receiver-driver boundaries.
- `docs/ntrip-and-corrections.md` for correction routing.
- `docs/android-background-operation.md` for Android robustness rules.

## Receiver And Protocol Rules

- Do not add risky receiver command sequences without source documentation,
  tests where practical and clear user-visible warnings.
- UM980/N4 commands should be grounded in the Unicore documentation and known
  local pipeline evidence where applicable.
- Treat `TX to receiver` as app transmit toward the receiver serial input. Do
  not describe this as "receiver TX", which means the opposite hardware line.
- Keep UM980, M8P, M8T and generic NMEA/RTCM capabilities distinct. M8T is a
  raw/timing/post-processing receiver, not an internal RTK float/fix rover like
  M8P.
- Long-term averaging is a fallback base-position strategy, not equivalent to
  PPP/static RTK or a known control point.

## Android Implementation Rules

- Foreground service owns recording, wake lock and session writers.
- UI code should be thin: collect user intent, display state and send explicit
  start/stop commands.
- Keep raw capture independent from Compose, Activity lifecycle and advisory
  parsers.
- Record receiver RX, app TX to receiver, correction input, events, quality and
  metadata as separate artifacts.
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
- Hardware-facing UM980 changes: document manual hardware smoke tests and the
  exact receiver/USB/baud assumptions.

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
