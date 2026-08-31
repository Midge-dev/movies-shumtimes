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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.ui.theme.AppDimBorder
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow

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
        modifier = Modifier
            .border(
                BorderStroke(
                    3.dp,
                    if (isFocused) {
                        Brush.linearGradient(listOf(NeonPurpleGlow, NeonPurple))
                    } else {
                        Brush.linearGradient(listOf(AppDimBorder, AppDimBorder))
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
