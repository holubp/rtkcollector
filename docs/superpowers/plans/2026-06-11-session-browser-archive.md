# Session Browser Archive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a storage-backed Sessions menu with grouped multi-selection, temporary share ZIPs, permanent archive ZIPs, restore from archive and safe deletion.

**Architecture:** Add JVM-testable session browser models and filesystem operations, then wire the Compose Sessions screen to those models. Keep raw recording immutable except for confirmed deletion of completed sessions. Treat SAF through the same model, with filesystem support as the first complete implementation.

**Tech Stack:** Kotlin, Compose Material3, `java.nio.file.Path`, `ZipOutputStream`, JUnit 5.

---

## File Structure

- Create `app/src/main/kotlin/org/rtkcollector/app/sessions/SessionBrowserModels.kt`: session entry, group, selection and operation result models.
- Create `app/src/main/kotlin/org/rtkcollector/app/sessions/FilesystemSessionBrowser.kt`: app-private filesystem discovery, latest-first sorting and delete support.
- Create `app/src/main/kotlin/org/rtkcollector/app/sessions/SessionArchiveManager.kt`: share ZIP planning, permanent archive, restore and verification.
- Create `app/src/test/kotlin/org/rtkcollector/app/sessions/SessionBrowserModelsTest.kt`: grouping and selection tests.
- Create `app/src/test/kotlin/org/rtkcollector/app/sessions/FilesystemSessionBrowserTest.kt`: temp-directory discovery and delete tests.
- Create `app/src/test/kotlin/org/rtkcollector/app/sessions/SessionArchiveManagerTest.kt`: share ZIP, archive and restore tests.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionBrowserModels.kt`: replace the minimal UI-only model or keep it as a UI facade backed by the new app session models.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionsScreen.kt`: grouped list, checkbox selection, bulk actions, progress and confirmations.
- Modify `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`: load sessions from configured storage, perform share/archive/restore/delete actions and share multiple temp ZIPs.

## Task 1: Session Browser Domain Models

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/sessions/SessionBrowserModels.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/sessions/SessionBrowserModelsTest.kt`

- [ ] **Step 1: Write failing model tests**

Create `SessionBrowserModelsTest.kt` with tests for grouping and selection:

```kotlin
package org.rtkcollector.app.sessions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionBrowserModelsTest {
    private val current = SessionBrowserEntry(
        id = "current",
        displayName = "Current",
        location = "current",
        modifiedAt = Instant.parse("2026-06-11T10:00:00Z"),
        kind = SessionEntryKind.CURRENT_ACTIVE,
    )
    private val normal = SessionBrowserEntry(
        id = "normal",
        displayName = "Normal",
        location = "normal",
        modifiedAt = Instant.parse("2026-06-11T09:00:00Z"),
        kind = SessionEntryKind.RECORDING,
    )
    private val archive = SessionBrowserEntry(
        id = "archive",
        displayName = "Archive",
        location = "archive.zip",
        modifiedAt = Instant.parse("2026-06-11T08:00:00Z"),
        kind = SessionEntryKind.ARCHIVE,
    )

    @Test
    fun `groups sessions by current normal and archived`() {
        val groups = SessionBrowserState.fromEntries(listOf(archive, normal, current)).groups

        assertEquals(SessionBrowserGroupKind.CURRENT, groups[0].kind)
        assertEquals(listOf(current), groups[0].entries)
        assertEquals(SessionBrowserGroupKind.RECORDINGS, groups[1].kind)
        assertEquals(listOf(normal), groups[1].entries)
        assertEquals(SessionBrowserGroupKind.ARCHIVES, groups[2].kind)
        assertEquals(listOf(archive), groups[2].entries)
    }

    @Test
    fun `select helpers choose current recordings archives and all`() {
        val state = SessionBrowserState.fromEntries(listOf(archive, normal, current))

        assertEquals(setOf("current"), state.selectCurrent().selectedIds)
        assertEquals(setOf("normal"), state.selectRecordings().selectedIds)
        assertEquals(setOf("archive"), state.selectArchives().selectedIds)
        assertEquals(setOf("current", "normal", "archive"), state.selectAll().selectedIds)
        assertEquals(emptySet<String>(), state.selectAll().clearSelection().selectedIds)
    }

    @Test
    fun `active current session is not eligible for destructive operations`() {
        assertFalse(current.canShareZip)
        assertFalse(current.canArchive)
        assertFalse(current.canDelete)
        assertTrue(normal.canShareZip)
        assertTrue(normal.canArchive)
        assertTrue(normal.canDelete)
        assertTrue(archive.canRestore)
        assertTrue(archive.canDelete)
    }
}
```

- [ ] **Step 2: Run model tests and verify RED**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.sessions.SessionBrowserModelsTest
```

