# Recording-Safe Telemetry Isolation Design

## Purpose

Protect recording reliability, UI responsiveness and battery life while adding
more solution consumers: Android mock location, richer averaging metrics and
future RTKLIB-EX integration.

The design keeps the raw receiver capture path minimal and makes all derived
work advisory. Derived work may be delayed, conflated, dropped or marked stale
under pressure. `receiver-rx.raw` must remain byte-exact and must not depend on
screen rendering, mock-provider publishing, averaging, frequency measurement or
future RTKLIB-EX processing.

## Evidence From Field Session

The session
`samples/debug/session-2026-06-17T18-45-13.769275Z-fd3451a6-81db-42c3-adfb-289296bc129a-1781722595680.zip`
showed that on-screen BESTNAV rate drops can reflect real raw-stream gaps, not
only UI formatting:

- `receiver-rx.raw` contained 12,303 `BESTNAVB` frames over about 630.95 s.
- The median `BESTNAVB` receiver-time gap was 50 ms.
- 13 `BESTNAVB` gaps were above 75 ms.
- 7 whole-stream receiver-time gaps were above 250 ms.
- The largest whole-stream gaps were about 2.75-3.10 s.
- No `advisory-queue-dropped` event was present in `events.jsonl`.

This evidence does not prove that UI, u-blox parsing or mock-location publishing
caused the gaps. It does prove that the app must keep all non-recording work off
the capture path and expose enough metrics to distinguish raw-stream gaps from
dashboard scheduling artefacts.

## Goals

- Keep USB read and raw append as priority 1.
- Keep parser, UI, mock-provider, averaging, NTRIP and future RTKLIB-EX failures
  away from raw recording.
- Make best-solution selection shared by screen and mock provider, but make
  mock-provider resampling a separate opt-in output concern.
- Make UM980 `BESTNAV/BESTNAVB` a transparent selected-solution source for
  primary monitoring.
- Keep richer averaging metrics isolated and memory-bounded.
- Prepare future RTKLIB-EX processing as an isolated consumer that cannot block
  recording or UI.
- Reduce avoidable battery drain from unnecessary periodic work.

## Non-Goals

- Do not implement RTKLIB-EX in this cleanup.
- Do not implement NTRIP caster upload in this cleanup. It is specified in
  [Base RTCM NTRIP Caster Upload](2026-06-17-base-rtcm-ntrip-caster-upload-design.md)
  and should be implemented after this isolation work.
- Do not introduce a full event bus unless a later measured need justifies it.
- Do not add maps, GIS, shapefile or feature-collection functionality.
- Do not change raw session file semantics.

## Architecture

The runtime is split into explicit paths.

```text
USB transport
  -> capture thread
       -> append receiver-rx.raw
       -> enqueue copied chunks to advisory queue
       -> update cheap byte counters on throttled cadence

advisory parser worker
  -> receiver-specific telemetry snapshots
  -> message-frequency metrics
  -> selected-solution snapshot
  -> quality/session sidecars

consumers
  -> dashboard latest-state snapshot
  -> mock-provider resampler/publisher when enabled
  -> averaging metrics worker when enabled
  -> future RTKLIB-EX worker when enabled
```

The capture thread must not parse receiver messages. It must not format UI
strings, compute averages, publish mock locations, run RTKLIB-EX, connect to a
caster or wait for any advisory consumer.

## Capture Path Contract

The capture path may do only:

1. read bytes from transport;
2. append bytes to `receiver-rx.raw`;
3. enqueue a copied byte chunk to advisory processing;
4. update simple byte counters;
5. perform throttled notification/dashboard signalling that does not allocate or
   block substantially.

The capture path must not do:

- UM980/u-blox/NMEA/RTCM parsing;
- best-solution arbitration beyond receiving already-computed state;
- frequency measurement;
- coordinate averaging or variance/error calculations;
- Android mock-location publishing;
- RTKLIB-EX invocation;
- NTRIP caster upload;
- Compose state shaping.

If advisory enqueue fails because the bounded queue is full, raw recording
continues and a best-effort advisory-drop event is recorded.

## Best Solution Semantics

Best-solution selection is a logical mechanism shared by the screen and mock
provider. It is not the same as resampling.

For receivers that already expose a documented best-navigation solution, the
selector is a transparent pass-through:

- UM980 `BESTNAV/BESTNAVB` is the selected device solution for monitoring.
- It must not be resampled, delayed or overwritten by a separate periodic tick
  for the screen.
- `PPPNAVB`, `RTKSTATUSB`, `RTCMSTATUSB` and `STADOPB` enrich diagnostics but
  do not replace `BESTNAVB` as the primary UM980 position/fix source.

For receivers without a direct `BESTNAV`-style output, the selector may need
capability-specific logic:

- u-blox `NAV-PVT` can be the selected device solution when available.
- u-blox M8T must not be treated as an internal RTK float/fix rover.
- M8P/F9P behaviour must be reviewed against receiver capability and tested
  before claiming RTK semantics.
- Generic NMEA receivers may use GGA/RMC/GSA/GST as reduced solution sources.

The selected-solution snapshot should contain enough information for both
dashboard and mock-provider consumers:

- receiver family and source id;
- solution engine;
- fix class;
- lat/lon;
- ellipsoidal height and MSL altitude where available;
- horizontal and vertical accuracy where available;
- satellites used and in view where available;
- receiver timestamp and parser receipt timestamp;
- stale/fresh status.

