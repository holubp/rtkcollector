package org.rtkcollector.app.recording

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import org.rtkcollector.core.session.SessionArtifactFile
import org.rtkcollector.core.session.SessionWriterCloseReport
import org.rtkcollector.core.session.SessionWriterIssue
import org.rtkcollector.core.session.SessionWriterIssueCategory
import org.rtkcollector.core.session.SessionWriterIssueSeverity
import org.rtkcollector.core.session.SessionWriters
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path

internal interface RecordingSessionWriters : Closeable {
    fun writeSessionJson(json: String)
    fun appendReceiverRx(bytes: ByteArray)
    fun appendTxToReceiver(bytes: ByteArray)
    fun appendCorrectionInput(bytes: ByteArray)
    fun appendBaseCasterUploadRtcm(bytes: ByteArray)
    fun appendEventJson(json: String)
    fun appendQualityLiveJson(json: String)
    fun appendReceiverSolutionNmea(sentence: String)
    fun appendReceiverSolutionJson(json: String)
    fun appendReceiverPppSolutionJson(json: String)
    fun appendExtractedRtcm(bytes: ByteArray)
    fun writeBasePositionJson(json: String)
    fun flush()
    fun finish(): SessionWriterCloseReport = SessionWriterCloseReport()
    fun flushRaw()
    fun closeAll(): SessionWriterCloseReport
}

internal class PathRecordingSessionWriters private constructor(
    private val delegate: SessionWriters,
) : RecordingSessionWriters {
    override fun writeSessionJson(json: String) = delegate.writeSessionJson(json)
    override fun appendReceiverRx(bytes: ByteArray) = delegate.appendReceiverRx(bytes)
    override fun appendTxToReceiver(bytes: ByteArray) = delegate.appendTxToReceiver(bytes)
    override fun appendCorrectionInput(bytes: ByteArray) = delegate.appendCorrectionInput(bytes)
    override fun appendBaseCasterUploadRtcm(bytes: ByteArray) = delegate.appendBaseCasterUploadRtcm(bytes)
    override fun appendEventJson(json: String) = delegate.appendEventJson(json)
    override fun appendQualityLiveJson(json: String) = delegate.appendQualityLiveJson(json)
    override fun appendReceiverSolutionNmea(sentence: String) = delegate.appendReceiverSolutionNmea(sentence)
    override fun appendReceiverSolutionJson(json: String) = delegate.appendReceiverSolutionJson(json)
    override fun appendReceiverPppSolutionJson(json: String) = delegate.appendReceiverPppSolutionJson(json)
    override fun appendExtractedRtcm(bytes: ByteArray) = delegate.appendExtractedRtcm(bytes)
    override fun writeBasePositionJson(json: String) = delegate.writeBasePositionJson(json)
    override fun flush() = delegate.flush()
    override fun flushRaw() = delegate.flush()
    override fun closeAll(): SessionWriterCloseReport {
        val issue = runCatching {
            delegate.flush()
            delegate.close()
        }.exceptionOrNull() ?: return SessionWriterCloseReport()

        return SessionWriterCloseReport(
            issues = listOf(
                SessionWriterIssue(
                    artifact = SessionArtifactFile.RECEIVER_RX_RAW.fileName,
                    category = SessionWriterIssueCategory.RAW_RX,
                    severity = SessionWriterIssueSeverity.FATAL,
                    message = issue.message ?: "Session writer close failed",
                ),
            ),
        )
    }

    override fun close() = delegate.close()

    companion object {
        fun open(sessionDirectory: Path): PathRecordingSessionWriters =
            PathRecordingSessionWriters(SessionWriters.open(sessionDirectory))
    }
}

