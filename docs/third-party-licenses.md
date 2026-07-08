# Third-Party Licences

RtkCollector is GPL-3.0-or-later. Before each release, verify this list against
the Gradle dependency graph used for the released AAB/APK.

## Runtime Dependencies

| Dependency | Purpose | Licence family |
| --- | --- | --- |
| Kotlin standard library | Kotlin runtime support | Apache-2.0 |
| AndroidX Core KTX | Android compatibility helpers | Apache-2.0 |
| AndroidX Activity Compose | Compose activity integration | Apache-2.0 |
| Jetpack Compose UI | Compose UI runtime | Apache-2.0 |
| Jetpack Compose Material 3 | Material components | Apache-2.0 |
| Jetpack Compose UI tooling preview | UI previews and preview metadata | Apache-2.0 |

## Test And Build Dependencies

| Dependency | Purpose | Licence family |
| --- | --- | --- |
| Gradle | Build tool | Apache-2.0 |
| Kotlin Gradle plugin | Kotlin compilation | Apache-2.0 |
| Android Gradle Plugin | Android build integration | Apache-2.0 |
| AndroidX Compose BOM (`androidx.compose:compose-bom`) | Compose version alignment (implementation declarations) | Apache-2.0 |
| JUnit BOM (`org.junit:junit-bom`) | JUnit test dependency alignment | Eclipse Public License 2.0 |
| JUnit Jupiter API + Engine split artifacts | App module unit-test API and engine declarations | Eclipse Public License 2.0 |
| JUnit Jupiter aggregate (`org.junit.jupiter:junit-jupiter`) | Equivalent aggregate test declaration used by selected JVM library modules instead of the split app-module form | Eclipse Public License 2.0 |
| JUnit Platform Launcher | Test runtime/discovery | Eclipse Public License 2.0 |
| Kotlin test (`kotlin-test`) | Shared Kotlin unit-test API | Apache-2.0 |
| Compose UI tooling | Debug-only UI inspection | Apache-2.0 |

## RTKLIB-EX

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

## Release Check

Run these reports and update this file if dependencies change:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

No map, GIS, shapefile, advertising, analytics or crash-reporting SDK dependency
is intentionally included in V1.
