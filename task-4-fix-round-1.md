# Task 4 Fix Round 1

Base reviewed: `af9da66`.

## Resolved Findings

- Correction start/update and caster-upload endpoint/security ingress share a
  strict typed `Intent` parser. Host, mountpoint, transport mode and TLS
  verification must be non-empty strings; port must be an `Int` in range; and
  unsafe acknowledgement must be a `Boolean`. Invalid inputs fail before either
  request object is constructed.
- Distribution behavior is tested through the generated variant `BuildConfig`.
  CI invokes the repository gate and explicitly compiles/tests Google Play and
  sideload debug variants on a full Android host.
- `base-caster-upload.rtcm3` is valid RTCM3 only. CRC-invalid candidate bytes
  are Base64-audited in a dropped-frame `events.jsonl` entry and never reach the
  upload queue. Valid frames remain in the artifact when the bounded queue
  drops them. Receiver raw capture remains independent of this advisory route.

## Verification

- `ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK sh scripts/pre_push_check.sh`
  passed in Termux. This compiled `ServiceNtripIntentParserTest` with
  `:app:compileSideloadDebugUnitTestKotlin`, ran the feasible test suite, and
  dry-ran `:app:unitTestClasses :app:androidTestClasses`.
- Direct execution attempt:
  `sh gradlew :app:testSideloadDebugUnitTest --tests org.rtkcollector.app.recording.ServiceNtripIntentParserTest --no-parallel`
  did not reach JUnit. It failed in `:app:processSideloadDebugResources` because
  the SDK `aapt2` Linux executable cannot run on Termux/aarch64 (`Syntax error:
  ")" unexpected`). This is an environment limitation, not a test result.

## Remaining Full-Host Evidence

The following has not run locally and remains required on CI, Android Studio, or
another Android host with runnable SDK native tools:

```sh
sh gradlew :app:testGooglePlayDebugUnitTest :app:testSideloadDebugUnitTest \
  :app:compileGooglePlayDebugKotlin :app:compileSideloadDebugKotlin --no-parallel
```
