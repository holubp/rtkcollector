# Google Play Readiness Execution Design

## Goal

Make the existing Google Play readiness plan executable against the current
RtkCollector repository state without weakening the GNSS recording architecture
or confusing Play publication requirements with optional product improvements.

## Context Reviewed

The historical implementation plan is
`docs/superpowers/plans/2026-06-14-google-play-readiness.md`. It remains the
primary execution plan, but it predates later work on NTRIP source upload,
release-native-library checks, device/profile filtering and the formal
specification tree.

Current repository evidence before execution:

- `PRIVACY.md` is absent.
- `docs/play-publication.md` is absent.
- `app/src/main/AndroidManifest.xml` has `allowBackup="false"` and no main
  `ACCESS_MOCK_LOCATION` permission.
- `ACCESS_MOCK_LOCATION` is debug-only in `app/src/debug/AndroidManifest.xml`.
- The main manifest does not yet declare `POST_NOTIFICATIONS`.
- The app targets SDK 36.
- Release Gradle tasks already guard against silently omitting RTKLIB native
  libraries from Google Play release builds.
- NTRIP source upload v1/v2 is now implemented separately from rover correction
  download and must not be regressed by Play-readiness work.
- Third-party licence documentation is still too sparse for release review.

## Execution Principles

Google Play readiness must be treated as a publication and compliance gate, not
as an opportunity for unrelated feature work.

The execution must preserve:

- byte-exact raw receiver recording;
- foreground-service-owned recording;
- parser/UI/NTRIP/upload/advisory failure isolation;
- session secret redaction;
- debug-only mock-location permission;
- no maps, GIS editing, shapefiles or field-feature collection.

## Publication Gates

### Gate 1: Internal Or Closed Testing

Internal or closed testing can proceed only when:

- privacy policy and Play disclosure source documents exist;
- Play Data safety answers are truthful for the actual shipped build;
- notification permission and foreground-service behaviour are reviewable on
  Android 13+;
- release builds include required native libraries;
- release notes identify the app as experimental GNSS receiver software;
- no known release-lint fatal issue remains in the main manifest.

NTRIP cleartext transport is not, by itself, a Play blocker if it is truthfully
disclosed and the app does not claim universal encryption in transit.

### Gate 2: Production

Production publication additionally needs:

- full release AAB build on Windows Android Studio or CI;
- manual install of the signed release build;
- foreground notification permission denial/acceptance smoke tests;
- plain rover and rover + NTRIP field smoke tests;
- settings import/export smoke tests;
- session share/export smoke tests;
- review of third-party licence and RTKLIB source-offer obligations.

## NTRIP Transport Decision

Google Play Data safety asks whether data is encrypted in transit. The correct
requirement is truthful disclosure.

Execution may choose either:

- implement an explicit TLS transport option for NTRIP correction download and
  claim encryption only for selected TLS profiles; or
- keep cleartext-only NTRIP correction download for the first testing release
  and explicitly answer that data is not universally encrypted in transit.

The plan's TLS task is still useful, but it is not a prerequisite for internal
or closed testing when the disclosures are explicit. It must not touch or
regress the already separate NTRIP source-upload implementation.

## Plan Drift Corrections

When executing the historical plan:

- use the current split profile/settings files rather than assuming every model
  lives in one old file;
- keep existing NTRIP caster upload source/client separation intact;
- treat existing release-native-library Gradle checks as code to verify, not as
  missing work to duplicate;
- keep mock-location permission only in debug;
- update `docs/specification/publication-readiness.md` and
  `docs/specification/verification-matrix.md` as tasks are completed;
- keep `docs/superpowers/plan-status.md` as `Open` until code/docs and release
  validation evidence exist.

## Required Execution Outputs

The implementation pass must produce:

- `PRIVACY.md`;
- `docs/play-publication.md`;
- updated `SECURITY.md`, `README.md`, `docs/user-workflows.md`,
  `docs/ntrip-and-corrections.md`, `docs/android-background-operation.md`,
  `docs/session-format.md` and `docs/third-party-licenses.md`;
- notification-permission handling or a documented reason it is not required;
- settings backup cache cleanup and tests;
- manifest/import-surface review and tests;
- release validation commands and manual Android smoke-test checklist.

## Verification

Before claiming the plan is complete:

- run `git diff --check`;
- run `sh gradlew :app:compileDebugKotlin` in this Termux environment;
- run app/core targeted tests for touched pure Kotlin logic where the local
  environment supports them;
- run `sh gradlew :app:unitTestClasses :app:androidTestClasses --dry-run` to
  preserve Android Studio task-selection compatibility;
- perform full release AAB build and Android 13+ notification smoke tests on
  Windows Android Studio, CI or another host with working Android SDK native
  tools.
