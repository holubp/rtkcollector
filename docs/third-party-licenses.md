# Third-Party Licences

Bootstrap dependencies:

- Gradle build tooling.
- Kotlin Gradle plugin and Kotlin standard library.

Future Android, USB serial, NTRIP, RTCM, logging or persistence dependencies
must be listed here before release distribution. Dependencies must be compatible
with GPL-3.0-or-later distribution.

RTKLIB-EX:

- Upstream: `https://github.com/rtklibexplorer/RTKLIB.git`.
- Pinned development commit:
  `8dfabc9a106b2e74c069bc80f0d7743f314e6ab4`, recorded in
  `third_party/rtklib-ex/snapshot.json`.
- Local checkout: `third_party/rtklib-ex/upstream/`, created by
  `tools/update_rtklib_ex.py` and ignored by Git.
- Licence: the pinned upstream checkout contains `license.txt`, which states
  RTKLIB is distributed under the BSD 2-clause licence and includes the
  copyright notice for T. Takasu. Some upstream subdirectories include
  additional third-party notices; release packaging must include the upstream
  licence file and preserve applicable notices for the compiled sources.
- RtkCollector native build glue:
  `app/src/main/cpp/CMakeLists.txt` and `app/src/main/cpp/rtklib_bridge.cpp`.

The Android app builds `librtkcollector_rtklib.so` from the ignored local
checkout only when a valid Android NDK is available. The current integration
does not commit RTKLIB-EX upstream source files into this repository, but
release artifacts that include the compiled native library must ship the
corresponding RTKLIB-EX licence and source-offer information required by the
project's GPL-3.0-or-later distribution.
