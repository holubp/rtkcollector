package org.rtkcollector.app.recording

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build

internal const val USB_RECOVERY_PERMISSION = "org.rtkcollector.app.USB_RECOVERY_PERMISSION"
internal const val USB_RECOVERY_SCHEME = "rtkcollector-usb-consent"

internal fun Intent.platformUsbDevice(): UsbDevice? = if (Build.VERSION.SDK_INT >= 33) {
    getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
} else {
    @Suppress("DEPRECATION")
    getParcelableExtra(UsbManager.EXTRA_DEVICE)
}

internal fun usbConsentIntent(context: Context, ticket: UsbConsentTicket): Intent =
    Intent(USB_RECOVERY_PERMISSION).setPackage(context.packageName).setData(
        Uri.Builder().scheme(USB_RECOVERY_SCHEME).authority("permission")
            .appendPath(ticket.generation.toString()).appendPath(ticket.deviceId.toString())
            .appendPath(ticket.nonce.toString()).build(),
    )

internal fun usbConsentTicket(intent: Intent): UsbConsentTicket? {
    if (intent.action != USB_RECOVERY_PERMISSION) return null
    val uri = intent.data ?: return null
    if (uri.scheme != USB_RECOVERY_SCHEME || uri.authority != "permission") return null
    val parts = uri.pathSegments
    if (parts.size != 3) return null
    return UsbConsentTicket(parts[0].toLongOrNull() ?: return null,
        parts[1].toIntOrNull() ?: return null, parts[2].toLongOrNull() ?: return null)
}

internal fun usbConsentFilter() = IntentFilter(USB_RECOVERY_PERMISSION).apply { addDataScheme(USB_RECOVERY_SCHEME) }

internal fun usbConsentPendingIntent(context: Context, ticket: UsbConsentTicket): PendingIntent =
    PendingIntent.getBroadcast(context, 0, usbConsentIntent(context, ticket),
        PendingIntent.FLAG_CANCEL_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