Expected: fails because `org.rtkcollector.app.sessions` models do not exist. If local Termux AAPT2 blocks app unit tests before Kotlin compilation, record that limitation and use `:app:compileDebugKotlin` after implementation for compile verification.

- [ ] **Step 3: Implement domain models**

Create `SessionBrowserModels.kt`:

```kotlin
package org.rtkcollector.app.sessions

import java.time.Instant

enum class SessionEntryKind {
    CURRENT_ACTIVE,
    CURRENT_STOPPED,
    RECORDING,
    ARCHIVE,
}

enum class SessionBrowserGroupKind(val title: String) {
    CURRENT("Current session"),
    RECORDINGS("Recordings"),
    ARCHIVES("Archived recordings"),
}

data class SessionBrowserEntry(
    val id: String,
    val displayName: String,
    val location: String,
    val modifiedAt: Instant,
    val kind: SessionEntryKind,
    val sizeBytes: Long? = null,
) {
    val isActive: Boolean get() = kind == SessionEntryKind.CURRENT_ACTIVE
    val isArchive: Boolean get() = kind == SessionEntryKind.ARCHIVE
    val canShareZip: Boolean get() = kind == SessionEntryKind.RECORDING || kind == SessionEntryKind.CURRENT_STOPPED
    val canArchive: Boolean get() = kind == SessionEntryKind.RECORDING || kind == SessionEntryKind.CURRENT_STOPPED
    val canRestore: Boolean get() = kind == SessionEntryKind.ARCHIVE
    val canDelete: Boolean get() = !isActive
}

data class SessionBrowserGroup(
    val kind: SessionBrowserGroupKind,
    val entries: List<SessionBrowserEntry>,
)

data class SessionBrowserState(
    val groups: List<SessionBrowserGroup>,
    val selectedIds: Set<String> = emptySet(),
) {
    val entries: List<SessionBrowserEntry> get() = groups.flatMap(SessionBrowserGroup::entries)
    val selectedEntries: List<SessionBrowserEntry> get() = entries.filter { it.id in selectedIds }

    fun toggle(id: String): SessionBrowserState =
        copy(selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id)

    fun clearSelection(): SessionBrowserState = copy(selectedIds = emptySet())
    fun selectCurrent(): SessionBrowserState = selectKind(SessionBrowserGroupKind.CURRENT)
    fun selectRecordings(): SessionBrowserState = selectKind(SessionBrowserGroupKind.RECORDINGS)
    fun selectArchives(): SessionBrowserState = selectKind(SessionBrowserGroupKind.ARCHIVES)
    fun selectAll(): SessionBrowserState = copy(selectedIds = entries.map(SessionBrowserEntry::id).toSet())

    private fun selectKind(kind: SessionBrowserGroupKind): SessionBrowserState =
        copy(selectedIds = groups.firstOrNull { it.kind == kind }?.entries?.map(SessionBrowserEntry::id)?.toSet().orEmpty())

    companion object {
        fun fromEntries(entries: List<SessionBrowserEntry>, selectedIds: Set<String> = emptySet()): SessionBrowserState {
            val sorted = entries.sortedWith(compareByDescending<SessionBrowserEntry> { it.modifiedAt }.thenBy { it.displayName })
            val groups = listOfNotNull(
                sorted.filter { it.kind == SessionEntryKind.CURRENT_ACTIVE || it.kind == SessionEntryKind.CURRENT_STOPPED }
                    .takeIf(List<SessionBrowserEntry>::isNotEmpty)
                    ?.let { SessionBrowserGroup(SessionBrowserGroupKind.CURRENT, it) },
                sorted.filter { it.kind == SessionEntryKind.RECORDING }
                    .takeIf(List<SessionBrowserEntry>::isNotEmpty)
                    ?.let { SessionBrowserGroup(SessionBrowserGroupKind.RECORDINGS, it) },
                sorted.filter { it.kind == SessionEntryKind.ARCHIVE }
                    .takeIf(List<SessionBrowserEntry>::isNotEmpty)
                    ?.let { SessionBrowserGroup(SessionBrowserGroupKind.ARCHIVES, it) },
            )
            val validIds = sorted.map(SessionBrowserEntry::id).toSet()
            return SessionBrowserState(groups = groups, selectedIds = selectedIds.intersect(validIds))
        }
    }
}
```

