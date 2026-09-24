package org.rtkcollector.app.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.rtkcollector.core.correction.NtripTlsVerification
import org.rtkcollector.core.correction.NtripTransportMode

@RunWith(RobolectricTestRunner::class)
class NtripProfileStoreSecurityTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("profile-manager", Context.MODE_PRIVATE)

    private fun reset() = prefs.edit().clear().commit().also { assertTrue(it) }

    private fun seed(key: String, profile: JSONObject) {
        assertTrue(prefs.edit().putString(key, JSONArray().put(profile).toString()).commit())
    }

    private fun persisted(key: String): JSONObject =
        JSONArray(prefs.getString(key, null)).getJSONObject(0)

    private fun assertDisabled(block: () -> Unit) {
        try {
            block()
            fail("Legacy Custom-CA profile must require a verification choice")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun defaultCorrectionUnsafeConsentSurvivesStoreReadSaveRead() {
        reset()
        val store = ProfileStores(context)
        val initial = store.ntripCasterProfiles().single()
        store.saveNtripCasterProfiles(listOf(initial.copy(
            host = "caster.example", transportMode = NtripTransportMode.TLS,
            tlsVerification = NtripTlsVerification.Unsafe,
        )))
        assertFalse(store.ntripCasterProfiles().single().unsafeTlsAcknowledged)
        store.saveNtripCasterProfiles(listOf(store.ntripCasterProfiles().single().copy(unsafeTlsAcknowledged = true)))
        assertTrue(store.ntripCasterProfiles().single().unsafeTlsAcknowledged)
        assertTrue(ProfileStores(context).ntripCasterProfiles().single().unsafeTlsAcknowledged)
        assertTrue(persisted("ntripCasterProfiles").getBoolean("unsafeTlsAcknowledged"))
        store.saveNtripCasterProfiles(listOf(store.ntripCasterProfiles().single().copy(host = "other.example")))
        assertFalse(ProfileStores(context).ntripCasterProfiles().single().unsafeTlsAcknowledged)
    }

    @Test
    fun editorCanPersistFreshCorrectionConsentAfterSecurityEditButLaterEditClearsIt() {
        reset()
        val store = ProfileStores(context)
        val original = store.ntripCasterProfiles().single()
        val edited = original.copy(host = "caster.example", transportMode = NtripTransportMode.TLS,
            tlsVerification = NtripTlsVerification.Unsafe, unsafeTlsAcknowledged = true)
        store.saveNtripCasterProfiles(listOf(edited), freshlyAcknowledgedEditorProfileId = edited.id)
        val saved = ProfileStores(context).ntripCasterProfiles().single()
        assertTrue(saved.unsafeTlsAcknowledged)
        assertEquals(NtripTlsVerification.Unsafe, saved.toCore(true).verification)
        store.saveNtripCasterProfiles(listOf(saved.copy(host = "next.example")))
        assertFalse(ProfileStores(context).ntripCasterProfiles().single().unsafeTlsAcknowledged)
    }

    @Test
    fun editorCanPersistFreshUploadConsentAfterEndpointEditButLaterEditClearsIt() {
        reset()
        val store = ProfileStores(context)
        val original = store.ntripCasterUploadProfiles().single()
        val edited = original.copy(host = "upload.example", port = 2201,
            tlsVerification = NtripTlsVerification.Unsafe, unsafeTlsAcknowledged = true)
        store.saveNtripCasterUploadProfiles(listOf(edited), freshlyAcknowledgedEditorProfileId = edited.id)
        val saved = ProfileStores(context).ntripCasterUploadProfiles().single()
        assertTrue(saved.unsafeTlsAcknowledged)
        assertEquals(NtripTlsVerification.Unsafe, saved.toCore(true).verification)
        store.saveNtripCasterUploadProfiles(listOf(saved.copy(port = 2202)))
        assertFalse(ProfileStores(context).ntripCasterUploadProfiles().single().unsafeTlsAcknowledged)
    }

    @Test
    fun plaintextEditorSelectionCanBeSavedAsCoreValidPolicy() {
        reset()
        val store = ProfileStores(context)
        val original = store.ntripCasterProfiles().single()
        val edited = original.copy(host = "caster.example", transportMode = NtripTransportMode.PLAINTEXT,
            tlsVerification = NtripTlsVerification.SystemTrust, unsafeTlsAcknowledged = false)
        store.saveNtripCasterProfiles(listOf(edited))
        val saved = ProfileStores(context).ntripCasterProfiles().single()
        assertEquals(NtripTransportMode.PLAINTEXT, saved.toCore(true).transport)
        assertEquals(NtripTlsVerification.SystemTrust, saved.toCore(true).verification)
    }

    @Test
    fun defaultCorrectionCustomCaIsRewrittenAndRemainsDisabled() {
        reset()
        seed("ntripCasterProfiles", JSONObject().put("id", "ntrip-caster-default")
            .put("name", "NTRIP caster").put("host", "private.example")
            .put("tlsVerification", "CUSTOM_CA").put("customCaCertificateDer", "private-certificate-bytes")
            .put("unsafeTlsAcknowledged", false))
        val store = ProfileStores(context)
        val migrated = store.ntripCasterProfiles().single()
        assertFalse(persisted("ntripCasterProfiles").toString().contains("private-certificate-bytes"))
        assertTrue(migrated.requiresTlsVerificationChoice)
        assertDisabled { migrated.copy(unsafeTlsAcknowledged = true).toCore(true) }
        assertTrue(ProfileStores(context).ntripCasterProfiles().single().requiresTlsVerificationChoice)
        store.saveNtripCasterProfiles(listOf(migrated.copy(unsafeTlsAcknowledged = true)))
        assertFalse(store.ntripCasterProfiles().single().unsafeTlsAcknowledged)
        assertDisabled { store.ntripCasterProfiles().single().toCore(true) }
        val chosen = store.ntripCasterProfiles().single().chooseTlsVerification(NtripTlsVerification.SystemTrust)
        store.saveNtripCasterProfiles(listOf(chosen))
        assertFalse(store.ntripCasterProfiles().single().requiresTlsVerificationChoice)
        assertEquals(NtripTlsVerification.SystemTrust, store.ntripCasterProfiles().single().toCore(false).verification)
    }

    @Test
    fun uploadCustomCaMigrationAndFreshUnsafeConsentRoundTrip() {
        reset()
        seed("ntripCasterUploadProfiles", JSONObject().put("id", "upload")
            .put("name", "Upload").put("host", "private.example")
            .put("tlsVerification", "CUSTOM_CA").put("customCaCertificateDer", "private-certificate-bytes"))
        val store = ProfileStores(context)
        val migrated = store.ntripCasterUploadProfiles().single()
        assertFalse(persisted("ntripCasterUploadProfiles").toString().contains("private-certificate-bytes"))
        assertTrue(ProfileStores(context).ntripCasterUploadProfiles().single().requiresTlsVerificationChoice)
        store.saveNtripCasterUploadProfiles(listOf(migrated.copy(unsafeTlsAcknowledged = true)))
        assertFalse(store.ntripCasterUploadProfiles().single().unsafeTlsAcknowledged)
        assertDisabled { store.ntripCasterUploadProfiles().single().toCore(true) }
        store.saveNtripCasterUploadProfiles(listOf(store.ntripCasterUploadProfiles().single()
            .chooseTlsVerification(NtripTlsVerification.Unsafe)))
        assertFalse(store.ntripCasterUploadProfiles().single().unsafeTlsAcknowledged)
        store.saveNtripCasterUploadProfiles(listOf(store.ntripCasterUploadProfiles().single()
            .copy(unsafeTlsAcknowledged = true)))
        assertEquals(NtripTlsVerification.Unsafe,
            ProfileStores(context).ntripCasterUploadProfiles().single().toCore(true).verification)
        store.saveNtripCasterUploadProfiles(listOf(store.ntripCasterUploadProfiles().single().copy(port = 2201)))
        assertFalse(ProfileStores(context).ntripCasterUploadProfiles().single().unsafeTlsAcknowledged)
    }
}
