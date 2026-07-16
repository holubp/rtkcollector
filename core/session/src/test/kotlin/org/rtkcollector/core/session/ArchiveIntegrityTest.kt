package org.rtkcollector.core.session

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveIntegrityTest {
    @Test
    fun `safe directory entries and nested files are preserved`() {
        val archive = zipOf(
            ZipContent("nested/", isDirectory = true),
            ZipContent("nested/session.json", "{}".toByteArray()),
            ZipContent("receiver-rx.raw", byteArrayOf(1, 2, 3)),
        )
        val restored = linkedMapOf<String, ByteArrayOutputStream>()

        val manifest = ArchiveIntegrity.extract(
            input = ByteArrayInputStream(archive),
            openOutput = { path -> ByteArrayOutputStream().also { restored[path] = it } },
        )

        assertEquals(listOf("nested/session.json", "receiver-rx.raw"), manifest.entries.map { it.relativePath })
        assertArrayEquals("{}".toByteArray(), restored.getValue("nested/session.json").toByteArray())
        assertArrayEquals(byteArrayOf(1, 2, 3), restored.getValue("receiver-rx.raw").toByteArray())
    }

    @Test
    fun `archive content must match the complete source file set`() {
        val archive = zipOf(
            ZipContent("session.json", "{}".toByteArray()),
            ZipContent("receiver-rx.raw", byteArrayOf(1, 2, 3)),
        )
        val sources = listOf(
            source("session.json", "{}".toByteArray()),
            source("receiver-rx.raw", byteArrayOf(1, 2, 3)),
        )

        val manifest = ArchiveIntegrity.verifyArchiveAgainstSources(ByteArrayInputStream(archive), sources)

        assertEquals(2, manifest.entries.size)
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveIntegrity.verifyArchiveAgainstSources(
                ByteArrayInputStream(archive),
                sources + source("events.jsonl", byteArrayOf(4)),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveIntegrity.verifyArchiveAgainstSources(
                ByteArrayInputStream(archive),
                listOf(source("session.json", "changed".toByteArray()), sources[1]),
            )
        }
    }

    @Test
    fun `archive source comparison accepts legal asymmetric short reads`() {
        val content = ByteArray(256 * 1024) { index -> index.toByte() }
        val archive = zipOf(ZipContent("receiver-rx.raw", content))
        val sources = listOf(
            ArchiveSourceFile("receiver-rx.raw") {
                ShortReadingInputStream(content, maximumReadBytes = 7)
            },
        )

        val manifest = ArchiveIntegrity.verifyArchiveAgainstSources(ByteArrayInputStream(archive), sources)

        assertEquals(content.size.toLong(), manifest.entries.single().sizeBytes)
    }

    @Test
    fun `restored sources must match the inspected archive manifest`() {
        val archive = zipOf(ZipContent("session.json", "{}".toByteArray()))
        val manifest = ArchiveIntegrity.inspect(ByteArrayInputStream(archive))

        ArchiveIntegrity.verifySourcesAgainstManifest(
            listOf(source("session.json", "{}".toByteArray())),
            manifest,
        )
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveIntegrity.verifySourcesAgainstManifest(
                listOf(source("session.json", "corrupt".toByteArray())),
                manifest,
            )
        }
    }

    @Test
    fun `traversal absolute and backslash paths are rejected`() {
        listOf("../escape", "/absolute", "nested\\escape").forEach { unsafePath ->
            val archive = zipOf(ZipContent(unsafePath, byteArrayOf(1)))

            assertThrows(IllegalArgumentException::class.java) {
                ArchiveIntegrity.inspect(ByteArrayInputStream(archive))
            }
        }
    }

    @Test
    fun `duplicate and file directory path collisions are rejected`() {
        val duplicate = zipOf(
            ZipContent("nested/", isDirectory = true),
            ZipContent("nested", byteArrayOf(1)),
        )
        val fileAsParent = zipOf(
            ZipContent("nested", byteArrayOf(1)),
            ZipContent("nested/session.json", byteArrayOf(2)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            ArchiveIntegrity.inspect(ByteArrayInputStream(duplicate))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveIntegrity.inspect(ByteArrayInputStream(fileAsParent))
        }
    }

    @Test
    fun `path depth and entry count limits include directories`() {
        val archive = zipOf(
            ZipContent("one/", isDirectory = true),
            ZipContent("one/two/session.json", byteArrayOf(1)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            ArchiveIntegrity.inspect(
                ByteArrayInputStream(archive),
                policy(maxEntries = 1, maxPathDepth = 3),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveIntegrity.inspect(
                ByteArrayInputStream(archive),
                policy(maxEntries = 2, maxPathDepth = 2),
            )
        }
    }

    @Test
    fun `per entry and cumulative expanded size limits stop extraction`() {
        val oversizedEntry = zipOf(ZipContent("receiver-rx.raw", byteArrayOf(1, 2, 3, 4)))
        val cumulative = zipOf(
            ZipContent("session.json", byteArrayOf(1, 2, 3)),
            ZipContent("receiver-rx.raw", byteArrayOf(4, 5, 6)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            ArchiveIntegrity.extract(ByteArrayInputStream(oversizedEntry), { ByteArrayOutputStream() }, policy(3, 3))
        }
        val outputs = mutableListOf<ByteArrayOutputStream>()
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveIntegrity.extract(
                ByteArrayInputStream(cumulative),
                { ByteArrayOutputStream().also(outputs::add) },
                policy(3, 5),
            )
        }
        assertEquals(2, outputs.size)
        assertEquals(2, outputs.last().size())
    }

    @Test
    fun `compression ratio limit rejects highly compressed content`() {
        val archive = zipOf(ZipContent("receiver-rx.raw", ByteArray(8 * 1024)))

        assertThrows(IllegalArgumentException::class.java) {
            ArchiveIntegrity.inspect(
                ByteArrayInputStream(archive),
                policy(8 * 1024L, 8 * 1024L, maxCompressionRatio = 2L),
            )
        }
    }

    private fun source(path: String, content: ByteArray): ArchiveSourceFile =
        ArchiveSourceFile(path) { ByteArrayInputStream(content) }

    private fun policy(
        maxEntryBytes: Long = 16L,
        maxTotalBytes: Long = 32L,
        maxEntries: Int = 10,
        maxPathDepth: Int = 4,
        maxCompressionRatio: Long = 200L,
    ): ArchiveIntegrityPolicy =
        ArchiveIntegrityPolicy(
            maxEntries = maxEntries,
            maxPathDepth = maxPathDepth,
            maxEntryUncompressedBytes = maxEntryBytes,
            maxTotalUncompressedBytes = maxTotalBytes,
            maxCompressionRatio = maxCompressionRatio,
        )

    private fun zipOf(vararg contents: ZipContent): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.setLevel(Deflater.BEST_COMPRESSION)
                contents.forEach { content ->
                    zip.putNextEntry(ZipEntry(content.path))
                    if (!content.isDirectory) zip.write(content.bytes)
                    zip.closeEntry()
                }
            }
            bytes.toByteArray()
        }

    private data class ZipContent(
        val path: String,
        val bytes: ByteArray = byteArrayOf(),
        val isDirectory: Boolean = false,
    )

    private class ShortReadingInputStream(
        content: ByteArray,
        private val maximumReadBytes: Int,
    ) : ByteArrayInputStream(content) {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, minOf(length, maximumReadBytes))
    }
}
