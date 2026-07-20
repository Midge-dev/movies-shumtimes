package com.moviesshumtimes.tv.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow

// A themed replacement for the platform's default scrollbar (tv-material3
// has no scrollbar widget of its own) — draws a track the height of
// whatever it's laid out alongside (a sibling verticalScroll Column) and a
// two-tone purple thumb sized/positioned from that ScrollState, matching
// the app's NeonPurple button border/glow treatment.
@Composable
fun NeonScrollbar(scrollState: ScrollState, modifier: Modifier = Modifier) {
    val thumbBrush = Brush.verticalGradient(listOf(NeonPurpleGlow, NeonPurple))
    Canvas(modifier = modifier.fillMaxHeight().width(4.dp)) {
        val cornerRadius = CornerRadius(size.width / 2, size.width / 2)
        drawRoundRect(color = AppSurfaceVariant, cornerRadius = cornerRadius)

        val maxValue = scrollState.maxValue
        if (maxValue > 0) {
            val totalExtent = size.height + maxValue
            val thumbHeight = (size.height * size.height / totalExtent).coerceAtLeast(24f)
            val thumbTop = (size.height - thumbHeight) * (scrollState.value.toFloat() / maxValue)
            drawRoundRect(
                brush = thumbBrush,
                topLeft = Offset(0f, thumbTop),
                size = Size(size.width, thumbHeight),
                cornerRadius = cornerRadius,
            )
        }
    }
}
