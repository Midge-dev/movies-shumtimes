package com.moviesshumtimes.tv.sync

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Bridges an ExoPlayer's local play/pause/seek actions to the relay, and
// applies whatever the other side does back onto this player.
//
// Self-echo guard: applying a remote event suppresses outgoing sends for a
// short cooldown window rather than just for the duration of the call that
// applies it. A plain "currently applying" boolean isn't enough — a remote
// seek makes ExoPlayer pause-for-buffering and then auto-resume playing
// again roughly a second or two later, well after the synchronous call
// returns, and that later onIsPlayingChanged(true) would otherwise slip
// through and get re-broadcast, causing both sides to keep nudging each
// other's playback position back and forth.
private const val ECHO_SUPPRESSION_WINDOW_MS = 2_500L

class SyncViewModel(
    private val player: ExoPlayer,
    private val relay: RelayClient,
    private val scope: CoroutineScope,
) {
    private var suppressUntilMs = 0L
    private var collectJob: Job? = null

    val connectionState get() = relay.connectionState

    private val listener = object : Player.Listener {
        // Deliberately onPlayWhenReadyChanged, not onIsPlayingChanged:
        // isPlaying also flips false whenever ExoPlayer pauses itself to
        // buffer (common on transcoded Plex streams), which broadcast as a
        // false "pause" and yanked the other side's playback around on
        // every network hiccup — buffering never touches playWhenReady, so
        // this only fires for changes ExoPlayer attributes to an actual
        // request (ours or the built-in controller's), not to buffering,
        // audio focus loss, or becoming-noisy.
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) return
            if (isSuppressed()) return
            relay.send(if (playWhenReady) "play" else "pause", player.currentPosition)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (isSuppressed()) return
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                relay.send("seek", newPosition.positionMs)
            }
        }
    }

    fun start() {
        player.addListener(listener)
        relay.connect()
        collectJob = scope.launch {
            relay.events.collect { event -> applyRemoteEvent(event) }
        }
    }

    private fun isSuppressed(): Boolean = System.currentTimeMillis() < suppressUntilMs

    private fun applyRemoteEvent(event: SyncEvent) {
        suppressUntilMs = System.currentTimeMillis() + ECHO_SUPPRESSION_WINDOW_MS
        when (event.type) {
            "play" -> {
                // The other side was already playing for however long the
                // event took to arrive — seek forward to compensate instead
                // of starting exactly where they were when they sent it.
                val elapsed = (System.currentTimeMillis() - event.sentAtEpochMs).coerceAtLeast(0)
                player.seekTo(event.positionMs + elapsed)
                player.play()
            }
            "pause" -> {
                player.seekTo(event.positionMs)
                player.pause()
            }
            "seek" -> player.seekTo(event.positionMs)
        }
    }

    fun stop() {
        player.removeListener(listener)
        relay.disconnect()
        collectJob?.cancel()
    }
}
