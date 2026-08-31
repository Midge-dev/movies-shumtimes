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

private const val TOLERANT_TIMEOUT_MS = 75_000L

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

    suspend fun testReachable(relayUrl: String): Boolean {
        val url = relayHttpUrl(relayUrl) ?: return false
        return runCatching { client.get(url.base).status.isSuccess() }.getOrDefault(false)
    }

    suspend fun testReachableTolerant(relayUrl: String): Boolean {
        val url = relayHttpUrl(relayUrl) ?: return false
        return runCatching {
            client.get(url.base) { timeout { requestTimeoutMillis = TOLERANT_TIMEOUT_MS } }.status.isSuccess()
        }.getOrDefault(false)
    }

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
