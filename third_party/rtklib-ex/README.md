# RTKLIB-EX Snapshot

RtkCollector uses RTKLIB-EX as a planned optional native library for V2
in-phone RTK solutions. The upstream source is not bundled in this repository
yet. Use the helper script below to create or refresh a local, pinned checkout
for Android native-build work:

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
- update `snapshot.json`, `NOTICE` and `docs/third-party-licenses.md` before
  bundling RTKLIB-EX source or binaries in a release build.

The repository currently commits only this control directory, patch policy and
automation. The local `upstream/` checkout is ignored by Git. `snapshot.json`
is intentionally not ignored: after an exact RTKLIB-EX commit is selected, it
is the small reproducibility record that should be reviewed and committed with
the native build integration.
