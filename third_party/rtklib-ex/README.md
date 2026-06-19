# RTKLIB-EX Snapshot

RtkCollector uses RTKLIB-EX as an optional native library for V2 in-phone RTK
solutions. The repository commits only the Android/native build glue and
snapshot metadata; the upstream source checkout under `upstream/` is a local
ignored working tree created from the pinned commit below.

Use the helper script below to create or refresh the local checkout for Android
native-build work:

```sh
python3 tools/update_rtklib_ex.py --ref <40-character-commit-hash>
```

Default upstream:

```text
https://github.com/rtklibexplorer/RTKLIB.git
```

Rules:

- use an exact 40-character commit hash for reproducible builds;
- do not track upstream `main`, tags or moving branches in committed state;
- do not commit generated build artifacts;
- do not commit local experiments under `upstream/`;
- document any local patch under `third_party/rtklib-ex/patches/`;
- keep `snapshot.json`, `NOTICE` and `docs/third-party-licenses.md` aligned
  with the intended native build.

The Android app module builds `librtkcollector_rtklib.so` from the local
checkout when a valid Android NDK is installed. Termux/aarch64 checkouts may
not have a runnable NDK/native toolchain; in that case Kotlin/JVM checks remain
valid, but the native `.so` must be validated on Android Studio, CI or another
host with a working NDK.

`snapshot.json` is intentionally not ignored: it is the small reproducibility
record that should be reviewed and committed with the native build integration.
