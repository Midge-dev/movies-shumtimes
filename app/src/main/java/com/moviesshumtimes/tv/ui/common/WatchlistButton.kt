package com.moviesshumtimes.tv.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.ui.kit.FocusableSurface
import com.moviesshumtimes.tv.ui.kit.ShumBorder
import com.moviesshumtimes.tv.ui.kit.ShumColors
import com.moviesshumtimes.tv.ui.kit.ShumGlow
import com.moviesshumtimes.tv.ui.kit.Text
import com.moviesshumtimes.tv.ui.theme.AppDimBorder
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGradient
import com.moviesshumtimes.tv.ui.theme.NeonPurplePressed

private val WatchlistButtonMinSize = 68.dp

/**
 * Circular add/remove-watchlist toggle. Icon-only (68dp circle) at rest; widens into a
 * labeled pill while focused so a couch user never has to guess what the icon means.
 */
@Composable
fun WatchlistButton(isOnWatchlist: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }

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
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .defaultMinSize(minWidth = WatchlistButtonMinSize, minHeight = WatchlistButtonMinSize)
            .animateContentSize(),
        shape = CircleShape,
        colors = colors,
        border = border,
        glow = ShumGlow(focusedColor = NeonPurpleGlow),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (focused) 24.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (isOnWatchlist) "✓" else "+")
            if (focused) Text("Watchlist")
        }
    }
}
