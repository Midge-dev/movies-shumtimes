package com.moviesshumtimes.tv.sync

enum class SyncPlaybackState { IDLE, BUFFERING, READY, ENDED }

interface SyncedPlayerListener {
    fun onPlayWhenReadyChanged(playWhenReady: Boolean, isUserRequest: Boolean) {}
    fun onPlaybackStateChanged(state: SyncPlaybackState) {}
    fun onSeek(positionMs: Long) {}
}

interface SyncedPlayer {
    val isPlaying: Boolean
    val currentPosition: Long
    val duration: Long
    val playbackState: SyncPlaybackState

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)

    fun addListener(listener: SyncedPlayerListener)
    fun removeListener(listener: SyncedPlayerListener)
}
