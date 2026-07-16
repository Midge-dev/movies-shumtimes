package com.moviesshumtimes.tv.data.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class PlexPin(
    val id: Long,
    val code: String,
    val authToken: String? = null,
    val expiresIn: Int = 0,
)

data class PlexAccount(val username: String, val thumb: String?)

@Serializable
private data class PlexAccountResponse(
    val username: String? = null,
    val title: String? = null,
    val thumb: String? = null,
)

// Client for Plex's PIN-based auth flow, meant for limited-input devices
// like a TV: https://plex.tv/api/v2/pins.
class PlexAuthApi(private val clientIdentifier: String) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private fun Request.Builder.withPlexHeaders(): Request.Builder = apply {
        addHeader("Accept", "application/json")
        addHeader("X-Plex-Product", "Movies Shumtimes")
        addHeader("X-Plex-Client-Identifier", clientIdentifier)
    }

    // Deliberately no "strong" param: that produces a long OAuth-style code
    // meant to be embedded in an app-link URL, not the short 4-character
    // code a person can type in by hand at plex.tv/link on a TV.
    suspend fun createPin(): PlexPin = withContext(Dispatchers.IO) {
        val body = ByteArray(0).toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val request = Request.Builder()
            .url("https://plex.tv/api/v2/pins")
            .post(body)
            .withPlexHeaders()
            .build()
        execute(request) { json.decodeFromString(PlexPin.serializer(), it) }
    }

    suspend fun pollPin(id: Long): PlexPin = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://plex.tv/api/v2/pins/$id")
            .get()
            .withPlexHeaders()
            .build()
        execute(request) { json.decodeFromString(PlexPin.serializer(), it) }
    }

    suspend fun fetchAccount(authToken: String): PlexAccount = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://plex.tv/api/v2/user")
            .get()
            .addHeader("X-Plex-Token", authToken)
            .withPlexHeaders()
            .build()
        val account = execute(request) { json.decodeFromString(PlexAccountResponse.serializer(), it) }
        PlexAccount(username = account.username ?: account.title ?: "Plex user", thumb = account.thumb)
    }

    private inline fun <T> execute(request: Request, parse: (String) -> T): T {
        client.newCall(request).execute().use { response ->
            val bodyString = response.body.string()
            check(response.isSuccessful) { "Plex request to ${request.url} failed: ${response.code} $bodyString" }
            return parse(bodyString)
        }
    }
}
