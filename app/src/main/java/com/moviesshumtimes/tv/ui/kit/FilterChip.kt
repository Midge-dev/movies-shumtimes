package com.moviesshumtimes.tv.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.ui.theme.AppOnSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGradient

private val ChipShape = RoundedCornerShape(50)
private val ChipContentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)

private val chipColors = ShumColors(
    container = AppSurfaceVariant,
    content = AppOnSurfaceVariant,
    focusedContainer = NeonPurple,
    focusedContent = AppWhite,
    selectedContainer = AppSurfaceVariant,
    selectedContent = AppWhite,
    disabledContent = AppOnSurfaceVariant.copy(alpha = 0.5f),
)
private val chipBorder = ShumBorder(focused = BorderStroke(2.dp, NeonPurpleGradient))
private val chipGlow = ShumGlow(focusedColor = NeonPurpleGlow)

@Composable
fun ShumFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        shape = ChipShape,
        colors = chipColors,
        border = chipBorder,
        glow = chipGlow,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(ChipContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
