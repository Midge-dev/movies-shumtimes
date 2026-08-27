package com.moviesshumtimes.tv.ui.kit

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter

// Replaces tv-material3's Icon — same call shape as every existing call
// site (imageVector, contentDescription, tint). The ImageVector assets
// themselves (Icons.Default.Pause etc.) come from the separate, unrelated
// material-icons-extended artifact and aren't part of this replacement.
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Image(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint),
    )
}
