package com.moviesshumtimes.tv.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import com.moviesshumtimes.tv.data.plex.PlexServer
import java.net.URLEncoder
import java.util.UUID

private const val SUBTITLE_STREAM_TYPE = 3

object PlexPlayerFactory {
    fun create(
        context: Context,
        server: PlexServer,
        decision: PlaybackDecision,
        maxVideoBitrateKbps: Int,
        startPositionMs: Long = 0,
    ): ExoPlayer =
        ExoPlayer.Builder(context).build().apply {
            if (decision is PlaybackDecision.DirectPlay) {
                applySubtitleSelection(this, decision)
            }
            setMediaItem(buildMediaItem(server, decision, maxVideoBitrateKbps))
            prepare()
            if (startPositionMs > 0) seekTo(startPositionMs)
            playWhenReady = true
        }

    fun applySubtitleSelection(player: ExoPlayer, decision: PlaybackDecision.DirectPlay) {
        val builder = player.trackSelectionParameters.buildUpon()
        val chosen = decision.part.streams
            .firstOrNull { it.streamType == SUBTITLE_STREAM_TYPE && it.id == decision.subtitleStreamId }
        player.trackSelectionParameters = if (chosen == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        } else {
            builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(chosen.languageCode)
                .setSelectUndeterminedTextLanguage(true)
                .build()
        }
    }

    private fun buildMediaItem(server: PlexServer, decision: PlaybackDecision, maxVideoBitrateKbps: Int): MediaItem =
        when (decision) {
            is PlaybackDecision.DirectPlay ->
                MediaItem.fromUri("${server.baseUrl}${decision.part.key}?X-Plex-Token=${server.accessToken}")
            is PlaybackDecision.Transcode ->
                MediaItem.fromUri(buildTranscodeUrl(server, decision, maxVideoBitrateKbps))
        }

    private fun buildTranscodeUrl(
        server: PlexServer,
        decision: PlaybackDecision.Transcode,
        maxVideoBitrateKbps: Int,
    ): String {
        val session = UUID.randomUUID().toString()
        val path = URLEncoder.encode("${server.baseUrl}/library/metadata/${decision.ratingKey}", "UTF-8")
        return "${server.baseUrl}/video/:/transcode/universal/start.m3u8" +
            "?path=$path" +
            "&mediaIndex=0&partIndex=0&protocol=hls" +
            "&fastSeek=1&copyts=1&offset=0" +
            "&directPlay=0&directStream=0" +
            "&videoResolution=1920x1080&maxVideoBitrate=$maxVideoBitrateKbps" +
            "&subtitleSize=100" +
            "&subtitleStreamID=${decision.subtitleStreamId ?: 0}" +
            "&session=$session" +
            "&X-Plex-Token=${server.accessToken}"
    }
}
