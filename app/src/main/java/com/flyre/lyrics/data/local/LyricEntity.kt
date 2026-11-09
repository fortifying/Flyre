package com.flyre.lyrics.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics_cache")
data class LyricEntity(
    @PrimaryKey
    val id: String, // track_artist normalized
    val trackTitle: String,
    val artistName: String,
    val albumName: String = "",
    val durationSeconds: Long = 0L,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val provider: String = "LRCLIB",
    val cachedAt: Long = System.currentTimeMillis()
)

