package com.moviesshumtimes.tv.playback

import com.moviesshumtimes.tv.data.plex.PlexServer
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// Reports playback progress to /:/timeline so the cousin's account also
// sees accurate "Now Playing" / resume state, per python-plexapi's
// updateTimeline (ratingKey, key, identifier, state, time, duration).
class TimelineReporter(private val server: PlexServer, private val clientIdentifier: String) {
    private val client = OkHttpClient()

    suspend fun report(ratingKey: String, state: String, timeMs: Long, durationMs: Long) =
        withContext(Dispatchers.IO) {
            val key = URLEncoder.encode("/library/metadata/$ratingKey", "UTF-8")
            val url = "${server.baseUrl}/:/timeline" +
                "?ratingKey=$ratingKey" +
                "&key=$key" +
                "&identifier=com.plexapp.plugins.library" +
                "&state=$state" +
                "&time=$timeMs" +
                "&duration=$durationMs"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("X-Plex-Token", server.accessToken)
                .addHeader("X-Plex-Client-Identifier", clientIdentifier)
                .build()
            runCatching { client.newCall(request).execute().close() }
        }
}
