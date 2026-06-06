# Testing Plan

## Automated Tests

- Unit tests for receiver-driver API data classes.
- Unit tests for NMEA parsing.
- Unit tests for RTCM3 frame extraction and CRC when implemented.
- File replay tests for deterministic capture pipeline behaviour.

## Manual And Protocol Tests

- High-baud soak test protocol.
- Screen-off/background manual test protocol.
- NTRIP reconnect test protocol.
- Unclean termination recovery test protocol.

## High-Baud Acceptance Target

- 921600 baud for several hours.
- Recording continues screen off.
- Parser failure does not block capture.
- Raw stream is not modified.
- Session is recoverable after unclean stop.

## Bootstrap Validation

The first skeleton should pass Gradle build and unit tests for pure Kotlin data
models and simple parser behaviour. Android foreground-service and USB serial
tests are deferred until an Android SDK and device/emulator test target are
available.
