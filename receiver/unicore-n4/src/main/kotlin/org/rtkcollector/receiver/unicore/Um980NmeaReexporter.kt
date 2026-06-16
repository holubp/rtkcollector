package org.rtkcollector.receiver.unicore

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

data class Um980NmeaReexportResult(
    val outputNmea: Path,
    val sentencesWritten: Long,
)

data class Um980NmeaReexportStreamResult(
    val sentencesWritten: Long,
)

data class Um980NmeaReexportProgress(
    val bytesRead: Long,
    val totalBytes: Long?,
    val sentencesWritten: Long,
) {
    val fraction: Double?
        get() = totalBytes?.takeIf { it > 0L }?.let { total -> bytesRead.toDouble() / total.toDouble() }
}

object Um980NmeaReexporter {
    fun reexportReceiverRxRaw(
        receiverRxRaw: Path,
        outputNmea: Path,
        options: Um980NmeaExportOptions = Um980NmeaExportOptions(),
    ): Um980NmeaReexportResult {
        require(Files.isRegularFile(receiverRxRaw)) { "receiver-rx.raw is required for NMEA re-export." }
        outputNmea.parent?.let(Files::createDirectories)
        val temporaryOutput = outputNmea.resolveSibling("${outputNmea.fileName}.tmp")
        var sentencesWritten = 0L
        Files.newInputStream(receiverRxRaw).use { input ->
            Files.newOutputStream(
                temporaryOutput,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { output ->
                sentencesWritten = reexportReceiverRxRaw(
                    input = input,
                    output = output,
                    totalBytes = Files.size(receiverRxRaw),
                    options = options,
                ).sentencesWritten
            }
        }
        Files.move(temporaryOutput, outputNmea, StandardCopyOption.REPLACE_EXISTING)
        return Um980NmeaReexportResult(
            outputNmea = outputNmea,
            sentencesWritten = sentencesWritten,
        )
    }

    fun reexportReceiverRxRaw(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long? = null,
        options: Um980NmeaExportOptions = Um980NmeaExportOptions(),
        onProgress: (Um980NmeaReexportProgress) -> Unit = {},
    ): Um980NmeaReexportStreamResult {
        val parser = Um980StreamParser()
        val nmeaExtractor = NmeaSentenceExtractor()
        val buffer = ByteArray(64 * 1024)
        var bytesRead = 0L
        var sentencesWritten = 0L
        onProgress(Um980NmeaReexportProgress(bytesRead, totalBytes, sentencesWritten))
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            bytesRead += read.toLong()
            parser.accept(buffer.copyOf(read)).forEach { record ->
                when (record.kind) {
                    "nmea" -> {
                        nmeaExtractor.accept(record.bytes).forEach { sentence ->
                            output.write(sentence.toByteArray(Charsets.US_ASCII))
                            sentencesWritten++
                        }
                    }
                    "unicore_binary" -> {
                        val telemetry = Um980BinaryParser.parseBestnavb(record.bytes)
                        if (telemetry != null) {
                            Um980NmeaExporter.export(telemetry, options).forEach { sentence ->
                                output.write(sentence.toByteArray(Charsets.US_ASCII))
                                sentencesWritten++
                            }
                        }
                    }
                    else -> Unit
                }
            }
            onProgress(Um980NmeaReexportProgress(bytesRead, totalBytes, sentencesWritten))
        }
        output.flush()
        return Um980NmeaReexportStreamResult(sentencesWritten = sentencesWritten)
    }
}
