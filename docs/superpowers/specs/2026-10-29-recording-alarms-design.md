# Recording Reliability Alarms

Status: Approved by human:holubp on 2026-10-29, including the explicit
automatic-recovery clarification. Implementation awaits implementation-plan
review. Owner: human:holubp.

## Intent And Agreed Behavior

The operator normally waits for positioning, puts the phone in a backpack and
expects recording to continue. Notify them when that expectation is no longer
met, audibly on the phone and visibly on the phone, lock screen and paired watch
where Android/watch settings permit. Alarms must never become a dependency of
byte-exact recording or its existing reconnect/finalization paths.

Agreed in discussion:

- Receiver connection loss and position loss have distinct visual notifications
  and distinctly different default acoustic patterns.
- Position loss defaults to lower-pitched repeating beep, pause.
- Receiver connection loss defaults to higher-pitched repeating three beeps,
  pause. Other actionable disruptions or unexpected recording stops use that
  same urgent default pattern, with cause-specific visual text.
- Menu -> Alarms configures each category independently: visual enabled,
  acoustic enabled and selected sound. Preferences persist across app restarts.
- Sound choices include built-in patterns (defaults) and Android notification
  sounds. Each choice can be previewed.
- Initial position acquisition is silent. Position loss arms only after the
  first fresh valid position in this recording. A previous session cannot arm it.
- Position loss means no fresh valid position, not RTK fixed becoming float or
  standalone. Its configurable delay defaults to five seconds.
- Deliberate Stop silences alarms; recovery also clears the relevant active
  condition. An unexpected stop must not be classified as a deliberate Stop.
- Human clarification confirmed: a recoverable disruption sounds only while
  that disruption persists. Verified recovery automatically stops its acoustic
  output and removes its visual warning, without any acknowledgement. Restoring
  USB alone cannot clear a still-unresolved loss of valid positioning.
- Chosen approach: system visual notifications plus independent service-owned
  acoustic playback, rather than relying on repeating notification sounds.

## Goals And Non-Goals

Provide trustworthy actionable alerts with explicit recording/position status;
support locked-screen/background use without keeping the Activity alive.
Preserve all raw/correction artifacts and existing workflow/engine semantics.

Do not add a watch app, RTK-quality threshold alarms, scheduled clock alarms,
automatic volume changes, Do Not Disturb bypass, full-screen intents, exact-alarm
permissions or automatic restarts of recording. Do not claim protection against
process death, force-stop, device shutdown or platform-wide resource exhaustion.
Existing reconnect attempts remain unchanged.

## Approaches Considered

1. Selected: separate system notifications and acoustic controller. Gives
   independent visual/audio switches and deterministic built-in patterns; needs
   explicit playback ownership, arbitration and unexpected-stop lifetime.
2. Notification-channel sounds only: less app playback code, but channel/user
   settings and notification opening control repeats. Does not meet the requested
   independently controlled repeating phone patterns well.

## Detection And Meaning

Use typed lifecycle/health and current solution evidence, not matching error
strings or treating every logged exception as recording failure.

| Category | Trigger | Clears |
| --- | --- | --- |
| Receiver connection lost | Confirmed USB detach/read/transport failure during an accepted recording, or existing no-RX health threshold (currently 10 s) | Receiver-originated bytes resume and capture/transport is healthy; unrelated TX/NTRIP activity cannot clear it |
| Position lost | After first fresh valid solution, valid position remains unavailable/stale for configured delay (5 s default) | A new fresh valid solution from the selected main engine |
| Recording interrupted | Unexpected capture/storage/service failure, or an actionable failure of a configured required workflow component | Verified recovery of that component, or deliberate Stop/acknowledgement for a terminal stop |

Recording-interrupted details must distinguish raw recording stopped from raw
recording continuing with degraded corrections/upload/processing. Required
correction loss or configured base-upload failure can therefore be reported
without falsely saying that receiver RX stopped. A raw storage failure is urgent.
Ordinary connection progress, expected reconnect retries, routine warnings,
deliberate shutdown, optional diagnostic/export failures and irrelevant disabled
components do not create repeated new alarm episodes. Advisory parser trouble
alone must not be presented as failed raw storage/capture.

Position freshness comes from newly decoded valid main-engine solutions with
coordinates and accepted fix status, using a monotonic receipt clock. Receiving
an invalid/no-fix message, unrelated satellite telemetry or repeated old dashboard
coordinates does not refresh it. Stationary receivers are valid; coordinates do
not have to change. The selected receiver/RTKLIB engine remains authoritative.
Raw-only/RTCM-only configurations that never produce positions never arm this
alarm. Temporary/fixed base workflows are not exempt if they do produce valid
position evidence. No RTK-quality waiting requirement is inferred from arming.

When receiver loss is known, suppress the redundant position-loss notification
and sound. Keep its arming history for that session: after RX recovery, position
freshness is assessed normally. Other simultaneous causes remain identifiable.

