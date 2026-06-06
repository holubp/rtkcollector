# Product Requirements

## Purpose

RtkCollector is a robust Android GNSS receiver recorder, NTRIP client,
correction router, receiver controller and base-calibration workflow companion.
It is receiver-agnostic and byte-exact first.

## Must-Have Requirements

- Android USB serial capture from GNSS receivers.
- Robust operation with the screen off and the app in the background.
- Foreground-service based recording.
- Byte-exact raw receiver stream capture.
- Bidirectional serial communication.
- NTRIP client receiving RTCM corrections and writing them to the receiver.
- Automatic NTRIP reconnect after network interruption.
- Custom receiver initialisation scripts and profiles.
- Rover mode.
- Base recording mode.
- Fixed-base mode using externally calibrated base coordinates.
- Session export compatible with downstream Python processing.
- Live quality monitoring from NMEA, RTCM and receiver-native messages where
  available.

## Should-Have Requirements

- UM980 / Unicore N4 profile.
- Generic NMEA + RTCM profile.
- u-blox M8P profile.
- u-blox M8T profile.
- Base RTCM re-streaming to a private caster or rtk2go.
- File replay transport for deterministic tests.

## Explicitly Out Of Scope

- Maps.
- Shapefiles.
- GIS editing.
- Feature collection.
- Cartographic survey project management.

## Bootstrap Scope

This first repository pass creates specifications, module boundaries, driver API
skeletons, session data models and minimal tests. It does not implement a full
Android foreground service, receiver command library, NTRIP caster, RTKLIB
integration or Android-side PPP/static solver.
