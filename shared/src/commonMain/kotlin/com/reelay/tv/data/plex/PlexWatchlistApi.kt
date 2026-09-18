package com.reelay.tv.data.plex

import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class WatchlistMediaContainer(@SerialName("Metadata") val items: List<PlexWatchlistItem> = emptyList())

@Serializable
private data class WatchlistResponse(@SerialName("MediaContainer") val mediaContainer: WatchlistMediaContainer)

/**
 * Client for Plex's account-level Watchlist, which lives in the Discover metadata universe
 * (`discover.provider.plex.tv`) rather than on any local server — the same reverse-engineered
 * API `python-plexapi`'s `MyPlexAccount.watchlist()`/`addToWatchlist()` use. A watchlist item's
 * [PlexWatchlistItem.guid] (`plex://movie/<id>`) is what ties it back to a local [PlexLibraryItem]
 * with the same guid; the trailing `<id>` is also the Discover `ratingKey` these actions expect.
 */
class PlexWatchlistApi(private val clientIdentifier: String) {
    private val client = plexHttpClient()

    private fun HttpRequestBuilder.withPlexHeaders(accountToken: String) {
        header("Accept", "application/json")
        header("X-Plex-Product", "Reelay")
        header("X-Plex-Client-Identifier", clientIdentifier)
        header("X-Plex-Token", accountToken)
    }

    private fun discoverRatingKey(guid: String): String = guid.substringAfterLast('/')

    suspend fun fetchWatchlist(accountToken: String): List<PlexWatchlistItem> =
        client.get("https://discover.provider.plex.tv/library/sections/watchlist/all") {
            withPlexHeaders(accountToken)
        }.body<WatchlistResponse>().mediaContainer.items

    suspend fun addToWatchlist(accountToken: String, guid: String) {
        client.put("https://discover.provider.plex.tv/actions/addToWatchlist?ratingKey=${discoverRatingKey(guid)}") {
            withPlexHeaders(accountToken)
        }
    }

    suspend fun removeFromWatchlist(accountToken: String, guid: String) {
        client.put("https://discover.provider.plex.tv/actions/removeFromWatchlist?ratingKey=${discoverRatingKey(guid)}") {
            withPlexHeaders(accountToken)
        }
    }
}
