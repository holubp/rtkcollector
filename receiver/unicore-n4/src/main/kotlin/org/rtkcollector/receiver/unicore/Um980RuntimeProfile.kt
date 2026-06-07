package org.rtkcollector.receiver.unicore

data class Um980RuntimeProfile(
    val id: String,
    val displayName: String,
    val enabled: Boolean,
    val runtimeOnly: Boolean,
    val commands: List<String>,
) {
    fun renderExecutableCommands(): List<String> {
        check(enabled) { "UM980 runtime profile '$id' is disabled." }
        return commands.map { command ->
            val normalized = command.trim()
            Um980RuntimeCommandValidator.validateRuntimeCommand(normalized)
            "$normalized\r\n"
        }
    }
}

object Um980RuntimeCommandValidator {
    private val riskyCommandPattern = Regex(
        pattern = """(?i)\b(SAVECONFIG|SAVE|RESET|FRESET|RESTORE|FLASH|NVM|USBMODE|DEFAULT|FACTORY|UPDATE|UPDATEAPP|UPGRADE|FORMAT|ERASE|BOOT|AUTH|PERMANENT)\b""",
    )

    private val shellMetacharacters = setOf(';', '&', '|', '`', '$', '<', '>')
    private val allowedRuntimeCommand = Regex("[A-Za-z0-9_.,:+/ -]+")

    fun validateRuntimeCommand(command: String) {
        require(command.isNotBlank()) { "UM980 runtime command must not be blank." }
        require(command.none { it == '\r' || it == '\n' }) {
            "UM980 runtime command must be a single line."
        }
        require(command.none(shellMetacharacters::contains)) {
            "UM980 runtime command contains a shell metacharacter."
        }
        require(allowedRuntimeCommand.matches(command)) {
            "UM980 runtime command contains unsupported characters."
        }
        require(!isPersistentOrRisky(command)) {
            "UM980 runtime command contains a persistent or risky receiver operation."
        }
    }

    fun isPersistentOrRisky(command: String): Boolean {
        return riskyCommandPattern.containsMatchIn(command)
    }
}
