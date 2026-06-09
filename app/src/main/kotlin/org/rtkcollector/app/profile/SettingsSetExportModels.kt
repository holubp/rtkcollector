package org.rtkcollector.app.profile

data class SettingsSetExportOptions(
    val includePlaintextPasswords: Boolean = false,
) {
    val passwordWarning: String?
        get() = if (includePlaintextPasswords) {
            "Plaintext NTRIP passwords will be included in the exported settings file."
        } else {
            null
        }
}
