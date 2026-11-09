package com.flyre.lyrics.data.util

import com.flyre.lyrics.data.model.LyricLine
import java.util.regex.Pattern

object LrcParser {
    // Standard LRC tag: [mm:ss.xx] or [mm:ss.xxx]
    private val TIME_TAG_PATTERN: Pattern = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]")

    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()

        val lines = mutableListOf<LyricLine>()
        val rawLines = lrcContent.lines()

        for (raw in rawLines) {
            val matcher = TIME_TAG_PATTERN.matcher(raw)
            var lastMatchEnd = 0

            val timestamps = mutableListOf<Long>()
            while (matcher.find()) {
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val msString = matcher.group(3) ?: "0"
                val millis = when (msString.length) {
                    1 -> msString.toLong() * 100
                    2 -> msString.toLong() * 10
                    else -> msString.toLong()
                }

                val totalMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
                timestamps.add(totalMs)
                lastMatchEnd = matcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val text = raw.substring(lastMatchEnd).trim()
                for (ts in timestamps) {
                    lines.add(LyricLine(timestampMs = ts, text = text))
                }
            }
        }

        // Sort by timestamp
        lines.sortBy { it.timestampMs }
        return lines
    }

    /**
     * Find active lyric index for a given playback timestamp using binary search.
     */
    fun findActiveIndex(lines: List<LyricLine>, currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (currentPositionMs < lines.first().timestampMs) return 0

        var low = 0
        var high = lines.size - 1
        var result = 0

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timestampMs <= currentPositionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return result
    }
}

