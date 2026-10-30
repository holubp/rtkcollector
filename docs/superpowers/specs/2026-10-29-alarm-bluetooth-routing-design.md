# Bluetooth Alarm Routing Before RC9

Status: Approved by human:holubp: "all three approved" on 2026-10-29, after
explicit approval of immediate phone-speaker fallback on Bluetooth disconnection
or inability to play. Implementation is tracked in the recording-alarm plan.
Supplements the recording-alarms design and supersedes its
no-routing-override rule only for explicitly enabled, per-player alarm routing.

## Operator Behavior

- Add one app-wide, persistent Alarms-menu toggle: Use Bluetooth audio for alarms.
  Proposed initial value is off; older saved settings retain current behavior.
- Off retains normal Android audio routing. It must not force the phone speaker
  or change routing for any other application.
- On requests a connected Bluetooth audio output for every enabled acoustic
  alarm category and sound preview. A paired device or notification-only watch
  is not necessarily a usable audio output. Watch notification mirroring remains
  independent.
- If Bluetooth disconnects, is unavailable or cannot play the alarm, immediately
  fall back to the built-in phone speaker. Do not leave playback silently waiting
  for Bluetooth or repeatedly retry a failing route.
- If Bluetooth becomes available again while an alarm continues, switch back
  without acknowledging, creating a new episode, duplicating playback or changing
  recording state. Avoid retrying a rejected device continuously; retry on a real
  device-availability change, a new alarm or an explicit settings/preview action.
- Prefer the current usable Bluetooth output when identifiable. If several are
  available, retain the chosen usable output while connected; otherwise use a
  stable deterministic choice. No additional device picker is proposed for RC9.
- Show a compact actual-route/fallback status in Alarms. If Android rejects the
  requested route, report that clearly; do not label requested Bluetooth as
  confirmed delivery. A preference setter's success alone is not verification.
- If even the speaker cannot play, preserve existing visual/operational evidence
  and report audio delivery failure. Respect volume, audio focus and DND; never
  claim that a selected/routed device proves the operator physically heard sound.

## Isolation And Compatibility

Use per-player routing. Global communication mode, Bluetooth SCO activation,
global speakerphone switching, volume changes and DND bypasses are not allowed.
Device callbacks, route enumeration/verification, provider/decoder work and
playback stay on bounded alarm-owned workers. They must not access raw writers,
receiver transports, correction routing or Stop/finalization dependencies.

Preserve one acoustic owner, real-alarm priority over previews, generation and
episode cancellation, recovery, deliberate Stop, terminal ownership and existing
bounded failure/deadline behavior. Toggling routing while recording must affect
only playback; no receiver restart or recording reconfiguration is allowed.

Preserve the current minimum SDK 26. A MediaPlayer-only implementation that
unguardedly calls API-28 routing methods is not acceptable. The implementation
plan must select a supported routing-capable playback solution covering Android
8 and later, built-in sounds and chosen Android notification sounds, retaining
safe built-in fallback for inaccessible/unsupported saved sounds. Do not silently
raise minimum SDK, hide the feature on older supported releases or weaken normal
recording reliability to simplify implementation. Any additional permission
requires an explicit rationale and approval before addition.

## Verification And Release

Use TDD for settings migration/persistence, deterministic device selection,
actual-versus-requested route decisions, connect/disconnect/rejection fallback,
reconnection without duplicate playback, toggle changes, real-alarm/preview
arbitration and stale callbacks after Stop/new recording. Test blocked/throwing
route/provider operations while byte-exact raw recording/finalization continues.

Compile-check all supported API guards and execute appropriate clean-host SDK
tests, including API 26/27 rather than only recent Android. Real Bluetooth and
phone-speaker routing still needs physical-device evidence; record any unrun
device checks explicitly. Update canonical requirements, user docs, privacy/Play
permission rationale if affected, matrix and the checkpointable plan. Re-run
repository gates and focused independent review for the changed playback boundary
before RC9 publication. Earlier 1,293-test receipts do not cover this extension.

The same "all three approved" statement separately approves the normal
CHANGE_NETWORK_STATE permission for detached foreground eligibility and the
RC9-specific use of checked-in pre-push, independent review and GitHub CI while
strict-assurance integration is deferred. This is not a passing strict-checker
receipt or a waiver for subsequent releases. Device checks remain truthfully
separate from source/CI verification.

## API Evidence

- Current Ringtone API has no preferred-device setter:
  https://developer.android.com/reference/android/media/Ringtone
- Preferred routing does not guarantee actual routing; actual-device queries are
  meaningful during playback:
  https://developer.android.com/reference/android/media/AudioRouting
- MediaPlayer preferred-device routing starts at API 28:
  https://developer.android.com/reference/android/media/MediaPlayer#setPreferredDevice(android.media.AudioDeviceInfo)

These API facts constrain implementation; they do not constitute Bluetooth
hardware validation or implementation evidence.
