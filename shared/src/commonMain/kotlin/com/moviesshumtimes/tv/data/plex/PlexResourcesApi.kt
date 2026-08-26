package com.moviesshumtimes.tv.data.plex

import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// PlexConnection/PlexResource/PlexServer live in the shared module now
// (data/plex/PlexModels.kt) — pure data, no OkHttp.

// Discovers Plex servers reachable from this account (including servers
// shared by other accounts, like the cousin's) and figures out which of
// each server's candidate connection URIs is actually reachable from here.
// A shared server run from another state will typically only answer on its
// relay connection unless the owner has port-forwarding set up, so we can't
// just take connections[0] — every candidate has to be tried for real.
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

    // preferredMachineIdentifier is a server the user explicitly chose in
    // Settings (see AppSettings.selectedServerId). When set, only that
    // server is tried — no silent fallback to a different one, since a
    // silent fallback is exactly what caused a real bug: the app used to
    // always prefer any owned==false (shared) resource, which broke for
    // an account (e.g. the cousin's own) that has access to more than one
    // shared server and owns its real one, landing on the wrong library.
    // When null (nothing chosen yet, e.g. first launch), fall back to that
    // same owned-server-last heuristic as a reasonable default.
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
