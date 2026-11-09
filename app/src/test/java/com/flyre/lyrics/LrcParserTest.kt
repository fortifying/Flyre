package com.flyre.lyrics

import com.flyre.lyrics.data.repository.LyricsRepository
import com.flyre.lyrics.data.util.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {

    @Test
    fun testParseStandardLrc() {
        val lrc = """
            [00:05.50]First line
            [00:10.20]Second line
            [00:20.00]Third line
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals(3, parsed.size)
        assertEquals(5500L, parsed[0].timestampMs)
        assertEquals("First line", parsed[0].text)
        assertEquals(10200L, parsed[1].timestampMs)
        assertEquals("Second line", parsed[1].text)
        assertEquals(20000L, parsed[2].timestampMs)
        assertEquals("Third line", parsed[2].text)
    }

    @Test
    fun testFindActiveIndex() {
        val lrc = """
            [00:05.00]Line 1
            [00:10.00]Line 2
            [00:15.00]Line 3
        """.trimIndent()

        val lines = LrcParser.parse(lrc)

        // Before first line
        assertEquals(0, LrcParser.findActiveIndex(lines, 2000L))
        // Exact match
        assertEquals(1, LrcParser.findActiveIndex(lines, 10000L))
        // In between lines
        assertEquals(1, LrcParser.findActiveIndex(lines, 12000L))
        // After last line
        assertEquals(2, LrcParser.findActiveIndex(lines, 30000L))
    }

    @Test
    fun testCleanSongTitle() {
        val rawTitle = "Blinding Lights (Official Music Video)"
        val cleaned = LyricsRepository.cleanTitle(rawTitle)
        assertEquals("Blinding Lights", cleaned)

        val rawWithFeat = "Levitating [feat. DaBaby]"
        val cleanedFeat = LyricsRepository.cleanTitle(rawWithFeat)
        assertEquals("Levitating", cleanedFeat)
    }
}

