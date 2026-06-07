package org.rtkcollector.core.session

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class SessionWriters private constructor(
    private val sessionDirectory: Path,
    private val receiverRx: OutputStream,
    private val txToReceiver: OutputStream,
    private val correctionInput: OutputStream,
    private val events: OutputStream,
    private val qualityLive: OutputStream,
) : Closeable {
    fun writeSessionJson(json: String) {
        Files.writeString(
            sessionDirectory.resolve("session.json"),
            json.trimEnd() + "\n",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
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
    }

    fun appendEventJson(json: String) {
        events.writeJsonLine(json)
    }

    fun appendQualityLiveJson(json: String) {
        qualityLive.writeJsonLine(json)
    }

    fun flush() {
        receiverRx.flush()
        txToReceiver.flush()
        correctionInput.flush()
        events.flush()
        qualityLive.flush()
    }

    override fun close() {
        receiverRx.close()
        txToReceiver.close()
        correctionInput.close()
        events.close()
        qualityLive.close()
    }

    companion object {
        fun open(sessionDirectory: Path): SessionWriters {
            Files.createDirectories(sessionDirectory)
            return SessionWriters(
                sessionDirectory = sessionDirectory,
                receiverRx = sessionDirectory.appendStream("receiver-rx.raw"),
                txToReceiver = sessionDirectory.appendStream("tx-to-receiver.raw"),
                correctionInput = sessionDirectory.appendStream("correction-input.raw"),
                events = sessionDirectory.appendStream("events.jsonl"),
                qualityLive = sessionDirectory.appendStream("quality-live.jsonl"),
            )
        }
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
    append("}")
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
