package com.moviesshumtimes.tv.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

private const val DEFAULT_LONG_PRESS_REPEAT_THRESHOLD = 6

/**
 * Fires [onLongPress] once [key] has been held past [thresholdRepeats] auto-repeat
 * ticks, consuming that key event so the normal focus-search underneath doesn't also
 * move. A short tap (below the threshold) is left unconsumed so it still navigates
 * normally; releasing and pressing again re-arms the long-press.
 */
fun Modifier.onDpadLongPress(
    key: Key,
    thresholdRepeats: Int = DEFAULT_LONG_PRESS_REPEAT_THRESHOLD,
    onLongPress: () -> Unit,
): Modifier = composed {
    var fired by remember { mutableStateOf(false) }
    onPreviewKeyEvent { event ->
        if (event.key != key) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyUp -> {
                fired = false
                false
            }
            KeyEventType.KeyDown -> {
                if (!fired && event.nativeKeyEvent.repeatCount >= thresholdRepeats) {
                    fired = true
                    onLongPress()
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }
}
