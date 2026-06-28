package org.rtkcollector.app.ui.profiles

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.rtkcollector.app.profile.NtripMountpointOverride
import org.rtkcollector.app.profile.RecordingSettingsSet
import org.rtkcollector.app.profile.SettingsSetOverrides
import org.junit.jupiter.api.Test

class ProfileListModelsTest {
    @Test
    fun `protected profile row is viewable and copyable`() {
        val row = ProfileListRow(
            id = "built-in",
            name = "Built-in",
            isProtected = true,
            hasLocalOverrides = false,
        )

        assertFalse(row.canEdit)
        assertTrue(row.canViewDetails)
        assertEquals("View", row.editActionLabel)
        assertTrue(row.canCopy)
        assertFalse(row.canRename)
        assertFalse(row.canDelete)
        assertFalse(row.isSelected)
        assertEquals("Built-in", row.displayName)
        assertEquals("", row.summary)
    }

    @Test
    fun `editable profile row uses edit action label`() {
        val row = ProfileListRow(
            id = "custom",
            name = "Custom",
            isProtected = false,
            hasLocalOverrides = false,
        )

        assertTrue(row.canEdit)
        assertTrue(row.canViewDetails)
        assertEquals("Edit", row.editActionLabel)
    }

    @Test
    fun `settings set local override label is visible`() {
        val state = SettingsSetListState.from(
            settingsSets = listOf(
                RecordingSettingsSet.builtInRoverNtrip().copy(
                    overrides = SettingsSetOverrides(
                        ntripMountpoint = NtripMountpointOverride(mountpoint = "TUBO00CZE0"),
                    ),
                ),
            ),
            selectedId = "um980-rover-ntrip",
        )

        assertEquals("UM980 rover + NTRIP + local changes", state.rows.single().displayName)
        assertTrue(state.rows.single().summary.contains("rover-ntrip"))
        assertTrue(state.rows.single().summary.contains("TUBO00CZE0"))
        assertTrue(state.rows.single().isSelected)
        assertTrue(state.rows.single().canDelete)
    }

    @Test
    fun `profile rename text is trimmed and blank names cannot be saved`() {
        assertEquals("New name", profileRenameSaveName("  New name  "))
        assertTrue(canSaveProfileRename("Old name", "  New name  "))
        assertFalse(canSaveProfileRename("Old name", "   "))
        assertFalse(canSaveProfileRename("Old name", "Old name"))
    }

    @Test
    fun `protected override delete action is labelled as reset`() {
        val protectedOverrideRow = ProfileListRow(
            id = "built-in",
            name = "Built-in",
            isProtected = true,
            hasLocalOverrides = true,
        )
        val editableRow = ProfileListRow(
            id = "custom",
            name = "Custom",
            isProtected = false,
            hasLocalOverrides = false,
        )

        assertEquals("Reset local overrides", profileDeleteActionLabel(protectedOverrideRow))
        assertEquals("Delete", profileDeleteActionLabel(editableRow))
    }

    @Test
    fun `editable profile field can expose fixed options`() {
        val field = EditableProfileField(
            key = "serialBaud",
            label = "Host serial baud",
            value = "230400",
            options = listOf("115200", "230400", "921600"),
        )

        assertEquals(listOf("115200", "230400", "921600"), field.options)
        assertEquals("230400", field.optionItems[1].value)
        assertEquals("230400", field.optionItems[1].label)
    }

    @Test
    fun `editable profile field can expose labelled options and readonly list`() {
        val selector = EditableProfileField(
            key = "commandProfileId",
            label = "Command profile",
            value = "um980-binary-multihz",
            optionItems = listOf(
                EditableProfileOption("um980-binary-multihz", "UM980 multi-Hz binary RTK+PPP"),
                EditableProfileOption("um980-ascii-ppp-nmea", "UM980 multi-Hz ASCII RTK+PPP"),
            ),
        )
        val list = EditableProfileField(
            key = "sourcetableMountpoints",
            label = "Known mountpoints",
            value = "",
            readOnlyList = listOf("TUBO00CZE0", "GOPE00CZE0"),
        )

        assertEquals("UM980 multi-Hz binary RTK+PPP", selector.optionItems.first().label)
        assertEquals(listOf("TUBO00CZE0", "GOPE00CZE0"), list.readOnlyList)
    }

