# Architecture

RtkCollector is service-first and receiver-agnostic. It is not a GIS app and is
not primarily an Android RTKLIB clone. The raw capture path is authoritative;
parsers are advisory.

## Data Flow

```text
USB/Bluetooth/TCP/File transport
  -> capture queue
  -> append-only raw recorder
  -> sidecar event/session writers
  -> advisory parsers
  -> quality monitor
  -> UI
```

Publication documentation must describe the shipped implementation, not only the
long-term transport architecture. If Bluetooth, TCP or replay transports are
documented as architectural seams but not implemented in the Android app, user
and Play-facing documents must label them as future or non-current capabilities.

## Capture Rules

- The capture path must not depend on Activity lifecycle.
- The capture path must not depend on Compose.
- The capture path must not block on NTRIP.
- The capture path must not block on parsers.
- The raw stream must never contain app-injected timestamps or markers.
- Commands sent to the receiver must go to a separate TX sidecar.
- Session metadata must be written separately.
- Recording must continue if parser, UI, NTRIP, quality monitoring or
  receiver-specific decoding fails.

## Components

- `core:transport` owns byte transport abstractions such as serial, Bluetooth,
  TCP and file replay.
- `core:capture` owns raw recording and capture event sinks.
- `core:correction` owns correction stream concepts and NTRIP state boundaries.
- `core:session` owns session metadata and base-position data models.
- `core:workflow` owns validated workflow specifications, workflow examples and
  workflow validation.
- `core:quality` owns quality event aggregation boundaries.
- `receiver:api` defines receiver-driver contracts.
- Receiver implementation modules provide advisory parsing and command builders.
- `app` hosts Android Activity, profile management and foreground-service integration.

## Failure Isolation

Transport and raw recording errors are capture-path errors. Parser errors,
driver identification failures, quality-monitor failures, UI failures and NTRIP
reconnect loops must be isolated from raw recording wherever the transport and
storage path still operate.

## Derived Telemetry Isolation

The capture thread writes receiver bytes before advisory processing. Dashboard
state, best-solution snapshots, Android mock-location output, coordinate
averaging, message-frequency metrics and future RTKLIB-EX processing consume
derived advisory state. They may be throttled, dropped or marked stale under
pressure. They must not block USB reads or raw receiver recording.

## Workflow Execution

Workflow rules are specified in [Workflows](workflows.md). The UI starts
validated `WorkflowSpec` instances; the foreground capture service executes
validated `WorkflowSpec` instances. Validation happens before receiver commands,
NTRIP connection or recording start.

Raw capture remains independent of parser, quality-monitor and solution-engine
failures. An RTKLIB, NTRIP or receiver-native parser failure may affect advisory
state, warnings or sidecar events, but it must not modify or stop byte-exact
receiver recording while transport and storage are still functioning.

Version 1 user workflows cover receiver-side solutions only: plain rover,
NTRIP-to-receiver rover, temporary-base preparation, fixed-base operation and
replay/test. In-phone RTKLIB real-time solution is a version 2 advisory engine
and must not be required by V1 capture or session execution.

## Configuration Authority

Profile libraries own complete reusable configuration. Settings sets own default
references, applicability and selection policies; active operator choices are
stored separately per set. `ActiveSetupResolver` is the shared authority for
Home, Menu and Start. A correction source resolves its exact caster reference,
never an endpoint or credential substituted by another selection.

Start submits one validated `RunningSetupSnapshot` through
`RecordingSetupBridge`. The service owns the accepted snapshot. Library edits
and Re-apply affect the next Start, not an existing recording. Supported live
source/mock patches bind request, session and revision identities; acceptance
and actual network connectivity are separate states. A failed or delayed patch
must not publish a selection as active or overwrite newer next-start choices.

Legacy field overlays are migration inputs only. Idle migration stages fresh
secret bindings before recoverable graph publication; recovery precedes normal
profile reads and Start. Ordinary profile/session JSON contains explicit secret
references, not passwords. Configuration and migration work remain outside the
byte-exact capture path.
