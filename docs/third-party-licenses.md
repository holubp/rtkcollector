# Third-Party Licences

Bootstrap dependencies:

- Gradle build tooling.
- Kotlin Gradle plugin and Kotlin standard library.

Future Android, USB serial, NTRIP, RTCM, logging or persistence dependencies
must be listed here before release distribution. Dependencies must be compatible
with GPL-3.0-or-later distribution.

RTKLIB-EX integration is planned but no RTKLIB-EX upstream source snapshot or
native binary is bundled in this repository yet. The helper script
`tools/update_rtklib_ex.py` can create a local pinned checkout from
`https://github.com/rtklibexplorer/RTKLIB.git` for development, but that local
checkout is ignored by Git. The current development snapshot metadata points at
commit `8dfabc9a106b2e74c069bc80f0d7743f314e6ab4` and is recorded in
`third_party/rtklib-ex/snapshot.json` for reproducibility. Before RTKLIB-EX
source or binaries are bundled for distribution, this file and the root
`NOTICE` file must record the exact upstream commit, licence terms and any
local patches.
