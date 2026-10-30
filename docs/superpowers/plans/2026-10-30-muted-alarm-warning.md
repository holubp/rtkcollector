# Muted Alarm Warning Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for this bounded extension.

**Goal:** Warn the recording operator when enabled alarm audio is known silenced.
**Architecture:** Extend the existing Home-only, single-lane advisory observer;
read alarm volume, stream mute and DND independently, off capture/UI threads.
Prefer a definite silence warning over the existing possible-DND warning.
**Tech Stack:** Kotlin, Compose, existing Android AudioManager/NotificationManager.
**Spec:** docs/specification/android-runtime.md, ANDROID-ALARM-008/009.

## Decisions And Constraints

- Reuse the current warning strip and one-second visible/recording/acoustic poll;
  do not add a second monitor or change service/audio ownership.
- Any enabled acoustic category qualifies, including disconnect/position loss.
- Zero volume, known stream mute or DND total silence warrants definite text.
  Silent ringer mode alone does not; other DND modes retain possible-warning text.
- Failed/unknown readings stay unknown; positive volume never promises audibility.
- No new permissions, setters, popups, dependencies or release/tag changes.
- Existing RC9 source/APK remains immutable; push source changes to main.

## Checkpoints

- [x] Add pure-policy regression tests; observe RED, then implement GREEN.
- [x] Integrate independent read failures and existing Home strip; add SDK host tests.
- [x] Update operator docs/matrix; run focused tests and prescribed full gate.
- [ ] Review full delta, commit/push and verify exact-commit host CI.

Physical vendor mute, DND and headset-volume behavior remains a manual check.

## Evidence And Gate Status

Policy RED: `muted-warning-red.log` ran seven tests with four expected assertion
failures for missing confirmed-silence behavior, not a compilation error.
Implementation reuses the current observer lane and Home Surface. Android reads
catch failures independently and do not invoke volume/mute/DND setters.
The strict checker is absent in this checkout. Owner approval was requested for
this update's use of normal pre-push/review/host-CI gates; the RC9-only exception
is not silently inherited. No push until that decision; RC9 tag/APK stay unchanged.

References:
- https://developer.android.com/reference/android/media/AudioManager#getStreamVolume(int)
- https://developer.android.com/reference/android/media/AudioManager#isStreamMute(int)
- https://developer.android.com/reference/android/app/NotificationManager#INTERRUPTION_FILTER_NONE

Local verification: `muted-warning-green.log` passes all seven policy tests.
`pre-push-muted-warning.log` passes the clean full gate: 1,345 tests / 192 suites,
zero failures/errors/skips, all app JVM test sources compile and IDE aliases
dry-run. SDK 26/27/33 host cases now cover actual STREAM_ALARM volume, mute and
unmute reads, but are only compile-checked locally. Bounded self-review confirms
no recording-service, playback, permission or volume/DND-setting modifications.
Commit remains local pending the owner's gate decision; host CI is unexecuted.
