# RtkCollector Workflow Specification Design

## Scope

This change defines workflow specifications and a pure Kotlin validation model.
It does not implement USB recording, Android foreground services, NTRIP
networking, RTKLIB integration, UM980 command sequences, UBX configuration,
Android PPP/static solving, maps, shapefiles or GIS editing.

## Architecture

`docs/workflows.md` is the authoritative product specification. `:core:workflow`
contains explicit domain types for receiver role, correction source, correction
targets, solution engines, base context, recording expectations, quality
monitoring and safety. `WorkflowValidator` returns structured errors and
warnings so the later Android UI and capture service can reject unsafe or
underspecified sessions before starting commands, networking or recording.

## Testing

Unit tests cover positive workflow fixtures, required validation errors and
warnings. Tests intentionally use pure Kotlin fixtures rather than Android,
network or receiver hardware code.
