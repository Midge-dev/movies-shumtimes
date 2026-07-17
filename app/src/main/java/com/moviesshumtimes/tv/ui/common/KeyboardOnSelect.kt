package com.moviesshumtimes.tv.ui.common

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow

// BasicTextField shows the software keyboard as soon as it gains focus, which
// on a D-pad means every field along a navigation path flashes the IME open
// just from being passed over. `readOnly` is what actually suppresses that
// auto-show internally (hiding it after the fact races the field's own
// focus-triggered effect and loses); this field only drops readOnly, and
// therefore only shows the keyboard, on an explicit select press — matching
// a remote's "click to type" model.
@Composable
fun ClickToTypeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    singleLine: Boolean = false,
    decorationBox: @Composable (@Composable () -> Unit) -> Unit = { innerTextField -> innerTextField() },
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var editingEnabled by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = !editingEnabled,
        textStyle = textStyle,
        singleLine = singleLine,
        decorationBox = decorationBox,
        cursorBrush = SolidColor(NeonPurple),
        // The border sits outside the caller's own background/padding
        // modifiers so it outlines the whole field rather than just its
        // padded content area — it's the only cue a D-pad user gets that
        // this field is focused and ready for a select press to open it.
        // Always present (dim gray unfocused, purple gradient focused)
        // rather than conditionally added/removed, so an unfocused-vs-
        // never-rendered mixup is visible at a glance instead of requiring
        // logcat: if it never even shows dim gray, rendering is the
        // problem; if it shows gray but never turns purple, isFocused
        // isn't updating.
        modifier = Modifier
            .border(
                BorderStroke(
                    3.dp,
                    if (isFocused) {
                        Brush.linearGradient(listOf(NeonPurpleGlow, NeonPurple))
                    } else {
                        Brush.linearGradient(listOf(Color.DarkGray, Color.DarkGray))
                    },
                ),
            )
            .then(modifier)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                Log.d("ClickToTypeTextField", "onFocusChanged isFocused=${state.isFocused}")
                if (!state.isFocused) {
                    editingEnabled = false
                    keyboardController?.hide()
                }
            }
            .onPreviewKeyEvent { event ->
                if (!editingEnabled && event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    editingEnabled = true
                    keyboardController?.show()
                    true
                } else {
                    false
                }
            },
    )
}
