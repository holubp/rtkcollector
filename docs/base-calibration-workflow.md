# Base Calibration Workflow

RtkCollector does not implement PPP or static solving on Android in this
bootstrap phase.

## Workflow

1. Android records a static base session.
2. An external Python pipeline computes a precise `base-position.json` using
   PPP, static processing, long averaging, a known point or another documented
   method.
3. Android imports `base-position.json`.
4. A receiver-specific driver configures fixed-base mode where supported.
5. Base mode may later re-stream RTCM to an NTRIP caster or private server.

## Safety

The imported base position must include frame, epoch if known, method, duration,
uncertainty estimate, antenna height and source session reference. Drivers must
not silently configure fixed-base mode without explicit base-position metadata.
