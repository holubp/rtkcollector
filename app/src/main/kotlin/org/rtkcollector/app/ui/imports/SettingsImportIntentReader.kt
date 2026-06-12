package org.rtkcollector.app.ui.imports

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

fun settingsImportUriFromIntent(intent: Intent?): Uri? {
    if (intent == null) return null
    return when (intent.action) {
        Intent.ACTION_VIEW -> validImportUri(intent.data) ?: clipDataUri(intent)
        Intent.ACTION_SEND -> validImportUri(
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java),
        ) ?: clipDataUri(intent)
        else -> null
    }
}

private fun clipDataUri(intent: Intent): Uri? {
    val clipData = intent.clipData ?: return null
    return (0 until clipData.itemCount)
        .asSequence()
        .mapNotNull { index -> validImportUri(clipData.getItemAt(index).uri) }
        .firstOrNull()
}

private fun validImportUri(uri: Uri?): Uri? =
    uri?.takeIf { it.scheme == "content" || it.scheme == "file" }
