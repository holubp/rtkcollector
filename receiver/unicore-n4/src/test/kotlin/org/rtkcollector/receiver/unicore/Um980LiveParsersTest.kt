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
        assertEquals(545.4, fix.altitudeM)
        assertEquals(46.9, fix.geoidSeparationM)
        assertEquals(592.3, fix.ellipsoidalHeightM!!, 0.0001)
    }

    @Test
    fun `parses GGA differential age and station id`() {
        val fix = NmeaGgaParser().parseLine(
            "\$GNGGA,120000.00,5000.0000,N,01400.0000,E,5,20,0.7,250.0,M,45.0,M,0.5,0001*00",
        )

        requireNotNull(fix)
        assertEquals(0.5, fix.differentialAgeS)
        assertEquals("0001", fix.stationId)
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
    fun `parses GSA dop and satellite use`() {
        val dop = NmeaGsaParser().parseLine(
            "\$GNGSA,A,3,04,05,09,12,24,25,29,31,32,36,40,45,1.2,0.8,0.9*00",
        )

        requireNotNull(dop)
        assertEquals(3, dop.fixMode)
        assertEquals(12, dop.satellitesUsed)
        assertEquals(1.2, dop.pdop)
        assertEquals(0.8, dop.hdop)
        assertEquals(0.9, dop.vdop)
    }

    @Test
    fun `parses GSV satellites in view`() {
        val view = NmeaGsvParser().parseLine(
            "\$GNGSV,3,1,31,01,40,120,42,02,30,130,40,03,20,140,35,04,10,150,30*00",
        )

        requireNotNull(view)
        assertEquals("GNGSV", view.talker)
        assertEquals(31, view.satellitesInView)
    }

    @Test
    fun `GSV tracker sums in view totals by constellation talker`() {
        val parser = NmeaGsvParser()
        val tracker = NmeaGsvInViewTracker()
        val views = parser.acceptText(
            "\$GPGSV,3,1,09,01,40,120,42*00\r\n" +
                "\$GLGSV,2,1,08,65,40,120,42*00\r\n",
        )

        views.forEach(tracker::accept)

        assertEquals(17, tracker.satellitesInView)
    }

    @Test
    fun `GSV tracker keeps maximum visible satellites across same-constellation signal groups`() {
        val parser = NmeaGsvParser()
        val tracker = NmeaGsvInViewTracker()
        val views = parser.acceptText(
            "\$GPGSV,3,1,10,03,20,119,39,04,55,062,46,06,52,245,48,07,42,180,47,1*62\r\n" +
                "\$GPGSV,1,1,02,04,54,062,46,11,36,301,45,9*6B\r\n" +
                "\$GLGSV,2,1,08,70,37,066,46,86,42,248,48,73,21,138,42,80,37,076,49,1*73\r\n" +
                "\$GLGSV,2,1,07,70,37,066,43,86,42,248,40,73,21,138,33,80,37,076,39,3*73\r\n" +
                "\$GBGSV,3,1,12,02,08,110,40,07,19,038,43,10,29,045,43,24,37,084,48,1*74\r\n" +
                "\$GBGSV,1,1,01,10,29,045,42,B*39\r\n" +
                "\$GAGSV,3,1,10,07,21,062,38,10,28,315,42,12,47,296,42,19,51,246,44,1*70\r\n" +
                "\$GAGSV,3,1,09,07,21,062,40,10,28,315,42,12,47,296,39,19,51,246,37,5*7B\r\n",
        )

        views.forEach(tracker::accept)

        assertEquals(40, tracker.satellitesInView)
    }

    @Test
    fun `parses GST position error estimates`() {
        val gst = NmeaGstParser().parseLine(
            "\$GNGST,120000.00,0.10,0.03,0.02,45.0,0.012,0.010,0.025*00",
        )

        requireNotNull(gst)
        assertEquals("120000.00", gst.utcTime)
        assertEquals(0.012, gst.latErrorM)
        assertEquals(0.010, gst.lonErrorM)
        assertEquals(0.025, gst.heightErrorM)
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
