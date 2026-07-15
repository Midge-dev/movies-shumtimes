package com.moviesshumtimes.tv.data.plex

object PlexImageUrl {
    fun of(server: PlexServer, path: String?): String? {
        if (path.isNullOrBlank()) return null
        return "${server.baseUrl}$path?X-Plex-Token=${server.accessToken}"
    }
}
