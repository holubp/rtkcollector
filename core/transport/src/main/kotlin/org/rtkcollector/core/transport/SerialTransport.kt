package org.rtkcollector.core.transport

interface SerialTransport {
    val isOpen: Boolean

    fun open()
    fun close()
    fun readAvailable(maxBytes: Int): ByteArray
    fun write(bytes: ByteArray)
}

class UsbSerialTransportPlaceholder : SerialTransport {
    override val isOpen: Boolean = false

    override fun open() {
        error("USB serial transport is not implemented in the bootstrap skeleton.")
    }

    override fun close() = Unit

    override fun readAvailable(maxBytes: Int): ByteArray =
        error("USB serial transport is not implemented in the bootstrap skeleton.")

    override fun write(bytes: ByteArray) {
        error("USB serial transport is not implemented in the bootstrap skeleton.")
    }
}
