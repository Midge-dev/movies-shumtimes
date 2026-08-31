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

// The shared low-level building block every kit component (Button, Card,
// ListItem, FilterChip) is built from — owns interactionSource-driven
// focused/pressed/selected/disabled state and turns it into a container
// color, content color, border, and glow, then draws them. D-pad center/
// enter → click is plain Modifier.clickable/combinedClickable, which
// already works for TV without any of this — HomeScreen's
// ContinueWatchingPoster proved that out before this file existed.

// Per-state color bag, same shape as tv-material3's ButtonColors/ListItemColors
// (deliberately — this is a drop-in mental model for anyone who worked with
// those), just owned by us now instead of borrowed.
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

// Hand-drawn, not Android's native elevation shadow — Modifier.shadow's
// ambient/spot tint is a platform compositor effect we don't fully control,
// and changing its elevation turned out to produce no visible difference at
// all on real hardware. radius/alpha here are plain numbers matching the
// design spec's focused box-shadow almost exactly (0 4px 14px
// rgba(231,149,252,.22)) and reliably do what they say on every device —
// see drawGlow below. One default here cascades to every ShumGlow() call
// site (Button, IconButton, Card, FilterChip, Switch) uniformly, so this is
// the one place to retune it again later.
data class ShumGlow(val focusedColor: Color? = null, val radius: Dp = 14.dp, val alpha: Float = 0.22f)

// Layers the surface's own shape outward at shrinking size/growing alpha to
// fake a soft blur — Compose's real blur (Modifier.blur, RenderEffect-based)
// needs API 31+ and this app's minSdk is 26, so a drawn approximation is the
// only thing that works everywhere. Each layer is faint on its own; they
// compound near the shape's edge into something that reads as a glow
// without ever needing platform blur support.
// Not private: HomeScreen's RoomCard hand-rolls its own card-level focus
// tracking (the card is a focusGroup of several independent buttons now, not
// a single FocusableSurface) but still needs the exact same glow, not an
// approximation — see its own doc comment.
internal fun Modifier.drawGlow(shape: Shape, color: Color, radius: Dp, alpha: Float, layers: Int = 14): Modifier =
    drawBehind {
        val radiusPx = radius.toPx()
        for (i in layers downTo 1) {
            val t = i.toFloat() / layers
            val expand = radiusPx * t
            // Each shell is drawn as a flat-alpha fill, largest first, so a
            // point at distance d from the surface's own edge ends up
            // covered by every shell whose expand >= d — composited "over"
            // blending across that many stacked shells is what actually
            // builds the visible falloff, not any single shell's own alpha.
            // 14 thin shells (not 3) is what turns that into a smooth
            // gradient instead of 1-2 visible concentric rings; a squared
            // falloff on top front-loads the fade near the edge (a real
            // glow reads brightest close in, not evenly ramped to the full
            // radius), and the fitted peak constant keeps the *composited*
            // brightness at the surface's own edge matching `alpha`, not
            // just each shell's own small contribution.
            val layerAlpha = (alpha * (1f - t) * (1f - t) * 0.4f).coerceIn(0f, 1f)
            if (layerAlpha <= 0f) continue
            // A rounded shape's corner radius has to grow by the same
            // `expand` as the shell itself, not stay fixed — otherwise a
            // small corner radius on an increasingly bigger rectangle reads
            // as squarer at each successive shell, so the outer (most
            // visible) part of the glow looks corner-cut instead of round
            // like the card it surrounds. CircleShape and other shapes
            // don't have this problem (a circle offset outward is still a
            // circle), so this only special-cases RoundedCornerShape.
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
