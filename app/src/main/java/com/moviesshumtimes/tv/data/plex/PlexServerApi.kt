package com.moviesshumtimes.tv.data.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
    // "movie" or "show" — absent on older responses that predate this field
    // being captured, but always present from Plex itself. Lets a
    // Home-sourced item (Recently Added / Suggestions) resolve isShow
    // without a ctx.selectedSection to infer it from.
    val type: String? = null,
    val title: String,
    // Only meaningful for type == "season" — /library/recentlyAdded surfaces
    // newly-added TV content at season granularity ("Season 14"), and
    // parentTitle/parentRatingKey are the actual show's name/ratingKey Plex
    // attaches to a season item, used to route taps to the show's own
    // detail screen instead of the season (which has no detail screen of
    // its own in this app). Confirmed against a real server: recentlyAdded
    // genuinely returns season-level items, not the show itself.
    val parentTitle: String? = null,
    val parentRatingKey: String? = null,
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

// streamType: 1 = video, 2 = audio, 3 = subtitle. id is the value the Plex
// API calls subtitleStreamID elsewhere (universal transcode's subtitle
// picker param) — distinct from index, which is just this stream's
// position within the container.
@Serializable
data class PlexStream(
    val id: Long = 0,
    val streamType: Int,
    val codec: String? = null,
    val language: String? = null,
    val languageCode: String? = null,
    val key: String? = null,
    val index: Int? = null,
    val selected: Boolean = false,
    val forced: Boolean = false,
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

// A mixed on-deck entry — a partially-watched movie, or the next-up episode
// of an in-progress show. grandparentTitle/parentIndex/index are only
// present when type == "episode" (show name / season # / episode #).
@Serializable
data class PlexOnDeckItem(
    val ratingKey: String,
    val type: String,
    val title: String,
    val thumb: String? = null,
    val art: String? = null,
    val duration: Long? = null,
    val viewOffset: Long? = null,
    val grandparentTitle: String? = null,
    val parentIndex: Int? = null,
    val index: Int? = null,
)

@Serializable
private data class OnDeckMediaContainer(@SerialName("Metadata") val items: List<PlexOnDeckItem> = emptyList())

@Serializable
private data class OnDeckResponse(@SerialName("MediaContainer") val mediaContainer: OnDeckMediaContainer)

// One shelf from Plex's personalized home feed (/hubs/home) — the same
// mechanism behind the official app's "Suggested"/"Because you watched X"
// rows. Reuses PlexOnDeckItem for the item shape (mixed movie/show/episode,
// same fields) rather than a fourth near-identical model; viewOffset/
// duration are simply unused here.
@Serializable
data class PlexHub(
    val hubIdentifier: String? = null,
    val title: String = "",
    @SerialName("Metadata") val items: List<PlexOnDeckItem> = emptyList(),
)

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
