# Tiered Diagnostics And Durable Session Events

Status: Approved by human:holubp on 2026-10-26. Not implemented.
Date: 2026-10-26

Builder check: completed 2026-10-26 by a fresh reviewer (requested model
`gpt-6.1-sol`, reasoning `medium`). No consequential unanswered planning
questions identified. Review is not human approval or implementation evidence.

## Intent

Make an unexplained recording stop diagnosable without requiring operators to
enable detailed logging in advance. Two inspected sessions had a final stop time
but empty `events.jsonl` files, and optional runtime logs were unavailable. This
does not establish the cause of either stop; the first was deliberately stopped.

## Decisions From The Discussion

- Minimal logging is always enabled. Verbose and Debug add increasing detail.
- The selected level persists across app restarts.
- Verbose and Debug produce a persistent, non-obstructive indication on the main
  monitoring screen. Minimal does not produce this indication.
- Logging is controlled from the UI, including storage, sharing and deletion.
- The optional log size cap is user-settable in the logging settings menu and
  persists across app restarts; it is not a fixed application limit.
- The initial optional log cap is 10 MB (10,000,000 bytes).
- Performance monitoring remains an independent option.
- Chosen approach 1: essential session events are separate from bounded optional
  application diagnostics. Debug traffic cannot evict essential session evidence.

The detailed defaults and mechanisms below were proposed by the designer and
approved with the design. The operator explicitly confirmed the initial 10 MB
cap. MB uses decimal units throughout the UI and implementation.

## Approaches

1. **Selected: separate essential session events and detailed app logs.** Keep
   essential evidence with each recording and use bounded rotation for optional
   app diagnostics. This fits existing session artifacts and keeps their lifetime
   independent of optional log maintenance. It needs explicit shutdown ordering.
2. **One shared store with priorities.** Simpler initial plumbing, but eviction,
   session exports and deletion need complicated filtering to protect essential
   evidence. Rejected in favor of the clearer lifetime boundary in approach 1.

## Levels And UI

One global selector replaces the runtime-logging boolean:

| Level | Contents |
| --- | --- |
| Minimal | Session lifecycle, stop provenance, actionable errors, recovery and significant subsystem state transitions. Always on. |
| Verbose | Minimal plus bounded protocol, reconnect, receiver-configuration and shutdown-step summaries. No raw stream duplication. |
| Debug | Verbose plus bounded internal diagnostic detail, timings and queue/drop counters needed for investigation. |

The existing diagnostics screen remains the entry point. It displays the selected
level, separate essential/optional/performance storage usage, retention settings,
sharing and deletion actions. Explain each level concisely. There is no Off
option for Minimal. Use existing app controls and accessibility conventions.

On the main screen, show `Verbose logging enabled` or `Debug logging enabled`
below setup controls and above monitoring cards, including while idle. Provide a
shortcut to diagnostics settings. This advisory must coexist with, not replace,
actionable errors. It must not claim a recording or connection has failed and
must not repeatedly open a modal. Its accessible text explains extra storage and
processing cost. Selection does not expire automatically.

Level changes apply to an active recording without restarting it; record the
transition as a Minimal event. Settings-set application, workflow changes and
imports must not silently reset this global diagnostics preference.

## Essential Evidence

Every new session uses the existing `events.jsonl` artifact for essential events.
Record at least session creation, start accepted/failed, capture started, explicit
stop requests, fatal recording failures, shutdown progress milestones, final stop
result and recovery/interruption assessment. Log subsystem state transitions,
not every USB read, GNSS epoch, correction frame or repeated identical error.

Each structured event includes schema version, stable event code, severity,
session identifier, wall-clock timestamp, monotonic elapsed time within that
process and sanitized allowlisted attributes. Correlation identifiers connect
stop requests and finalization. Existing event consumers must remain compatible;
additive schema changes are preferred.

Carry explicit stop provenance through service commands and lifecycle callbacks:
user stop, startup failure, fatal raw-storage failure, service destruction or
other named internal cause. Preserve the initiating cause and later contributing
failures independently. Repeated stop requests must not overwrite the original
cause or close writers twice. Normal user stops are not errors.

Final metadata summarizes stop outcome, initiating reason and evidence available;
the event timeline retains detail. Record stop request before shutdown work and
record finalization after producers have released session writers, before closing
the event writer. A deferred finalizer preserves this ordering.

Android may kill a process without invoking callbacks. Never promise a recorded
reason for such a kill. On recovery, a session without verified finalization is
`incomplete; cause unknown` unless independent evidence establishes more. Check
the active service/session registry before classifying a session: an Activity
restart must not label a live recording interrupted. Do not rewrite raw artifacts
or invent a stop timestamp to make an interrupted session appear finalized.

Before session writers exist, or when their storage fails, use a small bounded
app-private essential journal for startup/lifecycle failures. Link to session ID
when available. This is part of Minimal, not a fourth selectable level. It is a
best-effort fallback, not a guarantee against full storage or sudden power loss.

## Isolation, Bounds And Shutdown

