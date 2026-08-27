package com.moviesshumtimes.tv.ui.settings

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviesshumtimes.tv.data.pairing.PairingServer
import com.moviesshumtimes.tv.data.plex.PlexResource
import com.moviesshumtimes.tv.data.plex.PlexResourcesApi
import com.moviesshumtimes.tv.data.settings.AppSettings
import com.moviesshumtimes.tv.data.settings.ChatOverlayCorner
import com.moviesshumtimes.tv.data.settings.appSettingsStore
import com.moviesshumtimes.tv.ui.common.ClickToTypeTextField
import com.moviesshumtimes.tv.ui.common.NeonScrollbar
import com.moviesshumtimes.tv.ui.common.QrCodeImage
import com.moviesshumtimes.tv.ui.kit.FocusableSurface
import com.moviesshumtimes.tv.ui.kit.ShumBorder
import com.moviesshumtimes.tv.ui.kit.ShumButton
import com.moviesshumtimes.tv.ui.kit.ShumColors
import com.moviesshumtimes.tv.ui.kit.ShumFilterChip
import com.moviesshumtimes.tv.ui.kit.ShumGlow
import com.moviesshumtimes.tv.ui.kit.ShumListItem
import com.moviesshumtimes.tv.ui.kit.ShumOutlinedButton
import com.moviesshumtimes.tv.ui.kit.ShumRadioButton
import com.moviesshumtimes.tv.ui.kit.ShumSwitch
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppBackground
import com.moviesshumtimes.tv.ui.theme.AppDimBorder
import com.moviesshumtimes.tv.ui.theme.AppOnSurface
import com.moviesshumtimes.tv.ui.theme.AppOnSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGradient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    accountToken: String,
    clientIdentifier: String,
    hint: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AppSettings()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings = context.appSettingsStore.observe().first()
        loaded = true
    }

    // Which Plex servers this account can see — fetched separately from the
    // local DataStore settings above since it's a network call that can
    // fail independently (e.g. relay/server unreachable shouldn't block
    // editing the other settings).
    var sources by remember { mutableStateOf<List<PlexResource>>(emptyList()) }
    var sourcesLoaded by remember { mutableStateOf(false) }
    var sourcesError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { PlexResourcesApi(clientIdentifier).listServers(accountToken) }
            .onSuccess { sources = it }
            .onFailure { sourcesError = it.message ?: "Couldn't load sources" }
        sourcesLoaded = true
    }

    BackHandler(onBack = onBack)

    // "Pair from phone" — a phone on the same Wi-Fi can paste the relay URL
    // via a tiny local web page served straight from the TV, instead of
    // typing a long wss://...?token=... string on a remote.
    var pairingServer by remember { mutableStateOf<PairingServer?>(null) }
    var pairingUrl by remember { mutableStateOf<String?>(null) }
    var pairingError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { pairingServer?.stop() }
    }

    if (!loaded) {
        Text("Loading settings…")
        return
    }

    // BasicTextField doesn't hand D-pad DOWN/UP off to neighboring
    // focusables on its own (it treats them as text-cursor movement first),
    // so the row below it would otherwise be unreachable by remote. These
    // FocusRequesters make the down/up route explicit between the fields.
    val sourceFocuses = remember(sources) { sources.map { FocusRequester() } }
    val relayUrlFocus = remember { FocusRequester() }
    val pairButtonFocus = remember { FocusRequester() }
    val cancelPairingFocus = remember { FocusRequester() }
    val bitrateRowFocus = remember { FocusRequester() }
    val forceBurnFocus = remember { FocusRequester() }
    val showChatFocus = remember { FocusRequester() }
    val cornerTopStartFocus = remember { FocusRequester() }
    val cornerTopEndFocus = remember { FocusRequester() }
    val cornerBottomStartFocus = remember { FocusRequester() }
    val cornerBottomEndFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    // See LibraryScreen's matching comment — AppNavigationDrawer's sidebar
    // is the first focusable thing in the composition, so this screen needs
    // its own explicit request too. Waits for sourcesLoaded since the
    // source rows (the preferred target) don't exist until that async
    // fetch finishes either way — falls back to the always-present relay
    // URL field if there are none.
    LaunchedEffect(sourcesLoaded, sourceFocuses) {
        if (!sourcesLoaded) return@LaunchedEffect
        runCatching { (sourceFocuses.firstOrNull() ?: relayUrlFocus).requestFocus() }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Text("Settings", style = ShumTypography.displaySmall)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Available sources")
                when {
                    !sourcesLoaded -> Text("Loading sources…")
                    sourcesError != null -> Text("Couldn't load sources: $sourcesError")
                    sources.isEmpty() -> Text("No sources found")
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // ListItem handles its own focused/selected contrast,
                        // unlike the old Button+RadioButton combo which needed
                        // manually tracking focus to swap the radio's colors
                        // so it didn't blend into the button's focus fill.
                        sources.forEachIndexed { index, source ->
                            val selected = settings.selectedServerId == source.machineIdentifier
                            ShumListItem(
                                selected = selected,
                                onClick = { settings = settings.copy(selectedServerId = source.machineIdentifier) },
                                headlineContent = { Text("${source.name}${if (source.owned) " (owned)" else ""}") },
                                leadingContent = { ShumRadioButton(selected = selected) },
                                modifier = Modifier
                                    .focusRequester(sourceFocuses[index])
                                    .focusProperties {
                                        up = if (index > 0) sourceFocuses[index - 1] else FocusRequester.Default
                                        down = if (index < sourceFocuses.lastIndex) sourceFocuses[index + 1] else relayUrlFocus
                                    },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hint != null) {
                    Text(hint, color = NeonPurple)
                }
                Text("Relay URL (watch-together server)")
                ClickToTypeTextField(
                    value = settings.relayUrl ?: "",
                    onValueChange = { settings = settings.copy(relayUrl = it) },
                    textStyle = TextStyle(color = AppOnSurface),
                    modifier = Modifier
                        .background(AppSurfaceVariant)
                        .padding(12.dp)
                        .width(500.dp)
                        .focusRequester(relayUrlFocus)
                        .focusProperties {
                            up = if (sourceFocuses.isNotEmpty()) sourceFocuses.last() else FocusRequester.Default
                            down = pairButtonFocus
                        },
                )

                ShumButton(
                    onClick = {
                        pairingError = null
                        val server = PairingServer(
                            onSubmitted = { value ->
                                Handler(Looper.getMainLooper()).post {
                                    settings = settings.copy(relayUrl = value)
                                    pairingServer?.stop()
                                    pairingServer = null
                                    pairingUrl = null
                                }
                            },
                        )
                        val url = server.start()
                        if (url != null) {
                            pairingServer = server
                            pairingUrl = url
                        } else {
                            pairingError = "Couldn't find a Wi-Fi address — is the TV connected to a network?"
                        }
                    },
                    modifier = Modifier
                        .focusRequester(pairButtonFocus)
                        .focusProperties {
                            up = relayUrlFocus
                            down = if (pairingUrl != null) cancelPairingFocus else bitrateRowFocus
                        },
                ) {
                    Text("Pair from phone")
                }

                if (pairingError != null) {
                    Text(pairingError!!)
                }

                if (pairingUrl != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier
                            .background(AppSurfaceVariant)
                            .padding(24.dp),
                    ) {
                        QrCodeImage(
                            content = pairingUrl!!,
                            modifier = Modifier
                                .size(160.dp)
                                .background(AppWhite)
                                .padding(12.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Scan with your phone (same Wi-Fi as the TV), or visit:")
                            Text(pairingUrl!!, style = ShumTypography.bodyLarge)
                            Text("Paste the relay URL there and it'll appear here automatically.")
                            ShumOutlinedButton(
                                onClick = {
                                    pairingServer?.stop()
                                    pairingServer = null
                                    pairingUrl = null
                                },
                                modifier = Modifier
                                    .focusRequester(cancelPairingFocus)
                                    .focusProperties {
                                        up = pairButtonFocus
                                        down = bitrateRowFocus
                                    },
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Max transcode video bitrate")
                // Scrollable, not just wrapped in spacedBy — a plain Row
                // measures each child within the row's own bounded width, so
                // once the earlier chips ate up most of it the last preset's
                // text was getting squeezed into a sliver and wrapping
                // character-by-character instead of overflowing. Scrolling
                // content isn't width-bounded, so every chip keeps its
                // natural size; Compose brings a focused child that's
                // scrolled out of view back on screen automatically.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    AppSettings.BITRATE_PRESETS.forEachIndexed { index, preset ->
                        val bitrateSelected = settings.maxVideoBitrateKbps == preset.kbps
                        ShumFilterChip(
                            selected = bitrateSelected,
                            onClick = { settings = settings.copy(maxVideoBitrateKbps = preset.kbps) },
                            modifier = Modifier
                                .let { if (index == 0) it.focusRequester(bitrateRowFocus) else it }
                                .focusProperties {
                                    up = if (pairingUrl != null) cancelPairingFocus else pairButtonFocus
                                    down = forceBurnFocus
                                },
                        ) {
                            if (bitrateSelected) {
                                Text("✓", modifier = Modifier.padding(end = 8.dp))
                            }
                            Text(preset.label)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Force-burn subtitles into video even when not required")
                ShumSwitch(
                    checked = settings.forceBurnSubtitles,
                    onCheckedChange = { settings = settings.copy(forceBurnSubtitles = it) },
                    modifier = Modifier
                        .focusRequester(forceBurnFocus)
                        .focusProperties {
                            up = bitrateRowFocus
                            down = showChatFocus
                        },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Show watch-together chat messages on screen during playback")
                ShumSwitch(
                    checked = settings.showChatOverlay,
                    onCheckedChange = { settings = settings.copy(showChatOverlay = it) },
                    modifier = Modifier
                        .focusRequester(showChatFocus)
                        .focusProperties {
                            up = forceBurnFocus
                            down = cornerTopStartFocus
                        },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Chat position")
                Text("Pick the corner messages appear in", color = AppOnSurfaceVariant)
                ChatCornerPicker(
                    selected = settings.chatOverlayCorner,
                    onSelect = { settings = settings.copy(chatOverlayCorner = it) },
                    topStartFocus = cornerTopStartFocus,
                    topEndFocus = cornerTopEndFocus,
                    bottomStartFocus = cornerBottomStartFocus,
                    bottomEndFocus = cornerBottomEndFocus,
                    aboveFocus = showChatFocus,
                    belowFocus = saveFocus,
                )
            }

            ShumButton(
                onClick = {
                    scope.launch {
                        context.appSettingsStore.save(settings.copy(relayUrl = settings.relayUrl?.trim()?.ifBlank { null }))
                        onSaved()
                    }
                },
                modifier = Modifier
                    .focusRequester(saveFocus)
                    .focusProperties { up = cornerBottomStartFocus },
            ) {
                Text("Save")
            }
        }

        NeonScrollbar(scrollState = scrollState, modifier = Modifier.padding(vertical = 48.dp, horizontal = 12.dp))
    }
}

// Design spec section 07: four focusable 2×2 tiles, each a miniature of the
// player screen with the message stack drawn in its corner — cheaper to read
// from the couch than a text list, and one D-pad press per option.
@Composable
private fun ChatCornerPicker(
    selected: ChatOverlayCorner,
    onSelect: (ChatOverlayCorner) -> Unit,
    topStartFocus: FocusRequester,
    topEndFocus: FocusRequester,
    bottomStartFocus: FocusRequester,
    bottomEndFocus: FocusRequester,
    aboveFocus: FocusRequester,
    belowFocus: FocusRequester,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChatCornerTile(
                corner = ChatOverlayCorner.TOP_START,
                label = "Top left",
                selected = selected == ChatOverlayCorner.TOP_START,
                onClick = { onSelect(ChatOverlayCorner.TOP_START) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(topStartFocus)
                    .focusProperties {
                        up = aboveFocus
                        down = bottomStartFocus
                        right = topEndFocus
                    },
            )
            ChatCornerTile(
                corner = ChatOverlayCorner.TOP_END,
                label = "Top right",
                selected = selected == ChatOverlayCorner.TOP_END,
                onClick = { onSelect(ChatOverlayCorner.TOP_END) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(topEndFocus)
                    .focusProperties {
                        up = aboveFocus
                        down = bottomEndFocus
                        left = topStartFocus
                    },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChatCornerTile(
                corner = ChatOverlayCorner.BOTTOM_START,
                label = "Bottom left",
                selected = selected == ChatOverlayCorner.BOTTOM_START,
                onClick = { onSelect(ChatOverlayCorner.BOTTOM_START) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(bottomStartFocus)
                    .focusProperties {
                        up = topStartFocus
                        down = belowFocus
                        right = bottomEndFocus
                    },
            )
            ChatCornerTile(
                corner = ChatOverlayCorner.BOTTOM_END,
                label = "Bottom right",
                selected = selected == ChatOverlayCorner.BOTTOM_END,
                onClick = { onSelect(ChatOverlayCorner.BOTTOM_END) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(bottomEndFocus)
                    .focusProperties {
                        up = topEndFocus
                        down = belowFocus
                        left = bottomStartFocus
                    },
            )
        }
    }
}

private val ChatCornerTileShape = RoundedCornerShape(8.dp)
private val chatCornerTileColors = ShumColors(container = AppBackground, content = AppOnSurfaceVariant)
private val chatCornerTileBorder = ShumBorder(
    idle = BorderStroke(2.dp, AppDimBorder),
    focused = BorderStroke(2.dp, NeonPurpleGradient),
)
private val chatCornerTileGlow = ShumGlow(focusedColor = NeonPurpleGlow)
private val cornerLabelStyle = TextStyle(fontSize = 11.sp, lineHeight = 13.sp)

@Composable
private fun ChatCornerTile(
    corner: ChatOverlayCorner,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Newest message renders nearest the tile's marked corner in every case:
    // top corners stack a bright (newest) bar above a dim (older) one, bottom
    // corners put the bright bar last so it lands physically at the bottom.
    val stacksDownward = corner == ChatOverlayCorner.TOP_START || corner == ChatOverlayCorner.TOP_END
    val stackAlignment = when (corner) {
        ChatOverlayCorner.TOP_START -> Alignment.TopStart
        ChatOverlayCorner.TOP_END -> Alignment.TopEnd
        ChatOverlayCorner.BOTTOM_START -> Alignment.BottomStart
        ChatOverlayCorner.BOTTOM_END -> Alignment.BottomEnd
    }
    val labelAlignment = when (corner) {
        ChatOverlayCorner.TOP_START -> Alignment.BottomEnd
        ChatOverlayCorner.TOP_END -> Alignment.BottomStart
        ChatOverlayCorner.BOTTOM_START -> Alignment.TopEnd
        ChatOverlayCorner.BOTTOM_END -> Alignment.TopStart
    }
    val brightBar = if (selected) AppOnSurface else AppOnSurfaceVariant
    val dimBar = brightBar.copy(alpha = if (selected) 0.5f else 0.45f)
    val labelColor = if (selected) NeonPurpleGlow else AppOnSurfaceVariant

    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(64.dp),
        selected = selected,
        shape = ChatCornerTileShape,
        colors = chatCornerTileColors,
        border = chatCornerTileBorder,
        glow = chatCornerTileGlow,
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            Column(
                modifier = Modifier.align(stackAlignment),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val bars = listOf(
                    Modifier.width(44.dp).height(8.dp).background(brightBar, RoundedCornerShape(2.dp)),
                    Modifier.width(30.dp).height(8.dp).background(dimBar, RoundedCornerShape(2.dp)),
                )
                val ordered = if (stacksDownward) bars else bars.asReversed()
                for (barModifier in ordered) {
                    Box(barModifier)
                }
            }
            Text(
                text = if (selected) "$label ✓" else label,
                color = labelColor,
                style = cornerLabelStyle,
                modifier = Modifier.align(labelAlignment),
            )
        }
    }
}
