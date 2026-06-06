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
- `core:quality` owns quality event aggregation boundaries.
- `receiver:api` defines receiver-driver contracts.
- Receiver implementation modules provide advisory parsing and command builders.
- `app` will later host Android Activity and foreground-service integration.

## Failure Isolation

Transport and raw recording errors are capture-path errors. Parser errors,
driver identification failures, quality-monitor failures, UI failures and NTRIP
reconnect loops must be isolated from raw recording wherever the transport and
storage path still operate.
