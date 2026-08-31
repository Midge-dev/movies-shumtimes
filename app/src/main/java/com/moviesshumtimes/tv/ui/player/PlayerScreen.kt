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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val REPORT_INTERVAL_MS = 5_000L
private const val CONTROLS_HIDE_DELAY_MS = 3_000L
private const val PROGRESS_POLL_INTERVAL_MS = 200L
private const val SKIP_INCREMENT_MS = 10_000L

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

    var subtitleStreamId by remember(detail) { mutableStateOf(defaultSubtitleStreamId(detail)) }
    var restartPositionMs by remember(detail) { mutableStateOf(detail.viewOffset ?: 0L) }
    var activePlayer by remember(detail) { mutableStateOf<ExoPlayer?>(null) }

    val decision = remember(detail, subtitleStreamId, currentSettings.forceBurnSubtitles) {
        decidePlayback(detail, subtitleStreamId, currentSettings.forceBurnSubtitles)
    }

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

    var controlsVisible by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var subtitleMenuOpen by remember { mutableStateOf(false) }
    var bitrateMenuOpen by remember { mutableStateOf(false) }
    var pendingPlayPauseFocus by remember { mutableStateOf(true) }
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

    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) screenFocusRequester.requestFocus()
    }

    LaunchedEffect(controlsVisible, subtitleMenuOpen, bitrateMenuOpen, isPlaying, interactionTick) {
        if (controlsVisible && !subtitleMenuOpen && !bitrateMenuOpen) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

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
        scope.launch {
            withContext(NonCancellable) {
                reporter.report(detail.ratingKey, "stopped", position, duration)
            }
        }
        onExit()
    }

    DisposableEffect(player, sync) {
        sync.start()
        onDispose {
            sync.stop()
            player.release()
        }
    }

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
                        Key.DirectionLeft, Key.DirectionRight -> {
                            val direction = if (keyEvent.key == Key.DirectionRight) 1 else -1
                            seekBy(direction * seekIncrementForHold(keyEvent.nativeKeyEvent.repeatCount))
                            return@onPreviewKeyEvent true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            if (isFreshKeyDown) {
                                togglePlayPause()
                                controlsVisible = true
                                pendingPlayPauseFocus = true
                                return@onPreviewKeyEvent true
                            }
                        }
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
                            up = if (index > 0) focusRequesters[index - 1] else FocusRequester.Cancel
                            down = if (index < focusRequesters.lastIndex) focusRequesters[index + 1] else FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
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
                        up = if (index > 0) focusRequesters[index - 1] else FocusRequester.Cancel
                        down = if (index < focusRequesters.lastIndex) focusRequesters[index + 1] else FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
            )
        }
    }
}
