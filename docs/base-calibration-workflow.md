# Base Calibration Workflow

RtkCollector does not implement PPP or static solving on Android in this
bootstrap phase.

Base calibration and fixed-base operation are separate workflow concepts. The
authoritative workflow model is defined in [Workflows](workflows.md).

## Workflow

1. Android records a static base session.
2. One or more candidate-generation strategies produce candidate coordinates:
   static RTK against a known CORS/EUREF/local base, PPP/static processing,
   receiver survey-in, long-term averaging, manual/known point entry or an
   external `base-position.json`.
3. A candidate is accepted as a `base-position.json` with provenance,
   uncertainty and antenna metadata.
4. Android imports the accepted `base-position.json`.
5. A receiver-specific driver configures fixed-base mode where supported.
6. Base mode may later re-stream RTCM to an NTRIP caster or private server.

Long-term averaging is a lower-grade fallback, not the preferred source of
truth when PPP/static RTK is available.

## Safety

The imported base position must include frame, epoch if known, method, duration,
uncertainty estimate, antenna height and source session reference. Drivers must
not silently configure fixed-base mode without explicit base-position metadata.
