package org.rtkcollector.app.usb

import android.hardware.usb.UsbDevice

data class UsbDeviceSummary(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val interfaceCount: Int,
) {
    val label: String =
        "$deviceName VID=%04x PID=%04x interfaces=$interfaceCount".format(vendorId, productId)

    companion object {
        fun from(device: UsbDevice): UsbDeviceSummary =
            UsbDeviceSummary(
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                interfaceCount = device.interfaceCount,
            )
    }
}

data class UsbSerialOpenOptions(
    val baudRate: Int,
    val readTimeoutMillis: Int = 1000,
    val writeTimeoutMillis: Int = 2000,
)
