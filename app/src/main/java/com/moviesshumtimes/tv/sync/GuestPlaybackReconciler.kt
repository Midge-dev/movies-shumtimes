package com.moviesshumtimes.tv.sync

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// Guest-side reconciliation loop: converges the local player onto the
// host's authoritative PlaybackState. Ported down from Plezy's
// guest_playback_reconciler.dart — same deadband + hard-seek correction and
// scheduled-group-start-via-clock-sync approach. Dropped: the rate-based
// "nudge" tier for small drift (see HostPlaybackCoordinator's doc comment
// for why) — this only ever hard-seeks, gated by a deadband and a cooldown
// so it doesn't fight small, harmless jitter.
//
// Local user actions become ControlRequests sent to the host (this app has
// always let either side drive playback, so there's no host-only control
// mode to model) with a short optimistic window so the next heartbeat from
// the host doesn't undo them before its own reply lands.
class GuestPlaybackReconciler(
    private val myPeerId: String,
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val clock: ClockSync,
    private val sendControl: (ControlRequest) -> Unit,
    private val sendStatus: (PeerStatus) -> Unit,
) {
    private companion object {
        const val TICK_MS = 500L
        // 350ms was too tight for a relay-routed, cross-household
        // connection: without rate-based nudging (see
        // HostPlaybackCoordinator's doc comment for why that was dropped),
        // hard-seek is the *only* correction available, and normal WAN
        // clock-sync jitter routinely exceeds a few hundred ms even once
        // converged. At 350ms that meant a disruptive re-seek (and the
        // real rebuffer it causes) on practically every cooldown cycle —
        // a couple of seconds of drift between two people on a phone/voice
        // call together is imperceptible; constant stalling to chase it
        // isn't.
        const val DEADBAND_MS = 1_500L
        const val HARD_SEEK_COOLDOWN_MS = 4_000L
        const val PAUSED_SEEK_THRESHOLD_MS = 500L
        const val SETTLE_TIMEOUT_MS = 1_500L
        const val OPTIMISTIC_WINDOW_MS = 2_000L
        const val SUPPRESSION_WINDOW_MS = 400L
    }

    private var latestState: PlaybackState? = null
    private var lastSeq = -1
    private var localReady = false
    private var suppressUntilMs = 0L
    private var settling = false
    private var settleJob: Job? = null
    private var lastHardSeekMs = -HARD_SEEK_COOLDOWN_MS
    private var tickJob: Job? = null
    private var scheduledStartJob: Job? = null
    private var scheduledStartSeq: Int? = null
    private var optimisticUntilSeq: Int? = null
    private var optimisticDeadlineMs = 0L
    private var lastSentStatus: PeerStatus? = null
    private var disposed = false

    private fun isSuppressed() = System.currentTimeMillis() < suppressUntilMs
    private inline fun suppressed(block: () -> Unit) {
        suppressUntilMs = System.currentTimeMillis() + SUPPRESSION_WINDOW_MS
        block()
    }

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST || isSuppressed()) return
            if (latestState == null) return
            sendControl(ControlRequest(if (playWhenReady) ControlRequestKind.PLAY else ControlRequestKind.PAUSE))
            markOptimistic()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && !localReady) {
                localReady = true
                sendStatusNow(force = true)
                reconcile()
                return
            }
            sendStatusNow()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (isSuppressed() || reason != Player.DISCONTINUITY_REASON_SEEK) return
            if (latestState == null) return
            sendControl(ControlRequest(ControlRequestKind.SEEK, newPosition.positionMs))
            markOptimistic()
        }
    }

    fun start() {
        player.addListener(listener)
        localReady = player.playbackState == Player.STATE_READY
        clock.start()
        tickJob = scope.launch {
            while (true) {
                delay(TICK_MS)
                reconcile()
            }
        }
        sendStatusNow(force = true)
    }

    fun stop() {
        disposed = true
        player.removeListener(listener)
        clock.stop()
        tickJob?.cancel()
        settleJob?.cancel()
        scheduledStartJob?.cancel()
        // Tell the host we're no longer here so it re-gates the next
        // start instead of waiting on a stale "ready".
        sendStatus(PeerStatus(ready = false, buffering = false, positionMs = 0))
    }

    fun onState(state: PlaybackState) {
        if (state.seq <= lastSeq) return // Stale or reordered.
        lastSeq = state.seq
        latestState = state

        if (optimisticUntilSeq != null && (state.actorPeerId == myPeerId || state.actionHint != null)) {
            optimisticUntilSeq = null
        }
        // Self-heal: the host thinks it's waiting on us but we're healthy.
        if (state.waitingOn.contains(myPeerId) && localReady && player.playbackState != Player.STATE_BUFFERING) {
            sendStatusNow(force = true)
        }
        reconcile()
    }

    private fun markOptimistic() {
        optimisticUntilSeq = lastSeq
        optimisticDeadlineMs = System.currentTimeMillis() + OPTIMISTIC_WINDOW_MS
    }

    private fun optimisticWindowActive() = optimisticUntilSeq != null && System.currentTimeMillis() < optimisticDeadlineMs

    private fun reconcile() {
        if (disposed || settling) return
        val state = latestState ?: return
        if (!localReady || optimisticWindowActive()) return

        when (state.phase) {
            PlaybackPhase.LOADING, PlaybackPhase.WAITING_FOR_PEERS, PlaybackPhase.PAUSED -> {
                ensurePaused()
                alignWhileStopped(state)
            }
            PlaybackPhase.PLAYING -> reconcilePlaying(state)
        }
    }

    private fun reconcilePlaying(state: PlaybackState) {
        val hostNow = clock.hostNowMs()

        // Scheduled group start: hold at the anchor, then start on the dot.
        if (state.anchorHostTimeMs > hostNow) {
            if (scheduledStartSeq != state.seq) {
                scheduledStartJob?.cancel()
                scheduledStartSeq = state.seq
                val delayMs = state.anchorHostTimeMs - hostNow
                scheduledStartJob = scope.launch {
                    delay(delayMs)
                    scheduledStartSeq = null
                    if (latestState?.seq != state.seq) return@launch
                    suppressed { player.play() }
                }
            }
            ensurePaused()
            alignWhileStopped(state)
            return
        }
        if (scheduledStartSeq != null && scheduledStartSeq != state.seq) {
            scheduledStartJob?.cancel()
            scheduledStartSeq = null
        }

        val duration = player.duration
        var targetMs = state.targetPositionMs(hostNow)
        if (duration > 0 && targetMs > duration) targetMs = duration

        if (!player.isPlaying) suppressed { player.play() }

        val drift = player.currentPosition - targetMs
        if (abs(drift) <= DEADBAND_MS) return
        if (cooldownElapsed()) hardSeek(targetMs)
    }

    private fun cooldownElapsed() = System.currentTimeMillis() - lastHardSeekMs >= HARD_SEEK_COOLDOWN_MS

    private fun hardSeek(targetMs: Long) {
        lastHardSeekMs = System.currentTimeMillis()
        settling = true
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(SETTLE_TIMEOUT_MS)
            settling = false
        }
        suppressed { player.seekTo(targetMs.coerceAtLeast(0)) }
    }

    private fun alignWhileStopped(state: PlaybackState) {
        val offBy = abs(player.currentPosition - state.anchorPositionMs)
        if (offBy > PAUSED_SEEK_THRESHOLD_MS && cooldownElapsed()) hardSeek(state.anchorPositionMs)
    }

    private fun ensurePaused() {
        if (player.isPlaying) suppressed { player.pause() }
    }

    private fun sendStatusNow(force: Boolean = false) {
        val status = PeerStatus(
            ready = localReady,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition,
            rttMs = clock.minRttMs,
        )
        val last = lastSentStatus
        if (!force && last != null && last.ready == status.ready && last.buffering == status.buffering) return
        lastSentStatus = status
        sendStatus(status)
    }
}
