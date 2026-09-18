package com.reelay.tv.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.reelay.tv.ui.theme.AppWhite
import com.reelay.tv.ui.theme.NeonPurple
import com.reelay.tv.ui.theme.NeonPurpleGlow
import com.reelay.tv.ui.theme.NeonPurpleGradient
import com.reelay.tv.ui.theme.NeonPurplePressed

private val IconButtonShape = CircleShape
private val IconButtonSize = 44.dp

private val iconButtonColors = Colors(
    container = Color.Transparent,
    content = AppWhite,
    focusedContainer = NeonPurple,
    pressedContainer = NeonPurplePressed,
    disabledContent = AppWhite.copy(alpha = 0.5f),
)
private val iconButtonBorder = Border(focused = BorderStroke(2.dp, NeonPurpleGradient))
private val iconButtonGlow = Glow(focusedColor = NeonPurpleGlow)

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    border: Border = iconButtonBorder,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(IconButtonSize),
        enabled = enabled,
        shape = IconButtonShape,
        colors = iconButtonColors,
        border = border,
        glow = iconButtonGlow,
        interactionSource = interactionSource,
    ) {
        content()
    }
}
