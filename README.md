# RtkCollector

RtkCollector is a GPL-3.0-or-later Android GNSS receiver companion for robust
byte-exact receiver recording, NTRIP correction intake, correction routing,
receiver control and temporary-base preparation workflows.

RtkCollector is built for external USB GNSS receivers. It is not a phone-GNSS
app and it is not a GIS app: there are no maps, shapefiles, feature forms or
survey project-management tools.

The practical focus is currently:

- Unicore UM980 / Unicore N4 family for in-device RTK/PPP rover and base
  workflows.
- u-blox M8T for raw/timing recording and RTKLIB/post-processing style
  workflows.

u-blox M8P and ZED-F9P-class receivers may work in the same broad USB,
NTRIP-to-receiver and raw-recording modes where their serial protocol is
compatible, but precise-positioning workflows on those devices are not
supported or field-tested by the author because the author does not currently
have access to those receivers.

## What You Can Use It For

RtkCollector is meant to cover three basic field patterns:

1. Plain rover recording: connect an external receiver and record its byte-exact
   output for later inspection or post-processing.
2. Rover with NTRIP: let Android connect to an NTRIP caster, record the
   correction stream, and forward RTCM corrections to the receiver while
   continuing to record the receiver stream.
3. Base setup: record data for a stationary temporary base, optionally compare
   or average positions while switching between several NTRIP mountpoints,
   accept or import a base coordinate, then run a fixed base that can publish
   RTCM corrections through a caster such as rtk2go for a rover to consume.

Start with [User Workflows](docs/user-workflows.md) for a practical guide.
Receiver status is summarised in
[Supported Receivers](docs/supported-receivers.md).

## Development Status

This repository is in experimental bootstrap status. The current code includes
specifications, pure Kotlin workflow/session/correction primitives, an Android
UI and experimental USB/NTRIP recording workflows. It is not yet a production
Android field application.

The Android UI now opens to an experimental Compose dashboard with compact
Position, Fix, NTRIP and Files cards, plus Menu and Start/Stop controls that are
kept outside scrolling content. The existing real-recording path remains
available for USB device permission, UM980 runtime commands, optional NTRIP
client settings, foreground-service recording, live byte counters and
stop/shutdown control. On local Termux, APK assembly may be blocked by the
Android Gradle Plugin invoking SDK native binaries such as `aapt2` that are not
usable on this Termux/Android host; use `:app:compileDebugKotlin` to validate
the Kotlin/UI code locally.

Formal contributor-facing requirements live in
[docs/specification](docs/specification/index.md). User workflow documentation
lives in [docs/user-workflows.md](docs/user-workflows.md). Superpowers specs and
plans are retained as design and implementation history under
[docs/superpowers](docs/superpowers/plan-status.md).

## Privacy And Publication Status

RtkCollector privacy handling and Play publication declarations are tracked in
[PRIVACY.md](PRIVACY.md) and [docs/play-publication.md](docs/play-publication.md).

## Branding Assets

Source logo files live in `logos/`. Android launcher and compact badge assets
are generated from `logos/rtkcollector_logo.png` with:

```bash
python3 tools/generate_brand_assets.py
```

Generated launcher and badge assets are checked in so Android Studio and CI
builds do not depend on local image tooling.

Experimental u-blox M8T support configures UBX raw/timing output and can feed
receiver-derived positions into Android mock location while recording.

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
