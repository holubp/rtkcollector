package org.rtkcollector.core.session

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
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
    private val baseCasterUploadRtcm3: OutputStream,
    private val events: OutputStream,
    private val qualityLive: OutputStream,
    private val receiverSolutionNmea: OutputStream,
    private val receiverSolution: OutputStream,
    private val receiverPppSolution: OutputStream,
    private val rtklibSolutionNmea: OutputStream,
    private val rtklibSolutionPos: OutputStream,
    private val rtklibStatus: OutputStream,
    private val extractedRtcm: OutputStream,
) : Closeable {
    private var finalCloseReport: SessionWriterCloseReport? = null

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

    fun appendBaseCasterUploadRtcm(bytes: ByteArray) {
        baseCasterUploadRtcm3.write(bytes)
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

    fun appendRtklibSolutionNmea(sentence: String) {
        rtklibSolutionNmea.write(sentence.toByteArray(StandardCharsets.US_ASCII))
    }

    fun appendRtklibSolutionPos(line: String) {
        rtklibSolutionPos.write(line.toByteArray(StandardCharsets.US_ASCII))
    }

    fun appendRtklibStatusJson(json: String) {
        rtklibStatus.writeJsonLine(json)
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
        baseCasterUploadRtcm3.flush()
        events.flush()
        qualityLive.flush()
        receiverSolutionNmea.flush()
        receiverSolution.flush()
        receiverPppSolution.flush()
        rtklibSolutionNmea.flush()
        rtklibSolutionPos.flush()
        rtklibStatus.flush()
        extractedRtcm.flush()
    }

    fun flushReceiverRx() {
        receiverRx.flush()
    }

    @Synchronized
    fun closeAll(): SessionWriterCloseReport {
        finalCloseReport?.let { return it }
        val issues = mutableListOf<SessionWriterIssue>()
        writerTargets().forEach { target ->
            finaliseStream(target, issues)
        }
        return SessionWriterCloseReport(issues).also { finalCloseReport = it }
    }

    override fun close() {
        val report = closeAll()
        if (report.issues.isNotEmpty()) {
            throw IOException(report.userMessage ?: "Session writer close failed")
        }
    }

    companion object {
        fun openNew(sessionDirectory: Path): SessionWriters {
            requireNewSessionDirectory(sessionDirectory)
            return openAppendForRecovery(sessionDirectory)
        }

        fun openAppendForRecovery(sessionDirectory: Path): SessionWriters {
            return openAppendForRecovery(sessionDirectory) { path -> path.appendStream() }
        }

        internal fun openAppendForRecovery(
            sessionDirectory: Path,
            streamFactory: (Path) -> OutputStream,
        ): SessionWriters {
            Files.createDirectories(sessionDirectory)
            val openedStreams = mutableListOf<OpenedSessionWriterTarget>()
            fun openRequired(artifact: SessionArtifactFile): OutputStream =
                streamFactory(sessionDirectory.resolve(artifact.fileName)).also { stream ->
                    openedStreams += OpenedSessionWriterTarget(artifact, stream)
                }
            fun openOptional(artifact: SessionArtifactFile): OutputStream? =
                runCatching { openRequired(artifact) }.getOrNull()

            return try {
                SessionWriters(
                    sessionDirectory = sessionDirectory,
                    receiverRx = openRequired(SessionArtifactFile.RECEIVER_RX_RAW),
                    txToReceiver = openRequired(SessionArtifactFile.TX_TO_RECEIVER_RAW),
                    correctionInput = openRequired(SessionArtifactFile.CORRECTION_INPUT_RAW),
                    correctionInputRtcm3 = openOptional(SessionArtifactFile.CORRECTION_INPUT_RTCM3),
                    baseCasterUploadRtcm3 = openRequired(SessionArtifactFile.BASE_CASTER_UPLOAD_RTCM3),
                    events = openRequired(SessionArtifactFile.EVENTS_JSONL),
                    qualityLive = openRequired(SessionArtifactFile.QUALITY_LIVE_JSONL),
                    receiverSolutionNmea = openRequired(SessionArtifactFile.RECEIVER_SOLUTION_NMEA),
                    receiverSolution = openRequired(SessionArtifactFile.RECEIVER_SOLUTION_JSONL),
                    receiverPppSolution = openRequired(SessionArtifactFile.RECEIVER_PPP_SOLUTION_JSONL),
                    rtklibSolutionNmea = openRequired(SessionArtifactFile.RTKLIB_SOLUTION_NMEA),
                    rtklibSolutionPos = openRequired(SessionArtifactFile.RTKLIB_SOLUTION_POS),
                    rtklibStatus = openRequired(SessionArtifactFile.RTKLIB_STATUS_JSONL),
                    extractedRtcm = openRequired(SessionArtifactFile.RTCM_EXTRACTED_RTCM3),
                )
            } catch (failure: Throwable) {
                openedStreams.forEach { target ->
                    finaliseOpenedStream(target, failure)
                }
                throw failure
            }
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

    private fun writerTargets(): List<SessionWriterTarget> = buildList {
        add(SessionWriterTarget(receiverRx, SessionArtifactFile.RECEIVER_RX_RAW, SessionWriterIssueCategory.RAW_RX, SessionWriterIssueSeverity.FATAL))
        add(SessionWriterTarget(txToReceiver, SessionArtifactFile.TX_TO_RECEIVER_RAW, SessionWriterIssueCategory.BINARY_SIDECAR, SessionWriterIssueSeverity.DEGRADED))
        add(SessionWriterTarget(correctionInput, SessionArtifactFile.CORRECTION_INPUT_RAW, SessionWriterIssueCategory.BINARY_SIDECAR, SessionWriterIssueSeverity.DEGRADED))
        correctionInputRtcm3?.let {
            add(SessionWriterTarget(it, SessionArtifactFile.CORRECTION_INPUT_RTCM3, SessionWriterIssueCategory.BINARY_SIDECAR, SessionWriterIssueSeverity.DEGRADED))
        }
        add(SessionWriterTarget(baseCasterUploadRtcm3, SessionArtifactFile.BASE_CASTER_UPLOAD_RTCM3, SessionWriterIssueCategory.BINARY_SIDECAR, SessionWriterIssueSeverity.DEGRADED))
        add(SessionWriterTarget(extractedRtcm, SessionArtifactFile.RTCM_EXTRACTED_RTCM3, SessionWriterIssueCategory.BINARY_SIDECAR, SessionWriterIssueSeverity.DEGRADED))
        add(SessionWriterTarget(events, SessionArtifactFile.EVENTS_JSONL, SessionWriterIssueCategory.STRUCTURED_SIDECAR, SessionWriterIssueSeverity.DEGRADED))
        add(SessionWriterTarget(qualityLive, SessionArtifactFile.QUALITY_LIVE_JSONL, SessionWriterIssueCategory.STRUCTURED_SIDECAR, SessionWriterIssueSeverity.DEGRADED))
        add(SessionWriterTarget(receiverSolutionNmea, SessionArtifactFile.RECEIVER_SOLUTION_NMEA, SessionWriterIssueCategory.LINE_SIDECAR, SessionWriterIssueSeverity.DEGRADED))
        add(SessionWriterTarget(receiverSolution, SessionArtifactFile.RECEIVER_SOLUTION_JSONL, SessionWriterIssueCategory.STRUCTURED_SIDECAR, SessionWriterIssueSeverity.DEGRADED))
        add(SessionWriterTarget(receiverPppSolution, SessionArtifactFile.RECEIVER_PPP_SOLUTION_JSONL, SessionWriterIssueCategory.STRUCTURED_SIDECAR, SessionWriterIssueSeverity.DEGRADED))
        add(SessionWriterTarget(rtklibSolutionNmea, SessionArtifactFile.RTKLIB_SOLUTION_NMEA, SessionWriterIssueCategory.LINE_SIDECAR, SessionWriterIssueSeverity.DEGRADED))
        add(SessionWriterTarget(rtklibSolutionPos, SessionArtifactFile.RTKLIB_SOLUTION_POS, SessionWriterIssueCategory.LINE_SIDECAR, SessionWriterIssueSeverity.DEGRADED))
        add(SessionWriterTarget(rtklibStatus, SessionArtifactFile.RTKLIB_STATUS_JSONL, SessionWriterIssueCategory.STRUCTURED_SIDECAR, SessionWriterIssueSeverity.DEGRADED))
    }
}

private data class SessionWriterTarget(
    val stream: OutputStream,
    val artifact: SessionArtifactFile,
    val category: SessionWriterIssueCategory,
    val severity: SessionWriterIssueSeverity,
)

private data class OpenedSessionWriterTarget(
    val artifact: SessionArtifactFile,
    val stream: OutputStream,
)

private fun finaliseStream(
    target: SessionWriterTarget,
    issues: MutableList<SessionWriterIssue>,
) {
    runCatching { target.stream.flush() }
        .onFailure { error -> issues += target.issue("flush", error) }
    runCatching { target.stream.close() }
        .onFailure { error -> issues += target.issue("close", error) }
}

private fun SessionWriterTarget.issue(operation: String, error: Throwable): SessionWriterIssue =
    SessionWriterIssue(
        artifact = artifact.fileName,
        category = category,
        severity = severity,
        message = error.message ?: "Failed to $operation ${artifact.fileName}",
    )

private fun finaliseOpenedStream(target: OpenedSessionWriterTarget, openingFailure: Throwable) {
    runCatching { target.stream.flush() }
        .onFailure { error -> openingFailure.addSuppressed(target.cleanupFailure("flush", error)) }
    runCatching { target.stream.close() }
        .onFailure { error -> openingFailure.addSuppressed(target.cleanupFailure("close", error)) }
}

private fun OpenedSessionWriterTarget.cleanupFailure(operation: String, error: Throwable): IOException =
    IOException("Failed to $operation ${artifact.fileName} during partial session-writer cleanup.", error)

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
        appendJsonField("rtklibProfileId", metadata.rtklibProfileId)
        appendJsonField("rtklibEnabled", metadata.rtklibEnabled)
        appendJsonField("rtklibPreset", metadata.rtklibPreset)
        appendJsonField("rtklibSnapshotId", metadata.rtklibSnapshotId)
        appendJsonField("rtklibRoutePlan", metadata.rtklibRoutePlan)
        appendJsonField("rtklibValidationSummary", metadata.rtklibValidationSummary)
        appendJsonField("rtklibOutputNmea", metadata.rtklibOutputNmea)
        appendJsonField("rtklibOutputPos", metadata.rtklibOutputPos)
        appendJsonField("rtklibFrequencyCount", metadata.rtklibFrequencyCount)
        appendJsonField("rtklibServerCycleMillis", metadata.rtklibServerCycleMillis)
        appendJsonField("rtklibServerBufferBytes", metadata.rtklibServerBufferBytes)
        appendJsonField("rtklibSolutionBufferBytes", metadata.rtklibSolutionBufferBytes)
        appendJsonField("solutionPolicyProfileId", metadata.solutionPolicyProfileId)
        appendJsonField("solutionScreenPolicy", metadata.solutionScreenPolicy)
        appendJsonField("solutionMockPolicy", metadata.solutionMockPolicy)
        appendJsonField("storageProfileId", metadata.storageProfileId)
        appendJsonField("storageKind", metadata.storageKind)
        appendJsonField("coordinateSource", metadata.coordinateSource)
        appendJsonField("baseCoordinateId", metadata.baseCoordinateId)
        appendJsonField("baseCoordinateName", metadata.baseCoordinateName)
        appendJsonField("baseCoordinateMethod", metadata.baseCoordinateMethod)
        appendJsonField("baseCasterUploadEnabled", metadata.baseCasterUploadEnabled)
        appendJsonField("baseCasterUploadHost", metadata.baseCasterUploadHost)
        appendJsonField("baseCasterUploadPort", metadata.baseCasterUploadPort)
        appendJsonField("baseCasterUploadMountpoint", metadata.baseCasterUploadMountpoint)
        appendJsonField("baseCasterUploadUsernamePresent", metadata.baseCasterUploadUsernamePresent)
        appendJsonField("baseCasterUploadSecretRef", metadata.baseCasterUploadSecretRef)
        appendJsonField("baseCasterUploadFinalStatus", metadata.baseCasterUploadFinalStatus)
        appendJsonField("validationSummary", metadata.validationSummary)
        appendJsonArrayField("expectedArtifacts", metadata.expectedArtifacts)
        metadata.ntrip?.let { appendNtripMetadata(it) }
        append("}")
    }
}

private fun Path.appendStream(): OutputStream {
    return BufferedOutputStream(
        Files.newOutputStream(
            this,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        ),
    )
}

private fun OutputStream?.writeBestEffort(bytes: ByteArray) {
    this ?: return
    runCatching { write(bytes) }
}

private fun OutputStream?.flushBestEffort() {
    this ?: return
    runCatching { flush() }
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
