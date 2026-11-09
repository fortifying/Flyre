package com.flyre.lyrics.data.remote

import com.google.gson.annotations.SerializedName

data class LrclibResponse(
    @SerializedName("id")
    val id: Long? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("trackName")
    val trackName: String? = null,
    @SerializedName("artistName")
    val artistName: String? = null,
    @SerializedName("albumName")
    val albumName: String? = null,
    @SerializedName("duration")
    val duration: Double? = null,
    @SerializedName("instrumental")
    val instrumental: Boolean = false,
    @SerializedName("plainLyrics")
    val plainLyrics: String? = null,
    @SerializedName("syncedLyrics")
    val syncedLyrics: String? = null
)

