package org.rtkcollector.app.recording

import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.nio.file.StandardCopyOption
import org.json.JSONObject
import org.rtkcollector.core.rtklib.RtklibPostprocessBackend
import org.rtkcollector.core.rtklib.RtklibPostprocessMode
import org.rtkcollector.core.rtklib.RtklibPostprocessRequest
import org.rtkcollector.core.rtklib.RtklibPostprocessResult
import org.rtkcollector.core.rtklib.RtklibPreset
import org.rtkcollector.core.session.SessionArtifactFile

object RtklibSessionPostprocessor {
    fun postprocessFilesystemSession(
        sessionDirectory: Path,
        mode: RtklibPostprocessMode,
        backend: RtklibPostprocessBackend,
    ): RtklibPostprocessResult {
        if (!Files.isDirectory(sessionDirectory)) {
            return RtklibPostprocessResult.failed("RTKLIB postprocess requires a session directory: $sessionDirectory")
        }
        val receiverRxRaw = sessionDirectory.resolve(SessionArtifactFile.RECEIVER_RX_RAW.fileName)
        if (!Files.isRegularFile(receiverRxRaw) || Files.size(receiverRxRaw) <= 0L) {
            return RtklibPostprocessResult.failed("${SessionArtifactFile.RECEIVER_RX_RAW.fileName} is required for RTKLIB postprocessing.")
        }
        val correctionRtcm3 = sessionDirectory.resolve(SessionArtifactFile.CORRECTION_INPUT_RTCM3.fileName)
        if (!Files.isRegularFile(correctionRtcm3) || Files.size(correctionRtcm3) <= 0L) {
            return RtklibPostprocessResult.failed("${SessionArtifactFile.CORRECTION_INPUT_RTCM3.fileName} is required for RTKLIB postprocessing.")
        }

        val metadata = sessionMetadata(sessionDirectory).getOrElse { error ->
            return RtklibPostprocessResult.failed(error.message ?: "session.json could not be read.")
        }
        val roverFormat = metadata.roverFormat ?: roverFormatFor(metadata.receiverFamily)
            ?: return RtklibPostprocessResult.failed("Unsupported RTKLIB receiver family: ${metadata.receiverFamily ?: "unknown"}")
        val outputNmea = sessionDirectory.resolve(mode.outputNmeaFile.fileName)
        val outputPos = sessionDirectory.resolve(mode.outputPosFile.fileName)
        val temporaryNmea = sessionDirectory.resolve("${mode.outputNmeaFile.fileName}.tmp")
        val temporaryPos = sessionDirectory.resolve("${mode.outputPosFile.fileName}.tmp")
        Files.deleteIfExists(temporaryNmea)
        Files.deleteIfExists(temporaryPos)

        val result = runCatching {
            backend.postprocess(
                RtklibPostprocessRequest(
                    preset = metadata.preset,
                    roverFormat = roverFormat,
                    frequencyCount = metadata.frequencyCount,
                    mode = mode,
                    receiverRxRaw = receiverRxRaw,
                    correctionRtcm3 = correctionRtcm3,
                    outputNmea = temporaryNmea,
                    outputPos = temporaryPos,
                ),
            )
        }.getOrElse { error ->
            Files.deleteIfExists(temporaryNmea)
            Files.deleteIfExists(temporaryPos)
            return RtklibPostprocessResult.failed(error.message ?: "RTKLIB postprocess failed.")
        }
        if (!result.success) {
            Files.deleteIfExists(temporaryNmea)
            Files.deleteIfExists(temporaryPos)
            return result
        }
        if (!Files.isRegularFile(temporaryNmea) || Files.size(temporaryNmea) <= 0L) {
            Files.deleteIfExists(temporaryNmea)
            Files.deleteIfExists(temporaryPos)
            return RtklibPostprocessResult.failed("RTKLIB postprocess produced no NMEA output.")
        }
        if (!Files.isRegularFile(temporaryPos) || Files.size(temporaryPos) <= 0L) {
            Files.deleteIfExists(temporaryNmea)
            Files.deleteIfExists(temporaryPos)
            return RtklibPostprocessResult.failed("RTKLIB postprocess produced no POS output.")
        }
        return publishOutputPair(
            temporaryNmea = temporaryNmea,
            temporaryPos = temporaryPos,
            outputNmea = outputNmea,
            outputPos = outputPos,
        )
    }

