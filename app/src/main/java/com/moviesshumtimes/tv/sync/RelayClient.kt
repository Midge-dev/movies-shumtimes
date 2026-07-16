package com.moviesshumtimes.tv.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@Serializable
data class SyncEvent(val type: String, val positionMs: Long, val sentAtEpochMs: Long)

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

private const val INITIAL_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 30_000L

// Thin client for the watch-together relay (relay/server.js): sends local
// play/pause/seek events and surfaces whatever the relay forwards from the
// other side. Connection failures are swallowed on purpose — sync is a
// bonus on top of local playback, never something that should be able to
// break watching a movie solo or when the relay is unreachable. Reconnects
// automatically with exponential backoff until disconnect() is called.
class RelayClient(private val relayUrl: String, private val scope: CoroutineScope) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var backoffMs = INITIAL_BACKOFF_MS
    private var manuallyDisconnected = false

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SyncEvent> = _events

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    fun connect() {
        manuallyDisconnected = false
        backoffMs = INITIAL_BACKOFF_MS
        attemptConnect()
    }

    private fun attemptConnect() {
        _connectionState.value = ConnectionState.CONNECTING
        val request = Request.Builder().url(relayUrl).build()
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    backoffMs = INITIAL_BACKOFF_MS
                    _connectionState.value = ConnectionState.CONNECTED
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { json.decodeFromString(SyncEvent.serializer(), text) }
                        .onSuccess { _events.tryEmit(it) }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    scheduleReconnect()
                }
            },
        )
    }

    private fun scheduleReconnect() {
        if (manuallyDisconnected) return
        _connectionState.value = ConnectionState.RECONNECTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            if (!manuallyDisconnected) attemptConnect()
        }
    }

    fun send(type: String, positionMs: Long) {
        val event = SyncEvent(type, positionMs, System.currentTimeMillis())
        runCatching { webSocket?.send(json.encodeToString(SyncEvent.serializer(), event)) }
    }

    fun disconnect() {
        manuallyDisconnected = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "done")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
