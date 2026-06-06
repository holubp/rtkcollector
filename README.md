# RtkCollector

RtkCollector is a GPL-3.0-or-later Android GNSS receiver companion for robust
byte-exact receiver recording, NTRIP correction intake, correction routing,
receiver control and temporary-base preparation workflows.

The first receiver target is the Unicore UM980 / Unicore N4 family. Other early
targets are u-blox M8P, u-blox M8T and a generic NMEA + RTCM receiver profile.

## Development Status

This repository is in bootstrap status. The current code is a minimal
Kotlin-compatible skeleton and specification set, not a production Android field
application.

The bootstrap Android UI is a dry-run workflow launcher and recording monitor.
It validates workflow and receiver-profile choices with a derived command plan,
but it does not yet implement USB capture, receiver serial TX, NTRIP networking,
foreground services or real session file writing. On local Termux, APK assembly
may be blocked by the Android Gradle Plugin invoking SDK native binaries such as
`aapt2` that are not usable on this Termux/Android host; use
`:app:compileDebugKotlin` to validate the Kotlin/UI code locally.

## Core Goals

- Keep the raw receiver capture path authoritative and byte-exact.
- Continue recording even if parsing, UI display, NTRIP, quality monitoring or
  receiver-specific decoding fails.
- Support USB serial GNSS receiver capture with bidirectional receiver command
  and correction injection.
- Provide receiver-agnostic driver boundaries for Unicore, u-blox and generic
  NMEA/RTCM devices.
- Produce session exports that downstream Python processing can consume.

## Non-Goals

RtkCollector is intentionally not a GIS application. It excludes maps,
shapefiles, GIS editing, field-feature collection and cartographic survey
project management.

## High-Level Architecture

The app is designed as a service-first receiver data pipeline:

1. USB, Bluetooth, TCP or file replay transport receives bytes.
2. A capture queue feeds an append-only raw recorder.
3. Session metadata, transmitted receiver commands and quality events are
   written to sidecar files.
4. Parsers and receiver drivers observe the stream only as advisory consumers.
5. Quality monitoring and UI state derive from advisory parser output.

The capture path must not depend on the Android Activity lifecycle, Compose,
NTRIP availability or parser success.

Workflow selection is modelled as validated `WorkflowSpec` instances that
compose receiver role, correction source/target, solution engines, base context,
base-position candidate generation, recorded artifacts and safety rules. See
[Workflows](docs/workflows.md) and [User Workflows](docs/user-workflows.md).
Version 1 covers receiver-side rover, NTRIP-to-receiver, temporary-base,
fixed-base and replay workflows. In-phone RTKLIB real-time solution is deferred
to version 2.

## Licence

RtkCollector is licensed under GPL-3.0-or-later. See [LICENSE](LICENSE) and
[NOTICE](NOTICE).
