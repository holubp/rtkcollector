# Diagnostics Share Logs Design

## Purpose

RtkCollector needs a developer-facing way to collect, share and clear app
diagnostics from field devices. The feature must help investigate USB,
storage, NTRIP, receiver-command, UI and Android vendor issues without
weakening the non-negotiable recording architecture.

The core requirement is that diagnostics must not reduce recording reliability,
UI responsiveness or battery life when disabled. Runtime logging and
performance monitoring are therefore both explicit opt-in controls, disabled by
default.

## Scope

In scope:

- rename the settings menu group `Device tools` to `Developer tools`;
- add a `Share logs` item under `Developer tools`;
- allow runtime diagnostics logging to be enabled and disabled;
- allow performance monitoring to be enabled and disabled;
- share runtime diagnostics as a temporary ZIP through Android sharing;
- share performance metrics as a temporary ZIP through Android sharing;
- delete runtime diagnostics and performance metrics;
- bound all diagnostic storage so it cannot grow indefinitely;
- redact secrets before diagnostic records are written or shared.

Out of scope:

- Android system-wide logcat capture;
- uploading logs to a remote service;
- automatic background sharing;
- putting diagnostics into `receiver-rx.raw`;
- changing session artifact semantics;
- adding maps, GIS, shapefile or field-feature functionality.

## Menu And UI

The settings menu group currently named `Device tools` becomes
`Developer tools`.

`Developer tools` contains:

- `Device console`;
- `Share logs`.

The `Share logs` screen is only for developer diagnostics. It has three compact
sections:

1. Runtime logging
   - toggle: `Runtime logging`;
   - status text: disabled, enabled, file count/size when available;
   - button: `Share runtime logs`;
2. Performance monitoring
   - toggle: `Performance monitoring`;
   - status text: disabled, enabled, recent sample count/size when available;
   - button: `Share performance logs`;
3. Maintenance
   - button: `Delete logs`;
   - confirmation dialog before deletion.

Both toggles default to off. The toggle state may persist across app restarts
because field debugging often requires enabling diagnostics, reproducing a
problem and then sharing logs.

## Runtime Logging Model

Runtime diagnostics are app-private structured JSONL files. They are separate
from recording sessions and are not part of `session.json`. Sharing creates a
temporary ZIP under the existing app cache/FileProvider mechanism.

Runtime records may include:

- app version/build information when available;
- Android API level, device manufacturer/model and package name;
- coarse app lifecycle events;
- recording start/stop attempts and outcomes;
- user-visible recording errors with category and severity;
- USB open, permission, disconnect and reconnect failures;
- NTRIP connection status and errors with credentials redacted;
- receiver command failures;
- storage and SAF write failures;
- mock-location setup/update failures;
- uncaught exceptions that can be captured safely.

Runtime records must not include:

- plaintext NTRIP passwords;
- authorization headers;
- tokens;
- raw correction bytes;
- raw receiver bytes;
- full settings exports with secrets.

Credential-like values are redacted before records are accepted by the runtime
diagnostics writer.

## Performance Monitoring Model

Performance monitoring is also app-private structured JSONL. It is explicitly
off by default.

When enabled, metrics are sampled at low frequency, such as every five seconds,
from already-maintained counters or cheap Android runtime calls. The monitor may
record:

- receiver RX bytes and approximate byte rate;
- correction input bytes and approximate byte rate;
- TX-to-receiver bytes and approximate byte rate;
- current session total size if already available;
- heap used and heap max;
- thread count;
- mock-provider last-update interval when available;
- advisory queue pressure or dropped-count counters when already exposed.

Performance monitoring must not subscribe to every receiver byte, parse high
rate messages, run RTKLIB work, resample solutions, or force additional UI
broadcasts. It is a low-rate observer of existing counters only.

## Disabled-State Performance Rule

When runtime logging is disabled:

