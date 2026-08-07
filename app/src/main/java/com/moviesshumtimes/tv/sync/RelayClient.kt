package com.moviesshumtimes.tv.sync

import com.moviesshumtimes.tv.data.settings.RelayIdentity
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

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
class RelayClient(
    val relayUrl: String,
    private var identity: RelayIdentity,
    private val scope: CoroutineScope,
    private val onIdentityUpdated: (RelayIdentity) -> Unit = {},
) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var webSocket: WebSocket? = null
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
        val request = Request.Builder().url(relayUrl).build()
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    backoffMs = INITIAL_BACKOFF_MS
                    val hello = buildJsonObject {
                        put("type", "hello")
                        put("role", "device")
                        put("peerId", identity.peerId)
                        identity.reconnectToken?.let { put("reconnectToken", it) }
                    }
                    webSocket.send(hello.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleFrame(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _seatIndex.value = null
                    scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _seatIndex.value = null
                    scheduleReconnect()
                }
            },
        )
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
        val envelope = buildJsonObject {
            put("type", "event")
            put("payload", json.encodeToJsonElement(event))
        }
        runCatching { webSocket?.send(envelope.toString()) }
    }

    fun disconnect() {
        manuallyDisconnected = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "done")
        webSocket = null
        _seatIndex.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