internal class SafRecordingSessionWriters private constructor(
    private val resolver: ContentResolver,
    val sessionUri: Uri,
    private val files: MutableMap<String, Uri>,
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
    private val extractedRtcm: OutputStream,
) : RecordingSessionWriters {
    override fun writeSessionJson(json: String) {
        writeText(SessionArtifactFile.SESSION_JSON.fileName, json.trimEnd() + "\n")
    }

    override fun appendReceiverRx(bytes: ByteArray) {
        receiverRx.write(bytes)
    }

    override fun appendTxToReceiver(bytes: ByteArray) {
        txToReceiver.write(bytes)
    }

    override fun appendCorrectionInput(bytes: ByteArray) {
        correctionInput.write(bytes)
        correctionInputRtcm3.writeBestEffort(bytes)
    }

    override fun appendBaseCasterUploadRtcm(bytes: ByteArray) {
        baseCasterUploadRtcm3.write(bytes)
    }

    override fun appendEventJson(json: String) {
        events.writeJsonLine(json)
    }

    override fun appendQualityLiveJson(json: String) {
        qualityLive.writeJsonLine(json)
    }

    override fun appendReceiverSolutionNmea(sentence: String) {
        receiverSolutionNmea.write(sentence.toByteArray(StandardCharsets.US_ASCII))
    }

    override fun appendReceiverSolutionJson(json: String) {
        receiverSolution.writeJsonLine(json)
    }

    override fun appendReceiverPppSolutionJson(json: String) {
        receiverPppSolution.writeJsonLine(json)
    }

    override fun appendExtractedRtcm(bytes: ByteArray) {
        extractedRtcm.write(bytes)
    }

    override fun writeBasePositionJson(json: String) {
        writeText(SessionArtifactFile.BASE_POSITION_JSON.fileName, json.trimEnd() + "\n")
    }

    override fun flush() {
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
        extractedRtcm.flush()
    }

    override fun flushRaw() {
        receiverRx.flush()
    }

    override fun closeAll(): SessionWriterCloseReport {
        val issues = mutableListOf<SessionWriterIssue>()
        closeStream(
            stream = receiverRx,
            artifact = SessionArtifactFile.RECEIVER_RX_RAW,
            category = SessionWriterIssueCategory.RAW_RX,
            severity = SessionWriterIssueSeverity.FATAL,
            issues = issues,
        )
        closeStream(
            stream = txToReceiver,
            artifact = SessionArtifactFile.TX_TO_RECEIVER_RAW,
            category = SessionWriterIssueCategory.BINARY_SIDECAR,
            severity = SessionWriterIssueSeverity.DEGRADED,
            issues = issues,
        )
        closeStream(
            stream = correctionInput,
            artifact = SessionArtifactFile.CORRECTION_INPUT_RAW,
            category = SessionWriterIssueCategory.BINARY_SIDECAR,
            severity = SessionWriterIssueSeverity.DEGRADED,
            issues = issues,
        )
        closeOptionalStream(
            stream = correctionInputRtcm3,
            artifact = SessionArtifactFile.CORRECTION_INPUT_RTCM3,
            category = SessionWriterIssueCategory.BINARY_SIDECAR,
            severity = SessionWriterIssueSeverity.DEGRADED,
            issues = issues,
        )
        closeStream(
            stream = baseCasterUploadRtcm3,
            artifact = SessionArtifactFile.BASE_CASTER_UPLOAD_RTCM3,
            category = SessionWriterIssueCategory.BINARY_SIDECAR,
            severity = SessionWriterIssueSeverity.DEGRADED,
            issues = issues,
        )
        closeStream(
            stream = extractedRtcm,
            artifact = SessionArtifactFile.RTCM_EXTRACTED_RTCM3,
            category = SessionWriterIssueCategory.BINARY_SIDECAR,
            severity = SessionWriterIssueSeverity.DEGRADED,
            issues = issues,
        )
        closeStream(
            stream = events,
            artifact = SessionArtifactFile.EVENTS_JSONL,
            category = SessionWriterIssueCategory.LINE_SIDECAR,
            severity = SessionWriterIssueSeverity.DEGRADED,
            issues = issues,
        )
        closeStream(
            stream = qualityLive,
            artifact = SessionArtifactFile.QUALITY_LIVE_JSONL,
            category = SessionWriterIssueCategory.LINE_SIDECAR,
            severity = SessionWriterIssueSeverity.DEGRADED,
            issues = issues,
        )
        closeStream(
            stream = receiverSolutionNmea,
            artifact = SessionArtifactFile.RECEIVER_SOLUTION_NMEA,
            category = SessionWriterIssueCategory.LINE_SIDECAR,
            severity = SessionWriterIssueSeverity.DEGRADED,
            issues = issues,
        )
        closeStream(
            stream = receiverSolution,
            artifact = SessionArtifactFile.RECEIVER_SOLUTION_JSONL,
            category = SessionWriterIssueCategory.LINE_SIDECAR,
            severity = SessionWriterIssueSeverity.DEGRADED,
            issues = issues,
        )
        closeStream(
            stream = receiverPppSolution,
            artifact = SessionArtifactFile.RECEIVER_PPP_SOLUTION_JSONL,
            category = SessionWriterIssueCategory.LINE_SIDECAR,
            severity = SessionWriterIssueSeverity.DEGRADED,
            issues = issues,
        )
        return SessionWriterCloseReport(issues)
    }

    override fun close() {
        receiverRx.close()
        txToReceiver.close()
        correctionInput.close()
        correctionInputRtcm3.closeBestEffort()
        baseCasterUploadRtcm3.close()
        events.close()
        qualityLive.close()
        receiverSolutionNmea.close()
        receiverSolution.close()
        receiverPppSolution.close()
        extractedRtcm.close()
    }

    private fun writeText(fileName: String, text: String) {
        val uri = files[fileName] ?: createFile(fileName, fileMimeType(fileName))
        resolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(text.toByteArray(StandardCharsets.UTF_8))
            output.flush()
        } ?: error("Unable to open SAF file for writing: $fileName")
    }

    private fun createFile(fileName: String, mimeType: String): Uri =
        DocumentsContract.createDocument(resolver, sessionUri, mimeType, fileName)
            ?.also { files[fileName] = it }
            ?: error("Unable to create SAF file: $fileName")

    companion object {
        fun open(
            resolver: ContentResolver,
            treeUri: Uri,
            sessionName: String,
        ): SafRecordingSessionWriters {
            val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            val sessionUri = DocumentsContract.createDocument(
                resolver,
                rootDocumentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                sessionName,
            ) ?: error("Unable to create SAF session folder.")

            val files = mutableMapOf<String, Uri>()
            fun create(fileName: String): Uri =
                DocumentsContract.createDocument(resolver, sessionUri, fileMimeType(fileName), fileName)
                    ?.also { files[fileName] = it }
                    ?: error("Unable to create SAF file: $fileName")

            fun appendStream(fileName: String): OutputStream {
                val uri = create(fileName)
                return BufferedOutputStream(
                    resolver.openOutputStream(uri, "wa")
                        ?: error("Unable to open SAF file for append: $fileName"),
                )
            }

            fun tryAppendStream(fileName: String): OutputStream? =
                runCatching { appendStream(fileName) }.getOrNull()

            return SafRecordingSessionWriters(
                resolver = resolver,
                sessionUri = sessionUri,
                files = files,
                receiverRx = appendStream(SessionArtifactFile.RECEIVER_RX_RAW.fileName),
                txToReceiver = appendStream(SessionArtifactFile.TX_TO_RECEIVER_RAW.fileName),
                correctionInput = appendStream(SessionArtifactFile.CORRECTION_INPUT_RAW.fileName),
                correctionInputRtcm3 = tryAppendStream(SessionArtifactFile.CORRECTION_INPUT_RTCM3.fileName),
                baseCasterUploadRtcm3 = appendStream(SessionArtifactFile.BASE_CASTER_UPLOAD_RTCM3.fileName),
                events = appendStream(SessionArtifactFile.EVENTS_JSONL.fileName),
                qualityLive = appendStream(SessionArtifactFile.QUALITY_LIVE_JSONL.fileName),
                receiverSolutionNmea = appendStream(SessionArtifactFile.RECEIVER_SOLUTION_NMEA.fileName),
                receiverSolution = appendStream(SessionArtifactFile.RECEIVER_SOLUTION_JSONL.fileName),
                receiverPppSolution = appendStream(SessionArtifactFile.RECEIVER_PPP_SOLUTION_JSONL.fileName),
                extractedRtcm = appendStream(SessionArtifactFile.RTCM_EXTRACTED_RTCM3.fileName),
            )
        }
    }
}

private fun closeOptionalStream(
    stream: OutputStream?,
    artifact: SessionArtifactFile,
    category: SessionWriterIssueCategory,
    severity: SessionWriterIssueSeverity,
    issues: MutableList<SessionWriterIssue>,
) {
    stream ?: return
    closeStream(stream, artifact, category, severity, issues)
}

private fun closeStream(
    stream: OutputStream,
    artifact: SessionArtifactFile,
    category: SessionWriterIssueCategory,
    severity: SessionWriterIssueSeverity,
    issues: MutableList<SessionWriterIssue>,
) {
    runCatching {
        stream.flush()
        stream.close()
    }.onFailure { error ->
        issues += SessionWriterIssue(
            artifact = artifact.fileName,
            category = category,
            severity = severity,
            message = error.message ?: "Failed to close ${artifact.fileName}",
        )
    }
}

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

private fun fileMimeType(fileName: String): String =
    when {
        fileName.endsWith(".json") -> "application/json"
        fileName.endsWith(".jsonl") -> "application/x-ndjson"
        fileName.endsWith(".txt") -> "text/plain"
        fileName.endsWith(".nmea") -> "text/plain"
        else -> "application/octet-stream"
    }
