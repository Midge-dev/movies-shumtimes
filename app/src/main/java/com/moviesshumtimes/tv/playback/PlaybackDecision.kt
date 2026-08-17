package com.moviesshumtimes.tv.playback

import com.moviesshumtimes.tv.data.plex.PlexMovieDetail
import com.moviesshumtimes.tv.data.plex.PlexPart
import com.moviesshumtimes.tv.data.plex.PlexStream

// streamType 3 = subtitle, per Plex's Media/Part/Stream schema.
private const val SUBTITLE_STREAM_TYPE = 3
private val BURN_REQUIRED_SUBTITLE_CODECS = setOf("pgs", "vobsub", "dvdsub")

sealed interface PlaybackDecision {
    data class DirectPlay(val part: PlexPart, val subtitleStreamId: Long?) : PlaybackDecision
    data class Transcode(val ratingKey: String, val subtitleStreamId: Long?) : PlaybackDecision
}

// streamId null represents "Off". label/requiresBurn are precomputed for the
// picker UI so it doesn't need to know about codec internals.
data class SubtitleOption(
    val streamId: Long?,
    val label: String,
    val requiresBurn: Boolean,
)

fun subtitleOptions(part: PlexPart): List<SubtitleOption> {
    val tracks = part.streams
        .filter { it.streamType == SUBTITLE_STREAM_TYPE }
        .map { stream ->
            SubtitleOption(
                streamId = stream.id,
                label = subtitleLabel(stream),
                requiresBurn = stream.requiresBurn(),
            )
        }
    return listOf(SubtitleOption(streamId = null, label = "Off", requiresBurn = false)) + tracks
}

private fun subtitleLabel(stream: PlexStream): String {
    val base = stream.language ?: stream.codec?.uppercase() ?: "Unknown"
    val suffix = when {
        stream.forced -> " (Forced)"
        stream.requiresBurn() -> " (transcode)"
        else -> ""
    }
    return base + suffix
}

private fun PlexStream.requiresBurn(): Boolean = codec?.lowercase() in BURN_REQUIRED_SUBTITLE_CODECS

// Text-based subs (srt/webvtt/ass) are demuxed and rendered client-side by
// Media3 when the container is direct-played — no special handling needed.
// Image-based subs (pgs/vobsub) can't be rendered by Media3 at all, so if
// the *chosen* subtitle stream is one of those, the only way to see it is a
// server-side transcode that burns it into the video. `forceBurn` is the
// settings-screen override for titles where the automatic per-stream
// decision guesses wrong; it only applies when a subtitle is actually
// chosen — forcing a burn with nothing to burn would just be a pointless
// transcode.
fun decidePlayback(detail: PlexMovieDetail, subtitleStreamId: Long?, forceBurn: Boolean = false): PlaybackDecision {
    val media = detail.media.firstOrNull() ?: error("No playable media found for ${detail.title}")
    val part = media.parts.firstOrNull() ?: error("No file found for ${detail.title}")

    val chosenStream = part.streams
        .firstOrNull { it.streamType == SUBTITLE_STREAM_TYPE && it.id == subtitleStreamId }
    val requiresBurn = subtitleStreamId != null && (forceBurn || chosenStream?.requiresBurn() == true)

    return if (requiresBurn) {
        PlaybackDecision.Transcode(detail.ratingKey, subtitleStreamId)
    } else {
        PlaybackDecision.DirectPlay(part, subtitleStreamId)
    }
}

// Default subtitle selection when a title is first opened: whatever Plex
// already has marked "selected" for this part (mirrors Plex Web/mobile,
// which remember your last choice server-side). Local to this device from
// here on — the in-player picker changes only this device's ExoPlayer
// instance/transcode session, never the server-side selected flag, so two
// people watching the same item together can pick differently.
fun defaultSubtitleStreamId(detail: PlexMovieDetail): Long? =
    detail.media.firstOrNull()?.parts?.firstOrNull()
        ?.streams?.firstOrNull { it.streamType == SUBTITLE_STREAM_TYPE && it.selected }
        ?.id
