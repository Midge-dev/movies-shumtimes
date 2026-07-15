package com.moviesshumtimes.tv.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@Serializable
data class SyncEvent(val type: String, val positionMs: Long, val sentAtEpochMs: Long)

// Thin client for the watch-together relay (relay/server.js): sends local
// play/pause/seek events and surfaces whatever the relay forwards from the
// other side. Connection failures are swallowed on purpose — sync is a
// bonus on top of local playback, never something that should be able to
// break watching a movie solo or when the relay is unreachable.
class RelayClient(private val relayUrl: String) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var webSocket: WebSocket? = null

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SyncEvent> = _events

    fun connect() {
        val request = Request.Builder().url(relayUrl).build()
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { json.decodeFromString(SyncEvent.serializer(), text) }
                        .onSuccess { _events.tryEmit(it) }
                }
            },
        )
    }

    fun send(type: String, positionMs: Long) {
        val event = SyncEvent(type, positionMs, System.currentTimeMillis())
        runCatching { webSocket?.send(json.encodeToString(SyncEvent.serializer(), event)) }
    }

    fun disconnect() {
        webSocket?.close(1000, "done")
        webSocket = null
    }
}
