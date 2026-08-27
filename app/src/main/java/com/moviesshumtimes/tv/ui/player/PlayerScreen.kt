package com.moviesshumtimes.tv.ui.player

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
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
import com.moviesshumtimes.tv.data.plex.PlexMovieDetail
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.data.settings.AppSettings
import com.moviesshumtimes.tv.data.settings.ChatOverlayCorner
import com.moviesshumtimes.tv.data.settings.appSettingsStore
import com.moviesshumtimes.tv.playback.ExoPlayerAdapter
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
import com.moviesshumtimes.tv.ui.kit.Icon
import com.moviesshumtimes.tv.ui.kit.ShumIconButton
import com.moviesshumtimes.tv.ui.kit.ShumListItem
import com.moviesshumtimes.tv.ui.kit.ShumRadioButton
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppScrim
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val REPORT_INTERVAL_MS = 5_000L
private const val CONTROLS_HIDE_DELAY_MS = 3_000L
private const val PROGRESS_POLL_INTERVAL_MS = 200L
private const val SKIP_INCREMENT_MS = 10_000L

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
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<AppSettings?>(null) }

    LaunchedEffect(Unit) {
        settings = context.appSettingsStore.observe().first()
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

    // Only a mode change — direct play <-> a burn-required transcode, a
    // different burn-required stream, or (while transcoding) a different
    // bitrate — needs a whole new ExoPlayer/transcode session; there's no
    // live "swap the subtitle" or "reopen the HLS session at a new bitrate"
    // API on an existing session. Switching between two directly-played text
    // tracks (or off) doesn't touch this key, so PlayerSession patches the
    // running player in place instead of restarting it — see its own
    // LaunchedEffect(decision). Bitrate is likewise irrelevant to direct
    // play (it only ever feeds buildTranscodeUrl), so it's left out of that
    // branch's identity. key() tears down and rebuilds only when
    // playerIdentity itself changes, picking up wherever playback left off
    // via restartPositionMs (captured from the outgoing player right before
    // the switch, in onSelectSubtitle/onSelectBitrate below).
    val playerIdentity = when (decision) {
        is PlaybackDecision.Transcode -> decision to currentSettings.maxVideoBitrateKbps
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
            showChatOverlay = currentSettings.showChatOverlay,
            chatOverlayCorner = currentSettings.chatOverlayCorner,
            subtitleOptions = subtitleChoices,
            selectedSubtitleStreamId = subtitleStreamId,
            onPlayerCreated = { activePlayer = it },
            onSelectSubtitle = { option ->
                activePlayer?.let { restartPositionMs = it.currentPosition }
                subtitleStreamId = option.streamId
            },
            onSelectBitrate = { kbps ->
                activePlayer?.let { restartPositionMs = it.currentPosition }
                val updated = currentSettings.copy(maxVideoBitrateKbps = kbps)
                settings = updated
                scope.launch { context.appSettingsStore.save(updated) }
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
    showChatOverlay: Boolean,
    chatOverlayCorner: ChatOverlayCorner,
    subtitleOptions: List<SubtitleOption>,
    selectedSubtitleStreamId: Long?,
    onPlayerCreated: (ExoPlayer) -> Unit,
    onSelectSubtitle: (SubtitleOption) -> Unit,
    onSelectBitrate: (Int) -> Unit,
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

    val sync = remember(player) { SyncViewModel(ExoPlayerAdapter(player), relay, scope) }
    val connectionState by sync.connectionState.collectAsState()
    val phase by sync.phase.collectAsState()
    val waitingOn by sync.waitingOn.collectAsState()

    // Everything below owns the on-screen chrome entirely in Compose — no
    // Media3 PlayerControlView involved (useController=false on the
    // PlayerView further down). PlayerControlView's own key-swallowing,
    // auto-show/hide timing, and per-button enabled-state logic were fighting
    // this screen's own state at every turn (a D-pad press while its
    // controller wasn't yet "fully visible" got silently eaten, and its
    // subtitle button re-grayed itself any time ExoPlayer reported zero
    // native text tracks — exactly the burn-required/transcoded cases this
    // app's own subtitle picker exists for). Owning the whole surface here
    // means there's only ever one system deciding what a keypress does.
    var controlsVisible by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var subtitleMenuOpen by remember { mutableStateOf(false) }
    var bitrateMenuOpen by remember { mutableStateOf(false) }
    // Greets the user with focus already on Play/Pause, same as every show()
    // afterward — see the onPreviewKeyEvent handler below for the reveal
    // paths that re-arm this.
    var pendingPlayPauseFocus by remember { mutableStateOf(true) }
    // Bumped on every interaction while controls are visible so the auto-hide
    // effect below restarts its delay — without this, holding a seek or
    // browsing the transport row for longer than the hide delay would still
    // yank the chrome away mid-interaction.
    var interactionTick by remember { mutableStateOf(0) }
    var seekBar by remember { mutableStateOf<DefaultTimeBar?>(null) }
    val screenFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceIn(0, player.duration.coerceAtLeast(0))
        player.seekTo(target)
    }

    LaunchedEffect(pendingPlayPauseFocus) {
        if (pendingPlayPauseFocus) {
            runCatching { playPauseFocusRequester.requestFocus() }
            pendingPlayPauseFocus = false
        }
    }

    // Focus has nowhere to live once the controls row leaves composition —
    // hand it back to the screen root so the hidden-state branch of
    // onPreviewKeyEvent below keeps receiving D-pad events.
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) screenFocusRequester.requestFocus()
    }

    // Media3's own auto-hide only reschedules on an isPlaying state *change*;
    // driving it here off both playback state and every interaction avoids
    // relying on that internal scheduling and fixes the "hides itself mid
    // scrub" case interactionTick exists for.
    LaunchedEffect(controlsVisible, subtitleMenuOpen, bitrateMenuOpen, isPlaying, interactionTick) {
        if (controlsVisible && !subtitleMenuOpen && !bitrateMenuOpen) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    // Drives the seek bar's position/buffered/duration while it's on screen;
    // nothing else calls setPosition on it now that there's no
    // PlayerControlView update loop underneath.
    LaunchedEffect(controlsVisible, player) {
        if (!controlsVisible) return@LaunchedEffect
        while (true) {
            seekBar?.setDuration(player.duration.coerceAtLeast(0))
            seekBar?.setPosition(player.currentPosition.coerceAtLeast(0))
            seekBar?.setBufferedPosition(player.bufferedPosition.coerceAtLeast(0))
            delay(PROGRESS_POLL_INTERVAL_MS)
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

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
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
            .background(AppScrim)
            .focusRequester(screenFocusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (subtitleMenuOpen || bitrateMenuOpen || keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isFreshKeyDown = keyEvent.nativeKeyEvent.repeatCount == 0
                if (!controlsVisible) {
                    when (keyEvent.key) {
                        // Netflix/YouTube-style: seeking while the chrome is
                        // hidden doesn't require revealing it first — the
                        // chrome is a navigation aid, not a gate in front of
                        // the single most common remote gesture.
                        Key.DirectionLeft, Key.DirectionRight -> {
                            val direction = if (keyEvent.key == Key.DirectionRight) 1 else -1
                            seekBy(direction * seekIncrementForHold(keyEvent.nativeKeyEvent.repeatCount))
                            return@onPreviewKeyEvent true
                        }
                        // Select is the deliberate "reveal and act" gesture —
                        // toggling playback in the same press it reveals the
                        // chrome matches what a remote's OK button does on
                        // every other TV player.
                        Key.DirectionCenter, Key.Enter -> {
                            if (isFreshKeyDown) {
                                togglePlayPause()
                                controlsVisible = true
                                pendingPlayPauseFocus = true
                                return@onPreviewKeyEvent true
                            }
                        }
                        // Up/Down don't have an obvious hidden-state meaning
                        // of their own — just reveal and focus, one press.
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (isFreshKeyDown) {
                                controlsVisible = true
                                pendingPlayPauseFocus = true
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    return@onPreviewKeyEvent false
                }
                interactionTick++
                false
            }
            .focusable(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(context).apply {
                    useController = false
                    this.player = player
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
        )
        if (isBuffering) {
            AppLoadingIndicator(modifier = Modifier.align(Alignment.Center))
        }
        if (controlsVisible) {
            Text(
                text = detail.title,
                color = AppWhite,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .background(AppScrim.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (connectionState != ConnectionState.CONNECTED && controlsVisible) {
            Text(
                text = when (connectionState) {
                    ConnectionState.CONNECTING -> "Sync: connecting…"
                    ConnectionState.RECONNECTING -> "Sync: reconnecting…"
                    ConnectionState.ROOM_FULL -> "Sync: room full"
                    else -> "Sync: off"
                },
                color = AppWhite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(AppScrim.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (phase == PlaybackPhase.WAITING_FOR_PEERS && waitingOn.isNotEmpty()) {
            Text(
                text = "Waiting for the room to catch up…",
                color = AppWhite,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(24.dp)
                    .background(AppScrim.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (showChatOverlay) {
            val chatAlignment = when (chatOverlayCorner) {
                ChatOverlayCorner.TOP_START -> Alignment.TopStart
                ChatOverlayCorner.TOP_END -> Alignment.TopEnd
                ChatOverlayCorner.BOTTOM_START -> Alignment.BottomStart
                ChatOverlayCorner.BOTTOM_END -> Alignment.BottomEnd
            }
            ChatOverlay(
                messages = sync.chatMessages,
                corner = chatOverlayCorner,
                modifier = Modifier.align(chatAlignment).padding(24.dp),
            )
        }
        if (controlsVisible) {
            PlayerControlsBar(
                isPlaying = isPlaying,
                subtitlesAvailable = subtitleOptions.isNotEmpty(),
                playPauseFocusRequester = playPauseFocusRequester,
                onPlayPause = ::togglePlayPause,
                onRewind = { seekBy(-SKIP_INCREMENT_MS) },
                onForward = { seekBy(SKIP_INCREMENT_MS) },
                onOpenSubtitles = {
                    controlsVisible = false
                    subtitleMenuOpen = true
                },
                onOpenBitrate = {
                    controlsVisible = false
                    bitrateMenuOpen = true
                },
                onSeekBar = { seekBar = it },
                onSeekKey = { keyCode, repeatCount ->
                    val direction = when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                        KeyEvent.KEYCODE_DPAD_LEFT -> -1
                        else -> return@PlayerControlsBar false
                    }
                    seekBy(direction * seekIncrementForHold(repeatCount))
                    true
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (subtitleMenuOpen) {
            SubtitleMenu(
                options = subtitleOptions,
                selectedStreamId = selectedSubtitleStreamId,
                onSelect = { option ->
                    subtitleMenuOpen = false
                    onSelectSubtitle(option)
                    screenFocusRequester.requestFocus()
                },
                onDismiss = {
                    subtitleMenuOpen = false
                    screenFocusRequester.requestFocus()
                },
                modifier = Modifier.align(Alignment.CenterEnd).padding(24.dp),
            )
        }
        if (bitrateMenuOpen) {
            BitrateMenu(
                selectedKbps = maxVideoBitrateKbps,
                onSelect = { kbps ->
                    bitrateMenuOpen = false
                    onSelectBitrate(kbps)
                    screenFocusRequester.requestFocus()
                },
                onDismiss = {
                    bitrateMenuOpen = false
                    screenFocusRequester.requestFocus()
                },
                modifier = Modifier.align(Alignment.CenterEnd).padding(24.dp),
            )
        }
    }
}

@Composable
private fun PlayerControlsBar(
    isPlaying: Boolean,
    subtitlesAvailable: Boolean,
    playPauseFocusRequester: FocusRequester,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenBitrate: () -> Unit,
    onSeekBar: (DefaultTimeBar) -> Unit,
    onSeekKey: (keyCode: Int, repeatCount: Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppScrim.copy(alpha = 0.6f))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                DefaultTimeBar(ctx).apply {
                    // DefaultTimeBar has no gradient-stroke concept like the
                    // Compose two-tone borders elsewhere, so the two-tone
                    // treatment here is the played fill in the deeper
                    // NeonPurple with the scrubber (the focal, "active"
                    // point) in the lighter NeonPurpleGlow — same pairing,
                    // closest equivalent this component supports.
                    setPlayedColor(NeonPurple.toArgb())
                    setScrubberColor(NeonPurpleGlow.toArgb())
                    setBufferedColor(AppWhite.copy(alpha = 0.4f).toArgb())
                    setUnplayedColor(AppWhite.copy(alpha = 0.25f).toArgb())
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                        onSeekKey(keyCode, event.repeatCount)
                    }
                    onSeekBar(this)
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShumIconButton(onClick = onRewind) {
                Icon(Icons.Default.Replay10, contentDescription = "Rewind 10 seconds", tint = AppWhite)
            }
            ShumIconButton(
                onClick = onPlayPause,
                modifier = Modifier.focusRequester(playPauseFocusRequester),
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = AppWhite,
                )
            }
            ShumIconButton(onClick = onForward) {
                Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds", tint = AppWhite)
            }
            ShumIconButton(onClick = onOpenSubtitles, enabled = subtitlesAvailable) {
                Icon(
                    Icons.Default.ClosedCaption,
                    contentDescription = "Subtitles",
                    tint = AppWhite.copy(alpha = if (subtitlesAvailable) 1f else 0.5f),
                )
            }
            ShumIconButton(onClick = onOpenBitrate) {
                Icon(Icons.Default.HighQuality, contentDescription = "Quality", tint = AppWhite)
            }
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
            .background(AppScrim.copy(alpha = 0.85f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = "Subtitles", color = AppWhite, style = ShumTypography.titleMedium)
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
                ShumListItem(
                    selected = selected,
                    onClick = { onSelect(option) },
                    headlineContent = { Text(option.label) },
                    leadingContent = { ShumRadioButton(selected = selected) },
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

@Composable
private fun BitrateMenu(
    selectedKbps: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    val presets = AppSettings.BITRATE_PRESETS
    val focusRequesters = remember { presets.map { FocusRequester() } }
    LaunchedEffect(Unit) {
        val selectedIndex = presets.indexOfFirst { it.kbps == selectedKbps }.coerceAtLeast(0)
        runCatching { focusRequesters.getOrNull(selectedIndex)?.requestFocus() }
    }

    // Fixed, short preset list — unlike SubtitleMenu's language tracks, this
    // never grows past screen height, so a plain Column (no LazyColumn/height
    // cap) is enough. Wider than SubtitleMenu since the friendly quality
    // labels ("20 Mbps (High quality)") run longer than a language name.
    Column(
        modifier = modifier
            .width(420.dp)
            .background(AppScrim.copy(alpha = 0.85f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = "Quality", color = AppWhite, style = ShumTypography.titleMedium)
        presets.forEachIndexed { index, preset ->
            val selected = preset.kbps == selectedKbps
            ShumListItem(
                selected = selected,
                onClick = { onSelect(preset.kbps) },
                headlineContent = { Text(preset.label) },
                leadingContent = { ShumRadioButton(selected = selected) },
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
