package org.rtkcollector.receiver.unicore

class NmeaSentenceExtractor {
    private val lineBuffer = StringBuilder()

    fun accept(bytes: ByteArray): List<String> {
        val sentences = mutableListOf<String>()
        bytes.toString(Charsets.US_ASCII).forEach { character ->
            when (character) {
                '\n' -> {
                    val line = lineBuffer.toString().trim()
                    if (isValidNmeaSentence(line)) {
                        sentences += "$line\r\n"
                    }
                    lineBuffer.clear()
                }
                '\r' -> Unit
                else -> lineBuffer.append(character)
            }
        }
        return sentences
    }

    private fun isValidNmeaSentence(line: String): Boolean {
        if (line.length < MIN_NMEA_LENGTH || line.first() != '$') return false
        val star = line.lastIndexOf('*')
        if (star < 0 || star + 3 != line.length) return false
        val formatter = line.substring(1, 6)
        if (!formatter.all { it in 'A'..'Z' || it in '0'..'9' }) return false
        if (!line.substring(star + 1).all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }) return false
        val body = line.substring(1, star)
        if (!body.all { it.code in PRINTABLE_ASCII_RANGE }) return false
        val expected = body.fold(0) { checksum, char -> checksum xor char.code }
        val actual = line.substring(star + 1).toIntOrNull(16) ?: return false
        return expected == actual
    }

    private companion object {
        const val MIN_NMEA_LENGTH = 9
        val PRINTABLE_ASCII_RANGE = 0x20..0x7e
    }
}
