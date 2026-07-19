package org.rtkcollector.app.ui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rtkcollector.app.testing.TestFiles
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory

class FileProviderPathsTest {
    @Test
    fun `file provider exposes temporary share zip cache directory`() {
        val xml = TestFiles.locateProjectPath("src/main/res/xml/file_paths.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Files.newInputStream(xml))
        val nodes = document.getElementsByTagName("cache-path")

        val exposesShareZipCache = (0 until nodes.length).any { index ->
            val node = nodes.item(index)
            val attributes = node.attributes
            attributes.getNamedItem("name")?.nodeValue == "session_share_zips" &&
                attributes.getNamedItem("path")?.nodeValue == "session-share-zips/"
        }

        assertTrue(exposesShareZipCache, "FileProvider must expose cache/session-share-zips for temporary ZIP sharing.")
    }

    @Test
    fun `file provider exposes temporary diagnostics share cache directory`() {
        val xml = TestFiles.locateProjectPath("src/main/res/xml/file_paths.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Files.newInputStream(xml))
        val nodes = document.getElementsByTagName("cache-path")

        val exposesDiagnosticsShareCache = (0 until nodes.length).any { index ->
            val node = nodes.item(index)
            val attributes = node.attributes
            attributes.getNamedItem("name")?.nodeValue == "diagnostic_share" &&
                attributes.getNamedItem("path")?.nodeValue == "diagnostic-share/"
        }

        assertTrue(exposesDiagnosticsShareCache, "FileProvider must expose cache/diagnostic-share for diagnostics sharing.")
    }
}
