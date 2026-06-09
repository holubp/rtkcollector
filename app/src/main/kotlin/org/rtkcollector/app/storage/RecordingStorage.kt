package org.rtkcollector.app.storage

object RecordingStorage {
    const val APP_PRIVATE = "APP_PRIVATE"
    const val SAF_TREE = "SAF_TREE"

    fun isSupportedStorageKind(kind: String): Boolean =
        kind == APP_PRIVATE || kind == SAF_TREE

    fun validateStorageSelection(kind: String, treeUri: String?) {
        require(isSupportedStorageKind(kind)) { "Unsupported storage kind: $kind" }
        require(kind != SAF_TREE || !treeUri.isNullOrBlank()) { "SAF_TREE storage requires a tree URI." }
    }

    fun isLikelySafTreeUri(uri: String?): Boolean =
        !uri.isNullOrBlank() && uri.startsWith("content://") && "/tree/" in uri
}
