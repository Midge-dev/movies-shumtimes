package com.moviesshumtimes.tv.data.plex

object PlexImageUrl {
    fun of(server: PlexServer, path: String?): String? {
        if (path.isNullOrBlank()) return null
        // Most thumb/art fields are server-relative paths, but cast/crew
        // headshots (Role/Director/Writer.thumb) come back from Plex as
        // already-absolute external URLs (metadata-static.plex.tv) —
        // confirmed against a real server. Prefixing those with the
        // server's own baseUrl produced an invalid URL that failed to
        // load silently (no crash, just a blank placeholder).
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return "${server.baseUrl}$path?X-Plex-Token=${server.accessToken}"
    }
}
