package com.moviesshumtimes.tv.playback

import com.moviesshumtimes.tv.data.plex.PlexMovieDetail
import com.moviesshumtimes.tv.data.plex.PlexPart

// streamType 3 = subtitle, per Plex's Media/Part/Stream schema.
private const val SUBTITLE_STREAM_TYPE = 3
private val BURN_REQUIRED_SUBTITLE_CODECS = setOf("pgs", "vobsub", "dvdsub")

sealed interface PlaybackDecision {
    data class DirectPlay(val part: PlexPart) : PlaybackDecision
    data class Transcode(val ratingKey: String) : PlaybackDecision
}

// Text-based subs (srt/webvtt/ass) are demuxed and rendered client-side by
// Media3 when the container is direct-played — no special handling needed.
// Image-based subs (pgs/vobsub) can't be rendered by Media3 at all, so if
// one of those is the *selected* subtitle stream, the only way to see it is
// a server-side transcode that burns it into the video.
fun decidePlayback(detail: PlexMovieDetail): PlaybackDecision {
    val media = detail.media.firstOrNull() ?: error("No playable media found for ${detail.title}")
    val part = media.parts.firstOrNull() ?: error("No file found for ${detail.title}")

    val selectedSubtitleCodec = part.streams
        .firstOrNull { it.streamType == SUBTITLE_STREAM_TYPE && it.selected }
        ?.codec?.lowercase()

    return if (selectedSubtitleCodec in BURN_REQUIRED_SUBTITLE_CODECS) {
        PlaybackDecision.Transcode(detail.ratingKey)
    } else {
        PlaybackDecision.DirectPlay(part)
    }
}