- [ ] **Step 4: Verify model compile/test**

Run the same test command as Step 2. If blocked by AAPT2, run:

```bash
sh gradlew :app:compileDebugKotlin --rerun-tasks
```

Expected: Kotlin compile succeeds.

## Task 2: Filesystem Discovery And Deletion

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/sessions/FilesystemSessionBrowser.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/sessions/FilesystemSessionBrowserTest.kt`

- [ ] **Step 1: Write failing filesystem tests**

Create tests using temporary directories:

```kotlin
package org.rtkcollector.app.sessions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class FilesystemSessionBrowserTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `discovers sessions and archives latest first`() {
        val old = createSession("session-old", "2026-06-11T08:00:00Z")
        val latest = createSession("session-latest", "2026-06-11T10:00:00Z")
        val archive = Files.write(tempDir.resolve("session-archive.zip"), byteArrayOf(1, 2, 3))

        val entries = FilesystemSessionBrowser.discover(tempDir)

        assertEquals(listOf(latest.fileName.toString(), old.fileName.toString(), archive.fileName.toString()), entries.map { it.displayName })
        assertEquals(SessionEntryKind.RECORDING, entries[0].kind)
        assertEquals(SessionEntryKind.ARCHIVE, entries[2].kind)
    }

    @Test
    fun `delete recording removes whole session folder`() {
        val session = createSession("session-delete", "2026-06-11T09:00:00Z")

        FilesystemSessionBrowser.deleteRecording(session)

        assertFalse(Files.exists(session))
    }

    private fun createSession(name: String, startedAt: String): Path {
        val session = Files.createDirectories(tempDir.resolve(name))
        Files.writeString(session.resolve("session.json"), """{"startedAt":"$startedAt"}""")
        Files.write(session.resolve("receiver-rx.raw"), byteArrayOf(1))
        return session
    }
}
```

- [ ] **Step 2: Run filesystem tests and verify RED**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.sessions.FilesystemSessionBrowserTest
```

Expected: fails because `FilesystemSessionBrowser` does not exist, unless blocked by local AAPT2.

- [ ] **Step 3: Implement filesystem browser**

Create `FilesystemSessionBrowser.kt`:

```kotlin
package org.rtkcollector.app.sessions

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Comparator
import kotlin.io.path.name

object FilesystemSessionBrowser {
    fun discover(root: Path, currentLocation: String? = null, active: Boolean = false): List<SessionBrowserEntry> {
        if (!Files.isDirectory(root)) return emptyList()
        val currentPath = currentLocation?.let { runCatching { Path.of(it) }.getOrNull()?.toAbsolutePath()?.normalize() }
        return Files.list(root).use { stream ->
            stream
                .filter { Files.isDirectory(it) || it.fileName.toString().endsWith(".zip", ignoreCase = true) }
                .map { path -> entryFor(path, currentPath, active) }
                .filter { it.kind == SessionEntryKind.ARCHIVE || isSessionDirectory(Path.of(it.location)) }
                .sorted(Comparator.comparing(SessionBrowserEntry::modifiedAt).reversed())
                .toListCompat()
        }
    }

    fun deleteRecording(sessionDirectory: Path) {
        require(Files.isDirectory(sessionDirectory)) { "Recording delete target must be a directory." }
        Files.walk(sessionDirectory).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    fun deleteArchive(archive: Path) {
        require(archive.fileName.toString().endsWith(".zip", ignoreCase = true)) { "Archive delete target must be a ZIP." }
        Files.deleteIfExists(archive)
    }

    private fun entryFor(path: Path, currentPath: Path?, active: Boolean): SessionBrowserEntry {
        val normalized = path.toAbsolutePath().normalize()
        val isArchive = path.fileName.toString().endsWith(".zip", ignoreCase = true)
        val isCurrent = currentPath != null && normalized == currentPath
        val kind = when {
            isArchive -> SessionEntryKind.ARCHIVE
            isCurrent && active -> SessionEntryKind.CURRENT_ACTIVE
            isCurrent -> SessionEntryKind.CURRENT_STOPPED
            else -> SessionEntryKind.RECORDING
        }
        return SessionBrowserEntry(
            id = normalized.toString(),
            displayName = path.name,
            location = normalized.toString(),
            modifiedAt = sessionInstant(path) ?: Files.getLastModifiedTime(path).toInstant(),
            kind = kind,
            sizeBytes = if (Files.isRegularFile(path)) Files.size(path) else null,
        )
    }

    private fun isSessionDirectory(path: Path): Boolean =
        Files.isDirectory(path) && (Files.exists(path.resolve("session.json")) || Files.exists(path.resolve("receiver-rx.raw")))

    private fun sessionInstant(path: Path): Instant? =
        if (!Files.isDirectory(path)) {
            null
        } else {
            runCatching {
                val text = Files.readString(path.resolve("session.json"))
                Regex(""""(?:startedAt|startTime|startTimestamp)"\s*:\s*"([^"]+)"""")
                    .find(text)
                    ?.groupValues
                    ?.get(1)
                    ?.let(Instant::parse)
            }.getOrNull()
        }
}

private fun <T> java.util.stream.Stream<T>.toListCompat(): List<T> {
    val result = mutableListOf<T>()
    forEach(result::add)
    return result
}
```

