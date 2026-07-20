package com.moviesshumtimes.tv.data.plex

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class PlexConnection(
    val uri: String,
    val local: Boolean = false,
    val relay: Boolean = false,
)

@Serializable
data class PlexResource(
    val name: String,
    val provides: String = "",
    val owned: Boolean = false,
    // Plex's stable per-server machine ID — distinct from the app's own
    // client identifier passed into PlexResourcesApi's constructor. Used to
    // remember a user's explicit server choice across resource re-fetches,
    // since connection URIs/tokens can change but this doesn't.
    @SerialName("clientIdentifier") val machineIdentifier: String = "",
    val accessToken: String? = null,
    val connections: List<PlexConnection> = emptyList(),
)

data class PlexServer(val name: String, val baseUrl: String, val accessToken: String)

// Discovers Plex servers reachable from this account (including servers
// shared by other accounts, like the cousin's) and figures out which of
// each server's candidate connection URIs is actually reachable from here.
// A shared server run from another state will typically only answer on its
// relay connection unless the owner has port-forwarding set up, so we can't
// just take connections[0] — every candidate has to be tried for real.
class PlexResourcesApi(private val clientIdentifier: String) {
    private val client = OkHttpClient()
    private val connectClient = client.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchResources(accountToken: String): List<PlexResource> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1&includeIPv6=1")
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("X-Plex-Product", "Movies Shumtimes")
            .addHeader("X-Plex-Client-Identifier", clientIdentifier)
            .addHeader("X-Plex-Token", accountToken)
            .build()
        client.newCall(request).execute().use { response ->
            val bodyString = response.body.string()
            check(response.isSuccessful) { "Failed to fetch Plex resources: ${response.code} $bodyString" }
            json.decodeFromString(ListSerializer(PlexResource.serializer()), bodyString)
        }
    }

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
                .map { connection -> async(Dispatchers.IO) { connection to testConnection(connection, token) } }
                .awaitAll()
                .firstOrNull { it.second }
                ?.first
        }

    private fun testConnection(connection: PlexConnection, token: String): Boolean = runCatching {
        val request = Request.Builder()
            .url("${connection.uri}/identity")
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("X-Plex-Token", token)
            .build()
        connectClient.newCall(request).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}
