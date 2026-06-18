# u-blox M8T And Android Mock Provider Design

## Purpose

Add practical u-blox support starting with the u-blox M8T, and add a generic
Android mock-location output path driven by the best receiver solution currently
available to RtkCollector.

The first practical device target is M8T because it is available for real
testing. M8P and F9P should be kept in the model where the same UBX protocol
tooling naturally applies, but V1 must not claim untested field behaviour for
devices that are not available.

This work must preserve the existing recording architecture:

- raw receiver capture remains byte-exact and authoritative;
- parser, dashboard, NTRIP, quality-monitor and mock-provider failures must not
  stop or mutate raw recording while transport and storage still function;
- bytes sent from the app to the receiver are recorded in `tx-to-receiver.raw`;
- derived solution outputs and mock-provider events are sidecar/session state,
  not receiver RX data.

## Scope

In scope for V1:

- u-blox UBX frame builder and checksum validation;
- readable `!UBX ...` command-script compiler;
- M8T built-in editable/copyable command profiles;
- byte-level UBX stream parsing for M8T recording and status;
- receiver-agnostic best-solution selection;
- Android mock-location publishing while a recording/session workflow is active;
- documentation and focused tests for command compilation, parser behaviour,
  solution selection and mock-provider adapter logic.

Out of scope for V1:

- live mock location without an active recording/session;
- RTKLIB integration;
- treating M8T as an internal RTK float/fix rover;
- claiming tested F9P behaviour;
- map, GIS, shapefile or feature-collection functionality.

## Architecture

The design adds three bounded pieces.

### u-blox Protocol Layer

`receiver/ublox-m8` owns u-blox protocol mechanics:

- UBX frame building;
- UBX checksum calculation and verification;
- `!UBX ...` command-script parsing and compilation;
- M8T profile definitions;
- byte-level stream parsing for UBX and optional NMEA.

This layer must not own recording. It consumes byte chunks from the advisory
parser fanout and emits telemetry/candidate solution events. Parser failure is
logged and counted, but raw recording continues.

### Best Solution Layer

A receiver-neutral solution layer, preferably a small `core:solution` module or
a tightly scoped `core:quality` package, accepts solution candidates from:

- UM980 telemetry;
- u-blox telemetry;
- generic NMEA;
- future RTKLIB or other solution engines.

It produces a canonical `BestSolutionSnapshot` for both dashboard display and
Android mock location.

### Android Mock Provider Adapter

The mock provider implementation lives in `app` behind a small interface so
core modules do not depend on Android APIs. It is controlled by the foreground
recording service and runs only while a recording/session workflow is active.

The adapter publishes the current `BestSolutionSnapshot` when mock output is
enabled, the app is configured as Android's mock-location app, and the snapshot
is fresh and valid.

## u-blox Command Scripts

u-blox configuration commands in user profiles should use readable
RTKLIB-style lines:

```text
!UBX CFG-MSG 2 21 0 0 0 1 0 0
```

These lines are not receiver-native ASCII commands. RtkCollector must validate
and compile them into binary UBX frames before sending. The receiver receives
binary frames beginning with `B5 62`; the literal `!UBX ...` text is not sent to
the device.

Malformed lines block session start before any receiver command is sent. Error
messages should include the offending line number and enough detail to fix the
script.

The actual binary bytes sent to the receiver must be recorded in
`tx-to-receiver.raw`.

The compiler should initially support the numeric form used by existing
RTKLIB/RTKLIB-EX scripts:

```text
!UBX <CLASS-NAME> <MESSAGE-NAME> <payload byte/int fields...>
```

For V1, the compiler must support the command families used by the built-in
M8T profiles:

- `CFG-MSG`;
- `CFG-GNSS`;
- `CFG-NAV5`;
- `CFG-RATE`.

Unsupported `!UBX` command names fail closed with a line-numbered error. The
compiler should also accept numeric class/message identifiers where that is the
least ambiguous representation.

## M8T Built-In Profiles

Add three built-in, editable/copyable M8T command profiles.

### u-blox M8T Raw 1 Hz Safe

