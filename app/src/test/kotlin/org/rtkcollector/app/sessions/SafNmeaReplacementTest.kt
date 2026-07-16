package org.rtkcollector.app.sessions

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class SafNmeaReplacementTest {
    @Test
    fun `copy fallback replaces canonical export only after verified backup`() {
        val store = FakeStore(renameSupported = false)
        store.add("receiver-solution.nmea", "old".toByteArray())
        val temporary = store.add("receiver-solution.nmea.tmp", "new".toByteArray())

        SafNmeaReplacement.replace(
            temporary,
            "receiver-solution.nmea",
            "receiver-solution.nmea.previous",
            store,
        )

        assertArrayEquals("new".toByteArray(), store.content("receiver-solution.nmea"))
    }

    @Test
    fun `copy failure restores previous canonical export`() {
        val store = FakeStore(renameSupported = false, failReplacementCopy = true)
        store.add("receiver-solution.nmea", "old".toByteArray())
        val temporary = store.add("receiver-solution.nmea.tmp", "new".toByteArray())

        assertThrows(IllegalStateException::class.java) {
            SafNmeaReplacement.replace(
                temporary,
                "receiver-solution.nmea",
                "receiver-solution.nmea.previous",
                store,
            )
        }

        assertArrayEquals("old".toByteArray(), store.content("receiver-solution.nmea"))
    }

    @Test
    fun `copy failure without previous export removes partial canonical output`() {
        val store = FakeStore(renameSupported = false, failReplacementCopy = true)
        val temporary = store.add("receiver-solution.nmea.tmp", "new".toByteArray())

        assertThrows(IllegalStateException::class.java) {
            SafNmeaReplacement.replace(
                temporary,
                "receiver-solution.nmea",
                "receiver-solution.nmea.previous",
                store,
            )
        }

        assertFalse(store.exists("receiver-solution.nmea"))
    }

    @Test
    fun `provider delete refusal preserves previous canonical export`() {
        val store = FakeStore(renameSupported = false, refusePreviousDelete = true)
        store.add("receiver-solution.nmea", "old".toByteArray())
        val temporary = store.add("receiver-solution.nmea.tmp", "new".toByteArray())

        assertThrows(IllegalArgumentException::class.java) {
            SafNmeaReplacement.replace(
                temporary,
                "receiver-solution.nmea",
                "receiver-solution.nmea.previous",
                store,
            )
        }

        assertArrayEquals("old".toByteArray(), store.content("receiver-solution.nmea"))
    }

    @Test
    fun `corrupt rename replacement rolls back verified previous export`() {
        val store = FakeStore(renameSupported = true, corruptReplacementRename = true)
        store.add("receiver-solution.nmea", "old".toByteArray())
        val temporary = store.add("receiver-solution.nmea.tmp", "new".toByteArray())

        assertThrows(IllegalArgumentException::class.java) {
            SafNmeaReplacement.replace(
                temporary,
                "receiver-solution.nmea",
                "receiver-solution.nmea.previous",
                store,
            )
        }

        assertArrayEquals("old".toByteArray(), store.content("receiver-solution.nmea"))
    }

    private class FakeStore(
        private val renameSupported: Boolean,
        private val failReplacementCopy: Boolean = false,
        private val refusePreviousDelete: Boolean = false,
        private val corruptReplacementRename: Boolean = false,
    ) : SafReplacementStore<DocumentId> {
        private val documents = linkedMapOf<DocumentId, Document>()
        private var nextId = 1

        fun add(name: String, bytes: ByteArray): DocumentId =
            DocumentId(nextId++).also { documents[it] = Document(name, bytes.copyOf()) }

        fun content(name: String): ByteArray = documents.getValue(find(name)!!).bytes

        fun exists(name: String): Boolean = find(name) != null

        override fun find(name: String): DocumentId? = documents.entries.firstOrNull { it.value.name == name }?.key

        override fun rename(document: DocumentId, name: String): DocumentId? {
            if (!renameSupported) return null
            val current = documents.getValue(document)
            current.name = name
            if (corruptReplacementRename && name == "receiver-solution.nmea" && current.bytes.contentEquals("new".toByteArray())) {
                current.bytes = byteArrayOf()
            }
            return document
        }

        override fun create(name: String): DocumentId = add(name, byteArrayOf())

        override fun delete(document: DocumentId): Boolean {
            val current = documents.getValue(document)
            if (refusePreviousDelete && current.name == "receiver-solution.nmea" && current.bytes.contentEquals("old".toByteArray())) {
                return false
            }
            return documents.remove(document) != null
        }

        override fun digest(document: DocumentId): SafDocumentDigest {
            val bytes = documents.getValue(document).bytes
            return SafDocumentDigest(bytes.size.toLong(), MessageDigest.getInstance("SHA-256").digest(bytes).toHex())
        }

        override fun copy(source: DocumentId, destination: DocumentId) {
            val sourceDocument = documents.getValue(source)
            val destinationDocument = documents.getValue(destination)
            destinationDocument.bytes = sourceDocument.bytes.copyOf()
            if (
                failReplacementCopy &&
                sourceDocument.name == "receiver-solution.nmea.tmp" &&
                destinationDocument.name == "receiver-solution.nmea"
            ) {
                destinationDocument.bytes = "partial".toByteArray()
                error("injected replacement copy failure")
            }
        }
    }

    private data class DocumentId(val value: Int)

    private data class Document(
        var name: String,
        var bytes: ByteArray,
    )
}

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
