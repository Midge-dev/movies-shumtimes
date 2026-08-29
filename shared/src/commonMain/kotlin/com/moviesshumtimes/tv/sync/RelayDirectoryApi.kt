package com.moviesshumtimes.tv.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

// Fetches the relay's live room directory (GET /rooms) — no WebSocket
// needed just to browse. Powers the Home screen's Watch Together row.
// Failures collapse to an empty list rather than propagating: an
// unreachable/outdated relay should just mean "no rooms visible," same
// tolerance RelayClient already applies to the live connection.
class RelayDirectoryApi {
    private val client = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun listRooms(relayUrl: String): List<RelayRoomSummary> {
        val url = relayHttpUrl(relayUrl) ?: return emptyList()
        val fullUrl = "${url.base}/rooms" + (url.query?.let { "?$it" } ?: "")
        return runCatching { client.get(fullUrl).body<List<RelayRoomSummary>>() }.getOrDefault(emptyList())
    }
}
