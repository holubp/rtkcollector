# RtkCollector Bootstrap Design

## Scope

This bootstrap creates specifications and a minimal Kotlin-compatible module
skeleton for RtkCollector. It does not implement production Android recording,
USB serial drivers, RTKLIB, Android PPP/static solving, maps, shapefiles or GIS
editing.

## Architecture

The design is receiver-agnostic and capture-first. Raw receiver bytes flow from
transport to capture queue to append-only recorder independently of parsers,
NTRIP and UI. Receiver drivers provide advisory identification, command
construction and parsing only.

## Code Shape

The first code pass defines pure Kotlin data models and interfaces in small
modules: receiver API, receiver skeletons, transport, capture, correction,
session and quality. The `app` module remains a JVM-compatible placeholder
because the local environment has Gradle but no configured Android SDK.

## Testing

Tests cover receiver API data models, session metadata models and the basic
NMEA GGA fix-quality parser. Later Android and transport tests require Android
SDK/device infrastructure.
