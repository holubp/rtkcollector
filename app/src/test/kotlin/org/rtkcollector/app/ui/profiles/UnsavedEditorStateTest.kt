package org.rtkcollector.app.ui.profiles

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnsavedEditorStateTest {
    @Test
    fun `unchanged editor can leave immediately`() {
        val state = UnsavedEditorState(savedFingerprint = "a", currentFingerprint = "a")

        assertFalse(state.hasUnsavedChanges)
        assertTrue(state.canLeaveWithoutPrompt)
    }

    @Test
    fun `changed editor requires prompt`() {
        val state = UnsavedEditorState(savedFingerprint = "a", currentFingerprint = "b")

        assertTrue(state.hasUnsavedChanges)
        assertFalse(state.canLeaveWithoutPrompt)
    }

    @Test
    fun `editor fingerprint distinguishes embedded delimiters`() {
        val first = profileEditorFingerprint(mapOf("a" to "b=1:c;"))
        val second = profileEditorFingerprint(mapOf("a=b" to "1:c;"))

        assertNotEquals(first, second)
    }
}
