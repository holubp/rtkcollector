# Testing Plan

## Automated Tests

- Unit tests for receiver-driver API data classes.
- Unit tests for NMEA parsing.
- Unit tests for RTCM3 frame extraction and CRC when implemented.
- File replay tests for deterministic capture pipeline behaviour.
- Unit tests for UM980 runtime command-profile safety.
- Unit tests for session artifact writers and NTRIP secret redaction.
- Unit tests for capture runtime RX/TX/correction sidecar separation.
- Unit tests for NTRIP request rendering, response rejection and reconnect
  state transitions.

## Manual And Protocol Tests

- High-baud soak test protocol.
- Screen-off/background manual test protocol.
- NTRIP reconnect test protocol.
- Unclean termination recovery test protocol.

## Experimental UM980 V1 Manual Test

1. Start with UM980 saved/runtime serial configuration known to answer at
   `230400`.
2. Connect via the tested FTDI-style bridge and request Android USB permission.
3. Run passive/default profile capture at `230400` without NTRIP for at least
   5 minutes.
4. Verify `receiver-rx.raw` grows and `tx-to-receiver.raw` contains only app
   startup commands.
5. Stop recording and verify the foreground notification disappears and the
   session folder remains readable.
6. Repeat with NTRIP configured. Verify `correction-input.raw`,
   `correction-input.rtcm3` and `tx-to-receiver.raw` grow while receiver RX
   continues.
7. Turn the screen off for at least 15 minutes during recording. Verify RX byte
   counters continue after wake.
8. Disconnect USB during recording. Verify a visible error is reported and the
   partially written session artifacts remain recoverable.
9. Only after the above passes, test profile/serial baud changes at `460800` or
   `921600`.

## High-Baud Acceptance Target

- 921600 baud for several hours.
- Recording continues screen off.
- Parser failure does not block capture.
- Raw stream is not modified.
- Session is recoverable after unclean stop.

## Bootstrap Validation

The first skeleton should pass Gradle build and unit tests for pure Kotlin data
models and simple parser behaviour. The experimental Android foreground-service
and USB serial path should at least pass `:app:compileDebugKotlin` locally. Full
APK assembly may fail on Termux if SDK native tools such as `aapt2` cannot run
on the Android/aarch64 host.
