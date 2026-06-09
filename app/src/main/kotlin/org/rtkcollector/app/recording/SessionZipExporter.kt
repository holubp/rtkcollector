package org.rtkcollector.app.recording

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SessionZipProgress(
    val filesCompleted: Int,
    val totalFiles: Int,
    val currentFile: String? = null,
) {
    val fraction: Double
        get() = if (totalFiles == 0) 1.0 else filesCompleted.toDouble() / totalFiles.toDouble()
}

data class SessionZipPlan(
    val sourceDirectory: Path,
    val outputZip: Path,
    val files: List<Path>,
) {
    init {
        require(files.all { it.startsWith(sourceDirectory) }) {
            "All ZIP source files must be inside the session directory."
        }
    }

    companion object {
        fun fromSessionDirectory(sourceDirectory: Path, outputZip: Path): SessionZipPlan {
            require(Files.isDirectory(sourceDirectory)) { "Session ZIP source must be a directory." }
            val files = Files.walk(sourceDirectory).use { stream ->
                stream
                    .filter(Files::isRegularFile)
                    .filter { it != outputZip }
                    .sorted()
                    .toListCompat()
            }
            return SessionZipPlan(sourceDirectory, outputZip, files)
        }
    }
}

object SessionZipExporter {
    fun export(
        plan: SessionZipPlan,
        onProgress: (SessionZipProgress) -> Unit = {},
    ): Path {
        plan.outputZip.parent?.let(Files::createDirectories)
        onProgress(SessionZipProgress(filesCompleted = 0, totalFiles = plan.files.size))
        ZipOutputStream(Files.newOutputStream(plan.outputZip)).use { zip ->
            plan.files.forEachIndexed { index, file ->
                val relativeName = plan.sourceDirectory.relativize(file).joinToString("/")
                zip.putNextEntry(ZipEntry(relativeName))
                Files.newInputStream(file).use { input -> input.copyTo(zip) }
                zip.closeEntry()
                onProgress(
                    SessionZipProgress(
                        filesCompleted = index + 1,
                        totalFiles = plan.files.size,
                        currentFile = relativeName,
                    ),
                )
            }
        }
        return plan.outputZip
    }
}

private fun Path.joinToString(separator: String): String =
    iterator().asSequence().joinToString(separator) { it.toString() }

private fun <T> java.util.stream.Stream<T>.toListCompat(): List<T> {
    val result = mutableListOf<T>()
    forEach(result::add)
    return result
}
