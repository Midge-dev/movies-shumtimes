package com.moviesshumtimes.tv.data.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class PlexSection(val key: String, val title: String, val type: String = "")

@Serializable
data class PlexMovie(
    val ratingKey: String,
    val title: String,
    val year: Int? = null,
    val thumb: String? = null,
    val art: String? = null,
    val summary: String? = null,
)

@Serializable
private data class SectionsMediaContainer(@SerialName("Directory") val directories: List<PlexSection> = emptyList())

@Serializable
private data class SectionsResponse(@SerialName("MediaContainer") val mediaContainer: SectionsMediaContainer)

@Serializable
private data class MoviesMediaContainer(@SerialName("Metadata") val items: List<PlexMovie> = emptyList())

@Serializable
private data class MoviesResponse(@SerialName("MediaContainer") val mediaContainer: MoviesMediaContainer)

// Talks directly to a Plex Media Server (as opposed to plex.tv account
// endpoints) using the server-specific access token from PlexResourcesApi —
// the account token doesn't work here.
class PlexServerApi(private val server: PlexServer, private val clientIdentifier: String) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchMovieSections(): List<PlexSection> = withContext(Dispatchers.IO) {
        val body = execute("${server.baseUrl}/library/sections")
        json.decodeFromString(SectionsResponse.serializer(), body).mediaContainer.directories
            .filter { it.type == "movie" }
    }

    suspend fun fetchMovies(sectionKey: String): List<PlexMovie> = withContext(Dispatchers.IO) {
        val body = execute("${server.baseUrl}/library/sections/$sectionKey/all")
        json.decodeFromString(MoviesResponse.serializer(), body).mediaContainer.items
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
