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
        val ALLOWED_UM980_BAUD_RATES = listOf(9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600)
    }
}
