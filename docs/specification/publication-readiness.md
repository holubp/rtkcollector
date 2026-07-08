# Publication Readiness Requirements

This file contains requirements for Google Play and similar public Android
release channels. These requirements complement the runtime, security and
workflow specifications; they do not weaken recording architecture rules.

## Publication Disclosure

### PLAY-DISCLOSURE-001: Privacy Policy And Play Checklist Are Release Sources

Status: Normative

Every Google Play release MUST have repository-tracked privacy and publication
checklist sources. The Play listing, Data safety answers, foreground-service
declarations and permission declarations MUST be derived from those sources and
reviewed against the shipped APK/AAB.

Rationale:
Publication statements must match the actual app build, not stale planning
documents.

Applies to:
- Google Play listing and release preparation.
- `PRIVACY.md`.
- `docs/play-publication.md`.

Verification:
- Review: privacy policy and Play checklist exist and match current code.
- Manual: compare Play Console answers with `docs/play-publication.md` before
  upload.

Traceability:
- Source: `docs/superpowers/plans/2026-06-14-google-play-readiness.md`.
- Source: `docs/superpowers/specs/2026-07-08-google-play-readiness-execution-design.md`.

### PLAY-DATA-001: Data Safety Claims Must Be Truthful Per Build

Status: Normative

The app MUST NOT claim universal encryption in transit while any shipped
enabled workflow sends NTRIP credentials, GGA positions, corrections or source
upload data over ordinary cleartext TCP. If TLS support exists, Play Data
safety wording MUST distinguish TLS-capable profiles from cleartext profiles.

Rationale:
NTRIP deployments commonly use cleartext TCP, and false encryption claims would
be a publication compliance failure.

Applies to:
- NTRIP correction download.
- NTRIP source upload.
- Play Data safety answers.

Verification:
- Review: NTRIP transport code and profile options match the disclosure.
- Manual: Play Data safety answers do not overclaim encryption for cleartext
  profiles.

Traceability:
- Source: `docs/ntrip-and-corrections.md`.
- Source: `docs/superpowers/specs/2026-07-08-google-play-readiness-execution-design.md`.

## Permissions And Manifest

### PLAY-PERM-001: Release Manifest Uses Only Release-Justified Permissions

Status: Normative

The main release manifest MUST contain only permissions needed by shipped
release functionality and documented in the publication checklist. Debug-only
permissions such as `ACCESS_MOCK_LOCATION` MUST remain outside the main
manifest.

Rationale:
Google Play lint and review reject or flag permissions that are inappropriate
for release builds.

Applies to:
- `app/src/main/AndroidManifest.xml`.
- `app/src/debug/AndroidManifest.xml`.
- `docs/play-publication.md`.

Verification:
- Automated: release lint or manifest tests where practical.
- Review: permission list matches documented rationale.
- Manual: Google Play pre-launch/report checks.

Traceability:
- Source: `docs/superpowers/plans/2026-06-14-google-play-readiness.md`.

### PLAY-NOTIFICATION-001: Recording Must Not Silently Start Without Visible Notification

Status: Normative

On Android versions that require runtime notification permission for foreground
service notifications, the app MUST handle notification permission before or
during recording start so that recording does not silently run without a visible
foreground-service notification. Permission denial MUST produce a user-visible
blocked or degraded start state rather than an invisible recording.

Rationale:
The recording service is long-running field software and its foreground
notification is part of Android background-operation safety.

Applies to:
- Recording start UI.
- `RecordingForegroundService`.
- Android 13+ notification permission flow.

Verification:
- Automated: permission policy tests where practical.
- Manual: deny notification permission on Android 13+ and confirm recording
  does not silently start without a foreground notification.

Traceability:
- Source: `docs/android-background-operation.md`.
- Source: `docs/superpowers/plans/2026-06-14-google-play-readiness.md`.

### PLAY-FGS-001: Foreground Service Type And Rationale Are Publication-Ready