    private fun publishOutputPair(
        temporaryNmea: Path,
        temporaryPos: Path,
        outputNmea: Path,
        outputPos: Path,
    ): RtklibPostprocessResult {
        val backupNmea = outputNmea.resolveSibling("${outputNmea.fileName}.bak")
        val backupPos = outputPos.resolveSibling("${outputPos.fileName}.bak")
        Files.deleteIfExists(backupNmea)
        Files.deleteIfExists(backupPos)
        val hadNmea = Files.exists(outputNmea)
        val hadPos = Files.exists(outputPos)
        var publishedNmea = false
        var publishedPos = false
        return runCatching {
            if (hadNmea) Files.move(outputNmea, backupNmea, StandardCopyOption.REPLACE_EXISTING)
            if (hadPos) Files.move(outputPos, backupPos, StandardCopyOption.REPLACE_EXISTING)
            Files.move(temporaryNmea, outputNmea, StandardCopyOption.REPLACE_EXISTING)
            publishedNmea = true
            Files.move(temporaryPos, outputPos, StandardCopyOption.REPLACE_EXISTING)
            publishedPos = true
            Files.deleteIfExists(backupNmea)
            Files.deleteIfExists(backupPos)
            RtklibPostprocessResult.success()
        }.getOrElse { error ->
            Files.deleteIfExists(temporaryNmea)
            Files.deleteIfExists(temporaryPos)
            if (hadNmea && Files.exists(backupNmea)) {
                Files.move(backupNmea, outputNmea, StandardCopyOption.REPLACE_EXISTING)
            } else if (publishedNmea) {
                Files.deleteIfExists(outputNmea)
            }
            if (hadPos && Files.exists(backupPos)) {
                Files.move(backupPos, outputPos, StandardCopyOption.REPLACE_EXISTING)
            } else if (publishedPos) {
                Files.deleteIfExists(outputPos)
            }
            RtklibPostprocessResult.failed(error.message ?: "RTKLIB postprocess outputs could not be published.")
        }
    }

    private fun sessionMetadata(sessionDirectory: Path): Result<SessionRtklibMetadata> = runCatching {
        val json = sessionDirectory.resolve(SessionArtifactFile.SESSION_JSON.fileName)
        if (!Files.isRegularFile(json)) return@runCatching SessionRtklibMetadata()
        val parsed = JSONObject(String(Files.readAllBytes(json), StandardCharsets.UTF_8))
        SessionRtklibMetadata(
            receiverFamily = parsed.optString("receiverDriverId").takeIf(String::isNotBlank),
            roverFormat = parseRoverFormat(parsed.optString("rtklibRoutePlan")),
            preset = parsed.optString("rtklibPreset").takeIf(String::isNotBlank)
                ?.let { runCatching { RtklibPreset.valueOf(it) }.getOrNull() }
                ?: RtklibPreset.ROVER_KINEMATIC_RTK,
            frequencyCount = parsed.optInt("rtklibFrequencyCount", 1).coerceIn(1, 3),
        )
    }.recoverCatching { error ->
        throw IllegalArgumentException("session.json could not be parsed for RTKLIB postprocessing: ${error.message}", error)
    }

    private fun parseRoverFormat(routePlan: String): String? {
        val start = routePlan.indexOf('(')
        val end = routePlan.indexOf(')', startIndex = start + 1)
        return if (start >= 0 && end > start) routePlan.substring(start + 1, end).takeIf(String::isNotBlank) else null
    }

    private fun roverFormatFor(receiverFamily: String?): String? =
        when {
            receiverFamily == null -> null
            receiverFamily.startsWith("ublox", ignoreCase = true) -> "UBX_RXM_RAWX_SFRBX"
            receiverFamily.contains("um980", ignoreCase = true) -> "UNICORE_OBSVMB"
            receiverFamily.contains("unicore", ignoreCase = true) -> "UNICORE_OBSVMB"
            else -> null
        }

    private data class SessionRtklibMetadata(
        val receiverFamily: String? = null,
        val roverFormat: String? = null,
        val preset: RtklibPreset = RtklibPreset.ROVER_KINEMATIC_RTK,
        val frequencyCount: Int = 1,
    )
}

private val RtklibPostprocessMode.outputNmeaFile: SessionArtifactFile
    get() = when (this) {
        RtklibPostprocessMode.FORWARD -> SessionArtifactFile.RTKLIB_POSTPROCESSED_FORWARD_NMEA
        RtklibPostprocessMode.FORWARD_BACKWARD -> SessionArtifactFile.RTKLIB_POSTPROCESSED_COMBINED_NMEA
    }

private val RtklibPostprocessMode.outputPosFile: SessionArtifactFile
    get() = when (this) {
        RtklibPostprocessMode.FORWARD -> SessionArtifactFile.RTKLIB_POSTPROCESSED_FORWARD_POS
        RtklibPostprocessMode.FORWARD_BACKWARD -> SessionArtifactFile.RTKLIB_POSTPROCESSED_COMBINED_POS
    }
