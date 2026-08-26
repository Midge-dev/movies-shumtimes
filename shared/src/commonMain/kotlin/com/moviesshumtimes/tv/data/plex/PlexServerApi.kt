package com.moviesshumtimes.tv.data.plex

import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// PlexSection/PlexTag/PlexLibraryItem/PlexSeason/PlexEpisode/PlexStream/
// PlexPart/PlexMedia/PlexMovieDetail/PlexOnDeckItem/PlexHub live in the
// shared module now (data/plex/PlexModels.kt) — pure data, no OkHttp, so
// they're portable to every platform's client.

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
    private val client = plexHttpClient()

    private fun HttpRequestBuilder.withPlexHeaders() {
        header("Accept", "application/json")
        header("X-Plex-Token", server.accessToken)
        header("X-Plex-Client-Identifier", clientIdentifier)
    }

    private suspend inline fun <reified T> get(url: String): T = client.get(url) { withPlexHeaders() }.body()

    suspend fun fetchSections(): List<PlexSection> =
        get<SectionsResponse>("${server.baseUrl}/library/sections").mediaContainer.directories
            .filter { it.type == "movie" || it.type == "show" }

    suspend fun fetchLibraryItems(sectionKey: String): List<PlexLibraryItem> =
        get<LibraryItemsResponse>("${server.baseUrl}/library/sections/$sectionKey/all").mediaContainer.items

    suspend fun fetchSeasons(showRatingKey: String): List<PlexSeason> =
        get<SeasonsResponse>("${server.baseUrl}/library/metadata/$showRatingKey/children").mediaContainer.items

    suspend fun fetchEpisodes(seasonRatingKey: String): List<PlexEpisode> =
        get<EpisodesResponse>("${server.baseUrl}/library/metadata/$seasonRatingKey/children").mediaContainer.items

    suspend fun fetchMovieDetail(ratingKey: String): PlexMovieDetail =
        get<MovieDetailResponse>("${server.baseUrl}/library/metadata/$ratingKey").mediaContainer.items.first()

    suspend fun fetchOnDeck(): List<PlexOnDeckItem> =
        get<OnDeckResponse>("${server.baseUrl}/library/onDeck").mediaContainer.items

    suspend fun removeFromContinueWatching(ratingKey: String) {
        client.put("${server.baseUrl}/actions/removeFromContinueWatching?ratingKey=$ratingKey") { withPlexHeaders() }
    }

    suspend fun fetchRecentlyAdded(): List<PlexLibraryItem> =
        get<LibraryItemsResponse>("${server.baseUrl}/library/recentlyAdded").mediaContainer.items

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
    suspend fun fetchSuggestions(): List<PlexOnDeckItem> {
        val hubs = get<HubsResponse>("${server.baseUrl}/hubs/promoted").mediaContainer.hubs
        val suggested = hubs.firstOrNull { hub ->
            val id = hub.hubIdentifier?.lowercase() ?: ""
            val title = hub.title.lowercase()
            "suggest" in id || "recommend" in id || "suggest" in title || "recommend" in title
        }
        return suggested?.items ?: emptyList()
    }
}
