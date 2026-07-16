package com.moviesshumtimes.tv.ui.player

import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.moviesshumtimes.tv.data.plex.PlexMovieDetail
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.data.settings.AppSettings
import com.moviesshumtimes.tv.data.settings.SettingsStore
import com.moviesshumtimes.tv.playback.PlexPlayerFactory
import com.moviesshumtimes.tv.playback.TimelineReporter
import com.moviesshumtimes.tv.playback.decidePlayback
import com.moviesshumtimes.tv.sync.ConnectionState
import com.moviesshumtimes.tv.sync.RelayClient
import com.moviesshumtimes.tv.sync.SyncViewModel
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val REPORT_INTERVAL_MS = 5_000L

// DefaultTimeBar falls back to percentage-of-duration D-pad seek steps
// (1/20th of the runtime) when no fixed increment is set, which is several
// minutes per press on a feature-length movie. A flat 10s increment matches
// the feel of the big streaming apps' skip buttons.
private const val SEEK_KEY_INCREMENT_MS = 10_000L

@Composable
fun PlayerScreen(
    server: PlexServer,
    detail: PlexMovieDetail,
    clientIdentifier: String,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<AppSettings?>(null) }

    LaunchedEffect(Unit) {
        settings = SettingsStore.observe(context).first()
    }

    val currentSettings = settings ?: run {
        Text("Loading…")
        return
    }

    val reporter = remember(server, clientIdentifier) { TimelineReporter(server, clientIdentifier) }
    val decision = remember(detail, currentSettings.forceBurnSubtitles) {
        decidePlayback(detail, currentSettings.forceBurnSubtitles)
    }
    val player = remember(decision) {
        PlexPlayerFactory.create(
            context = context,
            server = server,
            decision = decision,
            maxVideoBitrateKbps = currentSettings.maxVideoBitrateKbps,
            startPositionMs = detail.viewOffset ?: 0,
        )
    }
    val sync = remember(player) {
        SyncViewModel(player, RelayClient(currentSettings.relayUrl, scope), scope)
    }
    val connectionState by sync.connectionState.collectAsState()
    var controllerVisible by remember { mutableStateOf(true) }

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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                    findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.apply {
                        setKeyTimeIncrement(SEEK_KEY_INCREMENT_MS)
                        setPlayedColor(NeonPurple.toArgb())
                        setScrubberColor(NeonPurple.toArgb())
                    }
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controllerVisible = visibility == View.VISIBLE
                        },
                    )
                }
            },
        )
        if (connectionState != ConnectionState.CONNECTED && controllerVisible) {
            Text(
                text = when (connectionState) {
                    ConnectionState.CONNECTING -> "Sync: connecting…"
                    ConnectionState.RECONNECTING -> "Sync: reconnecting…"
                    else -> "Sync: off"
                },
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
