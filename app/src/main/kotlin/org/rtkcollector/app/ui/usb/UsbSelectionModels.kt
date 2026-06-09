package org.rtkcollector.app.ui.usb

data class UsbDeviceChoice(
    val vendorId: Int,
    val productId: Int,
    val deviceName: String,
    val productName: String? = null,
) {
    val label: String =
        listOfNotNull(
            productName?.takeIf { it.isNotBlank() },
            deviceName.takeIf { it.isNotBlank() },
            "VID:%04X PID:%04X".format(vendorId, productId),
        ).joinToString(" - ")

    fun toProfileValue(): String =
        listOf(vendorId.toString(), productId.toString(), deviceName, productName.orEmpty()).joinToString("\t")

    companion object {
        fun fromProfileValue(value: String): UsbDeviceChoice? {
            if (value.isBlank()) return null
            val parts = value.split('\t', limit = 4)
            if (parts.size < 3) return null
            return UsbDeviceChoice(
                vendorId = parts[0].toIntOrNull() ?: return null,
                productId = parts[1].toIntOrNull() ?: return null,
                deviceName = parts[2],
                productName = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
            )
        }
    }
}

data class BaudSelectorState(
    val allowedBaudRates: List<Int> = ALLOWED_UM980_BAUD_RATES,
    val selectedBaudRate: Int = 230400,
) {
    init {
        require(selectedBaudRate in allowedBaudRates) {
            "Selected baud rate must be one of the allowed values."
        }
    }

    fun select(baudRate: Int): BaudSelectorState =
        copy(selectedBaudRate = baudRate)

    companion object {
        val ALLOWED_UM980_BAUD_RATES = listOf(4800, 9600, 14400, 19200, 38400, 57600, 115200, 128000, 230400, 256000, 460800, 921600)
    }
}
