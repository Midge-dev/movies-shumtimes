package com.moviesshumtimes.tv.ui.player

import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.moviesshumtimes.tv.R
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

// Held-seek acceleration: a single tap moves 10s, but the step grows the
// longer the button stays down (event.repeatCount climbs each ~50ms while
// held), matching the big streaming apps' hold-to-fast-seek feel instead of
// crawling through a movie 10s at a time.
private fun seekIncrementForHold(repeatCount: Int): Long = when {
    repeatCount == 0 -> 10_000L
    repeatCount < 8 -> 20_000L
    repeatCount < 20 -> 45_000L
    else -> 90_000L
}

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
    var isBuffering by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val screenFocusRequester = remember { FocusRequester() }

    // PlayerView.dispatchKeyEvent() short-circuits every D-pad key while its
    // controller is hidden, showing it and swallowing the event before a
    // native key listener on the view ever sees it (confirmed by inspecting
    // its bytecode: it returns early for KEYCODE_DPAD_CENTER without calling
    // any child dispatch). Intercepting one layer up, in Compose, sidesteps
    // that entirely — this fires during the tunnel/preview phase before the
    // event ever reaches the embedded native view.
    LaunchedEffect(Unit) { screenFocusRequester.requestFocus() }

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

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(REPORT_INTERVAL_MS)
            val state = if (player.isPlaying) "playing" else "paused"
            val duration = detail.duration ?: player.duration.coerceAtLeast(0)
            reporter.report(detail.ratingKey, state, player.currentPosition.coerceAtLeast(0), duration)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(screenFocusRequester)
            .onPreviewKeyEvent { keyEvent ->
                val isSelect = keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter
                val controllerHidden = playerView?.isControllerFullyVisible == false
                if (isSelect && keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.nativeKeyEvent.repeatCount == 0 && controllerHidden
                ) {
                    player.playWhenReady = !player.playWhenReady
                    playerView?.showController()
                    true
                } else {
                    false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                val themedContext = ContextThemeWrapper(context, R.style.PlayerControlsTheme)
                PlayerView(themedContext).apply {
                    useController = true
                    this.player = player
                    playerView = this
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.apply {
                        setPlayedColor(NeonPurple.toArgb())
                        setScrubberColor(NeonPurple.toArgb())
                        setOnKeyListener { _, keyCode, event ->
                            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                            val direction = when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                                KeyEvent.KEYCODE_DPAD_LEFT -> -1
                                else -> return@setOnKeyListener false
                            }
                            val increment = seekIncrementForHold(event.repeatCount)
                            val target = (player.currentPosition + direction * increment)
                                .coerceIn(0, player.duration.coerceAtLeast(0))
                            player.seekTo(target)
                            true
                        }
                    }
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controllerVisible = visibility == View.VISIBLE
                        },
                    )
                }
            },
        )
        if (isBuffering) {
            BufferingSpinner(modifier = Modifier.align(Alignment.Center))
        }
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
