package com.moviesshumtimes.tv.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.moviesshumtimes.tv.data.plex.PlexServer
import java.net.URLEncoder
import java.util.UUID

object PlexPlayerFactory {
    fun create(context: Context, server: PlexServer, decision: PlaybackDecision): ExoPlayer =
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(buildMediaItem(server, decision))
            prepare()
            playWhenReady = true
        }

    private fun buildMediaItem(server: PlexServer, decision: PlaybackDecision): MediaItem = when (decision) {
        is PlaybackDecision.DirectPlay ->
            MediaItem.fromUri("${server.baseUrl}${decision.part.key}?X-Plex-Token=${server.accessToken}")
        is PlaybackDecision.Transcode ->
            MediaItem.fromUri(buildTranscodeUrl(server, decision))
    }

    // Forcing directPlay=0/directStream=0 makes the server transcode both
    // video and audio into HLS, which is also the only way it will burn
    // image-based subtitles into the video (Plex always hardcodes subtitles
    // during transcode — there's no separate "burn" flag to set).
    private fun buildTranscodeUrl(server: PlexServer, decision: PlaybackDecision.Transcode): String {
        val session = UUID.randomUUID().toString()
        val path = URLEncoder.encode("${server.baseUrl}/library/metadata/${decision.ratingKey}", "UTF-8")
        return "${server.baseUrl}/video/:/transcode/universal/start.m3u8" +
            "?path=$path" +
            "&mediaIndex=0&partIndex=0&protocol=hls" +
            "&fastSeek=1&copyts=1&offset=0" +
            "&directPlay=0&directStream=0" +
            "&videoResolution=1920x1080&maxVideoBitrate=8000" +
            "&subtitleSize=100" +
            "&session=$session" +
            "&X-Plex-Token=${server.accessToken}"
    }
}
