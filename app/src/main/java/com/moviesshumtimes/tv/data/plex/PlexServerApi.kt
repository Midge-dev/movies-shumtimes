package com.moviesshumtimes.tv.data.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

// type is "movie" or "show" — every other Plex library (Anime, Stand-up
// Comedy, etc.) is really just a custom-named library of one of those two
// underlying types.
@Serializable
data class PlexSection(val key: String, val title: String, val type: String = "")

@Serializable
data class PlexTag(val tag: String)

// A top-level browsable item in a section grid — a movie in a "movie"
// section, or a show in a "show" section. Same JSON shape either way.
@Serializable
data class PlexLibraryItem(
    val ratingKey: String,
    val title: String,
    val year: Int? = null,
    val thumb: String? = null,
    val art: String? = null,
    val summary: String? = null,
    // Epoch seconds this was added to the Plex library (for the "date
    // added" sort/filter) — distinct from originallyAvailableAt, which is
    // the title's actual release date.
    val addedAt: Long? = null,
    val originallyAvailableAt: String? = null,
    @SerialName("Genre") val genres: List<PlexTag> = emptyList(),
)

@Serializable
data class PlexSeason(
    val ratingKey: String,
    val title: String,
    val index: Int? = null,
    val thumb: String? = null,
)

@Serializable
data class PlexEpisode(
    val ratingKey: String,
    val title: String,
    val index: Int? = null,
    val thumb: String? = null,
    val summary: String? = null,
)

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

// streamType: 1 = video, 2 = audio, 3 = subtitle.
@Serializable
data class PlexStream(
    val streamType: Int,
    val codec: String? = null,
    val language: String? = null,
    val key: String? = null,
    val index: Int? = null,
    val selected: Boolean = false,
)

@Serializable
data class PlexPart(
    val id: Long,
    val key: String,
    val container: String? = null,
    val duration: Long? = null,
    @SerialName("Stream") val streams: List<PlexStream> = emptyList(),
)

@Serializable
data class PlexMedia(
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val container: String? = null,
    val videoResolution: String? = null,
    @SerialName("Part") val parts: List<PlexPart> = emptyList(),
)

// Detail needed to actually play something — a movie or an episode, both of
// which have the same Media/Part/Stream shape in Plex's API.
@Serializable
data class PlexMovieDetail(
    val ratingKey: String,
    val title: String,
    val duration: Long? = null,
    // Position (ms) Plex has recorded from a previous partial watch, via our
    // own TimelineReporter calls or another Plex client — absent/0 means
    // start from the beginning.
    val viewOffset: Long? = null,
    @SerialName("Media") val media: List<PlexMedia> = emptyList(),
)

@Serializable
private data class MovieDetailMediaContainer(@SerialName("Metadata") val items: List<PlexMovieDetail> = emptyList())

@Serializable
private data class MovieDetailResponse(@SerialName("MediaContainer") val mediaContainer: MovieDetailMediaContainer)

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

    private fun execute(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
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
