package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Um980RuntimeProfileTest {
    @Test
    fun safeProfileLinesPass() {
        val profile = Um980RuntimeProfile(
            id = "safe",
            displayName = "Safe",
            enabled = true,
            runtimeOnly = true,
            commands = listOf(
                "UNLOG COM1",
                "BESTNAVB COM1 0.2",
                "OBSVMCMPB COM1 1",
            ),
        )

        assertEquals(
            listOf(
                "UNLOG COM1\r\n",
                "BESTNAVB COM1 0.2\r\n",
                "OBSVMCMPB COM1 1\r\n",
            ),
            profile.renderExecutableCommands(),
        )
    }

    @Test
    fun persistentAndRiskyCommandsFail() {
        val riskyTerms = listOf(
            "SAVECONFIG",
            "SAVE",
            "RESET",
            "FRESET",
            "FLASH",
            "NVM",
            "USBMODE",
            "BOOT",
            "DEFAULT",
            "FACTORY",
            "ERASE",
            "FORMAT",
            "UPDATE",
            "UPGRADE",
            "PERMANENT",
        )

        riskyTerms.forEach { term ->
            val profile = Um980RuntimeProfile(
                id = term.lowercase(),
                displayName = term,
                enabled = true,
                runtimeOnly = true,
                commands = listOf("$term COM1"),
            )

            assertThrows(IllegalArgumentException::class.java, profile::renderExecutableCommands, term)
        }
    }

    @Test
    fun shellMetacharactersFail() {
        val metacharacters = listOf(";", "&", "|", "`", "$", "<", ">")

        metacharacters.forEach { metacharacter ->
            val profile = Um980RuntimeProfile(
                id = "metacharacter",
                displayName = "Metacharacter",
                enabled = true,
                runtimeOnly = true,
                commands = listOf("BESTNAVB COM1 1 $metacharacter"),
            )

            assertThrows(IllegalArgumentException::class.java, profile::renderExecutableCommands, metacharacter)
        }
    }

    @Test
    fun disabledProfilesAreNotExecutable() {
        val profile = Um980RuntimeProfile(
            id = "disabled",
            displayName = "Disabled",
            enabled = false,
            runtimeOnly = true,
            commands = listOf("BESTNAVB COM1 1"),
        )

        assertThrows(IllegalStateException::class.java, profile::renderExecutableCommands)
    }

    @Test
    fun experimentalProfileIsRuntimeOnlyAndContainsNoPersistentCommands() {
        val profile = Um980RuntimeProfiles.experimentalRoverBasePreparation()

        assertTrue(profile.enabled)
        assertTrue(profile.runtimeOnly)
        assertTrue(profile.commands.any { it == "UNLOG COM1" })
        assertTrue(profile.commands.any { it == "MODE ROVER" })
        assertTrue(profile.commands.any { it == "CONFIG MMP ENABLE" })
        assertTrue(profile.commands.any { it == "CONFIG PPP ENABLE E6-HAS" })
        assertFalse(profile.commands.any { it.matches(Regex("CONFIG COM[1-8] \\d+")) })
        assertTrue(profile.commands.any { it == "OBSVMCMPB COM1 0.25" })
        assertTrue(profile.commands.any { it == "BESTNAVB COM1 0.05" })
        assertTrue(profile.commands.any { it == "PPPNAVB COM1 1" })
        assertTrue(profile.commands.any { it == "STADOPB COM1 1" })
        assertFalse(profile.commands.any(Um980RuntimeCommandValidator::isPersistentOrRisky))
        profile.renderExecutableCommands()
    }
}
