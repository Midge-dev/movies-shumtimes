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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.moviesshumtimes.tv.data.pairing.PairingServer
import com.moviesshumtimes.tv.data.settings.SettingsStore
import com.moviesshumtimes.tv.ui.common.ClickToTypeTextField
import com.moviesshumtimes.tv.ui.common.QrCodeImage
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonBorder
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonGlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Shown once, right after Plex login, when no relay URL is saved yet —
// makes watch-together setup part of first launch instead of something you
// only discover by finding it in Settings later. "Skip for now" is kept
// deliberately available though: sync has always been a bonus on top of
// local playback here, never a requirement, and someone who only wants to
// browse/watch solo shouldn't be blocked from reaching their library.
@Composable
fun RelaySetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var relayUrl by remember { mutableStateOf("") }

    var pairingServer by remember { mutableStateOf<PairingServer?>(null) }
    var pairingUrl by remember { mutableStateOf<String?>(null) }
    var pairingError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { pairingServer?.stop() } }

    suspend fun saveAndContinue() {
        val current = SettingsStore.observe(context).first()
        SettingsStore.save(context, current.copy(relayUrl = relayUrl.trim().ifBlank { null }))
        onDone()
    }

    val relayUrlFocus = remember { FocusRequester() }
    val pairButtonFocus = remember { FocusRequester() }
    val cancelPairingFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Set up watch-together", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Movies Shumtimes syncs playback with whoever you're watching with, over a " +
                    "relay server. Paste its URL below, or scan the QR from your phone.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )

            ClickToTypeTextField(
                value = relayUrl,
                onValueChange = { relayUrl = it },
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
                    .widthIn(min = 500.dp)
                    .focusRequester(relayUrlFocus)
                    .focusProperties { down = pairButtonFocus },
            )

            Button(
                onClick = {
                    pairingError = null
                    val server = PairingServer(
                        onSubmitted = { value ->
                            Handler(Looper.getMainLooper()).post {
                                relayUrl = value
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
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(24.dp),
                ) {
                    QrCodeImage(
                        content = pairingUrl!!,
                        modifier = Modifier.size(160.dp).background(Color.White).padding(12.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Scan with your phone (same Wi-Fi as the TV), or visit:")
                        Text(pairingUrl!!, style = MaterialTheme.typography.bodyLarge)
                        Text("Paste the relay URL there and it'll appear here automatically.")
                        OutlinedButton(
                            onClick = {
                                pairingServer?.stop()
                                pairingServer = null
                                pairingUrl = null
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
                Button(
                    onClick = { scope.launch { saveAndContinue() } },
                    enabled = relayUrl.isNotBlank(),
                    colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
                    border = neonPurpleButtonBorder(),
                    glow = neonPurpleButtonGlow(),
                    modifier = Modifier
                        .focusRequester(saveFocus)
                        .focusProperties {
                            up = if (pairingUrl != null) cancelPairingFocus else pairButtonFocus
                            down = skipFocus
                        },
                ) {
                    Text("Save & continue")
                }

                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.focusRequester(skipFocus).focusProperties { up = saveFocus },
                ) {
                    Text("Skip for now")
                }
            }
        }
    }
}
