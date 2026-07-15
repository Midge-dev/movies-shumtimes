package com.moviesshumtimes.tv.ui.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.moviesshumtimes.tv.data.plex.PlexMovieDetail
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.playback.PlexPlayerFactory
import com.moviesshumtimes.tv.playback.TimelineReporter
import com.moviesshumtimes.tv.playback.decidePlayback
import com.moviesshumtimes.tv.sync.RelayClient
import com.moviesshumtimes.tv.sync.RelayConfig
import com.moviesshumtimes.tv.sync.SyncViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REPORT_INTERVAL_MS = 5_000L

@Composable
fun PlayerScreen(
    server: PlexServer,
    detail: PlexMovieDetail,
    clientIdentifier: String,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reporter = remember(server, clientIdentifier) { TimelineReporter(server, clientIdentifier) }
    val decision = remember(detail) { decidePlayback(detail) }
    val player = remember(decision) { PlexPlayerFactory.create(context, server, decision) }
    val sync = remember(player) { SyncViewModel(player, RelayClient(RelayConfig.URL), scope) }

    BackHandler {
        val duration = detail.duration ?: player.duration.coerceAtLeast(0)
        val position = player.currentPosition.coerceAtLeast(0)
        sync.stop()
        player.stop()
        scope.launch { reporter.report(detail.ratingKey, "stopped", position, duration) }
        onExit()
    }

    DisposableEffect(player, sync) {
        sync.start()
        onDispose {
            sync.stop()
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(REPORT_INTERVAL_MS)
            val state = if (player.isPlaying) "playing" else "paused"
            val duration = detail.duration ?: player.duration.coerceAtLeast(0)
            reporter.report(detail.ratingKey, state, player.currentPosition.coerceAtLeast(0), duration)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            PlayerView(context).apply {
                useController = true
                this.player = player
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
    )
}
