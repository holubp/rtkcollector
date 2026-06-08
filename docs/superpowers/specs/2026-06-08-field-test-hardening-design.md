# RtkCollector Field-Test Hardening Design

## Purpose

This design hardens the experimental Android UM980 field-test path before adding
larger features. It prioritises preserving the authoritative raw receiver stream
over derived sidecars, UI convenience, parser output, or correction routing.

Implementation must proceed in this order:

1. raw receiver RX protection;
2. deterministic stop and restart behaviour;
3. actionable field error reporting.

## Scope

In scope:

- append-only raw RX recording guarantees;
- writer flush, close, and future structured-sidecar finalisation semantics;
- graceful degradation when sidecar writers, parsers, NTRIP, or UI reporting
  fail;
- deterministic foreground-service stop/restart behaviour;
- NTRIP reconnect, terminal authentication failure handling, and live NTRIP
  update/disable control while recording;
- clearer service/UI status categories for field diagnosis.

Out of scope:

- new receiver command features;
- RTKLIB real-time;
- Android-side PPP/static solving;
- NTRIP caster/server upload;
- maps, shapefiles, GIS editing, field-feature collection, or survey project
  management;
- broad UI redesign.

## Design Principles

- `receiver-rx.raw` is the authoritative artifact.
- Raw RX must never be silently truncated, overwritten, redirected, or modified.
- Parser, NTRIP, sidecar, and UI failures are degraded states, not raw-recording
  failures, unless the storage target or USB capture path itself has failed.
- The foreground service owns recording. The Activity sends commands and
  observes state.
- NTRIP changes are events and service state transitions. They are not raw RX
  markers.
- Secrets remain runtime-only or secret references. NTRIP passwords and tokens
  must not be written to session metadata, event logs, or quality logs.

## Raw RX Protection

The service must create a unique session target before opening USB capture. For
app-private storage, the target is a filesystem directory. For SAF storage, the
target is a newly created document-tree folder. If the requested storage target
cannot be opened, recording start must fail before any receiver bytes are read.

The raw RX writer must:

- open `receiver-rx.raw` in append mode;
- never truncate an existing raw file;
- avoid reusing a non-empty session folder unless explicitly operating in a
  recovery/resume mode that has been specified separately;
- flush raw bytes on normal stop;
- close raw bytes before lower-priority derived sidecars where practical;
- expose write failures as storage failures.

Raw write failure is a high-severity recording failure because the authoritative
artifact can no longer be trusted. Derived sidecar write failures are lower
severity and should be isolated where practical.

## Writer Lifecycle

Writers are grouped by artifact importance:

1. authoritative raw writer: `receiver-rx.raw`;
2. binary sidecar writers: `tx-to-receiver.raw`, `correction-input.raw`,
   `rtcm-extracted.rtcm3`;
3. line/event sidecar writers: `events.jsonl`, `quality-live.jsonl`,
   `receiver-solution.jsonl`, `receiver-ppp-solution.jsonl`;
4. structured finalisable writers: future GPX/XML or similar formats.

All writers need `flush()` and `close()` semantics. Structured finalisable
writers also need a best-effort `finish()` phase. Future GPX/XML writers must
attempt to write required closing syntax on normal stop and on exceptional stop
where enough state remains to do so. Failure to finalise a derived structured
sidecar must be reported but must not invalidate `receiver-rx.raw`.

The intended stop writer order is:

1. stop or unblock capture;
2. send shutdown commands if requested and runtime is still valid;
3. cancel or stop NTRIP;
4. finish structured sidecars;
5. flush raw and binary sidecars;
6. close all writers;
7. update final metadata if possible.

If writing final metadata fails after raw RX is closed, the raw artifact remains
valid. The UI should show that metadata finalisation failed separately from raw
recording status.

## Deterministic Stop And Restart

The foreground service should expose a small lifecycle model:

- `IDLE`;
- `STARTING`;
- `RECORDING`;
- `STOPPING`;
- `STOPPED`;
- `FAILED`.

Stop must be idempotent. Repeated stop requests should not send shutdown
commands multiple times, close writers multiple times, or report expected
interruption as an error.

Stop behaviour:

- transition to `STOPPING` once;
- prevent new capture/NTRIP work from being scheduled;
- send shutdown commands once if requested and if serial runtime is still open;
- cancel the active NTRIP client once;
- join or unblock worker threads within bounded timeouts;
- flush/finish/close writers;
- broadcast a final state.

Restart behaviour:

