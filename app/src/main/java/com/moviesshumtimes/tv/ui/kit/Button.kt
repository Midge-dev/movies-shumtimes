package com.moviesshumtimes.tv.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.ui.theme.AppDimBorder
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGradient
import com.moviesshumtimes.tv.ui.theme.NeonPurplePressed

// Design spec section 04: pill shape, label #FFFFFF in every state, 2dp
// stroke on focus. Filled — primary action: container transparent-ish
// (AppSurfaceVariant) at rest, solid NeonPurple + gradient border/glow on
// focus, a distinct darker pressed tone, never a plain "focused but held"
// look. No colors/border/glow params — this app's whole point is one theme,
// not a per-call-site configuration surface.
private val ButtonShape = CircleShape
private val ButtonMinWidth: Dp = 58.dp
private val ButtonMinHeight: Dp = 40.dp
private val ButtonContentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

private val filledColors = ShumColors(
    container = AppSurfaceVariant,
    content = AppWhite,
    focusedContainer = NeonPurple,
    pressedContainer = NeonPurplePressed,
    disabledContent = AppWhite.copy(alpha = 0.5f),
)
private val filledBorder = ShumBorder(focused = BorderStroke(2.dp, NeonPurpleGradient))
private val filledGlow = ShumGlow(focusedColor = NeonPurpleGlow)

@Composable
fun ShumButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = ButtonMinWidth, minHeight = ButtonMinHeight),
        enabled = enabled,
        shape = ButtonShape,
        colors = filledColors,
        border = filledBorder,
        glow = filledGlow,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(ButtonContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

// Outlined — secondary/dismiss: container never fills, only the 2dp border
// changes (idle dim gray → focused gradient); label stays #FFFFFF always.
private val outlinedColors = ShumColors(
    container = Color.Transparent,
    content = AppWhite,
    focusedContainer = Color.Transparent,
    pressedContainer = NeonPurple.copy(alpha = 0.25f),
    disabledContent = AppWhite.copy(alpha = 0.5f),
)
private val outlinedBorder = ShumBorder(
    idle = BorderStroke(2.dp, AppDimBorder),
    focused = BorderStroke(2.dp, NeonPurpleGradient),
)
private val outlinedGlow = ShumGlow(focusedColor = NeonPurpleGlow)

@Composable
fun ShumOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = ButtonMinWidth, minHeight = ButtonMinHeight),
        enabled = enabled,
        shape = ButtonShape,
        colors = outlinedColors,
        border = outlinedBorder,
        glow = outlinedGlow,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(ButtonContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
