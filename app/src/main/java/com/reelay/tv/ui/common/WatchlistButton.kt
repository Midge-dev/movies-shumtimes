package com.reelay.tv.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.reelay.tv.ui.kit.FocusableSurface
import com.reelay.tv.ui.kit.Border
import com.reelay.tv.ui.kit.Colors
import com.reelay.tv.ui.kit.Glow
import com.reelay.tv.ui.kit.Typography
import com.reelay.tv.ui.kit.Text
import com.reelay.tv.ui.theme.AppDimBorder
import com.reelay.tv.ui.theme.AppWhite
import com.reelay.tv.ui.theme.NeonPurple
import com.reelay.tv.ui.theme.NeonPurpleGlow
import com.reelay.tv.ui.theme.NeonPurpleGradient
import com.reelay.tv.ui.theme.NeonPurplePressed

private val WatchlistButtonSize = 44.dp

@Composable
fun WatchlistButton(isOnWatchlist: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = if (isOnWatchlist) {
        Colors(
            container = NeonPurple.copy(alpha = 0.3f),
            content = AppWhite,
            focusedContainer = NeonPurple,
            pressedContainer = NeonPurplePressed,
        )
    } else {
        Colors(
            container = Color.Transparent,
            content = AppWhite,
            focusedContainer = NeonPurple,
            pressedContainer = NeonPurplePressed,
        )
    }
    val border = Border(
        idle = if (isOnWatchlist) BorderStroke(2.dp, NeonPurpleGradient) else BorderStroke(2.dp, AppDimBorder),
        focused = BorderStroke(2.dp, NeonPurpleGradient),
    )

    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(WatchlistButtonSize),
        shape = CircleShape,
        colors = colors,
        border = border,
        glow = Glow(focusedColor = NeonPurpleGlow),
    ) {
        Text(if (isOnWatchlist) "✓" else "+", style = Typography.titleLarge)
    }
}
