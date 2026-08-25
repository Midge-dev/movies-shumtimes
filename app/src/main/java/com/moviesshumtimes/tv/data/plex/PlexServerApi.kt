package com.moviesshumtimes.tv.data.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// PlexSection/PlexTag/PlexLibraryItem/PlexSeason/PlexEpisode/PlexStream/
// PlexPart/PlexMedia/PlexMovieDetail/PlexOnDeckItem/PlexHub live in the
// shared module now (data/plex/PlexModels.kt) — pure data, no OkHttp, so
// they're portable to every platform's client. Only the actual networking
// (this class) stays Android-side pending the OkHttp->Ktor swap.

@Serializable
private data class SectionsMediaContainer(@SerialName("Directory") val directories: List<PlexSection> = emptyList())

@Serializable
private data class SectionsResponse(@SerialName("MediaContainer") val mediaContainer: SectionsMediaContainer)

@Serializable
private data class LibraryItemsMediaContainer(@SerialName("Metadata") val items: List<PlexLibraryItem> = emptyList())

@Serializable
private data class LibraryItemsResponse(@SerialName("MediaContainer") val mediaContainer: LibraryItemsMediaContainer)

@Serializable
private data class SeasonsMediaContainer(@SerialName("Metadata") val items: List<PlexSeason> = emptyList())

@Serializable
private data class SeasonsResponse(@SerialName("MediaContainer") val mediaContainer: SeasonsMediaContainer)

@Serializable
private data class EpisodesMediaContainer(@SerialName("Metadata") val items: List<PlexEpisode> = emptyList())

@Serializable
private data class EpisodesResponse(@SerialName("MediaContainer") val mediaContainer: EpisodesMediaContainer)

@Serializable
private data class MovieDetailMediaContainer(@SerialName("Metadata") val items: List<PlexMovieDetail> = emptyList())

@Serializable
private data class MovieDetailResponse(@SerialName("MediaContainer") val mediaContainer: MovieDetailMediaContainer)

@Serializable
private data class OnDeckMediaContainer(@SerialName("Metadata") val items: List<PlexOnDeckItem> = emptyList())

@Serializable
private data class OnDeckResponse(@SerialName("MediaContainer") val mediaContainer: OnDeckMediaContainer)

@Serializable
private data class HubsMediaContainer(@SerialName("Hub") val hubs: List<PlexHub> = emptyList())

@Serializable
private data class HubsResponse(@SerialName("MediaContainer") val mediaContainer: HubsMediaContainer)

// Talks directly to a Plex Media Server (as opposed to plex.tv account
// endpoints) using the server-specific access token from PlexResourcesApi —
// the account token doesn't work here.
class PlexServerApi(private val server: PlexServer, private val clientIdentifier: String) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchSections(): List<PlexSection> = withContext(Dispatchers.IO) {
        val body = execute("${server.baseUrl}/library/sections")
        json.decodeFromString(SectionsResponse.serializer(), body).mediaContainer.directories
            .filter { it.type == "movie" || it.type == "show" }
    }

    suspend fun fetchLibraryItems(sectionKey: String): List<PlexLibraryItem> = withContext(Dispatchers.IO) {
        val body = execute("${server.baseUrl}/library/sections/$sectionKey/all")
        json.decodeFromString(LibraryItemsResponse.serializer(), body).mediaContainer.items
    }

    suspend fun fetchSeasons(showRatingKey: String): List<PlexSeason> = withContext(Dispatchers.IO) {
        val body = execute("${server.baseUrl}/library/metadata/$showRatingKey/children")
        json.decodeFromString(SeasonsResponse.serializer(), body).mediaContainer.items
    }

    suspend fun fetchEpisodes(seasonRatingKey: String): List<PlexEpisode> = withContext(Dispatchers.IO) {
        val body = execute("${server.baseUrl}/library/metadata/$seasonRatingKey/children")
        json.decodeFromString(EpisodesResponse.serializer(), body).mediaContainer.items
    }

    suspend fun fetchMovieDetail(ratingKey: String): PlexMovieDetail = withContext(Dispatchers.IO) {
        val body = execute("${server.baseUrl}/library/metadata/$ratingKey")
        json.decodeFromString(MovieDetailResponse.serializer(), body).mediaContainer.items.first()
    }

    suspend fun fetchOnDeck(): List<PlexOnDeckItem> = withContext(Dispatchers.IO) {
        val body = execute("${server.baseUrl}/library/onDeck")
        json.decodeFromString(OnDeckResponse.serializer(), body).mediaContainer.items
    }

    suspend fun removeFromContinueWatching(ratingKey: String): Unit = withContext(Dispatchers.IO) {
        execute("${server.baseUrl}/actions/removeFromContinueWatching?ratingKey=$ratingKey", method = "PUT")
    }

    suspend fun fetchRecentlyAdded(): List<PlexLibraryItem> = withContext(Dispatchers.IO) {
        val body = execute("${server.baseUrl}/library/recentlyAdded")
        json.decodeFromString(LibraryItemsResponse.serializer(), body).mediaContainer.items
    }

    // Identifier naming isn't confirmed against a real server yet — matched
    // defensively by substring (identifier or title) rather than one
    // hardcoded string, since Plex's exact hub-identifier for its
    // personalized/suggested shelf varies by server/version. Returns empty
    // if nothing matches, which is a valid state (nothing suggested yet),
    // not necessarily a bug — see verification notes for this feature.
    // /hubs/home (plex.tv's cloud Discover path) 404s against a local Plex
    // Media Server — confirmed against a real server. /hubs/promoted is the
    // actual local-PMS path behind the official app's home-screen hub rows
    // (continue watching, recently added, suggested/related — all bundled
    // together as separate Hub entries in one response). Confirmed on a
    // real server: this hub only exists when the server actually has
    // suggested/related content to offer (likely gated on Plex Pass or
    // populated match data) — a server without one simply omits it from
    // /hubs/promoted entirely, which is why an empty result here is a valid
    // "nothing to suggest yet" state, not necessarily a bug.
    suspend fun fetchSuggestions(): List<PlexOnDeckItem> = withContext(Dispatchers.IO) {
        val body = execute("${server.baseUrl}/hubs/promoted")
        val hubs = json.decodeFromString(HubsResponse.serializer(), body).mediaContainer.hubs
        val suggested = hubs.firstOrNull { hub ->
            val id = hub.hubIdentifier?.lowercase() ?: ""
            val title = hub.title.lowercase()
            "suggest" in id || "recommend" in id || "suggest" in title || "recommend" in title
        }
        suggested?.items ?: emptyList()
    }

    private fun execute(url: String, method: String = "GET"): String {
        val request = Request.Builder()
            .url(url)
            .apply { if (method == "PUT") put(ByteArray(0).toRequestBody(null)) else get() }
            .addHeader("Accept", "application/json")
            .addHeader("X-Plex-Token", server.accessToken)
            .addHeader("X-Plex-Client-Identifier", clientIdentifier)
            .build()
        client.newCall(request).execute().use { response ->
            val bodyString = response.body.string()
            check(response.isSuccessful) { "Plex request to $url failed: ${response.code} $bodyString" }
            return bodyString
        }
    }
}
