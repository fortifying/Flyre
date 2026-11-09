package com.flyre.lyrics.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LyricDao {
    @Query("SELECT * FROM lyrics_cache WHERE id = :id LIMIT 1")
    suspend fun getLyricsById(id: String): LyricEntity?

    @Query("SELECT * FROM lyrics_cache WHERE LOWER(trackTitle) = LOWER(:title) AND LOWER(artistName) = LOWER(:artist) LIMIT 1")
    suspend fun findLyrics(title: String, artist: String): LyricEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyric(entity: LyricEntity)

    @Query("DELETE FROM lyrics_cache WHERE cachedAt < :thresholdTime")
    suspend fun clearOldLyrics(thresholdTime: Long)
}

