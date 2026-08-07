package com.moviesshumtimes.tv.sync

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// Host-side policy engine: owns the authoritative PlaybackState for however
// many guests have joined (N-capable — the relay's seat model supports up
// to MAX_DEVICE_SEATS, so this tracks a set of peers rather than assuming
// exactly one). Ported down from Plezy's host_playback_coordinator.dart:
// same phase machine, same peer-status-driven stall gating (the actual fix
// for the old "buffering broadcast as pause" bug), same scheduled
// group-start and safety-timeout-resumes-without-stragglers behavior.
// Dropped from the original: per-media "epoch" switching (not needed — see
// PlaybackState), RTT-adaptive start delay and rate-based drift nudging
// (simplified to a fixed start delay and hard-seek-only correction, partly
// to sidestep ExoPlayer's audio-passthrough/playback-speed interaction,
// partly because this app has no variable-speed feature to protect either).
class HostPlaybackCoordinator(
    private val myPeerId: String,
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val sendState: (PlaybackState) -> Unit,
    private val onWaitingOnChanged: (List<String>) -> Unit = {},
) {
    private companion object {
        const val STALL_GRACE_MS = 500L
        const val RECOVERY_HYSTERESIS_MS = 400L
        const val SAFETY_TIMEOUT_MS = 15_000L
        const val HEARTBEAT_PLAYING_MS = 2_000L
        const val HEARTBEAT_IDLE_MS = 5_000L
        const val START_DELAY_MS = 750L
        const val IMPLICIT_JUMP_THRESHOLD_MS = 1_500L
        // Long enough to cover ExoPlayer's listener-dispatch latency for a
        // command we just issued ourselves, short enough not to eat a real
        // second press the way the old flat 2.5s suppression window did.
        const val SUPPRESSION_WINDOW_MS = 400L
    }

    private var seq = 0
    private var phase = PlaybackPhase.LOADING
    private var intendedPlaying = false
    private var localReady = false
    private var localBuffering = false
    private var localStalled = false
    private var firstStartCompleted = false
    private var suppressUntilMs = 0L
    private var disposed = false

    private val knownPeers = mutableSetOf<String>()
    private val peerStatuses = mutableMapOf<String, PeerStatus>()
    private val stalledPeers = mutableSetOf<String>()
    private val excused = mutableSetOf<String>()
    private val peerStallGraceJobs = mutableMapOf<String, Job>()

    private var pendingStartJob: Job? = null
    private var safetyJob: Job? = null
    private var heartbeatJob: Job? = null
    private var allReadyCheckJob: Job? = null

    private var lastBroadcast: PlaybackState? = null
    private var pendingActor: String? = null

    private fun isSuppressed() = System.currentTimeMillis() < suppressUntilMs
    private inline fun suppressed(block: () -> Unit) {
        suppressUntilMs = System.currentTimeMillis() + SUPPRESSION_WINDOW_MS
        block()
    }

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST || isSuppressed()) return
            if (playWhenReady) requestPlay(myPeerId) else requestPause(myPeerId)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && !localReady) {
                localReady = true
                onLocalLoaded()
                return
            }
            if (!localReady) return
            val buffering = playbackState == Player.STATE_BUFFERING
            if (buffering == localBuffering) return
            localBuffering = buffering
            onSelfBuffering(buffering)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (isSuppressed() || reason != Player.DISCONTINUITY_REASON_SEEK) return
            afterHostSeek(newPosition.positionMs, myPeerId)
        }
    }

    fun start() {
        player.addListener(listener)
        localReady = player.playbackState == Player.STATE_READY
        if (localReady) onLocalLoaded() else setPhase(PlaybackPhase.LOADING)
        restartHeartbeat()
    }

    fun stop() {
        disposed = true
        player.removeListener(listener)
        pendingStartJob?.cancel()
        safetyJob?.cancel()
        heartbeatJob?.cancel()
        allReadyCheckJob?.cancel()
        peerStallGraceJobs.values.forEach { it.cancel() }
        peerStallGraceJobs.clear()
    }

    fun onPeerJoined(peerId: String) {
        if (peerId == myPeerId) return
        knownPeers.add(peerId)
        broadcast()
    }

    fun onPeerStatus(peerId: String, status: PeerStatus) {
        if (peerId == myPeerId) return
        knownPeers.add(peerId)
        val previous = peerStatuses[peerId]
        peerStatuses[peerId] = status
        if (status.ready && !status.buffering) excused.remove(peerId)

        if (status.ready && status.buffering) {
            if (phase == PlaybackPhase.PLAYING && peerId !in stalledPeers) {
                peerStallGraceJobs.getOrPut(peerId) {
                    scope.launch {
                        delay(STALL_GRACE_MS)
                        peerStallGraceJobs.remove(peerId)
                        val latest = peerStatuses[peerId] ?: return@launch
                        if (!latest.buffering || phase != PlaybackPhase.PLAYING) return@launch
                        stalledPeers.add(peerId)
                        enterWaiting()
                    }
                }
            } else if (phase == PlaybackPhase.WAITING_FOR_PEERS && peerId !in stalledPeers) {
                stalledPeers.add(peerId)
                scheduleAllReadyCheck(0)
            }
        } else {
            peerStallGraceJobs.remove(peerId)?.cancel()
            val wasStalled = stalledPeers.remove(peerId)
            val becameReady = status.ready && (previous == null || !previous.ready)
            if (wasStalled) scheduleAllReadyCheck(RECOVERY_HYSTERESIS_MS) else if (becameReady) scheduleAllReadyCheck(0)
        }
    }

    fun onPeerLeft(peerId: String) {
        knownPeers.remove(peerId)
        excused.remove(peerId)
        stalledPeers.remove(peerId)
        peerStatuses.remove(peerId)
        peerStallGraceJobs.remove(peerId)?.cancel()
        scheduleAllReadyCheck(0)
    }

    fun onControlRequest(peerId: String, request: ControlRequest) {
        when (request.kind) {
            ControlRequestKind.PLAY -> requestPlay(peerId)
            ControlRequestKind.PAUSE -> requestPause(peerId)
            ControlRequestKind.SEEK -> request.positionMs?.let { applyRemoteSeek(it, peerId) }
        }
    }

    // ---------------------------------------------------------------------
    // Local player signals
    // ---------------------------------------------------------------------

    private fun onLocalLoaded() {
        if (phase == PlaybackPhase.LOADING) {
            if (player.isPlaying) suppressed { player.pause() }
            setPhase(PlaybackPhase.WAITING_FOR_PEERS)
            broadcast()
            armSafetyIfGated()
        }
        scheduleAllReadyCheck(0)
    }

    private fun onSelfBuffering(buffering: Boolean) {
        if (buffering) {
            if (phase != PlaybackPhase.PLAYING || localStalled) return
            scope.launch {
                delay(STALL_GRACE_MS)
                if (!localBuffering || phase != PlaybackPhase.PLAYING) return@launch
                localStalled = true
                enterWaiting()
            }
        } else if (localStalled) {
            localStalled = false
            scheduleAllReadyCheck(RECOVERY_HYSTERESIS_MS)
        }
    }

    // ---------------------------------------------------------------------
    // Play / pause / seek policy
    // ---------------------------------------------------------------------

    private fun requestPlay(actor: String) {
        if (phase == PlaybackPhase.PLAYING) return
        intendedPlaying = true
        pendingActor = actor
        if (!localReady) {
            if (player.isPlaying) suppressed { player.pause() }
            return
        }
        val gating = gatingPeers()
        if (gating.isEmpty()) {
            scheduleStart(actor)
        } else {
            if (player.isPlaying) suppressed { player.pause() }
            if (phase != PlaybackPhase.WAITING_FOR_PEERS) {
                setPhase(PlaybackPhase.WAITING_FOR_PEERS)
                broadcast(actor = actor)
                armSafetyIfGated()
            }
        }
    }

    private fun requestPause(actor: String) {
        intendedPlaying = false
        pendingActor = null
        cancelPendingStart()
        cancelSafety()
        if (player.isPlaying) suppressed { player.pause() }
        if (phase == PlaybackPhase.LOADING) return
        setPhase(PlaybackPhase.PAUSED)
        broadcast(hint = PlaybackActionHint.PAUSE, actor = actor)
    }

    private fun applyRemoteSeek(targetMs: Long, actor: String) {
        val duration = player.duration
        if (duration <= 0 || targetMs < 0 || targetMs > duration) return
        suppressed { player.seekTo(targetMs) }
        afterHostSeek(targetMs, actor)
    }

    private fun afterHostSeek(targetMs: Long, actor: String) {
        broadcast(hint = PlaybackActionHint.SEEK, actor = actor, anchorOverrideMs = targetMs)
    }

    // ---------------------------------------------------------------------
    // Readiness / group-wait
    // ---------------------------------------------------------------------

    private fun gatingPeers(): Set<String> {
        val gating = mutableSetOf<String>()
        for (peerId in knownPeers) {
            if (peerId in excused) continue
            val status = peerStatuses[peerId]
            if (status == null || !status.ready) {
                if (!firstStartCompleted) gating.add(peerId)
                continue
            }
            if (peerId in stalledPeers) gating.add(peerId)
        }
        if (!localReady || localStalled) gating.add(myPeerId)
        return gating
    }

    private fun enterWaiting() {
        if (phase == PlaybackPhase.WAITING_FOR_PEERS) return
        if (player.isPlaying && !localStalled) suppressed { player.pause() }
        intendedPlaying = true
        setPhase(PlaybackPhase.WAITING_FOR_PEERS)
        broadcast()
        armSafetyIfGated()
    }

    private fun scheduleAllReadyCheck(delayMs: Long) {
        allReadyCheckJob?.cancel()
        allReadyCheckJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            checkAllReady()
        }
    }

    private fun checkAllReady() {
        if (disposed || phase != PlaybackPhase.WAITING_FOR_PEERS) return
        val gating = gatingPeers()
        if (gating.isNotEmpty()) {
            broadcastIfWaitingOnChanged(gating)
            return
        }
        resolveAllReady()
    }

    private fun resolveAllReady() {
        cancelSafety()
        if (intendedPlaying) scheduleStart(pendingActor ?: myPeerId) else {
            setPhase(PlaybackPhase.PAUSED)
            broadcast()
        }
        pendingActor = null
    }

    private fun scheduleStart(actor: String) {
        cancelPendingStart()
        val otherPeers = knownPeers - excused
        val delayMs = if (otherPeers.isEmpty()) 0L else START_DELAY_MS
        val startAt = System.currentTimeMillis() + delayMs
        val startPositionMs = player.currentPosition
        firstStartCompleted = true
        setPhase(PlaybackPhase.PLAYING)
        broadcast(
            hint = PlaybackActionHint.PLAY,
            actor = actor,
            anchorOverrideMs = startPositionMs,
            anchorHostTimeOverrideMs = startAt,
        )

        if (delayMs <= 0 && player.isPlaying) return // Solo resume — nothing to do.

        pendingStartJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            if (disposed || phase != PlaybackPhase.PLAYING) return@launch
            suppressed {
                if (abs(player.currentPosition - startPositionMs) > 250) player.seekTo(startPositionMs)
                player.play()
            }
        }
    }

    private fun armSafetyIfGated() {
        cancelSafety()
        if (gatingPeers().minus(myPeerId).isEmpty()) return
        safetyJob = scope.launch {
            delay(SAFETY_TIMEOUT_MS)
            if (phase != PlaybackPhase.WAITING_FOR_PEERS) return@launch
            val gating = gatingPeers().minus(myPeerId)
            if (gating.isEmpty()) return@launch
            excused.addAll(gating)
            stalledPeers.removeAll(gating)
            scheduleAllReadyCheck(0)
        }
    }

    private fun cancelPendingStart() {
        pendingStartJob?.cancel()
        pendingStartJob = null
    }

    private fun cancelSafety() {
        safetyJob?.cancel()
        safetyJob = null
    }

    // ---------------------------------------------------------------------
    // Heartbeat & broadcasting
    // ---------------------------------------------------------------------

    private fun restartHeartbeat() {
        heartbeatJob?.cancel()
        val interval = if (phase == PlaybackPhase.PLAYING) HEARTBEAT_PLAYING_MS else HEARTBEAT_IDLE_MS
        heartbeatJob = scope.launch {
            while (true) {
                delay(interval)
                onHeartbeat()
            }
        }
    }

    private fun onHeartbeat() {
        if (disposed) return
        val last = lastBroadcast
        var hint: PlaybackActionHint? = null
        if (last != null && pendingStartJob == null) {
            val expected = last.targetPositionMs(System.currentTimeMillis())
            if (abs(player.currentPosition - expected) > IMPLICIT_JUMP_THRESHOLD_MS) hint = PlaybackActionHint.SEEK
        }
        broadcast(hint = hint, actor = if (hint != null) myPeerId else null)
    }

    private fun broadcastIfWaitingOnChanged(gating: Set<String>) {
        val last = lastBroadcast ?: return
        if (gating.sorted() == last.waitingOn) return
        broadcast()
    }

    private fun setPhase(newPhase: PlaybackPhase) {
        if (phase == newPhase) return
        phase = newPhase
        restartHeartbeat()
    }

    private fun broadcast(
        hint: PlaybackActionHint? = null,
        actor: String? = null,
        anchorOverrideMs: Long? = null,
        anchorHostTimeOverrideMs: Long? = null,
    ) {
        if (disposed) return
        val anchorPositionMs = anchorOverrideMs ?: player.currentPosition
        val anchorHostTimeMs = anchorHostTimeOverrideMs ?: System.currentTimeMillis()
        val waitingOn = if (phase == PlaybackPhase.WAITING_FOR_PEERS) gatingPeers().sorted() else emptyList()

        val state = PlaybackState(
            seq = ++seq,
            phase = phase,
            anchorPositionMs = anchorPositionMs,
            anchorHostTimeMs = anchorHostTimeMs,
            waitingOn = waitingOn,
            actorPeerId = actor,
            actionHint = hint,
        )
        val previousWaiting = lastBroadcast?.waitingOn ?: emptyList()
        lastBroadcast = state
        if (previousWaiting != waitingOn) onWaitingOnChanged(waitingOn)
        sendState(state)
    }
}
