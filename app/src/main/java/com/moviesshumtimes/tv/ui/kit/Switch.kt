package com.moviesshumtimes.tv.ui.kit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurple
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGradient

private val TrackWidth = 44.dp
private val TrackHeight = 24.dp
private val ThumbSize = 18.dp
private val ThumbInset = 3.dp

@Composable
fun ShumSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = ShumColors(
        container = if (checked) NeonPurple else AppSurfaceVariant,
        content = AppWhite,
    )
    val border = ShumBorder(focused = BorderStroke(2.dp, NeonPurpleGradient))
    val glow = ShumGlow(focusedColor = NeonPurpleGlow)
    val thumbOffset by animateDpAsState(if (checked) TrackWidth - ThumbSize - ThumbInset else ThumbInset, label = "switchThumb")

    FocusableSurface(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.size(TrackWidth, TrackHeight),
        shape = CircleShape,
        colors = colors,
        border = border,
        glow = glow,
        interactionSource = interactionSource,
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = ThumbInset)
                .offset(x = thumbOffset)
                .size(ThumbSize)
                .clip(CircleShape)
                .background(AppWhite),
        )
    }
}
