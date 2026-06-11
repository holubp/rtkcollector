package org.rtkcollector.receiver.unicore

object Um980ModeParser {
    fun configuredMode(script: String): String? =
        script
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull(::parseModeLine)
            .lastOrNull()

    private fun parseModeLine(line: String): String? {
        val tokens = line.split(Regex("\\s+"))
        if (tokens.isEmpty() || !tokens[0].equals("MODE", ignoreCase = true)) return null
        return tokens.drop(1).takeIf { it.isNotEmpty() }?.joinToString(" ") { it.uppercase() }
    }
}
