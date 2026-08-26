package com.moviesshumtimes.tv.sync

// Everything HostPlaybackCoordinator/GuestPlaybackReconciler actually touch
// on a player — nothing more. Deliberately shaped around ExoPlayer's own
// state machine (play/pause/seekTo, isPlaying/currentPosition/duration,
// a 4-state playbackState) since that's the only implementation today
// (see ExoPlayerAdapter in the app module) and AVPlayer's model maps onto
// the same shape closely enough; the sync policy code itself never sees
// ExoPlayer or AVPlayer types.
enum class SyncPlaybackState { IDLE, BUFFERING, READY, ENDED }

// Mirrors Player.Listener's shape (optional overrides, one method per
// signal), but with the "why did this change" question already answered
// by the adapter instead of exposed as a raw platform reason code:
// - onPlayWhenReadyChanged's isUserRequest collapses ExoPlayer's reason
//   enum down to the one distinction the sync policy actually branches
//   on (a real user/remote tap vs. every other cause — audio focus loss,
//   end of media, a seek's own play/pause side effect, etc).
// - onSeek only ever fires for genuine seek-type discontinuities; the
//   adapter filters out every other discontinuity reason before calling
//   it, since nothing here ever consumed those anyway.
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
