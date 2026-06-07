package org.rtkcollector.receiver.unicore

object Um980RuntimeProfiles {
    fun experimentalRoverBasePreparation(): Um980RuntimeProfile =
        Um980RuntimeProfile(
            id = "experimental-rover-base-preparation",
            displayName = "Experimental rover/base preparation",
            enabled = true,
            runtimeOnly = true,
            commands = listOf(
                "UNLOG COM1",
                "MODE ROVER",
                "CONFIG MMP ENABLE",
                "VERSIONB",
                "BESTNAVB COM1 0.2",
                "OBSVMCMPB COM1 1",
                "GPSEPHB COM1 300",
                "GLOEPHB COM1 300",
                "GALEPHB COM1 300",
                "BDSEPHB COM1 300",
                "BD3EPHB COM1 300",
                "QZSSEPHB COM1 300",
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
