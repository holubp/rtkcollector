package org.rtkcollector.app.testing

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object TestFiles {
    fun readString(path: Path): String = String(Files.readAllBytes(path), UTF_8)

    fun writeString(path: Path, value: CharSequence): Path =
        Files.write(path, value.toString().toByteArray(UTF_8))

    fun locateProjectPath(
        relative: String,
        predicate: (Path) -> Boolean = { Files.exists(it) },
    ): Path = locateProjectPathFrom(
        workingDirectory = Paths.get("").toAbsolutePath().normalize(),
        relative = relative,
        predicate = predicate,
    )

    internal fun locateProjectPathFrom(
        workingDirectory: Path,
        relative: String,
        predicate: (Path) -> Boolean = { Files.exists(it) },
    ): Path {
        val requested = Paths.get(relative).normalize()
        require(!requested.isAbsolute) { "Project test path must be relative: $relative" }
        require(requested.none { it.toString() == ".." }) {
            "Project test path must stay inside the checkout: $relative"
        }

        val variants = buildList {
            add(requested)
            if (requested.nameCount > 1 && requested.getName(0).toString() == "app") {
                add(requested.subpath(1, requested.nameCount))
            } else {
                add(Paths.get("app").resolve(requested))
            }
        }
        val repositoryRoot = generateSequence(workingDirectory.toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { candidate ->
                Files.isRegularFile(candidate.resolve("settings.gradle.kts")) &&
                    Files.isRegularFile(candidate.resolve("app/build.gradle.kts"))
            }
            ?: error("Cannot locate repository root from $workingDirectory")
        return variants.asSequence()
            .map(repositoryRoot::resolve)
            .map(Path::normalize)
            .distinct()
            .firstOrNull(predicate)
            ?: error("Cannot locate project path $relative inside $repositoryRoot")
    }
}
