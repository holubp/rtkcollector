package org.rtkcollector.app.recording

import org.rtkcollector.receiver.ublox.UbloxNavPvtParser
import org.rtkcollector.receiver.ublox.UbloxNmeaExporter
import org.rtkcollector.receiver.ublox.UbloxStreamParser
import org.rtkcollector.receiver.unicore.Um980NmeaExportOptions
import org.rtkcollector.receiver.unicore.Um980NmeaReexportProgress
import org.rtkcollector.receiver.unicore.Um980NmeaReexporter
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

data class ReceiverNmeaReexportResult(
    val outputNmea: Path,
    val sentencesWritten: Long,
)

object ReceiverNmeaReexporter {
    fun reexportReceiverRxRaw(
        receiverRxRaw: Path,
        outputNmea: Path,
        receiverFamily: String?,
        options: Um980NmeaExportOptions = Um980NmeaExportOptions(),
        onProgress: (Um980NmeaReexportProgress) -> Unit = {},
    ): ReceiverNmeaReexportResult {
        require(Files.isRegularFile(receiverRxRaw)) { "receiver-rx.raw is required for NMEA regeneration." }
        outputNmea.parent?.let(Files::createDirectories)
        val temporaryOutput = outputNmea.resolveSibling("${outputNmea.fileName}.tmp")
        val sentencesWritten = Files.newInputStream(receiverRxRaw).use { input ->
            Files.newOutputStream(
                temporaryOutput,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { output ->
                reexportReceiverRxRaw(
                    input = input,
                    output = output,
                    receiverFamily = receiverFamily,
                    totalBytes = Files.size(receiverRxRaw),
                    options = options,
                    onProgress = onProgress,
                )
            }
        }
        if (sentencesWritten > 0L) {
            Files.move(temporaryOutput, outputNmea, StandardCopyOption.REPLACE_EXISTING)
        } else {
            Files.deleteIfExists(temporaryOutput)
        }
        return ReceiverNmeaReexportResult(outputNmea = outputNmea, sentencesWritten = sentencesWritten)
    }

    fun reexportReceiverRxRaw(
        input: InputStream,
        output: OutputStream,
        receiverFamily: String?,
        totalBytes: Long? = null,
        options: Um980NmeaExportOptions = Um980NmeaExportOptions(),
        onProgress: (Um980NmeaReexportProgress) -> Unit = {},
    ): Long =
        if (receiverFamily.orEmpty().startsWith("ublox", ignoreCase = true)) {
            reexportUblox(input, output, totalBytes, onProgress)
        } else {
            Um980NmeaReexporter.reexportReceiverRxRaw(
                input = input,
                output = output,
                totalBytes = totalBytes,
                options = options,
                onProgress = onProgress,
            ).sentencesWritten
        }

    private fun reexportUblox(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long?,
        onProgress: (Um980NmeaReexportProgress) -> Unit,
    ): Long {
        val parser = UbloxStreamParser()
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
                    "ubx" -> {
                        if (record.bytes.getOrNull(2) == 0x01.toByte() && record.bytes.getOrNull(3) == 0x07.toByte()) {
                            UbloxNavPvtParser.parse(record.bytes, nowMillis = 0L)
                                ?.let(UbloxNmeaExporter::exportGga)
                                ?.let { sentence ->
                                    output.write(sentence.toByteArray(Charsets.US_ASCII))
                                    sentencesWritten++
                                }
                        }
                    }
                    "nmea" -> {
                        record.text?.takeIf { it.startsWith("\$") }?.let { sentence ->
                            val normalized = if (sentence.endsWith("\n")) sentence else "$sentence\r\n"
                            output.write(normalized.toByteArray(Charsets.US_ASCII))
                            sentencesWritten++
                        }
                    }
                }
            }
            onProgress(Um980NmeaReexportProgress(bytesRead, totalBytes, sentencesWritten))
        }
        output.flush()
        return sentencesWritten
    }
}
