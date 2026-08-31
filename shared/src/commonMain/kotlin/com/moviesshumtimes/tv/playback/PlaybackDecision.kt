package com.moviesshumtimes.tv.playback

import com.moviesshumtimes.tv.data.plex.PlexMovieDetail
import com.moviesshumtimes.tv.data.plex.PlexPart
import com.moviesshumtimes.tv.data.plex.PlexStream

private const val SUBTITLE_STREAM_TYPE = 3
private val BURN_REQUIRED_SUBTITLE_CODECS = setOf("pgs", "vobsub", "dvdsub")

sealed interface PlaybackDecision {
    data class DirectPlay(val part: PlexPart, val subtitleStreamId: Long?) : PlaybackDecision
    data class Transcode(val ratingKey: String, val subtitleStreamId: Long?) : PlaybackDecision
}

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

fun defaultSubtitleStreamId(detail: PlexMovieDetail): Long? =
    detail.media.firstOrNull()?.parts?.firstOrNull()
        ?.streams?.firstOrNull { it.streamType == SUBTITLE_STREAM_TYPE && it.selected }
        ?.id
