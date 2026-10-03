package org.rtkcollector.app.ui.profiles

import java.util.UUID
import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripCasterUploadProfile

fun stageNtripCredentialEdit(
    owner: NtripCasterProfile,
    submittedPassword: String?,
    savePassword: (String, String) -> Unit,
): NtripCasterProfile {
    owner.validate()
    if (submittedPassword == null) return owner
    val updated = owner.copy(secretId = "ntrip-edit-${UUID.randomUUID()}").also(NtripCasterProfile::validate)
    stagePassword(updated.secretId, submittedPassword, savePassword)
    return updated
}

fun stageNtripCredentialEdit(
    owner: NtripCasterUploadProfile,
    submittedPassword: String?,
    savePassword: (String, String) -> Unit,
): NtripCasterUploadProfile {
    owner.validate()
    if (submittedPassword == null) return owner
    val updated = owner.copy(secretId = "ntrip-upload-edit-${UUID.randomUUID()}").also(NtripCasterUploadProfile::validate)
    stagePassword(updated.secretId, submittedPassword, savePassword)
    return updated
}

// A failed profile publication leaves its previous secret binding authoritative.
private fun stagePassword(id: String, password: String, save: (String, String) -> Unit) {
    try {
        save(id, password)
    } catch (_: Exception) {
        throw IllegalStateException("Credentials could not be saved.")
    }
}