    @Test
    fun `editable profile field can expose validation error`() {
        val field = EditableProfileField(
            key = "mountpoint",
            label = "Mountpoint",
            value = "OLD",
            errorText = "Mountpoint is not in the selected caster sourcetable.",
        )

        assertTrue(field.hasError)
        assertEquals("Mountpoint is not in the selected caster sourcetable.", field.errorText)
    }

    @Test
    fun `editable mountpoint field revalidates against selected caster options`() {
        val field = EditableProfileField(
            key = "mountpoint",
            label = "Mountpoint",
            value = "TUBO00CZE0",
            optionGroups = mapOf(
                "caster-a" to listOf(EditableProfileOption("TUBO00CZE0", "TUBO00CZE0")),
                "caster-b" to listOf(EditableProfileOption("GOPE00CZE0", "GOPE00CZE0")),
            ),
        )

        val valid = field.withRuntimeProfileValidation(
            mapOf("casterProfileId" to "caster-a", "mountpoint" to "TUBO00CZE0"),
        )
        val invalid = field.withRuntimeProfileValidation(
            mapOf("casterProfileId" to "caster-b", "mountpoint" to "TUBO00CZE0"),
        )

        assertFalse(valid.hasError)
        assertTrue(invalid.hasError)
        assertEquals(listOf("GOPE00CZE0"), invalid.optionItems.map { it.value })
    }

    @Test
    fun `ntrip source upload username is preserved but disabled for v1`() {
        val field = EditableProfileField(
            key = "username",
            label = "Username",
            value = "base01",
            sourceUploadUsername = true,
        )

        val v1 = field.withRuntimeProfileValidation(
            mapOf("protocolPolicy" to "NTRIP_V1_ONLY", "username" to "base01"),
        )
        val v2 = field.withRuntimeProfileValidation(
            mapOf("protocolPolicy" to "NTRIP_V2_ONLY", "username" to "base01"),
        )

        assertEquals("base01", v1.value)
        assertTrue(v1.readOnly)
        assertTrue(v1.label.contains("not used", ignoreCase = true))
        assertTrue(v1.helperText.orEmpty().contains("not sent", ignoreCase = true))
        assertEquals("base01", v2.value)
        assertFalse(v2.readOnly)
        assertEquals("Username", v2.label)
    }

    @Test
    fun `ntrip correction download username remains editable for v1`() {
        val field = EditableProfileField(
            key = "username",
            label = "Username",
            value = "rover",
        )

        val rendered = field.withRuntimeProfileValidation(
            mapOf("protocolPolicy" to "NTRIP_V1_ONLY", "username" to "rover"),
        )

        assertEquals("rover", rendered.value)
        assertFalse(rendered.readOnly)
        assertEquals("Username", rendered.label)
        assertEquals(null, rendered.helperText)
    }

    @Test
    fun `rtk2go safety state follows unsaved host value`() {
        val field = EditableProfileField(
            key = "safetyRulesEnabled",
            label = "RTK2go safety rules",
            value = "false",
            boolean = true,
            casterUploadSafety = true,
        )

        val forced = field.withRuntimeProfileValidation(
            mapOf("host" to "rtk2go.com", "safetyRulesEnabled" to "false"),
        )
        val manual = field.withRuntimeProfileValidation(
            mapOf("host" to "private.example.org", "safetyRulesEnabled" to "false"),
        )

        assertEquals("true", forced.value)
        assertTrue(forced.readOnly)
        assertTrue(forced.label.contains("required", ignoreCase = true))
        assertTrue(forced.helperText.orEmpty().contains("enforced", ignoreCase = true))
        assertEquals("false", manual.value)
        assertFalse(manual.readOnly)
        assertEquals("RTK2go safety rules", manual.label)
    }

    @Test
    fun `profile list row can expose warning state`() {
        val row = ProfileListRow(
            id = "mount",
            name = "Mount",
            isProtected = false,
            hasLocalOverrides = false,
            warningText = SuspectInvalidMountpointWarning,
        )

        assertTrue(row.hasWarning)
        assertEquals(SuspectInvalidMountpointWarning, row.warningText)
    }

    @Test
    fun `profile editor cannot save while field has validation error`() {
        val fields = listOf(
            EditableProfileField(
                key = "mountpoint",
                label = "Mountpoint",
                value = "OLD",
                errorText = "Mountpoint is not in the selected caster sourcetable.",
            ),
        )

        assertFalse(canSaveProfileEditor(fields))
    }

    @Test
    fun `profile editor actions are non destructive by default`() {
        val action = ProfileEditorAction("Refresh") {}

        assertFalse(action.destructive)
    }
}
