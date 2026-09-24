# Google Play Publication Checklist

This checklist is the source document for Play Console publication declarations.
It must be reviewed before every Play release.

## Current Intended Release Status

The current source candidate is `1.0-RC2`. Its GitHub source release may be
published before APK/AAB artifacts are attached. It is not ready for Play upload
until the signed release build and manual checks below have passed.

RtkCollector remains experimental GNSS field-recording software. Store listing text
must say that hardware support and receiver commands are experimental and that
users must validate recordings before operational use.

## Play Data Safety Draft

Declare the following data handling based on current V1 behaviour:

- Precise location: processed locally from external receiver streams; stored in
  user-controlled session files; optionally transmitted to the selected NTRIP
  caster if GGA upload is enabled.
- Files and docs: user-created session files, session archives and settings
  backups can be shared through Android share targets selected by the user.
- Device or other IDs: USB VID/PID, receiver identification and profile IDs are
  stored in session metadata/settings for app functionality.
- Personal info/User IDs: NTRIP username may be stored locally and transmitted
  to the selected NTRIP caster for authentication.
- App info and performance: local recording status and error messages are shown
  in the app and session sidecars; no analytics SDK is present.

Security practice declarations:

- Encryption in transit: the Google Play variant permits NTRIP only over TLS
  with Android system trust and hostname verification. Plaintext and unsafe TLS
  are sideload-only compatibility options, not Play distribution behavior.
  Verify the exact shipped variant before making Play Console claims.
- Data deletion: users can delete local sessions and archives in the app.
- No advertising or analytics SDKs are included.

## Privacy Policy

The Play listing must link to the published version of
`../PRIVACY.md`.

## Permission Rationale

- `INTERNET`: connects to user-configured NTRIP casters.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` and
  `FOREGROUND_SERVICE_DATA_SYNC`: keep user-started recording and correction
  routing active in the background while the app talks to an attached GNSS
  receiver and updates recording/session state.
- `WAKE_LOCK`: keeps capture running while the screen is off.
- `POST_NOTIFICATIONS`: required on Android 13+ before starting recording when
  notification permission is still missing, so Android can show the foreground
  recording notification.
- USB host feature: communicates with attached GNSS receivers after Android USB
  permission is granted.

## Foreground Service Declaration

The foreground service is user initiated by pressing Start. It records receiver
data and may route NTRIP corrections to an attached receiver while the app is in
the background. The release manifest uses `connectedDevice|dataSync` because the
service maintains a live connection to the receiver and keeps recording/correction
routing session state active. On Android 13+, the app requests notification
permission before recording when needed and does not start recording after a
denial. The notification must show recording state, receiver RX bytes and NTRIP
correction bytes.

## Release Validation

Before upload:

1. Run `git diff --check`.
2. Run both `:app:compileGooglePlayDebugKotlin` and
   `:app:compileSideloadDebugKotlin` sequentially on a supported host.
3. Run both flavor unit-test suites on a host where Android Gradle plugin
   native tools run.
4. Build a signed Google Play AAB with `:app:bundleGooglePlayRelease` on
   Windows Android Studio or CI; never submit a sideload bundle.
5. Install the release build on at least one Android 13+ device and verify
   notification permission flow before Play upload.
6. Verify a plain rover recording, rover + NTRIP recording, session ZIP share
   and settings backup import/export. Include real TLS correction intake and
   TLS source upload with compatible casters.
7. Confirm `third-party-licenses.md`, `../PRIVACY.md`, `../SECURITY.md` and
   Play Data safety answers match the shipped build.
