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
    @SerialName("clientIdentifier") val machineIdentifier: String = "",
    val accessToken: String? = null,
    val connections: List<PlexConnection> = emptyList(),
)

data class PlexServer(val name: String, val baseUrl: String, val accessToken: String)

@Serializable
data class PlexSection(val key: String, val title: String, val type: String = "")

@Serializable
data class PlexTag(val tag: String)

@Serializable
data class PlexLibraryItem(
    val ratingKey: String,
    val type: String? = null,
    val title: String,
    val parentTitle: String? = null,
    val parentRatingKey: String? = null,
    val year: Int? = null,
    val thumb: String? = null,
    val art: String? = null,
    val summary: String? = null,
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
    val duration: Long? = null,
    val viewOffset: Long? = null,
    val parentIndex: Int? = null,
    val grandparentTitle: String? = null,
    val originallyAvailableAt: String? = null,
)

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

@Serializable
data class PlexPerson(
    val id: Long? = null,
    val tag: String,
    val role: String? = null,
    val thumb: String? = null,
)

@Serializable
data class PlexReview(
    val tag: String,
    val text: String,
    val source: String? = null,
    val image: String? = null,
)

@Serializable
data class PlexMovieDetail(
    val ratingKey: String,
    val title: String,
    val thumb: String? = null,
    val art: String? = null,
    val duration: Long? = null,
    val viewOffset: Long? = null,
    @SerialName("Media") val media: List<PlexMedia> = emptyList(),
    val rating: Double? = null,
    val audienceRating: Double? = null,
    val ratingImage: String? = null,
    val audienceRatingImage: String? = null,
    @SerialName("Role") val roles: List<PlexPerson> = emptyList(),
    @SerialName("Director") val directors: List<PlexPerson> = emptyList(),
    @SerialName("Writer") val writers: List<PlexPerson> = emptyList(),
    @SerialName("Review") val reviews: List<PlexReview> = emptyList(),
)

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
data class PlexHub(
    val hubIdentifier: String? = null,
    val title: String = "",
    @SerialName("Metadata") val items: List<PlexOnDeckItem> = emptyList(),
)
