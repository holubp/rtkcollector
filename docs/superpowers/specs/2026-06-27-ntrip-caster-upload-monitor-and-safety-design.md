# NTRIP Caster Upload Monitor And Safety Design

Date: 2026-06-27

## Purpose

RtkCollector already supports base RTCM upload to an NTRIP caster. This design
adds the missing operator-facing monitoring and public-caster safety controls:

- show NTRIP caster upload as a main monitoring card whenever upload is
  configured and enabled;
- expose auth, connection, retry, queue, no-data and safety-stop problems;
- make reconnect behaviour configurable per `NtripCasterUploadProfile`;
- default new upload profiles to adaptive retry;
- enforce RTK2go-compatible safety rules when the upload host is RTK2go, and
  allow users to enable the same safety rules for other casters;
- show actual upload volume, bitrate, total RTCM message rate and per-message
  RTCM rates.

This feature applies only to NTRIP caster upload from base workflows. It does
not change rover NTRIP correction download, receiver raw capture, or the rule
that only valid extracted RTCM3 frames are uploaded.

## Current Context

The existing implementation already has these foundations:

- `NtripCasterUploadProfile` stores upload host, port, mountpoint, credentials,
  protocol policy and default enablement.
- `ActiveCasterUploadConfig` validates upload only for base workflows with an
  accepted base coordinate and an RTCM-capable command profile.
- `NtripCasterUploadController` owns upload retry and exposes upload snapshots
  with state, uploaded bytes, dropped bytes, last error and mountpoint URL.
- `RecordingForegroundService` extracts valid RTCM3 frames from receiver RX,
  writes them to `base-caster-upload.rtcm3`, and offers only those frames to
  the upload controller.
- `RecordingServiceState` already carries caster-upload state fields, but the
  home dashboard does not yet show a dedicated upload card.

## Profile Data Model

Extend `NtripCasterUploadProfile` with upload retry and safety policy fields:

- `retryMode`: `ADAPTIVE` by default, or `FIXED`.
- `fixedReconnectDelaySeconds`: minimum 10 seconds.
- `adaptiveInitialDelaySeconds`: default 10 seconds.
- `adaptiveMaxDelaySeconds`: default 300 seconds.
- `stopAfterFailuresEnabled`: default true.
- `stopAfterConsecutiveFailures`: default 5.
- `safetyRulesEnabled`: explicit user switch.
- `safetyMaxBitrateKbps`: default 35.
- `safetyBitrateWindowSeconds`: default 60.
- `safetyMaxSessionUploadMb`: default 500.

The profile editor must show these fields directly in the existing NTRIP
caster-upload profile screen. The fixed reconnect delay input must explain that
values under 10 seconds cannot be entered.

RTK2go host handling:

- if the upload host is recognised as RTK2go, safety rules are forced on;
- a forced-on RTK2go safety switch is displayed as enabled and locked;
- non-RTK2go upload profiles can enable the same safety rules manually;
- host detection only forces safety rules and does not replace the profile's
  other retry or safety threshold values.

## Runtime Behaviour

`NtripCasterUploadController` receives an explicit runtime retry/safety
policy derived from the active `NtripCasterUploadProfile`.

Retry rules:

- authentication and authorisation failures stop immediately, regardless of
  retry mode or failure-count limit;
- fixed retry waits the configured delay, which must be at least 10 seconds;
- adaptive retry starts at the configured initial delay and backs off up to the
  configured maximum delay;
- consecutive failed upload connections are counted;
- if `stopAfterFailuresEnabled` is true and the count reaches
  `stopAfterConsecutiveFailures`, upload stops and requires user action;
- a successful connection that streams useful RTCM resets the consecutive
  failure count.

No-data watchdog:

- after a successful SOURCE connection, the upload path must detect a connected
  stream that sends no RTCM bytes for 12 seconds;
- this condition is treated as a failed upload attempt and uses the same
  retry/backoff/stop policy;
- this protects public casters from connect-with-no-data loops.

Safety rules:

- when safety rules are enabled or forced, track actual uploaded byte rate and
  upload volume;
- stop upload when sustained bitrate exceeds `safetyMaxBitrateKbps` over
  `safetyBitrateWindowSeconds`;
- stop upload when current recording/session upload volume exceeds
  `safetyMaxSessionUploadMb`;
- warn, rather than block, if future app behaviour could create multiple
  simultaneous PUSH-in uploads from the same device.

Capture isolation rules remain unchanged:

- upload must not block or mutate `receiver-rx.raw`;
- upload queue drops degrade upload status only;
- OBSVM, OBSVMB, OBSVMCMPB, BESTSAT and other receiver telemetry are not sent
  to the caster;
