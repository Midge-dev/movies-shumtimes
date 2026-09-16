package com.moviesshumtimes.tv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.moviesshumtimes.tv.ui.kit.ShumButton
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppScrim

@Composable
fun RemoveConfirmOverlay(message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, textAlign = TextAlign.Center, style = ShumTypography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ShumButton(
                    onClick = onConfirm,
                    compact = true,
                    modifier = Modifier.focusRequester(removeFocus),
                ) { Text("Remove") }
                ShumButton(onClick = onCancel, compact = true) { Text("Cancel") }
            }
        }
    }
}
