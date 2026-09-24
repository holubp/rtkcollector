# Google Play Release Runbook

This is the operational runbook for publishing RtkCollector through Google
Play. It complements, rather than replaces, the release source documents:

- [docs/play-publication.md](docs/play-publication.md) is the app-specific
  declaration checklist.
- [PRIVACY.md](PRIVACY.md) is the privacy-policy source text.
- [docs/specification/publication-readiness.md](docs/specification/publication-readiness.md)
  contains the normative publication requirements.

Use the current wording in Play Console as the final authority. Google changes
Console fields and policy requirements independently of this repository.

## Scope And Current Preconditions

RtkCollector is an external USB GNSS receiver application. It records receiver
data locally and can connect to user-configured NTRIP casters. It does not use
advertising, analytics, crash-reporting or an RtkCollector-operated backend.

Do **not** upload the debug APK attached to a GitHub release. Google Play
requires a release-signed Android App Bundle (`.aab`), not a debug-signed APK.

Before starting, resolve these repository-specific prerequisites:

1. Work from a clean, reviewed commit that is aligned with `origin/main`.
   Do not build from a dirty checkout or from a branch that is behind the
   intended release commit.
2. Set a new, monotonically increasing `versionCode` and the intended
   `versionName` in `app/build.gradle.kts`. Google Play will reject a bundle
   whose version code has already been uploaded for `org.rtkcollector.app`.
3. Tag and record the exact source commit that produced the bundle.
4. Review `PRIVACY.md`, `SECURITY.md`, `docs/play-publication.md`, and
   `docs/third-party-licenses.md` against that exact build.
5. Before an upload, publish a privacy-policy web page at a public, active,
   non-geo-fenced, non-PDF URL. It must identify the app or the Play developer
   entity, include a privacy contact method, and be reachable without login.
   The repository `PRIVACY.md` is source text, not by itself a Play-ready URL.
6. Ensure the app itself exposes the same privacy policy by link or text. This
   is a separate Play requirement; do not assume that a store-listing link is
   sufficient.

### NTRIP Transport Gate

The Google Play variant enforces TLS with Android system trust and hostname
verification for correction, sourcetable and source-upload routes. Explicit
plaintext and acknowledged unsafe TLS remain available only in sideload builds
for compatibility with older or private casters. A TLS handshake failure must
never fall back to plaintext. Do not submit until the exact Play build has
passed full-host flavor tests, real TLS correction and source-upload checks,
signed-AAB validation and Data safety review. A Data safety disclosure cannot
make insecure transmission acceptable.

## 1. Create And Verify The Developer Account