## Alarm State And Lifetime

Scope every event/effect to a recording generation and alarm episode. Track
condition, first/last evidence, configuration, acknowledged state and desired
outputs independently of RecordingServiceState.lastError.
Track cause-level keys within a category, not just one category boolean. An
acknowledgement carries the exact displayed cause/episode IDs: acknowledging
correction failure cannot acknowledge a later storage failure. A newly fatal
failure creates an unacknowledged terminal episode even if an earlier degraded
cause had the same visual category. Repeated callbacks for the same unresolved
cause are updates, not new episodes.

- At Start: clear prior-session arming and acknowledgements; begin monitoring
  accepted recording startup. Position acquisition alone cannot alarm.
- Recovery: automatically cancel the recovered cause's active visual/audio
  effects, with no acknowledgement prerequisite and no latched recovered warning.
  Do not clear other unresolved causes. Receiver recovery alone cannot count as
  position recovery: if position is still stale after RX resumes, its armed
  position-loss alarm applies. A later new failure creates a new episode.
- Deliberate Stop: invalidate all outstanding effects immediately on accepting
  user intent, before waiting for finalization; do not wait for audio/Binder IO.
  Later shutdown callbacks cannot re-arm alarms from that generation.
- Terminal unexpected stop: finish raw/session cleanup normally and retain its
  independent alarm until the operator acknowledges/Stops it. Do not retain
  session writers, USB, recording wake lock or closed runtime to keep sound alive.
  Subsume prior receiver/position/degraded alarms into that terminal episode;
  acknowledging it cannot reveal an older, now-unrecoverable repeating sound.
  Further close/finalization errors update its evidence rather than create fresh
  acoustic episodes after acknowledgement.
- Notification action Acknowledge silences the current episode without stopping
  a still-running recording; its visible unresolved condition remains. It does
  not silence a different or later higher-priority episode. Stop explicitly
  clears all active alerts. Provide Stop/acknowledgement while terminal alarm is
  active even if recording is already stopped.
- A new accepted Start clears a previous terminal alarm. App recreation alone
  must not reset position arming or duplicate effects while the service lives.
- After process restart, report conservative recovered diagnostic evidence, but
  never replay an old acoustic alarm or claim uninterrupted alarm coverage.

Play only one acoustic source at a time: recording-interrupted first, receiver
loss second, position loss third. Disabled/acknowledged acoustic categories do
not suppress another eligible sound. Updating notification text does not restart
the sound or generate duplicate watch alerts.

## Foreground And Playback Ownership

Existing RecordingForegroundService has connectedDevice/dataSync types and
removes foreground state/stops itself after finalization. It cannot simply start
an unmanaged infinite sound thread and then stop normally.

Extend its lifecycle with an explicit alert-only ownership state when terminal
audio is required. Recording resources still close immediately through existing
ordering. Retain only the foreground/audio alarm shell, identifying that
recording is STOPPED. Declare/use mediaPlayback foreground capability for actual
ongoing acoustic playback, including after receiver detach; do not misrepresent
it as continuing connected-device recording. Establish necessary foreground
ownership from the user-started recording lifetime, never a boot/background
resurrection workaround. When acoustic alarm is stopped/disabled/acknowledged,
release playback/focus and stop the alert-only shell. A separate visible failure
notification can remain without retaining a foreground service when sound is off.

The implementation plan must cover supported SDK behavior, focus denial and
foreground promotion rejection. Notification/playback failure is isolated and
reported where possible; it cannot cancel raw finalization or become a reason to
hold recording resources open. No additional alarm wake lock is assumed; add
one only if device tests demonstrate a need and review its bounded ownership.

Audio uses appropriate alarm attributes and respects Android volume, focus,
audio routing and Do Not Disturb policy. Do not force speaker routing, change
system volume or request special DND permission. System-selected sounds repeat
without overlap, separated by a pause. An unavailable saved URI is visibly
reported and uses that category's built-in fallback; never crashes or deletes
the user's selection. Preview is short, stoppable, non-repeating and cannot
interrupt an active real alarm or start the recorder.

Proposed bounded focus policy: transient loss pauses sound and allows recovery
through platform focus callbacks; focus denial/delayed grant gets at most 30 s
of pending/retry ownership, using bounded backoff rather than a busy loop.
Permanent loss, foreground rejection or exhaustion of that window records
audio-delivery unavailable, releases alert-only playback ownership and leaves
the visual terminal alert where enabled/permitted. It does not acknowledge the
underlying failure, retry by resurrecting the service or affect recording.
Late grant callbacks after expiry/Stop are fenced. A later explicit user action
can retry sound. This is a proposed implementation limit, not a promise to bypass
platform silence or play indefinitely without audio permission/focus.

## Settings And Visual Delivery

Proposed defaults: visual and acoustic enabled for all three categories, with
the two agreed built-in patterns and independent sound selections. Keep settings
global, independent of receiver settings-set ownership and profile application.
Use normal app menu conventions; no new main-screen setup tile is needed.
Position-loss delay is numeric, proposed range 1-60 s, initial 5 s.

