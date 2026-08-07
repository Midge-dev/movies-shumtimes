package com.moviesshumtimes.tv.ui.lobby

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.moviesshumtimes.tv.sync.ChatMessage
import com.moviesshumtimes.tv.sync.ConnectionState
import com.moviesshumtimes.tv.sync.RelayClient
import com.moviesshumtimes.tv.sync.RelayEvent
import com.moviesshumtimes.tv.sync.toChatMessage
import com.moviesshumtimes.tv.ui.common.ChatOverlay
import com.moviesshumtimes.tv.ui.common.QrCodeImage
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonBorder
import com.moviesshumtimes.tv.ui.theme.neonPurpleButtonGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import java.net.URI

private const val PRESENCE_INTERVAL_MS = 3_000L
private const val ROSTER_STALE_MS = PRESENCE_INTERVAL_MS * 3

private data class RosterEntry(val username: String, val avatarUrl: String?, val lastSeenMs: Long)

// A skippable waiting room: shows who's connected before playback starts,
// but never blocks solo viewing — Start Movie is always enabled, matching
// the rest of the sync layer's "bonus, not a dependency" design. The relay
// connection is owned above this screen now (see MainActivity's AppRoot)
// so it survives the transition into PlayerScreen instead of reconnecting.
@Composable
fun LobbyScreen(
    detail: PlexMovieDetail,
    localUsername: String,
    localAvatarUrl: String?,
    relay: RelayClient,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val connectionState by relay.connectionState.collectAsState()
    var roster by remember { mutableStateOf<Map<String, RosterEntry>>(emptyMap()) }
    var chatEnabled by remember { mutableStateOf(false) }
    val chatMessages = remember { MutableSharedFlow<ChatMessage>(extraBufferCapacity = 16) }

    BackHandler(onBack = onBack)

    LaunchedEffect(relay, connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            while (true) {
                relay.send(RelayEvent(kind = "presence", fromPeerId = relay.myPeerId, username = localUsername, avatarUrl = localAvatarUrl))
                delay(PRESENCE_INTERVAL_MS)
            }
        }
    }

    // Roster entries expire on their own if presence stops arriving (peer
    // backgrounded, lost connection) rather than needing an explicit
    // "peerLeft" signal from the dumb relay.
    LaunchedEffect(Unit) {
        while (true) {
            delay(PRESENCE_INTERVAL_MS)
            val cutoff = System.currentTimeMillis() - ROSTER_STALE_MS
            roster = roster.filterValues { it.lastSeenMs >= cutoff }
        }
    }

    LaunchedEffect(relay) {
        relay.events.collect { event ->
            when (event.kind) {
                "presence" -> {
                    val fromPeerId = event.fromPeerId ?: return@collect
                    if (fromPeerId == relay.myPeerId) return@collect
                    roster = roster + (fromPeerId to RosterEntry(event.username ?: "Guest", event.avatarUrl, System.currentTimeMillis()))
                }
                "start" -> onStart()
                "chat" -> chatMessages.tryEmit(event.toChatMessage())
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

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(64.dp),
            ) {
                LobbyPersonCard(username = localUsername, avatarUrl = localAvatarUrl, present = true)
                if (roster.isEmpty()) {
                    LobbyPersonCard(username = "Waiting…", avatarUrl = null, present = false)
                } else {
                    for ((peerId, entry) in roster) {
                        LobbyPersonCard(username = entry.username, avatarUrl = entry.avatarUrl, present = true)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 48.dp)) {
                Button(
                    onClick = {
                        relay.send(RelayEvent(kind = "start", fromPeerId = relay.myPeerId, username = localUsername))
                        onStart()
                    },
                    colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
                    border = neonPurpleButtonBorder(),
                    glow = neonPurpleButtonGlow(),
                ) {
                    Text("Start")
                }

                Button(
                    onClick = { chatEnabled = !chatEnabled },
                    colors = ButtonDefaults.colors(focusedContainerColor = NeonPurple),
                    border = neonPurpleButtonBorder(),
                    glow = neonPurpleButtonGlow(),
                ) {
                    Text(if (chatEnabled) "Chat: On" else "Enable chat")
                }
            }

            if (chatEnabled) {
                val chatUrl = remember(relay.relayUrl) { relayUrlToChatUrl(relay.relayUrl) }
                if (chatUrl != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier
                            .padding(top = 32.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(24.dp),
                    ) {
                        QrCodeImage(
                            content = chatUrl,
                            modifier = Modifier.size(160.dp).background(Color.White).padding(12.dp),
                        )
                        Column {
                            Text("Scan with your phone to join the chat", color = Color.White)
                            Text(chatUrl, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    Text("Chat needs a wss:// or ws:// relay URL — check Settings.", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }

        if (chatEnabled) {
            ChatOverlay(
                messages = chatMessages,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.5f).padding(24.dp),
            )
        }
    }
}

// Derives the relay's phone-facing chat page URL from its WebSocket URL —
// same host/port/token, http(s) scheme, /chat path. No app install needed
// on the phone side, matching the existing "Pair from phone" QR flow.
private fun relayUrlToChatUrl(relayUrl: String): String? {
    val uri = runCatching { URI(relayUrl) }.getOrNull() ?: return null
    val scheme = when (uri.scheme) {
        "wss" -> "https"
        "ws" -> "http"
        else -> return null
    }
    val host = uri.host ?: return null
    val portPart = if (uri.port != -1) ":${uri.port}" else ""
    val query = uri.rawQuery?.let { "?$it" } ?: ""
    return "$scheme://$host$portPart/chat$query"
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
