package com.moviesshumtimes.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.moviesshumtimes.tv.data.settings.AppSettings
import com.moviesshumtimes.tv.data.settings.SettingsStore
import com.moviesshumtimes.tv.ui.common.ClickToTypeTextField
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonBorder
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonGlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val BITRATE_PRESETS_KBPS = listOf(2000, 4000, 8000, 20000)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AppSettings()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings = SettingsStore.observe(context).first()
        loaded = true
    }

    BackHandler(onBack = onBack)

    if (!loaded) {
        Text("Loading settings…")
        return
    }

    // BasicTextField doesn't hand D-pad DOWN/UP off to neighboring
    // focusables on its own (it treats them as text-cursor movement first),
    // so the row below it would otherwise be unreachable by remote. These
    // FocusRequesters make the down/up route explicit between the fields.
    val relayUrlFocus = remember { FocusRequester() }
    val bitrateRowFocus = remember { FocusRequester() }
    val forceBurnFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.displaySmall)

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
                    .focusProperties { down = bitrateRowFocus },
            )
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
                                up = relayUrlFocus
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
                    onBack()
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
}
