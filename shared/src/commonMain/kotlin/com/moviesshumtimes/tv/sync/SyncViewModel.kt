package com.moviesshumtimes.tv.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SyncViewModel(
    private val player: SyncedPlayer,
    private val relay: RelayClient?,
    private val scope: CoroutineScope,
) {
    val connectionState: StateFlow<ConnectionState> =
        relay?.connectionState ?: MutableStateFlow(ConnectionState.DISCONNECTED)

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
        val relay = relay ?: return
        eventsJob = scope.launch { relay.events.collect { handleEvent(relay, it) } }
        roleJob = scope.launch {
            relay.seatIndex.collect { seatIndex ->
                if (seatIndex == null || host != null || guest != null) return@collect
                if (seatIndex == 0) startAsHost(relay) else startAsGuest(relay)
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

    private fun startAsHost(relay: RelayClient) {
        host = HostPlaybackCoordinator(
            myPeerId = relay.myPeerId,
            player = player,
            scope = scope,
            sendState = { state -> relay.send(state.toRelayEvent()); _phase.value = state.phase; _waitingOn.value = state.waitingOn },
            onWaitingOnChanged = { _waitingOn.value = it },
        ).also { it.start() }
    }

    private fun startAsGuest(relay: RelayClient) {
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

    private fun handleEvent(relay: RelayClient, event: RelayEvent) {
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
                if (host == null) return
                val fromPeerId = event.fromPeerId ?: return
                val pingId = event.pingId ?: return
                relay.send(
                    RelayEvent(
                        kind = "clockPong",
                        fromPeerId = fromPeerId,
                        pingId = pingId,
                        remoteTimestampMs = nowMs(),
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
