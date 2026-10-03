package org.rtkcollector.app.profile

import org.rtkcollector.receiver.ublox.UbloxBaudCommands

internal fun receiverBaudTransitionCommands(family: String, initialBaud: Int, targetBaud: Int): List<String> = when {
    initialBaud == targetBaud -> emptyList()
    family.startsWith("ublox", ignoreCase = true) -> listOf(UbloxBaudCommands.uart1BaudCommand(targetBaud))
    family.lowercase() in setOf("um980", "um980-n4", "unicore-n4") -> listOf("CONFIG COM1 $targetBaud")
    else -> throw IllegalArgumentException(
        "Automatic baud transition is unsupported for $family; use matching initial and target baud.")
}
