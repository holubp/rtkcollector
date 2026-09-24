# Task 4 Fix Round 2

Base: `32583cd`.

## Changes

- Correction start and correction update both reach `ntripRuntimeConfig`, now
  calling `correctionNtripRequestFromIntent`. Source upload calls
  `uploadNtripRequestFromIntent`. Both are the production request-construction
  boundaries and use the shared typed Intent validation before returning a
  concrete request. No raw RX capture path changed.
- `ServiceNtripIntentParserTest` now calls those actual factories with typed
  Intent extras. It covers missing, mistyped, blank/invalid host and mountpoint,
  missing/mistyped/out-of-range port, transport and verification errors, Play
  distribution restrictions, and missing/mistyped/false unsafe-TLS
  acknowledgement. The old synthetic callback tests were removed.
- Session-format, correction-routing and user-workflow docs now define
  `base-caster-upload.rtcm3` as valid RTCM upload candidates extracted before
  queue admission. Queue-dropped candidates can be present, so the artifact
  need not equal bytes offered to or sent to the caster.

## Verification

- RED: Termux app test-source compilation failed on unresolved concrete
  request-factory calls before implementation. This was a compile RED, not an
  executed behavioral RED.
- GREEN: `sh scripts/pre_push_check.sh` passed with `ANDROID_HOME` set to
  `/storage/3830-3863/Termux/AndroidSDK` and the worktree marked Git-safe for
  this process. It ran 13 Python gate tests, the formal spec check, clean
  production Kotlin compilation, app test-source compilation, feasible app/JVM
  tests, and Android Studio compatibility-task dry-runs.
- After the final acknowledgement test addition,
  `sh gradlew :app:compileSideloadDebugUnitTestKotlin -x :app:processSideloadDebugResources --no-parallel`
  passed with the gate-generated debug `R.jar` seeded. `git diff --check` and
  `python3 tools/check_spec_requirements.py docs/specification` passed.

## Full-Host Limit

The new Robolectric test was compiled but not executed on Termux. Direct
`testSideloadDebugUnitTest` failed before JUnit in
`:app:processSideloadDebugResources`: SDK `aapt2-9.2.0-15009934-linux` cannot
start on Termux/aarch64 (`Syntax error: ")" unexpected`). Excluding that task
also failed before JUnit because AGP's runtime-JAR task requires its resource
provider. An Android host with runnable SDK native tools must execute both
Google Play and sideload debug unit tests and compiles before this round has
full behavioral GREEN evidence:

```sh
sh gradlew :app:testGooglePlayDebugUnitTest :app:testSideloadDebugUnitTest \
  :app:compileGooglePlayDebugKotlin :app:compileSideloadDebugKotlin --no-parallel
```

Live receiver/caster validation remains open. No push was made.
