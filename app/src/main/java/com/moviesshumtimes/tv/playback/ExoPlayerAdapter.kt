package com.moviesshumtimes.tv.playback

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.moviesshumtimes.tv.sync.SyncPlaybackState
import com.moviesshumtimes.tv.sync.SyncedPlayer
import com.moviesshumtimes.tv.sync.SyncedPlayerListener

// The only implementation of SyncedPlayer today — wraps a real ExoPlayer so
// HostPlaybackCoordinator/GuestPlaybackReconciler (shared module) never see
// an ExoPlayer or Media3 type directly. A future tvOS build supplies an
// AVPlayer-backed implementation of the same interface instead.
class ExoPlayerAdapter(private val player: ExoPlayer) : SyncedPlayer {
    override val isPlaying: Boolean get() = player.isPlaying
    override val currentPosition: Long get() = player.currentPosition
    override val duration: Long get() = player.duration
    override val playbackState: SyncPlaybackState get() = player.playbackState.toSyncPlaybackState()

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    // Player doesn't key listeners by anything the caller controls, so this
    // maps each SyncedPlayerListener to the Player.Listener adapting it,
    // to make removeListener possible.
    private val exoListeners = mutableMapOf<SyncedPlayerListener, Player.Listener>()

    override fun addListener(listener: SyncedPlayerListener) {
        val exoListener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                listener.onPlayWhenReadyChanged(
                    playWhenReady = playWhenReady,
                    isUserRequest = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                listener.onPlaybackStateChanged(playbackState.toSyncPlaybackState())
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) listener.onSeek(newPosition.positionMs)
            }
        }
        exoListeners[listener] = exoListener
        player.addListener(exoListener)
    }

    override fun removeListener(listener: SyncedPlayerListener) {
        exoListeners.remove(listener)?.let { player.removeListener(it) }
    }
}

private fun Int.toSyncPlaybackState(): SyncPlaybackState = when (this) {
    Player.STATE_BUFFERING -> SyncPlaybackState.BUFFERING
    Player.STATE_READY -> SyncPlaybackState.READY
    Player.STATE_ENDED -> SyncPlaybackState.ENDED
    else -> SyncPlaybackState.IDLE
}
