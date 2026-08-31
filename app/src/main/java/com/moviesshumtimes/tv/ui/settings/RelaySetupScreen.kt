package com.moviesshumtimes.tv.ui.settings

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.data.pairing.PairingServer
import com.moviesshumtimes.tv.data.settings.RelayEntry
import com.moviesshumtimes.tv.data.settings.appSettingsStore
import com.moviesshumtimes.tv.ui.common.ClickToTypeTextField
import com.moviesshumtimes.tv.ui.common.NeonScrollbar
import com.moviesshumtimes.tv.ui.common.QrCodeImage
import com.moviesshumtimes.tv.ui.kit.ShumButton
import com.moviesshumtimes.tv.ui.kit.ShumOutlinedButton
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppOnSurface
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppWhite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun RelaySetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var relayUrl by remember { mutableStateOf("") }
    var relayNickname by remember { mutableStateOf("My relay") }

    var pairingServer by remember { mutableStateOf<PairingServer?>(null) }
    var pairingUrl by remember { mutableStateOf<String?>(null) }
    var pairingError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { pairingServer?.stop() } }

    suspend fun saveAndContinue() {
        val store = context.appSettingsStore
        val current = store.observe().first()
        val url = relayUrl.trim()
        val relays = if (url.isBlank()) {
            emptyList()
        } else {
            listOf(RelayEntry(id = java.util.UUID.randomUUID().toString(), nickname = relayNickname.trim().ifBlank { "My relay" }, url = url, isDefault = true))
        }
        store.save(current.copy(relays = relays))
        onDone()
    }

    val relayUrlFocus = remember { FocusRequester() }
    val pairButtonFocus = remember { FocusRequester() }
    val cancelPairingFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }

    val scrollState = rememberScrollState()

    Row(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Set up watch-together", style = ShumTypography.headlineMedium)
                Text(
                    "Movies Shumtimes syncs playback with whoever you're watching with, over a " +
                        "relay server. Paste its URL below, or scan the QR from your phone.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
                )

                ClickToTypeTextField(
                    value = relayUrl,
                    onValueChange = { relayUrl = it },
                    textStyle = TextStyle(color = AppOnSurface),
                    modifier = Modifier
                        .background(AppSurfaceVariant)
                        .padding(12.dp)
                        .widthIn(min = 500.dp)
                        .focusRequester(relayUrlFocus)
                        .focusProperties { down = pairButtonFocus },
                )

                ShumButton(
                    onClick = {
                        pairingError = null
                        val server = PairingServer(
                            onSubmitted = { nickname, url ->
                                Handler(Looper.getMainLooper()).post {
                                    relayNickname = nickname
                                    relayUrl = url
                                    pairingServer?.stop()
                                    pairingServer = null
                                    pairingUrl = null
                                    runCatching { saveFocus.requestFocus() }
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
                        .padding(top = 16.dp)
                        .focusRequester(pairButtonFocus)
                        .focusProperties {
                            up = relayUrlFocus
                            down = if (pairingUrl != null) cancelPairingFocus else saveFocus
                        },
                ) {
                    Text("Pair from phone")
                }

                if (pairingError != null) {
                    Text(pairingError!!, modifier = Modifier.padding(top = 16.dp))
                }

                if (pairingUrl != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .background(AppSurfaceVariant)
                            .padding(24.dp),
                    ) {
                        QrCodeImage(
                            content = pairingUrl!!,
                            modifier = Modifier.size(160.dp).background(AppWhite).padding(12.dp),
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
                                    runCatching { pairButtonFocus.requestFocus() }
                                },
                                modifier = Modifier
                                    .focusRequester(cancelPairingFocus)
                                    .focusProperties {
                                        up = pairButtonFocus
                                        down = saveFocus
                                    },
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 32.dp)) {
                    ShumButton(
                        onClick = { scope.launch { saveAndContinue() } },
                        enabled = relayUrl.isNotBlank(),
                        modifier = Modifier
                            .focusRequester(saveFocus)
                            .focusProperties {
                                up = if (pairingUrl != null) cancelPairingFocus else pairButtonFocus
                                down = skipFocus
                            },
                    ) {
                        Text("Save & continue")
                    }

                    ShumOutlinedButton(
                        onClick = onDone,
                        modifier = Modifier.focusRequester(skipFocus).focusProperties { up = saveFocus },
                    ) {
                        Text("Skip for now")
                    }
                }
            }
        }
        NeonScrollbar(scrollState = scrollState, modifier = Modifier.padding(start = 12.dp))
    }
}
