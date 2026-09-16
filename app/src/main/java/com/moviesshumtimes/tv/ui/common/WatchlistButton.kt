package com.moviesshumtimes.tv.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.ui.kit.FocusableSurface
import com.moviesshumtimes.tv.ui.kit.ShumBorder
import com.moviesshumtimes.tv.ui.kit.ShumColors
import com.moviesshumtimes.tv.ui.kit.ShumGlow
import com.moviesshumtimes.tv.ui.kit.ShumTypography
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppDimBorder
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGradient
import com.moviesshumtimes.tv.ui.theme.NeonPurplePressed

private val WatchlistButtonSize = 44.dp

@Composable
fun WatchlistButton(isOnWatchlist: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = if (isOnWatchlist) {
        ShumColors(
            container = NeonPurple.copy(alpha = 0.3f),
            content = AppWhite,
            focusedContainer = NeonPurple,
            pressedContainer = NeonPurplePressed,
        )
    } else {
        ShumColors(
            container = Color.Transparent,
            content = AppWhite,
            focusedContainer = NeonPurple,
            pressedContainer = NeonPurplePressed,
        )
    }
    val border = ShumBorder(
        idle = if (isOnWatchlist) BorderStroke(2.dp, NeonPurpleGradient) else BorderStroke(2.dp, AppDimBorder),
        focused = BorderStroke(2.dp, NeonPurpleGradient),
    )

    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(WatchlistButtonSize),
        shape = CircleShape,
        colors = colors,
        border = border,
        glow = ShumGlow(focusedColor = NeonPurpleGlow),
    ) {
        Text(if (isOnWatchlist) "✓" else "+", style = ShumTypography.titleLarge)
    }
}