Status: Normative

Every foreground service type used by the release manifest MUST have a
Play-facing rationale that describes the user-initiated recording or correction
routing work it supports. The app MUST NOT declare foreground service types
that are not used by release behaviour.

Rationale:
Foreground service declarations are reviewed by Android and Google Play.

Applies to:
- `app/src/main/AndroidManifest.xml`.
- Recording service declaration.
- `docs/play-publication.md`.

Verification:
- Review: manifest service types match service behaviour and publication
  rationale.
- Manual: release checklist review before Play upload.

Traceability:
- Source: `docs/superpowers/plans/2026-06-14-google-play-readiness.md`.

## Secrets, Imports And Sharing

### PLAY-SECRETS-001: Settings Backup Sharing Handles Plaintext Password Risk

Status: Normative

Settings backup export MUST exclude plaintext passwords by default. If the user
explicitly includes plaintext passwords, the filename and UI MUST make that
risk visible, and temporary cache files used for sharing MUST be removed on a
best-effort basis after sharing.

Rationale:
Settings backups can contain operational credentials and are often shared
through third-party Android targets.

Applies to:
- Settings backup export.
- FileProvider cache.
- Security and user workflow documentation.

Verification:
- Automated: settings backup model and share-policy tests.
- Manual: export settings with and without plaintext passwords and inspect
  shared filenames/cache behaviour.

Traceability:
- Source: `docs/superpowers/plans/2026-06-14-google-play-readiness.md`.
- Source: `docs/superpowers/specs/2026-06-12-android-json-settings-import-design.md`.

### PLAY-IMPORT-001: External JSON Import Remains Explicit And Validated

Status: Normative

External JSON intents MUST NOT auto-import settings. The app MUST accept
settings import through Android content/share intents, not broad browsable
`file:` view intents. The app MUST validate the settings backup structure, show
a confirmation summary and warn about plaintext passwords before writing
profiles or secrets.

Rationale:
External files are untrusted input and can arrive from broad Android share
targets.

Applies to:
- Settings import intent handling.
- Settings import confirmation UI.

Verification:
- Automated: import intent reader, manifest and validation tests.
- Manual: open valid and invalid JSON backups from Android Files/share targets.

Traceability:
- Source: `docs/superpowers/specs/2026-06-12-android-json-settings-import-design.md`.
- Source: `docs/superpowers/plans/2026-06-14-google-play-readiness.md`.

## Release Build And Licences

### PLAY-BUILD-001: Release Build Must Include Required Native Libraries

Status: Normative

Google Play release AAB/APK builds MUST fail rather than silently omitting
required native libraries such as the RTKLIB native backend when a workflow or
shipped feature depends on them.

Rationale:
Google Play builds must not differ from tested local functionality by missing
native backends.

Applies to:
- Gradle release tasks.
- RTKLIB native build integration.

Verification:
- Automated: release build input validation and AAB library-entry checks.
- Manual: full release AAB build on Windows Android Studio or CI.

Traceability:
- Source: `app/build.gradle.kts`.
- Source: `docs/superpowers/specs/2026-07-08-google-play-readiness-execution-design.md`.

### PLAY-LICENSE-001: Third-Party Licence Documentation Matches Release Dependencies

Status: Normative

Before every Play release, third-party licence documentation MUST be reviewed
against the release dependency graph and native source bundle obligations.
Runtime dependencies, test/build dependencies relevant to source distribution,
and RTKLIB-EX licence/source-offer obligations MUST be documented.

Rationale:
RtkCollector is GPL-3.0-or-later and release artifacts include third-party
Android and native components.

Applies to:
- `docs/third-party-licenses.md`.
- Release checklist.

Verification:
- Automated: Gradle dependency report captured on release host where practical.
- Review: licence document covers current release dependency graph.
- Manual: release checklist confirms licence review.

Traceability:
- Source: `docs/superpowers/plans/2026-06-14-google-play-readiness.md`.
