package com.moviesshumtimes.tv.sync

import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GuestPlaybackReconciler(
    private val myPeerId: String,
    private val player: SyncedPlayer,
    private val scope: CoroutineScope,
    private val clock: ClockSync,
    private val sendControl: (ControlRequest) -> Unit,
    private val sendStatus: (PeerStatus) -> Unit,
) {
    private companion object {
        const val TICK_MS = 500L
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

    private fun isSuppressed() = nowMs() < suppressUntilMs
    private inline fun suppressed(block: () -> Unit) {
        suppressUntilMs = nowMs() + SUPPRESSION_WINDOW_MS
        block()
    }

    private val listener = object : SyncedPlayerListener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, isUserRequest: Boolean) {
            if (!isUserRequest || isSuppressed()) return
            if (latestState == null) return
            sendControl(ControlRequest(if (playWhenReady) ControlRequestKind.PLAY else ControlRequestKind.PAUSE))
            markOptimistic()
        }

        override fun onPlaybackStateChanged(state: SyncPlaybackState) {
            if (state == SyncPlaybackState.READY && !localReady) {
                localReady = true
                sendStatusNow(force = true)
                reconcile()
                return
            }
            sendStatusNow()
        }

        override fun onSeek(positionMs: Long) {
            if (isSuppressed()) return
            if (latestState == null) return
            sendControl(ControlRequest(ControlRequestKind.SEEK, positionMs))
            markOptimistic()
        }
    }

    fun start() {
        player.addListener(listener)
        localReady = player.playbackState == SyncPlaybackState.READY
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
        sendStatus(PeerStatus(ready = false, buffering = false, positionMs = 0))
    }

    fun onState(state: PlaybackState) {
        if (state.seq <= lastSeq) return
        lastSeq = state.seq
        latestState = state

        if (optimisticUntilSeq != null && (state.actorPeerId == myPeerId || state.actionHint != null)) {
            optimisticUntilSeq = null
        }
        if (state.waitingOn.contains(myPeerId) && localReady && player.playbackState != SyncPlaybackState.BUFFERING) {
            sendStatusNow(force = true)
        }
        reconcile()
    }

    private fun markOptimistic() {
        optimisticUntilSeq = lastSeq
        optimisticDeadlineMs = nowMs() + OPTIMISTIC_WINDOW_MS
    }

    private fun optimisticWindowActive() = optimisticUntilSeq != null && nowMs() < optimisticDeadlineMs

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

    private fun cooldownElapsed() = nowMs() - lastHardSeekMs >= HARD_SEEK_COOLDOWN_MS

    private fun hardSeek(targetMs: Long) {
        lastHardSeekMs = nowMs()
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
            buffering = player.playbackState == SyncPlaybackState.BUFFERING,
            positionMs = player.currentPosition,
            rttMs = clock.minRttMs,
        )
        val last = lastSentStatus
        if (!force && last != null && last.ready == status.ready && last.buffering == status.buffering) return
        lastSentStatus = status
        sendStatus(status)
    }
}
