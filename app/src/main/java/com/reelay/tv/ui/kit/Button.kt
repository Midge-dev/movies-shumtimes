package com.reelay.tv.ui.kit

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
import com.reelay.tv.ui.theme.AppDimBorder
import com.reelay.tv.ui.theme.AppSurfaceVariant
import com.reelay.tv.ui.theme.AppWhite
import com.reelay.tv.ui.theme.NeonPurple
import com.reelay.tv.ui.theme.NeonPurpleGlow
import com.reelay.tv.ui.theme.NeonPurpleGradient
import com.reelay.tv.ui.theme.NeonPurplePressed

private val ButtonShape = CircleShape
private val ButtonMinWidth: Dp = 58.dp
private val ButtonMinHeight: Dp = 40.dp
private val ButtonContentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

private val ButtonCompactMinHeight: Dp = 32.dp
private val ButtonCompactContentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

private val filledColors = Colors(
    container = AppSurfaceVariant,
    content = AppWhite,
    focusedContainer = NeonPurple,
    pressedContainer = NeonPurplePressed,
    disabledContent = AppWhite.copy(alpha = 0.5f),
)
private val filledBorder = Border(focused = BorderStroke(2.dp, NeonPurpleGradient))
private val filledGlow = Glow(focusedColor = NeonPurpleGlow)

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.sizeIn(
            minWidth = if (compact) 0.dp else ButtonMinWidth,
            minHeight = if (compact) ButtonCompactMinHeight else ButtonMinHeight,
        ),
        enabled = enabled,
        shape = ButtonShape,
        colors = filledColors,
        border = filledBorder,
        glow = filledGlow,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(if (compact) ButtonCompactContentPadding else ButtonContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

private val outlinedColors = Colors(
    container = Color.Transparent,
    content = AppWhite,
    focusedContainer = Color.Transparent,
    pressedContainer = NeonPurple.copy(alpha = 0.25f),
    disabledContent = AppWhite.copy(alpha = 0.5f),
)
private val outlinedBorder = Border(
    idle = BorderStroke(2.dp, AppDimBorder),
    focused = BorderStroke(2.dp, NeonPurpleGradient),
)
private val outlinedGlow = Glow(focusedColor = NeonPurpleGlow)

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.sizeIn(
            minWidth = if (compact) 0.dp else ButtonMinWidth,
            minHeight = if (compact) ButtonCompactMinHeight else ButtonMinHeight,
        ),
        enabled = enabled,
        shape = ButtonShape,
        colors = outlinedColors,
        border = outlinedBorder,
        glow = outlinedGlow,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(if (compact) ButtonCompactContentPadding else ButtonContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
