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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.data.settings.ChatOverlayCorner
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.sync.ChatMessage
import com.moviesshumtimes.tv.ui.theme.AppScrim
import com.moviesshumtimes.tv.ui.theme.AppWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

private const val MESSAGE_VISIBLE_MS = 6_000L
private const val FADE_OUT_MS = 300L
private const val MAX_VISIBLE = 4

@Composable
fun ChatOverlay(
    messages: SharedFlow<ChatMessage>,
    corner: ChatOverlayCorner = ChatOverlayCorner.BOTTOM_END,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }

    LaunchedEffect(messages) {
        messages.collect { message -> visible = (visible + message).takeLast(MAX_VISIBLE) }
    }

    val isTop = corner == ChatOverlayCorner.TOP_START || corner == ChatOverlayCorner.TOP_END
    val isStart = corner == ChatOverlayCorner.TOP_START || corner == ChatOverlayCorner.BOTTOM_START
    val ordered = if (isTop) visible.asReversed() else visible
    val textAlign = if (isStart) TextAlign.Start else TextAlign.End

    Column(
        modifier = modifier,
        horizontalAlignment = if (isStart) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (message in ordered) {
            key(message.receivedAtMs, message.username, message.text) {
                ChatBubble(message, textAlign = textAlign, onExpired = { visible = visible - message })
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, textAlign: TextAlign, onExpired: () -> Unit) {
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
            color = AppWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .background(AppScrim.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
