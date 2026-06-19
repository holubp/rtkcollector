# RTKLIB-EX Patch Policy

Local RTKLIB-EX patches must be small, reviewable and documented here before
they are used by the Android native build.

Each patch entry should record:

- upstream commit hash;
- patch filename;
- reason for the patch;
- affected RTKLIB-EX files/functions;
- whether the patch is Android build glue or solver behaviour;
- validation performed against u-blox and UM980 replay data.

Solver-behaviour patches require extra review because they can affect RTK
results. Android build-system patches should stay separate from solver changes.
