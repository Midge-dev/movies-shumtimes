package com.moviesshumtimes.tv.ui.kit

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.AppWhite
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGlow
import com.moviesshumtimes.tv.ui.theme.NeonPurpleGradient

private val CardShape = RoundedCornerShape(8.dp)

private val cardColors = ShumColors(container = AppSurfaceVariant, content = AppWhite)
private val cardBorder = ShumBorder(focused = BorderStroke(2.dp, NeonPurpleGradient))
private val cardGlow = ShumGlow(focusedColor = NeonPurpleGlow)

private const val DEFAULT_FOCUS_SCALE = 1.06f

@Composable
fun ShumCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    focusScale: Float = DEFAULT_FOCUS_SCALE,
    shape: Shape = CardShape,
    content: @Composable () -> Unit,
) {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by actualInteractionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusScale else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "cardFocusScale",
    )
    FocusableSurface(
        onClick = onClick,
        enabled = enabled,
        onLongClick = onLongClick,
        modifier = modifier
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = shape,
        colors = cardColors,
        border = cardBorder,
        glow = cardGlow,
        interactionSource = actualInteractionSource,
    ) {
        content()
    }
}

@Composable
fun ShumCardContainer(
    imageCard: @Composable (interactionSource: MutableInteractionSource) -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier = modifier.fillMaxWidth()) {
        imageCard(interactionSource)
        title()
    }
}
