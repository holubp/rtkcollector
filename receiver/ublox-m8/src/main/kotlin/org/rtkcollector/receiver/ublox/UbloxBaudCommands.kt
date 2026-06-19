package org.rtkcollector.receiver.ublox

object UbloxBaudCommands {
    private const val UART1_PORT_ID = 1
    private const val MODE_8N1 = 0x000008D0
    private const val INPUT_UBX_NMEA_RTCM3 = 0x0007
    private const val OUTPUT_UBX_NMEA = 0x0003

    fun uart1BaudCommand(baud: Int): String {
        require(baud in 9_600..921_600) { "u-blox UART1 baud must be 9600..921600." }
        return "!UBX CFG-PRT $UART1_PORT_ID 0 0 $MODE_8N1 $baud $INPUT_UBX_NMEA_RTCM3 $OUTPUT_UBX_NMEA 0 0"
    }
}