## Dashboard Consumer

The dashboard consumes a latest-state snapshot. It must not consume every parsed
message as a separate UI event.

Expected behaviour:

- parser/advisory code may update internal telemetry at full receiver rate;
- dashboard broadcast/rendering is throttled to a controlled cadence;
- the dashboard should show the newest selected solution and newest diagnostics;
- it should not run mock-provider resampling;
- it should not own service state.

Frequency metrics shown on screen should prefer receiver timestamps when a
message type carries receiver time. Android processing-time windows may be shown
only as parser-throughput diagnostics or as fallback for messages without
receiver time. This distinction matters because foreground/background UI
scheduling can distort processing-time estimates.

## Mock Provider Consumer

The mock provider consumes selected-solution snapshots and adds resampling only
when mock output is enabled.

Rules:

- disabled mock provider means no mock publishing tick;
- failed or not-permitted mock provider must not affect recording;
- mock resampling must not write back into primary dashboard position/fix fields;
- mock status may be shown as a separate dashboard diagnostic;
- publish cadence and duplicate-snapshot suppression belong to the mock-provider
  path, not the recording or primary monitoring path.

## Averaging And Rich Metrics

Temporary-base averaging and future error estimates must run off the capture
path. They should consume selected-solution snapshots or explicit receiver
telemetry snapshots.

The averaging worker must be memory-bounded:

- use online algorithms for mean, variance and standard error;
- store counts, sums and covariance/variance accumulators, not full sample
  histories;
- retain only a small bounded diagnostic history if needed for UI display;
- stop or invalidate averaging when the required fix class changes, according
  to workflow rules;
- report sample count, duration, mean lat/lon/height, SD and estimated
  horizontal/vertical uncertainty.

Welford-style online statistics are preferred for running mean and variance.
More detailed base-quality metrics can be added without retaining all samples.

## Future RTKLIB-EX Boundary

RTKLIB-EX integration must be a separate worker or process-like boundary:

- it consumes copied raw/observation/correction data or files;
- it has a bounded queue or file-replay input;
- if it lags, it marks its solution stale or drops derived backlog according to
  explicit policy;
- it must not block raw recording;
- it must not block the dashboard's direct receiver telemetry;
- it reports status, age, input lag and last error separately.

RTKLIB-EX output can become another candidate for the best-solution mechanism,
but it must not replace direct receiver monitoring unless it is fresh, enabled
and explicitly preferred by workflow policy.

## Battery And Memory Constraints

The service should minimise wakeups and allocations:

- no mock-provider tick when mock output is disabled;
- no RTKLIB-EX worker when RTKLIB-EX is disabled;
- no caster-upload worker when caster upload is disabled;
- no unbounded queues;
- no unbounded sample histories;
- no per-message UI broadcasts;
- no per-read notification updates beyond the existing throttled notification
  policy;
- use latest-state/conflated snapshots for UI.

The app should remain able to coexist with other field apps such as OsmAnd. A
slow or memory-heavy derived consumer must degrade itself before it threatens
the foreground recording service.

## Error And Backpressure Policy

Each non-capture worker needs an explicit overload behaviour:

- advisory parser queue full: drop advisory chunks, record/drop-summary event,
  keep raw recording;
- dashboard behind: replace latest snapshot, do not queue old UI states;
- mock provider behind: skip stale snapshots, publish latest fresh snapshot only;
- averaging behind: skip stale derived updates or mark averaging degraded;
- RTKLIB-EX behind: mark RTKLIB solution stale or drop its derived backlog;
- future caster upload behind: keep raw/base recording, mark upload degraded.

Backpressure must not propagate to USB read or `receiver-rx.raw` writes.

## Testing Strategy

Required tests for the implementation plan:

- capture runtime writes raw bytes before advisory enqueue;
- advisory queue overflow does not stop raw recording;
- UM980 `BESTNAVB` selected-solution path is pass-through for dashboard state;
- mock disabled means no publish action and no mock-specific resampling work;
- mock enabled publishes from selected-solution snapshots without modifying
  primary monitoring fields;
- dashboard merge/display tests preserve direct telemetry when selector/mock
  state is stale;
- frequency tracker can compute UM980 rates from receiver timestamps;
- averaging uses online accumulators and does not retain unbounded samples;
- future RTKLIB-EX boundary tests prove worker lag cannot block capture.

Field validation should include:

- inspect `receiver-rx.raw` for receiver-time gaps independently of dashboard
  rate display;
- verify no advisory queue-drop events under normal UM980 binary profile;
- verify UI remains responsive when app is backgrounded/foregrounded;
- verify mock disabled has no periodic mock publish work;
- verify mock enabled does not change raw recording rate or dashboard direct
  telemetry.

## Implementation Monitoring

The implementation plan for this spec must explicitly track:

- capture-path code touched and why;
- worker queues and their capacities;
- which computations run on which thread;
- whether any new periodic task is created and when it is disabled;
- memory-boundedness of averaging and metrics;
- verification commands and field-analysis scripts;
- deferred dependency on base NTRIP caster upload.

Base-to-caster upload must not be folded into the isolation cleanup. It should
start only after this spec's capture/advisory/consumer boundaries are in place.
