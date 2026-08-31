package com.moviesshumtimes.tv.sync

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.serialization.Serializable

// ROOM_CLOSED is distinct from ROOM_NOT_FOUND: both mean "this room is
// gone," but ROOM_CLOSED comes from an explicit "closed" frame the relay
// sends every occupied seat the instant a host ends their session (see
// relay/server.js's /rooms/:roomId/close) — a clean signal, not the
// transient-looking dropped-socket-then-reconnect-fails path ROOM_NOT_FOUND
// covers. Screens that care about "the room I was in just ended" (Lobby)
// should treat both the same; RelayClient never auto-reconnects past
// ROOM_CLOSED since there's nothing to reconnect to.
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ROOM_FULL, ROOM_NOT_FOUND, ROOM_CLOSED }

// One row of the relay's GET /rooms directory — a room currently live on
// the shared relay, with just enough to render a Home-screen card and join
// it. Sourced entirely from the relay's own in-memory state (see
// relay/server.js); no Plex API call and no persistence involved.
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

// The application-level payload carried inside every relay "event" message
// (see RelayClient's envelope types). One flat class for every kind, same
// pattern the app already used for the old SyncEvent — a sealed/polymorphic
// hierarchy would be "more correct" but this stays consistent with the rest
// of the codebase and every field is optional anyway, so there's nothing a
// sealed class would make meaningfully safer here.
//
// The relay itself never inspects any of this — it just rebroadcasts
// `event` payloads verbatim to every other connected peer (device seats and
// chat peers alike), so kinds not relevant to a given role are simply
// ignored by whoever receives them (e.g. only the host processes
// controlRequest/peerStatus/clockPing; guests ignore them).
@Serializable
data class RelayEvent(
    val kind: String,
    // Lobby roster / "who's here".
    val username: String? = null,
    val avatarUrl: String? = null,
    // fromPeerId: whose action/report this is. Used to target replies
    // (e.g. a clockPong only the pinging guest should consume) even though
    // the relay only broadcasts — never routes point-to-point.
    val fromPeerId: String? = null,
    // Host -> guests: authoritative PlaybackState broadcast.
    val seq: Int? = null,
    val phase: String? = null, // "loading" | "waitingForPeers" | "paused" | "playing"
    val anchorPositionMs: Long? = null,
    val anchorHostTimeMs: Long? = null,
    val rate: Float? = null,
    val waitingOn: List<String>? = null,
    val actorPeerId: String? = null,
    val actionHint: String? = null,
    // Guest -> host: local control intent.
    val requestKind: String? = null, // "play" | "pause" | "seek"
    val positionMs: Long? = null,
    // Guest -> host: local readiness/buffering report.
    val ready: Boolean? = null,
    val buffering: Boolean? = null,
    // Clock sync (guest <-> host, addressed via fromPeerId).
    val pingId: Long? = null,
    val remoteTimestampMs: Long? = null,
    // Chat.
    val text: String? = null,
)

data class ChatMessage(val username: String, val text: String, val receivedAtMs: Long)

@OptIn(ExperimentalTime::class)
fun RelayEvent.toChatMessage() = ChatMessage(
    username = username ?: "them",
    text = text ?: "",
    receivedAtMs = Clock.System.now().toEpochMilliseconds(),
)
