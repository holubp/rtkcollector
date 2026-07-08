package org.rtkcollector.app.share

private val SETTINGS_BACKUP_FILE_NAME = Regex("""^rtkcollector-settings(?:-plaintext-passwords)?-\d+\.json$""")

fun settingsBackupFileName(
    epochMillis: Long,
    includesPlaintextPasswords: Boolean,
): String {
    require(epochMillis >= 0) { "Settings backup timestamp must not be negative." }
    val suffix = if (includesPlaintextPasswords) "-plaintext-passwords" else ""
    return "rtkcollector-settings$suffix-$epochMillis.json"
}

fun isSettingsBackupCacheFile(name: String): Boolean {
    if (name.contains('/') || name.contains('\\')) return false
    return SETTINGS_BACKUP_FILE_NAME.matches(name)
}
