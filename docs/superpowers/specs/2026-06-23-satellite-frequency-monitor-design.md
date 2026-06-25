# Satellite Frequency Monitor Design

## Purpose

Add a live satellite-frequency monitor that helps operators compare what the
external rover receiver observes, what the NTRIP base corrections contain, and
which satellite signals are used by the selected solution engine.

The feature is for recording sessions only. It is an advisory monitor layered
after byte-exact receiver and correction capture. It must not block, mutate or
resample `receiver-rx.raw`, `tx-to-receiver.raw` or `correction-input.raw`.

The monitor is explicitly scoped to external USB GNSS receivers. Android
internal GNSS is not a supported input for this feature and must not be implied
in user-facing text.

## Goals

- Show per-frequency satellite counts for rover and base corrections.
- Group detailed rows by frequency band, such as `L1`, `L2` and `L5` (available bands can be collected from UM980 specification); only bands with are available in current device OR base shall be displayed, unused ones not (e.g., UM980 depends on signalgroup selection)
- Compare rover and base signal strength side by side where strength metadata
  is available.
- Distinguish observed, base-present and used-in-solution state clearly.
- Support both receiver-internal RTK and RTKLIB-backed solution workflows.
- Use explicit satellite-monitoring profiles for supported receivers.
- Keep the compact dashboard simple while providing a detailed advanced view.
- Preserve RtkCollector's foreground-service-owned recording architecture.

## Non-Goals

- No map, GIS, shapefile or feature-collection functionality.
- No replay or persistent per-satellite chart history in this design.
- No hidden receiver command injection.
- No inference of per-frequency solution usage from generic NMEA GSV alone.
- No requirement that V1 recording, NTRIP routing or fixed-base workflows use
  RTKLIB.

## User Interface

The main recording dashboard gets a compact `Satellites` card that appears only
while recording. The main card always stays with the active main solution
engine and does not expose an engine selector. The selected engine label is
display-only on the compact card. Engine selection belongs to the broader
workflow/profile policy and detailed monitor views, not to the main dashboard
card.

The default main engine is profile-driven:

- UM980/N4 and u-blox M8P internal RTK monitoring profiles default to
  `In-device RTK`.
- u-blox M8T and similar raw-only monitoring profiles default to `RTKLIB`.

If the active main engine is unavailable for the receiver/profile, the card and
advanced screen show an unavailable state instead of silently falling back.

The compact card groups first by constellation and then by frequency band. Each
frequency cell contains two horizontal boxed-bar rows:

- `R` for rover observations;
- `B` for base correction observations.

Each row shows `used/visible` as a right-aligned number. The boxed bar uses
lower-saturation boxes for visible satellites and higher-saturation boxes for
satellites used by the active main engine. Used boxes are drawn as a saturated
prefix inside the visible total. This means the visible count sets the total
filled extent, and the used count sets the saturated subset.

The card has three labelled freshness dots near the top:

- `R`: rover observation freshness;
- `B`: base correction observation freshness;
- `S`: selected-solution usage freshness.

The card has a compact `(i)` affordance in the top-right corner that explains
the `R/B/S` labels and `used/visible` boxed-bar semantics.

The dashboard follows the normal light application colour scheme by default.
The dashboard layout settings include a binary dark-mode switch for the
satellite card only. When enabled, the satellite card uses the dark colour
scheme from the approved compact-card mockup without changing the rest of the
application theme.

Tapping the card opens the detailed live `Satellite monitor` screen.

The advanced screen defaults to grouping rows by frequency band. It also has a
slider switch that changes the detailed grouping mode to constellation-first,
with frequency bands nested inside each constellation. The selected grouping is
only a detailed-view presentation mode; the compact dashboard remains grouped
by constellation first, with frequency bands nested inside each constellation.

Each satellite row shows rover and base bars side by side when signal strength
is available. Colour indicates state:

- rover observed and used in the selected solution;
- rover observed but not used;
- base correction present;
- unavailable or stale source.

Signal-strength bars are optional and controlled from the monitor menu. If
disabled, the screen still shows per-frequency counts and per-satellite
presence/usage states.

## Data Model

Add a receiver-agnostic live model:

- `SatelliteMonitorSnapshot`
- `SatelliteSignalKey`
- `RoverSignalState`
- `BaseSignalState`
- `SolutionSignalState`
- `SatelliteFrequencySummary`

`SatelliteSignalKey` identifies constellation, satellite ID and normalized
frequency band. The model separates three concepts:

- `rover observed`: evidence from the external USB receiver observation stream;
- `base present`: evidence from NTRIP RTCM MSM corrections;
- `used in solution`: evidence from the selected solution engine.

This separation is required because M8T-like receivers observe raw data but do
not solve RTK internally, while UM980/N4 and M8P-like workflows can expose
receiver-internal RTK state.

## Solution Engines

The monitor supports two solution-engine routes.

### In-Device RTK

This route is for receivers that can solve RTK internally and expose solution
state.

Inputs:

- rover observations from the external USB receiver's observation/status
  messages;
- base observations from NTRIP RTCM MSM parsing;
- used-in-solution state from receiver RTK/navigation status where available.

The UI labels this route as `In-device RTK`.

### RTKLIB

This route is for raw-observation receivers that do not solve RTK internally,
especially u-blox M8T.

Inputs:

