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
- `app` will later host Android Activity and foreground-service integration.

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
