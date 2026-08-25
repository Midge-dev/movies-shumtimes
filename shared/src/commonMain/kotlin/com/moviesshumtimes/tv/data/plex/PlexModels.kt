package com.moviesshumtimes.tv.data.plex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