Purpose: conservative raw/timing capture for stable long recordings and
post-processing.

Expected behaviour:

- configure `CFG-RATE 1000 1 1`;
- enable `RXM-RAWX` on USB;
- enable `RXM-SFRBX` on USB;
- enable `TIM-TM2` on USB;
- disable noisy default messages when they are not needed.

### u-blox M8T Raw 5 Hz RTKLIB-EX

Purpose: match the known RTKLIB-EX style workflow used by the project owner.

Use the supplied script as the source profile, normalized only where needed for
safe compilation:

```text
!UBX CFG-MSG 2 21 0 0 0 1 0 0
!UBX CFG-MSG 2 19 0 0 0 1 0 0
!UBX CFG-MSG 13 3 0 0 0 1 0 0
!UBX CFG-GNSS 0 32 32 1 0 8 16 0 65537
!UBX CFG-GNSS 0 32 32 1 1 1 3 0 65537
!UBX CFG-GNSS 0 32 32 1 2 4 8 0 65537
!UBX CFG-GNSS 0 32 32 1 3 8 16 0 0
!UBX CFG-GNSS 0 32 32 1 4 0 8 0 0
!UBX CFG-GNSS 0 32 32 1 5 0 3 0 0
!UBX CFG-GNSS 0 32 32 1 6 8 14 0 65537
!UBX CFG-NAV5 1 3 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
!UBX CFG-MSG 240 0 0 0 0 0 0 0
!UBX CFG-MSG 240 1 0 0 0 0 0 0
!UBX CFG-MSG 240 2 0 0 0 0 0 0
!UBX CFG-MSG 240 3 0 0 0 0 0 0
!UBX CFG-MSG 240 4 0 0 0 0 0 0
!UBX CFG-MSG 240 5 0 0 0 0 0 0
!UBX CFG-MSG 240 8 0 0 0 0 0 0
!UBX CFG-RATE 200 1 1
```

The default high-rate profile is 5 Hz. Users can copy the editable profile and
change `CFG-RATE 200 1 1` to `CFG-RATE 250 1 1` for a 4 Hz variant if their
receiver, USB bridge or downstream tooling is more reliable at that rate.

### u-blox M8T Raw + Status/Mock

Purpose: raw recording plus enough device solution output for dashboard and
Android mock location.

Expected behaviour:

- keep `RXM-RAWX` and `RXM-SFRBX` enabled;
- enable UBX `NAV-PVT` as the default compact navigation solution source;
- keep NMEA disabled by default to reduce chatter;
- avoid excessive chatter that competes with raw observation capture.

Users can copy this profile and add low-rate NMEA GGA/RMC/GSA/GST if needed for
external compatibility.

## u-blox Parsing And Monitoring

The u-blox stream parser must be byte-level. It scans mixed receiver RX bytes
for UBX frames beginning with `B5 62`, checks length sanity, validates checksum,
and resynchronizes after garbage or truncated data. NMEA parsing may also run on
line fragments when a profile enables NMEA, but arbitrary UBX bytes must never
be fed into a text parser.

For M8T V1, parse enough of these messages to support monitoring and solution
selection:

- `RXM-RAWX`: raw observation presence and frequency/last-seen counters;
- `RXM-SFRBX`: navigation-message capture presence and frequency/last-seen
  counters;
- `TIM-TM2`: timing-message presence and frequency/last-seen counters;
- `NAV-PVT`: lat/lon, height, fix type, accuracy estimates, time and satellite
  count;
- NMEA GGA/RMC/GSA/GST only when enabled by a copied or edited profile.

Dashboard frequency display should mirror the compact UM980 style, for example:

```text
Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA 1/1/1/1/- Hz
```

M8T must not be displayed as an internal RTK float/fix rover. It can report
standalone, SBAS or DGPS where the receiver reports that state, and it should
report raw-observation readiness for post-processing.

## Best Solution Selection

Add a canonical solution candidate model with at least:

- source receiver/profile;
- source engine, such as device internal, receiver PPP, generic NMEA, future
  RTKLIB;
