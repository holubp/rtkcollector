# RtkCollector Simple UI Design

## Scope

This design covers the first simple RtkCollector user interface and build shape.
It keeps the implementation small: a guided Android UI skeleton plus a JVM-testable
workflow dry-run path. It does not implement Android USB capture, real serial TX,
NTRIP networking, foreground services, RTKLIB integration, receiver command
sequences, PPP/static solving, maps, shapefiles, GIS editing or field-feature
collection.

## Chosen Approach

Use a hybrid build approach:

- Add the real Android UI source in `:app` so the product moves toward an APK.
- Keep the workflow/domain model in pure Kotlin modules so it remains testable on
  this Termux device even if local Android packaging is blocked by native SDK
  tools.
- Treat Android APK assembly as expected to work in CI or on a normal Linux/macOS
  Android SDK host. Local Termux validation may be limited because the installed
  Google SDK build tools include x86-64 native binaries.

The first UI is a guided workflow launcher and session dry-run UI. It must not
invent ad-hoc UI modes. Every user path must be backed by a `WorkflowSpec`, a
receiver/profile selection, a command plan and a structured validation result.

## User Flow

The UI has three phases.

### 1. Pre-recording Plan

The user selects:

- workflow scenario;
- receiver profile;
- init command sequence;
- one mode-specific command sequence;
- optional shutdown command sequence, usually empty;
- correction source, if the workflow uses corrections;
- base context, if required;
- expected recording artifacts.

The initial V1 workflow choices are:

- plain rover recording;
- rover with NTRIP fed to the receiver;
- temporary base calibration recording;
- fixed-base operation from an accepted base position;
- replay/test workflow.

The UI runs validation before any start action is enabled. In this first pass,
start is dry-run only and must not open USB, connect to NTRIP or start a service.

### 2. Recording Monitor

After a dry-run start, the UI switches to a recording monitor screen that models
the later foreground-service session. It displays observables, not controlling
business logic:

- recording state and elapsed time;
- RX raw byte count or expected `receiver-rx.raw` growth;
- TX/correction byte count or expected `tx-to-receiver.raw` growth;
- serial throughput;
- latest in-device solution status;
- NTRIP state and correction age when applicable;
- raw observation presence, expected at least 1 Hz where supported;
- parser, quality, NTRIP and solution warnings separated from raw recording
  health;
- expected session artifacts.

The screen includes a clear stop action. Parser, NTRIP, RTKLIB or UI failures
must remain advisory and must not imply raw recording failure unless the capture
or recorder itself fails.

### 3. Stop and Shutdown

When the user stops:

- the session enters a stopping/finalising state;
- optional shutdown commands are attempted only if configured and the transport
  is still available;
- shutdown TX bytes must later be recorded to the TX sidecar;
- session metadata records whether shutdown commands were not configured,
  attempted, sent, skipped or failed;
- the UI shows finalisation status and the expected artifact list.

In the first UI pass this is still a dry-run state transition. It prepares the
right lifecycle surfaces for the later recording service.

## Receiver Command Plan

Receiver commands are part of the validated session plan, not hidden UI actions.
A receiver profile may provide or reference these command groups:

- init sequence;
- rover sequence;
- base-calibration sequence;
- fixed-base sequence;
- shutdown sequence.

Startup commands are ordered as:

1. init sequence;
2. exactly one workflow mode sequence.

The selected mode sequence is derived from the validated workflow role. The app
must not silently concatenate conflicting mode sequences, such as rover and
fixed-base commands. Runtime correction bytes are not command scripts, but they
share the receiver TX path and must later be recorded separately from receiver
RX. Shutdown commands are sent after stop, if available, and must never modify
or corrupt the already captured RX raw stream.

The first implementation may model command groups as references or placeholder
plans. It must not add risky concrete UM980 or UBX command sequences.

## Data Flow

The intended later execution model is:

1. UI builds or selects a `WorkflowSpec`.
2. UI selects receiver profile and command plan.
3. Validator checks workflow, receiver capabilities, base context, correction
   context, recording expectations, safety flags and command-plan consistency.
4. Foreground capture service receives only a validated plan.
5. Raw capture remains authoritative and independent of parsers, NTRIP, RTKLIB,
   quality monitoring and UI.

For this UI pass, step 4 is represented by a dry-run session state object rather
than a real service.

## Error Handling

Validation errors block dry-run start. Warnings are visible but do not block
start unless they indicate forbidden secrets or unsafe command/session settings.
The UI must never store plaintext NTRIP passwords or tokens in exportable session
metadata. It may display redacted metadata or secret references.

Runtime monitor errors are grouped by source:

- recording health;
- transport/TX health;
- correction source state;
- parser/quality state;
- solution state.

This separation keeps the raw capture path authoritative and prevents advisory
subsystems from being treated as recording failure.

## Testing

The implementation plan should include:

- pure Kotlin tests for command-plan ordering and validation;
- workflow dry-run tests for each V1 workflow;
- UI-model tests for pre-recording, recording, stop and shutdown states where
  practical;
- Gradle validation that still works without Android device hardware.

If Android APK packaging cannot run locally on Termux because of SDK native
binary architecture, the result must be reported clearly and the pure Kotlin
checks must remain green.
