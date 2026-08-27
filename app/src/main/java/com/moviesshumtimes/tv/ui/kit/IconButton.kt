package com.moviesshumtimes.tv.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGradient
import com.moviesshumtimes.tv.ui.theme.NeonPurplePressed

// Player transport controls only — transparent at rest so icons float over
// video with no visible container until focused; 44dp minimum hit target
// per the design spec's "Icon — player transport only" note.
private val IconButtonShape = CircleShape
private val IconButtonSize = 44.dp

private val iconButtonColors = ShumColors(
    container = Color.Transparent,
    content = AppWhite,
    focusedContainer = NeonPurple,
    pressedContainer = NeonPurplePressed,
    disabledContent = AppWhite.copy(alpha = 0.5f),
)
private val iconButtonBorder = ShumBorder(focused = BorderStroke(2.dp, NeonPurpleGradient))
private val iconButtonGlow = ShumGlow(focusedColor = NeonPurpleGlow)

@Composable
fun ShumIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(IconButtonSize),
        enabled = enabled,
        shape = IconButtonShape,
        colors = iconButtonColors,
        border = iconButtonBorder,
        glow = iconButtonGlow,
        interactionSource = interactionSource,
    ) {
        content()
    }
}
