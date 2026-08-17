package com.moviesshumtimes.tv.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import com.moviesshumtimes.tv.data.plex.PlexServer
import java.net.URLEncoder
import java.util.UUID

// streamType 3 = subtitle, matching PlaybackDecision's constant.
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
                trackSelectionParameters = subtitleTrackSelectionParameters(trackSelectionParameters, decision)
            }
            setMediaItem(buildMediaItem(server, decision, maxVideoBitrateKbps))
            prepare()
            if (startPositionMs > 0) seekTo(startPositionMs)
            playWhenReady = true
        }

    // ExoPlayer doesn't know about Plex stream ids — it only sees whatever
    // text tracks the container's own extractor demuxes. Steering selection
    // by language code (rather than trying to map a Plex stream id to a
    // TrackGroup index) is the only correlation available without deep
    // format inspection, and is good enough short of two same-language
    // non-forced tracks in one file, which Plex clients targeting arbitrary
    // media generally accept as a known limitation too.
    private fun subtitleTrackSelectionParameters(
        base: TrackSelectionParameters,
        decision: PlaybackDecision.DirectPlay,
    ): TrackSelectionParameters {
        val builder = base.buildUpon()
        val chosen = decision.part.streams
            .firstOrNull { it.streamType == SUBTITLE_STREAM_TYPE && it.id == decision.subtitleStreamId }
        return if (chosen == null) {
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

    // Forcing directPlay=0/directStream=0 makes the server transcode both
    // video and audio into HLS, which is also the only way it will burn
    // image-based subtitles into the video (Plex always hardcodes subtitles
    // during transcode — there's no separate "burn" flag to set).
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
            // 0 is Plex's convention for "no subtitle" — explicit rather
            // than omitted, so the server doesn't fall back to its own
            // stored default and silently reintroduce a track this device
            // didn't ask for.
            "&subtitleStreamID=${decision.subtitleStreamId ?: 0}" +
            "&session=$session" +
            "&X-Plex-Token=${server.accessToken}"
    }
}