- [ ] **Step 4: Verify filesystem behavior**

Run the filesystem test command from Step 2 or `sh gradlew :app:compileDebugKotlin --rerun-tasks` if local AAPT2 blocks app unit tests.

## Task 3: Archive, Restore And Share ZIP Plans

**Files:**
- Create: `app/src/main/kotlin/org/rtkcollector/app/sessions/SessionArchiveManager.kt`
- Test: `app/src/test/kotlin/org/rtkcollector/app/sessions/SessionArchiveManagerTest.kt`

- [ ] **Step 1: Write failing archive tests**

Create tests:

```kotlin
package org.rtkcollector.app.sessions

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SessionArchiveManagerTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `share zip is created in cache and keeps originals`() {
        val session = createSession("session-share")
        val cache = Files.createDirectories(tempDir.resolve("cache"))

        val zip = SessionArchiveManager.createTemporaryShareZip(session, cache)

        assertTrue(Files.exists(zip))
        assertTrue(Files.exists(session.resolve("receiver-rx.raw")))
        assertTrue(zip.startsWith(cache))
    }

    @Test
    fun `archive verifies zip and removes original session`() {
        val session = createSession("session-archive")

        val archive = SessionArchiveManager.archive(session, tempDir)

        assertTrue(Files.exists(archive))
        assertFalse(Files.exists(session))
    }

    @Test
    fun `restore extracts archive and removes zip`() {
        val session = createSession("session-restore")
        val archive = SessionArchiveManager.archive(session, tempDir)

        val restored = SessionArchiveManager.restore(archive, tempDir)

        assertTrue(Files.exists(restored.resolve("receiver-rx.raw")))
        assertFalse(Files.exists(archive))
    }

    private fun createSession(name: String): Path {
        val session = Files.createDirectories(tempDir.resolve(name))
        Files.writeString(session.resolve("session.json"), "{}")
        Files.write(session.resolve("receiver-rx.raw"), byteArrayOf(1, 2, 3))
        return session
    }
}
```

- [ ] **Step 2: Run archive tests and verify RED**

Run:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.sessions.SessionArchiveManagerTest
```

Expected: fails because `SessionArchiveManager` does not exist, unless local AAPT2 blocks before execution.

- [ ] **Step 3: Implement archive manager**

Create `SessionArchiveManager.kt`:

```kotlin
package org.rtkcollector.app.sessions

import org.rtkcollector.app.recording.SessionZipPlan
import org.rtkcollector.app.recording.SessionZipProgress
import org.rtkcollector.app.recording.SessionZipExporter
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.nameWithoutExtension

object SessionArchiveManager {
    fun createTemporaryShareZip(
        sessionDirectory: Path,
        cacheDirectory: Path,
        onProgress: (SessionZipProgress) -> Unit = {},
    ): Path {
        Files.createDirectories(cacheDirectory)
        val zip = cacheDirectory.resolve("${sessionDirectory.fileName}.zip")
        return SessionZipExporter.export(SessionZipPlan.fromSessionDirectory(sessionDirectory, zip), onProgress)
    }