- UTC or monotonic observation time;
- lat/lon;
- ellipsoidal height and/or MSL altitude when available;
- horizontal and vertical accuracy when available;
- fix class;
- satellites used/in view when available;
- freshness/staleness state.

The `BestSolutionSelector` ranks fresh valid candidates by quality:

1. RTK fixed.
2. RTK float.
3. PPP converged.
4. DGPS/SBAS.
5. Single.

If candidates are equivalent, prefer the most recent candidate with better
accuracy metadata. The selector should not invent higher quality from PPP
convergence status alone; PPP is a fix class only when the receiver reports a
converged PPP position.

The selected snapshot is the single source for:

- main dashboard position/fix display;
- Android mock-location publishing;
- derived solution sidecars when the recording policy enables them.

## Android Mock Provider

Mock-location publishing is optional and profile-controlled. V1 supports mock
location only while a recording/session foreground service is running.

The mock provider must:

- use `BestSolutionSnapshot`;
- publish Android `Location.altitude` from ellipsoidal height when available,
  not orthometric/MSL altitude;
- publish only fresh valid solutions;
- stop publishing when solutions go stale;
- attach satellite used/in-view counts as best-effort `Location` extras when
  available;
- expose a clear user-facing error if Android mock-location setup is missing;
- log mock-provider state/errors to events or quality sidecars;
- never write mock-provider data into `receiver-rx.raw`.

Android's public mock-location API publishes `Location` objects. It does not
let an ordinary third-party app inject complete `GnssStatus` satellite lists or
satellite sky positions into the platform GNSS provider. Any satellite-count
extras are therefore advisory and consumer-dependent.

Mock provider errors must not stop raw capture. If Android rejects a mock update,
the app should stop mock publishing, surface the error, and continue recording.

Live mock-location mode without recording is future work. It would start most of
the same workflow without normal recording provenance and therefore needs a
separate UX design before implementation.

## M8P And F9P Positioning

M8P should reuse the UBX frame builder, script compiler and stream parser. M8P
capabilities remain distinct:

- M8P-0: rover-oriented, RTCM input, internal RTK, raw observations;
- M8P-2: rover/base capable, RTCM input/output, internal RTK, raw observations.

F9P may be represented in documentation or capability placeholders only if the
code makes it clear that it is experimental/untested in this project. Do not
make F9P the practical V1 target without hardware validation.

## Error Handling

Before session start:

- malformed `!UBX` lines block start with line-numbered errors;
- unsupported command names fail closed unless explicitly implemented;
- mock provider enabled without Android setup produces a clear preflight warning
  and starts recording with mock publishing disabled; V1 has no required-mock
  workflow that would block recording start;
- M8T workflows must not require internal RTK support.

During recording:

- UBX checksum, length and resync failures are counted and logged;
- raw recording continues when parsers fail;
- stale solution candidates remove dashboard/mock availability but do not stop
  recording;
- mock-provider failures stop mock publication but do not stop capture;
- NTRIP is not started unless the selected workflow/profile uses corrections.

## Tests

Add focused tests for:

- UBX checksum and frame building;
- `!UBX` script compilation for representative M8T profile lines;
- rejection of malformed and unsupported `!UBX` lines;
- UBX stream parser resynchronization after garbage and truncated frames;
- `NAV-PVT` solution candidate conversion;
- frequency tracking for `RAWX`, `SFRBX`, `TIM-TM2`, `NAV-PVT` and NMEA where
  enabled;
- `BestSolutionSelector` ranking and staleness;
- fake mock-provider adapter behaviour;
- M8T workflow validation not requiring internal RTK;
- M8P capability separation from M8T.

## Validation

In the current Termux/aarch64 environment, avoid repeatedly running Android
packaging or app resource/unit-test tasks that are known to fail on the
Gradle-resolved x86-64 Maven `aapt2` binary.

Use:

```sh
git diff --check
sh gradlew :app:compileDebugKotlin
```

Run pure JVM/library module tests where they do not trigger Android app resource
processing. APK packaging and Android app unit-test execution should be
validated in Windows Android Studio, CI, an emulator host, or another
environment where native Android SDK build tools can execute.
