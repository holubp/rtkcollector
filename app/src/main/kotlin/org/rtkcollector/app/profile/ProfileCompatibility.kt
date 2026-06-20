package org.rtkcollector.app.profile

enum class ProfileCompatibilityStatus {
    COMPATIBLE,
    INCOMPATIBLE,
    WARNING,
}

enum class BaudCompatibilityStatus {
    RECOMMENDED,
    KNOWN_WORKING,
    UNTESTED,
    UNUSUAL,
}

data class ProfileCompatibilityResult(
    val status: ProfileCompatibilityStatus,
    val editable: Boolean = true,
    val activatable: Boolean,
    val reason: String? = null,
    val baudStatus: BaudCompatibilityStatus? = null,
)

object ProfileCompatibility {
    fun commandProfile(
        receiverProfileId: String,
        commandProfile: CommandProfile,
    ): ProfileCompatibilityResult {
        val compatible = receiverFamilyCompatible(receiverProfileId, commandProfile.receiverFamily)
        return ProfileCompatibilityResult(
            status = if (compatible) ProfileCompatibilityStatus.COMPATIBLE else ProfileCompatibilityStatus.INCOMPATIBLE,
            activatable = compatible,
            reason = if (compatible) null else "Command profile is for ${commandProfile.receiverFamily}, not $receiverProfileId.",
        )
    }

    fun rtklibProfile(
        receiverProfileId: String,
        commandProfile: CommandProfile,
        rtklibProfile: RtklibProfile,
    ): ProfileCompatibilityResult {
        if (!rtklibProfile.enabled) {
            return ProfileCompatibilityResult(
                status = ProfileCompatibilityStatus.COMPATIBLE,
                activatable = true,
            )
        }

        val commandCompatible = commandProfile(receiverProfileId, commandProfile)
        if (!commandCompatible.activatable) {
            return commandCompatible
        }

        val script = commandProfile.runtimeScript.uppercase()
        val hasRoute = when {
            receiverProfileId.startsWith("ublox", ignoreCase = true) ->
                "RXM-RAWX" in script || "CFG-MSG 2 21" in script
            receiverProfileId.startsWith("um980", ignoreCase = true) ->
                "OBSVMB" in script || "OBSVMCMPB" in script
            else -> false
        }

        return ProfileCompatibilityResult(
            status = if (hasRoute) ProfileCompatibilityStatus.COMPATIBLE else ProfileCompatibilityStatus.INCOMPATIBLE,
            activatable = hasRoute,
            reason = if (hasRoute) null else "RTKLIB profile requires raw observations compatible with $receiverProfileId.",
        )
    }

    fun baudProfile(
        receiverProfileId: String,
        usbBaudProfile: UsbBaudProfile,
    ): ProfileCompatibilityResult {
        val status = baudStatus(receiverProfileId, usbBaudProfile.serialBaud)
        return ProfileCompatibilityResult(
            status = if (status == BaudCompatibilityStatus.UNUSUAL) {
                ProfileCompatibilityStatus.WARNING
            } else {
                ProfileCompatibilityStatus.COMPATIBLE
            },
            activatable = true,
            reason = if (status == BaudCompatibilityStatus.UNUSUAL) {
                "${usbBaudProfile.serialBaud} baud is allowed but unusual for $receiverProfileId."
            } else {
                null
            },
            baudStatus = status,
        )
    }

    private fun receiverFamilyCompatible(receiverProfileId: String, receiverFamily: String): Boolean {
        val receiver = receiverProfileId.lowercase()
        val family = receiverFamily.lowercase()
        return receiver == family ||
            receiver.startsWith(family) ||
            family.startsWith(receiver) ||
            (receiver.startsWith("um980") && family.startsWith("um980")) ||
            (receiver.startsWith("ublox") && family.startsWith("ublox") && receiver == family)
    }

    private fun baudStatus(receiverProfileId: String, baud: Int): BaudCompatibilityStatus {
        val receiver = receiverProfileId.lowercase()
        return when {
            receiver.startsWith("um980") && baud == 230400 -> BaudCompatibilityStatus.RECOMMENDED
            receiver.startsWith("um980") && baud in setOf(115200, 460800, 921600) -> BaudCompatibilityStatus.KNOWN_WORKING
            receiver.startsWith("ublox") && baud in setOf(9600, 38400, 115200, 230400) -> BaudCompatibilityStatus.KNOWN_WORKING
            baud in setOf(4800, 9600, 14400, 19200, 38400, 57600, 115200, 128000, 230400, 256000, 460800, 921600) ->
                BaudCompatibilityStatus.UNTESTED
            else -> BaudCompatibilityStatus.UNUSUAL
        }
    }
}
