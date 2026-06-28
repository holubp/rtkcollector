package org.rtkcollector.app.base

data class FixedBaseMaterializationResult(
    val runtimeScript: String,
    val replacedLine: String?,
)

object FixedBaseProfileMaterializer {
    fun materialize(
        runtimeScript: String,
        modeBaseCommand: String,
    ): FixedBaseMaterializationResult {
        require(modeBaseCommand.startsWith("MODE BASE ")) {
            "Fixed-base materialization requires a MODE BASE command."
        }
        val lines = if (runtimeScript.isEmpty()) mutableListOf() else runtimeScript.lineSequence().toMutableList()
        val modeIndex = lines.indexOfFirst { it.trimStart().startsWith("MODE BASE ", ignoreCase = true) }
        if (modeIndex >= 0) {
            val replacedLine = lines[modeIndex]
            lines[modeIndex] = modeBaseCommand
            return FixedBaseMaterializationResult(
                runtimeScript = lines.joinToString("\n"),
                replacedLine = replacedLine,
            )
        }
        val unlogIndex = lines.indexOfFirst { it.trim().equals("UNLOG COM1", ignoreCase = true) }
        val insertIndex = if (unlogIndex >= 0) unlogIndex + 1 else 0
        lines.add(insertIndex, modeBaseCommand)
        return FixedBaseMaterializationResult(
            runtimeScript = lines.joinToString("\n"),
            replacedLine = null,
        )
    }
}
