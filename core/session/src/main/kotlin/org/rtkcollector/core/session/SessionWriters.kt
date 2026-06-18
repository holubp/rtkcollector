package org.rtkcollector.core.session

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

class SessionWriters private constructor(
    private val sessionDirectory: Path,
    private val receiverRx: OutputStream,
    private val txToReceiver: OutputStream,
    private val correctionInput: OutputStream,
    private val correctionInputRtcm3: OutputStream?,
    private val events: OutputStream,
    private val qualityLive: OutputStream,
    private val receiverSolutionNmea: OutputStream,
    private val receiverSolution: OutputStream,
    private val receiverPppSolution: OutputStream,
    private val extractedRtcm: OutputStream,
) : Closeable {
    fun writeSessionJson(json: String) {
        writeAtomicText(
            fileName = SessionArtifactFile.SESSION_JSON.fileName,
            json.trimEnd() + "\n",
        )
    }

    fun appendReceiverRx(bytes: ByteArray) {
        receiverRx.write(bytes)
    }

    fun appendTxToReceiver(bytes: ByteArray) {
        txToReceiver.write(bytes)
    }

    fun appendCorrectionInput(bytes: ByteArray) {
        correctionInput.write(bytes)
        correctionInputRtcm3.writeBestEffort(bytes)
    }

    fun appendEventJson(json: String) {
        events.writeJsonLine(json)
    }

    fun appendQualityLiveJson(json: String) {
        qualityLive.writeJsonLine(json)
    }

    fun appendReceiverSolutionNmea(sentence: String) {
        receiverSolutionNmea.write(sentence.toByteArray(StandardCharsets.US_ASCII))
    }

    fun appendReceiverSolutionJson(json: String) {
        receiverSolution.writeJsonLine(json)
    }

    fun appendReceiverPppSolutionJson(json: String) {
        receiverPppSolution.writeJsonLine(json)
    }

    fun appendExtractedRtcm(bytes: ByteArray) {
        extractedRtcm.write(bytes)
    }

    fun writeBasePositionJson(json: String) {
        writeUtf8Text(
            sessionDirectory.resolve(SessionArtifactFile.BASE_POSITION_JSON.fileName),
            json.trimEnd() + "\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
    }

    fun flush() {
        receiverRx.flush()
        txToReceiver.flush()
        correctionInput.flush()
        correctionInputRtcm3.flushBestEffort()
        events.flush()
        qualityLive.flush()
        receiverSolutionNmea.flush()
        receiverSolution.flush()
        receiverPppSolution.flush()
        extractedRtcm.flush()
    }

    override fun close() {
        receiverRx.close()
        txToReceiver.close()
        correctionInput.close()
        correctionInputRtcm3.closeBestEffort()
        events.close()
        qualityLive.close()
        receiverSolutionNmea.close()
        receiverSolution.close()
        receiverPppSolution.close()
        extractedRtcm.close()
    }

    companion object {
        fun openNew(sessionDirectory: Path): SessionWriters {
            requireNewSessionDirectory(sessionDirectory)
            return openAppendForRecovery(sessionDirectory)
        }

        fun openAppendForRecovery(sessionDirectory: Path): SessionWriters {
            Files.createDirectories(sessionDirectory)
            return SessionWriters(
                sessionDirectory = sessionDirectory,
                receiverRx = sessionDirectory.appendStream(SessionArtifactFile.RECEIVER_RX_RAW.fileName),
                txToReceiver = sessionDirectory.appendStream(SessionArtifactFile.TX_TO_RECEIVER_RAW.fileName),
                correctionInput = sessionDirectory.appendStream(SessionArtifactFile.CORRECTION_INPUT_RAW.fileName),
                correctionInputRtcm3 = sessionDirectory.tryAppendStream(SessionArtifactFile.CORRECTION_INPUT_RTCM3.fileName),
                events = sessionDirectory.appendStream(SessionArtifactFile.EVENTS_JSONL.fileName),
                qualityLive = sessionDirectory.appendStream(SessionArtifactFile.QUALITY_LIVE_JSONL.fileName),
                receiverSolutionNmea = sessionDirectory.appendStream(SessionArtifactFile.RECEIVER_SOLUTION_NMEA.fileName),
                receiverSolution = sessionDirectory.appendStream(SessionArtifactFile.RECEIVER_SOLUTION_JSONL.fileName),
                receiverPppSolution = sessionDirectory.appendStream(SessionArtifactFile.RECEIVER_PPP_SOLUTION_JSONL.fileName),
                extractedRtcm = sessionDirectory.appendStream(SessionArtifactFile.RTCM_EXTRACTED_RTCM3.fileName),
            )
        }

        fun open(sessionDirectory: Path): SessionWriters = openNew(sessionDirectory)

        private fun requireNewSessionDirectory(sessionDirectory: Path) {
            if (Files.exists(sessionDirectory) && Files.list(sessionDirectory).use { it.findAny().isPresent }) {
                error("Refusing to open non-empty session directory for a new recording: $sessionDirectory")
            }
        }
    }

    private fun writeAtomicText(fileName: String, text: String) {
        val target = sessionDirectory.resolve(fileName)
        val temporary = sessionDirectory.resolve("$fileName.tmp")
        writeUtf8Text(
            temporary,
            text,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
        runCatching {
            Files.move(
                temporary,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(
                temporary,
                target,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}

private fun writeUtf8Text(path: Path, text: String, vararg options: StandardOpenOption) {
    Files.newOutputStream(path, StandardOpenOption.WRITE, *options).use { output ->
        output.write(text.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }
}

fun exportSessionMetadata(metadata: SessionMetadata): String {
    return buildString {
        append("{")
        appendJsonField("appVersion", metadata.appVersion)
        appendJsonField("androidDeviceModel", metadata.androidDeviceModel)
        appendJsonField("androidVersion", metadata.androidVersion)
        appendJsonField("receiverDriverId", metadata.receiverDriverId)
        appendJsonField("usbVid", metadata.usbVid)
        appendJsonField("usbPid", metadata.usbPid)
        appendJsonField("baudRate", metadata.baudRate)
        appendJsonField("mode", metadata.mode.name)
        appendJsonField("startedAt", metadata.startedAt)
        appendJsonField("stoppedAt", metadata.stoppedAt)
        appendJsonField("sessionUuid", metadata.sessionUuid)
        appendJsonField("linkedBaseSessionUuid", metadata.linkedBaseSessionUuid)
        appendJsonField("workflowId", metadata.workflowId)
        appendJsonField("workflowName", metadata.workflowName)
        appendJsonField("receiverRole", metadata.receiverRole)
        appendJsonField("um980ProfileId", metadata.um980ProfileId)
        appendJsonField("commandProfileId", metadata.commandProfileId)
        appendJsonField("usbBaudProfileId", metadata.usbBaudProfileId)
        appendJsonField("ntripCasterProfileId", metadata.ntripCasterProfileId)
        appendJsonField("ntripMountpointProfileId", metadata.ntripMountpointProfileId)
        appendJsonField("recordingPolicyId", metadata.recordingPolicyId)
        appendJsonField("storageProfileId", metadata.storageProfileId)
        appendJsonField("storageKind", metadata.storageKind)
        appendJsonField("coordinateSource", metadata.coordinateSource)
        appendJsonField("baseCoordinateId", metadata.baseCoordinateId)
        appendJsonField("baseCoordinateName", metadata.baseCoordinateName)
        appendJsonField("baseCoordinateMethod", metadata.baseCoordinateMethod)
        appendJsonField("validationSummary", metadata.validationSummary)
        appendJsonArrayField("expectedArtifacts", metadata.expectedArtifacts)
        metadata.ntrip?.let { appendNtripMetadata(it) }
        append("}")
    }
}

private fun Path.appendStream(fileName: String): OutputStream {
    return BufferedOutputStream(
        Files.newOutputStream(
            resolve(fileName),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        ),
    )
}

private fun Path.tryAppendStream(fileName: String): OutputStream? =
    runCatching { appendStream(fileName) }.getOrNull()

private fun OutputStream?.writeBestEffort(bytes: ByteArray) {
    this ?: return
    runCatching { write(bytes) }
}

private fun OutputStream?.flushBestEffort() {
    this ?: return
    runCatching { flush() }
}

private fun OutputStream?.closeBestEffort() {
    this ?: return
    runCatching { close() }
}

private fun OutputStream.writeJsonLine(json: String) {
    write(json.toByteArray(StandardCharsets.UTF_8))
    write('\n'.code)
}

private fun StringBuilder.appendJsonField(name: String, value: String?) {
    appendSeparatorIfNeeded()
    appendQuoted(name)
    append(":")
    if (value == null) {
        append("null")
    } else {
        appendQuoted(value)
    }
}

private fun StringBuilder.appendJsonField(name: String, value: Int?) {
    appendSeparatorIfNeeded()
    appendQuoted(name)
    append(":")
    if (value == null) {
        append("null")
    } else {
        append(value)
    }
}

private fun StringBuilder.appendJsonField(name: String, value: Boolean) {
    appendSeparatorIfNeeded()
    appendQuoted(name)
    append(":")
    append(value)
}

private fun StringBuilder.appendNtripMetadata(ntrip: NtripSessionMetadata) {
    appendSeparatorIfNeeded()
    appendQuoted("ntrip")
    append(":{")
    appendJsonField("casterHost", ntrip.casterHost)
    appendJsonField("casterPort", ntrip.casterPort)
    appendJsonField("mountpoint", ntrip.mountpoint)
    appendJsonField("usernamePresent", ntrip.usernamePresent)
    appendJsonField("ggaUploadEnabled", ntrip.ggaUploadEnabled)
    appendJsonField("secretRef", ntrip.secretRef)
    appendJsonField("protocol", ntrip.protocol)
    appendJsonField("finalStatus", ntrip.finalStatus)
    append("}")
}

private fun StringBuilder.appendJsonArrayField(name: String, values: List<String>) {
    appendSeparatorIfNeeded()
    appendQuoted(name)
    append(":[")
    values.forEachIndexed { index, value ->
        if (index > 0) {
            append(",")
        }
        appendQuoted(value)
    }
    append("]")
}

private fun StringBuilder.appendSeparatorIfNeeded() {
    if (isNotEmpty() && last() != '{') {
        append(",")
    }
}

private fun StringBuilder.appendQuoted(value: String) {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}
