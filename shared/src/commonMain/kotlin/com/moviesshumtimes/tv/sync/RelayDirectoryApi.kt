package com.moviesshumtimes.tv.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val AMBIENT_TIMEOUT_MS = 5_000L

// Design spec 09d: "cold start up to ~60s is treated as normal, not
// exceptional" for an active "Add a relay" test — matches the Lobby's own
// 75s failure threshold for the same underlying reason (a free-tier relay
// that's been idle needs time to wake, not a fast fail).
private const val TOLERANT_TIMEOUT_MS = 75_000L

// Fetches the relay's live room directory (GET /rooms) — no WebSocket
// needed just to browse. Powers the Home screen's Watch Together row.
// Failures collapse to an empty list rather than propagating: an
// unreachable/outdated relay should just mean "no rooms visible," same
// tolerance RelayClient already applies to the live connection.
class RelayDirectoryApi {
    private val client = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = AMBIENT_TIMEOUT_MS }
    }

    suspend fun listRooms(relayUrl: String): List<RelayRoomSummary> {
        val url = relayHttpUrl(relayUrl) ?: return emptyList()
        val fullUrl = "${url.base}/rooms" + (url.query?.let { "?$it" } ?: "")
        return runCatching { client.get(fullUrl).body<List<RelayRoomSummary>>() }.getOrDefault(emptyList())
    }

    // Ambient reachability (Settings' room-count refresh, Home's per-relay
    // label) — a real true/false rather than listRooms' error-swallowing-
    // to-empty-list, short timeout since this isn't tolerating a cold start.
    suspend fun testReachable(relayUrl: String): Boolean {
        val url = relayHttpUrl(relayUrl) ?: return false
        return runCatching { client.get(url.base).status.isSuccess() }.getOrDefault(false)
    }

    // Used by Settings' "Add a relay" -> "Test & save" — tolerates a
    // free-tier cold start rather than failing fast.
    suspend fun testReachableTolerant(relayUrl: String): Boolean {
        val url = relayHttpUrl(relayUrl) ?: return false
        return runCatching {
            client.get(url.base) { timeout { requestTimeoutMillis = TOLERANT_TIMEOUT_MS } }.status.isSuccess()
        }.getOrDefault(false)
    }

    // Ends a room this device hosts, callable from Home well after the live
    // socket to it is gone — peerId + reconnectToken are the same proof of
    // host identity a reconnect would present, checked server-side against
    // seat 0. False for "not authorized" and "unreachable" alike; either way
    // there's nothing more the caller can do about it.
    suspend fun closeRoom(relayUrl: String, roomId: String, peerId: String, reconnectToken: String): Boolean {
        val url = relayHttpUrl(relayUrl) ?: return false
        val fullUrl = "${url.base}/rooms/$roomId/close" + (url.query?.let { "?$it" } ?: "")
        return runCatching {
            client.post(fullUrl) {
                contentType(ContentType.Application.Json)
                setBody(CloseRoomRequest(peerId, reconnectToken))
            }.status.isSuccess()
        }.getOrDefault(false)
    }
}

@Serializable
private data class CloseRoomRequest(val peerId: String, val reconnectToken: String)
