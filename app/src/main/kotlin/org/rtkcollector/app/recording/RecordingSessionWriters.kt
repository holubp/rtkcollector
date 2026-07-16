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
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

internal interface RecordingSessionWriters : Closeable {
    val totalBytesWritten: Long
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
    fun appendRtklibSolutionNmea(sentence: String)
    fun appendRtklibSolutionPos(line: String)
    fun appendRtklibStatusJson(json: String)
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
    private val totalBytes = AtomicLong(0)

    override val totalBytesWritten: Long
        get() = totalBytes.get()

    override fun writeSessionJson(json: String) {
        val text = json.trimEnd() + "\n"
        delegate.writeSessionJson(json)
        countUtf8(text)
    }

    override fun appendReceiverRx(bytes: ByteArray) {
        delegate.appendReceiverRx(bytes)
        count(bytes.size.toLong())
    }

    override fun appendTxToReceiver(bytes: ByteArray) {
        delegate.appendTxToReceiver(bytes)
        count(bytes.size.toLong())
    }

    override fun appendCorrectionInput(bytes: ByteArray) {
        delegate.appendCorrectionInput(bytes)
        count(bytes.size * 2L)
    }

    override fun appendBaseCasterUploadRtcm(bytes: ByteArray) {
        delegate.appendBaseCasterUploadRtcm(bytes)
        count(bytes.size.toLong())
    }

    override fun appendEventJson(json: String) {
        delegate.appendEventJson(json)
        countJsonLine(json)
    }

    override fun appendQualityLiveJson(json: String) {
        delegate.appendQualityLiveJson(json)
        countJsonLine(json)
    }

    override fun appendReceiverSolutionNmea(sentence: String) {
        delegate.appendReceiverSolutionNmea(sentence)
        countAscii(sentence)
    }

    override fun appendReceiverSolutionJson(json: String) {
        delegate.appendReceiverSolutionJson(json)
        countJsonLine(json)
    }

    override fun appendReceiverPppSolutionJson(json: String) {
        delegate.appendReceiverPppSolutionJson(json)
        countJsonLine(json)
    }

    override fun appendRtklibSolutionNmea(sentence: String) {
        delegate.appendRtklibSolutionNmea(sentence)
        countAscii(sentence)
    }

    override fun appendRtklibSolutionPos(line: String) {
        delegate.appendRtklibSolutionPos(line)
        countAscii(line)
    }

    override fun appendRtklibStatusJson(json: String) {
        delegate.appendRtklibStatusJson(json)
        countJsonLine(json)
    }

    override fun appendExtractedRtcm(bytes: ByteArray) {
        delegate.appendExtractedRtcm(bytes)
        count(bytes.size.toLong())
    }

    override fun writeBasePositionJson(json: String) {
        val text = json.trimEnd() + "\n"
        delegate.writeBasePositionJson(json)
        countUtf8(text)
    }
    override fun flush() = delegate.flush()
    override fun flushRaw() = delegate.flushReceiverRx()
    override fun closeAll(): SessionWriterCloseReport = delegate.closeAll()

    override fun close() {
        val report = closeAll()
        if (report.issues.isNotEmpty()) {
            throw IOException(report.userMessage ?: "Session writer close failed")
        }
    }

    private fun count(bytes: Long) {
        totalBytes.addAndGet(bytes)
    }

    private fun countAscii(text: String) {
        count(text.toByteArray(StandardCharsets.US_ASCII).size.toLong())
    }

    private fun countUtf8(text: String) {
        count(text.toByteArray(StandardCharsets.UTF_8).size.toLong())
    }

