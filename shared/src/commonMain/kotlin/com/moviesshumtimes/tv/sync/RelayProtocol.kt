package com.moviesshumtimes.tv.sync

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.serialization.Serializable

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ROOM_FULL, ROOM_NOT_FOUND, ROOM_CLOSED }

@Serializable
data class RelayRoomSummary(
    val roomId: String,
    val title: String,
    val thumb: String? = null,
    val ratingKey: String? = null,
    val hostName: String,
    val occupants: Int,
    val maxSeats: Int,
)

@Serializable
data class RelayEvent(
    val kind: String,
    val username: String? = null,
    val avatarUrl: String? = null,
    val fromPeerId: String? = null,
    val seq: Int? = null,
    val phase: String? = null,
    val anchorPositionMs: Long? = null,
    val anchorHostTimeMs: Long? = null,
    val rate: Float? = null,
    val waitingOn: List<String>? = null,
    val actorPeerId: String? = null,
    val actionHint: String? = null,
    val requestKind: String? = null,
    val positionMs: Long? = null,
    val ready: Boolean? = null,
    val buffering: Boolean? = null,
    val pingId: Long? = null,
    val remoteTimestampMs: Long? = null,
    val text: String? = null,
)

data class ChatMessage(val username: String, val text: String, val receivedAtMs: Long)

@OptIn(ExperimentalTime::class)
fun RelayEvent.toChatMessage() = ChatMessage(
    username = username ?: "them",
    text = text ?: "",
    receivedAtMs = Clock.System.now().toEpochMilliseconds(),
)
