package org.rtkcollector.app.receiver

import org.rtkcollector.receiver.unicore.Um980PersistentBaudPlan
import org.rtkcollector.receiver.unicore.Um980RuntimeCommandValidator

val SupportedPersistentBaudRates: Set<Int> = setOf(
    4800,
    9600,
    14400,
    19200,
    38400,
    57600,
    115200,
    128000,
    230400,
    256000,
    460800,
    921600,
)

fun persistentReceiverCommands(initScript: String): List<String> =
    initScript
        .lineSequence()
        .map(String::trim)
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .filterNot { it.equals("SAVECONFIG", ignoreCase = true) }
        .onEach(Um980RuntimeCommandValidator::validateRuntimeCommand)
        .toList() + "SAVECONFIG"

fun persistentBaudCommands(targetBaud: Int): List<String> {
    require(targetBaud in SupportedPersistentBaudRates) {
        "Target baud must be one of ${SupportedPersistentBaudRates.sorted().joinToString()}."
    }
    return Um980PersistentBaudPlan.commands(targetBaud)
}
