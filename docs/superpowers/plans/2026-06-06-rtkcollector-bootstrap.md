# RtkCollector Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Populate the near-empty RtkCollector repository with specification documents, repository hygiene, a compiling Kotlin skeleton and initial unit tests.

**Architecture:** Use pure Kotlin/JVM modules for the bootstrap because no Android SDK is configured locally. Preserve Android-facing module names and a minimal `app` placeholder so a future Android Gradle conversion can keep package and module boundaries.

**Tech Stack:** Gradle 9.5.1, Kotlin JVM plugin, Kotlin standard library, Kotlin test.

---

### Task 1: Specification Documents

**Files:**
- Modify: `README.md`
- Create: `NOTICE`
- Create: `CONTRIBUTING.md`
- Create: `SECURITY.md`
- Create: `docs/*.md`

- [x] Write product requirements, architecture, receiver driver API, receiver profiles, session format, Android background operation, NTRIP/correction, base calibration, supported receiver, testing and third-party licence documents.
- [x] Ensure all documents state maps, shapefiles and GIS editing are out of scope.

### Task 2: Kotlin Gradle Skeleton

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: per-module `build.gradle.kts`
- Create: Kotlin source files under `app/`, `core/` and `receiver/`

- [x] Configure Kotlin/JVM modules.
- [x] Add an `assembleDebug` alias to the root project for Android-like local validation.
- [x] Keep receiver commands as explicit byte arrays and avoid risky hard-coded operational commands.

### Task 3: Tests

**Files:**
- Create: `receiver/api/src/test/kotlin/...`
- Create: `receiver/generic-nmea-rtcm/src/test/kotlin/...`
- Create: `core/session/src/test/kotlin/...`

- [x] Add minimal tests for receiver API data models.
- [x] Add minimal tests for basic GGA fix-quality parser behaviour.
- [x] Add minimal tests for session metadata and base position models.

### Task 4: Repository Hygiene

**Files:**
- Modify: `.gitignore`
- Create: `.github/workflows/android.yml`
- Create: `.github/ISSUE_TEMPLATE/*.yml`
- Create: `.github/pull_request_template.md`
- Create: `testdata/README.md`

- [x] Add CI commands for Gradle clean, assembleDebug and test.
- [x] Add issue and PR templates that ask about capture, background operation, receiver commands and session compatibility.

### Task 5: Validation And Commit

- [x] Run `./gradlew clean`; on Android shared storage this returned
  `Permission denied`, so run the equivalent `sh ./gradlew clean`.
- [x] Run `sh ./gradlew assembleDebug`.
- [x] Run `sh ./gradlew test`.
- [ ] Review staged files.
- [ ] Commit with subject `Bootstrap RtkCollector architecture and receiver driver skeleton`.
- [ ] Push `codex/bootstrap-rtkcollector`.
