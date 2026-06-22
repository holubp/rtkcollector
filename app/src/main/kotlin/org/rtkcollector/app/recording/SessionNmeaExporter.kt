package org.rtkcollector.app.recording

import org.rtkcollector.core.session.SessionArtifactFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

enum class SessionNmeaSource(
    val artifactFileName: String,
    val exportSuffix: String,
    val displayName: String,
) {
    RECEIVER_SOLUTION(
        artifactFileName = SessionArtifactFile.RECEIVER_SOLUTION_NMEA.fileName,
        exportSuffix = "receiver-solution",
        displayName = "Receiver solution",
    ),
    RTKLIB_REALTIME(
        artifactFileName = SessionArtifactFile.RTKLIB_SOLUTION_NMEA.fileName,
        exportSuffix = "rtklib-realtime",
        displayName = "RTKLIB real-time",
    ),
    RTKLIB_POSTPROCESSED_FORWARD(
        artifactFileName = SessionArtifactFile.RTKLIB_POSTPROCESSED_FORWARD_NMEA.fileName,
        exportSuffix = "rtklib-postprocessed-forward",
        displayName = "RTKLIB postprocessed forward",
    ),
    RTKLIB_POSTPROCESSED_COMBINED(
        artifactFileName = SessionArtifactFile.RTKLIB_POSTPROCESSED_COMBINED_NMEA.fileName,
        exportSuffix = "rtklib-postprocessed-combined",
        displayName = "RTKLIB postprocessed forward/backward",
    ),
}

data class SessionNmeaSharePlan(
    val source: SessionNmeaSource,
    val sourceNmea: Path,
    val outputNmea: Path,
) {
    init {
        require(sourceNmea.fileName.toString() == source.artifactFileName) {
            "NMEA share source ${source.displayName} must use ${source.artifactFileName}."
        }
        require(outputNmea.fileName.toString().endsWith(".nmea")) {
            "NMEA share output must use .nmea suffix."
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
            requestedSources: Set<SessionNmeaSource>? = null,
        ): SessionNmeaShareSelection {
            val availableBySession = sessionDirectories.map { session ->
                session to availableSources(session, requestedSources)
            }
            val receiverOnlyAcrossSelection = availableBySession
                .filter { (_, sources) -> sources.isNotEmpty() }
                .all { (_, sources) -> sources == listOf(SessionNmeaSource.RECEIVER_SOLUTION) }
            val plans = mutableListOf<SessionNmeaSharePlan>()
            var skipped = 0
            availableBySession.forEach { (session, sources) ->
                if (sources.isEmpty()) {
                    skipped++
                } else {
                    sources.forEach { source ->
                        val outputName = exportName(
                            sessionName = session.fileName.toString(),
                            source = source,
                            useLegacyReceiverName = receiverOnlyAcrossSelection && source == SessionNmeaSource.RECEIVER_SOLUTION,
                        )
                        plans += SessionNmeaSharePlan(
                            source = source,
                            sourceNmea = session.resolve(source.artifactFileName),
                            outputNmea = outputDirectory.resolve(outputName),
                        )
                    }
                }
            }
            return SessionNmeaShareSelection(plans = plans, skippedCount = skipped)
        }

        fun availableSources(
            sessionDirectory: Path,
            requestedSources: Set<SessionNmeaSource>? = null,
        ): List<SessionNmeaSource> {
            require(Files.isDirectory(sessionDirectory)) { "NMEA share source must be a session directory." }
            val allowed = requestedSources ?: SessionNmeaSource.entries.toSet()
            return SessionNmeaSource.entries.filter { source ->
                source in allowed &&
                    sessionDirectory.resolve(source.artifactFileName).let { Files.isRegularFile(it) && Files.size(it) > 0L }
            }
        }

        fun exportName(
            sessionName: String,
            source: SessionNmeaSource,
            useLegacyReceiverName: Boolean,
        ): String =
            if (useLegacyReceiverName) {
                "$sessionName.nmea"
            } else {
                "$sessionName-${source.exportSuffix}.nmea"
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
