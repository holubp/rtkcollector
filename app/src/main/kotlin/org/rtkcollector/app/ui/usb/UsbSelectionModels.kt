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

enum class UsbStartAccessAction {
    NO_DEVICE,
    REQUEST_PERMISSION,
    VERIFY_AND_START,
}

data class UsbStartAccessResult(
    val action: UsbStartAccessAction,
    val message: String,
)

object UsbStartAccessDecision {
    fun evaluate(
        deviceConnected: Boolean,
        permissionReportedGranted: Boolean,
    ): UsbStartAccessResult =
        when {
            !deviceConnected -> UsbStartAccessResult(
                action = UsbStartAccessAction.NO_DEVICE,
                message = "Selected USB receiver is not connected.",
            )
            !permissionReportedGranted -> UsbStartAccessResult(
                action = UsbStartAccessAction.REQUEST_PERMISSION,
                message = "USB permission requested. Approve the Android permission dialog, then press Start again.",
            )
            else -> UsbStartAccessResult(
                action = UsbStartAccessAction.VERIFY_AND_START,
                message = "USB permission reported granted; access will be verified on Start.",
            )
        }

    fun permissionDeniedMessage(): String =
        "USB permission was denied. Approve USB access before starting recording."

    fun permissionGrantedMessage(): String =
        "USB permission granted. Press Start again to begin recording."

    fun openFailureMessage(): String =
        "Android reports USB permission, but the receiver could not be opened. Reconnect the receiver, close other serial apps, then retry USB access."
}