- rover observations from receiver raw observation messages, such as UBX
  `RXM-RAWX` where applicable;
- base observations from NTRIP RTCM MSM parsing;
- used-in-solution state from RTKLIB live per-satellite status.

The UI labels this route as `RTKLIB`.

RTKLIB aggregate satellites-used counts are not enough for the final colour
model. M8T-quality monitoring requires a live RTKLIB bridge that exposes
per-satellite, per-signal used/not-used state.

## Source Availability And Freshness

Each source has its own availability and freshness state:

- rover observations;
- base observations;
- solution usage.

The monitor never infers missing data.

If rover observations are present but base MSM data is missing, the monitor
shows rover-only diagnostics and marks base unavailable. If base MSM is present
but the selected solution engine does not expose per-satellite usage, the
monitor shows rover/base correlation and marks solution usage unavailable. If
correction bytes stop, stale base data fades out and then becomes unavailable.
This is separate from receiver capture health.

For active profiles without explicit satellite-monitor support, the card shows:

```text
Satellite monitor requires a satellite-monitoring receiver profile
```

No hidden command profile mutation is allowed.

## Receiver Profiles

Satellite monitoring uses explicit built-in profiles. Built-ins remain visible
and read-only; users can copy them before editing.

Initial profile set:

- `UM980 rover + NTRIP + satellite monitor`
  - Default engine: `In-device RTK`.
  - Rover observations from UM980 observation/reporting messages.
  - Solution usage from UM980/N4 RTK/navigation status where available.
  - Base observations from RTCM MSM corrections.

- `u-blox M8P rover + NTRIP + satellite monitor`
  - Default engine: `In-device RTK` when configured for receiver-side RTK.
  - Rover observations from u-blox satellite/raw observation messages available
    on M8P.
  - Solution usage from u-blox navigation/status messages where available.
  - Base observations from RTCM MSM corrections.

- `u-blox M8T rover + NTRIP + satellite monitor`
  - Default engine: `RTKLIB`.
  - Rover observations from UBX raw observation messages.
  - Solution usage from RTKLIB live per-satellite status.
  - Base observations from RTCM MSM corrections.

- `Generic NMEA/RTCM + NTRIP`
  - No full satellite-frequency comparison unless an explicit profile supplies
    real per-frequency rover observations.
  - Generic NMEA GSV may remain aggregate/view diagnostics only and must not be
    treated as full frequency or solution-usage evidence.

## RTCM MSM Parsing

Implement RTCM3 MSM observation parsing in the correction layer for base-side
signal presence.

Initial target families:

- GPS `107x`;
- GLONASS `108x`;
- Galileo `109x`;
- BeiDou `112x`;
- QZSS and SBAS if already relevant to supported workflows.

The parser should decode satellite and signal masks into normalized
`SatelliteSignalKey` entries. C/N0 or equivalent signal-strength fields are
exposed only for MSM variants that carry them. Unsupported or malformed RTCM
frames are dropped as advisory parser failures; raw correction capture
continues.

## Performance And Persistence

The monitor uses a bounded advisory pipeline after raw receiver bytes and
correction bytes have already been persisted.

Data retention:

- live snapshots are kept in memory only while recording;
- session files may record coarse availability/capability events;
- high-rate per-satellite chart history is not persisted;
- replay is out of scope.

Update cadence:

- parsers may consume high-rate observation/correction streams;
- dashboard snapshots are throttled;
- the advanced screen may update faster than the compact card, but still
  through bounded queues/state flows;
- bar charts render the latest snapshot, not every observation event.

Parser failures must not stop recording, NTRIP, RTKLIB or session writers.

## Documentation Requirements

Implementation must update canonical docs, not only this design record.

Required documentation updates:

- formal requirements under `docs/specification/` for live
  satellite-frequency monitoring, source availability, explicit profiles,
  engine selection and external-USB-only scope;
- `docs/specification/verification-matrix.md` with automated tests and manual
  hardware/caster validation gaps;
- user-facing docs that state RtkCollector is currently built for external USB
  GNSS receivers and this monitor does not use Android internal GNSS;
- receiver profile docs with satellite-monitor profile commands and expected
  telemetry.

## Testing

Add focused tests for:

- RTCM MSM satellite/signal mask parsing and frequency-band mapping;
- receiver observation mapping for each supported profile family where fixtures
  are available;
- solution-engine default selection:
  - M8T defaults to `RTKLIB`;
  - UM980/N4 and M8P internal RTK profiles default to `In-device RTK`;
- unavailable selected engine state;
- compact dashboard summaries;
- stale and unavailable source labels;
- advanced screen grouping and detailed source availability without changing
  the main-card active-engine rule;
- optional signal-strength bar visibility;
- RTKLIB per-satellite used/not-used mapping after the RTKLIB bridge exposes
  that status.

Hardware/manual validation remains required for:

- UM980/N4 observation and solution-status profile output;
- u-blox M8P receiver-internal RTK profile output;
- u-blox M8T raw observation plus RTKLIB per-satellite usage;
- live caster streams carrying the target RTCM MSM message families.

## Implementation Boundaries

This design should be implemented behind explicit capability checks. The UI
must reflect capability and freshness honestly rather than making partial data
look complete.

The implementation plan should keep reusable protocol parsing, solution-engine
selection, profile definitions and Compose display state separate enough to test
independently.
