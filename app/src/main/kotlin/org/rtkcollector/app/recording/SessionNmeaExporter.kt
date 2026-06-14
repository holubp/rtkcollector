package org.rtkcollector.app.recording

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val RECEIVER_SOLUTION_NMEA = "receiver-solution.nmea"

data class SessionNmeaSharePlan(
    val sourceNmea: Path,
    val outputNmea: Path,
) {
    init {
        require(sourceNmea.fileName.toString() == RECEIVER_SOLUTION_NMEA) {
            "NMEA share source must be receiver-solution.nmea."
        }
        require(outputNmea.fileName.toString().endsWith(".nmea")) {
            "NMEA share output must use .nmea suffix."
        }
    }

    companion object {
        fun fromSessionDirectory(sessionDirectory: Path, outputDirectory: Path): SessionNmeaSharePlan {
            require(Files.isDirectory(sessionDirectory)) { "NMEA share source must be a session directory." }
            val source = sessionDirectory.resolve(RECEIVER_SOLUTION_NMEA)
            require(Files.isRegularFile(source)) { "Session has no receiver-solution.nmea." }
            return SessionNmeaSharePlan(
                sourceNmea = source,
                outputNmea = outputDirectory.resolve("${sessionDirectory.fileName}.nmea"),
            )
        }
    }
}

data class SessionNmeaShareSelection(
    val plans: List<SessionNmeaSharePlan>,
    val skippedCount: Int,
) {
    val hasShareableNmea: Boolean
        get() = plans.isNotEmpty()

    companion object {
        fun fromSessionDirectories(
            sessionDirectories: List<Path>,
            outputDirectory: Path,
        ): SessionNmeaShareSelection {
            val plans = mutableListOf<SessionNmeaSharePlan>()
            var skipped = 0
            sessionDirectories.forEach { session ->
                val plan = runCatching {
                    SessionNmeaSharePlan.fromSessionDirectory(session, outputDirectory)
                }.getOrNull()
                if (plan == null) {
                    skipped++
                } else {
                    plans += plan
                }
            }
            return SessionNmeaShareSelection(plans = plans, skippedCount = skipped)
        }
    }
}

object SessionNmeaExporter {
    fun export(plan: SessionNmeaSharePlan): Path {
        plan.outputNmea.parent?.let(Files::createDirectories)
        Files.copy(plan.sourceNmea, plan.outputNmea, StandardCopyOption.REPLACE_EXISTING)
        return plan.outputNmea
    }
}
