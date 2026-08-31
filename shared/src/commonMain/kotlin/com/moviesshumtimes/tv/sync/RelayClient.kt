package com.moviesshumtimes.tv.sync

import com.moviesshumtimes.tv.data.settings.RelayIdentity
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val INITIAL_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 30_000L

// What a connection is trying to do — create a fresh room (host) or join an
// existing one by id (guest, from the Home screen's room directory). The
// relay's old single-tenant "hello" envelope is gone; every device
// connection now states its room intent up front.
sealed interface RoomIntent {
    // maxSeats: design spec section 14 "Maximum seats" — a client-side cap on
    // rooms this device hosts, sent with the room when it opens; the relay
    // clamps it to its own MAX_DEVICE_SEATS ceiling rather than trusting it
    // outright. Null lets the relay fall back to its own default (joining a
    // room, or an older client that never set the setting, needs no opinion).
    data class Create(val title: String, val thumb: String?, val ratingKey: String?, val hostName: String, val maxSeats: Int? = null) :
        RoomIntent
    data class Join(val roomId: String) : RoomIntent
}

// Thin transport client for the watch-together relay (relay/server.js):
// speaks the hello/welcome/event/full envelope protocol, carries an
// application-level RelayEvent inside every "event" message, and persists
// the identity (peerId + reconnectToken) the relay uses to hand a
// reconnecting device its own seat back — see RelayIdentityStore for why
// that round-trips through the caller rather than living here.
//
// Connection failures are swallowed on purpose — sync is a bonus on top of
// local playback, never something that should be able to break watching a
// movie solo or when the relay is unreachable. Reconnects automatically
// with exponential backoff until disconnect() is called.
//
// The connect/send/disconnect surface stays synchronous (fire-and-forget),
// matching the old OkHttp WebSocket's callback-driven shape, even though
// Ktor's session API (webSocketSession/send/close) is suspend-based —
// each dispatches onto `scope` internally instead of exposing suspend
// functions, so call sites don't change.
class RelayClient(
    val relayUrl: String,
    private var identity: RelayIdentity,
    private val scope: CoroutineScope,
    private val onIdentityUpdated: (RelayIdentity) -> Unit = {},
    // Fired whenever a "welcome" lands us in seat 0 — true host, whether
    // this connection just created the room or reclaimed it after a drop.
    // Lets the caller persist "the room I'm hosting" independent of this
    // client's own lifetime, so Home can still offer to end it long after
    // this RelayClient (and its live socket) has been torn down. Carries
    // this room's own host-seat token alongside its id — needed to close
    // this specific room later, since a device hosting multiple rooms holds
    // a different token per room.
    private val onHostedRoomIdUpdated: (roomId: String, reconnectToken: String) -> Unit = { _, _ -> },
) {
    private val client = HttpClient { install(WebSockets) }
    private val json = Json { ignoreUnknownKeys = true }
    private var session: DefaultClientWebSocketSession? = null
    private var sessionJob: Job? = null
    private var reconnectJob: Job? = null
    private var backoffMs = INITIAL_BACKOFF_MS
    private var manuallyDisconnected = false
    // ROOM_FULL and ROOM_NOT_FOUND are both terminal rejections, not
    // transient failures — a reconnect won't fix "the room is full" or "the
    // room ended," so scheduleReconnect surfaces whichever one just
    // happened instead of always falling back to RECONNECTING.
    private var lastRejection: ConnectionState? = null
    // What the *next* (re)connect attempt should do — set by connect(),
    // reused verbatim across reconnects so a dropped connection rejoins the
    // same room instead of re-running whatever intent was passed in first.
    private var intent: RoomIntent? = null

    private val _events = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<RelayEvent> = _events

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // 0 = host, 1..N-1 = guest. Null until a "welcome" is received.
    private val _seatIndex = MutableStateFlow<Int?>(null)
    val seatIndex: StateFlow<Int?> = _seatIndex

    // Populated from "welcome" — null until connected. Needed by the Lobby's
    // chat QR (to scope the phone's chat join to this room) and by Home to
    // mark this room as "yours" (Rejoin / "You're in") in the directory.
    private val _roomId = MutableStateFlow<String?>(null)
    val roomId: StateFlow<String?> = _roomId

    val myPeerId: String get() = identity.peerId

    fun connect(intent: RoomIntent) {
        this.intent = intent
        manuallyDisconnected = false
        backoffMs = INITIAL_BACKOFF_MS
        attemptConnect()
    }

    private fun attemptConnect() {
        val currentIntent = intent ?: return
        lastRejection = null
        _connectionState.value = ConnectionState.CONNECTING
        // One coroutine owns the whole connection lifecycle: opening the
        // session, sending the create/join request, and reading frames
        // until the session ends (gracefully or not — both cases fall
        // through to the same finally block, mirroring the old
        // onClosed/onFailure callbacks both unconditionally calling
        // scheduleReconnect()). Cancelling this job (see disconnect()) is
        // itself what tears the session down, via structured concurrency,
        // instead of a separate close call racing against it.
        sessionJob = scope.launch {
            try {
                val newSession = client.webSocketSession(relayUrl)
                session = newSession
                backoffMs = INITIAL_BACKOFF_MS

                val request = buildJsonObject {
                    when (currentIntent) {
                        is RoomIntent.Create -> {
                            put("type", "createRoom")
                            put("title", currentIntent.title)
                            currentIntent.thumb?.let { put("thumb", it) }
                            currentIntent.ratingKey?.let { put("ratingKey", it) }
                            put("hostName", currentIntent.hostName)
                            currentIntent.maxSeats?.let { put("maxSeats", it) }
                        }
                        is RoomIntent.Join -> {
                            put("type", "joinRoom")
                            put("roomId", currentIntent.roomId)
                        }
                    }
                    put("peerId", identity.peerId)
                    identity.reconnectToken?.let { put("reconnectToken", it) }
                }
                newSession.send(Frame.Text(request.toString()))

                for (frame in newSession.incoming) {
                    if (frame is Frame.Text) handleFrame(frame.readText())
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
            } finally {
                session = null
                _seatIndex.value = null
                scheduleReconnect()
            }
        }
    }

    private fun handleFrame(text: String) {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        when (root["type"]?.jsonPrimitive?.contentOrNull) {
            "welcome" -> {
                val newToken = root["reconnectToken"]?.jsonPrimitive?.contentOrNull
                if (newToken != null && newToken != identity.reconnectToken) {
                    identity = identity.copy(reconnectToken = newToken)
                    onIdentityUpdated(identity)
                }
                val seat = root["seatIndex"]?.jsonPrimitive?.intOrNull
                _seatIndex.value = seat
                val newRoomId = root["roomId"]?.jsonPrimitive?.contentOrNull
                newRoomId?.let { _roomId.value = it }
                if (seat == 0 && newRoomId != null) {
                    identity.reconnectToken?.let { onHostedRoomIdUpdated(newRoomId, it) }
                }
                _connectionState.value = ConnectionState.CONNECTED
            }
            "full" -> {
                lastRejection = ConnectionState.ROOM_FULL
                _connectionState.value = ConnectionState.ROOM_FULL
            }
            "notFound" -> {
                lastRejection = ConnectionState.ROOM_NOT_FOUND
                _connectionState.value = ConnectionState.ROOM_NOT_FOUND
            }
            // The host ended this room on purpose (see relay/server.js's
            // /rooms/:roomId/close, which now notifies every occupied seat,
            // not just the host's own). The relay closes this socket right
            // after sending this frame — manuallyDisconnected=true here
            // stops the resulting disconnect from scheduling a pointless
            // reconnect against a room that's gone for good, which would
            // otherwise silently overwrite this state.
            "closed" -> {
                manuallyDisconnected = true
                _connectionState.value = ConnectionState.ROOM_CLOSED
            }
            "event" -> {
                val payload = root["payload"] ?: return
                runCatching { json.decodeFromJsonElement<RelayEvent>(payload) }
                    .onSuccess { _events.tryEmit(it) }
            }
        }
    }

    private fun scheduleReconnect() {
        if (manuallyDisconnected) return
        // A room that's full or gone won't fix itself on a timer the way a
        // dropped network connection might — still retry (the host could
        // free a seat, or this could be a stale rejection from before a
        // reconnect), but report the specific rejection meanwhile rather
        // than a generic RECONNECTING that reads as "still trying to
        // connect" when the real answer is already known.
        _connectionState.value = lastRejection ?: ConnectionState.RECONNECTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            if (!manuallyDisconnected) attemptConnect()
        }
    }

    // Design spec 09d's Lobby failure state ("Can't reach {relay}") ->
    // Retry — cancels whatever backoff wait is pending and attempts right
    // now instead of making the user wait out the current delay too.
    fun retryNow() {
        if (manuallyDisconnected) return
        reconnectJob?.cancel()
        sessionJob?.cancel()
        backoffMs = INITIAL_BACKOFF_MS
        attemptConnect()
    }

    fun send(event: RelayEvent) {
        val currentSession = session ?: return
        val envelope = buildJsonObject {
            put("type", "event")
            put("payload", json.encodeToJsonElement(event))
        }
        scope.launch { runCatching { currentSession.send(Frame.Text(envelope.toString())) } }
    }

    fun disconnect() {
        manuallyDisconnected = true
        reconnectJob?.cancel()
        sessionJob?.cancel()
        session = null
        intent = null
        _seatIndex.value = null
        _roomId.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
