package com.moviesshumtimes.tv.ui.player

import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import com.moviesshumtimes.tv.R
import com.moviesshumtimes.tv.data.plex.PlexMovieDetail
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.data.settings.AppSettings
import com.moviesshumtimes.tv.data.settings.SettingsStore
import com.moviesshumtimes.tv.playback.PlaybackDecision
import com.moviesshumtimes.tv.playback.PlexPlayerFactory
import com.moviesshumtimes.tv.playback.SubtitleOption
import com.moviesshumtimes.tv.playback.TimelineReporter
import com.moviesshumtimes.tv.playback.decidePlayback
import com.moviesshumtimes.tv.playback.defaultSubtitleStreamId
import com.moviesshumtimes.tv.playback.subtitleOptions
import com.moviesshumtimes.tv.sync.ConnectionState
import com.moviesshumtimes.tv.sync.PlaybackPhase
import com.moviesshumtimes.tv.sync.RelayClient
import com.moviesshumtimes.tv.sync.SyncViewModel
import com.moviesshumtimes.tv.ui.common.AppLoadingIndicator
import com.moviesshumtimes.tv.ui.common.ChatOverlay
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
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
    relay: RelayClient?,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf<AppSettings?>(null) }

    LaunchedEffect(Unit) {
        settings = SettingsStore.observe(context).first()
    }

    val currentSettings = settings ?: run {
        Text("Loading…")
        return
    }

    val part = remember(detail) { detail.media.firstOrNull()?.parts?.firstOrNull() }
    val subtitleChoices = remember(part) { part?.let { subtitleOptions(it) } ?: emptyList() }

    // Local to this device only — this is what makes subtitle choice
    // independent per viewer even though everyone's watching off the same
    // Plex item. decidePlayback/PlexPlayerFactory never call the
    // PUT /library/parts "selected stream" endpoint, so nothing here writes
    // back to shared account state on the server.
    var subtitleStreamId by remember(detail) { mutableStateOf(defaultSubtitleStreamId(detail)) }
    var restartPositionMs by remember(detail) { mutableStateOf(detail.viewOffset ?: 0L) }
    var activePlayer by remember(detail) { mutableStateOf<ExoPlayer?>(null) }

    val decision = remember(detail, subtitleStreamId, currentSettings.forceBurnSubtitles) {
        decidePlayback(detail, subtitleStreamId, currentSettings.forceBurnSubtitles)
    }

    // Only a mode change — direct play <-> a burn-required transcode, or a
    // different burn-required stream — needs a whole new ExoPlayer/transcode
    // session; there's no live "swap the subtitle" API on an existing
    // session. Switching between two directly-played text tracks (or off)
    // doesn't touch this key, so PlayerSession patches the running player in
    // place instead of restarting it — see its own LaunchedEffect(decision).
    // key() tears down and rebuilds only when playerIdentity itself changes,
    // picking up wherever playback left off via restartPositionMs (captured
    // from the outgoing player right before the switch, in onSelectSubtitle
    // below).
    val playerIdentity = when (decision) {
        is PlaybackDecision.Transcode -> decision
        is PlaybackDecision.DirectPlay -> decision.part.id
    }
    key(playerIdentity) {
        PlayerSession(
            server = server,
            detail = detail,
            decision = decision,
            startPositionMs = restartPositionMs,
            clientIdentifier = clientIdentifier,
            relay = relay,
            maxVideoBitrateKbps = currentSettings.maxVideoBitrateKbps,
            subtitleOptions = subtitleChoices,
            selectedSubtitleStreamId = subtitleStreamId,
            onPlayerCreated = { activePlayer = it },
            onSelectSubtitle = { option ->
                activePlayer?.let { restartPositionMs = it.currentPosition }
                subtitleStreamId = option.streamId
            },
            onExit = onExit,
        )
    }
}

