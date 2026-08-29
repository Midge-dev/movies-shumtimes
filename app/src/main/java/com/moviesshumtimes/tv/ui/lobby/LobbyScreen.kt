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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import coil3.compose.AsyncImage
import com.moviesshumtimes.tv.R
import com.moviesshumtimes.tv.data.plex.PlexMovieDetail
import com.moviesshumtimes.tv.data.settings.ChatOverlayCorner
import com.moviesshumtimes.tv.data.settings.appSettingsStore
import com.moviesshumtimes.tv.sync.ChatMessage
import com.moviesshumtimes.tv.sync.ConnectionState
import com.moviesshumtimes.tv.sync.RelayClient
import com.moviesshumtimes.tv.sync.RelayEvent
import com.moviesshumtimes.tv.sync.toChatMessage
import com.moviesshumtimes.tv.ui.common.ChatOverlay
import com.moviesshumtimes.tv.ui.common.QrCodeImage
import com.moviesshumtimes.tv.ui.common.WatchTogetherIcon
import com.moviesshumtimes.tv.ui.kit.ShumButton
import com.moviesshumtimes.tv.ui.kit.ShumOutlinedButton
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppScrim
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import java.net.URI
import java.net.URLEncoder

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
    var showChatModal by remember { mutableStateOf(false) }
    val chatMessages = remember { MutableSharedFlow<ChatMessage>(extraBufferCapacity = 16) }

    // Lobby renders immediately with the default corner and re-aligns once
    // the real preference loads — unlike PlayerScreen, this screen shouldn't
    // block on a settings read just to draw its chat overlay.
    val context = LocalContext.current
    var chatOverlayCorner by remember { mutableStateOf(ChatOverlayCorner.BOTTOM_END) }
    LaunchedEffect(Unit) {
        chatOverlayCorner = context.appSettingsStore.observe().first().chatOverlayCorner
    }
    val chatAlignment = when (chatOverlayCorner) {
        ChatOverlayCorner.TOP_START -> Alignment.TopStart
        ChatOverlayCorner.TOP_END -> Alignment.TopEnd
        ChatOverlayCorner.BOTTOM_START -> Alignment.BottomStart
        ChatOverlayCorner.BOTTOM_END -> Alignment.BottomEnd
    }

    BackHandler(onBack = onBack)
    // Composed after (and so takes priority over) the screen-level handler
    // above while the modal is open — dismisses just the modal on Back
    // instead of leaving the whole lobby.
    BackHandler(enabled = showChatModal) { showChatModal = false }

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
        Box(modifier = Modifier.fillMaxSize().background(AppScrim.copy(alpha = 0.78f)))

        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WatchTogetherIcon()
                Text(detail.title, style = ShumTypography.displaySmall, color = AppWhite)
            }
            Text(
                "Waiting to watch together",
                color = AppWhite,
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
                ShumButton(
                    onClick = {
                        relay.send(RelayEvent(kind = "start", fromPeerId = relay.myPeerId, username = localUsername))
                        onStart()
                    },
                ) {
                    Text("Start")
                }

                ShumButton(onClick = { showChatModal = true }) {
                    Text("Chat QR code")
                }
            }
        }

        ChatOverlay(
            messages = chatMessages,
            corner = chatOverlayCorner,
            modifier = Modifier.align(chatAlignment).fillMaxWidth(0.5f).padding(24.dp),
        )

        if (showChatModal) {
            ChatQrModal(relayUrl = relay.relayUrl, defaultName = localUsername, onDismiss = { showChatModal = false })
        }
    }
}

// A dedicated full-screen modal instead of an inline panel: the lobby's
// content column is vertically centered and grows with the roster, so
// appending the QR row to it (the original approach) could get pushed
// outside the screen's safe area on real TV overscan — cutting off both
// the QR and the URL text, exactly what happened during Sean's testing.
// Centering this independently in its own Box guarantees it's always
// fully visible regardless of what else is on the lobby screen, and it
// can afford a much bigger, more scannable QR code too.
@Composable
private fun ChatQrModal(relayUrl: String, defaultName: String, onDismiss: () -> Unit) {
    // relayUrl is guaranteed non-placeholder here: LobbyScreen only exists
    // when ensureRelayClient() already built a real connection from it (see
    // MainActivity's onPlay/onSelect handlers), so there's nothing left to
    // validate — just the ws(s):// -> http(s):// scheme swap can fail if
    // the URL is malformed in some other way (missing host, etc).
    // defaultName rides along as a query param so scanning your own TV's
    // code pre-fills your real Plex username on the chat page instead of a
    // random "Phone 123" — still editable there, and remembered after.
    val chatUrl = remember(relayUrl, defaultName) { relayUrlToChatUrl(relayUrl, defaultName) }
    val closeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { closeFocusRequester.requestFocus() }

    Box(
        modifier = Modifier.fillMaxSize().background(AppScrim.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.widthIn(max = 560.dp).padding(vertical = 24.dp).background(AppSurfaceVariant),
        ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Join the chat", style = ShumTypography.headlineMedium, color = AppWhite)

            if (chatUrl == null) {
                Text(
                    "Chat needs a wss:// or ws:// relay URL — check Settings.",
                    color = AppWhite,
                    modifier = Modifier.padding(top = 20.dp),
                )
            } else {
                QrCodeImage(
                    content = chatUrl,
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .size(220.dp)
                        .background(AppWhite)
                        .padding(12.dp),
                )
                Text("Scan with your phone, or visit:", color = AppWhite, modifier = Modifier.padding(top = 20.dp))
                Text(chatUrl, color = AppWhite, style = ShumTypography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            }

            // Secondary/dismiss action — OutlinedButton reads as lower
            // emphasis than the solid NeonPurple buttons used for primary
            // actions elsewhere (Start, Save & continue).
            ShumOutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.padding(top = 28.dp).focusRequester(closeFocusRequester),
            ) {
                Text("Close")
            }
        }
        }
    }
}

// Derives the relay's phone-facing chat page URL from its WebSocket URL —
// same host/port/token, http(s) scheme, /chat path, plus a name param so
// the chat page can default to the scanning device's real username instead
// of a random placeholder. No app install needed on the phone side,
// matching the existing "Pair from phone" QR flow.
private fun relayUrlToChatUrl(relayUrl: String, defaultName: String): String? {
    val uri = runCatching { URI(relayUrl) }.getOrNull() ?: return null
    val scheme = when (uri.scheme) {
        "wss" -> "https"
        "ws" -> "http"
        else -> return null
    }
    val host = uri.host ?: return null
    val portPart = if (uri.port != -1) ":${uri.port}" else ""
    val params = buildList {
        uri.rawQuery?.let { add(it) }
        if (defaultName.isNotBlank()) add("name=${URLEncoder.encode(defaultName, "UTF-8")}")
    }
    val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
    return "$scheme://$host$portPart/chat$query"
}

@Composable
private fun LobbyPersonCard(username: String, avatarUrl: String?, present: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(if (present) NeonPurple.copy(alpha = 0.35f) else AppWhite.copy(alpha = 0.1f)),
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
                Text(username.take(1).uppercase(), style = ShumTypography.headlineMedium, color = AppWhite)
            }
        }
        Text(username, color = AppWhite, modifier = Modifier.padding(top = 12.dp))
    }
}
