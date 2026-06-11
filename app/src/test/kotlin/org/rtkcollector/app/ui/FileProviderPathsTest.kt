package org.rtkcollector.app.ui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

class FileProviderPathsTest {
    @Test
    fun `file provider exposes temporary share zip cache directory`() {
        val xml = sourceFile("src/main/res/xml/file_paths.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Files.newInputStream(xml))
        val nodes = document.getElementsByTagName("cache-path")

        val exposesShareZipCache = (0 until nodes.length).any { index ->
            val node = nodes.item(index)
            val attributes = node.attributes
            attributes.getNamedItem("android:name")?.nodeValue == "session_share_zips" &&
                attributes.getNamedItem("android:path")?.nodeValue == "session-share-zips/"
        }

        assertTrue(exposesShareZipCache, "FileProvider must expose cache/session-share-zips for temporary ZIP sharing.")
    }

    private fun sourceFile(relative: String): Path {
        val candidates = listOf(Path.of(relative), Path.of("app").resolve(relative))
        return candidates.firstOrNull(Files::exists)
            ?: error("Cannot locate source file $relative from ${Path.of("").toAbsolutePath()}")
    }
}
