package com.flyre.lyrics.data.repository

import com.flyre.lyrics.data.model.SongInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object MediaStateRepository {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _currentSong = MutableStateFlow<SongInfo?>(null)
    val currentSong: StateFlow<SongInfo?> = _currentSong.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private var progressJob: Job? = null
    private var lastUpdateRealtime: Long = 0L
    private var basePositionMs: Long = 0L
    private var isPlaying: Boolean = false

    fun updateSong(song: SongInfo?) {
        _currentSong.value = song

        if (song == null) {
            stopPositionTracker()
            _currentPositionMs.value = 0L
            return
        }

        basePositionMs = song.positionMs
        lastUpdateRealtime = System.currentTimeMillis()
        isPlaying = song.isPlaying
        _currentPositionMs.value = basePositionMs

        if (isPlaying) {
            startPositionTracker()
        } else {
            stopPositionTracker()
        }
    }

    fun updatePlaybackState(playing: Boolean, positionMs: Long) {
        isPlaying = playing
        basePositionMs = positionMs
        lastUpdateRealtime = System.currentTimeMillis()
        _currentPositionMs.value = positionMs

        val current = _currentSong.value
        if (current != null && (current.isPlaying != playing || Math.abs(current.positionMs - positionMs) > 1000)) {
            _currentSong.value = current.copy(isPlaying = playing, positionMs = positionMs)
        }

        if (isPlaying) {
            startPositionTracker()
        } else {
            stopPositionTracker()
        }
    }

    private fun startPositionTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && isPlaying) {
                val elapsed = System.currentTimeMillis() - lastUpdateRealtime
                val estimated = basePositionMs + elapsed
                _currentPositionMs.value = estimated
                delay(200) // update 5 times a second for fluid lyric sync
            }
        }
    }

    private fun stopPositionTracker() {
        progressJob?.cancel()
        progressJob = null
    }
}

