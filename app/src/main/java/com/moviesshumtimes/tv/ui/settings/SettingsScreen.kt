package com.moviesshumtimes.tv.ui.settings

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviesshumtimes.tv.data.pairing.PairingServer
import com.moviesshumtimes.tv.data.plex.PlexResource
import com.moviesshumtimes.tv.data.plex.PlexResourcesApi
import com.moviesshumtimes.tv.data.settings.AppSettings
import com.moviesshumtimes.tv.data.settings.ChatOverlayCorner
import com.moviesshumtimes.tv.data.settings.RelayEntry
import com.moviesshumtimes.tv.data.settings.appSettingsStore
import com.moviesshumtimes.tv.sync.RelayDirectoryApi
import com.moviesshumtimes.tv.ui.common.ClickToTypeTextField
import com.moviesshumtimes.tv.ui.common.LoadingScreen
import com.moviesshumtimes.tv.ui.common.NeonScrollbar
import com.moviesshumtimes.tv.ui.common.QrCodeImage
import com.moviesshumtimes.tv.ui.common.RelayStatus
import com.moviesshumtimes.tv.ui.common.RelayStatusDot
import com.moviesshumtimes.tv.ui.common.RelayStatusLine
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
import com.moviesshumtimes.tv.ui.theme.AppScrim
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGradient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

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

    var showingRelaySettings by remember { mutableStateOf(false) }
    val relaySettingsEntryFocus = remember { FocusRequester() }
    BackHandler(enabled = showingRelaySettings) { showingRelaySettings = false }

    var maxSeatsMenuExpanded by remember { mutableStateOf(false) }
    val maxSeatsOptionFocuses = remember {
        AppSettings.MAX_HOST_SEATS_OPTIONS.associateWith { FocusRequester() }
    }
    BackHandler(enabled = maxSeatsMenuExpanded) { maxSeatsMenuExpanded = false }

    var pairingServer by remember { mutableStateOf<PairingServer?>(null) }
    var pairingUrl by remember { mutableStateOf<String?>(null) }
    var pairingError by remember { mutableStateOf<String?>(null) }
    var editingRelayId by remember { mutableStateOf<String?>(null) }
    var testingRelayName by remember { mutableStateOf<String?>(null) }
    var testStatus by remember { mutableStateOf<RelayStatus>(RelayStatus.Silent) }

    val relayDirectoryApi = remember { RelayDirectoryApi() }
    var relayStatuses by remember { mutableStateOf<Map<String, Pair<Boolean, Int>?>>(emptyMap()) }

    DisposableEffect(Unit) {
        onDispose { pairingServer?.stop() }
    }

    if (!loaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingScreen("Loading settings…")
        }
        return
    }

    LaunchedEffect(settings.relays.map { it.id }) {
        relayStatuses = settings.relays.associate { it.id to null }
        for (entry in settings.relays) {
            val reachable = relayDirectoryApi.testReachable(entry.url)
            val count = if (reachable) relayDirectoryApi.listRooms(entry.url).size else 0
            relayStatuses = relayStatuses + (entry.id to (reachable to count))
        }
    }

    val sourceFocuses = remember(sources) { sources.map { FocusRequester() } }
    val relayRowFocuses = remember { mutableStateMapOf<String, RelayRowFocus>() }
    fun relayRowFocus(entry: RelayEntry): RelayRowFocus = relayRowFocuses.getOrPut(entry.id) { RelayRowFocus() }
    val firstRelayRowFocus = settings.relays.firstOrNull()?.let { relayRowFocus(it).leftmost(it) }
    val lastRelayRowFocus = settings.relays.lastOrNull()?.let { relayRowFocus(it).remove }
    val addRelayFocus = remember { FocusRequester() }
    val cancelPairingFocus = remember { FocusRequester() }
    val maxHostSeatsFocus = remember { FocusRequester() }
    LaunchedEffect(maxSeatsMenuExpanded) {
        if (!maxSeatsMenuExpanded) runCatching { maxHostSeatsFocus.requestFocus() }
    }
    val bitrateRowFocus = remember { FocusRequester() }
    val forceBurnFocus = remember { FocusRequester() }
    val showChatFocus = remember { FocusRequester() }
    val cornerTopStartFocus = remember { FocusRequester() }
    val cornerTopEndFocus = remember { FocusRequester() }
    val cornerBottomStartFocus = remember { FocusRequester() }
    val cornerBottomEndFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    LaunchedEffect(sourcesLoaded, sourceFocuses) {
        if (!sourcesLoaded) return@LaunchedEffect
        val target = sourceFocuses.firstOrNull() ?: relaySettingsEntryFocus
        runCatching { target.requestFocus() }
    }

    LaunchedEffect(showingRelaySettings) {
        val target = if (showingRelaySettings) firstRelayRowFocus ?: addRelayFocus else relaySettingsEntryFocus
        repeat(5) {
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos {}
        }
    }

    fun persistRelays(newRelays: List<RelayEntry>) {
        settings = settings.copy(relays = newRelays)
        scope.launch {
            val persisted = context.appSettingsStore.observe().first()
            context.appSettingsStore.save(persisted.copy(relays = newRelays))
        }
    }

    fun startPairing(editingId: String?, prefillNickname: String, prefillUrl: String) {
        pairingError = null
        editingRelayId = editingId
        val server = PairingServer(
            prefillNickname = prefillNickname,
            prefillUrl = prefillUrl,
            onSubmitted = { nickname, url ->
                Handler(Looper.getMainLooper()).post {
                    pairingServer?.stop()
                    pairingServer = null
                    pairingUrl = null
                    editingRelayId = null
                    runCatching { addRelayFocus.requestFocus() }
                    scope.launch {
                        testingRelayName = nickname
                        testStatus = RelayStatus.Silent
                        val silentJob = launch {
                            delay(2_000)
                            if (testStatus == RelayStatus.Silent) testStatus = RelayStatus.Waking
                        }
                        val reachable = relayDirectoryApi.testReachableTolerant(url)
                        silentJob.cancel()
                        val updatedRelays = if (editingId != null) {
                            settings.relays.map { if (it.id == editingId) it.copy(nickname = nickname, url = url) else it }
                        } else {
                            settings.relays + RelayEntry(
                                id = UUID.randomUUID().toString(),
                                nickname = nickname,
                                url = url,
                                isDefault = settings.relays.isEmpty(),
                            )
                        }
                        persistRelays(updatedRelays)
                        val statusId = editingId ?: updatedRelays.last().id
                        val count = if (reachable) relayDirectoryApi.listRooms(url).size else 0
                        relayStatuses = relayStatuses + (statusId to (reachable to count))
                        testingRelayName = null
                    }
                }
            },
        )
        val url = server.start()
        if (url != null) {
            pairingServer = server
            pairingUrl = url
        } else {
            pairingError = "Couldn't find a Wi-Fi address — is the TV connected to a network?"
            editingRelayId = null
        }
    }

    if (showingRelaySettings) {
        RelaySettingsPane(
            settings = settings,
            relayStatuses = relayStatuses,
            editingRelayId = editingRelayId,
            pairingUrl = pairingUrl,
            pairingError = pairingError,
            testingRelayName = testingRelayName,
            testStatus = testStatus,
            relayRowFocus = ::relayRowFocus,
            firstRelayRowFocus = firstRelayRowFocus,
            lastRelayRowFocus = lastRelayRowFocus,
            addRelayFocus = addRelayFocus,
            cancelPairingFocus = cancelPairingFocus,
            onBack = { showingRelaySettings = false },
            onMakeDefault = { entry -> persistRelays(settings.relays.map { it.copy(isDefault = it.id == entry.id) }) },
            onEdit = { entry -> startPairing(entry.id, entry.nickname, entry.url) },
            onRemove = { entry ->
                val remaining = settings.relays.filterNot { it.id == entry.id }
                persistRelays(
                    if (entry.isDefault && remaining.isNotEmpty()) {
                        remaining.mapIndexed { i, r -> r.copy(isDefault = i == 0) }
                    } else {
                        remaining
                    },
                )
            },
            onAddRelay = { startPairing(editingId = null, prefillNickname = "", prefillUrl = "") },
            onCancelPairing = {
                pairingServer?.stop()
                pairingServer = null
                pairingUrl = null
                editingRelayId = null
                runCatching { addRelayFocus.requestFocus() }
            },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(48.dp),
        ) {
            Text("Settings", style = ShumTypography.displaySmall)
            Spacer(Modifier.height(32.dp))

            SettingsGroup(title = "Libraries", showRule = false) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Available sources")
                when {
                    !sourcesLoaded -> Text("Loading sources…")
                    sourcesError != null -> Text("Couldn't load sources: $sourcesError")
                    sources.isEmpty() -> Text("No sources found")
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        down = if (index < sourceFocuses.lastIndex) {
                                            sourceFocuses[index + 1]
                                        } else {
                                            relaySettingsEntryFocus
                                        }
                                    },
                            )
                        }
                    }
                }
            }
            }

            SettingsGroup(title = "Watch Together", showRule = true) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hint != null) {
                    Text(hint, color = NeonPurple)
                }
                val defaultRelay = settings.relays.firstOrNull { it.isDefault }
                FocusableSurface(
                    onClick = { showingRelaySettings = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .focusRequester(relaySettingsEntryFocus)
                        .focusProperties {
                            up = sourceFocuses.lastOrNull() ?: FocusRequester.Default
                            down = maxHostSeatsFocus
                        },
                    shape = RoundedCornerShape(8.dp),
                    colors = ShumColors(container = AppBackground, content = AppOnSurfaceVariant, focusedContent = AppWhite),
                    border = ShumBorder(idle = BorderStroke(2.dp, AppDimBorder), focused = BorderStroke(2.dp, NeonPurpleGradient)),
                    glow = ShumGlow(focusedColor = NeonPurpleGlow),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Relay settings")
                            Text(
                                text = if (defaultRelay != null) "${defaultRelay.nickname} · Default" else "None configured",
                                color = AppOnSurfaceVariant,
                            )
                        }
                        Text("›", color = AppOnSurfaceVariant)
                    }
                }

                FocusableSurface(
                    onClick = { maxSeatsMenuExpanded = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .focusRequester(maxHostSeatsFocus)
                        .focusProperties {
                            up = relaySettingsEntryFocus
                            down = bitrateRowFocus
                        },
                    shape = RoundedCornerShape(8.dp),
                    colors = ShumColors(container = AppBackground, content = AppOnSurfaceVariant, focusedContent = AppWhite),
                    border = ShumBorder(idle = BorderStroke(2.dp, AppDimBorder), focused = BorderStroke(2.dp, NeonPurpleGradient)),
                    glow = ShumGlow(focusedColor = NeonPurpleGlow),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Maximum seats")
                        Text(text = "${settings.maxHostSeats}", color = AppOnSurfaceVariant)
                    }
                }
            }
            }

            SettingsGroup(title = "Playback", showRule = true) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Max transcode video bitrate")
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppSettings.BITRATE_PRESETS.forEachIndexed { index, preset ->
                        val bitrateSelected = settings.maxVideoBitrateKbps == preset.kbps
                        ShumFilterChip(
                            selected = bitrateSelected,
                            onClick = { settings = settings.copy(maxVideoBitrateKbps = preset.kbps) },
                            modifier = Modifier
                                .let { if (index == 0) it.focusRequester(bitrateRowFocus) else it }
                                .focusProperties {
                                    up = maxHostSeatsFocus
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
            }
            }

            SettingsGroup(title = "Chat", showRule = true) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
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
            }
            }

            Spacer(Modifier.height(32.dp))
            ShumButton(
                onClick = {
                    scope.launch {
                        context.appSettingsStore.save(settings)
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

    if (maxSeatsMenuExpanded) {
        Box(modifier = Modifier.fillMaxSize().background(AppScrim.copy(alpha = 0.4f)))
        MaxSeatsMenu(
            selected = settings.maxHostSeats,
            rowFocuses = maxSeatsOptionFocuses,
            onSelect = { value ->
                settings = settings.copy(maxHostSeats = value)
                maxSeatsMenuExpanded = false
                runCatching { maxHostSeatsFocus.requestFocus() }
            },
            modifier = Modifier.align(Alignment.TopStart).padding(start = 220.dp, top = 220.dp),
        )
    }
    }
}

private val settingsGroupLabelStyle = TextStyle(fontSize = 12.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)

@Composable
private fun SettingsGroup(title: String, showRule: Boolean, content: @Composable () -> Unit) {
    Column {
        if (showRule) {
            Spacer(Modifier.height(26.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(AppSurfaceVariant))
            Spacer(Modifier.height(14.dp))
        }
        Text(text = title.uppercase(), style = settingsGroupLabelStyle, color = AppOnSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun RelaySettingsPane(
    settings: AppSettings,
    relayStatuses: Map<String, Pair<Boolean, Int>?>,
    editingRelayId: String?,
    pairingUrl: String?,
    pairingError: String?,
    testingRelayName: String?,
    testStatus: RelayStatus,
    relayRowFocus: (RelayEntry) -> RelayRowFocus,
    firstRelayRowFocus: FocusRequester?,
    lastRelayRowFocus: FocusRequester?,
    addRelayFocus: FocusRequester,
    cancelPairingFocus: FocusRequester,
    onBack: () -> Unit,
    onMakeDefault: (RelayEntry) -> Unit,
    onEdit: (RelayEntry) -> Unit,
    onRemove: (RelayEntry) -> Unit,
    onAddRelay: () -> Unit,
    onCancelPairing: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(48.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 24.dp),
            ) {
                Text("‹ Settings", color = AppOnSurfaceVariant)
            }
            Text("Relay settings", style = ShumTypography.displaySmall)
            Text(
                "Anyone who keeps a relay running can be added by address — a cloud host, a Pi in " +
                    "someone's front room, whatever answers.",
                color = AppOnSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                settings.relays.forEachIndexed { index, entry ->
                    val rowFocus = relayRowFocus(entry)
                    RelayRow(
                        entry = entry,
                        status = relayStatuses[entry.id],
                        onMakeDefault = { onMakeDefault(entry) },
                        onEdit = { onEdit(entry) },
                        onRemove = { onRemove(entry) },
                        rowFocus = rowFocus,
                        upFocus = if (index > 0) {
                            val prev = settings.relays[index - 1]
                            relayRowFocus(prev).leftmost(prev)
                        } else {
                            FocusRequester.Default
                        },
                        downFocus = if (index < settings.relays.lastIndex) {
                            val next = settings.relays[index + 1]
                            relayRowFocus(next).leftmost(next)
                        } else {
                            addRelayFocus
                        },
                    )
                }

                FocusableSurface(
                    onClick = onAddRelay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .focusRequester(addRelayFocus)
                        .focusProperties {
                            up = lastRelayRowFocus ?: FocusRequester.Default
                            down = if (pairingUrl != null) cancelPairingFocus else FocusRequester.Default
                        },
                    shape = RoundedCornerShape(8.dp),
                    colors = ShumColors(container = AppBackground, content = AppOnSurfaceVariant, focusedContent = AppWhite),
                    border = ShumBorder(idle = BorderStroke(2.dp, AppDimBorder), focused = BorderStroke(2.dp, NeonPurpleGradient)),
                    glow = ShumGlow(focusedColor = NeonPurpleGlow),
                ) {
                    Text("Add a relay")
                }

                if (pairingError != null) {
                    Text(pairingError)
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
                            content = pairingUrl,
                            modifier = Modifier
                                .size(160.dp)
                                .background(AppWhite)
                                .padding(12.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Scan with your phone (same Wi-Fi as the TV), or visit:")
                            Text(pairingUrl, style = ShumTypography.bodyLarge)
                            Text(
                                if (editingRelayId != null) {
                                    "Update the nickname and URL there — it'll change here automatically."
                                } else {
                                    "Type a nickname and the relay URL there — it'll appear here automatically."
                                },
                            )
                            ShumOutlinedButton(
                                onClick = onCancelPairing,
                                modifier = Modifier
                                    .focusRequester(cancelPairingFocus)
                                    .focusProperties {
                                        up = addRelayFocus
                                        down = FocusRequester.Default
                                    },
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }

                if (testingRelayName != null) {
                    RelayStatusLine(
                        status = testStatus,
                        relayNickname = testingRelayName,
                        onRetry = {},
                        onHostOnAnother = null,
                    )
                }
            }
        }

        NeonScrollbar(scrollState = scrollState, modifier = Modifier.padding(vertical = 48.dp, horizontal = 12.dp))
    }
}

@Composable
private fun MaxSeatsMenu(
    selected: Int,
    rowFocuses: Map<Int, FocusRequester>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = AppSettings.MAX_HOST_SEATS_OPTIONS
    LaunchedEffect(Unit) {
        runCatching { rowFocuses.getValue(selected).requestFocus() }
    }
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .width(300.dp)
            .background(AppBackground, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(max = 320.dp)
                .verticalScroll(scrollState)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEachIndexed { index, value ->
                val applied = value == selected
                FocusableSurface(
                    onClick = { onSelect(value) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .focusRequester(rowFocuses.getValue(value))
                        .focusProperties {
                            up = if (index > 0) rowFocuses.getValue(options[index - 1]) else FocusRequester.Cancel
                            down = if (index < options.lastIndex) rowFocuses.getValue(options[index + 1]) else FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                    selected = applied,
                    shape = RoundedCornerShape(8.dp),
                    colors = ShumColors(
                        container = Color.Transparent,
                        content = AppWhite,
                        focusedContainer = NeonPurple,
                        selectedContainer = NeonPurple.copy(alpha = 0.35f),
                    ),
                    border = ShumBorder(focused = BorderStroke(2.dp, NeonPurpleGradient)),
                    glow = ShumGlow(focusedColor = NeonPurpleGlow),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.width(16.dp)) {
                            if (applied) Text("✓")
                        }
                        Text("$value")
                    }
                }
            }
        }
        NeonScrollbar(scrollState = scrollState, modifier = Modifier.padding(start = 8.dp))
    }
}

private class RelayRowFocus {
    val makeDefault = FocusRequester()
    val edit = FocusRequester()
    val remove = FocusRequester()
}

private fun RelayRowFocus.leftmost(entry: RelayEntry): FocusRequester = if (entry.isDefault) edit else makeDefault

@Composable
private fun RelayRow(
    entry: RelayEntry,
    status: Pair<Boolean, Int>?,
    onMakeDefault: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    rowFocus: RelayRowFocus,
    upFocus: FocusRequester,
    downFocus: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppBackground, RoundedCornerShape(8.dp))
            .border(BorderStroke(2.dp, AppDimBorder), RoundedCornerShape(8.dp))
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        RelayStatusDot(status = if (status?.first == true) RelayStatus.DotOnly else RelayStatus.Silent)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(entry.nickname)
                if (entry.isDefault) {
                    Box(
                        modifier = Modifier
                            .background(NeonPurple.copy(alpha = 0.9f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text("Default", color = AppWhite)
                    }
                }
            }
            Text(relayStatusLabel(status), color = AppOnSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!entry.isDefault) {
                ShumOutlinedButton(
                    onClick = onMakeDefault,
                    modifier = Modifier
                        .focusRequester(rowFocus.makeDefault)
                        .focusProperties {
                            up = upFocus
                            down = downFocus
                            right = rowFocus.edit
                        },
                ) {
                    Text("Make default")
                }
            }
            ShumOutlinedButton(
                onClick = onEdit,
                modifier = Modifier
                    .focusRequester(rowFocus.edit)
                    .focusProperties {
                        up = upFocus
                        down = downFocus
                        left = if (!entry.isDefault) rowFocus.makeDefault else FocusRequester.Default
                        right = rowFocus.remove
                    },
            ) {
                Text("Edit")
            }
            ShumOutlinedButton(
                onClick = onRemove,
                modifier = Modifier
                    .focusRequester(rowFocus.remove)
                    .focusProperties {
                        up = upFocus
                        down = downFocus
                        left = rowFocus.edit
                    },
            ) {
                Text("Remove")
            }
        }
    }
}

private fun relayStatusLabel(status: Pair<Boolean, Int>?): String = when {
    status == null -> "…"
    !status.first -> "Not responding"
    else -> "${status.second} room${if (status.second == 1) "" else "s"}"
}

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
