package org.rtkcollector.app.recording

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import org.rtkcollector.core.session.SessionArtifactFile
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
    fun appendEventJson(json: String)
    fun appendQualityLiveJson(json: String)
    fun appendReceiverSolutionNmea(sentence: String)
    fun appendReceiverSolutionJson(json: String)
    fun appendReceiverPppSolutionJson(json: String)
    fun appendExtractedRtcm(bytes: ByteArray)
    fun writeBasePositionJson(json: String)
    fun flush()
}

internal class PathRecordingSessionWriters private constructor(
    private val delegate: SessionWriters,
) : RecordingSessionWriters {
    override fun writeSessionJson(json: String) = delegate.writeSessionJson(json)
    override fun appendReceiverRx(bytes: ByteArray) = delegate.appendReceiverRx(bytes)
    override fun appendTxToReceiver(bytes: ByteArray) = delegate.appendTxToReceiver(bytes)
    override fun appendCorrectionInput(bytes: ByteArray) = delegate.appendCorrectionInput(bytes)
    override fun appendEventJson(json: String) = delegate.appendEventJson(json)
    override fun appendQualityLiveJson(json: String) = delegate.appendQualityLiveJson(json)
    override fun appendReceiverSolutionNmea(sentence: String) = delegate.appendReceiverSolutionNmea(sentence)
    override fun appendReceiverSolutionJson(json: String) = delegate.appendReceiverSolutionJson(json)
    override fun appendReceiverPppSolutionJson(json: String) = delegate.appendReceiverPppSolutionJson(json)
    override fun appendExtractedRtcm(bytes: ByteArray) = delegate.appendExtractedRtcm(bytes)
    override fun writeBasePositionJson(json: String) = delegate.writeBasePositionJson(json)
    override fun flush() = delegate.flush()
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

            return SafRecordingSessionWriters(
                resolver = resolver,
                sessionUri = sessionUri,
                files = files,
                receiverRx = appendStream(SessionArtifactFile.RECEIVER_RX_RAW.fileName),
                txToReceiver = appendStream(SessionArtifactFile.TX_TO_RECEIVER_RAW.fileName),
                correctionInput = appendStream(SessionArtifactFile.CORRECTION_INPUT_RAW.fileName),
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
