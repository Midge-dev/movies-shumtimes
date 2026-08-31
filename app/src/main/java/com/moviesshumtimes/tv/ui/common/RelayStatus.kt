package com.moviesshumtimes.tv.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.sync.ConnectionState
import com.moviesshumtimes.tv.ui.kit.ShumOutlinedButton
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppOnSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import kotlinx.coroutines.delay

sealed interface RelayStatus {
    data object Silent : RelayStatus
    data object Waking : RelayStatus
    data object ConnectedConfirm : RelayStatus
    data object DotOnly : RelayStatus
    data object Reconnecting : RelayStatus
    data object Failed : RelayStatus
}

private val AmberGrey = Color(0xFFB89A6A)

private const val WAKING_AT_MS = 2_000L
private const val FAILED_AT_MS = 75_000L
private const val CONNECTED_CONFIRM_VISIBLE_MS = 2_000L

@Composable
fun rememberRelayStatus(connectionState: ConnectionState): RelayStatus {
    var status by remember { mutableStateOf<RelayStatus>(RelayStatus.Silent) }
    var everConnected by remember { mutableStateOf(false) }
    val isTryingToConnect = connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.RECONNECTING

    LaunchedEffect(isTryingToConnect, everConnected) {
        if (!isTryingToConnect) return@LaunchedEffect
        if (everConnected) {
            status = RelayStatus.Reconnecting
            return@LaunchedEffect
        }
        status = RelayStatus.Silent
        delay(WAKING_AT_MS)
        status = RelayStatus.Waking
        delay(FAILED_AT_MS - WAKING_AT_MS)
        status = RelayStatus.Failed
    }
    LaunchedEffect(connectionState) {
        when (connectionState) {
            ConnectionState.CONNECTED -> {
                everConnected = true
                status = RelayStatus.ConnectedConfirm
                delay(CONNECTED_CONFIRM_VISIBLE_MS)
                status = RelayStatus.DotOnly
            }
            ConnectionState.ROOM_FULL, ConnectionState.ROOM_NOT_FOUND -> status = RelayStatus.Failed
            else -> {}
        }
    }
    return status
}

@Composable
fun RelayStatusDot(status: RelayStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        RelayStatus.Silent, RelayStatus.Failed -> AppOnSurfaceVariant.copy(alpha = 0.3f)
        RelayStatus.Waking -> NeonPurpleGlow
        RelayStatus.ConnectedConfirm, RelayStatus.DotOnly -> NeonPurple
        RelayStatus.Reconnecting -> AmberGrey
    }
    Canvas(modifier = modifier.size(8.dp)) { drawCircle(color = color) }
}

@Composable
fun RelayStatusLine(
    status: RelayStatus,
    relayNickname: String,
    onRetry: () -> Unit,
    onHostOnAnother: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    when (status) {
        RelayStatus.DotOnly -> {}
        RelayStatus.Silent -> IndeterminateSweep(modifier)
        RelayStatus.Waking -> Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Spinner()
                Text("Waking up the relay")
            }
            Text(
                "This can take up to a minute if nobody has used it in a while. Playback works — you'll be synced when it connects.",
                color = AppOnSurfaceVariant,
            )
        }
        RelayStatus.ConnectedConfirm -> AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
                Canvas(modifier = Modifier.size(20.dp)) { drawCircle(color = NeonPurple) }
                Text("Connected — room is live")
            }
        }
        RelayStatus.Reconnecting -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier,
        ) {
            RelayStatusDot(status)
            Text("Reconnecting", color = AppOnSurfaceVariant)
        }
        RelayStatus.Failed -> Column(
            modifier = modifier.focusProperties { onExit = { cancelFocusChange() } },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Can't reach $relayNickname")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ShumOutlinedButton(onClick = onRetry) { Text("Retry") }
                if (onHostOnAnother != null) {
                    ShumOutlinedButton(onClick = onHostOnAnother) { Text("Host on another relay") }
                }
            }
        }
    }
}

@Composable
private fun Spinner() {
    val transition = rememberInfiniteTransition(label = "relayStatusSpinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawArc(color = NeonPurpleGlow.copy(alpha = 0.5f), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
        drawArc(color = NeonPurpleGlow, startAngle = rotation, sweepAngle = 90f, useCenter = false, style = stroke)
    }
}

@Composable
private fun IndeterminateSweep(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "relaySweep")
    val position by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "sweepPosition",
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(AppSurfaceVariant.copy(alpha = 0.6f)),
    ) {
        val segmentWidth = size.width * 0.38f
        val x = (size.width + segmentWidth) * position - segmentWidth
        drawRect(
            brush = Brush.horizontalGradient(listOf(NeonPurpleGlow.copy(alpha = 0f), NeonPurpleGlow)),
            topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
            size = androidx.compose.ui.geometry.Size(segmentWidth, size.height),
        )
    }
}
