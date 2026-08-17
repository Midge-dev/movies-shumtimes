package com.moviesshumtimes.tv.ui.common

import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moviesshumtimes.tv.ui.theme.AppSurfaceVariant
import com.moviesshumtimes.tv.ui.theme.NeonPurple

// Material 3's own shape-morphing loading indicator
// (m3.material.io/components/loading-indicator) — replaces the hand-rolled
// BufferingSpinner and the plain-text "Connecting…" screens alike.
// tv-material3 has no loading indicator of its own, so this borrows the one
// component pulled from the base (non-TV) Material3 library, restyled to
// this app's NeonPurple palette instead of Material's own default colors.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppLoadingIndicator(modifier: Modifier = Modifier) {
    ContainedLoadingIndicator(
        modifier = modifier,
        containerColor = AppSurfaceVariant,
        indicatorColor = NeonPurple,
    )
}
