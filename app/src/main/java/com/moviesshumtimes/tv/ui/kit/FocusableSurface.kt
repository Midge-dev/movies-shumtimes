package com.moviesshumtimes.tv.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ShumColors(
    val container: Color,
    val content: Color,
    val focusedContainer: Color = container,
    val focusedContent: Color = content,
    val pressedContainer: Color = focusedContainer,
    val pressedContent: Color = focusedContent,
    val selectedContainer: Color = container,
    val selectedContent: Color = content,
    val disabledContainer: Color = container,
    val disabledContent: Color = content,
)

data class ShumBorder(val idle: BorderStroke? = null, val focused: BorderStroke? = null)

data class ShumGlow(val focusedColor: Color? = null, val radius: Dp = 14.dp, val alpha: Float = 0.22f)

internal fun Modifier.drawGlow(shape: Shape, color: Color, radius: Dp, alpha: Float, layers: Int = 14): Modifier =
    drawBehind {
        val radiusPx = radius.toPx()
        for (i in layers downTo 1) {
            val t = i.toFloat() / layers
            val expand = radiusPx * t
            val layerAlpha = (alpha * (1f - t) * (1f - t) * 0.4f).coerceIn(0f, 1f)
            if (layerAlpha <= 0f) continue
            val expandedShape = if (shape is RoundedCornerShape) {
                RoundedCornerShape(shape.topStart.toPx(size, this) + expand)
            } else {
                shape
            }
            val outline = expandedShape.createOutline(
                Size(size.width + expand * 2f, size.height + expand * 2f),
                layoutDirection,
                this,
            )
            translate(left = -expand, top = -expand) {
                drawOutline(outline, color = color.copy(alpha = layerAlpha))
            }
        }
    }

@Composable
fun FocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    shape: Shape,
    colors: ShumColors,
    border: ShumBorder = ShumBorder(),
    glow: ShumGlow = ShumGlow(),
    interactionSource: MutableInteractionSource? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by actualInteractionSource.collectIsFocusedAsState()
    val isPressed by actualInteractionSource.collectIsPressedAsState()

    val containerColor = when {
        !enabled -> colors.disabledContainer
        isPressed -> colors.pressedContainer
        isFocused -> colors.focusedContainer
        selected -> colors.selectedContainer
        else -> colors.container
    }
    val contentColor = when {
        !enabled -> colors.disabledContent
        isPressed -> colors.pressedContent
        isFocused -> colors.focusedContent
        selected -> colors.selectedContent
        else -> colors.content
    }
    val activeBorder = if (isFocused) border.focused else border.idle
    val glowColor = if (isFocused) glow.focusedColor else null

    Box(
        modifier = modifier
            .let { m ->
                if (glowColor != null) {
                    m.drawGlow(shape, glowColor, glow.radius, glow.alpha)
                } else {
                    m
                }
            }
            .clip(shape)
            .background(containerColor, shape)
            .let { m -> activeBorder?.let { m.border(it, shape) } ?: m }
            .let { m ->
                if (onLongClick != null) {
                    m.combinedClickable(
                        interactionSource = actualInteractionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    m.clickable(
                        interactionSource = actualInteractionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick,
                    )
                }
            },
        contentAlignment = contentAlignment,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
