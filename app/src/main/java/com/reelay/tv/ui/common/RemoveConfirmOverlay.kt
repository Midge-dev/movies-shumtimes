package com.reelay.tv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelay.tv.ui.kit.Button
import com.reelay.tv.ui.kit.Typography
import com.reelay.tv.ui.kit.Text
import com.reelay.tv.ui.theme.AppScrim

@Composable
fun RemoveConfirmOverlay(message: String, onConfirm: () -> Unit, onCancel: () -> Unit, compact: Boolean = false) {
    val removeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { removeFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppScrim.copy(alpha = 0.85f))
            .focusGroup()
            .focusProperties { onExit = { cancelFocusChange() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = if (compact) Modifier.padding(horizontal = 8.dp) else Modifier,
        ) {
            Text(
                message,
                textAlign = TextAlign.Center,
                style = Typography.bodyLarge,
                modifier = if (compact) Modifier.fillMaxWidth() else Modifier,
                maxLines = if (compact) 3 else Int.MAX_VALUE,
                autoSize = if (compact) TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 16.sp) else null,
            )
            if (compact) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onConfirm,
                        compact = true,
                        modifier = Modifier.focusRequester(removeFocus),
                    ) { Text("Remove") }
                    Button(onClick = onCancel, compact = true) { Text("Cancel") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = onConfirm,
                        compact = true,
                        modifier = Modifier.focusRequester(removeFocus),
                    ) { Text("Remove") }
                    Button(onClick = onCancel, compact = true) { Text("Cancel") }
                }
            }
        }
    }
}