Raw RX remains byte-exact and authoritative. Logging, serialization, log export,
rotation and deletion never perform disk I/O on the raw capture path. Diagnostic
exceptions cannot stop capture or propagate into receiver/correction processing.

Use separate bounded optional and essential queues with reserved essential
capacity; optional traffic cannot consume essential capacity. Bound individual
event sizes and coalesce repeated state/error events. Preserve lifecycle/stop
slots; if essential pressure still exceeds finite capacity, retain aggregate drop
counts and report degraded diagnostics. Do not claim every event is durable.

Draining/flushing is bounded and outside capture. Logging must not wait on a
worker that is waiting for shutdown or hold session-writer locks across callbacks.
Persist finalization only when its required writes have succeeded; inability to
persist evidence remains explicitly unknown, not a successful diagnostic receipt.
Do not prolong an otherwise completed recording indefinitely for optional logs.

Replace append-and-rewrite trimming with size-bounded file rotation. Proposed
defaults: optional runtime logs 10 MB total, essential app journal 1 MB total,
performance metrics retain their existing independent policy. The logging
settings menu exposes the optional runtime log size cap explicitly; proposed
choices are 5/10/25/50 MB. The selected cap persists across restarts and applies
to the total of active and rotated optional files, not each file separately.
Reducing it triggers asynchronous pruning without restarting recording or
changing the logging level. Age retention is separately user-selectable at
1/7/30 days (default 7 days); enforce whichever limit is reached first.
Rotation includes the active file and
uses bounded individual files. Expiry occurs on append/startup, without an
unnecessary always-running cleanup service. Session events live as long as the
session and are never pruned by app-log retention.

## Sharing, Deletion And Privacy

Session sharing includes essential session events and the stop summary regardless
of logging level. App-log sharing includes a stable snapshot of the essential app
journal, retained optional logs, selected level, schema/app version and drop/write
failure counters. Performance metrics remain independently shareable. Export
uses bounded streaming and cannot block capture or race rotation into corruption.
Explain that optional app logs may cover multiple sessions.

Deleting app diagnostics does not delete sessions or their essential events.
Clearing logs does not change the selected level; logging resumes into a fresh
generation. Sharing/deletion failure is reported without altering recording.

All levels exclude passwords, tokens, authorization headers and secret-bearing
command text. Use structured allowlisted fields plus redaction; redact exception
messages and attribute values too. Do not serialize arbitrary objects or map
keys supplied by a peer. No automatic full payload, NMEA, raw RX/TX or precise
position dumps in Debug. Existing user-requested raw session artifacts remain
unchanged; exports explain their existing sensitivity.

## Migration And Compatibility

Missing/false legacy runtime toggle maps to Minimal; true maps to Verbose, never
Debug. A valid persisted new level takes precedence. Preserve the independent
performance toggle. Do not mutate historical sessions to suggest new evidence
existed; viewers tolerate absent stop summaries and event schema versions.

## Verification Contract

Automated checks must establish:

- Level filtering, cumulative semantics, migration, persistence and live changes.
- Minimal events with optional logging disabled; independent bounded queues,
  rotation limits, large events, repeated errors and observable drops.
- User stop, startup failure, fatal storage failure, service destruction,
  duplicate stop, deferred finalization and interruption/recovery ordering.
- Failure injection: diagnostic I/O, full queues, export/delete concurrency and
  logger shutdown cannot corrupt or stop otherwise healthy raw capture.
- Credentials are absent at every level, including exception/attribute paths.
- Session ZIP essential evidence, legacy-session compatibility, clear/delete
  isolation, and UI level/warning behavior in light/dark layouts.

Hardware/device acceptance includes healthy capture at the supported high-rate
UM980 configuration under Debug, repeated start/stop, Activity recreation,
backgrounding, USB loss/recovery, process termination/restart and sharing during
recording. Compare raw byte integrity and throughput to Minimal; record actual
measurements, not an unverified reliability claim. Android force-stop/power loss
can legitimately leave final events absent.

## Scope, Traceability And Next Step

No receiver protocol changes, RTKLIB changes, automatic bugreport collection,
remote telemetry, battery-policy changes or speculative fix for the observed
unexplained stop. This delivers evidence for future diagnosis, not its cause.

After approval, update `ANDROID-DIAGNOSTICS-001` and add precise lifecycle,
retention/privacy and UI requirements under `docs/specification/`; align the
verification matrix, session-format documentation, architecture, operator docs
and `docs/superpowers/plan-status.md`. The prior 2026-06-21 diagnostics design
remains historical; this design supersedes its opt-in runtime boolean, not the
separate performance-monitoring feature. Formal approval/evidence gates must be
resolved explicitly; prior release-specific waivers do not automatically apply.

Builder owns exact Kotlin types, bounded queue sizes, flush deadlines and rotation
file sizes within the behavioral limits above, backed by failure-path tests.
Next step after human approval: a checkpointable implementation plan, followed by
implementation and the repository-prescribed local/CI gates. No implementation
or field-validation claim is made by this design.
