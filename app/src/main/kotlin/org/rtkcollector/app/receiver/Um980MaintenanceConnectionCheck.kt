package org.rtkcollector.app.receiver

fun um980VersionProbeBytes(): ByteArray =
    "VERSION\r\n".toByteArray(Charsets.US_ASCII)

fun isPlausibleUm980MaintenanceResponse(bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    val ascii = bytes.toString(Charsets.US_ASCII)
    return ascii.contains("UM980", ignoreCase = true) ||
        ascii.contains("Unicore", ignoreCase = true) ||
        ascii.contains("#VERSION", ignoreCase = true)
}

fun isUm980CommandOkResponse(bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    val normalized = bytes
        .toString(Charsets.US_ASCII)
        .replace(" ", "")
    return normalized.contains(",response:OK", ignoreCase = true)
}
