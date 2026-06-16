package org.rtkcollector.app.sessions

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import org.rtkcollector.core.session.SessionArtifactFile

object SafSessionBrowser {
    fun discover(
        resolver: ContentResolver,
        treeUri: Uri,
        currentSessionLocation: String? = null,
        currentSessionActive: Boolean = false,
    ): SessionBrowserState {
        val rootUri = treeUri.rootDocumentUri()
        val currentUri = currentSessionLocation
            ?.takeIf { it.isNotBlank() && it.startsWith("content://") }
            ?.let(Uri::parse)
        val entries = mutableListOf<SessionBrowserEntry>()
        currentUri?.let { uri ->
            entries += SessionBrowserEntry(
                id = uri.toString(),
                title = if (currentSessionActive) "Current recording" else (uri.displayName(resolver) ?: "Last session"),
                subtitle = uri.toString(),
                location = uri.toString(),
                kind = if (currentSessionActive) SessionEntryKind.CURRENT_ACTIVE else SessionEntryKind.CURRENT_STOPPED,
                modifiedEpochMillis = uri.lastModified(resolver),
                sizeBytes = uri.directorySize(resolver),
                filesystemBacked = false,
                capabilities = capabilitiesForDirectory(currentSessionActive),
            )
        }

        rootUri.children(resolver).forEach { child ->
            if (currentUri != null && child.uri == currentUri) return@forEach
            if (child.isDirectory && child.uri.looksLikeSessionDirectory(resolver)) {
                entries += SessionBrowserEntry(
                    id = child.uri.toString(),
                    title = child.displayName,
                    subtitle = child.uri.toString(),
                    location = child.uri.toString(),
                    kind = SessionEntryKind.RECORDING,
                    modifiedEpochMillis = child.lastModified,
                    sizeBytes = child.uri.directorySize(resolver),
                    filesystemBacked = false,
                    capabilities = capabilitiesForDirectory(isActive = false),
                )
            } else if (!child.isDirectory && child.displayName.endsWith(".zip")) {
                entries += SessionBrowserEntry(
                    id = child.uri.toString(),
                    title = child.displayName.removeSuffix(".zip"),
                    subtitle = "Archived ZIP: ${child.uri}",
                    location = child.uri.toString(),
                    kind = SessionEntryKind.ARCHIVE,
                    modifiedEpochMillis = child.lastModified,
                    sizeBytes = child.sizeBytes,
                    filesystemBacked = false,
                    capabilities = capabilitiesForArchive(),
                )
            }
        }
        return sessionBrowserStateOf(entries)
    }

    private fun capabilitiesForDirectory(isActive: Boolean): SessionActionCapabilities =
        SessionActionCapabilities(
            shareZip = !isActive,
            shareNmea = !isActive,
            reexportNmea = !isActive,
            archive = !isActive,
            restore = false,
            delete = !isActive,
        )

    private fun capabilitiesForArchive(): SessionActionCapabilities =
        SessionActionCapabilities(
            shareZip = false,
            shareNmea = false,
            reexportNmea = false,
            archive = false,
            restore = true,
            delete = true,
        )
}

internal data class SafDocument(
    val uri: Uri,
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val lastModified: Long,
    val sizeBytes: Long?,
) {
    val isDirectory: Boolean
        get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

internal fun Uri.rootDocumentUri(): Uri =
    DocumentsContract.buildDocumentUriUsingTree(this, DocumentsContract.getTreeDocumentId(this))

internal fun Uri.children(resolver: ContentResolver): List<SafDocument> {
    val parentDocumentId = DocumentsContract.getDocumentId(this)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(this, parentDocumentId)
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_SIZE,
    )
    val result = mutableListOf<SafDocument>()
    resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
        while (cursor.moveToNext()) {
            val documentId = cursor.getString(idIndex)
            result += SafDocument(
                uri = DocumentsContract.buildDocumentUriUsingTree(this, documentId),
                documentId = documentId,
                displayName = cursor.getString(nameIndex).orEmpty(),
                mimeType = cursor.getString(mimeIndex).orEmpty(),
                lastModified = cursor.getLongOrZero(modifiedIndex),
                sizeBytes = cursor.getLongOrNull(sizeIndex),
            )
        }
    }
    return result
}

internal fun Uri.findChild(resolver: ContentResolver, name: String): SafDocument? =
    children(resolver).firstOrNull { it.displayName == name }

internal fun Uri.displayName(resolver: ContentResolver): String? =
    querySingleString(resolver, DocumentsContract.Document.COLUMN_DISPLAY_NAME)

internal fun Uri.lastModified(resolver: ContentResolver): Long =
    querySingleLong(resolver, DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: 0L

internal fun Uri.directorySize(resolver: ContentResolver): Long? =
    runCatching {
        children(resolver).sumOf { child ->
            if (child.isDirectory) {
                child.uri.directorySize(resolver) ?: 0L
            } else {
                child.sizeBytes ?: 0L
            }
        }
    }.getOrNull()

internal fun Uri.looksLikeSessionDirectory(resolver: ContentResolver): Boolean {
    val name = displayName(resolver).orEmpty()
    return name.startsWith("session-") ||
        findChild(resolver, SessionArtifactFile.SESSION_JSON.fileName) != null ||
        findChild(resolver, SessionArtifactFile.RECEIVER_RX_RAW.fileName) != null
}

private fun Uri.querySingleString(resolver: ContentResolver, column: String): String? =
    resolver.query(this, arrayOf(column), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

private fun Uri.querySingleLong(resolver: ContentResolver, column: String): Long? =
    resolver.query(this, arrayOf(column), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLongOrNull(0) else null
    }

private fun android.database.Cursor.getLongOrNull(index: Int): Long? =
    if (isNull(index)) null else getLong(index)

private fun android.database.Cursor.getLongOrZero(index: Int): Long =
    getLongOrNull(index) ?: 0L
