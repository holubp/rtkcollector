package org.rtkcollector.core.rtklib

import java.io.Closeable
import java.io.OutputStream
import java.nio.charset.StandardCharsets

class RtklibOutputWriters private constructor(
    private val nmeaOutput: LineOutput?,
    private val posOutput: LineOutput?,
) : Closeable {
    constructor(
        nmeaOutput: OutputStream?,
        posOutput: OutputStream?,
    ) : this(
        nmeaOutput = nmeaOutput?.let(::StreamLineOutput),
        posOutput = posOutput?.let(::StreamLineOutput),
    )

    private var closed = false

    @Synchronized
    fun write(batch: RtklibNativeOutputBatch) {
        check(!closed) { "RTKLIB output writers are closed" }
        batch.nmeaLines.forEach { line -> nmeaOutput?.writeLine(line) }
        batch.posLines.forEach { line -> posOutput?.writeLine(line) }
    }

    @Synchronized
    fun flush() {
        if (closed) return
        nmeaOutput?.flush()
        posOutput?.flush()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        try {
            flush()
        } finally {
            nmeaOutput?.close()
            posOutput?.close()
            closed = true
        }
    }

    private interface LineOutput : Closeable {
        fun writeLine(line: String)
        fun flush()
    }

    private class StreamLineOutput(private val output: OutputStream) : LineOutput {
        override fun writeLine(line: String) {
            output.write(line.toByteArray(StandardCharsets.US_ASCII))
            if (!line.endsWith("\n")) {
                output.write('\n'.code)
            }
        }

        override fun flush() {
            output.flush()
        }

        override fun close() {
            output.close()
        }
    }

    private class CallbackLineOutput(private val appendLine: (String) -> Unit) : LineOutput {
        override fun writeLine(line: String) {
            appendLine(if (line.endsWith("\n")) line else "$line\n")
        }

        override fun flush() = Unit
        override fun close() = Unit
    }

    companion object {
        fun fromCallbacks(
            appendNmeaLine: ((String) -> Unit)?,
            appendPosLine: ((String) -> Unit)?,
        ): RtklibOutputWriters =
            RtklibOutputWriters(
                nmeaOutput = appendNmeaLine?.let(::CallbackLineOutput),
                posOutput = appendPosLine?.let(::CallbackLineOutput),
            )
    }
}
