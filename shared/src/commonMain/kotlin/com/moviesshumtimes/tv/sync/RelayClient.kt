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
) {
    private val client = HttpClient { install(WebSockets) }
    private val json = Json { ignoreUnknownKeys = true }
    private var session: DefaultClientWebSocketSession? = null
    private var sessionJob: Job? = null
    private var reconnectJob: Job? = null
    private var backoffMs = INITIAL_BACKOFF_MS
    private var manuallyDisconnected = false
    private var lastRejectionWasFull = false

    private val _events = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<RelayEvent> = _events

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // 0 = host, 1..N-1 = guest. Null until a "welcome" is received.
    private val _seatIndex = MutableStateFlow<Int?>(null)
    val seatIndex: StateFlow<Int?> = _seatIndex

    val myPeerId: String get() = identity.peerId

    fun connect() {
        manuallyDisconnected = false
        backoffMs = INITIAL_BACKOFF_MS
        attemptConnect()
    }

    private fun attemptConnect() {
        lastRejectionWasFull = false
        _connectionState.value = ConnectionState.CONNECTING
        // One coroutine owns the whole connection lifecycle: opening the
        // session, sending "hello", and reading frames until the session
        // ends (gracefully or not — both cases fall through to the same
        // finally block, mirroring the old onClosed/onFailure callbacks
        // both unconditionally calling scheduleReconnect()). Cancelling
        // this job (see disconnect()) is itself what tears the session
        // down, via structured concurrency, instead of a separate close
        // call racing against it.
        sessionJob = scope.launch {
            try {
                val newSession = client.webSocketSession(relayUrl)
                session = newSession
                backoffMs = INITIAL_BACKOFF_MS

                val hello = buildJsonObject {
                    put("type", "hello")
                    put("role", "device")
                    put("peerId", identity.peerId)
                    identity.reconnectToken?.let { put("reconnectToken", it) }
                }
                newSession.send(Frame.Text(hello.toString()))

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
                _seatIndex.value = root["seatIndex"]?.jsonPrimitive?.intOrNull
                _connectionState.value = ConnectionState.CONNECTED
            }
            "full" -> {
                lastRejectionWasFull = true
                _connectionState.value = ConnectionState.ROOM_FULL
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
        _connectionState.value = if (lastRejectionWasFull) ConnectionState.ROOM_FULL else ConnectionState.RECONNECTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            if (!manuallyDisconnected) attemptConnect()
        }
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
        _seatIndex.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
