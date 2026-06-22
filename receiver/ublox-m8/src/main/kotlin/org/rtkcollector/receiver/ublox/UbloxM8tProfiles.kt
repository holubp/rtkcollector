package org.rtkcollector.receiver.ublox

object UbloxM8tProfiles {
    val raw1HzSafe: String = """
        !UBX CFG-RATE 1000 1 1
        !UBX CFG-MSG 2 21 0 0 0 1 0 0
        !UBX CFG-MSG 2 19 0 0 0 1 0 0
        !UBX CFG-MSG 13 3 0 0 0 1 0 0
        !UBX CFG-MSG 1 7 0 0 0 1 0 0
        !UBX CFG-MSG 1 53 0 0 0 1 0 0
        !UBX CFG-MSG 240 0 0 0 0 0 0 0
        !UBX CFG-MSG 240 1 0 0 0 0 0 0
        !UBX CFG-MSG 240 2 0 0 0 0 0 0
        !UBX CFG-MSG 240 3 0 0 0 0 0 0
        !UBX CFG-MSG 240 4 0 0 0 0 0 0
        !UBX CFG-MSG 240 5 0 0 0 0 0 0
        !UBX CFG-MSG 240 8 0 0 0 0 0 0
    """.trimIndent()

    val raw5HzRtklibEx: String = """
        !UBX CFG-MSG 2 21 0 0 0 1 0 0
        !UBX CFG-MSG 2 19 0 0 0 1 0 0
        !UBX CFG-MSG 13 3 0 0 0 1 0 0
        !UBX CFG-MSG 1 7 0 0 0 1 0 0
        !UBX CFG-MSG 1 53 0 0 0 1 0 0
        !UBX CFG-MSG 1 4 0 0 0 1 0 0
        !UBX CFG-GNSS 0 32 32 1 0 8 16 0 65537
        !UBX CFG-GNSS 0 32 32 1 1 1 3 0 65537
        !UBX CFG-GNSS 0 32 32 1 2 4 8 0 65537
        !UBX CFG-GNSS 0 32 32 1 3 8 16 0 0
        !UBX CFG-GNSS 0 32 32 1 4 0 8 0 0
        !UBX CFG-GNSS 0 32 32 1 5 0 3 0 0
        !UBX CFG-GNSS 0 32 32 1 6 8 14 0 65537
        !UBX CFG-NAV5 1 3 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
        !UBX CFG-MSG 240 0 0 0 0 0 0 0
        !UBX CFG-MSG 240 1 0 0 0 0 0 0
        !UBX CFG-MSG 240 2 0 0 0 0 0 0
        !UBX CFG-MSG 240 3 0 0 0 0 0 0
        !UBX CFG-MSG 240 4 0 0 0 0 0 0
        !UBX CFG-MSG 240 5 0 0 0 0 0 0
        !UBX CFG-MSG 240 8 0 0 0 0 0 0
        !UBX CFG-RATE 200 1 1
    """.trimIndent()

    val rawStatusMock: String = """
        !UBX CFG-RATE 1000 1 1
        !UBX CFG-MSG 2 21 0 0 0 1 0 0
        !UBX CFG-MSG 2 19 0 0 0 1 0 0
        !UBX CFG-MSG 13 3 0 0 0 1 0 0
        !UBX CFG-MSG 1 7 0 0 0 1 0 0
        !UBX CFG-MSG 1 53 0 0 0 1 0 0
        !UBX CFG-MSG 1 4 0 0 0 1 0 0
        !UBX CFG-MSG 240 0 0 0 0 0 0 0
        !UBX CFG-MSG 240 1 0 0 0 0 0 0
        !UBX CFG-MSG 240 2 0 0 0 0 0 0
        !UBX CFG-MSG 240 3 0 0 0 0 0 0
        !UBX CFG-MSG 240 4 0 0 0 0 0 0
        !UBX CFG-MSG 240 5 0 0 0 0 0 0
    """.trimIndent()
}