- a new recording may start only after the previous service state is stopped or
  failed and resources have been released;
- a new start always creates a new session folder;
- no new recording should append to a previous completed session unless a future
  explicit recovery/resume workflow is specified.

## NTRIP Degradation And Reconnect

NTRIP state should distinguish:

- `DISABLED`;
- `CONNECTING`;
- `AUTHENTICATING`;
- `STREAMING`;
- `RECONNECT_WAIT`;
- `STOPPED`;
- `AUTH_ERROR`;
- `NETWORK_ERROR`.

Network loss is recoverable for NTRIP-enabled workflows. The service should
continue recording, enter reconnect wait, and resume correction feeding when the
network and caster become reachable again.

HTTP `401` and `403` are terminal NTRIP authentication/authorisation errors for
the active NTRIP attempt. The client must not keep retrying and bugging the
server. The service must report the auth error to the user and continue raw
receiver recording without corrections.

When NTRIP is not enabled by the active workflow, the service must not create a
NTRIP client or open a caster connection, even if saved NTRIP profiles contain
valid host, mountpoint, and credentials.

## Live NTRIP Reconfiguration

For active workflows that permit NTRIP corrections, the UI may send a live NTRIP
update to the foreground service without stopping recording.

Live update behaviour:

- validate host, port, mountpoint, username, and secret reference;
- do not write plaintext credentials to session metadata or events;
- cancel the current NTRIP client;
- record a redacted `ntrip-config-updated` event;
- start a new NTRIP client with the replacement configuration;
- keep raw RX capture running throughout;
- keep `correction-input.raw` and `tx-to-receiver.raw` append-only across the
  switch.

If update validation fails, the existing active NTRIP stream should continue
unless the user explicitly requested disable. If the new configuration connects
but receives `401` or `403`, the new NTRIP attempt enters `AUTH_ERROR` and
recording continues.

An explicit "Disable NTRIP now" action is a should-have for this pass. It stops
the active NTRIP client, stops feeding corrections to receiver TX, records a
redacted event, and leaves raw RX recording active. Re-enabling NTRIP is allowed
through a valid live update if the workflow supports NTRIP.

## Actionable Field Errors

Service status should categorise failures so the UI can show whether the raw
recording remains safe.

Error categories:

- `USB`: permission, open, read, write, disconnect, baud reconfigure;
- `STORAGE`: session creation, raw RX write, flush, close, SAF permission;
- `NTRIP`: network, auth, sourcetable, correction stream, reconnect state;
- `RECEIVER_COMMAND`: init, baud switch, mode command, shutdown command;
- `PARSER_EXPORT`: NMEA/UM980/RTCM parsing, NMEA/JSON/GPX sidecar export;
- `SERVICE_LIFECYCLE`: duplicate starts, stop timeout, worker cleanup.

Each status should include:

- category;
- severity;
- short user-facing message;
- whether raw RX recording is still active;
- whether corrections are currently being fed to receiver TX;
- whether the user action is to keep recording, check configuration, reconnect
  hardware, choose storage again, or stop.

## Testing Strategy

Required tests or checks:

- raw writer opens append-only and does not truncate existing raw bytes;
- duplicate session folder reuse is rejected or impossible in normal start;
- sidecar writer failure does not stop raw RX where practical;
- future structured sidecar abstraction can finalise closing syntax on normal
  and exceptional stop;
- stop is idempotent and sends shutdown commands at most once;
- expected NTRIP cancellation/interruption does not surface as an app error;
- NTRIP network failure enters reconnect wait and can recover;
- NTRIP `401`/`403` stops retrying and reports auth error while recording
  continues;
- plain rover recording does not start NTRIP even when profiles are populated;
- live NTRIP update cancels the old client and starts the new client without
  stopping capture;
- live NTRIP disable stops correction feeding while recording continues.

Android SAF provider crash semantics remain a manual/device test item because
provider append/truncate behaviour cannot be fully validated in JVM tests.

## Acceptance Criteria

- A raw RX storage failure is the only writer failure that can stop recording.
- Parser/export/NTRIP failures are visible but do not invalidate raw RX.
- Stop can be requested repeatedly without duplicate shutdown commands or noisy
  expected interruption errors.
- Loss of NTRIP network connectivity reconnects when possible.
- NTRIP auth failure is terminal for NTRIP but not for recording.
- Live NTRIP config update works without stopping recording for NTRIP-capable
  workflows.
- Non-NTRIP workflows do not establish NTRIP connections.
- Docs and tests reflect the safety model.
