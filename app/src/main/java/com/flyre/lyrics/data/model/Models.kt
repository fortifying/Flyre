package com.flyre.lyrics.data.model

data class SongInfo(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    val displayTitle: String get() = title.ifBlank { "Unknown Title" }
    val displayArtist: String get() = artist.ifBlank { "Unknown Artist" }

    fun id(): String = "${title.trim().lowercase()}_${artist.trim().lowercase()}"
}

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class LyricsData(
    val title: String,
    val artist: String,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val isSynced: Boolean = !syncedLyrics.isNullOrBlank(),
    val lines: List<LyricLine> = emptyList()
)

