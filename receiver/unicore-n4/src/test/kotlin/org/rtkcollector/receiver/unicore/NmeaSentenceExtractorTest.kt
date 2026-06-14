package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NmeaSentenceExtractorTest {
    @Test
    fun `extracts valid nmea sentences with crlf terminator`() {
        val extractor = NmeaSentenceExtractor()

        val sentences = extractor.accept(
            "\$GNGGA,151437.150,4914.6094192,N,01634.8215775,E,2,8,,278.536,M,44.249,M,2.2,1022*79\r\n".encodeToByteArray(),
        )

        assertEquals(
            listOf("\$GNGGA,151437.150,4914.6094192,N,01634.8215775,E,2,8,,278.536,M,44.249,M,2.2,1022*79\r\n"),
            sentences,
        )
    }

    @Test
    fun `rejects binary fragments that only look like dollar-prefixed lines`() {
        val extractor = NmeaSentenceExtractor()

        val sentences = extractor.accept(byteArrayOf('$'.code.toByte(), 0x01, 0x02, 0x03, '\n'.code.toByte()))

        assertEquals(emptyList<String>(), sentences)
    }

    @Test
    fun `rejects checksum valid lines containing control bytes`() {
        val extractor = NmeaSentenceExtractor()
        val sentence = checksummedSentence("GPGGA,\u0001")

        val sentences = extractor.accept(sentence.encodeToByteArray())

        assertEquals(emptyList<String>(), sentences)
    }

    @Test
    fun `buffers partial sentence across chunks`() {
        val extractor = NmeaSentenceExtractor()

        assertEquals(emptyList<String>(), extractor.accept("\$GNRMC,151437.150,A".encodeToByteArray()))
        val sentences = extractor.accept(",4914.6094192,N,01634.8215775,E,0.167,278.043,140626,,,D*7B\r\n".encodeToByteArray())

        assertEquals(
            listOf("\$GNRMC,151437.150,A,4914.6094192,N,01634.8215775,E,0.167,278.043,140626,,,D*7B\r\n"),
            sentences,
        )
    }

    private fun checksummedSentence(body: String): String {
        val checksum = body.fold(0) { value, character -> value xor character.code }
        return "\$$body*%02X\r\n".format(checksum)
    }
}
