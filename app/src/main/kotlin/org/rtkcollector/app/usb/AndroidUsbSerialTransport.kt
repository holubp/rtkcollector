package org.rtkcollector.app.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import org.rtkcollector.core.transport.SerialTransport
import kotlin.math.min

class AndroidUsbSerialTransport(
    private val usbManager: UsbManager,
    private val device: UsbDevice,
    private val options: UsbSerialOpenOptions,
) : SerialTransport {
    private var connection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
    private var ftdiMode: Boolean = false
    private var inMaxPacketSize: Int = 64

    override val isOpen: Boolean
        get() = connection != null

    override fun open() {
        if (isOpen) return
        val selected = selectEndpoint(device) ?: error("No usable USB serial IN endpoint found.")
        val opened = usbManager.openDevice(device) ?: error("Unable to open USB device.")
        if (!opened.claimInterface(selected.usbInterface, true)) {
            opened.close()
            error("Unable to claim USB interface ${selected.usbInterface.id}.")
        }

        connection = opened
        claimedInterface = selected.usbInterface
        inEndpoint = selected.inEndpoint
        outEndpoint = selected.outEndpoint
        inMaxPacketSize = selected.inEndpoint.maxPacketSize.coerceAtLeast(64)
        ftdiMode = device.vendorId == FTDI_VENDOR_ID
        if (ftdiMode) {
            configureFtdi(opened, selected.usbInterface.id, options.baudRate)
        }
    }

    override fun close() {
        val opened = connection
        val iface = claimedInterface
        if (opened != null && iface != null) {
            runCatching { opened.releaseInterface(iface) }
        }
        runCatching { opened?.close() }
        connection = null
        claimedInterface = null
        inEndpoint = null
        outEndpoint = null
    }

    override fun readAvailable(maxBytes: Int): ByteArray {
        require(maxBytes > 0) { "maxBytes must be positive" }
        val opened = connection ?: error("USB serial transport is not open.")
        val endpoint = inEndpoint ?: error("USB serial IN endpoint is not open.")
        val buffer = ByteArray(maxBytes)
        val count = opened.bulkTransfer(endpoint, buffer, buffer.size, options.readTimeoutMillis)
        if (count <= 0) return byteArrayOf()
        val received = buffer.copyOf(count)
        return if (ftdiMode) stripFtdiStatus(received, inMaxPacketSize) else received
    }

    override fun write(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val opened = connection ?: error("USB serial transport is not open.")
        val endpoint = outEndpoint ?: error("USB serial OUT endpoint is not available.")
        var offset = 0
        while (offset < bytes.size) {
            val chunkSize = min(MAX_WRITE_CHUNK, bytes.size - offset)
            val chunk = bytes.copyOfRange(offset, offset + chunkSize)
            val written = opened.bulkTransfer(endpoint, chunk, chunk.size, options.writeTimeoutMillis)
            if (written <= 0) {
                error("USB serial write failed.")
            }
            offset += written
        }
    }

    fun reconfigureBaud(baudRate: Int) {
        val opened = connection ?: error("USB serial transport is not open.")
        val iface = claimedInterface ?: error("USB serial interface is not open.")
        if (ftdiMode) {
            setFtdiLineCoding(opened, iface.id, baudRate)
        }
    }

    private fun configureFtdi(connection: UsbDeviceConnection, interfaceId: Int, baudRate: Int) {
        val index = interfaceId + 1
        control(connection, request = 0, value = 0, index = index, label = "FTDI reset")
        control(connection, request = 0, value = 1, index = index, label = "FTDI RX purge")
        control(connection, request = 0, value = 2, index = index, label = "FTDI TX purge")
        setFtdiLineCoding(connection, interfaceId, baudRate)
    }

    private fun setFtdiLineCoding(connection: UsbDeviceConnection, interfaceId: Int, baudRate: Int) {
        val index = interfaceId + 1
        control(connection, request = 4, value = 8, index = index, label = "FTDI 8N1")
        val divisor = ftdiBaudDivisor(baudRate)
        val value = divisor and 0xffff
        val baudIndex = index or ((divisor shr 8) and 0xff00)
        control(connection, request = 3, value = value, index = baudIndex, label = "FTDI baud")
    }

    private fun control(
        connection: UsbDeviceConnection,
        request: Int,
        value: Int,
        index: Int,
        label: String,
    ) {
        val result = connection.controlTransfer(0x40, request, value, index, null, 0, 1000)
        if (result < 0) {
            error("$label control transfer failed.")
        }
    }

    private data class SelectedEndpoint(
        val usbInterface: UsbInterface,
        val inEndpoint: UsbEndpoint,
        val outEndpoint: UsbEndpoint?,
    )

    private companion object {
        const val FTDI_VENDOR_ID = 0x0403
        const val MAX_WRITE_CHUNK = 4096
        val FTDI_FRAC_CODE = intArrayOf(0, 3, 2, 4, 1, 5, 6, 7)

        fun selectEndpoint(device: UsbDevice): SelectedEndpoint? {
            var selected: SelectedEndpoint? = null
            var bestScore = Int.MIN_VALUE
            for (interfaceIndex in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(interfaceIndex)
                var inEndpoint: UsbEndpoint? = null
                var outEndpoint: UsbEndpoint? = null
                var hasBulkIn = false
                var hasInterruptIn = false
                var hasBulkOut = false
                for (endpointIndex in 0 until usbInterface.endpointCount) {
                    val endpoint = usbInterface.getEndpoint(endpointIndex)
                    val isIn = endpoint.direction == UsbConstants.USB_DIR_IN
                    val isBulk = endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                    val isInterrupt = endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT
                    if (isIn && isBulk) {
                        inEndpoint = endpoint
                        hasBulkIn = true
                    } else if (isIn && isInterrupt && inEndpoint == null) {
                        inEndpoint = endpoint
                        hasInterruptIn = true
                    } else if (!isIn && isBulk) {
                        outEndpoint = endpoint
                        hasBulkOut = true
                    }
                }
                val score = when {
                    hasBulkIn -> 100
                    hasInterruptIn -> 10
                    else -> -1000
                } + if (hasBulkOut) 50 else 0
                if (inEndpoint != null && score > bestScore) {
                    selected = SelectedEndpoint(usbInterface, inEndpoint, outEndpoint)
                    bestScore = score
                }
            }
            return selected
        }

        fun stripFtdiStatus(bytes: ByteArray, maxPacketSize: Int): ByteArray {
            val packetSize = maxPacketSize.coerceAtLeast(64)
            val payload = ArrayList<Byte>(bytes.size)
            var offset = 0
            while (offset < bytes.size) {
                val packetLen = min(packetSize, bytes.size - offset)
                if (packetLen > 2) {
                    for (index in offset + 2 until offset + packetLen) {
                        payload += bytes[index]
                    }
                }
                offset += packetLen
            }
            return payload.toByteArray()
        }

        fun ftdiBaudDivisor(baudRate: Int): Int {
            require(baudRate > 0) { "baudRate must be positive" }
            val divisor3 = ((24_000_000L + baudRate / 2L) / baudRate).coerceAtLeast(1).toInt()
            var divisor = (divisor3 shr 3) or (FTDI_FRAC_CODE[divisor3 and 7] shl 14)
            if (divisor == 1) {
                divisor = 0
            } else if (divisor == 0x4001) {
                divisor = 1
            }
            return divisor
        }
    }
}
