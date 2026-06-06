# Temporary-Base Preparation Workflow

RtkCollector V1 records the evidence needed to derive a base coordinate, but it
does not implement PPP or static solving on Android.

Temporary-base preparation and fixed-base operation are separate workflow
concepts. The authoritative workflow model is defined in
[Workflows](workflows.md).

## Workflow

1. Android records a temporary static base session close to the rover, usually
   with good sky visibility.
2. The session records raw observations at `>= 1 Hz` where supported, the
   normal in-device solution and receiver PPP solution where supported.
3. One or more candidate-generation strategies produce candidate coordinates:
   static RTK against a known CORS/EUREF/local base, PPP/static processing,
   receiver in-device PPP, long averaging of non-PPP in-device solution,
   receiver survey-in, manual/known point entry or an external
   `base-position.json`.
4. A candidate is accepted as a `base-position.json` with provenance,
   uncertainty, frame/datum, epoch and antenna metadata.
5. Android imports the accepted `base-position.json`.
6. A receiver-specific driver configures fixed-base mode where supported.
7. Base mode may later re-stream RTCM to an NTRIP caster or private server.

Long-term averaging is a lower-grade fallback, not the preferred source of
truth when PPP/static RTK is available.

## Safety

The imported base position must include frame, epoch if known, method, duration,
uncertainty estimate, antenna height and source session reference. Drivers must
not silently configure fixed-base mode without explicit base-position metadata.
