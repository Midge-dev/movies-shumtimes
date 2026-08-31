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

sealed interface RoomIntent {
    data class Create(val title: String, val thumb: String?, val ratingKey: String?, val hostName: String, val maxSeats: Int? = null) :
        RoomIntent
    data class Join(val roomId: String) : RoomIntent
}

class RelayClient(
    val relayUrl: String,
    private var identity: RelayIdentity,
    private val scope: CoroutineScope,
    private val onIdentityUpdated: (RelayIdentity) -> Unit = {},
    private val onHostedRoomIdUpdated: (roomId: String, reconnectToken: String) -> Unit = { _, _ -> },
) {
    private val client = HttpClient { install(WebSockets) }
    private val json = Json { ignoreUnknownKeys = true }
    private var session: DefaultClientWebSocketSession? = null
    private var sessionJob: Job? = null
    private var connectionGeneration = 0
    private var reconnectJob: Job? = null
    private var backoffMs = INITIAL_BACKOFF_MS
    private var manuallyDisconnected = false
    private var lastRejection: ConnectionState? = null
    private var intent: RoomIntent? = null

    private val _events = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<RelayEvent> = _events

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _seatIndex = MutableStateFlow<Int?>(null)
    val seatIndex: StateFlow<Int?> = _seatIndex

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
        val myGeneration = ++connectionGeneration
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
                if (myGeneration == connectionGeneration) {
                    session = null
                    _seatIndex.value = null
                    scheduleReconnect()
                }
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
        _connectionState.value = lastRejection ?: ConnectionState.RECONNECTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            if (!manuallyDisconnected) attemptConnect()
        }
    }

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
