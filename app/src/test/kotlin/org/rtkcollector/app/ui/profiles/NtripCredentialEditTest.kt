package org.rtkcollector.app.ui.profiles

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripCasterUploadProfile

class NtripCredentialEditTest {
    @Test fun `invalid correction owner does not touch secret storage`() {
        var writes = 0
        assertThrows(IllegalArgumentException::class.java) {
            stageNtripCredentialEdit(NtripCasterProfile("c", "Caster", port = 0), "new", { _, _ -> writes++ })
        }
        assertEquals(0, writes)
    }

    @Test fun `invalid upload owner does not touch secret storage`() {
        var writes = 0
        assertThrows(IllegalArgumentException::class.java) {
            stageNtripCredentialEdit(NtripCasterUploadProfile("u", "Upload", port = 0), "new", { _, _ -> writes++ })
        }
        assertEquals(0, writes)
    }

    @Test fun `edited password is staged without overwriting committed binding`() {
        val secrets = mutableMapOf("committed" to "old")
        val previous = NtripCasterProfile("c", "Caster", secretId = "committed")
        val changed = stageNtripCredentialEdit(previous, "new", { id, value -> secrets[id] = value })
        assertNotEquals(previous.secretId, changed.secretId)
        assertEquals("old", secrets[previous.secretId])
        assertEquals("new", secrets[changed.secretId])
        assertEquals(previous.copy(secretId = changed.secretId), changed)
    }

    @Test fun `omitted password preserves upload owner and explicit binding`() {
        val previous = NtripCasterUploadProfile("u", "Upload", secretId = "custom-binding")
        val changed = stageNtripCredentialEdit(previous, null, { _, _ -> fail<Unit>("Unexpected secret write") })
        assertEquals(previous, changed)
    }
}
