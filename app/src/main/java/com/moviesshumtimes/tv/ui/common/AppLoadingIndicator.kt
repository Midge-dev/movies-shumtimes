package com.moviesshumtimes.tv.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.NeonPurple

// Design spec 08 (Modals, QR & system states): a plain rotating ring, not
// Material's shape-morphing blob — track AppSurfaceVariant, a 90° accent
// arc in NeonPurple, 4dp stroke, one full turn per second. Hand-rolled since
// tv-material3 has no loading indicator of its own and the base (non-TV)
// Material3 ContainedLoadingIndicator this used to borrow reads as a
// recognizably different, non-native component next to the rest of the app.
private const val STROKE_WIDTH_DP = 4
private const val SWEEP_DEGREES = 90f

@Composable
fun AppLoadingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "AppLoadingIndicator")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )
    Canvas(modifier = modifier.size(56.dp)) {
        val stroke = Stroke(width = STROKE_WIDTH_DP.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = AppSurfaceVariant,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke,
        )
        drawArc(
            color = NeonPurple,
            startAngle = rotation,
            sweepAngle = SWEEP_DEGREES,
            useCenter = false,
            style = stroke,
        )
    }
}
