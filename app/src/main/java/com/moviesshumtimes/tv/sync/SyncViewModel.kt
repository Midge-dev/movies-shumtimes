package com.moviesshumtimes.tv.sync

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Bridges an ExoPlayer to the relay. Picks host or guest role from the
// relay's assigned seat index (0 = host, every other seat = guest) and
// wires up the matching coordinator — see HostPlaybackCoordinator /
// GuestPlaybackReconciler for the actual sync policy, this class is just
// the glue: RelayEvent <-> domain-type translation, routing incoming
// events to the right place, and relaying the guest-side clock-sync
// ping/pong (the host side of that exchange is a one-line reply here too,
// not worth its own class).
//
// Does not own the RelayClient's connection lifecycle — that's hoisted
// above Lobby/Player now (see MainActivity's AppRoot) so the connection,
// and chat, survive the Lobby -> Player transition instead of reconnecting
// on every screen change.
class SyncViewModel(
    private val player: ExoPlayer,
    private val relay: RelayClient,
    private val scope: CoroutineScope,
) {
    val connectionState get() = relay.connectionState

    private val _chatMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 16)
    val chatMessages: SharedFlow<ChatMessage> = _chatMessages

    private val _phase = MutableStateFlow<PlaybackPhase?>(null)
    val phase: StateFlow<PlaybackPhase?> = _phase

    private val _waitingOn = MutableStateFlow<List<String>>(emptyList())
    val waitingOn: StateFlow<List<String>> = _waitingOn

    private var host: HostPlaybackCoordinator? = null
    private var guest: GuestPlaybackReconciler? = null
    private var clock: ClockSync? = null
    private var roleJob: Job? = null
    private var eventsJob: Job? = null

    fun start() {
        eventsJob = scope.launch { relay.events.collect(::handleEvent) }
        roleJob = scope.launch {
            relay.seatIndex.collect { seatIndex ->
                if (seatIndex == null || host != null || guest != null) return@collect
                if (seatIndex == 0) startAsHost() else startAsGuest()
            }
        }
    }

    fun stop() {
        eventsJob?.cancel()
        roleJob?.cancel()
        host?.stop()
        guest?.stop()
        clock?.stop()
    }

    private fun startAsHost() {
        host = HostPlaybackCoordinator(
            myPeerId = relay.myPeerId,
            player = player,
            scope = scope,
            sendState = { state -> relay.send(state.toRelayEvent()); _phase.value = state.phase; _waitingOn.value = state.waitingOn },
            onWaitingOnChanged = { _waitingOn.value = it },
        ).also { it.start() }
    }

    private fun startAsGuest() {
        val clockSync = ClockSync(
            scope = scope,
            sendPing = { pingId -> relay.send(RelayEvent(kind = "clockPing", fromPeerId = relay.myPeerId, pingId = pingId)) },
        )
        clock = clockSync
        guest = GuestPlaybackReconciler(
            myPeerId = relay.myPeerId,
            player = player,
            scope = scope,
            clock = clockSync,
            sendControl = { request -> relay.send(request.toRelayEvent(relay.myPeerId)) },
            sendStatus = { status -> relay.send(status.toRelayEvent(relay.myPeerId)) },
        ).also { it.start() }
    }

    private fun handleEvent(event: RelayEvent) {
        // Any message with a fromPeerId is proof-of-life for that peer —
        // the host tracks the roster off of this rather than a separate
        // relay-level "peerJoined" message.
        event.fromPeerId?.let { if (it != relay.myPeerId) host?.onPeerJoined(it) }

        when (event.kind) {
            "playbackState" -> {
                val state = event.toPlaybackState() ?: return
                _phase.value = state.phase
                _waitingOn.value = state.waitingOn
                guest?.onState(state)
            }
            "controlRequest" -> {
                val fromPeerId = event.fromPeerId ?: return
                val request = event.toControlRequest() ?: return
                host?.onControlRequest(fromPeerId, request)
            }
            "peerStatus" -> {
                val fromPeerId = event.fromPeerId ?: return
                host?.onPeerStatus(
                    fromPeerId,
                    PeerStatus(event.ready ?: false, event.buffering ?: false, event.positionMs ?: 0),
                )
            }
            "clockPing" -> {
                // Broadcast-only relay means every peer sees every ping;
                // only the host replies, addressed back via fromPeerId so
                // the right guest (and only that guest) consumes the pong.
                if (host == null) return
                val fromPeerId = event.fromPeerId ?: return
                val pingId = event.pingId ?: return
                relay.send(
                    RelayEvent(
                        kind = "clockPong",
                        fromPeerId = fromPeerId,
                        pingId = pingId,
                        remoteTimestampMs = System.currentTimeMillis(),
                    ),
                )
            }
            "clockPong" -> {
                if (event.fromPeerId != relay.myPeerId) return
                val pingId = event.pingId ?: return
                val remoteTs = event.remoteTimestampMs ?: return
                clock?.onPong(pingId, remoteTs)
            }
            "chat" -> _chatMessages.tryEmit(event.toChatMessage())
        }
    }
}

