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

// Design spec section 05: posters/cards never change container fill on
// focus, only the gradient border + glow — unlike buttons, which solid-fill
// with NeonPurple. radius.sm = 8dp.
private val CardShape = RoundedCornerShape(8.dp)

private val cardColors = ShumColors(container = AppSurfaceVariant, content = AppWhite)
private val cardBorder = ShumBorder(focused = BorderStroke(2.dp, NeonPurpleGradient))
private val cardGlow = ShumGlow(focusedColor = NeonPurpleGlow)

// Design spec "Focus magnification": every browsable card grows toward the
// viewer on focus — scale only, border/glow unchanged, title (rendered
// separately by ShumCardContainer) never moves. 1.06 targets ~10dp of growth
// on the 160dp-wide cards every ShumCard call site in this app happens to
// use (posters, season/episode thumbnails); a wider card wanting the same
// ~10dp absolute growth would pass a smaller factor here instead of 1.06.
private const val DEFAULT_FOCUS_SCALE = 1.06f

@Composable
fun ShumCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    focusScale: Float = DEFAULT_FOCUS_SCALE,
    // CardShape (radius.sm) for every poster/tile in the app; a cast/crew
    // avatar passes CircleShape instead, reusing the same focus/glow/scale
    // behavior rather than re-deriving it.
    shape: Shape = CardShape,
    content: @Composable () -> Unit,
) {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by actualInteractionSource.collectIsFocusedAsState()
    // Spring, not a fixed-duration tween, so the settle feels like a physical
    // nudge forward rather than a mechanical resize — the slight overshoot on
    // the way in is intentional. Never dips below 1f on press; a press stays
    // a colour change only (FocusableSurface's own pressed-state handling),
    // so nothing ever looks like it retreats from the viewer.
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusScale else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "cardFocusScale",
    )
    FocusableSurface(
        onClick = onClick,
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

// Replaces StandardCardContainer: an image card plus a title below it,
// title gap tuned per-card by the caller (see design spec section 05 — the
// gap has to clear the glow, not just the stroke, so it varies: 16dp for
// 2:3 posters, 8dp for 16:9 resume cards).
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
