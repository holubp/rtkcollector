package org.rtkcollector.receiver.unicore

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

data class Um980NmeaReexportResult(
    val outputNmea: Path,
    val sentencesWritten: Long,
)

object Um980NmeaReexporter {
    fun reexportReceiverRxRaw(
        receiverRxRaw: Path,
        outputNmea: Path,
        options: Um980NmeaExportOptions = Um980NmeaExportOptions(),
    ): Um980NmeaReexportResult {
        require(Files.isRegularFile(receiverRxRaw)) { "receiver-rx.raw is required for NMEA re-export." }
        outputNmea.parent?.let(Files::createDirectories)
        val temporaryOutput = outputNmea.resolveSibling("${outputNmea.fileName}.tmp")
        val parser = Um980StreamParser()
        var sentencesWritten = 0L
        Files.newInputStream(receiverRxRaw).use { input ->
            Files.newOutputStream(
                temporaryOutput,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { output ->
                val buffer = ByteArray(64 * 1024)
                val nmeaExtractor = NmeaSentenceExtractor()
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
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
                }
            }
        }
        Files.move(temporaryOutput, outputNmea, StandardCopyOption.REPLACE_EXISTING)
        return Um980NmeaReexportResult(
            outputNmea = outputNmea,
            sentencesWritten = sentencesWritten,
        )
    }
}
