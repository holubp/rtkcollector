package org.rtkcollector.receiver.unicore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Um980ModeParserTest {
    @Test
    fun `parses rover survey command`() {
        assertEquals("ROVER SURVEY", Um980ModeParser.configuredMode("UNLOG COM1\nMODE ROVER SURVEY\nBESTNAVB COM1 1"))
    }

    @Test
    fun `parses rover automotive command`() {
        assertEquals("ROVER AUTOMOTIVE", Um980ModeParser.configuredMode("MODE ROVER AUTOMOTIVE"))
    }

    @Test
    fun `parses plain rover command`() {
        assertEquals("ROVER", Um980ModeParser.configuredMode("MODE ROVER"))
    }
}