@Composable
private fun PlayerSession(
    server: PlexServer,
    detail: PlexMovieDetail,
    decision: PlaybackDecision,
    startPositionMs: Long,
    clientIdentifier: String,
    relay: RelayClient?,
    maxVideoBitrateKbps: Int,
    subtitleOptions: List<SubtitleOption>,
    selectedSubtitleStreamId: Long?,
    onPlayerCreated: (ExoPlayer) -> Unit,
    onSelectSubtitle: (SubtitleOption) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val reporter = remember(server, clientIdentifier) { TimelineReporter(server, clientIdentifier) }
    // Deliberately keyless: this player is scoped to this PlayerSession's
    // whole lifetime (one per playerIdentity, per the parent's key()), so it
    // should only ever be built once from whatever `decision`/
    // `startPositionMs` were current at that first composition — later
    // direct-play subtitle changes arrive as recompositions of the same
    // `decision` parameter and are applied live below instead.
    val player = remember {
        PlexPlayerFactory.create(
            context = context,
            server = server,
            decision = decision,
            maxVideoBitrateKbps = maxVideoBitrateKbps,
            startPositionMs = startPositionMs,
        )
    }
    LaunchedEffect(player) { onPlayerCreated(player) }
    LaunchedEffect(decision) {
        if (decision is PlaybackDecision.DirectPlay) {
            PlexPlayerFactory.applySubtitleSelection(player, decision)
        }
    }

    val sync = remember(player) { SyncViewModel(player, relay, scope) }
    val connectionState by sync.connectionState.collectAsState()
    val phase by sync.phase.collectAsState()
    val waitingOn by sync.waitingOn.collectAsState()
    var controllerVisible by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var subtitleMenuOpen by remember { mutableStateOf(false) }
    val screenFocusRequester = remember { FocusRequester() }

    // PlayerView.dispatchKeyEvent() short-circuits every D-pad key while its
    // controller is hidden, showing it and swallowing the event before a
    // native key listener on the view ever sees it (confirmed by inspecting
    // its bytecode: it returns early for KEYCODE_DPAD_CENTER without calling
    // any child dispatch). Intercepting one layer up, in Compose, sidesteps
    // that entirely — this fires during the tunnel/preview phase before the
    // event ever reaches the embedded native view.
    LaunchedEffect(Unit) { screenFocusRequester.requestFocus() }

    // Media3's own controllerShowTimeoutMs only reschedules the hide on an
    // isPlaying state *change* — if the movie is already playing by the time
    // the controller first shows (the normal case entering from the Lobby),
    // that timer never gets armed and the controller (and our title/sync
    // overlays, which key off the same controllerVisible flag) sit onscreen
    // until a real pause/play toggle happens to trigger it. Driving the hide
    // ourselves sidesteps relying on that internal scheduling.
    LaunchedEffect(controllerVisible) {
        if (controllerVisible) {
            delay(3_000)
            playerView?.hideController()
        }
    }

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

    // The player isn't otherwise tied to the Activity lifecycle, so leaving
    // the app (home button, switching apps) doesn't stop it — audio and
    // playback kept running in the background. Pausing on ON_STOP matches
    // what every other TV player app does.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                val isDirectional = keyEvent.key == Key.DirectionUp ||
                    keyEvent.key == Key.DirectionDown ||
                    keyEvent.key == Key.DirectionLeft ||
                    keyEvent.key == Key.DirectionRight
                val controllerHidden = playerView?.isControllerFullyVisible == false
                val isFreshKeyDown = keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.nativeKeyEvent.repeatCount == 0
                // Reveal, but never swallow. A previous version of this
                // consumed the revealing keypress (returned true), which
                // meant a hidden-controller press only showed it — the same
                // D-pad press that revealed it didn't also move focus, so
                // every reveal cost an extra press before navigation did
                // anything. Calling showController() here and still falling
                // through to native dispatch (false) lets the very same
                // press both reveal the controls and land on/move the
                // focused button in one motion — nothing gets *activated* by
                // this, since a plain directional key only moves focus and
                // Select only activates whatever's already focused (that's
                // still how it's always worked; a still-earlier version
                // hijacked Select to toggle play/pause directly instead of
                // letting native dispatch route it to the focused button,
                // which broke every other button in the controller — that
                // mistake isn't being reintroduced here).
                if (isFreshKeyDown && (isSelect || isDirectional) && controllerHidden) {
                    playerView?.showController()
                }
                false
            }
            .focusable(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                val themedContext = ContextThemeWrapper(context, R.style.PlayerControlsTheme)
                PlayerView(themedContext).apply {
                    useController = true
                    controllerShowTimeoutMs = 3_000
                    // Otherwise PlayerView re-shows the controller on its own
                    // whenever playback state fires an event (buffering
                    // blips, position updates), fighting our own hide timer
                    // below and making the auto-hide effectively invisible
                    // during normal viewing.
                    controllerAutoShow = false
                    setShowSubtitleButton(true)
                    this.player = player
                    playerView = this
                    // controllerAutoShow=false means Media3 won't show the
                    // controller on its own even for the very first frame —
                    // do that one show explicitly so title/controls still
                    // greet the user on entry, same as before.
                    showController()
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    // Deferred to post(): PlayerView's controller row isn't
                    // guaranteed fully inflated/attached the instant this
                    // apply{} block runs, and findViewById on a not-yet-ready
                    // subtree silently no-ops instead of failing loudly.
                    post {
                        listOf(
                            androidx.media3.ui.R.id.exo_play_pause,
                            androidx.media3.ui.R.id.exo_prev,
                            androidx.media3.ui.R.id.exo_next,
                            androidx.media3.ui.R.id.exo_subtitle,
                        ).forEach { id ->
                            findViewById<View>(id)?.apply {
                                background = ContextCompat.getDrawable(themedContext, R.drawable.exo_control_button_focus)
                                foreground = null
                            }
                        }
                        // Media3's own subtitle button only knows about
                        // tracks ExoPlayer can already see in the current
                        // MediaItem, so it can't offer streams that need a
                        // server-side transcode to burn in (image-based
                        // subs) — replacing its click behavior with our own
                        // picker (built from Plex's full stream list, not
                        // just ExoPlayer's) covers both cases in one place,
                        // while keeping the button itself inside PlayerView's
                        // already-working D-pad focus order alongside
                        // play/pause/prev/next.
                        findViewById<View>(androidx.media3.ui.R.id.exo_subtitle)?.apply {
                            visibility = View.VISIBLE
                            isEnabled = true
                            setOnClickListener {
                                playerView?.hideController()
                                subtitleMenuOpen = true
                            }
                        }
                    }
                    findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.apply {
                        // DefaultTimeBar has no gradient-stroke concept like
                        // the Compose two-tone borders elsewhere, so the
                        // two-tone treatment here is the played fill in the
                        // deeper NeonPurple with the scrubber (the focal,
                        // "active" point) in the lighter NeonPurpleGlow —
                        // same pairing, closest equivalent this component
                        // supports.
                        setPlayedColor(NeonPurple.toArgb())
                        setScrubberColor(NeonPurpleGlow.toArgb())
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
            AppLoadingIndicator(modifier = Modifier.align(Alignment.Center))
        }
        if (controllerVisible) {
            Text(
                text = detail.title,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (connectionState != ConnectionState.CONNECTED && controllerVisible) {
            Text(
                text = when (connectionState) {
                    ConnectionState.CONNECTING -> "Sync: connecting…"
                    ConnectionState.RECONNECTING -> "Sync: reconnecting…"
                    ConnectionState.ROOM_FULL -> "Sync: room full"
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
        if (phase == PlaybackPhase.WAITING_FOR_PEERS && waitingOn.isNotEmpty()) {
            Text(
                text = "Waiting for the room to catch up…",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        ChatOverlay(
            messages = sync.chatMessages,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        )
        if (subtitleMenuOpen) {
            SubtitleMenu(
                options = subtitleOptions,
                selectedStreamId = selectedSubtitleStreamId,
                onSelect = { option ->
                    subtitleMenuOpen = false
                    onSelectSubtitle(option)
                },
                onDismiss = { subtitleMenuOpen = false },
                modifier = Modifier.align(Alignment.CenterEnd).padding(24.dp),
            )
        }
    }
}

@Composable
private fun SubtitleMenu(
    options: List<SubtitleOption>,
    selectedStreamId: Long?,
    onSelect: (SubtitleOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    val focusRequesters = remember(options) { options.map { FocusRequester() } }
    LaunchedEffect(options) {
        val selectedIndex = options.indexOfFirst { it.streamId == selectedStreamId }.coerceAtLeast(0)
        runCatching { focusRequesters.getOrNull(selectedIndex)?.requestFocus() }
    }

    Column(
        modifier = modifier
            .width(360.dp)
            .heightIn(max = 480.dp)
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = "Subtitles", color = Color.White, style = MaterialTheme.typography.titleMedium)
        // Plain Column had no height cap, so a title with many language
        // tracks just grew past the screen edge — D-pad Down still moved
        // focus onto those off-screen rows, but with nothing to auto-scroll
        // the container, the highlight visually vanished. LazyColumn caps
        // the visible height and scrolls the focused row into view on its
        // own, the same way HomeScreen's rows already rely on for
        // focus-driven scrolling.
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(options) { index, option ->
                val selected = option.streamId == selectedStreamId
                ListItem(
                    selected = selected,
                    onClick = { onSelect(option) },
                    headlineContent = { Text(option.label) },
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                    modifier = Modifier
                        .focusRequester(focusRequesters[index])
                        .focusProperties {
                            up = if (index > 0) focusRequesters[index - 1] else FocusRequester.Default
                            down = if (index < focusRequesters.lastIndex) focusRequesters[index + 1] else FocusRequester.Default
                        },
                )
            }
        }
    }
}