    private fun countJsonLine(json: String) {
        countUtf8(json)
        count(1)
    }

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
    private val rtklibSolutionNmea: OutputStream,
    private val rtklibSolutionPos: OutputStream,
    private val rtklibStatus: OutputStream,
    private val extractedRtcm: OutputStream,
) : RecordingSessionWriters {
    private val totalBytes = AtomicLong(0)
    private var finalCloseReport: SessionWriterCloseReport? = null

    override val totalBytesWritten: Long
        get() = totalBytes.get()

    override fun writeSessionJson(json: String) {
        val text = json.trimEnd() + "\n"
        writeText(SessionArtifactFile.SESSION_JSON.fileName, text)
        countUtf8(text)
    }

    override fun appendReceiverRx(bytes: ByteArray) {
        receiverRx.write(bytes)
        count(bytes.size)
    }

    override fun appendTxToReceiver(bytes: ByteArray) {
        txToReceiver.write(bytes)
        count(bytes.size)
    }

    override fun appendCorrectionInput(bytes: ByteArray) {
        correctionInput.write(bytes)
        correctionInputRtcm3.writeBestEffort(bytes)
        count(bytes.size)
        if (correctionInputRtcm3 != null) count(bytes.size)
    }

    override fun appendBaseCasterUploadRtcm(bytes: ByteArray) {
        baseCasterUploadRtcm3.write(bytes)
        count(bytes.size)
    }

    override fun appendEventJson(json: String) {
        events.writeJsonLine(json)
        countJsonLine(json)
    }

    override fun appendQualityLiveJson(json: String) {
        qualityLive.writeJsonLine(json)
        countJsonLine(json)
    }

    override fun appendReceiverSolutionNmea(sentence: String) {
        receiverSolutionNmea.write(sentence.toByteArray(StandardCharsets.US_ASCII))
        countAscii(sentence)
    }

    override fun appendReceiverSolutionJson(json: String) {
        receiverSolution.writeJsonLine(json)
        countJsonLine(json)
    }

    override fun appendReceiverPppSolutionJson(json: String) {
        receiverPppSolution.writeJsonLine(json)
        countJsonLine(json)
    }

    override fun appendRtklibSolutionNmea(sentence: String) {
        rtklibSolutionNmea.write(sentence.toByteArray(StandardCharsets.US_ASCII))
        countAscii(sentence)
    }

    override fun appendRtklibSolutionPos(line: String) {
        rtklibSolutionPos.write(line.toByteArray(StandardCharsets.US_ASCII))
        countAscii(line)
    }

    override fun appendRtklibStatusJson(json: String) {
        rtklibStatus.writeJsonLine(json)
        countJsonLine(json)
    }

    override fun appendExtractedRtcm(bytes: ByteArray) {
        extractedRtcm.write(bytes)
        count(bytes.size)
    }

    override fun writeBasePositionJson(json: String) {
        val text = json.trimEnd() + "\n"
        writeText(SessionArtifactFile.BASE_POSITION_JSON.fileName, text)
        countUtf8(text)
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
        rtklibSolutionNmea.flush()
        rtklibSolutionPos.flush()
        rtklibStatus.flush()
        extractedRtcm.flush()
    }

    override fun flushRaw() {
        receiverRx.flush()
    }

    @Synchronized
    override fun closeAll(): SessionWriterCloseReport {
        finalCloseReport?.let { return it }
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
        closeStream(
            stream = rtklibSolutionNmea,
            artifact = SessionArtifactFile.RTKLIB_SOLUTION_NMEA,
            category = SessionWriterIssueCategory.LINE_SIDECAR,
            severity = SessionWriterIssueSeverity.DEGRADED,
            issues = issues,
        )
        closeStream(
            stream = rtklibSolutionPos,
            artifact = SessionArtifactFile.RTKLIB_SOLUTION_POS,
            category = SessionWriterIssueCategory.LINE_SIDECAR,
            severity = SessionWriterIssueSeverity.DEGRADED,
            issues = issues,
        )
        closeStream(
            stream = rtklibStatus,
            artifact = SessionArtifactFile.RTKLIB_STATUS_JSONL,
            category = SessionWriterIssueCategory.LINE_SIDECAR,
            severity = SessionWriterIssueSeverity.DEGRADED,
            issues = issues,
        )
        return SessionWriterCloseReport(issues).also { finalCloseReport = it }
    }

    override fun close() {
        val report = closeAll()
        if (report.issues.isNotEmpty()) {
            throw IOException(report.userMessage ?: "Session writer close failed")
        }
    }

    private fun count(bytes: Int) {
        count(bytes.toLong())
    }

    private fun count(bytes: Long) {
        totalBytes.addAndGet(bytes)
    }

    private fun countAscii(text: String) {
        count(text.toByteArray(StandardCharsets.US_ASCII).size)
    }

    private fun countUtf8(text: String) {
        count(text.toByteArray(StandardCharsets.UTF_8).size)
    }

    private fun countJsonLine(json: String) {
        countUtf8(json)
        count(1)
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

            val openedStreams = mutableListOf<OpenedSafStream>()
            fun appendStream(artifact: SessionArtifactFile): OutputStream {
                val uri = create(artifact.fileName)
                return BufferedOutputStream(
                    resolver.openOutputStream(uri, "wa")
                        ?: error("Unable to open SAF file for append: ${artifact.fileName}"),
                ).also { stream -> openedStreams += OpenedSafStream(artifact, stream) }
            }

            fun tryAppendStream(artifact: SessionArtifactFile): OutputStream? =
                runCatching { appendStream(artifact) }.getOrNull()

            return try {
                SafRecordingSessionWriters(
                    resolver = resolver,
                    sessionUri = sessionUri,
                    files = files,
                    receiverRx = appendStream(SessionArtifactFile.RECEIVER_RX_RAW),
                    txToReceiver = appendStream(SessionArtifactFile.TX_TO_RECEIVER_RAW),
                    correctionInput = appendStream(SessionArtifactFile.CORRECTION_INPUT_RAW),
                    correctionInputRtcm3 = tryAppendStream(SessionArtifactFile.CORRECTION_INPUT_RTCM3),
                    baseCasterUploadRtcm3 = appendStream(SessionArtifactFile.BASE_CASTER_UPLOAD_RTCM3),
                    events = appendStream(SessionArtifactFile.EVENTS_JSONL),
                    qualityLive = appendStream(SessionArtifactFile.QUALITY_LIVE_JSONL),
                    receiverSolutionNmea = appendStream(SessionArtifactFile.RECEIVER_SOLUTION_NMEA),
                    receiverSolution = appendStream(SessionArtifactFile.RECEIVER_SOLUTION_JSONL),
                    receiverPppSolution = appendStream(SessionArtifactFile.RECEIVER_PPP_SOLUTION_JSONL),
                    rtklibSolutionNmea = appendStream(SessionArtifactFile.RTKLIB_SOLUTION_NMEA),
                    rtklibSolutionPos = appendStream(SessionArtifactFile.RTKLIB_SOLUTION_POS),
                    rtklibStatus = appendStream(SessionArtifactFile.RTKLIB_STATUS_JSONL),
                    extractedRtcm = appendStream(SessionArtifactFile.RTCM_EXTRACTED_RTCM3),
                )
            } catch (failure: Throwable) {
                openedStreams.forEach { target -> finaliseOpenedSafStream(target, failure) }
                throw failure
            }
        }
    }
}

private data class OpenedSafStream(
    val artifact: SessionArtifactFile,
    val stream: OutputStream,
)

private fun finaliseOpenedSafStream(target: OpenedSafStream, openingFailure: Throwable) {
    runCatching { target.stream.flush() }
        .onFailure { error -> openingFailure.addSuppressed(safCleanupFailure(target.artifact, "flush", error)) }
    runCatching { target.stream.close() }
        .onFailure { error -> openingFailure.addSuppressed(safCleanupFailure(target.artifact, "close", error)) }
}

private fun safCleanupFailure(artifact: SessionArtifactFile, operation: String, error: Throwable): IOException =
    IOException("Failed to $operation ${artifact.fileName} during partial SAF session-writer cleanup.", error)

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
    runCatching { stream.flush() }.onFailure { error ->
        issues += SessionWriterIssue(
            artifact = artifact.fileName,
            category = category,
            severity = severity,
            message = error.message ?: "Failed to flush ${artifact.fileName}",
        )
    }
    runCatching { stream.close() }.onFailure { error ->
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
        fileName.endsWith(".pos") -> "text/plain"
        else -> "application/octet-stream"
    }