- hot-path call sites must not build JSON;
- hot-path call sites must not format diagnostic strings;
- hot-path call sites must not expand stack traces;
- hot-path call sites must not perform file I/O;
- hot-path call sites must not enqueue diagnostic objects;
- where practical, call sites use a cheap boolean guard before constructing a
  diagnostic record.

When performance monitoring is disabled:

- no timer or sampler thread is active;
- no metric aggregation happens;
- no performance file is opened;
- no performance JSON is constructed.

The implementation should expose pass-through/no-op APIs only where that keeps
call sites simple, but the preferred hot-path pattern is:

```kotlin
if (Diagnostics.runtimeLoggingEnabled) {
    Diagnostics.runtime().record(...)
}
```

This ensures disabled diagnostics are close to eliminated and cannot degrade
recording reliability, UI responsiveness, background operation or battery life.

## Enabled-State Safety Rule

When diagnostics are enabled, they remain advisory and discardable:

- writes happen off the raw capture path;
- bounded queues or background writes must use drop-on-pressure behaviour;
- diagnostic failures must never stop recording;
- diagnostic failures must never mutate receiver RX, correction input or
  TX-to-receiver artifacts;
- diagnostics should prefer losing a diagnostic record over blocking receiver
  capture, USB TX, NTRIP routing or UI state changes.

## Storage And Sharing

Diagnostics live under app-private storage, for example:

- `files/diagnostics/runtime/`;
- `files/diagnostics/performance/`.

Temporary share ZIPs live under app cache, for example:

- `cache/diagnostic-share/`.

The app `FileProvider` must expose only the temporary diagnostics-share cache
directory, not all app-private diagnostics storage.

Runtime and performance ZIPs should include:

- `diagnostic-summary.json` or `performance-summary.json`;
- one or more JSONL files;
- a small `README.txt` explaining redaction and opt-in state.

ZIP creation is user-triggered and can run on a background dispatcher. It is not
part of recording startup or capture loops.

Temporary ZIPs may be deleted after sharing is launched, following the existing
session ZIP cleanup pattern.

## Deletion

`Delete logs` removes runtime diagnostics, performance metrics and temporary
diagnostics share ZIPs after user confirmation.

Deletion must not delete recording sessions, session ZIPs, settings backups,
receiver raw files or NTRIP correction artifacts.

## Error Handling

If sharing fails, show a concise user-visible error in the `Share logs` screen.

If diagnostics storage cannot be written while logging is enabled, disable the
affected diagnostic writer and show that logging failed. Recording must
continue.

If deletion partially fails, report which category could not be deleted while
leaving recording/session files untouched.

## Formal Requirements To Update

The implementation should add formal requirements covering:

- diagnostics are opt-in and disabled by default;
- disabled diagnostics have near-zero hot-path overhead;
- diagnostics never write secrets;
- diagnostics are separate from session artifacts;
- diagnostic sharing uses scoped temporary FileProvider paths.

The verification matrix should include tests for redaction, disabled-state
no-op behaviour, FileProvider scope and ZIP contents.

## Testing Strategy

Automated tests:

- redaction removes passwords, authorization headers and secret-looking values;
- disabled runtime logging does not create files or enqueue records;
- disabled performance monitoring does not start a sampler;
- enabled runtime logging writes bounded JSONL records;
- enabled performance monitoring writes low-rate metric samples from provided
  counters;
- deleting diagnostics does not delete unrelated files;
- diagnostics share ZIP contains only expected files;
- FileProvider XML exposes the diagnostics share cache path.

Manual checks:

- enable runtime logging, trigger a visible start failure, share ZIP and inspect
  redaction;
- enable performance monitoring during recording and verify recording continues;
- disable both controls and verify no new diagnostic files are produced;
- delete logs and verify sessions remain.

## Constraints

The feature must preserve these existing RtkCollector rules:

- `receiver-rx.raw` remains byte-exact and authoritative;
- parser, UI, NTRIP, quality-monitor, diagnostics and RTKLIB failures must not
  block raw recording;
- foreground service continues to own recording;
- no secrets in session metadata or shared diagnostics;
- no map/GIS/shapefile assumptions.
