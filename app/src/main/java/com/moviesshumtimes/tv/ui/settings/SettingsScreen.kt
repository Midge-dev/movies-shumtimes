package com.moviesshumtimes.tv.ui.settings

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.RadioButtonDefaults
import androidx.tv.material3.Text
import com.moviesshumtimes.tv.data.pairing.PairingServer
import com.moviesshumtimes.tv.data.plex.PlexResource
import com.moviesshumtimes.tv.data.plex.PlexResourcesApi
import com.moviesshumtimes.tv.data.settings.AppSettings
import com.moviesshumtimes.tv.data.settings.SettingsStore
import com.moviesshumtimes.tv.ui.common.ClickToTypeTextField
import com.moviesshumtimes.tv.ui.common.NeonScrollbar
import com.moviesshumtimes.tv.ui.common.QrCodeImage
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonBorder
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonGlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val BITRATE_PRESETS_KBPS = listOf(2000, 4000, 8000, 20000)

@Composable
fun SettingsScreen(accountToken: String, clientIdentifier: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AppSettings()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings = SettingsStore.observe(context).first()
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
    val saveFocus = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.displaySmall)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Available sources")
                when {
                    !sourcesLoaded -> Text("Loading sources…")
                    sourcesError != null -> Text("Couldn't load sources: $sourcesError")
                    sources.isEmpty() -> Text("No sources found")
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sources.forEachIndexed { index, source ->
                            val selected = settings.selectedServerId == source.machineIdentifier
                            var rowFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { settings = settings.copy(selectedServerId = source.machineIdentifier) },
                                colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
                                border = neonPurpleButtonBorder(),
                                glow = neonPurpleButtonGlow(),
                                modifier = Modifier
                                    .onFocusChanged { rowFocused = it.isFocused }
                                    .focusRequester(sourceFocuses[index])
                                    .focusProperties {
                                        up = if (index > 0) sourceFocuses[index - 1] else FocusRequester.Default
                                        down = if (index < sourceFocuses.lastIndex) sourceFocuses[index + 1] else relayUrlFocus
                                    },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // The radio's own two-tone purple blends into the
                                    // button's NeonPurple focusedContainerColor, so swap
                                    // to a dark color while this row is focused instead.
                                    RadioButton(
                                        selected = selected,
                                        onClick = null,
                                        colors = if (rowFocused) {
                                            RadioButtonDefaults.colors(
                                                selectedColor = Color.Black,
                                                unselectedColor = Color.DarkGray,
                                            )
                                        } else {
                                            RadioButtonDefaults.colors(
                                                selectedColor = NeonPurple,
                                                unselectedColor = NeonPurpleGlow,
                                            )
                                        },
                                    )
                                    Text("${source.name}${if (source.owned) " (owned)" else ""}")
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Relay URL (watch-together server)")
                ClickToTypeTextField(
                    value = settings.relayUrl,
                    onValueChange = { settings = settings.copy(relayUrl = it) },
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                        .width(500.dp)
                        .focusRequester(relayUrlFocus)
                        .focusProperties {
                            up = if (sourceFocuses.isNotEmpty()) sourceFocuses.last() else FocusRequester.Default
                            down = pairButtonFocus
                        },
                )

                Button(
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
                    colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
                    border = neonPurpleButtonBorder(),
                    glow = neonPurpleButtonGlow(),
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
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(24.dp),
                    ) {
                        QrCodeImage(
                            content = pairingUrl!!,
                            modifier = Modifier
                                .size(160.dp)
                                .background(Color.White)
                                .padding(12.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Scan with your phone (same Wi-Fi as the TV), or visit:")
                            Text(pairingUrl!!, style = MaterialTheme.typography.bodyLarge)
                            Text("Paste the relay URL there and it'll appear here automatically.")
                            Button(
                                onClick = {
                                    pairingServer?.stop()
                                    pairingServer = null
                                    pairingUrl = null
                                },
                                colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
                                border = neonPurpleButtonBorder(),
                                glow = neonPurpleButtonGlow(),
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
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BITRATE_PRESETS_KBPS.forEachIndexed { index, preset ->
                        val selected = settings.maxVideoBitrateKbps == preset
                        Button(
                            onClick = { settings = settings.copy(maxVideoBitrateKbps = preset) },
                            colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
                            border = neonPurpleButtonBorder(),
                            glow = neonPurpleButtonGlow(),
                            modifier = Modifier
                                .let { if (index == 0) it.focusRequester(bitrateRowFocus) else it }
                                .focusProperties {
                                    up = if (pairingUrl != null) cancelPairingFocus else pairButtonFocus
                                    down = forceBurnFocus
                                },
                        ) {
                            Text(if (selected) "[${preset / 1000} Mbps]" else "${preset / 1000} Mbps")
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Force-burn subtitles into video even when not required")
                Button(
                    onClick = { settings = settings.copy(forceBurnSubtitles = !settings.forceBurnSubtitles) },
                    colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
                    border = neonPurpleButtonBorder(),
                    glow = neonPurpleButtonGlow(),
                    modifier = Modifier
                        .focusRequester(forceBurnFocus)
                        .focusProperties {
                            up = bitrateRowFocus
                            down = saveFocus
                        },
                ) {
                    Text(if (settings.forceBurnSubtitles) "On" else "Off")
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        SettingsStore.save(context, settings)
                        onSaved()
                    }
                },
                colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
                border = neonPurpleButtonBorder(),
                glow = neonPurpleButtonGlow(),
                modifier = Modifier
                    .focusRequester(saveFocus)
                    .focusProperties { up = forceBurnFocus },
            ) {
                Text("Save")
            }
        }

        NeonScrollbar(scrollState = scrollState, modifier = Modifier.padding(vertical = 48.dp, horizontal = 12.dp))
    }
}