- only valid RTCM3 frames extracted after receiver RX write are eligible for
  upload.

## Monitoring Card

The home dashboard must show a `Caster upload` card whenever caster upload is
configured and enabled, including before recording starts.

Card states:

- `Configured` or `Idle` before recording;
- `Connecting`;
- `Authenticating`;
- `Streaming`;
- `Retrying`;
- `Degraded`;
- `Stopped`;
- `Auth error`;
- `Connection error`;
- `No RTCM data`;
- `Safety stop`.

The compact card must include:

- primary state;
- compact redacted host:port/mountpoint;
- total uploaded volume;
- current upload bitrate;
- total valid uploaded RTCM frame rate in Hz;
- inline per-RTCM-message-type rates in Hz;
- dropped upload bytes or frames when non-zero;
- last error or safety-stop reason, promoted when present.

The selected visual direction is the dense inline type grid: per-message RTCM
rates are shown on the compact main card, not only in a detailed view.

## Detailed Upload Monitor

Tapping the compact card opens a detailed NTRIP caster upload monitor.

The detailed screen shows:

- profile and redacted mountpoint metadata;
- current state and last state transition time;
- connection state timeline or recent event list;
- last error and last stop reason;
- retry mode, current retry delay, consecutive failure count and stop policy;
- uploaded bytes, current bitrate and upload queue drops;
- total valid uploaded RTCM frame rate;
- full per-message-type RTCM rate table;
- safety mode status, including whether it is enabled by user or forced by
  RTK2go host detection;
- configured safety thresholds and current measured values;
- no-data watchdog status.

This screen is operational telemetry. It must not expose passwords,
authorisation headers, or raw sensitive server replies.

## RTCM Upload Statistics

The upload path computes statistics from the exact valid RTCM frames that
are offered to the caster upload controller.

Required statistics:

- total valid uploaded RTCM frame rate in Hz;
- per-message-type RTCM frame rate in Hz;
- total uploaded bytes;
- current upload bitrate;
- dropped queued upload bytes and/or frames.

The statistics use a bounded recent time window suitable for dashboard display.
The same measurement path feeds the compact card,
detailed screen, and safety bitrate enforcement so the UI and enforcement do
not disagree.

## Persistent Events And Diagnostics

Persist redacted upload lifecycle events to session events or quality logs:

- upload configured;
- connect attempt;
- connected/authenticated;
- rejected SOURCE request;
- authentication or authorisation stop;
- retry scheduled with delay and failure count;
- retry limit reached;
- no-data watchdog failure;
- queue drop;
- bitrate safety stop;
- session-volume safety stop;
- final upload summary.

Persisted records must not include passwords, raw authentication headers, or
raw credentials. Server responses are reduced to safe status categories
and short redacted messages.

## Documentation

Update formal and user-facing documentation:

- add or update formal requirements for caster-upload monitoring, retry policy,
  safety stops and dashboard visibility;
- update `docs/ntrip-and-corrections.md` with public-caster safety behaviour
  and RTK2go-compatible guidance;
- update user workflows to explain that high RTCM output rates consume mobile
  data and can get public-caster uploads banned;
- explain that command profiles control RTCM message emission, while upload
  safety monitors what is actually uploaded;
- update verification matrix and plan status.

## Testing And Verification

Automated tests cover:

- profile JSON round-trip defaults and migration for new fields;
- validation that fixed reconnect delays below 10 seconds are rejected;
- RTK2go host detection forces safety rules on;
- adaptive retry backoff and maximum delay;
- fixed retry delay;
- stop-after-consecutive-failures;
- immediate stop for authentication and authorisation failures;
- no-data watchdog behaviour;
- bitrate safety stop;
- session-volume safety stop;
- total and per-message RTCM Hz computation from uploaded frames;
- dashboard mapper/model card visibility, state, errors, volume, bitrate and
  per-message rates.

Manual or visual checks cover:

- compact card before recording when upload is enabled;
- compact card while streaming;
- compact card auth error;
- compact card safety stop;
- detailed upload monitor layout;
- Android field validation with a private caster or safe test caster before any
  RTK2go live validation.

## Non-Goals

This design does not:

- add another caster implementation;
- change rover NTRIP correction download behaviour;
- upload OBSVM/OBSVMCMPB or any non-RTCM receiver telemetry;
- enforce RTK2go's 1 Hz observation guidance by parsing command profiles in
  this feature;
- support product testing against RTK2go;
- change raw receiver recording ownership or byte-exact capture.
