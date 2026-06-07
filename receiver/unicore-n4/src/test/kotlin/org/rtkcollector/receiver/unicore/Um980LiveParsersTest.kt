package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Um980LiveParsersTest {
    @Test
    fun `parses GGA fix quality and position`() {
        val fix = NmeaGgaParser().parseLine(
            "\$GPGGA,123519,4807.038,N,01131.000,E,4,12,0.8,545.4,M,46.9,M,,*47",
        )

        requireNotNull(fix)
        assertEquals(4, fix.fixQuality)
        assertEquals(12, fix.satelliteCount)
        assertEquals(48.1173, fix.latDeg!!, 0.0001)
        assertEquals(11.5167, fix.lonDeg!!, 0.0001)
    }

    @Test
    fun `parses UM980 PPP ASCII solution status`() {
        val solution = Um980AsciiSolutionParser().parseLine(
            "#PPPNAVA,64,GPS,FINE,2207,464961000,0,0,18,13;SOL_COMPUTED,PPP_CONVERGING,40.07899442145,116.23661087189,65.8944,8.4923,WGS84*2d9412be",
        )

        requireNotNull(solution)
        assertEquals("PPPNAVA", solution.logName)
        assertEquals("SOL_COMPUTED", solution.solutionStatus)
        assertEquals("PPP_CONVERGING", solution.positionType)
        assertEquals(40.07899442145, solution.latDeg)
    }

    @Test
    fun `buffers partial parser lines`() {
        val parser = NmeaGgaParser()

        assertEquals(emptyList<NmeaGgaFix>(), parser.acceptText("\$GNGGA,120000,5000.0,N,01400.0,E"))
        val fixes = parser.acceptText(",1,08,1.0,250.0,M,0.0,M,0.0,0000*00\r\n")

        assertEquals(1, fixes.size)
        assertEquals(1, fixes.single().fixQuality)
    }
}