1. Go to [Play Console](https://play.google.com/console/) using the Google
   account that should own RtkCollector.
2. Accept the Developer Distribution Agreement and Play Console terms.
3. Pay the one-time USD 25 registration fee.
4. Select the account type deliberately:
   - Choose **Personal** for an individual author/hobbyist account.
   - Choose **Organization** only when publishing for an organization or
     business. Google requires organization verification and normally a D-U-N-S
     number.
5. Complete the requested identity, contact, and payments-profile verification.
   Use contact information that will remain controlled by the account owner.
   For a personal account, complete Play Console device verification when
   prompted using the account owner's eligible physical Android device. For an
   organization, complete the requested website, identity, organization and
   payments-profile verification using information that matches the legal
   entity; obtain a D-U-N-S number when the Console requires one.
6. Enable strong security on the Google account, including two-step
   verification, and keep account-owner recovery information current.
7. In Play Console, add only trusted users with the minimum role necessary.
   Keep account ownership separate from routine release work where practical.

Account type is not a cosmetic choice. A personal account created after
2023-11-13 must complete Play's closed-testing requirement before production
access is enabled.

## 2. Prepare A Reproducible Windows Release Build

Use a Windows machine with a current Android Studio installation. Install the
Android SDK components requested by the project, plus an Android NDK and CMake
through **Tools > SDK Manager**. Install Git for Windows and Python 3, and use
Android Studio's bundled JDK or set `JAVA_HOME` to a supported JDK. The release
build intentionally fails if it cannot package the RTKLIB native library.

1. Clone or update the repository and switch to the reviewed release commit.
2. Confirm the checkout is clean:

   ```powershell
   git status --short --branch
   git rev-parse HEAD
   ```

3. Provision the exact RTKLIB-EX commit recorded by the tracked snapshot into
   the ignored local checkout. Keep generated snapshot metadata outside the
   repository so this preparation cannot dirty the release source tree:

   ```powershell
   $snapshot = Get-Content third_party/rtklib-ex/snapshot.json | ConvertFrom-Json
   $metadata = Join-Path $env:TEMP 'rtkcollector-rtklib-ex-snapshot.json'
   python tools/update_rtklib_ex.py --ref $snapshot.resolvedCommit `
     --destination third_party/rtklib-ex/upstream --metadata $metadata
   git status --short
   ```

   Do not substitute an RTKLIB branch name or a newer upstream HEAD. The final
   `git status --short` must remain empty before the release metadata is added.

4. Run the mandatory repository gate before building the release artifact. In
   Git Bash from Git for Windows, run:

   ```sh
   sh scripts/pre_push_check.sh
   ```

   This is mandatory, not a substituteable compilation preview. It checks the
   formal specification gate and compiles every Android test source set,
   including Android Studio compatibility task aliases. Retain the complete log
   with the release record. Resolve a host-tool failure on a compatible Windows
   host rather than treating it as a passing source check.

5. Confirm the release identity before continuing:

   ```powershell
   .\gradlew.bat :app:signingReport
   ```

   Record the upload-key certificate fingerprint. Verify
   `applicationId = "org.rtkcollector.app"` in `app/build.gradle.kts`; the
   signed AAB manifest is checked in Step 4.

## 3. Create And Protect The Upload Key

Google Play App Signing is required for a new Play app. Google then signs
the APKs delivered to users, while the local build uses a separate **upload
key** to authenticate uploads.

1. In Android Studio select **Build > Generate Signed Bundle / APK**.
2. Select **Android App Bundle**.
3. Create a new keystore if an upload key does not already exist. Use a strong,
   unique keystore password and key password, a stable alias, and a long key
   validity period.
4. Store the keystore and its recovery information outside the repository, in
   encrypted storage with an independently recoverable backup. Store passwords
   in a password manager. Never commit a `.jks`, `.keystore`, passwords,
   `key.properties`, or signing configuration containing secrets.
5. Keep the upload key separate from any GitHub/debug signing material. A lost
   upload key can be reset through Play Console; careless key handling still
   delays releases.

Avoid adding signing secrets directly to `app/build.gradle.kts`. The Android
Studio signing wizard can produce a one-off signed bundle without committing
private key material into the project.

## 4. Build And Verify The Signed AAB

1. In the same Android Studio wizard, select the `googlePlayRelease` build
   variant, enter the upload-key details, and generate the signed bundle.
2. Record the resulting file, normally:

   ```text
   app/build/outputs/bundle/googlePlayRelease/app-googlePlay-release.aab
   ```

3. Run the project release-bundle check **before signing** as a build-input and
   native-library preflight:

   ```powershell
   .\gradlew.bat validateGooglePlayReleaseBundle
   ```

   This task may build an unsigned Google Play release AAB. It does **not** verify
   the upload-key-signed artifact from the Android Studio wizard.
4. Verify the exact signed AAB recorded in Step 2. With a trusted local
   `bundletool` JAR, run the following against that exact path, then retain the
   output:

   ```powershell
   $bundle = Resolve-Path app/build/outputs/bundle/googlePlayRelease/app-googlePlay-release.aab
   jarsigner -verify -verbose -certs $bundle
   java -jar <path-to-bundletool.jar> validate --bundle=$bundle
   java -jar <path-to-bundletool.jar> dump manifest --bundle=$bundle
   ```

   Confirm the manifest package is `org.rtkcollector.app`. Generate and install
   an APK set from this same signed AAB, or use Internal testing, for device
   testing. Do not replace the signed AAB between verification and upload.
5. Verify 16 KB page-size compatibility in the final native artifacts and run a
   release APK generated from the signed AAB on an Android 15+ 16 KB-page-size
   device or emulator. Exercise an RTKLIB-enabled route, not only app startup.
   The CMake configuration and the presence of `.so` files are not sufficient
   evidence of final-package compatibility.
6. Test the generated release APKs on at least one Android 13+ physical device
   and one Android 15+ physical device. The required manual checks are:
   - deny and allow notification permission, confirming that recording never
     silently continues without a foreground notification;
   - plain rover recording with a supported external USB receiver;
   - rover recording with NTRIP corrections;
   - session archive sharing;
   - settings backup export/import, including the default no-password export;
   - background/screen-off recording and reconnect behaviour;
   - a controlled long-running `dataSync` foreground-service test, including
     the Android 15+ timeout/interruption path. Do not publish until the app
     handles the applicable timeout safely and preserves raw recording data.
7. Preserve the AAB's SHA-256, source commit, version code, version name,
   signing certificate fingerprint, test devices, and test outcome in the
   release record.

An AAB cannot be installed directly. Install an APK set generated from it, or
use the Internal testing track, to test the same Play-delivery path.

## 5. Create The Play App

1. In Play Console choose **All apps > Create app**.
2. Enter the public app name, default language, app/game type, free/paid
   setting, and required declarations. Start as **free** unless a paid-business
   decision has been made; a free app cannot later be changed to paid.
3. Upload the verified signed AAB to **Internal testing** first. Complete the
   required Play App Signing enrollment and retain both upload and app-signing
   certificate fingerprints.
4. Complete the store listing with truthful material:
   - concise and full descriptions that say RtkCollector requires a compatible
     external USB GNSS receiver;
   - screenshots made from the actual shipped build, showing the real
     receiver/NTRIP workflow rather than mock data;
   - app icon, feature graphic, category, contact email, and support URL;
   - the public privacy-policy URL from the preconditions above.
5. Do not promise survey-grade accuracy, universal receiver compatibility,
   encryption for sideload compatibility modes, or phone-internal-GNSS support.
   The listing must match the actual release build and hardware limitations.

## 6. Complete App Content And Data Safety

Use `docs/play-publication.md` as the source inventory, then re-check every
answer against the uploaded AAB and its dependencies. Do not reuse answers from
an older release without review.

For this app, explicitly review these facts in the current Data safety form:

| App behaviour | Required review point |
| --- | --- |
| Receiver-derived precise position | Stored locally in session artifacts; optionally transmitted to a user-selected NTRIP caster as GGA for VRS. This is off-device collection when enabled. |
| NTRIP username and password | Stored locally; transmitted to the selected caster for authentication. This is off-device collection when enabled. |
| USB receiver identifiers and configuration | Stored locally in settings/session metadata for functionality. |
| Session ZIPs and settings backups | Shared only when the user explicitly invokes Android sharing. Plaintext-password export is opt-in. |
| Analytics, ads, crash reporting | None are intentionally included; re-check the final dependency graph. |
| Network security | Google Play routes require TLS with system trust and hostname verification; sideload-only plaintext/unsafe options are excluded. Real caster and shipped-bundle validation remain publication gates. |

Treat transmission to an external NTRIP provider as **collection** in Data
Safety whenever the user enables it, including when the provider is a third
party. Mark the collection optional only if NTRIP/GGA is genuinely opt-in.
Assess the separate sharing question using the current Console definitions and
the actual caster relationship; a user-initiated transfer may affect that
answer, but never removes the collection disclosure. Keep the answer consistent
with the privacy policy and the exact shipped transport.

Complete all other App content declarations truthfully:

1. **App access:** there is no RtkCollector account login. Explain that a
   compatible USB receiver and optional NTRIP credentials are needed for
   hardware-specific flows. Give reviewers a reusable non-production test
   endpoint and credentials, or a genuinely accessible public test stream, for
   every authenticated flow needed to review the release. Include concise setup
   steps and a short video; do not provide private or production credentials.
2. **Ads:** declare no ads, unless the shipped build changes.
3. **Content rating:** complete the questionnaire based on actual app content.
4. **Target audience and content:** select the real intended audience; do not
   market the app to children.
5. **Permissions and foreground services:** confirm the release manifest only
   requests `INTERNET`, notification, wake-lock, and
   `connectedDevice|dataSync` foreground-service capabilities described in
   `docs/play-publication.md`. Complete the required foreground-service
   declaration for both types before review: declared type, user-facing core
   use case, why it cannot be deferred or interrupted, and a short video showing
   user-initiated USB recording with its persistent foreground notification.

## 7. Test Through Google Play

1. Use Internal testing for the first installation and smoke test. It is fast,
   but it does not satisfy the production-access requirement for new personal
   accounts.
2. Create a Closed testing track after app setup is complete. Add testers via
   an email list or Google Group and give them the opt-in link.
3. For a personal developer account created after 2023-11-13, retain at least
   **12 testers continuously opted in for 14 consecutive days** before applying
   for production access. A tester who opts out and opts in again restarts their
   own continuous period.
4. Ask testers to use their own compatible hardware where possible and capture
   structured feedback: Android/device version, receiver model, USB adapter,
   workflow, NTRIP behaviour, recording start/stop, session sharing, and any
   crash or raw-data concern.
5. Monitor the Play pre-launch report, Android vitals, policy messages, and
   tester feedback. Fix confirmed issues in a new version code and re-run the
   complete release process.
6. When eligible, select **Apply for production access** in Play Console and
   answer the questions about testing, app design, and production readiness
   honestly.

## 8. Production Rollout And Every Update

1. Create a Production release only after production access, required App
   content, Data safety, privacy policy, listing, and testing are all complete.
2. Promote the same verified AAB that completed testing. Upload a new version
   code only when the artifact changes; then repeat the complete validation and
   testing path for that new artifact. Prepare concise release notes.
3. For the first production release, select the intended countries/regions and
   complete the initial production rollout. Use staged rollout controls for
   later production updates, then watch Android vitals and incoming reports
   before increasing reach.
4. For every update, repeat the release validation, native-library check,
   privacy/Data safety review, permission/foreground-service review, and
   release-device smoke tests. Changing an NTRIP transport, a data export path,
   a dependency, a permission, or an external service can change Play answers.

## Release Record Template

Keep this alongside the release tag or in the release notes:

```text
Source commit:
Git tag:
versionName / versionCode:
AAB SHA-256:
Upload certificate SHA-256:
RTKLIB-EX snapshot commit:
Android Studio / AGP / NDK versions:
Repository gate log:
Signed-AAB validation and manifest check:
16 KB native-package and device test:
Devices and Android versions tested:
Manual workflow results:
Play track and rollout date:
Data safety / App content reviewer:
```

## Authoritative References

Reviewed 2026-09-23. Re-check these before a real upload:

- [Create and set up an app](https://support.google.com/googleplay/android-developer/answer/9859152)
- [Account types and verification](https://support.google.com/googleplay/android-developer/answer/13634885)
- [Testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Prepare an app for review](https://support.google.com/googleplay/android-developer/answer/9859455)
- [Google Play User Data policy and Data safety](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Complete the Data safety form](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Declare app permissions](https://support.google.com/googleplay/android-developer/answer/9214102)
- [Foreground service declarations and review material](https://support.google.com/googleplay/android-developer/answer/13392821)
- [Android App Signing](https://developer.android.com/studio/publish/app-signing)
- [Build an Android App Bundle](https://developer.android.com/build/build-for-release)
- [Foreground service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout)
- [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)
- [Staged production rollouts](https://support.google.com/googleplay/android-developer/answer/6346149)
