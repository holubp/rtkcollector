package org.rtkcollector.receiver.ublox

object UbloxFrame {
    fun build(messageClass: Int, messageId: Int, payload: ByteArray): ByteArray {
        require(messageClass in 0..255) { "UBX message class must be 0..255." }
        require(messageId in 0..255) { "UBX message id must be 0..255." }
        require(payload.size <= 65_535) { "UBX payload is too large: ${payload.size} bytes." }
        val headerAndPayload = ByteArray(4 + payload.size)
        headerAndPayload[0] = messageClass.toByte()
        headerAndPayload[1] = messageId.toByte()
        headerAndPayload[2] = (payload.size and 0xff).toByte()
        headerAndPayload[3] = ((payload.size ushr 8) and 0xff).toByte()
        payload.copyInto(headerAndPayload, destinationOffset = 4)
        val checksum = checksum(headerAndPayload)
        return byteArrayOf(0xB5.toByte(), 0x62) + headerAndPayload + checksum
    }

    fun isValid(frame: ByteArray): Boolean {
        if (frame.size < 8 || frame[0] != 0xB5.toByte() || frame[1] != 0x62.toByte()) return false
        val length = (frame[4].toInt() and 0xff) or ((frame[5].toInt() and 0xff) shl 8)
        if (frame.size != 8 + length) return false
        val expected = checksum(frame.copyOfRange(2, frame.size - 2))
        return frame[frame.size - 2] == expected[0] && frame[frame.size - 1] == expected[1]
    }

    fun checksum(classIdLengthPayload: ByteArray): ByteArray {
        var ckA = 0
        var ckB = 0
        classIdLengthPayload.forEach { byte ->
            ckA = (ckA + (byte.toInt() and 0xff)) and 0xff
            ckB = (ckB + ckA) and 0xff
        }
        return byteArrayOf(ckA.toByte(), ckB.toByte())
    }
}
