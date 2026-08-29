package com.moviesshumtimes.tv.ui.settings

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableStateMapOf
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

    // "Pair from phone" — a phone on the same Wi-Fi can type a relay's
    // nickname + URL via a tiny local web page served straight from the TV,
    // instead of typing a long wss://...?token=... string on a remote.
    var pairingServer by remember { mutableStateOf<PairingServer?>(null) }
    var pairingUrl by remember { mutableStateOf<String?>(null) }
    var pairingError by remember { mutableStateOf<String?>(null) }
    // Non-null while the phone-pairing panel is open to edit an existing
    // relay's nickname/URL in place, rather than append a new entry.
    var editingRelayId by remember { mutableStateOf<String?>(null) }
    // Design spec 09d: "Test & save" — a candidate relay's reachability is
    // checked (tolerating a cold start) before it's added to the list, and
    // saved either way, greyed if the test failed.
    var testingRelayName by remember { mutableStateOf<String?>(null) }
    var testStatus by remember { mutableStateOf<RelayStatus>(RelayStatus.Silent) }

    val relayDirectoryApi = remember { RelayDirectoryApi() }
    // id -> (reachable, room count) — null while still loading. Fetched
    // once per relay list identity (on load, and again whenever a relay is
    // added/removed) rather than continuously polled; this screen is
    // short-lived.
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

    // BasicTextField doesn't hand D-pad DOWN/UP off to neighboring
    // focusables on its own (it treats them as text-cursor movement first),
    // so the row below it would otherwise be unreachable by remote. These
    // FocusRequesters make the down/up route explicit between the fields.
    val sourceFocuses = remember(sources) { sources.map { FocusRequester() } }
    // Keyed by relay id (stable across add/remove/make-default), not by the
    // relay list itself — a remember(settings.relays) map would rebuild
    // every FocusRequester whenever ANY entry's fields changed (they're
    // data classes, so isDefault flipping changes list equality), orphaning
    // whichever row/button currently held focus and dropping the remote's
    // next press into the nav drawer. Entries for removed relays are simply
    // left unused — harmless for a screen this short-lived.
    val relayRowFocuses = remember { mutableStateMapOf<String, RelayRowFocus>() }
    fun relayRowFocus(entry: RelayEntry): RelayRowFocus = relayRowFocuses.getOrPut(entry.id) { RelayRowFocus() }
    val firstRelayRowFocus = settings.relays.firstOrNull()?.let { relayRowFocus(it).leftmost(it) }
    val lastRelayRowFocus = settings.relays.lastOrNull()?.let { relayRowFocus(it).remove }
    val addRelayFocus = remember { FocusRequester() }
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
    // fetch finishes either way — falls back to the first relay row, or the
    // always-present "Add a relay" row if there are none yet.
    LaunchedEffect(sourcesLoaded, sourceFocuses) {
        if (!sourcesLoaded) return@LaunchedEffect
        val target = sourceFocuses.firstOrNull() ?: firstRelayRowFocus ?: addRelayFocus
        runCatching { target.requestFocus() }
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
                                        down = if (index < sourceFocuses.lastIndex) {
                                            sourceFocuses[index + 1]
                                        } else {
                                            firstRelayRowFocus ?: addRelayFocus
                                        }
                                    },
                            )
                        }
                    }
                }
            }

            // Relay list changes take effect immediately rather than waiting
            // on the screen's big Save button below — the add flow already
            // behaved this way (a relay shows up mid-screen as soon as the
            // phone submits it), and Make default / Remove / Edit need the
            // same immediacy so Home's room polling never disagrees with
            // what Settings shows. Reads the currently *persisted* settings
            // rather than the in-progress `settings` var, so an unsaved
            // pending edit to something unrelated (bitrate, subtitles, ...)
            // doesn't get silently committed early by a relay change.
            fun persistRelays(newRelays: List<RelayEntry>) {
                settings = settings.copy(relays = newRelays)
                scope.launch {
                    val persisted = context.appSettingsStore.observe().first()
                    context.appSettingsStore.save(persisted.copy(relays = newRelays))
                }
            }

            // Shared by "Add a relay" and each row's "Edit" button — both
            // hand off to the same phone-pairing QR panel; editingId is null
            // for a fresh add (appends a new entry) or a relay's id to
            // update that entry in place instead.
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
                            // The QR panel (and its focused Cancel button, if
                            // that's what the remote was last aimed at) is
                            // about to be removed from composition by
                            // clearing pairingUrl above — without an
                            // explicit target, Compose's focus-loss fallback
                            // can escape to the nav drawer, and a buffered
                            // remote press landing there reads as "kicked
                            // out of Settings" (same bug class as this app's
                            // other documented focus-escape fixes).
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

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hint != null) {
                    Text(hint, color = NeonPurple)
                }
                Text("Relays")
                Text(
                    "Anyone who keeps a relay running can be added by address — a cloud host, a Pi in " +
                        "someone's front room, whatever answers.",
                    color = AppOnSurfaceVariant,
                )

                settings.relays.forEachIndexed { index, entry ->
                    val rowFocus = relayRowFocus(entry)
                    RelayRow(
                        entry = entry,
                        status = relayStatuses[entry.id],
                        onMakeDefault = {
                            persistRelays(settings.relays.map { it.copy(isDefault = it.id == entry.id) })
                        },
                        onEdit = { startPairing(entry.id, entry.nickname, entry.url) },
                        onRemove = {
                            val remaining = settings.relays.filterNot { it.id == entry.id }
                            persistRelays(
                                if (entry.isDefault && remaining.isNotEmpty()) {
                                    remaining.mapIndexed { i, r -> r.copy(isDefault = i == 0) }
                                } else {
                                    remaining
                                },
                            )
                        },
                        rowFocus = rowFocus,
                        upFocus = if (index > 0) {
                            val prev = settings.relays[index - 1]
                            relayRowFocus(prev).leftmost(prev)
                        } else {
                            sourceFocuses.lastOrNull() ?: FocusRequester.Default
                        },
                        downFocus = if (index < settings.relays.lastIndex) {
                            val next = settings.relays[index + 1]
                            relayRowFocus(next).leftmost(next)
                        } else {
                            addRelayFocus
                        },
                    )
                }

                // Design spec 09d: same phone hand-off as first-run setup —
                // the TV never asks anyone to type a wss:// address on a
                // D-pad. Invoked fresh per attempt (add or edit), same as it
                // always was.
                FocusableSurface(
                    onClick = { startPairing(editingId = null, prefillNickname = "", prefillUrl = "") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .focusRequester(addRelayFocus)
                        .focusProperties {
                            up = lastRelayRowFocus ?: (sourceFocuses.lastOrNull() ?: FocusRequester.Default)
                            down = if (pairingUrl != null) cancelPairingFocus else bitrateRowFocus
                        },
                    shape = RoundedCornerShape(8.dp),
                    colors = ShumColors(container = AppBackground, content = AppOnSurfaceVariant, focusedContent = AppWhite),
                    border = ShumBorder(idle = BorderStroke(2.dp, AppDimBorder), focused = BorderStroke(2.dp, NeonPurpleGradient)),
                    glow = ShumGlow(focusedColor = NeonPurpleGlow),
                ) {
                    Text("Add a relay")
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
                            Text(
                                if (editingRelayId != null) {
                                    "Update the nickname and URL there — it'll change here automatically."
                                } else {
                                    "Type a nickname and the relay URL there — it'll appear here automatically."
                                },
                            )
                            ShumOutlinedButton(
                                onClick = {
                                    pairingServer?.stop()
                                    pairingServer = null
                                    pairingUrl = null
                                    editingRelayId = null
                                },
                                modifier = Modifier
                                    .focusRequester(cancelPairingFocus)
                                    .focusProperties {
                                        up = addRelayFocus
                                        down = bitrateRowFocus
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
                        relayNickname = testingRelayName ?: "",
                        onRetry = {},
                        onHostOnAnother = null,
                    )
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
                                    up = if (pairingUrl != null) cancelPairingFocus else addRelayFocus
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
}

// Stable per-relay focus targets, keyed (by the caller) on entry.id rather
// than list position — see the relayRowFocuses comment in SettingsScreen
// for why identity has to survive add/remove/make-default.
private class RelayRowFocus {
    val makeDefault = FocusRequester()
    val edit = FocusRequester()
    val remove = FocusRequester()
}

// The leftmost real button in a row — default rows skip "Make default", so
// their leftmost is "Edit" instead. Used to target vertical D-pad moves
// between rows without caring which buttons a given row actually has.
private fun RelayRowFocus.leftmost(entry: RelayEntry): FocusRequester = if (entry.isDefault) edit else makeDefault

// Design spec 09d: one row per configured relay — dot, nickname (+ Default
// badge) and a status line (room count / not responding) on the left, real
// buttons on the right. The URL itself isn't shown — three actions (make
// default, edit, remove) don't fit a click/hold-to-remove gesture model
// cleanly, so each gets its own focusable button instead.
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
