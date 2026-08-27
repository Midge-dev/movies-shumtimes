package com.moviesshumtimes.tv.ui.kit

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

// Replaces tv-material3's Text — same call shape (positional text, then the
// handful of named params every call site in this app actually uses) so
// migrating a screen is an import swap, not a rewrite. Unlike Material's
// Text, there's no MaterialTheme to read a default color/style from; color
// falls back to LocalContentColor and style defaults to bodyLarge.
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = ShumTypography.bodyLarge,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val contentColor = LocalContentColor.current
    val resolvedStyle = style.merge(
        TextStyle(
            color = if (color.isSpecified) color else contentColor,
            textAlign = textAlign,
        ),
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = resolvedStyle,
        maxLines = maxLines,
        overflow = overflow,
    )
}
