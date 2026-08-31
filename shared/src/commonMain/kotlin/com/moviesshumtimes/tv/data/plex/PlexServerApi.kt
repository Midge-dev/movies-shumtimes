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

// A show's own metadata, requested with includeOnDeck=1 — Plex embeds the
// in-progress/next-up episode (if any) as a nested OnDeck element rather
// than exposing it as its own sub-resource (there's no
// /library/metadata/{id}/onDeck — confirmed 404 against a real server).
@Serializable
private data class EmbeddedOnDeck(@SerialName("Metadata") val items: List<PlexOnDeckItem> = emptyList())

@Serializable
private data class ShowMetadataWithOnDeck(@SerialName("OnDeck") val onDeck: EmbeddedOnDeck? = null)

@Serializable
private data class ShowMetadataContainer(@SerialName("Metadata") val items: List<ShowMetadataWithOnDeck> = emptyList())

@Serializable
private data class ShowMetadataResponse(@SerialName("MediaContainer") val mediaContainer: ShowMetadataContainer)

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

    // includeReviews=1 is what makes the server attach the Review[] array
    // (Rotten Tomatoes critic quotes) to the response — confirmed this
    // session against a real server; without it Review is simply absent.
    suspend fun fetchMovieDetail(ratingKey: String): PlexMovieDetail {
        val items = get<MovieDetailResponse>("${server.baseUrl}/library/metadata/$ratingKey?includeReviews=1").mediaContainer.items
        // An empty items array is a real response shape, not a parsing
        // failure — it's what the server returns for a ratingKey that was
        // deleted/moved from the library since whatever list linked here was
        // loaded. Every caller already wraps this call in runCatching and
        // surfaces exceptionOrNull()?.message, so this exists purely to make
        // that message say something useful instead of a bare
        // NoSuchElementException.
        return items.firstOrNull() ?: throw NoSuchElementException("This title is no longer available on the server.")
    }

    // Confirmed this session against a real server: a single call to
    // /related returns both a "Related Movies" hub and, when the top-billed
    // actor has other titles in this library, an auto-generated
    // "More with <lead actor>" hub — render each under the title the server
    // gives it rather than assuming which hubs come back or hardcoding
    // "Related Movies".
    suspend fun fetchRelatedHubs(ratingKey: String): List<PlexHub> =
        get<HubsResponse>("${server.baseUrl}/library/metadata/$ratingKey/related").mediaContainer.hubs

    // For "More with <co-star>" — Plex only auto-generates a same-actor hub
    // for the top-billed cast member (see fetchRelatedHubs), so any other
    // cast/crew member's filmography has to be queried directly. actorId is
    // the person's PlexPerson.id (their tag ID), not their name.
    suspend fun fetchLibraryItemsByActor(sectionKey: String, actorId: Long): List<PlexLibraryItem> =
        get<LibraryItemsResponse>("${server.baseUrl}/library/sections/$sectionKey/all?actor=$actorId").mediaContainer.items

    suspend fun fetchOnDeck(): List<PlexOnDeckItem> =
        get<OnDeckResponse>("${server.baseUrl}/library/onDeck").mediaContainer.items

    // Resolves what "Play" should do for a show's detail screen: the
    // in-progress/next-up episode if the show has watch history, falling
    // back to season 1 (skipping specials, index 0) episode 1 for a show
    // that's never been started. Returned as a PlexOnDeckItem — same shape
    // either way — so the caller gets parentIndex/index for the "Play S1E1"
    // label and ratingKey for playback without a second model.
    suspend fun fetchNextEpisodeForShow(showRatingKey: String): PlexOnDeckItem? {
        // Best-effort: a server that doesn't populate OnDeck for this show
        // (or an unexpected response shape) just means "nothing in
        // progress" — fall through to the season/episode fallback below
        // rather than failing the whole lookup.
        val onDeck = runCatching {
            get<ShowMetadataResponse>("${server.baseUrl}/library/metadata/$showRatingKey?includeOnDeck=1")
                .mediaContainer.items.firstOrNull()?.onDeck?.items?.firstOrNull()
        }.getOrNull()
        if (onDeck != null) return onDeck

        val firstSeason = fetchSeasons(showRatingKey)
            .filter { (it.index ?: 0) > 0 }
            .minByOrNull { it.index ?: Int.MAX_VALUE }
            ?: return null
        val firstEpisode = fetchEpisodes(firstSeason.ratingKey)
            .minByOrNull { it.index ?: Int.MAX_VALUE }
            ?: return null
        return PlexOnDeckItem(
            ratingKey = firstEpisode.ratingKey,
            type = "episode",
            title = firstEpisode.title,
            thumb = firstEpisode.thumb,
            parentIndex = firstSeason.index,
            index = firstEpisode.index,
        )
    }

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
