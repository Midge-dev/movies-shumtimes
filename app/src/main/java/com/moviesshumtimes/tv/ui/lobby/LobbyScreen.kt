package com.moviesshumtimes.tv.ui.lobby

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import coil3.compose.AsyncImage
import com.moviesshumtimes.tv.data.plex.PlexImageUrl
import com.moviesshumtimes.tv.data.plex.PlexMovieDetail
import com.moviesshumtimes.tv.data.plex.PlexServer
import com.moviesshumtimes.tv.data.settings.ChatOverlayCorner
import com.moviesshumtimes.tv.data.settings.appSettingsStore
import com.moviesshumtimes.tv.sync.ChatMessage
import com.moviesshumtimes.tv.sync.ConnectionState
import com.moviesshumtimes.tv.sync.RelayClient
import com.moviesshumtimes.tv.sync.RelayEvent
import com.moviesshumtimes.tv.sync.relayHttpUrl
import com.moviesshumtimes.tv.sync.toChatMessage
import com.moviesshumtimes.tv.ui.common.ChatOverlay
import com.moviesshumtimes.tv.ui.common.QrCodeImage
import com.moviesshumtimes.tv.ui.common.RelayStatusDot
import com.moviesshumtimes.tv.ui.common.RelayStatusLine
import com.moviesshumtimes.tv.ui.common.ShumArtwork
import com.moviesshumtimes.tv.ui.common.WatchTogetherIcon
import com.moviesshumtimes.tv.ui.common.rememberRelayStatus
import com.moviesshumtimes.tv.ui.kit.ShumButton
import com.moviesshumtimes.tv.ui.kit.ShumOutlinedButton
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppBackground
import com.moviesshumtimes.tv.ui.theme.AppScrim
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import java.net.URLEncoder

private const val PRESENCE_INTERVAL_MS = 3_000L
private const val ROSTER_STALE_MS = PRESENCE_INTERVAL_MS * 3
private const val ROOM_SEAT_CAP = 8

private data class RosterEntry(val username: String, val avatarUrl: String?, val lastSeenMs: Long)

@Composable
fun LobbyScreen(
    server: PlexServer,
    detail: PlexMovieDetail,
    localUsername: String,
    localAvatarUrl: String?,
    hostName: String,
    relayNickname: String,
    relay: RelayClient,
    onHostOnAnother: (() -> Unit)?,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val connectionState by relay.connectionState.collectAsState()
    val relayStatus = rememberRelayStatus(connectionState)
    val mySeatIndex by relay.seatIndex.collectAsState()
    val roomId by relay.roomId.collectAsState()
    val isHost = mySeatIndex == 0
    var roster by remember { mutableStateOf<Map<String, RosterEntry>>(emptyMap()) }
    var showChatModal by remember { mutableStateOf(false) }
    val chatMessages = remember { MutableSharedFlow<ChatMessage>(extraBufferCapacity = 16) }

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
    BackHandler(enabled = showChatModal) { showChatModal = false }

    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.ROOM_CLOSED || connectionState == ConnectionState.ROOM_NOT_FOUND) {
            onBack()
        }
    }

    LaunchedEffect(relay, connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            while (true) {
                relay.send(RelayEvent(kind = "presence", fromPeerId = relay.myPeerId, username = localUsername, avatarUrl = localAvatarUrl))
                delay(PRESENCE_INTERVAL_MS)
            }
        }
    }

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
        ShumArtwork(
            model = PlexImageUrl.of(server, detail.art ?: detail.thumb),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(AppBackground.copy(alpha = 0.62f), AppBackground.copy(alpha = 0.92f)),
                    ),
                ),
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WatchTogetherIcon()
                Text(detail.title, style = ShumTypography.displaySmall, color = AppWhite)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 8.dp, bottom = 48.dp),
            ) {
                RelayStatusDot(status = relayStatus)
                Text(relayNickname, color = AppWhite.copy(alpha = 0.7f))
            }

            val others = remember(roster, isHost, localUsername, localAvatarUrl) {
                buildList {
                    if (!isHost) add(RosterEntry(localUsername, localAvatarUrl, Long.MAX_VALUE))
                    addAll(roster.values.sortedBy { it.lastSeenMs })
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LobbyPersonCard(
                    name = hostName,
                    avatarUrl = if (isHost) localAvatarUrl else null,
                    subtitle = "host",
                )
                for (entry in others) {
                    LobbyPersonCard(name = entry.username, avatarUrl = entry.avatarUrl)
                }
                if (others.size + 1 < ROOM_SEAT_CAP) {
                    EmptySeat()
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

        Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.6f).padding(32.dp)) {
            RelayStatusLine(
                status = relayStatus,
                relayNickname = relayNickname,
                onRetry = { relay.retryNow() },
                onHostOnAnother = onHostOnAnother,
            )
        }

        if (showChatModal) {
            ChatQrModal(relayUrl = relay.relayUrl, roomId = roomId, defaultName = localUsername, onDismiss = { showChatModal = false })
        }
    }
}

@Composable
private fun ChatQrModal(relayUrl: String, roomId: String?, defaultName: String, onDismiss: () -> Unit) {
    val chatUrl = remember(relayUrl, roomId, defaultName) {
        roomId?.let { relayUrlToChatUrl(relayUrl, it, defaultName) }
    }
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
                    "Still connecting to the room — try again in a moment.",
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

private fun relayUrlToChatUrl(relayUrl: String, roomId: String, defaultName: String): String? {
    val parsed = relayHttpUrl(relayUrl) ?: return null
    val params = buildList {
        parsed.query?.let { add(it) }
        add("room=$roomId")
        if (defaultName.isNotBlank()) add("name=${URLEncoder.encode(defaultName, "UTF-8")}")
    }
    return "${parsed.base}/chat?${params.joinToString("&")}"
}

@Composable
private fun LobbyPersonCard(name: String, avatarUrl: String?, subtitle: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(NeonPurple.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(name.take(1).uppercase(), style = ShumTypography.headlineMedium, color = AppWhite)
            }
        }
        Text(name, color = AppWhite, modifier = Modifier.padding(top = 12.dp))
        if (subtitle != null) {
            Text(subtitle, color = AppWhite.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun EmptySeat() {
    Canvas(modifier = Modifier.size(96.dp)) {
        drawCircle(color = AppWhite.copy(alpha = 0.06f))
        drawCircle(
            color = AppWhite.copy(alpha = 0.18f),
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
        )
    }
}
