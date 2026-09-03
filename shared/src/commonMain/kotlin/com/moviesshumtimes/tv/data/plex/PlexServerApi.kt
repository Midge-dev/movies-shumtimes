package com.moviesshumtimes.tv.data.plex

import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

    suspend fun fetchMovieDetail(ratingKey: String): PlexMovieDetail {
        val items = get<MovieDetailResponse>("${server.baseUrl}/library/metadata/$ratingKey?includeReviews=1").mediaContainer.items
        return items.firstOrNull() ?: throw NoSuchElementException("This title is no longer available on the server.")
    }

    suspend fun fetchRelatedHubs(ratingKey: String): List<PlexHub> =
        get<HubsResponse>("${server.baseUrl}/library/metadata/$ratingKey/related").mediaContainer.hubs

    suspend fun fetchLibraryItemsByActor(sectionKey: String, actorId: Long): List<PlexLibraryItem> =
        get<LibraryItemsResponse>("${server.baseUrl}/library/sections/$sectionKey/all?actor=$actorId").mediaContainer.items

    suspend fun fetchOnDeck(): List<PlexOnDeckItem> =
        get<OnDeckResponse>("${server.baseUrl}/library/onDeck").mediaContainer.items

    suspend fun fetchNextEpisodeForShow(showRatingKey: String): PlexOnDeckItem? {
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

    /**
     * Plex only surfaces a literal "Suggested"/"Recommended" hub under
     * `/hubs/promoted` for accounts with Discover/Plex Pass recommendations
     * enabled. Without that, each library still generates its own
     * personalized "for you" hubs (Top Unwatched, Start Watching,
     * Rediscover) under `/hubs/sections/{key}` — this priority list picks
     * the best available one per library and merges them.
     */
    private val suggestionHubPriority = listOf("suggest", "recommend", "topunwatched", "startwatching", "rediscover")

    /** Per-library "Recently Watched" hubs (`movie.recentlyviewed.*`, `tv.recentlyviewed.*`). */
    private val recentActivityHubPriority = listOf("recentlyviewed", "recentlywatched", "history")

    suspend fun fetchSuggestions(): List<PlexOnDeckItem> {
        val promoted = get<HubsResponse>("${server.baseUrl}/hubs/promoted").mediaContainer.hubs
        val promotedHub = suggestionHubPriority.firstNotNullOfOrNull { keyword ->
            promoted.firstOrNull { keyword in (it.hubIdentifier?.lowercase() ?: it.title.lowercase()) && it.items.isNotEmpty() }
        }
        return promotedHub?.items ?: fetchPerSectionHubItems(suggestionHubPriority)
    }

    /** Recently finished-watching items, pulled from each library's own "Recently Watched" hub. */
    suspend fun fetchRecentActivity(): List<PlexOnDeckItem> = fetchPerSectionHubItems(recentActivityHubPriority)

    private suspend fun fetchPerSectionHubItems(keywordPriority: List<String>): List<PlexOnDeckItem> {
        val sections = get<SectionsResponse>("${server.baseUrl}/library/sections").mediaContainer.directories
        return sections.flatMap { section ->
            val sectionHubs = runCatching {
                get<HubsResponse>("${server.baseUrl}/hubs/sections/${section.key}").mediaContainer.hubs
            }.getOrElse { emptyList() }
            val hub = keywordPriority.firstNotNullOfOrNull { keyword ->
                sectionHubs.firstOrNull { keyword in (it.hubIdentifier?.lowercase() ?: "") && it.items.isNotEmpty() }
            }
            hub?.items ?: emptyList()
        }
    }
}
