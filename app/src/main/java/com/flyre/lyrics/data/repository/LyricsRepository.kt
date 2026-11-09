package com.flyre.lyrics.data.repository

import com.flyre.lyrics.data.local.LyricDao
import com.flyre.lyrics.data.local.LyricEntity
import com.flyre.lyrics.data.model.LyricLine
import com.flyre.lyrics.data.model.LyricsData
import com.flyre.lyrics.data.model.SongInfo
import com.flyre.lyrics.data.remote.LrclibApi
import com.flyre.lyrics.data.remote.LrclibResponse
import com.flyre.lyrics.data.util.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class LyricsRepository(
    private val lyricDao: LyricDao,
    private val api: LrclibApi
) {
    suspend fun getLyrics(song: SongInfo): Result<LyricsData> = withContext(Dispatchers.IO) {
        val normalizedTitle = cleanTitle(song.title)
        val normalizedArtist = cleanArtist(song.artist)
        val cacheId = "${normalizedTitle.lowercase()}_${normalizedArtist.lowercase()}"

        // 1. Check local cache
        try {
            val cached = lyricDao.getLyricsById(cacheId)
                ?: lyricDao.findLyrics(normalizedTitle, normalizedArtist)
            if (cached != null) {
                val lines = if (!cached.syncedLyrics.isNullOrBlank()) {
                    LrcParser.parse(cached.syncedLyrics)
                } else if (!cached.plainLyrics.isNullOrBlank()) {
                    cached.plainLyrics.lines().map { LyricLine(0L, it) }
                } else emptyList()

                return@withContext Result.success(
                    LyricsData(
                        title = cached.trackTitle,
                        artist = cached.artistName,
                        plainLyrics = cached.plainLyrics,
                        syncedLyrics = cached.syncedLyrics,
                        lines = lines
                    )
                )
            }
        } catch (e: Exception) {
            // Log & continue to network
        }

        // 2. Fetch from LRCLIB API (exact match first)
        try {
            val durationSec = if (song.durationMs > 0) song.durationMs / 1000 else null
            val response = api.getLyrics(
                trackName = normalizedTitle,
                artistName = normalizedArtist,
                durationSeconds = durationSec
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val data = processResponse(cacheId, normalizedTitle, normalizedArtist, body)
                return@withContext Result.success(data)
            }
        } catch (e: Exception) {
            // Fallback to search query
        }

        // 3. Fallback to Search API (fuzzier match)
        try {
            val query = "$normalizedTitle $normalizedArtist".trim()
            val searchResponse = api.searchLyrics(query)

            if (searchResponse.isSuccessful && !searchResponse.body().isNullOrEmpty()) {
                val bestMatch = searchResponse.body()!!.firstOrNull {
                    !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank()
                } ?: searchResponse.body()!!.first()

                val data = processResponse(cacheId, normalizedTitle, normalizedArtist, bestMatch)
                return@withContext Result.success(data)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        Result.failure(NoSuchElementException("No lyrics found for $normalizedTitle - $normalizedArtist"))
    }

    private suspend fun processResponse(
        cacheId: String,
        title: String,
        artist: String,
        response: LrclibResponse
    ): LyricsData {
        val synced = response.syncedLyrics
        val plain = response.plainLyrics

        // Cache in DB
        val entity = LyricEntity(
            id = cacheId,
            trackTitle = title,
            artistName = artist,
            albumName = response.albumName ?: "",
            plainLyrics = plain,
            syncedLyrics = synced
        )
        try {
            lyricDao.insertLyric(entity)
        } catch (ignored: Exception) {}

        val lines = if (!synced.isNullOrBlank()) {
            LrcParser.parse(synced)
        } else if (!plain.isNullOrBlank()) {
            plain.lines().map { LyricLine(0L, it) }
        } else emptyList()

        return LyricsData(
            title = title,
            artist = artist,
            plainLyrics = plain,
            syncedLyrics = synced,
            lines = lines
        )
    }

    companion object {
        private val EXTRA_TEXT_PATTERN = Pattern.compile(
            "\\s*[\\[(](?:official|audio|video|lyric video|music video|hd|4k|remastered|feat\\.?|ft\\.?).*?[\\])]",
            Pattern.CASE_INSENSITIVE
        )

        fun cleanTitle(title: String): String {
            var cleaned = EXTRA_TEXT_PATTERN.matcher(title).replaceAll("")
            // Remove trailing dashes or spaces
            cleaned = cleaned.trim().trimEnd('-', '–', '—', '|').trim()
            return cleaned.ifBlank { title }
        }

        fun cleanArtist(artist: String): String {
            // If YouTube Music outputs "Artist - Topic", clean it
            return artist.replace(" - Topic", "", ignoreCase = true).trim()
        }
    }
}