    fun cleanupTemporaryShareZips(cacheDirectory: Path) {
        if (!Files.isDirectory(cacheDirectory)) return
        Files.list(cacheDirectory).use { stream ->
            stream
                .filter { it.fileName.toString().endsWith(".zip", ignoreCase = true) }
                .forEach { runCatching { Files.deleteIfExists(it) } }
        }
    }

    fun archive(sessionDirectory: Path, archiveRoot: Path): Path {
        require(Files.isDirectory(sessionDirectory)) { "Archive source must be a session directory." }
        Files.createDirectories(archiveRoot)
        val archive = uniquePath(archiveRoot.resolve("${sessionDirectory.fileName}.zip"))
        createMaximumCompressionZip(sessionDirectory, archive)
        verifyArchive(archive)
        FilesystemSessionBrowser.deleteRecording(sessionDirectory)
        return archive
    }

    fun restore(archive: Path, restoreRoot: Path): Path {
        require(Files.isRegularFile(archive)) { "Restore source must be an archive ZIP." }
        verifyArchive(archive)
        Files.createDirectories(restoreRoot)
        val target = uniquePath(restoreRoot.resolve(archive.fileName.nameWithoutExtension))
        Files.createDirectories(target)
        ZipInputStream(Files.newInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val destination = target.resolve(entry.name).normalize()
                require(destination.startsWith(target)) { "Archive entry escapes restore directory." }
                if (entry.isDirectory) {
                    Files.createDirectories(destination)
                } else {
                    destination.parent?.let(Files::createDirectories)
                    Files.newOutputStream(destination).use(zip::copyTo)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        require(Files.walk(target).use { stream -> stream.anyMatch(Files::isRegularFile) }) {
            "Restored archive contains no files."
        }
        Files.deleteIfExists(archive)
        return target
    }

    fun verifyArchive(archive: Path) {
        require(Files.isRegularFile(archive)) { "Archive ZIP is missing." }
        ZipFile(archive.toFile()).use { zip ->
            val entries = zip.entries().asSequence().filterNot(ZipEntry::isDirectory).toList()
            require(entries.isNotEmpty()) { "Archive ZIP contains no files." }
            require(entries.any { it.name == "session.json" } || entries.any { it.name == "receiver-rx.raw" }) {
                "Archive ZIP does not contain expected recording artifacts."
            }
        }
    }

    private fun createMaximumCompressionZip(sourceDirectory: Path, outputZip: Path) {
        val files = Files.walk(sourceDirectory).use { stream ->
            stream.filter(Files::isRegularFile).sorted().toListCompat()
        }
        ZipOutputStream(Files.newOutputStream(outputZip)).use { zip ->
            zip.setLevel(java.util.zip.Deflater.BEST_COMPRESSION)
            files.forEach { file ->
                val relativeName = sourceDirectory.relativize(file).joinToString("/")
                zip.putNextEntry(ZipEntry(relativeName))
                Files.newInputStream(file).use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun uniquePath(base: Path): Path {
        if (!Files.exists(base)) return base
        val fileName = base.fileName.toString()
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = base.resolveSibling("$stem-$index$ext")
            if (!Files.exists(candidate)) return candidate
            index += 1
        }
    }
}

private fun Path.joinToString(separator: String): String =
    iterator().asSequence().joinToString(separator) { it.toString() }

private fun <T> java.util.stream.Stream<T>.toListCompat(): List<T> {
    val result = mutableListOf<T>()
    forEach(result::add)
    return result
}
```

- [ ] **Step 4: Verify archive behavior**

Run archive tests or `:app:compileDebugKotlin` if blocked.

## Task 4: Sessions Screen UI

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionBrowserModels.kt`
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/sessions/SessionsScreen.kt`

- [ ] **Step 1: Write or update UI model tests**

If current UI model remains, add tests that convert `SessionBrowserState` into display rows. Otherwise rely on Task 1 model tests and keep Compose changes compile-verified.

- [ ] **Step 2: Implement grouped Sessions screen**

Replace the current single-card list with:

- top bar: `Sessions`, `Back`;
- progress text;
- selection shortcut row: `Current`, `Recordings`, `Archived`, `All`, `Clear`;
- grouped cards with checkboxes;
- action row: `Share ZIP`, `Archive`, `Restore`, `Delete`;
- confirmation dialogs for archive, restore and delete.

Use callbacks:

```kotlin
fun SessionsScreen(
    state: SessionBrowserState,
    progressText: String? = null,
    onToggle: (String) -> Unit,
    onSelectCurrent: () -> Unit,
    onSelectRecordings: () -> Unit,
    onSelectArchives: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onShareSelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    onRestoreSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onBack: () -> Unit,
)
```

Disable action buttons when no selected entries are eligible. Show active sessions as not eligible for destructive/archive/share actions.

- [ ] **Step 3: Compile UI**

Run:

```bash
sh gradlew :app:compileDebugKotlin --rerun-tasks
```

Expected: compile succeeds.

## Task 5: MainActivity Wiring

**Files:**
- Modify: `app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt`

- [ ] **Step 1: Add session state loading**

Add state:

```kotlin
var sessionBrowserState by remember { mutableStateOf(SessionBrowserState.fromEntries(emptyList())) }
```

When opening `AppScreen.SESSIONS`, load entries from configured app-private storage root plus current state. For SAF storage, show current session and a message that full SAF archive browsing is pending unless the SAF adapter has been implemented.

- [ ] **Step 2: Wire share selected**

For each selected completed recording:

1. create temp ZIP in `cacheDir/rtkcollector-share`;
2. share one ZIP with `ACTION_SEND`, multiple with `ACTION_SEND_MULTIPLE`;
3. do not delete originals;
4. clean old temp ZIPs when opening Sessions.

- [ ] **Step 3: Wire archive selected**

For each selected completed recording:

1. call `SessionArchiveManager.archive(sessionPath, storageRoot)`;
2. reload browser entries;
3. report partial failures with `Toast`.

- [ ] **Step 4: Wire restore selected**

For each selected archive:

1. call `SessionArchiveManager.restore(archivePath, storageRoot)`;
2. reload browser entries;
3. report partial failures with `Toast`.

- [ ] **Step 5: Wire delete selected**

For selected completed recordings, delete folders. For selected archives, delete ZIPs. Skip active current session. Confirm before running.

- [ ] **Step 6: Verify compile**

Run:

```bash
sh gradlew :app:compileDebugKotlin --rerun-tasks
```

Expected: compile succeeds.

## Task 6: Documentation And Final Verification

**Files:**
- Modify: `docs/user-workflows.md`
- Modify: `docs/session-format.md`

- [ ] **Step 1: Document Sessions menu**

Update user docs with:

- current/recordings/archives groups;
- temporary share ZIP semantics;
- permanent archive semantics;
- restore semantics;
- deletion confirmation and active-session protection.

- [ ] **Step 2: Run feasible verification**

Run sequentially:

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector diff --check
sh gradlew :app:compileDebugKotlin --rerun-tasks
sh gradlew :receiver:unicore-n4:test --rerun-tasks
```

Attempt targeted app tests:

```bash
sh gradlew :app:testDebugUnitTest --tests org.rtkcollector.app.sessions.SessionBrowserModelsTest --tests org.rtkcollector.app.sessions.FilesystemSessionBrowserTest --tests org.rtkcollector.app.sessions.SessionArchiveManagerTest
```

If local Termux AAPT2 blocks app tests before execution, report that exact limitation.

- [ ] **Step 3: Commit**

Stage only intended files. Do not stage `.superpowers/` or `samples/`.

```bash
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector add \
  docs/superpowers/specs/2026-06-11-session-browser-archive-design.md \
  docs/superpowers/plans/2026-06-11-session-browser-archive.md \
  app/src/main/kotlin/org/rtkcollector/app/sessions \
  app/src/test/kotlin/org/rtkcollector/app/sessions \
  app/src/main/kotlin/org/rtkcollector/app/ui/sessions \
  app/src/main/kotlin/org/rtkcollector/app/ui/MainActivity.kt \
  docs/user-workflows.md \
  docs/session-format.md
git -c safe.directory=/storage/emulated/0/GitHub/rtkcollector commit -m "Add session browser archive workflow"
```

## Self-Review

- Spec coverage: browsing configured storage, grouping, sorting latest first, multi-select, temporary share ZIP, permanent archive, restore and delete are covered by Tasks 1-6.
- Safety: active recording is not eligible for archive/delete/share ZIP; share ZIP never removes originals; archive verifies before deleting originals; restore verifies before deleting ZIP.
- Known limitation: SAF full traversal/mutation may need Android-specific adapter work after the app-private path is complete. The model and UI must not hard-code filesystem-only concepts into public display state.
