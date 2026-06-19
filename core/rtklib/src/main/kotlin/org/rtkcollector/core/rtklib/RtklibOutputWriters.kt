package org.rtkcollector.core.rtklib

import java.io.Closeable
import java.io.OutputStream
import java.nio.charset.StandardCharsets

class RtklibOutputWriters(
    private val nmeaOutput: OutputStream?,
    private val posOutput: OutputStream?,
) : Closeable {
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

    private fun OutputStream.writeLine(line: String) {
        write(line.toByteArray(StandardCharsets.US_ASCII))
        if (!line.endsWith("\n")) {
            write('\n'.code)
        }
    }
}
