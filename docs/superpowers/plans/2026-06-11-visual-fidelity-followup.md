# Visual Fidelity Follow-Up Checkpoint

## Source

Review current `main` against
`/data/data/com.termux/files/home/downloads/DOC-20260610-WA0022.mht`.

## Scope

UI-only changes. Do not touch receiver recording, NTRIP protocol handling,
UM980 parsing, workflow validation, storage writers, maps/GIS, or session
format behaviour.

## Must Fix

1. Default dashboard should visually match the compact mockup: standard phone
   portrait should use a dense two-column card grid where width permits.
2. Dashboard cards should be shorter and more instrument-like; avoid the
   current scroll-heavy `184.dp` card minimum and fixed `68.dp` major value.
3. Default setup strip should be a coherent fixed chip grid for Workflow,
   Mountpoint, Receiver and Storage. Settings-set details belong in the setup
   profiles card and Settings menu.
4. Rail layout should look like a real rail: compact left context, active
   left-border styling and integrated setup actions where practical.
5. Profile list rows should include compact descriptions and wrapped actions
   instead of large weighted button rows.
6. Profile editor chrome should expose frozen Save, Discard and Delete actions
   where delete is available.

## Should Fix

1. Dashboard top bar should include workflow/receiver context and a compact
   state badge.
2. Settings hub should be visually lighter and include a help action.
3. Rename dialog should use Save, Discard and Cancel.
4. Help overlay should be a compact local help card with one dismiss control.

## Resume Notes

Keep changes small and checkpoint after each slice. Preferred verification:
targeted app UI tests, `:app:compileDebugKotlin`, then broader tests if the
local Android/Termux environment allows it.