private fun PlaybackPhase.wire() = when (this) {
    PlaybackPhase.LOADING -> "loading"
    PlaybackPhase.WAITING_FOR_PEERS -> "waitingForPeers"
    PlaybackPhase.PAUSED -> "paused"
    PlaybackPhase.PLAYING -> "playing"
}

private fun String.toPlaybackPhase() = when (this) {
    "loading" -> PlaybackPhase.LOADING
    "waitingForPeers" -> PlaybackPhase.WAITING_FOR_PEERS
    "paused" -> PlaybackPhase.PAUSED
    "playing" -> PlaybackPhase.PLAYING
    else -> null
}

private fun PlaybackActionHint.wire() = when (this) {
    PlaybackActionHint.PLAY -> "play"
    PlaybackActionHint.PAUSE -> "pause"
    PlaybackActionHint.SEEK -> "seek"
}

private fun String.toActionHint() = when (this) {
    "play" -> PlaybackActionHint.PLAY
    "pause" -> PlaybackActionHint.PAUSE
    "seek" -> PlaybackActionHint.SEEK
    else -> null
}

private fun PlaybackState.toRelayEvent() = RelayEvent(
    kind = "playbackState",
    seq = seq,
    phase = phase.wire(),
    anchorPositionMs = anchorPositionMs,
    anchorHostTimeMs = anchorHostTimeMs,
    waitingOn = waitingOn,
    actorPeerId = actorPeerId,
    actionHint = actionHint?.wire(),
)

private fun RelayEvent.toPlaybackState(): PlaybackState? {
    val seq = seq ?: return null
    val phase = phase?.toPlaybackPhase() ?: return null
    val anchorPositionMs = anchorPositionMs ?: return null
    val anchorHostTimeMs = anchorHostTimeMs ?: return null
    return PlaybackState(
        seq = seq,
        phase = phase,
        anchorPositionMs = anchorPositionMs,
        anchorHostTimeMs = anchorHostTimeMs,
        waitingOn = waitingOn ?: emptyList(),
        actorPeerId = actorPeerId,
        actionHint = actionHint?.toActionHint(),
    )
}

private fun ControlRequest.toRelayEvent(fromPeerId: String) = RelayEvent(
    kind = "controlRequest",
    fromPeerId = fromPeerId,
    requestKind = when (kind) {
        ControlRequestKind.PLAY -> "play"
        ControlRequestKind.PAUSE -> "pause"
        ControlRequestKind.SEEK -> "seek"
    },
    positionMs = positionMs,
)

private fun RelayEvent.toControlRequest(): ControlRequest? {
    val kind = when (requestKind) {
        "play" -> ControlRequestKind.PLAY
        "pause" -> ControlRequestKind.PAUSE
        "seek" -> ControlRequestKind.SEEK
        else -> return null
    }
    return ControlRequest(kind, positionMs)
}

private fun PeerStatus.toRelayEvent(fromPeerId: String) = RelayEvent(
    kind = "peerStatus",
    fromPeerId = fromPeerId,
    ready = ready,
    buffering = buffering,
    positionMs = positionMs,
)
