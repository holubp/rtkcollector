package org.rtkcollector.app.testing

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path

object TestFiles {
    fun readString(path: Path): String = String(Files.readAllBytes(path), UTF_8)

    fun writeString(path: Path, value: CharSequence): Path =
        Files.write(path, value.toString().toByteArray(UTF_8))
}
