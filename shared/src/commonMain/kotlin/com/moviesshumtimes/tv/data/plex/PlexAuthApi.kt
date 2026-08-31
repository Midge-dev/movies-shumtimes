package com.moviesshumtimes.tv.data.plex

import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

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

class PlexAuthApi(private val clientIdentifier: String) {
    private val client = plexHttpClient()

    private fun HttpRequestBuilder.withPlexHeaders() {
        header("Accept", "application/json")
        header("X-Plex-Product", "Movies Shumtimes")
        header("X-Plex-Client-Identifier", clientIdentifier)
    }

    suspend fun createPin(): PlexPin =
        client.post("https://plex.tv/api/v2/pins") {
            withPlexHeaders()
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(ByteArray(0))
        }.body()

    suspend fun pollPin(id: Long): PlexPin =
        client.get("https://plex.tv/api/v2/pins/$id") {
            withPlexHeaders()
        }.body()

    suspend fun fetchAccount(authToken: String): PlexAccount {
        val account: PlexAccountResponse = client.get("https://plex.tv/api/v2/user") {
            withPlexHeaders()
            header("X-Plex-Token", authToken)
        }.body()
        return PlexAccount(username = account.username ?: account.title ?: "Plex user", thumb = account.thumb)
    }
}