Use stable per-category notification IDs/channels, standard system-compatible
layouts, clear distinct titles/icons and cause text. Never rely on color alone.
Separate alarm channels from the existing low-importance recording channel.
Default visual alarm channels are high importance but silent: the acoustic
controller owns phone repeats, avoiding duplicate sounds. Respect OS channel
overrides and show effective restrictions in settings rather than fighting them.
Visual off suppresses that alarm's extra visual notification, not Android's
mandatory foreground-service indication. Acoustic off must not hide visual alarms.

Expose permission/channel status and shortcuts to Android notification settings.
Show a clear non-blocking limitation when requested visual alerts are blocked;
never fail recording because permission, a sound provider or a watch is absent.
Lock-screen content excludes coordinates, credentials and private paths. Standard
notifications remain eligible for watch bridging; no local-only flag. Watch
mirroring, watch tone/vibration and lock-screen visibility are ultimately controlled
by system/companion settings, not guaranteed by phone sound selection.

## Recording Isolation Contract

- No notification, audio, sound-provider access, settings IO or JSON construction
  in raw receiver append/correction routing/receiver-command paths.
- Detector consumes lightweight bounded advisory snapshots after append. It
  cannot back-pressure raw capture, own session writers or call recorder Stop.
- Separate bounded/conflated notification and acoustic effect lanes: a stalled
  notification Binder call cannot prevent tone playback or capture progress.
- Never join/wait for alarm effects from capture, reconnect or finalization.
  Use generation fencing so delayed work cannot start old sounds after Stop.
- Deduplicate lifecycle transitions and bound memory, retries and worker count;
  no notification repost per beep and no thread/player creation per receiver read.
- Catch platform/sound-provider failures at effect boundaries, retain truthful
  alarm-delivery status and use existing safe Minimal diagnostics. Diagnostic
  failures cannot recursively alarm.
- Failures in arbitrary application code, native code or resource exhaustion
  cannot be made absolutely impossible; make no zero-overhead or post-kill claim.

## Verification And Specification Work

After approval, add canonical requirement IDs/matrix mappings for detection,
settings, outputs, lifecycle and raw-path isolation before implementing behavior.
The implementation plan must be checkpointable and use TDD for pure policy and
failure-path helpers. Reuse an independent review once for the integrated change.

Automated coverage must include acquisition silence; no previous-session arming;
5 s boundary and wall-clock jumps; invalid/old solutions; fixed/float/standalone;
receiver loss/recovery and overlapping categories; intended versus unexpected
stop; required versus advisory errors; independent toggles and persistence;
automatic sound cancellation and warning removal on verified recovery without
acknowledgement; receiver recovery while positioning remains unavailable;
sound URI fallback; playback arbitration and acknowledgements; late work after
Stop/new Start; bounded queues and resources; blocked/throwing notification/audio
effects with ongoing byte-exact raw writes; normal finalization without alarm
joins; no writer retention by an alert-only shell; SDK foreground/focus rejection.

Run the repository pre-push gate, clean-host CI (including both distributions)
and native packaging. Field validation: target Android versions, UM980/M8T,
screen off/backpack, unplug/replug, no-fix after acquisition, NTRIP interruption,
storage failure simulation, all sound choices, volume/DND/headphone routes,
permission/channel denial, real watch delivery and repeated Start/Stop. Record
unexecuted checks honestly; design approval is not field evidence or a general
strict-framework release-gate waiver.

## References

- Android notification and channel behavior:
  https://developer.android.com/develop/ui/compose/notifications
- Watch notification bridging:
  https://developer.android.com/training/wearables/notifications
- Foreground service types and ongoing playback:
  https://developer.android.com/develop/background-work/services/fgs/service-types
- Android audio focus and background restrictions:
  https://developer.android.com/media/optimize/audio-focus
  https://developer.android.com/about/versions/17/changes/bg-audio
- System sound picker:
  https://developer.android.com/reference/android/media/RingtoneManager

## Builder Decisions Still Allowed

Exact beep pitches, waveform/durations/pause, IDs, icon assets, class names,
storage encoding and worker primitives are builder choices, provided the agreed
patterns remain clearly distinct and every isolation/lifecycle test above holds.
The enabled defaults, acknowledgement behavior, alert-only lifetime, fallback,
delay bounds and bounded focus policy were proposed in this document and are
included in the subsequent human approval; they were not inferred as original
user instructions.

## Builder Check

One read-only builder check requested with gpt-6.1-sol, medium effort, on
2026-10-29. It identified three substantive ambiguities, resolved in this draft:
terminal episodes subsume obsolete alarms; acknowledgements bind cause/episode
IDs and cannot silence newly fatal failures; audio focus unavailability has an
explicit bounded policy and visible delivery state instead of indefinite retry.
These are design proposals for human approval, not implementation/test evidence.
