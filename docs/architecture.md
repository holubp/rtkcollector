# Architecture

## Recording Alarms

Alarm policy is advisory and session-scoped, not a capture dependency. It consumes
decoded position receipt evidence and typed health/failure transitions after raw
append. A separate monotonic timer detects stale positions even with mock output
disabled. Cached RTKLIB status snapshots do not advance freshness.

Independent bounded notification/audio lanes consume conflated desired state.
They do not own session writers, USB, the recording wake lock or receiver commands.
Stop invalidates old effects without joining workers; final visual clearing drains
asynchronously. Generation-tagged notifications prevent late clearing of a new
session's alarm. Terminal alert-only ownership cannot retain recording resources.
Android/device lifecycle verification is tracked separately.

Alarm-state broadcasts also use a process-owned bounded lane, never synchronous
Binder work on Stop/finalization. Pending terminal decisions retain only bounded
alarm ownership, not recording resources. Cause evidence retains first/last receipt
and the latest bounded detail without changing an unresolved episode's identity.

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

## Diagnostic Isolation

Minimal essential evidence is always enabled. Session events use a dedicated,
bounded worker with reserved lifecycle capacity; optional Verbose/Debug logging
uses separate bounded delivery and byte-based rotation. Logging serialization,
file I/O, snapshots and maintenance do not execute on the raw capture path.
Diagnostic errors cannot mutate raw artifacts or stop healthy recording.

The foreground service carries stop origin and correlation through finalization.
Essential events drain before their writer closes, including deferred shutdown.
Metadata summarizes provenance and evidence failures; it does not claim a known
cause after an unobserved process kill. App-wide optional-log deletion never
deletes essential session evidence. The separate Minimal app journal covers
failures before session writers exist.
