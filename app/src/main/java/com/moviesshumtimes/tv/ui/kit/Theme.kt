package com.moviesshumtimes.tv.ui.kit

import androidx.compose.runtime.compositionLocalOf
import com.moviesshumtimes.tv.ui.theme.AppOnBackground

// Replaces tv-material3's MaterialTheme content-color propagation: most of
// the app's Text() calls pass no explicit color and expect to inherit
// whatever's ambient (AppWhite over video/scrims, AppOnBackground/AppOnSurface
// elsewhere) — see MainActivity's root CompositionLocalProvider. Kept as a
// plain CompositionLocal (not a bundled "theme" object) since this app's
// palette is just the flat AppColors.kt constants; there's no ColorScheme/
// Typography bundle to carry alongside it.
val LocalContentColor = compositionLocalOf { AppOnBackground }
