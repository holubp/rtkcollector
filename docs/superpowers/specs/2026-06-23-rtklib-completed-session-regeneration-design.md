# RTKLIB Completed-Session Regeneration Design

## Goal

Add an explicit completed-session action that calls the native RTKLIB-EX library
to regenerate postprocessed RTKLIB outputs from already recorded artifacts.

This is separate from real-time RTKLIB. It must not run during recording, must
not touch the raw receiver capture path, and must only create derived advisory
artifacts.

## Inputs

For a filesystem-backed session directory, regeneration requires:

- `receiver-rx.raw` for rover receiver bytes;
- `correction-input.rtcm3` for base/correction RTCM3 bytes;
- `session.json` metadata for RTKLIB route/preset/frequency hints.

The first implementation is filesystem-only because RTKLIB `convrnx()` and
`postpos()` operate on local file paths. SAF sessions remain share/archive
compatible, but postprocessing requires a later cache-copy implementation.

## Native Processing

The app must call RTKLIB-EX library functions directly:

1. `convrnx()` converts rover raw bytes to RINEX observation/navigation files.
2. `convrnx()` converts RTCM3 correction bytes to base observation/navigation
   files.
3. `postpos()` computes the requested solution.

The RTKLIB input list must preserve rover/base receiver ordering:

1. rover observation file;
2. base observation file;
3. rover navigation files;
4. base navigation files.

Temporary RINEX files are implementation details and must be removed after the
operation, even when postprocessing fails.

## Outputs

Forward-only postprocessing writes:

- `rtklib-postprocessed-forward.nmea`
- `rtklib-postprocessed-forward.pos`

Forward/backward postprocessing writes:

- `rtklib-postprocessed-combined.nmea`
- `rtklib-postprocessed-combined.pos`

Outputs are written through temporary files and moved into place only when both
NMEA and POS outputs are non-empty.

## UI

The recorded-sessions screen exposes `Regenerate RTKLIB` for completed
filesystem-backed sessions. Pressing it opens a compact chooser:

- `Forward only`
- `Forward + backward`

After generation, existing `Share NMEA` source selection offers only the
postprocessed NMEA files that actually exist.

## Constraints

- Do not run RTKLIB postprocessing on the foreground-service raw recording path.
- Do not load or call native RTKLIB unless the user explicitly requests this
  regeneration action.
- Do not overwrite receiver-derived NMEA.
- Do not commit user debug recordings from `samples/`.
