package org.rtkcollector.receiver.unicore

object Um980RuntimeProfiles {
    fun experimentalRoverBasePreparation(comPort: String = "COM1", baudRate: Int = 230400): Um980RuntimeProfile {
        require(comPort.matches(Regex("COM[1-8]"))) { "UM980 COM port must be COM1..COM8." }
        require(baudRate in setOf(9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600)) {
            "UM980 baud rate must be one of the supported runtime profile values."
        }
        return Um980RuntimeProfile(
            id = "experimental-rover-base-preparation",
            displayName = "Experimental rover/base preparation",
            enabled = true,
            runtimeOnly = true,
            commands = listOf(
                "UNLOG $comPort",
                "MODE ROVER",
                "CONFIG MMP ENABLE",
                "CONFIG PPP ENABLE E6-HAS",
                "CONFIG PPP DATUM WGS84",
                "CONFIG PPP TIMEOUT 120",
                "CONFIG PPP CONVERGE 15 30",
                "VERSIONB",
                "BESTNAVB $comPort 0.05",
                "PPPNAVB $comPort 1",
                "OBSVMCMPB $comPort 0.25",
                "STADOPB $comPort 1",
                "GPSEPHB $comPort 300",
                "GLOEPHB $comPort 300",
                "GALEPHB $comPort 300",
                "BDSEPHB $comPort 300",
                "BD3EPHB $comPort 300",
                "QZSSEPHB $comPort 300",
                "GPSIONB ONCHANGED",
                "BDSIONB ONCHANGED",
                "BD3IONB ONCHANGED",
                "GALIONB ONCHANGED",
                "GPSUTCB ONCHANGED",
                "BDSUTCB ONCHANGED",
                "BD3UTCB ONCHANGED",
                "GALUTCB ONCHANGED",
            ),
        )
    }

    fun legacyExperimentalRoverBasePreparation(): Um980RuntimeProfile =
        experimentalRoverBasePreparation()
}
