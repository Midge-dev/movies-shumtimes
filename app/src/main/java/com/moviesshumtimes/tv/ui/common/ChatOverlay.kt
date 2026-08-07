package com.moviesshumtimes.tv.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.moviesshumtimes.tv.sync.ChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

private const val MESSAGE_VISIBLE_MS = 6_000L
private const val FADE_OUT_MS = 300L
private const val MAX_VISIBLE = 4

// Renders incoming chat as fading toasts, similar to a game's area chat —
// messages appear briefly and clear themselves rather than accumulating
// into a persistent log the viewer has to manage with no keyboard on a TV.
@Composable
fun ChatOverlay(messages: SharedFlow<ChatMessage>, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }

    LaunchedEffect(messages) {
        messages.collect { message -> visible = (visible + message).takeLast(MAX_VISIBLE) }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (message in visible) {
            key(message.receivedAtMs, message.username) {
                ChatBubble(message, onExpired = { visible = visible - message })
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, onExpired: () -> Unit) {
    var shown by remember { mutableStateOf(true) }
    LaunchedEffect(message) {
        delay(MESSAGE_VISIBLE_MS)
        shown = false
        delay(FADE_OUT_MS)
        onExpired()
    }
    AnimatedVisibility(visible = shown, enter = fadeIn(), exit = fadeOut()) {
        Text(
            text = "${message.username}: ${message.text}",
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
