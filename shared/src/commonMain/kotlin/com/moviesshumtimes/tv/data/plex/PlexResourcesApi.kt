package com.moviesshumtimes.tv.data.plex

import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class PlexResourcesApi(private val clientIdentifier: String) {
    private val client = plexHttpClient()
    private val connectClient = plexHttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 4_000
            requestTimeoutMillis = 4_000
        }
    }

    suspend fun fetchResources(accountToken: String): List<PlexResource> =
        client.get("https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1&includeIPv6=1") {
            header("Accept", "application/json")
            header("X-Plex-Product", "Movies Shumtimes")
            header("X-Plex-Client-Identifier", clientIdentifier)
            header("X-Plex-Token", accountToken)
        }.body()

    suspend fun listServers(accountToken: String): List<PlexResource> =
        fetchResources(accountToken).filter { "server" in it.provides && it.accessToken != null }

    suspend fun findReachableServer(accountToken: String, preferredMachineIdentifier: String? = null): PlexServer? {
        val servers = listServers(accountToken)
        if (preferredMachineIdentifier != null) {
            val chosen = servers.firstOrNull { it.machineIdentifier == preferredMachineIdentifier } ?: return null
            return connectTo(chosen)
        }
        for (resource in servers.sortedBy { it.owned }) {
            connectTo(resource)?.let { return it }
        }
        return null
    }

    private suspend fun connectTo(resource: PlexResource): PlexServer? {
        val token = resource.accessToken ?: return null
        val (direct, relay) = resource.connections.partition { !it.relay }
        val connection = firstReachable(direct, token) ?: firstReachable(relay, token)
        return connection?.let { PlexServer(resource.name, it.uri.trimEnd('/'), token) }
    }

    private suspend fun firstReachable(candidates: List<PlexConnection>, token: String): PlexConnection? =
        coroutineScope {
            if (candidates.isEmpty()) return@coroutineScope null
            candidates
                .map { connection -> async { connection to testConnection(connection, token) } }
                .awaitAll()
                .firstOrNull { it.second }
                ?.first
        }

    private suspend fun testConnection(connection: PlexConnection, token: String): Boolean = runCatching {
        connectClient.get("${connection.uri}/identity") {
            header("Accept", "application/json")
            header("X-Plex-Token", token)
        }
        true
    }.getOrDefault(false)
}
