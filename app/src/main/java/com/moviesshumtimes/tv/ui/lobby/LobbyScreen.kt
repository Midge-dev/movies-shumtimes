package com.moviesshumtimes.tv.ui.lobby

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.moviesshumtimes.tv.R
import com.moviesshumtimes.tv.data.plex.PlexMovieDetail
import com.moviesshumtimes.tv.data.settings.AppSettings
import com.moviesshumtimes.tv.data.settings.SettingsStore
import com.moviesshumtimes.tv.sync.ConnectionState
import com.moviesshumtimes.tv.sync.RelayClient
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonBorder
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val PRESENCE_INTERVAL_MS = 3_000L

// A skippable waiting room: shows who's connected before playback starts,
// but never blocks solo viewing — Start Movie is always enabled, matching
// the rest of the sync layer's "bonus, not a dependency" design.
@Composable
fun LobbyScreen(
    detail: PlexMovieDetail,
    localUsername: String,
    localAvatarUrl: String?,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<AppSettings?>(null) }

    LaunchedEffect(Unit) {
        settings = SettingsStore.observe(context).first()
    }

    BackHandler(onBack = onBack)

    val currentSettings = settings ?: run {
        Text("Loading…")
        return
    }

    val relay = remember { RelayClient(currentSettings.relayUrl, scope) }
    val connectionState by relay.connectionState.collectAsState()
    var remoteUsername by remember { mutableStateOf<String?>(null) }
    var remoteAvatarUrl by remember { mutableStateOf<String?>(null) }

    DisposableEffect(relay) {
        relay.connect()
        onDispose { relay.disconnect() }
    }

    LaunchedEffect(relay, connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            while (true) {
                relay.send("presence", username = localUsername, avatarUrl = localAvatarUrl)
                delay(PRESENCE_INTERVAL_MS)
            }
        }
    }

    LaunchedEffect(relay) {
        relay.events.collect { event ->
            when (event.type) {
                "presence" -> {
                    remoteUsername = event.username
                    remoteAvatarUrl = event.avatarUrl
                }
                "start" -> onStart()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.lobby_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)))

        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(detail.title, style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(
                "Waiting to watch together",
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp, bottom = 48.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                LobbyPersonCard(username = localUsername, avatarUrl = localAvatarUrl, present = true)
                LobbyPersonCard(
                    username = remoteUsername ?: "Waiting…",
                    avatarUrl = remoteAvatarUrl,
                    present = remoteUsername != null,
                )
            }

            Button(
                onClick = {
                    relay.send("start", username = localUsername)
                    onStart()
                },
                colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
                border = neonPurpleButtonBorder(),
                glow = neonPurpleButtonGlow(),
                modifier = Modifier.padding(top = 48.dp),
            ) {
                Text("Start")
            }
        }
    }
}

@Composable
private fun LobbyPersonCard(username: String, avatarUrl: String?, present: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(if (present) NeonPurple.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(username.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
        }
        Text(username, color = Color.White, modifier = Modifier.padding(top = 12.dp))
    }
}
